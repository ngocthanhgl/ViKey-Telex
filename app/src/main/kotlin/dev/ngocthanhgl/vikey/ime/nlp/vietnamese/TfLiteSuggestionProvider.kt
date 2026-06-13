package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.editor.EditorContent
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionProvider
import dev.ngocthanhgl.vikey.ime.nlp.WordSuggestionCandidate
import dev.ngocthanhgl.vikey.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TfLiteSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.tflite"
        private const val MODEL_PATH = "ime/dict/vikey_cifg_int8.tflite"
        private const val TOKENIZER_PATH = "ime/dict/tokenizer.json"
        private const val CONTEXT_LEN = 30
        private const val VOCAB_SIZE = 15000
        private const val OOV_ID = 3
    }

    private var interpreter: Interpreter? = null
    private var w2i: Map<String, Int> = emptyMap()
    private var i2w: Map<Int, String> = emptyMap()
    private var loaded = false

    override val providerId = ProviderId

    override suspend fun create() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            try {
                loadTokenizer()
                val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(modelBytes.size)
                buffer.order(ByteOrder.nativeOrder())
                buffer.put(modelBytes)
                interpreter = Interpreter(buffer)
                loaded = true
            } catch (e: Exception) {
                flogDebug { "TFLite: init failed: ${e.message}" }
            }
        }
    }

    override suspend fun preload(subtype: Subtype) {
        if (!loaded) create()
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        if (!loaded) return emptyList()
        val interp = interpreter ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val textBefore = content.textBeforeSelection
                val prefix = getCurrentWord(content) ?: return@withContext emptyList()

                val words = tokenize(textBefore.removeSuffix(prefix))
                val probs = predictRaw(interp, words)

                val result = if (prefix.isNotBlank()) {
                    i2w.values.filter { w -> w.startsWith(prefix, ignoreCase = true) && !w.startsWith("<") }
                        .map { w -> w to (probs[w2i[w] ?: OOV_ID].toDouble().coerceAtLeast(0.0)) }
                        .sortedByDescending { it.second }
                        .take(maxCandidateCount)
                } else {
                    topKWords(probs, maxCandidateCount)
                }

                result.map { (word, _) ->
                    WordSuggestionCandidate(
                        text = adjustCase(prefix, word),
                        confidence = 1.0,
                        isEligibleForAutoCommit = false,
                        sourceProvider = this@TfLiteSuggestionProvider,
                    )
                }
            } catch (e: Exception) {
                flogDebug { "TFLite:suggest failed: ${e.message}" }
                emptyList()
            }
        }
    }

    private fun predictRaw(interp: Interpreter, words: List<String>): FloatArray {
        val inputIds = IntArray(CONTEXT_LEN) { 0 }
        val lastWords = words.takeLast(CONTEXT_LEN)
        val offset = CONTEXT_LEN - lastWords.size
        for (i in lastWords.indices) {
            inputIds[offset + i] = w2i[lastWords[i]] ?: OOV_ID
        }
        val input = arrayOf(inputIds)
        val output = Array(1) { Array(CONTEXT_LEN) { FloatArray(VOCAB_SIZE) } }
        interp.run(input, output)
        return output[0][CONTEXT_LEN - 1]
    }

    private fun topKWords(probs: FloatArray, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceAtMost(50)
        val scored = mutableListOf<Pair<Int, Float>>()
        for (id in 0 until VOCAB_SIZE) {
            val word = i2w[id] ?: continue
            if (!word.startsWith("<")) {
                scored.add(id to probs[id])
            }
        }
        return scored.sortedByDescending { it.second }
            .take(limit)
            .map { (id, p) -> i2w[id]!! to p.toDouble() }
    }

    private fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun loadTokenizer() {
        val text = BufferedReader(InputStreamReader(context.assets.open(TOKENIZER_PATH)))
            .use { it.readText() }
        val json = JSONObject(text)
        val w2iObj = json.getJSONObject("w2i")
        val i2wObj = json.getJSONObject("i2w")
        w2i = mutableMapOf<String, Int>().apply {
            for (key in w2iObj.keys()) put(key, w2iObj.getInt(key))
        }
        i2w = mutableMapOf<Int, String>().apply {
            for (key in i2wObj.keys()) put(key.toInt(), i2wObj.getString(key))
        }
    }

    private fun adjustCase(reference: String, word: String): String {
        if (reference.length > 1 && reference.all { it.isUpperCase() }) return word.uppercase()
        if (reference.firstOrNull()?.isUpperCase() == true) return word.replaceFirstChar { it.uppercase() }
        return word
    }

    private fun getCurrentWord(content: EditorContent): String? {
        content.composingText.let { if (it.isNotBlank()) return it.toString() }
        content.currentWordText.let { if (it.isNotBlank()) return it.toString() }
        val textBefore = content.textBeforeSelection
        if (textBefore.isNotBlank()) {
            val words = textBefore.split(Regex("[\\s\\p{Punct}]+"))
            return words.lastOrNull { it.isNotBlank() }
        }
        return null
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {}
    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }
    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }
    override suspend fun getListOfWords(subtype: Subtype): List<String> = emptyList()
    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double = 0.0
    override suspend fun destroy() {
        interpreter?.close()
        interpreter = null
        loaded = false
    }
}
