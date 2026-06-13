package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import dev.ngocthanhgl.vikey.appContext
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.editor.EditorContent
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionProvider
import dev.ngocthanhgl.vikey.ime.nlp.WordSuggestionCandidate
import dev.ngocthanhgl.vikey.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class TfLiteSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.tflite"
        private const val MODEL_PATH = "ime/dict/vikey_cifg_int8.tflite"
        private const val VI_DICT_PATH = "ime/dict/vi.json"
        private const val CONTEXT_LEN = 30
        private const val VOCAB_SIZE = 15000
        private const val PAD_ID = 0
        private const val UNK_ID = 1
    }

    private val appContext by context.appContext()
    private var interpreter: Interpreter? = null
    private var dict: Map<String, Int> = emptyMap()
    private var dictLoaded = false
    private var modelLoaded = false

    override val providerId = ProviderId

    override suspend fun create() {
        withContext(Dispatchers.IO) {
            loadDict()
            loadModel()
        }
    }

    override suspend fun preload(subtype: Subtype) {
        if (!modelLoaded || !dictLoaded) create()
    }

    private suspend fun loadDict() {
        if (dictLoaded) return
        try {
            val raw = appContext.assets.readText(VI_DICT_PATH)
            val serializer = MapSerializer(String.serializer(), Int.serializer())
            dict = Json.decodeFromString(serializer, raw)
            dictLoaded = true
        } catch (e: Exception) {
            flogDebug { "TFLite: failed to load dict: ${e.message}" }
        }
    }

    private suspend fun loadModel() {
        if (modelLoaded) return
        try {
            val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(modelBytes.size)
            buffer.order(ByteOrder.nativeOrder())
            buffer.put(modelBytes)
            interpreter = Interpreter(buffer)
            modelLoaded = true
        } catch (e: Exception) {
            flogDebug { "TFLite: failed to load model: ${e.message}" }
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> = withContext(Dispatchers.IO) {
        try {
            val textBefore = content.textBeforeSelection
            val prefix = getCurrentWord(content) ?: return@withContext emptyList()
            if (prefix.isBlank()) return@withContext emptyList()

            suggestCompletions(textBefore, prefix, maxCandidateCount)
        } catch (e: Exception) {
            flogDebug { "TFLite:suggest failed: ${e.message}" }
            emptyList()
        }
    }

    private fun suggestCompletions(
        textBefore: String,
        prefix: String,
        maxCount: Int,
    ): List<SuggestionCandidate> {
        if (!dictLoaded) return emptyList()
        val lower = prefix.lowercase(Locale.ROOT)

        val matches = dict.entries
            .filter { (k, _) -> k.startsWith(lower) && k.length > lower.length }
            .sortedByDescending { it.value }
            .take(maxCount * 2)

        if (matches.isEmpty()) return emptyList()
        if (!modelLoaded) return buildDictSuggestions(matches, lower, maxCount)

        val probs = predictNextCharProbs(textBefore)

        val scored = matches.map { (word, freq) ->
            val suffix = word.removePrefix(lower)
            val modelScore = suffix.firstOrNull()?.let { c ->
                val id = charToId(c)
                if (id in probs.indices) probs[id].toDouble() else 0.0
            } ?: 0.0
            word to (freq.toDouble() * (1.0 + modelScore))
        }

        return scored
            .sortedByDescending { it.second }
            .take(maxCount)
            .mapIndexed { index, (word, _) ->
                WordSuggestionCandidate(
                    text = adjustCase(prefix, word),
                    confidence = (1.0 - index * 0.08).coerceAtLeast(0.1),
                    isEligibleForAutoCommit = word == lower && index == 0,
                    sourceProvider = this,
                )
            }
    }

    private fun buildDictSuggestions(
        matches: List<Map.Entry<String, Int>>,
        lower: String,
        maxCount: Int,
    ): List<SuggestionCandidate> {
        return matches.take(maxCount).mapIndexed { index, (word, _) ->
            WordSuggestionCandidate(
                text = adjustCase(lower, word),
                confidence = (1.0 - index * 0.08).coerceAtLeast(0.1),
                isEligibleForAutoCommit = word == lower && index == 0,
                sourceProvider = this,
            )
        }
    }

    private fun predictNextCharProbs(textBefore: String): FloatArray {
        val interpreter = interpreter ?: return FloatArray(VOCAB_SIZE)
        return try {
            val contextText = textBefore.takeLast(CONTEXT_LEN)
            val inputIds = IntArray(CONTEXT_LEN) { PAD_ID }
            val offset = CONTEXT_LEN - contextText.length
            for (i in contextText.indices) {
                inputIds[offset + i] = charToId(contextText[i])
            }

            val input = arrayOf(inputIds)
            val output = Array(1) { Array(CONTEXT_LEN) { FloatArray(VOCAB_SIZE) } }
            interpreter.run(input, output)
            output[0][CONTEXT_LEN - 1]
        } catch (e: Exception) {
            flogDebug { "TFLite: inference failed: ${e.message}" }
            FloatArray(VOCAB_SIZE)
        }
    }

    private fun charToId(c: Char): Int {
        val cp = c.code
        if (cp in 32 until VOCAB_SIZE) return cp
        if (cp == '\n'.code) return ' '.code
        return UNK_ID
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

    override suspend fun getListOfWords(subtype: Subtype): List<String> = dict.keys.toList()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return dict.getOrDefault(word, 0) / 255.0
    }

    override suspend fun destroy() {
        interpreter?.close()
        interpreter = null
        modelLoaded = false
    }
}
