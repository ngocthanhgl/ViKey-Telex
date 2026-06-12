package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import android.util.Log
import dev.ngocthanhgl.vikey.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText

class CharNGramPredictor(context: Context) {
    companion object {
        private const val TAG = "CharNGramPredictor"
        private const val VI_DICT = "ime/dict/vi.json"
        private const val EN_DICT = "ime/dict/data.json"
        private const val COMPLETION_LIMIT = 100000
    }

    private val appContext by context.appContext()

    private var unigrams: Map<String, Int> = emptyMap()
    private var viUnigrams: Map<String, Int> = emptyMap()
    private var enUnigrams: Map<String, Int> = emptyMap()
    private var charUnigrams: MutableMap<String, Int> = mutableMapOf()
    private var bigrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var trigrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var quadgrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var allWords: List<String> = emptyList()
    private var singleWords: List<String> = emptyList()
    private var viSingleWords: List<String> = emptyList()
    private var enSingleWords: List<String> = emptyList()
    private var viCompletionWords: List<String> = emptyList()
    private var enCompletionWords: List<String> = emptyList()
    private var mergedCompletionWords: List<String> = emptyList()
    private var wordBigrams: Map<String, List<Pair<String, Int>>> = emptyMap()
    private var wordTrigrams: Map<String, List<Pair<String, Int>>> = emptyMap()
    private var wordQuadgrams: Map<String, List<Pair<String, Int>>> = emptyMap()
    private var viFreqMax: Long = 1
    private var enFreqMax: Long = 1
    private var topUnigrams: List<Pair<String, Int>> = emptyList()
    private var loaded = false

    private val viDiacritics: Regex by lazy {
        Regex("[âăđêôơưửừữựắằẵặấầẩẫậéèẻẽẹíìỉĩịóòỏõọúùủũụýỳỷỹỵ]")
    }

    enum class Language { VIETNAMESE, ENGLISH, UNKNOWN }

