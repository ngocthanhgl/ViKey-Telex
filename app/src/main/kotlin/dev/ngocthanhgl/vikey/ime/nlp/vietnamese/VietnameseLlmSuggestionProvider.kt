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

class VietnameseLlmSuggestionProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese"
    }

    private val appContext by context.appContext()
    private val predictor = CharNGramPredictor(context)
    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private var dictLoaded = false
    private var predictorLoaded = false

    override val providerId = ProviderId

    override suspend fun create() {
        predictor.load()
        predictorLoaded = predictor.isLoaded
        ensureDict()
    }

    override suspend fun preload(subtype: Subtype) {
        if (!predictorLoaded) {
            predictor.load()
            predictorLoaded = predictor.isLoaded
        }
        ensureDict()
    }

    private suspend fun ensureDict() {
        if (dictLoaded) return
        wordData.withLock { dict ->
            if (dict.isEmpty()) {
                try {
                    val rawData = withContext(Dispatchers.IO) {
                        appContext.assets.readText("ime/dict/vi.json")
                    }
                    val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                    dict.putAll(jsonData)
                    dictLoaded = true
                } catch (e: Exception) {
                    flogDebug { "Failed to load dictionary: ${e.message}" }
                }
            }
        }
    }

    override suspend fun spell(
        subtype: Subtype, word: String, precedingWords: List<String>,
        followingWords: List<String>, maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean, isPrivateSession: Boolean,
    ): SpellingResult {
        return SpellingResult.validWord()
    }

    override suspend fun suggest(
        subtype: Subtype, content: EditorContent, maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean, isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val prefix = getCurrentWord(content) ?: return emptyList()
        if (prefix.isBlank()) return emptyList()

        val completions = if (predictorLoaded) {
            predictor.completePrefix(prefix, maxCandidateCount)
        } else {
            emptyList()
        }

        if (completions.isNotEmpty()) {
            return completions.mapIndexed { index, word ->
                WordSuggestionCandidate(
                    text = word,
                    confidence = (1.0 - index * 0.08).coerceAtLeast(0.1),
                    isEligibleForAutoCommit = false,
                    sourceProvider = this,
                )
            }
        }

        val dict = wordData.withLock { it.toMap() }
        if (dict.isEmpty()) return emptyList()
        val lower = prefix.lowercase()
        return dict.entries
            .filter { it.key.startsWith(lower) }
            .sortedByDescending { it.value }
            .take(maxCandidateCount)
            .map { (word, _) ->
                WordSuggestionCandidate(
                    text = word,
                    confidence = 0.5,
                    isEligibleForAutoCommit = false,
                    sourceProvider = this,
                )
            }
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

    override suspend fun destroy() {}
}
