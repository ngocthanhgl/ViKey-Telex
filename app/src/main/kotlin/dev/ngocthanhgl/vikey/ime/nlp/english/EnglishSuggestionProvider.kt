package dev.ngocthanhgl.vikey.ime.nlp.english

import android.content.Context
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.editor.EditorContent
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionProvider
import dev.ngocthanhgl.vikey.ime.nlp.WordSuggestionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class EnglishSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.english"
        private const val EN_WORDS = "ime/dict/en.json"
        private const val PERSONAL_DICT = "english_personal_dict.json"
    }

    private val wordFrequencies = mutableMapOf<String, Int>()
    private val sortedWords = mutableListOf<String>()
    private val personalDict = mutableMapOf<String, Int>()
    private var personalDirty = false

    override val providerId = ProviderId

    override suspend fun create() {
        withContext(Dispatchers.IO) {
            loadWords()
            loadPersonalDict()
        }
    }

    private fun loadWords() {
        try {
            val raw = context.assets.open(EN_WORDS).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val w = key.lowercase()
                if (w.isNotEmpty() && w.none { it.isWhitespace() } && w.all { it.isLetter() || it == '\'' }) {
                    wordFrequencies[w] = json.getInt(key)
                }
            }
            sortedWords.addAll(wordFrequencies.entries.sortedByDescending { it.value }.map { it.key })
        } catch (_: Exception) {}
    }

    private fun loadPersonalDict() {
        try {
            val f = File(context.filesDir, PERSONAL_DICT)
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            for (key in json.keys()) {
                personalDict[key] = json.getInt(key)
            }
        } catch (_: Exception) {}
    }

    private fun savePersonalDict() {
        if (!personalDirty) return
        try {
            val json = JSONObject()
            for ((word, count) in personalDict) {
                json.put(word, count)
            }
            File(context.filesDir, PERSONAL_DICT).writeText(json.toString())
            personalDirty = false
        } catch (_: Exception) {}
    }

    fun recordWord(raw: String) {
        val lc = raw.lowercase().trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>')
        if (lc.isEmpty() || lc.any { !it.isLetter() && it != '\'' }) return
        personalDict[lc] = (personalDict[lc] ?: 0) + 1
        personalDirty = true
    }

    override suspend fun preload(subtype: Subtype) {}

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val textBefore = content.textBeforeSelection
        val prefix = textBefore.substringAfterLast(' ').substringAfter('\n').lowercase()
        if (prefix.isEmpty()) return emptyList()

        val found = mutableSetOf<String>()
        val candidates = mutableListOf<SuggestionCandidate>()

        for (word in sortedWords) {
            if (word.startsWith(prefix) && found.add(word)) {
                candidates.add(WordSuggestionCandidate(word))
                if (candidates.size >= maxCandidateCount) break
            }
        }
        for (word in personalDict.keys) {
            if (word.startsWith(prefix) && found.add(word)) {
                candidates.add(WordSuggestionCandidate(word))
                if (candidates.size >= maxCandidateCount) break
            }
        }
        return candidates
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        recordWord(candidate.text.toString())
        savePersonalDict()
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {}

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean = false

    override suspend fun getListOfWords(subtype: Subtype): List<String> =
        (wordFrequencies.keys + personalDict.keys).toList()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val lc = word.lowercase()
        val pd = personalDict[lc]
        if (pd != null) return (pd / 50.0).coerceIn(0.0, 1.0)
        val freq = wordFrequencies[lc] ?: return 0.0
        return (freq / 50_000_000.0).coerceIn(0.0, 1.0)
    }

    override suspend fun destroy() {
        savePersonalDict()
    }
}
