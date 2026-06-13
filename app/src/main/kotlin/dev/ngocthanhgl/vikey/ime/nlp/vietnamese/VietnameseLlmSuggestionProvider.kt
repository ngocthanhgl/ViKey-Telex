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

class VietnameseLlmSuggestionProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese"
    }

    private val predictor = CharNGramPredictor(context)
    private var predictorLoaded = false

    override val providerId = ProviderId

    override suspend fun create() {
        predictor.load()
        predictorLoaded = predictor.isLoaded
    }

    override suspend fun preload(subtype: Subtype) {
        if (!predictorLoaded) {
            predictor.load()
            predictorLoaded = predictor.isLoaded
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
    ): List<SuggestionCandidate> = withContext(Dispatchers.IO) {
        try {
            val textBefore = content.textBeforeSelection

            if (textBefore.endsWith(" ")) {
                return@withContext suggestNextWords(textBefore, 2)
            }

            val prefix = getCurrentWord(content) ?: return@withContext emptyList()
            if (prefix.isBlank()) return@withContext emptyList()

            suggestCompletions(prefix, maxCandidateCount)
        } catch (e: Exception) {
            flogDebug { "suggest failed: ${e.message}" }
            emptyList()
        }
    }

    private fun suggestNextWords(textBefore: String, maxCount: Int): List<SuggestionCandidate> {
        if (!predictorLoaded) return emptyList()
        return try {
            val nextWords = predictor.predictNextWords(textBefore, maxCount)
            if (nextWords.isEmpty()) return emptyList()

            val lastWord = textBefore.split(Regex("[\\s\\p{Punct}]+"))
                .lastOrNull { it.isNotBlank() } ?: return emptyList()

            nextWords.mapIndexed { index, word ->
                WordSuggestionCandidate(
                    text = adjustCase(lastWord, word),
                    confidence = (1.0 - index * 0.15).coerceAtLeast(0.5),
                    isEligibleForAutoCommit = false,
                    sourceProvider = this,
                )
            }
        } catch (e: Exception) {
            flogDebug { "suggestNextWords failed: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun suggestCompletions(prefix: String, maxCount: Int): List<SuggestionCandidate> {
        return try {
            suggestCompletionsImpl(prefix, maxCount)
        } catch (e: Exception) {
            flogDebug { "suggestCompletions failed: ${e.message}" }
            emptyList()
        }
    }

    private suspend fun suggestCompletionsImpl(prefix: String, maxCount: Int): List<SuggestionCandidate> {
        val completions = if (predictorLoaded) {
            predictor.completePrefix(prefix, maxCount)
        } else {
            emptyList()
        }

        return completions.mapIndexed { index, word ->
            WordSuggestionCandidate(
                text = adjustCase(prefix, word),
                confidence = (1.0 - index * 0.08).coerceAtLeast(0.1),
                isEligibleForAutoCommit = false,
                sourceProvider = this,
            )
        }
    }

    private fun adjustCase(reference: String, word: String): String {
        if (reference.length > 1 && reference.all { it.isUpperCase() }) return word.uppercase()
        if (reference.firstOrNull()?.isUpperCase() == true) return word.replaceFirstChar { it.uppercase() }
        return word
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

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {}

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return emptyList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return 0.0
    }

    override suspend fun destroy() {}
}
