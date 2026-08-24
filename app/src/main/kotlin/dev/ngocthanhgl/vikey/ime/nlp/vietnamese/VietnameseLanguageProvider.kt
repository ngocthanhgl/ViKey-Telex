package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import dev.ngocthanhgl.vikey.appContext
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.editor.EditorContent
import dev.ngocthanhgl.vikey.ime.nlp.SpellingProvider
import dev.ngocthanhgl.vikey.ime.nlp.SpellingResult
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionProvider
import dev.ngocthanhgl.vikey.ime.nlp.WordSuggestionCandidate
import dev.ngocthanhgl.vikey.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock
import java.util.Locale

class VietnameseLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese"
    }

    private val appContext by context.appContext()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    override val providerId = ProviderId

    override suspend fun create() {
    }

    override suspend fun preload(subtype: Subtype) {
    }

    private suspend fun loadDict() {
        wordData.withLock { dict ->
            if (dict.isEmpty()) {
                try {
                    val rawData = withContext(Dispatchers.IO) {
                        appContext.assets.readText("ime/dict/vi.json")
                    }
                    val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                    dict.putAll(jsonData)
                } catch (e: Exception) {
                    flogDebug { "Failed to load Vietnamese dictionary: ${e.message}" }
                }
            }
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        return SpellingResult.validWord()
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val prefix = getCurrentWord(content)
            ?: return emptyList()

        loadDict()
        val dict = wordData.withLock { it.toMap() }
        if (dict.isEmpty()) return emptyList()

        val lowerPrefix = prefix.lowercase(Locale.ROOT)

        val matches = dict.entries
            .filter { it.key.startsWith(lowerPrefix) }
            .sortedByDescending { it.value }
            .take(maxCandidateCount)

        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { index, (word, _) ->
            // Only treat as exact (auto-commit eligible) when the typed prefix matches the
            // dictionary entry character-for-character, including letter case.
            val isExact = word == prefix
            WordSuggestionCandidate(
                text = applyCasePattern(prefix, word),
                confidence = if (isExact) 1.0 else (0.9 - index * 0.1).coerceAtLeast(0.1),
                isEligibleForAutoCommit = isExact && index == 0,
                sourceProvider = this,
            )
        }
    }

    private fun applyCasePattern(typed: String, word: String): String {
        if (typed.isEmpty()) return word
        val sb = StringBuilder(word)
        var i = 0
        while (i < typed.length && i < sb.length) {
            if (typed[i].isUpperCase()) {
                sb[i] = sb[i].uppercaseChar()
            }
            i++
        }
        return sb.toString()
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

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {
    }
}
