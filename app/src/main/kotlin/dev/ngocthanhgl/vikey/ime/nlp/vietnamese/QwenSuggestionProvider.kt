package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.dictionary.DictionaryManager
import dev.ngocthanhgl.vikey.ime.dictionary.UserDictionaryEntry
import dev.ngocthanhgl.vikey.ime.editor.EditorContent
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionProvider
import dev.ngocthanhgl.vikey.ime.nlp.WordSuggestionCandidate
import dev.ngocthanhgl.vikey.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.math.pow

class QwenSuggestionProvider(private val context: Context) : SuggestionProvider {
    init {
        Companion.currentInstance = this
    }

    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.qwen"
        private const val VOCAB_PATH = "ime/dict/vocab.txt"
        private const val MODEL_PATH = "ime/dict/qwen-pruned-50k-q5_0.gguf"
        private const val NGRAM_PATH = "qwen_ngrams.json"
        private const val PERSONAL_DICT = "qwen_personal_dict.json"
        private const val CLEARED_MARKER = ".qwen_cleared"
        private const val NEXT_WORD_POOL = 500
        private const val RERANK_CAP = 10
        private const val BIGRAM_BOOST = 30.0
        private const val TRIGRAM_BOOST = 50.0

        private var currentInstance: QwenSuggestionProvider? = null

