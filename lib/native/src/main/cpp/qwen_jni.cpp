#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <vector>
#include <string>
#include <algorithm>
#include <cmath>
#include <numeric>

#include "llama.h"
#include "qwen_jni.h"

#define LOG_TAG "QwenJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string s(chars ? chars : "");
    env->ReleaseStringUTFChars(jstr, chars);
    return s;
}

static jstring string_to_jstring(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

static std::string token_to_piece(llama_context* ctx, llama_token token) {
    std::vector<char> buf(64);
    int n = llama_token_to_piece(ctx, token, buf.data(), buf.size(), 0, false);
    if (n < 0) {
        buf.resize(-n);
        n = llama_token_to_piece(ctx, token, buf.data(), buf.size(), 0, false);
    }
    return std::string(buf.data(), n);
}

static std::vector<llama_token> tokenize(llama_context* ctx, const std::string& text, bool add_bos) {
    std::vector<llama_token> tokens(text.length() + 16);
    int n = llama_tokenize(ctx, text.c_str(), text.length(), tokens.data(), tokens.size(), add_bos, false);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(ctx, text.c_str(), text.length(), tokens.data(), tokens.size(), add_bos, false);
    }
    tokens.resize(n);
    return tokens;
}

static void eval_string(QwenState* state, const std::string& text) {
    auto new_tokens = tokenize(state->ctx, text, true);

    size_t common = 0;
    while (common < state->cached_tokens.size() &&
           common < new_tokens.size() &&
           state->cached_tokens[common] == new_tokens[common]) {
        common++;
    }

    if (common < state->cached_tokens.size()) {
        llama_kv_cache_seq_rm(state->ctx, 0, common, -1);
    }

    for (size_t i = common; i < new_tokens.size(); i++) {
        llama_token batch_tokens[1] = { new_tokens[i] };
        llama_batch batch = llama_batch_get_one(batch_tokens, 1);
        if (llama_decode(state->ctx, batch) != 0) {
            LOGE("llama_decode failed at token %zu", i);
            state->cached_tokens.clear();
            return;
        }
    }

    state->cached_tokens = new_tokens;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_ngocthanhgl_vikey_ime_nlp_vietnamese_QwenNatives_nativeOpen(
    JNIEnv* env, jclass, jstring modelPath) {

    std::string path = jstring_to_string(env, modelPath);
    LOGI("Loading model: %s", path.c_str());

    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    llama_model* model = llama_init_from_file(path.c_str(), mparams);
    if (!model) {
        LOGE("Failed to load model from %s", path.c_str());
        return 0;
    }

    auto cparams = llama_context_default_params();
    cparams.n_ctx = 256;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    llama_context* ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_free_model(model);
        return 0;
    }

    QwenState* state = new QwenState();
    state->model = model;
    state->ctx = ctx;

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(state);
}

JNIEXPORT void JNICALL
Java_dev_ngocthanhgl_vikey_ime_nlp_vietnamese_QwenNatives_nativeClose(
    JNIEnv*, jclass, jlong ptr) {

    if (ptr == 0) return;
    QwenState* state = reinterpret_cast<QwenState*>(ptr);
    delete state;
    LOGI("Model closed");
}

JNIEXPORT jfloatArray JNICALL
Java_dev_ngocthanhgl_vikey_ime_nlp_vietnamese_QwenNatives_nativeScoreCandidates(
    JNIEnv* env, jclass, jlong ptr, jstring prevWord, jobjectArray candidates) {

    if (ptr == 0) return nullptr;
    QwenState* state = reinterpret_cast<QwenState*>(ptr);

    std::string context = jstring_to_string(env, prevWord);

    eval_string(state, context);

    float* logits = llama_get_logits_ith(state->ctx, -1);
    int n_vocab = llama_n_vocab(state->model);

    int count = env->GetArrayLength(candidates);
    std::vector<float> scores(count, -INFINITY);

    for (int i = 0; i < count; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(candidates, i);
        if (!jstr) continue;

        std::string word = jstring_to_string(env, jstr);
        env->DeleteLocalRef(jstr);

        if (word.empty()) continue;

        auto tokens = tokenize(state->ctx, word, false);
        if (tokens.empty()) continue;

        int id = tokens[0];
        if (id >= 0 && id < n_vocab) {
            scores[i] = logits[id];
        }
    }

    jfloatArray result = env->NewFloatArray(count);
    if (result) {
        env->SetFloatArrayRegion(result, 0, count, scores.data());
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_dev_ngocthanhgl_vikey_ime_nlp_vietnamese_QwenNatives_nativePredictNext(
    JNIEnv* env, jclass, jlong ptr, jstring text, jint topK) {

    if (ptr == 0) return nullptr;
    QwenState* state = reinterpret_cast<QwenState*>(ptr);

    std::string context = jstring_to_string(env, text);

    eval_string(state, context);

    float* logits = llama_get_logits_ith(state->ctx, -1);
    int n_vocab = llama_n_vocab(state->model);

    std::vector<std::pair<float, int>> scored;
    scored.reserve(n_vocab);
    for (int i = 0; i < n_vocab; i++) {
        scored.emplace_back(logits[i], i);
    }

    int k = std::min(std::max(1, (int)topK), n_vocab);
    std::partial_sort(scored.begin(), scored.begin() + k, scored.end(),
        [](const auto& a, const auto& b) { return a.first > b.first; });

    std::vector<std::string> words;
    for (int i = 0; i < k; i++) {
        llama_token token = scored[i].second;
        std::string piece = token_to_piece(state->ctx, token);
        piece.erase(0, piece.find_first_not_of(" \t\n\r"));
        piece.erase(piece.find_last_not_of(" \t\n\r") + 1);
        if (!piece.empty()) {
            words.push_back(piece);
        }
    }

    if (words.empty()) return nullptr;

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(words.size(), stringClass, nullptr);
    for (size_t i = 0; i < words.size(); i++) {
        env->SetObjectArrayElement(result, i, string_to_jstring(env, words[i]));
    }
    return result;
}

} // extern "C"
