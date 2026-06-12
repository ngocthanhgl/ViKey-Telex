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
    private val sessionFreq = mutableMapOf<String, Int>()
    private var sessionTapCount = 0

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
    ): List<SuggestionCandidate> = withContext(Dispatchers.IO) {
        try {
            val textBefore = content.textBeforeSelection

            if (textBefore.endsWith(" ")) {
                return@withContext suggestNextWords(textBefore, maxCandidateCount)
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
                    confidence = (1.0 - index * 0.08).coerceAtLeast(0.1),
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
            val completions = if (predictorLoaded) {
                predictor.completePrefix(prefix, maxCount)
            } else {
                emptyList()
            }

            if (completions.isNotEmpty()) {
                return@try rankCompletions(prefix, completions, maxCount)
            }

            val unrolled = if (predictorLoaded) {
                predictor.unrollWord(prefix, 20, 5)
            } else {
                emptyList()
            }

            if (unrolled.isNotEmpty()) {
                return@try unrolled.mapIndexed { index, word ->
                    WordSuggestionCandidate(
                        text = adjustCase(prefix, word),
                        confidence = (1.0 - index * 0.15).coerceAtLeast(0.1),
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                }
            }

            val dict = wordData.withLock { it.toMap() }
            if (dict.isEmpty()) return@try emptyList()
            val lower = prefix.lowercase()
            dict.entries
                .filter { it.key.startsWith(lower) && !it.key.contains(" ") }
                .sortedByDescending { it.value }
                .take(maxCount)
                .map { (word, _) ->
                    WordSuggestionCandidate(
                        text = adjustCase(prefix, word),
                        confidence = 0.5,
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                }
        } catch (e: Exception) {
            flogDebug { "suggestCompletions failed: ${e.message}" }
            emptyList()
        }
    }

    private fun rankCompletions(
        prefix: String,
        words: List<String>,
        maxCount: Int,
    ): List<SuggestionCandidate> {
        return try {
            val lang = predictor.detectLanguage(prefix)
            val bias = when (lang) {
                CharNGramPredictor.Language.VIETNAMESE -> 1.5
                CharNGramPredictor.Language.ENGLISH -> 1.5
                CharNGramPredictor.Language.UNKNOWN -> 1.0
            }

            val scored = words.map { word ->
                val normFreq = predictor.normalizedFrequency(word)
                val langBonus = when (lang) {
                    CharNGramPredictor.Language.VIETNAMESE ->
                        if (predictor.isVietnameseWord(word)) bias else 1.0
                    CharNGramPredictor.Language.ENGLISH ->
                        if (predictor.isEnglishWord(word)) bias else 1.0
                    CharNGramPredictor.Language.UNKNOWN -> 1.0
                }
                val sessionBoost = 1.0 + (sessionFreq[word] ?: 0) * 0.2
                word to (normFreq * langBonus * sessionBoost)
            }

            scored
                .sortedByDescending { it.second }
                .take(maxCount)
                .mapIndexed { index, (word, _) ->
                    WordSuggestionCandidate(
                        text = adjustCase(prefix, word),
                        confidence = (1.0 - index * 0.08).coerceAtLeast(0.1),
                        isEligibleForAutoCommit = false,
                        sourceProvider = this,
                    )
                }
        } catch (e: Exception) {
            flogDebug { "rankCompletions failed: ${e.message}" }
            emptyList()
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

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.toString().lowercase()
        sessionFreq.merge(word, 1) { a, b -> a + b }
        sessionTapCount++
        if (sessionFreq.size > 200) {
            val toRemove = sessionFreq.entries
                .sortedBy { it.value }
                .take(50)
                .map { it.key }
            toRemove.forEach { sessionFreq.remove(it) }
        }
        flogDebug { "Session word: $word (${sessionFreq[word]})" }
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
