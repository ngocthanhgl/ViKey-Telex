/*
 * Copyright (C) 2026 ViKey Telex contributors
 *
 * Licensed under the GNU General Public License v3.0 or later.
 */

package dev.ngocthanhgl.vikey.ime.text.gestures

/**
 * Loads an AOSP-style `.combined` dictionary into the FUTO Swipe trie engine.
 * The returned [Handle.itrie] is a native `ITrie*` accepted by
 * `org.futo.ml.inference.SwipeDecoder.setMode`.
 */
object FutoTrieLoader {
    init {
        System.loadLibrary("futo_trie")
    }

    class Handle internal constructor(val pointer: Long) : AutoCloseable {
        val itrie: Long = nativeItrie(pointer)

        override fun close() {
            if (pointer != 0L) {
                nativeFree(pointer)
            }
        }
    }

    fun load(path: String): Handle? {
        val pointer = runCatching { nativeLoad(path) }.getOrElse { return null }
        return if (pointer == 0L) null else Handle(pointer)
    }

    private external fun nativeLoad(path: String): Long
    private external fun nativeItrie(handle: Long): Long
    private external fun nativeFree(handle: Long)
}
