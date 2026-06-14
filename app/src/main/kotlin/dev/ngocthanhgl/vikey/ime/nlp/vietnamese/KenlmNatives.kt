package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

object KenlmNatives {
    var isAvailable = false
        private set

    init {
        try {
            System.loadLibrary("kenlm_jni")
            isAvailable = true
        } catch (_: UnsatisfiedLinkError) { }
    }

    fun loadModel(modelPath: String): Long {
        if (!isAvailable) return 0
        return try { nativeLoad(modelPath) } catch (_: Exception) { 0 }
    }

    fun unloadModel(ptr: Long) {
        if (!isAvailable || ptr == 0L) return
        try { nativeUnload(ptr) } catch (_: Exception) { }
    }

    fun scoreCandidates(ptr: Long, prevWord: String?, candidates: Array<String>): FloatArray? {
        if (!isAvailable || ptr == 0L || candidates.isEmpty()) return null
        return try { nativeScoreCandidates(ptr, prevWord, candidates) } catch (_: Exception) { null }
    }

    private external fun nativeLoad(modelPath: String): Long
    private external fun nativeUnload(ptr: Long)
    private external fun nativeScoreCandidates(ptr: Long, prevWord: String?, candidates: Array<String>): FloatArray
}
