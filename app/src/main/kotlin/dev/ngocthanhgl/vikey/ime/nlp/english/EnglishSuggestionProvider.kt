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

class EnglishSuggestionProvider(private val context: Context) : SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.english"
        private const val EN_WORDS = "ime/dict/en.json"
    }

    private val wordFrequencies = mutableMapOf<String, Int>()
    private val sortedWords = mutableListOf<String>()

    override val providerId = ProviderId

    override suspend fun create() {
        withContext(Dispatchers.IO) {
            loadWords()
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

        val candidates = mutableListOf<SuggestionCandidate>()
        for (word in sortedWords) {
            if (word.startsWith(prefix)) {
                candidates.add(WordSuggestionCandidate(word))
                if (candidates.size >= maxCandidateCount) break
            }
        }
        return candidates
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {}

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {}

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean = false

    override suspend fun getListOfWords(subtype: Subtype): List<String> = wordFrequencies.keys.toList()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val freq = wordFrequencies[word.lowercase()] ?: return 0.0
        return (freq / 50_000_000.0).coerceIn(0.0, 1.0)
    }

    override suspend fun destroy() {}
}
