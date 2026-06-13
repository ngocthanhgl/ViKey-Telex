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
import java.util.PriorityQueue

/**
 * Char-level CIFG model (Zalo vikey_cifg_int8).
 * Input:  30 Unicode codepoints (right-aligned, pad=0, unk=1)
 * Output: [1, 30, 15000] softmax — next-char probability at each position
 */
class TfLiteSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.tflite"
        private const val MODEL_PATH = "ime/dict/vikey_cifg_int8.tflite"
        private const val TOKENIZER_PATH = "ime/dict/tokenizer.json"
        private const val CONTEXT_LEN = 30
        private const val VOCAB_SIZE = 15000
    }

    private var interpreter: Interpreter? = null
    private var prefixIndex: Map<Char, List<String>> = emptyMap()
    private var loaded = false
    private var lastContextStr: String? = null
    private var lastProbs: FloatArray? = null

    override val providerId = ProviderId

    override suspend fun create() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            try {
                loadVocabulary()

                val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(modelBytes.size)
                buffer.order(ByteOrder.nativeOrder())
                buffer.put(modelBytes)
                interpreter = Interpreter(buffer)

                predictRaw(interpreter!!, "")
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
        return withContext(Dispatchers.Default) {
            try {
                val textBefore = content.textBeforeSelection
                val isNewWord = textBefore.lastOrNull()
                    ?.let { it == ' ' || it == '\n' || it == '\t' } ?: true

                if (isNewWord) {
                    val ctx = textBefore.trimEnd().takeLast(CONTEXT_LEN)
                    val probs = getOrPredict(interp, ctx)
                    suggestNextWord(probs, maxCandidateCount)
                } else {
                    val cur = getCurrentWord(content) ?: return@withContext emptyList()
                    if (cur.isBlank()) return@withContext emptyList()
                    val ctx = textBefore.removeSuffix(cur)
                    val contextStr = (ctx + cur).takeLast(CONTEXT_LEN)
                    val probs = getOrPredict(interp, contextStr)
                    completeCurrentWord(probs, cur, maxCandidateCount)
                }
            } catch (e: Exception) {
                flogDebug { "TFLite:suggest failed: ${e.message}" }
                emptyList()
            }
        }.map { (word, _) ->
            WordSuggestionCandidate(
                text = word,
                confidence = 1.0,
                isEligibleForAutoCommit = false,
                sourceProvider = this@TfLiteSuggestionProvider,
            )
        }
    }

    private fun getOrPredict(interp: Interpreter, contextStr: String): FloatArray {
        if (contextStr == lastContextStr && lastProbs != null) return lastProbs!!
        val probs = predictRaw(interp, contextStr)
        lastContextStr = contextStr
        lastProbs = probs
        return probs
    }

    private fun predictRaw(interp: Interpreter, contextStr: String): FloatArray {
        val inputIds = IntArray(CONTEXT_LEN) { 0 }
        val chars = contextStr.takeLast(CONTEXT_LEN)
        val offset = CONTEXT_LEN - chars.length
        for (i in chars.indices) {
            inputIds[offset + i] = charToId(chars[i])
        }
        val input = arrayOf(inputIds)
        val output = Array(1) { Array(CONTEXT_LEN) { FloatArray(VOCAB_SIZE) } }
        interp.run(input, output)
        return output[0][CONTEXT_LEN - 1].copyOf()
    }

    private fun charToId(c: Char): Int {
        val cp = c.code
        if (cp == '\n'.code) return ' '.code
        return if (cp in 32 until VOCAB_SIZE) cp else 1
    }

    private fun suggestNextWord(probs: FloatArray, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val charPQ = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
        for (cid in 2 until VOCAB_SIZE) {
            val c = cid.toChar()
            if (!c.isLetter()) continue
            val p = probs[cid]
            if (charPQ.size < 20) {
                charPQ.add(cid to p)
            } else if (p > charPQ.peek().second) {
                charPQ.poll()
                charPQ.add(cid to p)
            }
        }

        val topChars = mutableListOf<Pair<Int, Float>>()
        while (charPQ.isNotEmpty()) topChars.add(charPQ.poll())
        topChars.reverse()

        val wordPQ = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        val seen = mutableSetOf<String>()
        for ((cid, _) in topChars) {
            val candidates = prefixIndex[cid.toChar().lowercaseChar()] ?: continue
            val firstCharP = probs[cid].toDouble().coerceAtLeast(0.0)
            for (word in candidates) {
                if (word in seen) continue
                seen.add(word)
                if (wordPQ.size < limit) {
                    wordPQ.add(word to firstCharP)
                } else if (firstCharP > wordPQ.peek().second) {
                    wordPQ.poll()
                    wordPQ.add(word to firstCharP)
                }
            }
        }

        val result = mutableListOf<Pair<String, Double>>()
        while (wordPQ.isNotEmpty()) result.add(wordPQ.poll())
        result.reverse()
        return result
    }

    private fun completeCurrentWord(
        probs: FloatArray,
        prefix: String,
        k: Int,
    ): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val firstChar = prefix.first().lowercaseChar()
        val candidates = prefixIndex[firstChar]
            ?.filter { it.startsWith(prefix, ignoreCase = true) }
            ?: return emptyList()

        if (candidates.isEmpty()) return emptyList()

        val pq = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        for (word in candidates) {
            val nextIdx = if (word.length > prefix.length) charToId(word[prefix.length]) else -1
            val p = if (nextIdx >= 0) probs[nextIdx].toDouble().coerceAtLeast(0.0) else 0.0
            if (pq.size < limit) {
                pq.add(word to p)
            } else if (p > pq.peek().second) {
                pq.poll()
                pq.add(word to p)
            }
        }

        val result = mutableListOf<Pair<String, Double>>()
        while (pq.isNotEmpty()) result.add(pq.poll())
        result.reverse()
        return result
    }

    private fun loadVocabulary() {
        val text = BufferedReader(InputStreamReader(context.assets.open(TOKENIZER_PATH)))
            .use { it.readText() }
        val json = JSONObject(text)
        val w2iObj = json.getJSONObject("w2i")
        val map = mutableMapOf<Char, MutableList<String>>()
        for (key in w2iObj.keys()) {
            if (key.startsWith("<")) continue
            val c = key.firstOrNull() ?: continue
            map.getOrPut(c.lowercaseChar()) { mutableListOf() }.add(key)
        }
        prefixIndex = map
    }

    private fun getCurrentWord(content: EditorContent): String? {
        content.composingText.let { if (it.isNotBlank()) return it.toString() }
        content.currentWordText.let { if (it.isNotBlank()) return it.toString() }
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
