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
import java.util.Locale

class TfLiteSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.tflite"
        private const val MODEL_PATH = "ime/dict/vikey_cifg_int8.tflite"
        private const val CONTEXT_LEN = 30
        private const val VOCAB_SIZE = 15000
        private const val PAD_ID = 0
        private const val UNK_ID = 1
        private const val MAX_GEN_LEN = 20
        private const val BEAM_WIDTH = 3
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
        if (!loaded) return@withContext emptyList()
        try {
            val textBefore = content.textBeforeSelection
            val prefix = getCurrentWord(content) ?: return@withContext emptyList()
            if (prefix.isBlank()) return@withContext emptyList()

            generateCompletions(interpreter, textBefore, prefix, maxCandidateCount)
        } catch (e: Exception) {
            flogDebug { "TFLite:suggest failed: ${e.message}" }
            emptyList()
        }
    }

    private fun generateCompletions(
        interpreter: Interpreter,
        textBefore: String,
        prefix: String,
        maxCount: Int,
    ): List<SuggestionCandidate> {
        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<SuggestionCandidate>()

        // Beam search: take top beamWidth chars, extend each greedily
        val contextEnd = buildContextTail(textBefore, prefix)
        val baseProbs = predictNext(interpreter, contextEnd)
        val topChars = topKChars(baseProbs, BEAM_WIDTH)

        for ((firstChar, _) in topChars) {
            val word = greedyExtend(interpreter, contextEnd, firstChar)
            if (word.isNotEmpty() && word !in seen && word != prefix) {
                seen.add(word)
                candidates.add(
                    WordSuggestionCandidate(
                        text = adjustCase(prefix, word),
                        confidence = 0.9,
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                )
            }
        }

        // If not enough candidates, add more from top beam
        if (candidates.size < maxCount && topChars.size > BEAM_WIDTH) {
            for ((char, _) in topChars.drop(BEAM_WIDTH)) {
                if (candidates.size >= maxCount) break
                val word = prefix + char
                if (word !in seen) {
                    seen.add(word)
                    candidates.add(
                        WordSuggestionCandidate(
                            text = adjustCase(prefix, word),
                            confidence = 0.7,
                            isEligibleForAutoCommit = false,
                            sourceProvider = this,
                        )
                    )
                }
            }
        }

        candidates.take(maxCount)
    }

    private fun greedyExtend(
        interpreter: Interpreter,
        contextEnd: String,
        firstChar: Char,
    ): String {
        val sb = StringBuilder()
        sb.append(firstChar)
        var input = contextEnd + firstChar

        for (step in 0 until MAX_GEN_LEN) {
            val probs = predictNext(interpreter, input)
            val next = argMaxChar(probs) ?: break
            if (next == ' ' || next == '\n') break
            if (next.code !in 32 until VOCAB_SIZE) break
            sb.append(next)
            input += next
        }

        return sb.toString()
    }

    private fun predictNext(interpreter: Interpreter, text: String): FloatArray {
        val inputIds = IntArray(CONTEXT_LEN) { PAD_ID }
        val chars = text.takeLast(CONTEXT_LEN)
        val offset = CONTEXT_LEN - chars.length
        for (i in chars.indices) {
            inputIds[offset + i] = charToId(chars[i])
        }

        val input = arrayOf(inputIds)
        val output = Array(1) { Array(CONTEXT_LEN) { FloatArray(VOCAB_SIZE) } }
        interpreter.run(input, output)
        return output[0][CONTEXT_LEN - 1]
    }

    private fun topKChars(probs: FloatArray, k: Int): List<Pair<Char, Float>> {
        val result = mutableListOf<Pair<Char, Float>>()
        for (id in 0 until VOCAB_SIZE) {
            val c = idToChar(id)
            if (c != '\u0000' && (c.isLetter() || c == '\'')) {
                result.add(c to probs[id])
            }
        }
        return result.sortedByDescending { it.second }.take(k)
    }

    private fun argMaxChar(probs: FloatArray): Char? {
        var bestId = -1
        var bestProb = -1f
        for (id in 0 until VOCAB_SIZE) {
            val c = idToChar(id)
            if (c != '\u0000' && (c.isLetter() || c == '\'') && probs[id] > bestProb) {
                bestProb = probs[id]
                bestId = id
            }
        }
        return if (bestId >= 0) idToChar(bestId) else null
    }

    private fun buildContextTail(textBefore: String, prefix: String): String {
        val trimmed = textBefore.removeSuffix(prefix)
        return trimmed + prefix
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
