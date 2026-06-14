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

class KenlmSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.kenlm"
        private const val VOCAB_PATH = "ime/dict/vocab.txt"
        private const val MODEL_PATH = "ime/dict/ime_lm.klm"
        private const val NGRAM_PATH = "ngrams.json"
        private const val NEXT_WORD_POOL = 500
        private const val LEARN_BOOST = 50.0
        private const val BIGRAM_BOOST = 30.0
        private const val TRIGRAM_BOOST = 50.0
    }

    private var vocabList = listOf<String>()
    private var prefixIndex = mapOf<Char, List<String>>()
    private var commonWords = listOf<String>()
    private var modelPtr = 0L
    private var natLoaded = false
    private var natLoading = false

    private var userWords = mutableMapOf<String, Int>()
    private var userDirty = false
    private var learnCounter = 0

    private var bigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private var trigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private var ngramDirty = false

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val providerId = ProviderId

    override suspend fun create() {
        if (vocabList.isNotEmpty()) return
        withContext(Dispatchers.IO) {
            loadVocab()
            loadUserDict()
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
            vocabList = lines
            commonWords = lines.take(NEXT_WORD_POOL)
            val idx = mutableMapOf<Char, MutableList<String>>()
            for (word in lines) {
                val c = word.firstOrNull()?.lowercaseChar() ?: continue
                idx.getOrPut(c) { mutableListOf() }.add(word)
            }
            prefixIndex = idx
            flogDebug { "KenLM: loaded ${lines.size} words" }
        } catch (e: Exception) {
            flogDebug { "KenLM: vocab load failed: ${e.message}" }
        }
    }

    private fun loadUserDict() {
        try {
            val dm = DictionaryManager.default()
            dm.loadUserDictionariesIfNecessary()
            val dao = dm.florisUserDictionaryDao()
            if (dao != null) {
                val entries = dao.queryAll()
                for (e in entries) {
                    if (e.word.isNotBlank()) userWords[e.word.lowercase()] = e.freq.coerceIn(1, 255)
                }
                flogDebug { "KenLM: loaded ${userWords.size} learned words from Room DB" }
                return
            }
        } catch (_: Exception) {}
        try {
            val f = File(context.filesDir, "user_words.json")
            if (f.exists()) {
                val raw = f.readText()
                val json = JSONObject(raw)
                for (key in json.keys()) {
                    userWords[key] = json.getInt(key)
                }
                flogDebug { "KenLM: loaded ${userWords.size} learned words from JSON fallback" }
            }
        } catch (e: Exception) {
            flogDebug { "KenLM: user dict load: ${e.message}" }
        }
    }

    private fun saveUserDict() {
        if (!userDirty) return
        try {
            val dm = DictionaryManager.default()
            dm.loadUserDictionariesIfNecessary()
            val dao = dm.florisUserDictionaryDao()
            if (dao != null) {
                for ((word, freq) in userWords) {
                    val existing = dao.queryExact(word)
                    if (existing.isNotEmpty()) {
                        dao.update(existing[0].copy(freq = freq))
                    } else {
                        dao.insert(UserDictionaryEntry(0, word, freq, null, null))
                    }
                }
                userDirty = false
                flogDebug { "KenLM: saved ${userWords.size} words to Room DB" }
                return
            }
        } catch (_: Exception) {}
        try {
            val json = JSONObject(userWords).toString()
            File(context.filesDir, "user_words.json").writeText(json)
            userDirty = false
        } catch (e: Exception) {
            flogDebug { "KenLM: user dict save: ${e.message}" }
        }
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
            flogDebug { "KenLM: loaded ${bigrams.size} bigrams, ${trigrams.size} trigrams" }
        } catch (e: Exception) {
            flogDebug { "KenLM: load ngrams: ${e.message}" }
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
            flogDebug { "KenLM: save ngrams: ${e.message}" }
        }
    }

    private fun loadModelBg() {
        if (!KenlmNatives.isAvailable) {
            flogDebug { "KenLM: native lib not available" }
            return
        }
        natLoading = true
        bgScope.launch {
            try {
                val file = File(context.filesDir, "ime_lm.klm")
                if (!file.exists()) {
                    val t0 = System.currentTimeMillis()
                    context.assets.open(MODEL_PATH).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    flogDebug { "KenLM: KLM copy ${System.currentTimeMillis() - t0}ms" }
                }
                if (file.exists() && file.length() > 0) {
                    val t0 = System.currentTimeMillis()
                    modelPtr = KenlmNatives.loadModel(file.absolutePath)
                    natLoaded = modelPtr != 0L
                    flogDebug { "KenLM: load ptr=$modelPtr ${System.currentTimeMillis() - t0}ms" }
                }
            } catch (e: Exception) {
                flogDebug { "KenLM: model load failed: ${e.message}" }
            }
            natLoading = false
        }
    }

    override suspend fun preload(subtype: Subtype) {
        if (vocabList.isEmpty()) create()
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        if (vocabList.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            try {
                val textBefore = content.textBeforeSelection
                if (textBefore.isBlank()) return@withContext emptyList()
                val lastChar = textBefore.last()
                if (lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '\n')
                    return@withContext emptyList()

                learnFromText(textBefore)

                val pairs = if (lastChar == ' ' || lastChar == '\t') {
                    val words = textBefore.trimEnd().split(Regex("\\s+")).filter { it.isNotBlank() }
                    val lastWord = if (words.isNotEmpty()) words.last() else ""
                    if (lastWord.isBlank()) return@withContext emptyList()
                    suggestNextWord(textBefore.trimEnd(), maxCandidateCount)
                } else {
                    val cur = getCurrentWord(content) ?: return@withContext emptyList()
                    if (cur.isBlank()) return@withContext emptyList()
                    completeCurrentWord(cur, maxCandidateCount)
                }

                boostLearned(pairs).map { (word, _) ->
                    WordSuggestionCandidate(
                        text = word,
                        confidence = 1.0,
                        isEligibleForAutoCommit = false,
                        sourceProvider = this@KenlmSuggestionProvider,
                    )
                }
            } catch (e: Exception) {
                flogDebug { "KenLM:suggest failed: ${e.message}" }
                emptyList()
            }
        }
    }

    private fun learnFromText(text: CharSequence) {
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

        for (w in recent) {
            if (w !in userWords && w !in vocabList) continue
            val prev = userWords[w] ?: 0
            userWords[w] = (prev + 1).coerceAtMost(255)
            userDirty = true
        }

        if (userDirty && learnCounter % 30 == 0) saveUserDict()
        if (ngramDirty && learnCounter % 30 == 0) saveNgrams()
    }

    private fun boostLearned(pairs: List<Pair<String, Double>>): List<Pair<String, Double>> {
        if (userWords.isEmpty()) return pairs
        return pairs.map { (word, score) ->
            val boost = userWords[word.lowercase()]?.toDouble()?.times(LEARN_BOOST) ?: 0.0
            word to (score + boost)
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
            val scores = KenlmNatives.scoreCandidates(modelPtr, w2, pool.toTypedArray())
            if (scores != null && scores.size == pool.size) {
                for (i in pool.indices) {
                    val word = pool[i]
                    if (word !in scored) {
                        scored[word] = (scored[word] ?: 0.0) + scores[i].toDouble()
                    }
                }
            } else {
                for (word in pool) {
                    if (word !in scored) scored[word] = (scored[word] ?: 0.0) + freqScore(word)
                }
            }
        } else {
            for (word in pool) {
                if (word !in scored) scored[word] = (scored[word] ?: 0.0) + freqScore(word)
            }
        }

        return scored.entries.sortedByDescending { it.value }.take(limit)
            .map { it.key to it.value }
    }

    private fun completeCurrentWord(prefix: String, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val firstChar = prefix.first().lowercaseChar()
        val candidates = prefixIndex[firstChar]
            ?.filter { it.startsWith(prefix, ignoreCase = true) }
            ?: return emptyList()
        if (candidates.isEmpty()) return emptyList()

        val scored = if (!natLoading && natLoaded && modelPtr != 0L && candidates.size <= 200) {
            val scores = KenlmNatives.scoreCandidates(modelPtr, null, candidates.toTypedArray())
            if (scores != null && scores.size == candidates.size) {
                candidates.indices.map { candidates[it] to scores[it].toDouble() }
            } else {
                candidates.map { it to freqScore(it) }
            }
        } else {
            candidates.map { it to freqScore(it) }
        }

        val useUpper = prefix.length > 1 && prefix.all { it.isUpperCase() }
        val useTitle = !useUpper && prefix[0].isUpperCase()

        return scored.sortedByDescending { it.second }.take(limit).map { (word, score) ->
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
        if (word.length < 3) return
        val prev = userWords[word] ?: 0
        userWords[word] = (prev + 5).coerceAtMost(255)
        userDirty = true
        bgScope.launch { saveUserDict() }
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
        if (userDirty) saveUserDict()
        if (ngramDirty) saveNgrams()
        if (modelPtr != 0L) KenlmNatives.unloadModel(modelPtr)
        modelPtr = 0L
        natLoaded = false
    }
}
