package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import dev.ngocthanhgl.vikey.ime.core.Subtype
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
        private const val USER_DICT = "user_words.json"
        private const val NEXT_WORD_POOL = 500
        private const val LEARN_BOOST = 50.0
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

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val providerId = ProviderId

    override suspend fun create() {
        if (vocabList.isNotEmpty()) return
        withContext(Dispatchers.IO) {
            loadVocab()
            loadUserDict()
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
            val f = File(context.filesDir, USER_DICT)
            if (f.exists()) {
                val raw = f.readText()
                val json = JSONObject(raw)
                for (key in json.keys()) {
                    userWords[key] = json.getInt(key)
                }
                flogDebug { "KenLM: loaded ${userWords.size} learned words" }
            }
        } catch (e: Exception) {
            flogDebug { "KenLM: user dict load: ${e.message}" }
        }
    }

    private fun saveUserDict() {
        if (!userDirty) return
        try {
            val json = JSONObject(userWords).toString()
            File(context.filesDir, USER_DICT).writeText(json)
            userDirty = false
        } catch (e: Exception) {
            flogDebug { "KenLM: user dict save: ${e.message}" }
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
                    suggestNextWord(lastWord, maxCandidateCount)
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
        val words = text.split(Regex("[\\s\\p{Punct}]+")).filter { it.length >= 3 }
        for (w in words.takeLast(10)) {
            val lc = w.lowercase()
            if (lc !in userWords && lc in vocabList) continue
            val prev = userWords[lc] ?: 0
            userWords[lc] = (prev + 1).coerceAtMost(255)
            userDirty = true
        }
        if (userDirty && learnCounter % 30 == 0) saveUserDict()
    }

    private fun boostLearned(pairs: List<Pair<String, Double>>): List<Pair<String, Double>> {
        if (userWords.isEmpty()) return pairs
        return pairs.map { (word, score) ->
            val boost = userWords[word.lowercase()]?.toDouble()?.times(LEARN_BOOST) ?: 0.0
            word to (score + boost)
        }.sortedByDescending { it.second }
    }

    private fun suggestNextWord(prevWord: String, k: Int): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val pool = commonWords

        if (!natLoading && natLoaded && modelPtr != 0L) {
            val scores = KenlmNatives.scoreCandidates(modelPtr, prevWord, pool.toTypedArray())
            if (scores != null && scores.size == pool.size) {
                return pool.indices.map { pool[it] to scores[it].toDouble() }
                    .sortedByDescending { it.second }.take(limit)
            }
        }
        return pool.take(limit).map { it to 1.0 }
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
        if (modelPtr != 0L) KenlmNatives.unloadModel(modelPtr)
        modelPtr = 0L
        natLoaded = false
    }
}
