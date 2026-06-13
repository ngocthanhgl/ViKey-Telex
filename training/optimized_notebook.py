# ViKey CIFG Training - Optimized v2
# EN + VI | Kaggle GPU T4 | 1M sentences | fp16 export
# Expected: ~2h training time

# CELL1: SETUP
import os, json, re, time, gc
from collections import Counter

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
os.environ["TF_GPU_ALLOCATOR"] = "cuda_malloc_async"
!pip install -q tqdm 2>/dev/null

import tensorflow as tf
from tensorflow.keras import layers, Model, backend as K
from tqdm.notebook import tqdm
import numpy as np

# Enable mixed precision (2x faster on T4)
tf.keras.mixed_precision.set_global_policy("mixed_float16")
# Fallback to float32 for softmax stability
import keras; keras.config.set_dtype_policy("float32", layer_name="output")

print(f"GPU: {tf.config.list_physical_devices('GPU')}")
print(f"TF: {tf.__version__}")
print(f"Policy: {tf.keras.mixed_precision.global_policy()}")

# CELL2: CONFIG
class C:
    vocab_size    = 15000
    embedding_dim = 128       # 96 -> 128 (better representations)
    cifg_units    = 1024      # 670 -> 1024 (more capacity)
    seq_len       = 30
    batch_size    = 256
    epochs        = 15
    lr            = 2e-3      # higher LR with cosine decay
    min_lr        = 1e-5
    sent_limit    = 1_000_000 # 200K -> 1M (5x more data)
    working_dir   = "/kaggle/working"

cfg = C()
os.makedirs(cfg.working_dir, exist_ok=True)

print(f"Vocab={cfg.vocab_size} Embed={cfg.embedding_dim} CIFG={cfg.cifg_units}u")
print(f"Batch={cfg.batch_size} Epochs={cfg.epochs} Data={cfg.sent_limit:,}")

# CELL3: CIFG LAYER
class CIFGCell(layers.Layer):
    def __init__(self, units, **kwargs):
        super().__init__(**kwargs)
        self._units = units
        self.state_size = [units, units]
        self.output_size = units

    def build(self, input_shape):
        n = self._units; d = input_shape[-1]
        self.W = self.add_weight(shape=(d, 3*n), initializer="glorot_uniform", name="W")
        self.U = self.add_weight(shape=(n, 3*n), initializer="orthogonal", name="U")
        self.b = self.add_weight(shape=(3*n,), initializer="zeros", name="b")
        self.ln = layers.LayerNormalization()
        self.built = True

    def call(self, x, states):
        h, c = states
        gate_input = tf.matmul(x, self.W) + tf.matmul(h, self.U) + self.b
        z, i, o = tf.split(gate_input, 3, -1)
        # LayerNorm on cell input (stabilizes training)
        z = self.ln(z)
        f = 1.0 - tf.sigmoid(i)
        c2 = f * c + tf.sigmoid(i) * tf.tanh(z)
        h2 = tf.sigmoid(o) * tf.tanh(c2)
        return h2, [h2, c2]

class SharedEmb(layers.Layer):
    def __init__(self, vs, d, **kw):
        super().__init__(**kw)
        self.vs = vs; self.d = d
    def build(self, _):
        self.e = self.add_weight(
            shape=(self.vs, self.d),
            initializer="uniform",
            name="shared_emb"
        )
    def call(self, x, mode="e"):
        return tf.nn.embedding_lookup(self.e, x) if mode == "e" else tf.matmul(x, self.e, transpose_b=True)

# CELL4: BUILD
def build_model(vs, ed, cu, sl):
    se = SharedEmb(vs, ed)
    i = layers.Input((sl,), dtype=tf.int32)
    x = se(i, mode="e")
    h = layers.RNN(CIFGCell(cu), return_sequences=True)(x)
    p = layers.Dense(ed)(h)
    o = layers.Activation("softmax", dtype="float32")(se(p, mode="d"))
    return Model(i, o, name="vikey"), se

model, _ = build_model(cfg.vocab_size, cfg.embedding_dim, cfg.cifg_units, cfg.seq_len)
model.summary(line_length=100)
print(f"\nParams: {model.count_params():,}")

