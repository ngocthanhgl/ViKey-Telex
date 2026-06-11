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
import kotlin.math.sqrt

class CharNGramPredictor(context: Context) {
    companion object {
        private const val TAG = "CharNGramPredictor"
        private const val VI_DICT = "ime/dict/vi.json"
        private const val EN_DICT = "ime/dict/data.json"
    }

    private val appContext by context.appContext()

    private var unigrams: Map<String, Int> = emptyMap()
    private var bigrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var trigrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var allWords: List<String> = emptyList()
    private var loaded = false

    suspend fun load() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            try {
                val viWords = loadDict(VI_DICT)
                val enWords = loadDict(EN_DICT)
                val merged = viWords.toMutableMap()
                enWords.forEach { (k, v) -> merged.merge(k, v) { a, b -> a.coerceAtLeast(b) } }
                unigrams = merged
                allWords = merged.entries
                    .filter { it.key.all { c -> c.isLetter() || c == ' ' } }
                    .sortedByDescending { it.value }
                    .map { it.key }
                buildNGrams()
                loaded = true
                Log.i(TAG, "Loaded ${allWords.size} words, ${bigrams.size} bigrams, ${trigrams.size} trigrams")
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
        for (word in allWords) {
            val w = " $word "
            for (i in 0 until w.length - 1) {
                val c1 = w[i].toString()
                val c2 = w[i + 1].toString()
                bigrams.getOrPut(c1) { mutableMapOf() }.merge(c2, 1) { a, b -> a + b }
            }
            for (i in 0 until w.length - 2) {
                val c1 = w[i].toString()
                val c2 = w[i + 1].toString()
                val c3 = w[i + 2].toString()
                val key = "$c1$c2"
                trigrams.getOrPut(key) { mutableMapOf() }.merge(c3, 1) { a, b -> a + b }
            }
        }
    }

    fun predictNextChar(context: String): List<Pair<String, Double>> {
        if (context.length >= 2) {
            val lastTwo = context.takeLast(2)
            val trigramMap = trigrams[lastTwo]
            if (trigramMap != null) {
                val total = trigramMap.values.sum().toDouble()
                return trigramMap.entries
                    .sortedByDescending { it.value }
                    .map { (c, count) -> c to count / total }
                    .take(5)
            }
        }
        if (context.isNotEmpty()) {
            val lastChar = context.last().toString()
            val bigramMap = bigrams[lastChar]
            if (bigramMap != null) {
                val total = bigramMap.values.sum().toDouble()
                return bigramMap.entries
                    .sortedByDescending { it.value }
                    .map { (c, count) -> c to count / total }
                    .take(5)
            }
        }
        return listOf(" " to 0.3, "a" to 0.1, "c" to 0.1, "t" to 0.1, "n" to 0.1)
    }

    fun wordScore(word: String): Double {
        val freq = unigrams[word] ?: return 0.0
        var score = freq / 255.0
        val w = " $word "
        for (i in 0 until w.length - 1) {
            val c1 = w[i].toString()
            val c2 = w[i + 1].toString()
            val bg = bigrams[c1]?.get(c2) ?: 0
            if (bg > 0) score *= 1.0 + sqrt(bg.toDouble()) * 0.01
        }
        return score
    }

    fun completePrefix(prefix: String, maxCount: Int = 8): List<String> {
        if (prefix.isBlank()) {
            return allWords.take(maxCount)
        }
        val lower = prefix.lowercase()
        val scored = allWords
            .filter { it.startsWith(lower) }
            .map { it to wordScore(it) }
            .sortedByDescending { it.second }
            .take(maxCount)
            .map { it.first }

        if (scored.isNotEmpty()) return scored

        return allWords
            .filter { it.contains(lower) }
            .map { it to wordScore(it) }
            .sortedByDescending { it.second }
            .take(maxCount)
            .map { it.first }
    }

    fun unrollWord(prefix: String, maxLen: Int = 20): List<String> {
        if (prefix.length >= maxLen) return emptyList()
        val suggestions = mutableListOf<String>()
        val exact = allWords.filter { it == prefix.lowercase() }
        if (exact.isNotEmpty()) return exact

        var current = prefix.lowercase()
        for (step in 0 until 10) {
            val nextChars = predictNextChar(current)
            val best = nextChars.firstOrNull() ?: break
            val nextChar = best.first
            if (nextChar == " ") {
                val word = current.trim()
                if (word.length > prefix.length) {
                    suggestions.add(word)
                }
                break
            }
            current += nextChar
            if (current.length > maxLen) break
            if (allWords.any { it == current }) {
                suggestions.add(current)
                if (suggestions.size >= 3) break
            }
        }
        return suggestions
    }

    val wordCount: Int get() = allWords.size
    val isLoaded: Boolean get() = loaded
}
