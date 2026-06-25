package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import android.util.LruCache
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.pow

class QwenSuggestionProvider(private val context: Context) : SuggestionProvider {
    private val prefs by FlorisPreferenceStore
    private var autocorrectEngine: AutocorrectEngine? = null
    private var typoDetector: TypoDetector? = null

    init {
        Companion.currentInstance = this
    }

    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.qwen"
        private const val NGRAM_PATH = "qwen_ngrams.json"
        private const val PERSONAL_DICT = "qwen_personal_dict.json"
        private const val DISCOURSE_PATH = "qwen_discourse.json"
        private const val CLEARED_MARKER = ".qwen_cleared"
        private const val BIGRAM_BOOST = 5.0
        private const val TRIGRAM_BOOST = 3.0
        private const val SEED_WORDS = "ime/dict/vi.json"
        private const val PHRASES_PATH = "ime/dict/phrases.json"

        private var currentInstance: QwenSuggestionProvider? = null

        fun recase(word: String, shiftState: dev.ngocthanhgl.vikey.ime.input.InputShiftState?): String {
            return when (shiftState) {
                dev.ngocthanhgl.vikey.ime.input.InputShiftState.CAPS_LOCK -> word.uppercase()
                dev.ngocthanhgl.vikey.ime.input.InputShiftState.SHIFTED_MANUAL,
                dev.ngocthanhgl.vikey.ime.input.InputShiftState.SHIFTED_AUTOMATIC -> word.replaceFirstChar { it.uppercase() }
                null, dev.ngocthanhgl.vikey.ime.input.InputShiftState.UNSHIFTED -> word.lowercase()
                else -> word
            }
        }