# CELL5: TOKENIZER
def tokenize(text):
    text = re.sub(r"[^\w\s']", " ", text.lower().strip())
    return re.sub(r"\s+", " ", text).split()

def build_vocab(texts, vs):
    c = Counter()
    for t in tqdm(texts, desc="Vocab"):
        c.update(tokenize(t))
    mc = c.most_common(vs - 3)  # 4 special tokens: pad=0, bos=1, eos=2, oov=3
    w2i = {"<pad>": 0, "<bos>": 1, "<eos>": 2, "<oov>": 3}
    for i, (w, _) in enumerate(mc, 4):
        w2i[w] = i
    return w2i, {v: k for k, v in w2i.items()}

# CELL6: LOAD DATA (use ALL shards, more sentences)
print("=" * 60)
print("Loading wiki40b/en + wiki40b/vi...")
print("=" * 60)

def extract_gcs(lang, limit):
    import urllib.request, io, os, tempfile
    base_url = "https://storage.googleapis.com/tfds-data/downloads/wiki40b/tfrecord_prod/train"
    total_shards = {"en": 6, "vi": 4}
    shards = {"en": list(range(6)), "vi": list(range(4))}  # ALL shards

    out = []
    for sidx in shards[lang]:
        if len(out) >= limit: break
        fname = f"{lang}_examples-{sidx:05d}-of-{total_shards[lang]:05d}"
        url = f"{base_url}/{fname}"
        print(f"  Downloading {fname}...")
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=300) as resp:
            raw = resp.read()
        print(f"    {len(raw)/1e6:.1f} MB")

        tmp = os.path.join(tempfile.mkdtemp(), fname)
        with open(tmp, "wb") as f: f.write(raw)
        count = 0
        for ex in tf.data.TFRecordDataset(tmp, compression_type=""):
            try:
                example = tf.train.Example()
                example.ParseFromString(ex.numpy())
                text = example.features.feature["text"].bytes_list.value[0].decode("utf-8", errors="ignore")
                for para in text.split("\n"):
                    para = para.strip()
                    if len(para) >= 20:
                        out.append(para)
                        count += 1
                        if len(out) >= limit: break
            except: pass
            if len(out) >= limit: break
        os.remove(tmp)
        print(f"    {count} sentences (total: {len(out)})")
    return out[:limit]

en = extract_gcs("en", int(cfg.sent_limit * 0.40))
vi = extract_gcs("vi", int(cfg.sent_limit * 0.60))

all_texts = en + vi
print(f"\nEN={len(en):,}  VI={len(vi):,}  Total={len(all_texts):,}")
del en, vi; gc.collect()

# CELL7: BUILD TOKENIZER
print("\n" + "=" * 60)
print("Training tokenizer...")
print("=" * 60)

word2idx, idx2word = build_vocab(all_texts, cfg.vocab_size)
print(f"Vocab size: {len(word2idx)}")

tok_path = os.path.join(cfg.working_dir, "tokenizer.json")
json.dump({"w2i": word2idx, "i2w": {int(k): v for k, v in idx2word.items()}},
          open(tok_path, "w", encoding="utf-8"), ensure_ascii=False)
print(f"Saved: {tok_path}")

# CELL8: TF.DATASET
def gen(texts, w2i, sl):
    bos = w2i["<bos>"]; eos = w2i["<eos>"]; oov = w2i["<oov>"]
    for t in texts:
        ids = [w2i.get(w, oov) for w in tokenize(t)]
        if len(ids) < 2: continue
        # Truncate to seq_len
        ids = ids[:sl]
        # Input: bos + first sl-1 tokens
        # Target: next token for each position
        inp = [bos] + ids[:sl-1]
        tgt = ids[:sl] + [eos]
        if len(inp) < sl:
            inp = inp + [eos] * (sl - len(inp))
            tgt = tgt + [eos] * (sl - len(tgt))
        yield tf.constant(inp, tf.int32), tf.constant(tgt, tf.int32)

np.random.shuffle(all_texts)
sp = int(len(all_texts) * 0.95)
train_t, val_t = all_texts[:sp], all_texts[sp:]
print(f"Train={len(train_t):,}  Val={len(val_t):,}")

