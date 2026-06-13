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
 * Word-level CIFG language model (vikey_cifg_int8).
 * Input:  [1, 50] token IDs (right-padded, bos=1, pad=0)
 * Output: [1, 50, vocab] softmax — next-word probability at each position
 */
class TfLiteSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.tflite"
        private const val MODEL_PATH = "ime/dict/vikey_cifg_int8.tflite"
        private const val TOKENIZER_PATH = "ime/dict/tokenizer.json"
        private const val SEQ_LEN = 50
    }

    private var interpreter: Interpreter? = null
    private var vocabSize = 0
    private var idx2word: Map<Int, String> = emptyMap()
    private var word2idx: Map<String, Int> = emptyMap()
    private var loaded = false
    private var lastInputHash: Int? = null
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

                val dummy = IntArray(SEQ_LEN) { 1 }
                predictRaw(interpreter!!, dummy)
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
                    val tokenIds = tokenize(textBefore.trimEnd())
                    val probs = getOrPredict(interp, tokenIds)
                    suggestNextWord(probs, maxCandidateCount)
                } else {
                    val cur = getCurrentWord(content) ?: return@withContext emptyList()
                    if (cur.isBlank()) return@withContext emptyList()
                    val ctx = textBefore.removeSuffix(cur)
                    val tokenIds = tokenize(ctx)
                    val probs = getOrPredict(interp, tokenIds)
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

    private fun getOrPredict(interp: Interpreter, tokenIds: List<Int>): FloatArray {
        val h = tokenIds.hashCode()
        if (h == lastInputHash && lastProbs != null) return lastProbs!!
        val probs = predictRaw(interp, tokenIds.toIntArray())
        lastInputHash = h
        lastProbs = probs
        return probs
    }

    private fun tokenize(text: String): List<Int> {
        val words = text.trim().lowercase()
            .replace(Regex("[^\\w\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val oov = word2idx["<oov>"] ?: 3
        return words.map { word2idx[it] ?: oov }
    }

    private fun predictRaw(interp: Interpreter, tokenIds: IntArray): FloatArray {
        val bos = word2idx["<bos>"] ?: 1
        val padded = IntArray(SEQ_LEN) { bos }
        val offset = SEQ_LEN - tokenIds.size.coerceAtMost(SEQ_LEN)
        for (i in tokenIds.indices) {
            if (offset + i >= SEQ_LEN) break
            padded[offset + i] = tokenIds[i]
        }
        val input = arrayOf(padded)
        val output = Array(1) { Array(SEQ_LEN) { FloatArray(vocabSize) } }
        interp.run(input, output)
        return output[0][SEQ_LEN - 1].copyOf()
    }

    private fun suggestNextWord(probs: FloatArray, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val pq = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
        for (id in 4 until vocabSize) {
            val p = probs[id]
            if (pq.size < limit) {
                pq.add(id to p)
            } else if (p > pq.peek().second) {
                pq.poll()
                pq.add(id to p)
            }
        }
        val result = mutableListOf<Pair<String, Double>>()
        while (pq.isNotEmpty()) result.add(pq.poll())
        result.reverse()
        return result.map { (id, _) -> idx2word[id] ?: "" to 1.0 }
            .filter { it.first.isNotBlank() }
    }

    private fun completeCurrentWord(probs: FloatArray, prefix: String, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val lower = prefix.lowercase()
        val pq = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        for (id in 4 until vocabSize) {
            val word = idx2word[id] ?: continue
            if (!word.startsWith(lower)) continue
            val p = probs[id].toDouble().coerceAtLeast(0.0)
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
        val i2wObj = json.getJSONObject("i2w")

        val w2i = mutableMapOf<String, Int>()
        for (key in w2iObj.keys()) {
            w2i[key] = w2iObj.getInt(key)
        }
        word2idx = w2i
        vocabSize = w2i.size

        val i2w = mutableMapOf<Int, String>()
        for (key in i2wObj.keys()) {
            i2w[key.toIntOrNull() ?: continue] = i2wObj.getString(key)
        }
        idx2word = i2w
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