        fun setupDownload(context: Context) {
            QwenModelManager.init(context)
            val instance = currentInstance
            QwenModelManager.setOnModelReadyListener {
                instance?.reloadModel()
            }
        }
    }

    private var vocabList = listOf<String>()
    private var vocabSet = setOf<String>()
    private var prefixIndex = mapOf<Char, List<String>>()
    private var commonWords = listOf<String>()
    private var modelPtr = 0L
    private var natLoaded = false
    private var natLoading = false

    private data class PersonalWord(val count: Int, val lastUsedTs: Long)

    private val personalDict = mutableMapOf<String, PersonalWord>()
    private var personalDirty = false
    private var learnCounter = 0

    private var bigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private var trigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private var ngramDirty = false
    private var lastTextLen = 0
    private var pasteUntil = 0L

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val providerId = ProviderId

    override suspend fun create() {
        if (vocabList.isNotEmpty()) return
        withContext(Dispatchers.IO) {
            loadVocab()
            loadPersonalDict()
            loadNgrams()
            try { DictionaryManager.default().loadUserDictionariesIfNecessary() }
            catch (_: Exception) {}
            loadModelBg()
        }
    }

    private fun loadVocab() {
        try {
            val lines = mutableListOf<String>()
            BufferedReader(InputStreamReader(context.assets.open(VOCAB_PATH))).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.trim()?.let { if (it.isNotBlank()) lines.add(it) }
                }
            }
            val clean = lines.filter { w -> w.all { it.isLetter() || it == '\'' } }
            vocabList = clean
            vocabSet = clean.toSet()
            commonWords = clean.take(NEXT_WORD_POOL)
            val idx = mutableMapOf<Char, MutableList<String>>()
            for (word in clean) {
                val c = word.firstOrNull()?.lowercaseChar() ?: continue
                idx.getOrPut(c) { mutableListOf() }.add(word)
            }
            prefixIndex = idx
            flogDebug { "Qwen: loaded ${lines.size} words" }
        } catch (e: Exception) {
            flogDebug { "Qwen: vocab load failed: ${e.message}" }
        }
    }

    private fun loadPersonalDict() {
        try {
            val f = File(context.filesDir, PERSONAL_DICT)
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            for (key in json.keys()) {
                val obj = json.getJSONObject(key)
                personalDict[key] = PersonalWord(
                    count = obj.optInt("c", 1),
                    lastUsedTs = obj.optLong("t", System.currentTimeMillis()),
                )
            }
            flogDebug { "Qwen: loaded ${personalDict.size} personal words" }
        } catch (e: Exception) {
            flogDebug { "Qwen: personal dict load: ${e.message}" }
        }
    }

    private fun savePersonalDict() {
        checkClearedMarker()
        if (!personalDirty) return
        try {
            val json = JSONObject()
            for ((word, pw) in personalDict) {
                val obj = JSONObject()
                obj.put("c", pw.count)
                obj.put("t", pw.lastUsedTs)
                json.put(word, obj)
            }
            File(context.filesDir, PERSONAL_DICT).writeText(json.toString())
            syncRoomDb()
            personalDirty = false
        } catch (e: Exception) {
            flogDebug { "Qwen: personal dict save: ${e.message}" }
        }
    }

    private fun syncRoomDb() {
        try {
            val dm = DictionaryManager.default()
            dm.loadUserDictionariesIfNecessary()
            val dao = dm.florisUserDictionaryDao() ?: return
            for ((word, pw) in personalDict) {
                if (pw.count < 3) continue
                val existing = dao.queryExact(word)
                val freq = pw.count.coerceIn(1, 255)
                if (existing.isNotEmpty()) {
                    dao.update(existing[0].copy(freq = freq))
                } else {
                    dao.insert(UserDictionaryEntry(0, word, freq, null, null))
                }
            }
        } catch (_: Exception) {}
    }

    fun clearAll() {
        personalDict.clear()
        personalDirty = false
        File(context.filesDir, PERSONAL_DICT).delete()
        try {
            DictionaryManager.default().florisUserDictionaryDao()?.deleteAll()
        } catch (_: Exception) {}
    }

    private fun loadNgrams() {
        try {
            val f = File(context.filesDir, NGRAM_PATH)
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            val bi = json.optJSONObject("bigrams")
            if (bi != null) {
                for (k1 in bi.keys()) {
                    val inner = bi.getJSONObject(k1)
                    val map = mutableMapOf<String, Int>()
                    for (k2 in inner.keys()) map[k2] = inner.getInt(k2)
                    bigrams[k1] = map
                }
            }
            val tri = json.optJSONObject("trigrams")
            if (tri != null) {
                for (k1 in tri.keys()) {
                    val inner = tri.getJSONObject(k1)
                    val map = mutableMapOf<String, Int>()
                    for (k2 in inner.keys()) map[k2] = inner.getInt(k2)
                    trigrams[k1] = map
                }
            }
            flogDebug { "Qwen: loaded ${bigrams.size} bigrams, ${trigrams.size} trigrams" }
        } catch (e: Exception) {
            flogDebug { "Qwen: load ngrams: ${e.message}" }
        }
    }

    private fun saveNgrams() {
        if (!ngramDirty) return
        try {
            val bi = JSONObject()
            for ((k1, inner) in bigrams) {
                val jo = JSONObject()
                for ((k2, v) in inner) jo.put(k2, v)
                bi.put(k1, jo)
            }
            val tri = JSONObject()
            for ((k1, inner) in trigrams) {
                val jo = JSONObject()
                for ((k2, v) in inner) jo.put(k2, v)
                tri.put(k1, jo)
            }
            val root = JSONObject()
            root.put("bigrams", bi)
            root.put("trigrams", tri)
            File(context.filesDir, NGRAM_PATH).writeText(root.toString())
            ngramDirty = false
        } catch (e: Exception) {
            flogDebug { "Qwen: save ngrams: ${e.message}" }
        }
    }

    private fun loadModelBg() {
        if (!QwenNatives.isAvailable) {
            flogDebug { "Qwen: native lib not available" }
            return
        }
        natLoading = true
        bgScope.launch {
            try {
                val file = File(context.filesDir, "qwen-pruned-50k-q5_0.gguf")
                if (!file.exists()) {
                    val t0 = System.currentTimeMillis()
                    context.assets.open(MODEL_PATH).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    flogDebug { "Qwen: GGUF copy ${System.currentTimeMillis() - t0}ms" }
                }
                if (file.exists() && file.length() > 0) {
                    val t0 = System.currentTimeMillis()
                    modelPtr = QwenNatives.open(file.absolutePath)
                    natLoaded = modelPtr != 0L
                    flogDebug { "Qwen: load ptr=$modelPtr ${System.currentTimeMillis() - t0}ms" }
                }
            } catch (e: Exception) {
                flogDebug { "Qwen: model load failed: ${e.message}" }
            }
            natLoading = false
        }
    }

    override suspend fun preload(subtype: Subtype) {
        if (vocabList.isEmpty()) create()
    }

    fun reloadModel() {
        if (modelPtr != 0L) return
        if (natLoading) return
        if (!QwenNatives.isAvailable) return
        loadModelBg()
    }

    private fun checkClearedMarker() {
        val f = File(context.filesDir, CLEARED_MARKER)
        if (!f.exists()) return
        f.delete()
        personalDict.clear()
        personalDirty = false
        File(context.filesDir, PERSONAL_DICT).delete()
        try { DictionaryManager.default().florisUserDictionaryDao()?.deleteAll() }
        catch (_: Exception) {}
        flogDebug { "Qwen: processed .cleared marker" }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        if (vocabList.isEmpty()) return emptyList()
        checkClearedMarker()
        return withContext(Dispatchers.Default) {
            try {
                val textBefore = content.textBeforeSelection
                if (textBefore.isBlank()) return@withContext emptyList()
                val now = System.currentTimeMillis()
                if (textBefore.length > lastTextLen + 1) pasteUntil = now + 500
                lastTextLen = textBefore.length
                val lastChar = textBefore.last()
                if (lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '\n')
                    return@withContext emptyList()

                learnFromText(textBefore)

                val pairs = if (lastChar == ' ' || lastChar == '\t') {
                    val words = textBefore.trimEnd().split(Regex("\\s+")).filter { it.isNotBlank() }
                    val lastWord = if (words.isNotEmpty()) words.last() else ""
                    if (lastWord.isBlank()) return@withContext emptyList()
                    if (now >= pasteUntil) recordWord(lastWord)
                    suggestNextWord(textBefore.trimEnd(), maxCandidateCount)
                } else {
                    val cur = getCurrentWord(content) ?: return@withContext emptyList()
                    if (cur.isBlank()) return@withContext emptyList()
                    completeCurrentWord(cur, maxCandidateCount)
                }

                pairs.map { (word, _) ->
                    WordSuggestionCandidate(
                        text = word,
                        confidence = 1.0,
                        isEligibleForAutoCommit = false,
                        sourceProvider = this@QwenSuggestionProvider,
                    )
                }
            } catch (e: Exception) {
                flogDebug { "Qwen:suggest failed: ${e.message}" }
                emptyList()
            }
        }
    }

    private fun learnFromText(text: CharSequence) {
        if (System.currentTimeMillis() < pasteUntil) return
        learnCounter++
        if (learnCounter % 3 != 0) return
        val words = text.trimEnd().split(Regex("\\s+"))
            .map { it.lowercase().trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>') }
            .filter { it.length >= 2 }
        if (words.size < 2) return
        val recent = words.takeLast(8)

        for (i in 0 until recent.size - 1) {
            val w1 = recent[i]
            val w2 = recent[i + 1]
            val bi = bigrams.getOrPut(w1) { mutableMapOf() }
            bi[w2] = (bi[w2] ?: 0).coerceAtMost(254) + 1
            if (i + 2 < recent.size) {
                val w3 = recent[i + 2]
                val tri = trigrams.getOrPut("$w1|$w2") { mutableMapOf() }
                tri[w3] = (tri[w3] ?: 0).coerceAtMost(254) + 1
            }
        }
        ngramDirty = true
        if (ngramDirty && learnCounter % 30 == 0) saveNgrams()
    }

    private fun isNoise(w: String): Boolean {
        if (w.length < 2 || w.length > 30) return true
        if (w.contains("@")) return true
        if (w.contains("://") || w.startsWith("www")) return true
        if (w.count { it.isDigit() } > w.length / 2) return true
        if (!w.all { it.isLetter() || it == '\'' }) return true
        return false
    }

    private fun recordWord(raw: String) {
        val lc = raw.lowercase().trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>')
        if (isNoise(lc)) return
        val existing = personalDict[lc]
        val newCount = (existing?.count ?: 0) + 1
        personalDict[lc] = PersonalWord(count = newCount, lastUsedTs = System.currentTimeMillis())
        if (existing != null || newCount >= 3) personalDirty = true
    }

    private fun computeAlpha(decayedCount: Double): Double = when {
        decayedCount < 3.0 -> 0.10
        decayedCount < 10.0 -> 0.25
        decayedCount < 30.0 -> 0.40
        else -> 0.50
    }

    private fun decayedCount(pw: PersonalWord): Double {
        val daysSince = (System.currentTimeMillis() - pw.lastUsedTs) / 86400000.0
        return pw.count * 0.95.pow(daysSince)
    }

    private fun personalScore(pw: PersonalWord): Double =
        (decayedCount(pw) / 50.0).coerceIn(0.0, 1.0)

    private fun rerankWithPersonal(candidates: List<Pair<String, Double>>): List<Pair<String, Double>> {
        if (personalDict.isEmpty()) return candidates
        val rawScores = candidates.map { it.second }
        val minScore = rawScores.min()
        val maxScore = rawScores.max()
        val range = maxScore - minScore
        return candidates.map { (word, baseScore) ->
            val normBase = if (range > 0.0) (baseScore - minScore) / range else 0.5
            val pw = personalDict[word.lowercase()]
            if (pw != null && pw.count >= 3) {
                val dc = decayedCount(pw)
                val alpha = computeAlpha(dc)
                val ps = personalScore(pw)
                word to (alpha * ps + (1.0 - alpha) * normBase)
            } else {
                word to normBase
            }
        }.sortedByDescending { it.second }
    }

    private fun suggestNextWord(textBefore: String, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val words = textBefore.split(Regex("\\s+")).filter { it.isNotBlank() }
        val w2 = if (words.isNotEmpty()) words.last().lowercase() else ""
        val w1 = if (words.size >= 2) words[words.size - 2].lowercase() else null

        val scored = mutableMapOf<String, Double>()

        if (w1 != null) {
            trigrams["$w1|$w2"]?.forEach { (w3, freq) ->
                scored[w3] = (scored[w3] ?: 0.0) + freq * TRIGRAM_BOOST
            }
        }

        bigrams[w2]?.forEach { (next, freq) ->
            if (next !in scored) {
                scored[next] = (scored[next] ?: 0.0) + freq * BIGRAM_BOOST
            }
        }

        val pool = commonWords
        if (!natLoading && natLoaded && modelPtr != 0L) {
            val predictions = QwenNatives.predictNext(modelPtr, textBefore, limit * 3)
            if (predictions != null) {
                val firstBatch = predictions.take(limit * 2)
                val startScore = firstBatch.size.toDouble()
                for ((idx, word) in firstBatch.withIndex()) {
                    val lc = word.lowercase()
                    if (lc in vocabSet && lc !in scored) {
                        scored[lc] = (scored[lc] ?: 0.0) + (startScore - idx) * 2.0
                    }
                }
            }
        }

        for (word in pool) {
            if (word !in scored) {
                scored[word] = (scored[word] ?: 0.0) + freqScore(word)
            }
        }

        val topBase = scored.entries.sortedByDescending { it.value }
            .take(RERANK_CAP).map { it.key to it.value }

        return rerankWithPersonal(topBase).take(limit)
    }

    private fun completeCurrentWord(prefix: String, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val firstChar = prefix.first().lowercaseChar()

        val baseCandidates = prefixIndex[firstChar]
            ?.filter { it.startsWith(prefix, ignoreCase = true) }
            ?: emptyList()

        val topBase = if (baseCandidates.isNotEmpty()) {
            val scored = if (!natLoading && natLoaded && modelPtr != 0L) {
                val scores = QwenNatives.scoreCandidates(modelPtr, null, baseCandidates.toTypedArray())
                if (scores != null && scores.size == baseCandidates.size) {
                    baseCandidates.indices.map { baseCandidates[it] to scores[it].toDouble() }
                } else {
                    baseCandidates.map { it to freqScore(it) }
                }
            } else {
                baseCandidates.map { it to freqScore(it) }
            }
            scored.sortedByDescending { it.second }.take(RERANK_CAP)
        } else {
            emptyList()
        }

        val oovCandidates = personalDict.filter { it.value.count >= 3 }
            .keys
            .filter { it.startsWith(prefix, ignoreCase = true) && it !in vocabSet && !isNoise(it) }
            .map { it to 0.0 }

        val merged = (oovCandidates + topBase).distinctBy { it.first.lowercase() }
            .take(limit + oovCandidates.size)

        val reranked = rerankWithPersonal(merged)

        val useUpper = prefix.length > 1 && prefix.all { it.isUpperCase() }
        val useTitle = !useUpper && prefix[0].isUpperCase()

        return reranked.take(limit).map { (word, score) ->
            val cased = when {
                useUpper -> word.uppercase()
                useTitle -> word.replaceFirstChar { it.uppercase() }
                else -> word
            }
            cased to score
        }
    }

    private fun freqScore(word: String): Double {
        val idx = vocabList.indexOf(word)
        return if (idx >= 0) (vocabList.size - idx).toDouble() else 0.0
    }

    private fun getCurrentWord(content: EditorContent): String? {
        content.composingText.let { if (it.isNotBlank()) return it.toString() }
        content.currentWordText.let { if (it.isNotBlank()) return it.toString() }
        return null
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.toString().lowercase().trim()
        if (word.length < 2) return
        recordWord(word)
        bgScope.launch { savePersonalDict() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> = vocabList

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val idx = vocabList.indexOf(word)
        return if (idx >= 0) (vocabList.size - idx).toDouble() / vocabList.size else 0.0
    }

    override suspend fun destroy() {
        if (personalDirty) savePersonalDict()
        if (ngramDirty) saveNgrams()
        if (modelPtr != 0L) QwenNatives.close(modelPtr)
        modelPtr = 0L
        natLoaded = false
    }
}
