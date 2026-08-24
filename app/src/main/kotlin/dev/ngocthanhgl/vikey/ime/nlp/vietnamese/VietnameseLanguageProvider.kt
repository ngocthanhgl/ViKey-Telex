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
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

class VietnameseLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese"

        private const val PREFIX_INDEX_MAX_LENGTH = 6

        /**
         * Fold a Vietnamese word to its toneless ASCII skeleton so toneless input
         * ("duoc") can match dictionary forms carrying diacritics ("được").
         *
         * NFD splits every precomposed Vietnamese glyph into base letter + combining
         * mark; all such marks live in U+0300..U+036F (including U+031B, the horn of
         * ơ/ư). Đ (U+0111) has no canonical decomposition, so it is mapped manually.
         */
        fun foldVietnamese(word: String): String {
            val normalized = Normalizer.normalize(word, Normalizer.Form.NFD)
            val sb = StringBuilder(normalized.length)
            for (c in normalized) {
                when {
                    c == 'đ' -> sb.append('d')
                    c == 'Đ' -> sb.append('D')
                    c in '\u0300'..'\u036F' -> {}
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private data class DictEntry(val word: String, val freq: Int)
    }

    private val appContext by context.appContext()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    /** prefix (1..6 chars, lowercase) -> entries sorted by frequency descending. */
    private val prefixIndex = guardedByLock { mutableMapOf<String, MutableList<DictEntry>>() }

    /** folded lowercase skeleton -> real dictionary words sorted by frequency descending. */
    private val foldedIndex = guardedByLock { mutableMapOf<String, MutableList<String>>() }

    @Volatile
    private var maxFreq = 1L

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
                rebuildIndexes(dict)
            }
        }
    }

    private fun rebuildIndexes(dict: Map<String, Int>) {
        var max = 1L
        prefixIndex.withLock { pIdx ->
            pIdx.clear()
            for ((word, freq) in dict) {
                if (freq > max) max = freq.toLong()
                val lower = word.lowercase(Locale.ROOT)
                val upperLen = minOf(PREFIX_INDEX_MAX_LENGTH, lower.length)
                for (len in 1..upperLen) {
                    pIdx.getOrPut(lower.take(len)) { mutableListOf() }.add(DictEntry(word, freq))
                }
            }
            for (list in pIdx.values) {
                list.sortByDescending { it.freq }
            }
        }
        foldedIndex.withLock { fIdx ->
            fIdx.clear()
            for ((word, freq) in dict) {
                val folded = foldVietnamese(word).lowercase(Locale.ROOT)
                if (folded.isEmpty()) continue
                fIdx.getOrPut(folded) { mutableListOf() }.add(word)
            }
            // Sort every bucket by its corpus frequency (descending).
            val freqs = dict
            for (list in fIdx.values) {
                list.sortByDescending { freqs[it] ?: 0 }
            }
        }
        maxFreq = max
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

        val lowerPrefix = prefix.lowercase(Locale.ROOT)

        // Primary path: O(1)-ish exact-prefix hit against the prebuilt index.
        val direct = prefixIndex.withLock { it[lowerPrefix] }.orEmpty()

        if (direct.size >= maxCandidateCount) {
            return direct.take(maxCandidateCount).mapIndexed { index, entry ->
                buildCandidate(prefix, entry.word, entry.freq, index, maxCandidateCount)
            }
        }

        val seen = HashSet<String>(direct.size * 2 + 8)
        val merged = ArrayList<Pair<String, Int>>(maxCandidateCount)
        for (entry in direct) {
            if (seen.add(entry.word.lowercase(Locale.ROOT))) merged.add(entry.word to entry.freq)
        }

        // Fallback path: match through the toneless folded skeleton so typing
        // "duoc" can surface "được" even without an ASCII dictionary entry.
        val foldedPrefix = foldVietnamese(lowerPrefix)
        if (foldedPrefix.isNotEmpty()) {
            val foldedHits = foldedIndex.withLock { fIdx ->
                val out = mutableListOf<String>()
                for ((skeleton, words) in fIdx) {
                    if (!skeleton.startsWith(foldedPrefix)) continue
                    for (word in words) {
                        if (seen.add(word.lowercase(Locale.ROOT))) {
                            out.add(word)
                        }
                    }
                }
                out
            }
            // Re-rank by actual corpus frequency using wordData lookups.
            val ranked = foldedHits
                .map { word -> word to (wordData.withLock { data -> data[word] ?: 0 }) }
                .sortedByDescending { it.second }
            for ((word, freq) in ranked) {
                if (merged.size >= maxCandidateCount * 2) break
                merged.add(word to freq)
            }
        }

        if (merged.isEmpty()) return emptyList()

        return merged.take(maxCandidateCount).mapIndexed { index, (word, freq) ->
            buildCandidate(prefix, word, freq, index, maxCandidateCount)
        }
    }

    private fun buildCandidate(
        prefix: String,
        word: String,
        freq: Int,
        index: Int,
        maxCandidateCount: Int,
    ): SuggestionCandidate {
        // Only treat as exact (auto-commit eligible) when the typed prefix matches the
        // dictionary entry character-for-character, including letter case.
        val isExact = word == prefix
        val normFreq = if (maxFreq > 0) ln(1.0 + freq) / ln(1.0 + maxFreq) else 0.0
        val confidence = if (isExact) {
            1.0
        } else {
            (normFreq * (1.0 - index.toDouble() / maxCandidateCount)).coerceIn(0.05, 0.99)
        }
        return WordSuggestionCandidate(
            text = applyCasePattern(prefix, word),
            confidence = confidence,
            isEligibleForAutoCommit = isExact && index == 0,
            sourceProvider = this,
        )
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
        // Log-scaled normalization keeps this meaningful across the corpus's huge
        // dynamic range (raw counts span single digits to tens of millions); callers
        // receive a value in [0, 1] instead of the old saturating count/255 formula.
        val count = wordData.withLock { it[word] ?: 0 }
        return ln(1.0 + count) / ln(1.0 + maxFreq)
    }

    override suspend fun destroy() {
    }
}