        fun getInstance(): QwenSuggestionProvider? = currentInstance
    }

    private var modelPtr = 0L
    private var natLoaded = false
    private var natLoading = false

    private data class PersonalWord(val count: Int, val lastUsedTs: Long)
    private data class DampedWord(val dampCount: Int = 0, val lastDampedTs: Long = 0)

    private val personalDict = mutableMapOf<String, PersonalWord>()
    private val dampedWords = mutableMapOf<String, DampedWord>()
    private var personalDirty = false
    private var learnCounter = 0
    private var lastTopSuggestion: String? = null

    private val seedWords = mutableSetOf<String>()
    private var prefixTrie: Map<String, List<String>> = mapOf()
    private var useTrie = false
    private var bigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private var trigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private var ngramDirty = false
    private var lastTextLen = 0
    private var pasteUntil = 0L
    private val discourseBuffer = mutableListOf<String>()
    private val phraseMap = mutableMapOf<String, List<String>>()
    private val suggestionCache = LruCache<String, List<Pair<String, Double>>>(50)

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val providerId = ProviderId

    override suspend fun create() {
        withContext(Dispatchers.IO) {
            loadSeedWords()
            loadPersonalDict()
            loadNgrams()
            loadDiscourseBuffer()
            try { DictionaryManager.default().loadUserDictionariesIfNecessary() }
            catch (_: Exception) {}
            loadModelBg()
        }
        startPeriodicSave()
    }

    private fun startPeriodicSave() {
        bgScope.launch {
            while (true) {
                delay(30_000)
                if (personalDirty) savePersonalDict()
                if (ngramDirty) saveNgrams()
            }
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
                val dc = obj.optInt("d", 0)
                if (dc > 0) {
                    dampedWords[key] = DampedWord(
                        dampCount = dc,
                        lastDampedTs = obj.optLong("dt", System.currentTimeMillis()),
                    )
                }
            }
            flogDebug { "Qwen: loaded ${personalDict.size} personal words, ${dampedWords.size} damped" }
        } catch (e: Exception) {
            flogDebug { "Qwen: personal dict load: ${e.message}" }
        }
    }

    private fun loadSeedWords() {
        try {
            val raw = context.assets.open(SEED_WORDS).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val w = key.lowercase()
                if (w.isNotEmpty() && w.none { it.isWhitespace() } && w.all { it.isLetter() || it == '\'' }) {
                    seedWords.add(w)
                }
            }
            flogDebug { "Qwen: loaded ${seedWords.size} seed words" }
            val trie = mutableMapOf<String, MutableList<String>>()
            for (word in seedWords) {
                for (i in 1..word.length.coerceAtMost(6)) {
                    val p = word.take(i)
                    trie.getOrPut(p) { mutableListOf() }.add(word)
                }
            }
            prefixTrie = trie
            useTrie = true
            autocorrectEngine = AutocorrectEngine(seedWords)
            typoDetector = TypoDetector(seedWords)
            loadPhrases()
        } catch (e: Exception) {
            flogDebug { "Qwen: seed words load: ${e.message}" }
        }
    }

    private fun loadPhrases() {
        try {
            val raw = context.assets.open(PHRASES_PATH).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val arr = json.getJSONArray(key)
                val phrases = mutableListOf<String>()
                for (i in 0 until arr.length()) phrases.add(arr.getString(i))
                phraseMap[key.lowercase()] = phrases
            }
            flogDebug { "Qwen: loaded ${phraseMap.size} phrase entries" }
        } catch (e: Exception) {
            flogDebug { "Qwen: phrases load: ${e.message}" }
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
                val dw = dampedWords[word]
                if (dw != null && dw.dampCount > 0) {
                    obj.put("d", dw.dampCount)
                    obj.put("dt", dw.lastDampedTs)
                }
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

    private fun loadDiscourseBuffer() {
        try {
            val f = File(context.filesDir, DISCOURSE_PATH)
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            val words = json.optJSONArray("words")
            if (words != null) {
                discourseBuffer.clear()
                for (i in 0 until words.length()) {
                    discourseBuffer.add(words.getString(i))
                }
            }
        } catch (e: Exception) {
            flogDebug { "Qwen: discourse load: ${e.message}" }
        }
    }

    private fun saveDiscourseBuffer() {
        try {
            val json = JSONObject()
            val arr = org.json.JSONArray(discourseBuffer)
            json.put("words", arr)
            File(context.filesDir, DISCOURSE_PATH).writeText(json.toString())
        } catch (e: Exception) {
            flogDebug { "Qwen: discourse save: ${e.message}" }
        }
    }

    private fun modelFile(): File? {
        val dir = context.filesDir
        return dir.listFiles { f -> f.extension == "gguf" && f.length() > 0 }
            ?.maxByOrNull { it.length() }
    }

    fun getModelName(): String? = modelFile()?.name

    private fun loadModelBg() {
        if (!QwenNatives.isAvailable) {
            flogDebug { "Qwen: native lib not available" }
            return
        }
        if (natLoading || natLoaded) return
        natLoading = true
        bgScope.launch {
            try {
                val file = modelFile()
                if (file != null) {
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
        create()
    }

    fun reloadModel() {
        if (natLoading || natLoaded) return
        if (!QwenNatives.isAvailable) return
        loadModelBg()
    }

    fun removeModel() {
        if (modelPtr != 0L) {
            QwenNatives.close(modelPtr)
            modelPtr = 0L
        }
        natLoaded = false
        natLoading = false
        modelFile()?.delete()
        flogDebug { "Qwen: model removed" }
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

    private fun resolveTelexPrefix(raw: String): String {
        var s = raw.lowercase()
        s = s.replace("dd", "đ").replace("aa", "â").replace("aw", "ă")
        s = s.replace("ee", "ê").replace("oo", "ô").replace("ow", "ơ").replace("uw", "ư")
        s = s.replace("uow", "ươ")
        if (s.isNotEmpty()) {
            val last = s.last()
            if (last in setOf('s', 'f', 'r', 'x', 'j') && s.length > 1) {
                val base = s.dropLast(1)
                if (base.any { it in "aeiouyăâêôơ" }) return base
            }
        }
        return s
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        checkClearedMarker()
        if (isPrivateSession) return emptyList()
        return withContext(Dispatchers.Default) {
            try {
                val textBefore = content.textBeforeSelection
                if (textBefore.isBlank()) return@withContext emptyList()
                val now = System.currentTimeMillis()
                if (textBefore.length > lastTextLen + 1) pasteUntil = now + 500
                lastTextLen = textBefore.length
                val lastChar = textBefore.last()
                if (lastChar == '\n') return@withContext emptyList()
                if (lastChar == '.' || lastChar == '?' || lastChar == '!') {
                    val words = textBefore.trimEnd().split(Regex("\\s+")).filter { it.isNotBlank() }
                    discourseBuffer.clear()
                    discourseBuffer.addAll(words.takeLast(5))
                    return@withContext emptyList()
                }

                bgScope.launch { learnFromText(textBefore) }

                var autoCommitWord: String? = null

                val pairs = if (lastChar == ' ' || lastChar == '\t') {
                    val words = textBefore.trimEnd().split(Regex("\\s+")).filter { it.isNotBlank() }
                    val lastWord = if (words.isNotEmpty()) words.last() else ""
                    if (lastWord.isBlank()) return@withContext emptyList()
                    if (now >= pasteUntil) {
                        recordWord(lastWord)
                        val lastTop = lastTopSuggestion
                        if (lastTop != null && lastWord.lowercase() != lastTop) {
                            dampWord(lastTop)
                        }
                    }
                    suggestNextWord(textBefore, maxCandidateCount)
                } else {
                    val cur = getCurrentWord(content) ?: return@withContext emptyList()
                    if (cur.isBlank()) return@withContext emptyList()
                    val stripped = cur.trimEnd { !it.isLetter() }
                    autoCommitWord = stripped.ifEmpty { null }?.lowercase()
                    completeCurrentWord(stripped.ifEmpty { cur }, maxCandidateCount, textBefore)
                }

                pairs.also { result ->
                    lastTopSuggestion = result.firstOrNull()?.first?.lowercase()
                }.mapIndexed { index, (word, _) ->
                    val lcWord = word.lowercase()
                    val shouldAutoCommit = prefs.correction.autoCorrect.get() &&
                        autoCommitWord != null && index == 0 &&
                        lcWord != autoCommitWord &&
                        !lcWord.startsWith(autoCommitWord!!) &&
                        !personalDict.containsKey(autoCommitWord)
                    WordSuggestionCandidate(
                        text = word,
                        confidence = 1.0,
                        isEligibleForAutoCommit = shouldAutoCommit,
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
            .filter { it.isNotEmpty() && !isNoise(it) }
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
        if (w.length < 1 || w.length > 30) return true
        if (w.contains("@")) return true
        if (w.contains("://") || w.startsWith("www")) return true
        if (w.count { it.isDigit() } > w.length / 2) return true
        if (!w.any { it.isLetter() }) return true
        if (w.toSet().size == 1) return true
        if (w.any { c -> w.count { it == c } > w.length * 0.6 }) return true
        return false
    }

    private fun recordWord(raw: String) {
        val lc = raw.lowercase().trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>')
        if (isNoise(lc)) return
        val existing = personalDict[lc]
        val newCount = (existing?.count ?: 0) + 1
        personalDict[lc] = PersonalWord(count = newCount, lastUsedTs = System.currentTimeMillis())
        personalDirty = true
    }

    private fun dampWord(word: String) {
        val lc = word.lowercase()
        val existing = dampedWords[lc]
        val newCount = (existing?.dampCount ?: 0).coerceAtMost(5) + 1
        dampedWords[lc] = DampedWord(dampCount = newCount, lastDampedTs = System.currentTimeMillis())
    }

    private fun computeAlpha(decayedCount: Double, qwenScored: Boolean): Double = when {
        qwenScored -> when {
            decayedCount < 1.0 -> 0.15
            decayedCount < 3.0 -> 0.20
            decayedCount < 10.0 -> 0.25
            decayedCount < 30.0 -> 0.30
            else -> 0.35
        }
        else -> when {
            decayedCount < 1.0 -> 0.25
            decayedCount < 3.0 -> 0.35
            decayedCount < 10.0 -> 0.45
            decayedCount < 30.0 -> 0.50
            else -> 0.50
        }
    }

    private fun decayedCount(pw: PersonalWord): Double {
        val daysSince = (System.currentTimeMillis() - pw.lastUsedTs) / 86400000.0
        return pw.count * 0.95.pow(daysSince)
    }

    private fun personalScore(pw: PersonalWord): Double =
        (decayedCount(pw) / 50.0).coerceIn(0.0, 1.0)

    private fun rerankWithPersonal(candidates: List<Pair<String, Double>>, qwenScored: Boolean = false): List<Pair<String, Double>> {
        if (personalDict.isEmpty()) return candidates
        val rawScores = candidates.map { it.second }
        val minScore = rawScores.min()
        val maxScore = rawScores.max()
        val range = maxScore - minScore
        return candidates.map { (word, baseScore) ->
            var score = if (range > 0.0) (baseScore - minScore) / range else 0.5
            val pw = personalDict[word.lowercase()]
            if (pw != null) {
                val dc = decayedCount(pw)
                val alpha = computeAlpha(dc, qwenScored)
                val ps = personalScore(pw)
                score = alpha * ps + (1.0 - alpha) * score
            }
            val dw = dampedWords[word.lowercase()]
            if (dw != null && dw.dampCount > 0) {
                val penalty = (dw.dampCount * 0.02).coerceAtMost(0.10)
                score *= (1.0 - penalty)
            }
            word to score
        }.sortedByDescending { it.second }
    }

    private fun suggestNextWord(textBefore: String, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val words = textBefore.split(Regex("\\s+")).filter { it.isNotBlank() }
        val w2 = if (words.isNotEmpty()) words.last().lowercase() else ""
        val w1 = if (words.size >= 2) words[words.size - 2].lowercase() else null

        val scored = mutableMapOf<String, Double>()
        var qwenScored = false

        if (!natLoading && natLoaded && modelPtr != 0L) {
            val contextText = if (discourseBuffer.isNotEmpty() && textBefore.split(Regex("\\s+")).size <= 2) {
                discourseBuffer.joinToString(" ") + " " + textBefore.trimStart()
            } else {
                textBefore
            }
            val predictions = QwenNatives.predictNext(modelPtr, contextText, limit * 3)
            if (predictions != null) {
                qwenScored = true
                val firstBatch = predictions.take(limit * 2)
                val startScore = firstBatch.size.toDouble()
                for ((idx, word) in firstBatch.withIndex()) {
                    val lc = word.lowercase()
                    var s = (startScore - idx) * 2.0
                    if (w1 != null) {
                        trigrams["$w1|$w2"]?.let { tri ->
                            s += (tri[lc] ?: 0) * TRIGRAM_BOOST
                        }
                    }
                    bigrams[w2]?.let { bi ->
                        s += (bi[lc] ?: 0) * BIGRAM_BOOST
                    }
                    scored[lc] = s
                }
            }
        }

        if (scored.isEmpty()) {
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
        }

        val topBase = scored.entries.sortedByDescending { it.value }
            .take(limit * 3).map { it.key to it.value }

        return rerankWithPersonal(topBase, qwenScored).take(limit)
    }

    private fun ngramWords(): Set<String> {
        val words = mutableSetOf<String>()
        words.addAll(bigrams.keys)
        bigrams.values.forEach { words.addAll(it.keys) }
        trigrams.keys.forEach { key -> key.split("|").forEach { words.add(it) } }
        trigrams.values.forEach { words.addAll(it.keys) }
        return words
    }

    private fun completeCurrentWord(prefix: String, k: Int, textBefore: String): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val lcPrefix = prefix.lowercase()
        val cacheKey = "$lcPrefix|$textBefore.length"
        suggestionCache.get(cacheKey)?.let { return it }

        val result = doCompleteCurrentWord(prefix, k, textBefore)
        suggestionCache.put(cacheKey, result)
        return result
    }

    private fun doCompleteCurrentWord(prefix: String, k: Int, textBefore: String): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val lcPrefix = prefix.lowercase()
        val resolvedPrefix = resolveTelexPrefix(lcPrefix)

        val context = buildString {
            val ctx = textBefore.dropLast(prefix.length).trimEnd()
            append(ctx)
            if (ctx.isNotEmpty()) append(' ')
        }

        val basePool = (
            if (useTrie) {
                val trieResults = prefixTrie[lcPrefix].orEmpty()
                val resolvedResults = if (resolvedPrefix != lcPrefix) {
                    prefixTrie[resolvedPrefix].orEmpty()
                } else emptyList()
                (trieResults + resolvedResults).filter { it.length > resolvedPrefix.length }
            } else {
                seedWords.filter {
                    it.startsWith(lcPrefix) || it.startsWith(resolvedPrefix)
                }.filter { it.length > resolvedPrefix.length }
            }
        ).union(ngramWords()).union(personalDict.keys)
            .filter { !isNoise(it) }
            .take(200)
            .toSet()

        val autoCorrectOn = prefs.correction.autoCorrect.get()

        val mergedPool = if (autoCorrectOn && lcPrefix.length >= 2) {
            val corrections = autocorrectEngine?.correct(lcPrefix, limit * 2) ?: emptyList()
            val typoCorrections = typoDetector?.detectAndScore(lcPrefix) ?: emptyList()
            (basePool + corrections.map { it.word } + typoCorrections.map { it.first })
                .take(250)
                .toTypedArray()
        } else {
            basePool.toTypedArray()
        }

        val candidates = mutableListOf<Pair<String, Double>>()
        var qwenScored = false

        if (mergedPool.isNotEmpty() && !natLoading && natLoaded && modelPtr != 0L) {
            val scores = QwenNatives.scoreCandidates(modelPtr, context, mergedPool)
            if (scores != null && scores.size == mergedPool.size) {
                qwenScored = true
                for (i in mergedPool.indices) {
                    val base = if (autoCorrectOn) {
                        autocorrectEngine?.score(lcPrefix, mergedPool[i], scores[i].toDouble()) ?: scores[i].toDouble()
                    } else {
                        scores[i].toDouble()
                    }
                    candidates.add(mergedPool[i] to base)
                }
            }
        }

        if (candidates.isEmpty()) {
            val contextWords = context.trimEnd().split(Regex("\\s+"))
                .map { it.trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>') }
                .filter { it.isNotBlank() }
            val w2 = if (contextWords.isNotEmpty()) contextWords.last().lowercase() else ""
            val w1 = if (contextWords.size >= 2) contextWords[contextWords.size - 2].lowercase() else null
            for (word in mergedPool) {
                var s = 0.5
                if (w1 != null) {
                    trigrams["$w1|$w2"]?.let { tri -> s += (tri[word]?.toDouble() ?: 0.0) * TRIGRAM_BOOST }
                }
                bigrams[w2]?.let { bi -> s += (bi[word]?.toDouble() ?: 0.0) * BIGRAM_BOOST }
                val base = if (autoCorrectOn) {
                    autocorrectEngine?.score(lcPrefix, word, s) ?: s
                } else {
                    s
                }
                candidates.add(word to base)
            }
        }

        val phraseCandidates = phraseMap[resolvedPrefix].orEmpty()
            .filter { it.length > resolvedPrefix.length }
        if (phraseCandidates.isNotEmpty()) {
            for (phrase in phraseCandidates) {
                if (candidates.none { it.first == phrase }) {
                    candidates.add(phrase to 0.9)
                }
            }
        }

        return rerankWithPersonal(candidates, qwenScored).take(limit)
    }

    private fun getCurrentWord(content: EditorContent): String? {
        content.composingText.let { if (it.isNotBlank()) return it.toString() }
        content.currentWordText.let { if (it.isNotBlank()) return it.toString() }
        return null
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.toString().lowercase().trim()
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

    override suspend fun getListOfWords(subtype: Subtype): List<String> = personalDict.keys.toList()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val pw = personalDict[word.lowercase()]
        return if (pw != null) (decayedCount(pw) / 50.0).coerceIn(0.0, 1.0) else 0.0
    }

    override suspend fun destroy() {
        bgScope.cancel()
        if (personalDirty) savePersonalDict()
        if (ngramDirty) saveNgrams()
        saveDiscourseBuffer()
        if (modelPtr != 0L) QwenNatives.close(modelPtr)
        modelPtr = 0L
        natLoaded = false
    }
}
