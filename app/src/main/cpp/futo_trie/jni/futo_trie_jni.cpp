/*
 * Minimal JNI bridge that loads an AOSP-style .combined dictionary into the
 * same Trie implementation the FUTO Swipe engine uses. The returned ITrie*
 * is passed to org.futo.ml.inference.SwipeDecoder.setMode().
 *
 * Copyright (C) 2026 ViKey Telex contributors
 * Derived from FUTO Swipe (GPLv3): https://gitlab.futo.org/keyboard/swipe-library
 */

#include <jni.h>

#include <cstdint>
#include <string>

#include "swipe_decoder/trie.hpp"

using swipe_decoder::ParsedCombined;
using swipe_decoder::Trie;

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_ngocthanhgl_vikey_ime_text_gestures_FutoTrieLoader_nativeLoad(
    JNIEnv *env, jclass /* clazz */, jstring path
) {
    if (path == nullptr) return 0;

    const char *raw = env->GetStringUTFChars(path, nullptr);
    if (raw == nullptr) return 0;

    ParsedCombined parsed;
    const bool ok = swipe_decoder::parse_combined_vocab(std::string(raw), "f", parsed);
    env->ReleaseStringUTFChars(path, raw);
    if (!ok || parsed.empty()) return 0;

    auto *trie = new Trie();
    swipe_decoder::apply_parsed_to_trie(parsed, *trie);
    if (trie->size() == 0) {
        delete trie;
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(trie));
}

JNIEXPORT jlong JNICALL
Java_dev_ngocthanhgl_vikey_ime_text_gestures_FutoTrieLoader_nativeItrie(
    JNIEnv * /* env */, jclass /* clazz */, jlong handle
) {
    auto *trie = reinterpret_cast<Trie *>(static_cast<uintptr_t>(handle));
    if (trie == nullptr) return 0;
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(trie->itrie()));
}

JNIEXPORT void JNICALL
Java_dev_ngocthanhgl_vikey_ime_text_gestures_FutoTrieLoader_nativeFree(
    JNIEnv * /* env */, jclass /* clazz */, jlong handle
) {
    auto *trie = reinterpret_cast<Trie *>(static_cast<uintptr_t>(handle));
    delete trie;
}

}  // extern "C"
