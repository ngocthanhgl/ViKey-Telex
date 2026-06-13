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
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TfLiteSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.tflite"
        private const val MODEL_PATH = "ime/dict/vikey_cifg_int8.tflite"
        private const val CONTEXT_LEN = 30
        private const val VOCAB_SIZE = 15000
        private const val PAD_ID = 0
        private const val UNK_ID = 1
    }

    private var interpreter: Interpreter? = null
    private var loaded = false

    override val providerId = ProviderId

    override suspend fun create() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            try {
                val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(modelBytes.size)
                buffer.order(ByteOrder.nativeOrder())
                buffer.put(modelBytes)
                interpreter = Interpreter(buffer)
                loaded = true
            } catch (e: Exception) {
                flogDebug { "TFLite: failed to load model: ${e.message}" }
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
    ): List<SuggestionCandidate> = withContext(Dispatchers.IO) {
        val interpreter = interpreter ?: return@withContext emptyList()
        try {
            val textBefore = content.textBeforeSelection

            if (textBefore.endsWith(" ")) {
                return@withContext predictNextWord(interpreter, textBefore, maxCandidateCount)
            }

            val prefix = getCurrentWord(content) ?: return@withContext emptyList()
            if (prefix.isBlank()) return@withContext emptyList()

            suggestCompletions(interpreter, textBefore, prefix, maxCandidateCount)
        } catch (e: Exception) {
            flogDebug { "TFLite:suggest failed: ${e.message}" }
            emptyList()
        }
    }

    private fun suggestCompletions(
        interpreter: Interpreter,
        textBefore: String,
        prefix: String,
        maxCount: Int,
    ): List<SuggestionCandidate> {
        val contextText = buildContext(textBefore, prefix)
        val inputIds = textToIds(contextText)
        val output = runInference(interpreter, inputIds)
        val pos = (contextText.length - 1).coerceIn(0, CONTEXT_LEN - 1)
        val probs = output[pos]

        val candidates = mutableListOf<SuggestionCandidate>()
        val usedChars = mutableSetOf<Char>()

        val topIndices = (0 until VOCAB_SIZE)
            .map { it to probs[it] }
            .filter { (id, _) ->
                val c = idToChar(id)
                c != '\u0000' && c.isLetterOrDigit() && c !in usedChars
            }
            .sortedByDescending { it.second }
            .take(maxCount)

        for ((id, _) in topIndices) {
            val c = idToChar(id)
            usedChars.add(c)
            val word = prefix + c
            candidates.add(
                WordSuggestionCandidate(
                    text = adjustCase(prefix, word),
                    confidence = 0.8,
                    isEligibleForAutoCommit = false,
                    sourceProvider = this,
                )
            )
        }

        return candidates
    }

    private fun predictNextWord(
        interpreter: Interpreter,
        textBefore: String,
        maxCount: Int,
    ): List<SuggestionCandidate> {
        val contextEnd = textBefore.trimEnd()
        val contextText = contextEnd.takeLast(CONTEXT_LEN)
        val inputIds = textToIds(contextText)
        val output = runInference(interpreter, inputIds)
        val pos = (contextText.length - 1).coerceIn(0, CONTEXT_LEN - 1)
        val probs = output[pos]

        val candidates = mutableListOf<SuggestionCandidate>()
        val usedChars = mutableSetOf<Char>()

        val topChars = (0 until VOCAB_SIZE)
            .map { it to probs[it] }
            .filter { (id, _) ->
                val c = idToChar(id)
                c.isLetter() && c !in usedChars
            }
            .sortedByDescending { it.second }
            .take(maxCount)

        for ((id, _) in topChars) {
            val c = idToChar(id)
            usedChars.add(c)
            candidates.add(
                WordSuggestionCandidate(
                    text = c.toString(),
                    confidence = 0.8,
                    isEligibleForAutoCommit = false,
                    sourceProvider = this,
                )
            )
        }

        return candidates
    }

    private fun buildContext(textBefore: String, prefix: String): String {
        val trimmed = textBefore.removeSuffix(prefix)
        return trimmed + prefix
    }

    private fun textToIds(text: String): IntArray {
        val ids = IntArray(CONTEXT_LEN) { PAD_ID }
        val chars = text.takeLast(CONTEXT_LEN)
        val offset = CONTEXT_LEN - chars.length
        for (i in chars.indices) {
            ids[offset + i] = charToId(chars[i])
        }
        return ids
    }

    private fun charToId(c: Char): Int {
        val cp = c.code
        if (cp in 32 until VOCAB_SIZE) return cp
        if (cp == '\n'.code) return ' '.code
        return UNK_ID
    }

    private fun idToChar(id: Int): Char {
        return if (id in 2 until VOCAB_SIZE) id.toChar() else '\u0000'
    }

    private fun runInference(interpreter: Interpreter, inputIds: IntArray): Array<FloatArray> {
        val input = arrayOf(inputIds)
        val output = Array(1) { Array(CONTEXT_LEN) { FloatArray(VOCAB_SIZE) } }
        interpreter.run(input, output)
        return output[0]
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
