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
    private var singleWords: List<String> = emptyList()
    private var mergedCompletionWords: List<String> = emptyList()
    private var mergedByFirstChar: Map<Char, List<String>> = emptyMap()
    private var wordBigrams: Map<String, List<Pair<String, Int>>> = emptyMap()
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

                val sorted = merged.entries
                    .filter { it.key.all { c -> c.isLetter() || c == ' ' } }
                    .sortedByDescending { it.value }
                    .map { it.key }
                singleWords = sorted.filter { !it.contains(" ") }
                mergedCompletionWords = singleWords.take(COMPLETION_LIMIT)
                mergedByFirstChar = mergedCompletionWords.groupBy { it[0] }

                buildWordNGrams()
                loaded = true

                Log.i(TAG, "Loaded ${sorted.size} words, " +
                    "${singleWords.size} singles, " +
                    "${wordBigrams.size} word bigrams")
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

    private fun buildWordNGrams() {
        val bi = mutableMapOf<String, MutableMap<String, Int>>()

        for ((phrase, freq) in unigrams) {
            val parts = phrase.split(" ")
            if (parts.size >= 2) {
                for (i in 0 until parts.size - 1) {
                    val w1 = parts[i].lowercase()
                    val w2 = parts[i + 1].lowercase()
                    bi.getOrPut(w1) { mutableMapOf() }.merge(w2, freq) { a, b -> a + b }
                }
            }
        }

        wordBigrams = bi.mapValues { (_, v) ->
            v.entries.sortedByDescending { it.value }.map { it.key to it.value }
        }
    }

    fun completePrefix(prefix: String, maxCount: Int = 8): List<String> {
        return try {
            completePrefixImpl(prefix, maxCount)
        } catch (e: Exception) {
            Log.w(TAG, "completePrefix failed: ${e.message}")
            emptyList()
        }
    }

    private fun completePrefixImpl(prefix: String, maxCount: Int = 8): List<String> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()
        val first = lower[0]

        val group = mergedByFirstChar[first]
        if (group != null) {
            val exact = group.filter { it.startsWith(lower) }.take(maxCount)
            if (exact.isNotEmpty()) return exact
            val fuzzy = group.filter { it.contains(lower) }.take(maxCount)
            if (fuzzy.isNotEmpty()) return fuzzy
        }

        return mergedCompletionWords.filter { it.contains(lower) }.take(maxCount)
    }

    fun predictNextWords(context: String, maxCount: Int = 2): List<String> {
        return try {
            predictNextWordsImpl(context, maxCount)
        } catch (e: Exception) {
            Log.w(TAG, "predictNextWords failed: ${e.message}")
            emptyList()
        }
    }

    private fun predictNextWordsImpl(context: String, maxCount: Int = 2): List<String> {
        val words = context.split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
        if (words.isEmpty()) return emptyList()

        val lastWord = words.last()
        val bmap = wordBigrams[lastWord]
        if (bmap != null) {
            return bmap.take(maxCount).map { it.first }
        }

        return emptyList()
    }

    val isLoaded: Boolean get() = loaded
}
