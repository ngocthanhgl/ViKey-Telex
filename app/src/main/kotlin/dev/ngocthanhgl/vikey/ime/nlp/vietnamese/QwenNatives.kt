package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

object QwenNatives {
    var isAvailable = false
        private set

    init {
        try {
            System.loadLibrary("qwen_jni")
            isAvailable = true
        } catch (_: UnsatisfiedLinkError) { }
    }

    fun open(modelPath: String): Long {
        if (!isAvailable) return 0
        return try { nativeOpen(modelPath) } catch (_: Exception) { 0 }
    }

    fun close(ptr: Long) {
        if (!isAvailable || ptr == 0L) return
        try { nativeClose(ptr) } catch (_: Exception) { }
    }

    fun scoreCandidates(ptr: Long, prevWord: String?, candidates: Array<String>): FloatArray? {
        if (!isAvailable || ptr == 0L || candidates.isEmpty()) return null
        return try { nativeScoreCandidates(ptr, prevWord, candidates) } catch (_: Exception) { null }
    }

    fun predictNext(ptr: Long, text: String, topK: Int): Array<String>? {
        if (!isAvailable || ptr == 0L) return null
        return try { nativePredictNext(ptr, text, topK) } catch (_: Exception) { null }
    }

    private external fun nativeOpen(modelPath: String): Long
    private external fun nativeClose(ptr: Long)
    private external fun nativeScoreCandidates(ptr: Long, prevWord: String?, candidates: Array<String>): FloatArray
    private external fun nativePredictNext(ptr: Long, text: String, topK: Int): Array<String>
}