sig = (tf.TensorSpec((cfg.seq_len,), tf.int32),
       tf.TensorSpec((cfg.seq_len,), tf.int32))

train_ds = tf.data.Dataset.from_generator(
    lambda: gen(train_t, word2idx, cfg.seq_len),
    output_signature=sig
).shuffle(10000, reshuffle_each_iteration=True).repeat() \
 .batch(cfg.batch_size).prefetch(tf.data.AUTOTUNE)

val_ds = tf.data.Dataset.from_generator(
    lambda: gen(val_t, word2idx, cfg.seq_len),
    output_signature=sig
).repeat().batch(cfg.batch_size).prefetch(tf.data.AUTOTUNE)

train_steps = max(1, len(train_t) * cfg.seq_len // cfg.batch_size)
val_steps   = max(1, len(val_t) * cfg.seq_len // cfg.batch_size)
print(f"Steps/epoch: train={train_steps:,}  val={val_steps:,}")

# CELL9: COMPILE
# Cosine decay with warmup
lr_schedule = tf.keras.optimizers.schedules.CosineDecay(
    initial_learning_rate=cfg.lr,
    decay_steps=train_steps * 3,
    alpha=cfg.min_lr / cfg.lr,
)

model.compile(
    optimizer=tf.keras.optimizers.Adam(lr_schedule, clipnorm=3.0),
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"],
)

# CELL10: CALLBACKS
class ProgCB(tf.keras.callbacks.Callback):
    def __init__(self, n):
        self.nsteps = n
    def on_epoch_begin(self, e, _):
        print(f"\n{'='*60}\nEpoch {e+1}/{cfg.epochs}\n{'='*60}")
        self.p = tqdm(total=self.nsteps, desc=f"Train {e+1}", unit="b", leave=False)
        self.s = 0
    def on_batch_end(self, b, logs=None):
        self.s += 1; self.p.update(1)
        if self.s % 200 == 0:
            self.p.set_postfix({
                "loss": f"{logs.get('loss',0):.4f}",
                "acc": f"{logs.get('accuracy',0):.3f}",
                "lr": f"{float(K.get_value(model.optimizer.learning_rate)):.1e}"
            })
    def on_epoch_end(self, e, l=None):
        self.p.close()
        if l:
            print(f"  loss={l.get('loss',0):.4f} acc={l.get('accuracy',0):.3f}  "
                  f"val_loss={l.get('val_loss',0):.4f} val_acc={l.get('val_accuracy',0):.3f}")
        # Save final weights each epoch
        model.save_weights(os.path.join(cfg.working_dir, f"ckpt_{e+1:02d}.weights.h5"))

ckpt = tf.keras.callbacks.ModelCheckpoint(
    os.path.join(cfg.working_dir, "ckpt_{epoch:02d}.weights.h5"),
    save_weights_only=True, verbose=0)
rlr  = tf.keras.callbacks.ReduceLROnPlateau(
    monitor="val_loss", factor=0.5, patience=3, min_lr=1e-5, verbose=1)
es   = tf.keras.callbacks.EarlyStopping(
    monitor="val_loss", patience=8, restore_best_weights=True, verbose=1)

# CELL11: TRAIN
print("\n" + "=" * 60)
print("TRAINING START")
print(f"{cfg.epochs} epochs x {train_steps:,} steps = {cfg.epochs*train_steps:,} total")
print(f"Kaggle limit: 9h, expected: ~2-3h")
print("=" * 60)

t0 = time.time()
h = model.fit(train_ds, steps_per_epoch=train_steps,
              validation_data=val_ds, validation_steps=val_steps,
              epochs=cfg.epochs,
              callbacks=[ProgCB(train_steps), ckpt, rlr, es], verbose=0)

model.save(os.path.join(cfg.working_dir, "vikey_cifg.keras"))
elapsed = (time.time() - t0) / 3600
print(f"\nDone: {len(h.history['loss'])} epochs in {elapsed:.1f}h")

# CELL12: EVAL
print("\n" + "=" * 60)
print("EVAL Top-1 / Top-3 / Perplexity")
print("=" * 60)

def topk(m, ds, n=500, k=3):
    c1=ck=tot=0; losses=[]
    for xb, yb in tqdm(ds.take(n), total=n, desc="Eval"):
        p = m.predict(xb, verbose=0)
        tg = yb
        # Only evaluate last position (next-word prediction)
        pl = p[:, -1, :]
        tl = tg[:, -1]
        t1 = tf.cast(tf.argmax(pl, -1), tf.int32)
        tk = tf.cast(tf.math.top_k(pl, k).indices, tf.int32)
        c1 += int(tf.reduce_sum(tf.cast(t1 == tl, tf.int32)))
        for i in range(k):
            ck += int(tf.reduce_sum(tf.cast(tk[:, i] == tl, tf.int32)))
        tot += xb.shape[0]
        # Perplexity (cross-entropy loss)
        ce = tf.keras.losses.sparse_categorical_crossentropy(tl, pl)
        losses.extend(ce.numpy())
    ppl = np.exp(np.mean(losses))
    return c1/tot, ck/tot, ppl

t1, t3, ppl = topk(model, val_ds, 500)
print(f"\n  Top-1:  {t1:.1%}")
print(f"  Top-3:  {t3:.1%}")
print(f"  Perp:   {ppl:.1f}")
print(f"  (Gboard: 16.5% / 27.1% / perp=47)")

# CELL13: TFLITE EXPORT
print("\n" + "=" * 60)
print("EXPORT TFLITE")
print("=" * 60)

# FP16 (best quality for on-device)
conv = tf.lite.TFLiteConverter.from_keras_model(model)
conv.optimizations = [tf.lite.Optimize.DEFAULT]
conv.target_spec.supported_types = [tf.float16]
conv.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]
tflite = conv.convert()
p = os.path.join(cfg.working_dir, "vikey_cifg_fp16.tflite")
with open(p, "wb") as f: f.write(tflite)
print(f"FP16: {len(tflite)/1e6:.2f} MB -> {p}")

# Also export INT8 for comparison
try:
    c2 = tf.lite.TFLiteConverter.from_keras_model(model)
    c2.optimizations = [tf.lite.Optimize.DEFAULT]
    c2.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    def rep():
        for _ in range(100):
            yield [np.random.randint(0, cfg.vocab_size, (1, cfg.seq_len)).astype(np.int32)]
    c2.representative_dataset = rep
    tflite_int8 = c2.convert()
    p2 = os.path.join(cfg.working_dir, "vikey_cifg_int8.tflite")
    with open(p2, "wb") as f: f.write(tflite_int8)
    print(f"INT8: {len(tflite_int8)/1e6:.2f} MB -> {p2}")
except Exception as e:
    print(f"INT8 failed: {e}")

print("\nFiles in /kaggle/working/:")
for f in os.listdir(cfg.working_dir):
    fp = os.path.join(cfg.working_dir, f)
    if os.path.isfile(fp):
        print(f"  {f:<35s} {os.path.getsize(fp)/1e6:.2f} MB")

# CELL14: TEST
print("\n" + "=" * 60)
print("TEST - quick sanity check")
print("=" * 60)

it = tf.lite.Interpreter(model_path=p)
it.allocate_tensors()
id_ = it.get_input_details()
od_ = it.get_output_details()

def pred(text, k=5):
    tok = [word2idx.get(w, word2idx["<oov>"]) for w in tokenize(text)]
    tok = [word2idx["<bos>"]] * (cfg.seq_len - len(tok)) + tok[-(cfg.seq_len):]
    it.set_tensor(id_[0]["index"], np.array([tok], np.int32))
    it.invoke()
    probs = it.get_tensor(od_[0]["index"])[0, -1, :]
    top = np.argsort(probs)[::-1][:k]
    return [(idx2word[int(i)], float(probs[i])) for i in top]

tests = [
    "hello how are",
    "xin chào",
    "the",
    "tôi là",
    "machine learning",
    "english is",
    "việt nam",
]
for pre in tests:
    ps = pred(pre, 3)
    print(f"  '{pre}' -> {', '.join(f\"'{w}' ({p:.1%})\" for w,p in ps)}")

print("\n✅ Done. Commit notebook → Output tab → download")
