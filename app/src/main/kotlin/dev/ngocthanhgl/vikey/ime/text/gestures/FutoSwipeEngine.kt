/*
 * Copyright (C) 2026 ViKey Telex contributors
 *
 * Licensed under the GNU General Public License v3.0 or later.
 */

package dev.ngocthanhgl.vikey.ime.text.gestures

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.ml.inference.SwipeDecoder
import java.io.File

/**
 * Owns the FUTO Swipe models and the dictionary trie.
 *
 * Models and dictionary are bundled in the APK and copied once to the code
 * cache, because ExecuTorch needs real file paths. Nothing is downloaded.
 */
class FutoSwipeEngine(private val context: Context) {

    companion object {
        private const val TAG = "FutoSwipeEngine"
        private const val ASSET_ROOT = "futo-swipe"
        private const val CACHE_DIR = "futo-swipe-v1"
        private const val BEAM_WIDTH = 100
        private const val TOP_K = 4
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"

        private const val ENCODER = "honorable_sturgeon/model_fp32.pte"
        private const val DECODER = "magic_macaw/model_fp32.pte"
        private const val LM = "hungry_jellyfish/context_lm.pte"
        private const val LM_VOCAB = "hungry_jellyfish/vocab.txt"
        private const val DICTIONARY = "en_wordlist.combined"

        private val ASSETS = listOf(
            ENCODER,
            "honorable_sturgeon/metadata.json",
            DECODER,
            "magic_macaw/metadata.json",
            LM,
            "hungry_jellyfish/metadata.json",
            LM_VOCAB,
            DICTIONARY,
        )
    }

    data class Layout(val letters: String, val xs: FloatArray, val ys: FloatArray)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()

    @Volatile
    var isReady: Boolean = false
        private set

    private var started = false
    private var loadAttempted = false
    private var decoder: SwipeDecoder? = null
    private var trie: FutoTrieLoader.Handle? = null
    private var appliedLayout: Layout? = null

    @Volatile
    private var pendingLayout: Layout? = null

    fun start() {
        if (started) return
        started = true
        scope.launch { ensureLoaded() }
    }

    fun setLayout(layout: Layout) {
        pendingLayout = layout
        start()
    }

    /**
     * @return top words, or an empty list if the engine is unavailable. Callers
     * must provide at least two points with non-decreasing timestamps.
     */
    fun recognize(
        x: FloatArray,
        y: FloatArray,
        t: FloatArray,
        contextWords: List<String>,
        topK: Int = TOP_K,
    ): List<String> {
        synchronized(lock) {
            val engine = decoder ?: return emptyList()
            val layout = pendingLayout ?: return emptyList()
            if (layout !== appliedLayout) {
                val ok = runCatching {
                    engine.setMode(
                        layout.letters,
                        layout.xs,
                        layout.ys,
                        longArrayOf(trie?.itrie ?: return emptyList()),
                    )
                }.getOrDefault(false)
                if (!ok) {
                    Log.e(TAG, "setMode failed for ${layout.letters.length} keys")
                    return emptyList()
                }
                appliedLayout = layout
            }

            runCatching { SwipeDecoder.pinCores("big") }
            engine.setContext(contextWords.takeLast(10))
            return runCatching {
                val segment = SwipeDecoder.SwipeSeg(x, y, t)
                engine.recognize(
                    arrayOf(segment),
                    arrayOf(),
                    topK,
                    BEAM_WIDTH,
                    null,
                ).map { it.word }
            }.onFailure {
                Log.e(TAG, "recognize failed", it)
            }.getOrDefault(emptyList())
        }
    }

    /** Blocking helper for instrumentation tests. */
    fun awaitReady(timeoutMs: Long = 30_000L): Boolean {
        start()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isReady) return true
            Thread.sleep(50)
        }
        return isReady
    }

    fun lastTiming(): SwipeDecoder.Timing? = synchronized(lock) {
        runCatching { decoder?.lastTiming() }.getOrNull()
    }

    fun close() {
        scope.cancel()
        synchronized(lock) {
            runCatching { decoder?.close() }
            decoder = null
            runCatching { trie?.close() }
            trie = null
            isReady = false
        }
    }

    private suspend fun ensureLoaded() {
        synchronized(lock) {
            if (isReady || loadAttempted) return
            loadAttempted = true
        }
        withContext(Dispatchers.IO) {
            runCatching { load() }
                .onSuccess {
                    synchronized(lock) { isReady = true }
                    Log.i(TAG, "FUTO Swipe ready")
                }
                .onFailure {
                    Log.e(TAG, "Failed to load FUTO Swipe", it)
                }
        }
    }

    private fun load() {
        val dir = File(context.codeCacheDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()

        for (asset in ASSETS) {
            extract(asset, File(dir, asset))
        }

        val trieHandle = FutoTrieLoader.load(File(dir, DICTIONARY).absolutePath)
            ?: error("Could not load $DICTIONARY")
        val encoder = File(dir, ENCODER).absolutePath
        val decoderPath = File(dir, DECODER).absolutePath
        val lmPath = File(dir, LM).absolutePath
        val lmVocab = File(dir, LM_VOCAB).absolutePath

        val attempts = listOf(
            Triple(decoderPath, lmPath, lmVocab),
            Triple(decoderPath, null, null),
            Triple(null, lmPath, lmVocab),
            Triple(null, null, null),
        )

        var created: SwipeDecoder? = null
        var lastError: Throwable? = null
        for ((dec, lm, vocab) in attempts) {
            try {
                created = SwipeDecoder(
                    encoder,
                    dec,
                    2,
                    BEAM_WIDTH,
                    TOP_K,
                    true,
                    "f",
                    lm,
                    vocab,
                )
                break
            } catch (t: Throwable) {
                lastError = t
                Log.w(TAG, "Model combination failed (decoder=$dec, lm=$lm)", t)
            }
        }
        if (created == null) {
            trieHandle.close()
            throw lastError ?: IllegalStateException("No FUTO model combination loaded")
        }

        synchronized(lock) {
            trie = trieHandle
            decoder = created
        }
    }

    private fun extract(asset: String, target: File) {
        if (target.exists() && target.length() > 0L) return
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        context.assets.open("$ASSET_ROOT/$asset").use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