    suspend fun load() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            try {
                val viWords = loadDict(VI_DICT)
                val enWords = loadDict(EN_DICT)

                viFreqMax = (viWords.values.maxOrNull() ?: 1).toLong()
                enFreqMax = (enWords.values.maxOrNull() ?: 1).toLong()
                viUnigrams = viWords
                enUnigrams = enWords

                val merged = viWords.toMutableMap()
                enWords.forEach { (k, v) -> merged.merge(k, v) { a, b -> a.coerceAtLeast(b) } }
                unigrams = merged

                val sorted = merged.entries
                    .filter { it.key.all { c -> c.isLetter() || c == ' ' } }
                    .sortedByDescending { it.value }
                    .map { it.key }
                allWords = sorted
                singleWords = sorted.filter { !it.contains(" ") }

                viSingleWords = viWords.keys
                    .filter { !it.contains(" ") }
                    .sortedByDescending { viWords[it] }
                enSingleWords = enWords.keys
                    .filter { !it.contains(" ") }
                    .sortedByDescending { enWords[it] }
                viCompletionWords = viSingleWords.take(COMPLETION_LIMIT)
                enCompletionWords = enSingleWords.take(COMPLETION_LIMIT)
                mergedCompletionWords = singleWords.take(COMPLETION_LIMIT)

                buildNGrams()
                buildWordNGrams()
                topUnigrams = unigrams.entries
                    .filter { !it.key.contains(" ") }
                    .sortedByDescending { it.value }
                    .take(20)
                    .map { it.key to it.value }
                loaded = true

                Log.i(TAG, "Loaded ${allWords.size} words, " +
                    "${viSingleWords.size} VI + ${enSingleWords.size} EN singles, " +
                    "${bigrams.size} bi, ${trigrams.size} tri, ${quadgrams.size} quad chars, " +
                    "${wordBigrams.size} wbi, ${wordTrigrams.size} wtri, ${wordQuadgrams.size} wquad")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load: ${e.message}")
            }
        }
    }

    private fun loadDict(path: String): Map<String, Int> {
        return try {
            val raw = appContext.assets.readText(path)
            val serializer = MapSerializer(String.serializer(), Int.serializer())
            Json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $path: ${e.message}")
            emptyMap()
        }
    }

    private fun buildNGrams() {
        val charCounts = mutableMapOf<String, Int>()
        for (word in allWords) {
            val w = " $word "
            for (i in 0 until w.length) {
                charCounts.merge(w[i].toString(), 1) { a, b -> a + b }
            }
            for (i in 0 until w.length - 1) {
                bigrams.getOrPut(w[i].toString()) { mutableMapOf() }
                    .merge(w[i + 1].toString(), 1) { a, b -> a + b }
            }
            for (i in 0 until w.length - 2) {
                trigrams.getOrPut(w.substring(i, i + 2)) { mutableMapOf() }
                    .merge(w[i + 2].toString(), 1) { a, b -> a + b }
            }
            for (i in 0 until w.length - 3) {
                quadgrams.getOrPut(w.substring(i, i + 3)) { mutableMapOf() }
                    .merge(w[i + 3].toString(), 1) { a, b -> a + b }
            }
        }
        charUnigrams = charCounts
    }

    private fun buildWordNGrams() {
        val bi = mutableMapOf<String, MutableMap<String, Int>>()
        val tri = mutableMapOf<String, MutableMap<String, Int>>()
        val quad = mutableMapOf<String, MutableMap<String, Int>>()

        for ((phrase, freq) in unigrams) {
            val parts = phrase.split(" ")
            if (parts.size >= 2) {
                for (i in 0 until parts.size - 1) {
                    val w1 = parts[i].lowercase()
                    val w2 = parts[i + 1].lowercase()
                    bi.getOrPut(w1) { mutableMapOf() }.merge(w2, freq) { a, b -> a + b }
                }
            }
            if (parts.size >= 3) {
                for (i in 0 until parts.size - 2) {
                    val key = "${parts[i].lowercase()} ${parts[i + 1].lowercase()}"
                    val w3 = parts[i + 2].lowercase()
                    tri.getOrPut(key) { mutableMapOf() }.merge(w3, freq) { a, b -> a + b }
                }
            }
            if (parts.size >= 4) {
                for (i in 0 until parts.size - 3) {
                    val key = "${parts[i].lowercase()} ${parts[i + 1].lowercase()} ${parts[i + 2].lowercase()}"
                    val w4 = parts[i + 3].lowercase()
                    quad.getOrPut(key) { mutableMapOf() }.merge(w4, freq) { a, b -> a + b }
                }
            }
        }

        wordBigrams = bi.mapValues { (_, v) ->
            v.entries.sortedByDescending { it.value }.map { it.key to it.value }
        }
        wordTrigrams = tri.mapValues { (_, v) ->
            v.entries.sortedByDescending { it.value }.map { it.key to it.value }
        }
        wordQuadgrams = quad.mapValues { (_, v) ->
            v.entries.sortedByDescending { it.value }.map { it.key to it.value }
        }
    }

    fun predictNextChar(context: String): List<Pair<String, Double>> {
        val ctx = context.takeLast(3)
        val candidates = mutableMapOf<String, Double>()

        if (ctx.length >= 3) {
            val qmap = quadgrams[ctx.takeLast(3)]
            if (qmap != null) {
                val total = qmap.values.sum().toDouble()
                val weight = 0.40
                for ((c, count) in qmap) {
                    candidates.merge(c, weight * count / total) { a, b -> a + b }
                }
            }
        }

        if (ctx.length >= 2) {
            val tmap = trigrams[ctx.takeLast(2)]
            if (tmap != null) {
                val total = tmap.values.sum().toDouble()
                val weight = 0.30
                for ((c, count) in tmap) {
                    candidates.merge(c, weight * count / total) { a, b -> a + b }
                }
            }
        }

        if (ctx.isNotEmpty()) {
            val bmap = bigrams[ctx.last().toString()]
            if (bmap != null) {
                val total = bmap.values.sum().toDouble()
                val weight = 0.20
                for ((c, count) in bmap) {
                    candidates.merge(c, weight * count / total) { a, b -> a + b }
                }
            }
        }

        val uniTotal = charUnigrams.values.sum().toDouble()
        val uniWeight = 0.10
        for ((c, count) in charUnigrams.entries.sortedByDescending { it.value }.take(26)) {
            candidates.merge(c, uniWeight * count / uniTotal) { a, b -> a + b }
        }

        return candidates.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
    }

    fun completePrefix(prefix: String, maxCount: Int = 8): List<String> {
        return try {
            if (prefix.isBlank()) return@try emptyList()
            val lower = prefix.lowercase()

            val pool = when (detectLanguage(prefix)) {
                Language.VIETNAMESE -> viCompletionWords
                Language.ENGLISH -> enCompletionWords
                Language.UNKNOWN -> mergedCompletionWords
            }

            pool
                .filter { it.startsWith(lower) }
                .take(maxCount)
                .ifEmpty {
                    pool
                        .filter { it.contains(lower) }
                        .take(maxCount)
                }
                .ifEmpty {
                    mergedCompletionWords
                        .filter { it.contains(lower) }
                        .take(maxCount)
                }
        } catch (e: Exception) {
            Log.w(TAG, "completePrefix failed: ${e.message}")
            emptyList()
        }
    }

    fun predictNextWords(context: String, maxCount: Int = 8): List<String> {
        return try {
            predictNextWordsImpl(context, maxCount)
        } catch (e: Exception) {
            Log.w(TAG, "predictNextWords failed: ${e.message}")
            emptyList()
        }
    }

    private fun predictNextWordsImpl(context: String, maxCount: Int = 8): List<String> {
        val words = context.split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
        if (words.isEmpty()) return emptyList()

        val lastWord = words.last()
        val last2 = if (words.size >= 2) listOf(words[words.size - 2], words[words.size - 1])
            .joinToString(" ") else null
        val last3 = if (words.size >= 3) listOf(words[words.size - 3], words[words.size - 2], words[words.size - 1])
            .joinToString(" ") else null

        val lang = detectLanguage(lastWord)
        val langBias = when (lang) {
            Language.VIETNAMESE -> 1.5
            Language.ENGLISH -> 1.5
            Language.UNKNOWN -> 1.0
        }

        val candidates = mutableMapOf<String, Double>()

        if (last3 != null) {
            val qmap = wordQuadgrams[last3]
            if (qmap != null) {
                val total = qmap.sumOf { it.second }.toDouble()
                val weight = 0.40
                for ((w, count) in qmap) {
                    val bias = if (isVietnameseWord(w)) langBias else 1.0
                    candidates.merge(w, weight * count / total * bias) { a, b -> a + b }
                }
            }
        }

        if (last2 != null) {
            val tmap = wordTrigrams[last2]
            if (tmap != null) {
                val total = tmap.sumOf { it.second }.toDouble()
                val weight = 0.30
                for ((w, count) in tmap) {
                    val bias = if (isVietnameseWord(w)) langBias else 1.0
                    candidates.merge(w, weight * count / total * bias) { a, b -> a + b }
                }
            }
        }

        val bmap = wordBigrams[lastWord]
        if (bmap != null) {
            val total = bmap.sumOf { it.second }.toDouble()
            val weight = 0.20
            for ((w, count) in bmap) {
                val bias = if (isVietnameseWord(w)) langBias else 1.0
                candidates.merge(w, weight * count / total * bias) { a, b -> a + b }
            }
        }

        val uniWeight = 0.10
        val uniTotal = topUnigrams.sumOf { it.second.toDouble() }
        for ((w, count) in topUnigrams) {
            val bias = when (lang) {
                Language.VIETNAMESE -> if (isVietnameseWord(w)) langBias else 1.0
                Language.ENGLISH -> if (isEnglishWord(w)) langBias else 1.0
                Language.UNKNOWN -> 1.0
            }
            candidates.merge(w, uniWeight * count / uniTotal * bias) { a, b -> a + b }
        }

        return candidates.entries
            .sortedByDescending { it.value }
            .take(maxCount)
            .map { it.key }
    }

    fun unrollWord(prefix: String, maxLen: Int = 20, beamWidth: Int = 5): List<String> {
        return try {
            unrollWordImpl(prefix, maxLen, beamWidth)
        } catch (e: Exception) {
            Log.w(TAG, "unrollWord failed: ${e.message}")
            emptyList()
        }
    }

    private fun unrollWordImpl(prefix: String, maxLen: Int = 20, beamWidth: Int = 5): List<String> {
        if (prefix.length >= maxLen) return emptyList()
        val found = mutableSetOf<String>()
        var beam = listOf(prefix.lowercase() to 1.0)

        for (step in 0 until (maxLen - prefix.length)) {
            val expanded = mutableListOf<Pair<String, Double>>()

            for ((text, score) in beam) {
                val nextChars = predictNextChar(text)
                val best = nextChars.take(beamWidth)

                for ((c, prob) in best) {
                    val newText = text + c
                    val newScore = score * prob
                    val word = newText.trim()

                    if (c == " ") {
                        if (word.length > prefix.length &&
                            unigrams.containsKey(word)) {
                            found.add(word)
                        }
                        continue
                    }

                    if (unigrams.containsKey(newText)) {
                        found.add(newText)
                    }

                    expanded.add(newText to newScore)
                }
            }

            beam = expanded.sortedByDescending { it.second }.take(beamWidth)
            if (beam.isEmpty() || found.size >= 3) break
        }

        return found
            .filter { it.length > prefix.length }
            .sortedByDescending { unigrams[it] ?: 0 }
            .take(3)
    }

    fun detectLanguage(word: String): Language {
        val lower = word.lowercase()
        if (lower.isEmpty()) return Language.UNKNOWN
        if (viDiacritics.containsMatchIn(lower)) return Language.VIETNAMESE

        val inVi = lower in viUnigrams
        val inEn = lower in enUnigrams

        return when {
            inVi && !inEn -> Language.VIETNAMESE
            inEn && !inVi -> Language.ENGLISH
            else -> Language.UNKNOWN
        }
    }

    fun isVietnameseWord(word: String): Boolean {
        return word.lowercase() in viUnigrams
    }

    fun isEnglishWord(word: String): Boolean {
        return word.lowercase() in enUnigrams
    }

    fun normalizedFrequency(word: String): Double {
        val lower = word.lowercase()
        val viFreq = viUnigrams[lower]?.toDouble() ?: 0.0
        val enFreq = enUnigrams[lower]?.toDouble() ?: 0.0
        val viNorm = if (viFreqMax > 0) viFreq / viFreqMax else 0.0
        val enNorm = if (enFreqMax > 0) enFreq / enFreqMax else 0.0
        return maxOf(viNorm, enNorm)
    }

    val wordCount: Int get() = allWords.size
    val isLoaded: Boolean get() = loaded
}
