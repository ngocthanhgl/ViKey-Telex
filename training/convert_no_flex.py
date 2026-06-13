"""
ViKey: Re-export TFLite model WITHOUT flex ops (SELECT_TF_OPS).
Run: python3 training/convert_no_flex.py --keras /sdcard/Download/results/vikey_cifg.keras

Removes the need for tensorflow-lite-select-tf-ops (~40-50MB installed).
"""
import os, sys, argparse
import tensorflow as tf
from tensorflow.keras import layers, Model
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument("--keras", default="/sdcard/Download/results/vikey_cifg.keras")
parser.add_argument("--out", default="/sdcard/Download/results/")
args = parser.parse_args()

VOCAB = 30000
EMBED = 256
CIFG_UNITS = 2048
SEQ_LEN = 50

class CIFGCell(layers.Layer):
    def __init__(self, units, **kwargs):
        super().__init__(**kwargs)
        self._units = units
        self.state_size = [units, units]
        self.output_size = units

    def build(self, input_shape):
        n = self._units
        d = input_shape[-1]
        self.W = self.add_weight(shape=(d, 3*n), initializer="glorot_uniform", name="W")
        self.U = self.add_weight(shape=(n, 3*n), initializer="orthogonal", name="U")
        self.b = self.add_weight(shape=(3*n,), initializer="zeros", name="b")
        self.built = True

    def call(self, x, states):
        h, c = states
        gates = tf.matmul(x, self.W) + tf.matmul(h, self.U) + self.b
        n = self._units
        z = gates[..., :n]
        i_gate = gates[..., n:2*n]
        o_gate = gates[..., 2*n:]
        i = tf.sigmoid(i_gate)
        f = 1.0 - i
        c2 = f * c + i * tf.tanh(z)
        h2 = tf.sigmoid(o_gate) * tf.tanh(c2)
        return h2, [h2, c2]

    def get_config(self):
        return {"units": self._units}

class SharedEmb(layers.Layer):
    def __init__(self, vs, d, **kw):
        super().__init__(**kw)
        self.vs = vs; self.d = d
    def build(self, _):
        self.e = self.add_weight(shape=(self.vs, self.d), initializer="uniform", name="shared_emb")
    def call(self, x, mode="e"):
        return tf.nn.embedding_lookup(self.e, x) if mode == "e" else tf.matmul(x, self.e, transpose_b=True)
    def get_config(self):
        return {"vs": self.vs, "d": self.d}

print("Loading saved Keras model...")
model = tf.keras.models.load_model(args.keras, custom_objects={
    "CIFGCell": CIFGCell,
    "SharedEmb": SharedEmb,
})
print(f"Loaded. Params: {model.count_params():,}")

# Export FP16 (builtin ops only)
print("\nExporting FP16 (no flex)...")
conv_fp16 = tf.lite.TFLiteConverter.from_keras_model(model)
conv_fp16.optimizations = [tf.lite.Optimize.DEFAULT]
conv_fp16.target_spec.supported_types = [tf.float16]
conv_fp16.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
tflite_fp16 = conv_fp16.convert()
fp16_path = os.path.join(args.out, "vikey_cifg_fp16.tflite")
with open(fp16_path, "wb") as f:
    f.write(tflite_fp16)
print(f"FP16: {len(tflite_fp16)/1e6:.2f} MB -> {fp16_path}")

# Export INT8 (builtin ops only)
print("Exporting INT8 (no flex)...")
def rep():
    for _ in range(100):
        yield [np.random.randint(0, VOCAB, (1, SEQ_LEN)).astype(np.int32)]

conv_int8 = tf.lite.TFLiteConverter.from_keras_model(model)
conv_int8.optimizations = [tf.lite.Optimize.DEFAULT]
conv_int8.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
conv_int8.representative_dataset = rep
tflite_int8 = conv_int8.convert()
int8_path = os.path.join(args.out, "vikey_cifg_int8.tflite")
with open(int8_path, "wb") as f:
    f.write(tflite_int8)
print(f"INT8: {len(tflite_int8)/1e6:.2f} MB -> {int8_path}")

# Verify no flex ops
for path in [fp16_path, int8_path]:
    with open(path, "rb") as f:
        d = f.read()
    if b"Flex" in d:
        print(f"WARNING: {os.path.basename(path)} still has flex ops!")
    else:
        print(f"OK: {os.path.basename(path)} is flex-free")

print("\nDone. Copy vikey_cifg_int8.tflite and tokenizer.json to assets/ime/dict/")
