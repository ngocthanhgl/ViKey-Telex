/*
 * Copyright (C) 2026 The ViKey Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.ngocthanhgl.vikey.ime.text.gestures

import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class DTWGlideTypingClassifier(context: Context) : StatisticalGlideTypingClassifier(context) {

    override fun unCachedGetSuggestions(maxSuggestionCount: Int): List<Pair<CharSequence, Float>> {
        val smoothed = gesture.smooth()
        var remaining = pruner.pruneByExtremities(smoothed, keys)
        remaining = pruner.pruneByLength(smoothed, remaining, keysByCharacter, keys)
        if (remaining.isEmpty()) return emptyList()

        val userPoints = smoothed.toPointList()
        val inputLen = userPoints.size
        if (inputLen < 2) return emptyList()

        val results = mutableListOf<Pair<String, Double>>()
        var bestScore = Double.MAX_VALUE
        var bestDtwDist = Double.MAX_VALUE

        for (word in remaining) {
            val rawWord = word
            val idealGestures = Gesture.generateIdealGestures(rawWord, keysByCharacter)
            for (ideal in idealGestures) {
                val wordPoints = ideal.toPointList()
                if (wordPoints.size < 2) continue

                val window = max(max(wordPoints.size, inputLen) / 2, 10)
                val cutoff = if (bestDtwDist < Double.MAX_VALUE) bestDtwDist * 1.5 else Double.MAX_VALUE
                val dtwDist = dtwDistance(userPoints, wordPoints, window, cutoff)

                if (dtwDist == Double.MAX_VALUE) continue

                if (dtwDist < bestDtwDist) {
                    bestDtwDist = dtwDist
                }

                val normalizedDist = dtwDist / inputLen
                val freq = (wordFrequencies[rawWord] ?: 0.0)
                val score = normalizedDist - freq * POP_WEIGHT

                if (score < bestScore) {
                    bestScore = score
                }
                results.add(rawWord to score)
            }
        }

        results.sortBy { it.second }
        return results.take(maxSuggestionCount)
            .map { (word, score) -> word as CharSequence to score.toFloat() }
    }

    private fun dtwDistance(
        s: List<Pair<Float, Float>>,
        t: List<Pair<Float, Float>>,
        window: Int,
        cutoff: Double,
    ): Double {
        val n = s.size
        val m = t.size
        if (n == 0 || m == 0) return Double.MAX_VALUE

        val w = window.coerceAtMost(max(n, m))

        var prev = DoubleArray(m + 1) { Double.MAX_VALUE }
        var curr = DoubleArray(m + 1) { Double.MAX_VALUE }
        prev[0] = 0.0

        for (i in 1..n) {
            curr[0] = Double.MAX_VALUE
            val jStart = max(1, i - w)
            val jEnd = min(m, i + w)

            if (jStart > 1) {
                curr[jStart - 1] = Double.MAX_VALUE
            }

            var rowMin = Double.MAX_VALUE
            for (j in jStart..jEnd) {
                val cost = euclideanDist(s[i - 1], t[j - 1])
                val prevMin = minOf(prev[j], curr[j - 1], prev[j - 1])
                curr[j] = cost + prevMin
                if (curr[j] < rowMin) rowMin = curr[j]
            }

            if (rowMin > cutoff) return Double.MAX_VALUE

            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[m]
    }

    private fun euclideanDist(a: Pair<Float, Float>, b: Pair<Float, Float>): Double {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt((dx * dx + dy * dy).toDouble())
    }

    companion object {
        private const val POP_WEIGHT = 0.25
    }
}
