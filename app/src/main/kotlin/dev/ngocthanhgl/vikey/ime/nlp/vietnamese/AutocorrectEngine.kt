package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import kotlin.math.max
import kotlin.math.min

class AutocorrectEngine(private val seedWords: Set<String>) {

    data class CorrectionCandidate(
        val word: String,
        val editDistance: Int,
        val proximityScore: Double,
    )

    fun correct(prefix: String, maxCandidates: Int = 10): List<CorrectionCandidate> {
        val lcPrefix = prefix.lowercase()
        val candidates = mutableListOf<CorrectionCandidate>()

        for (word in seedWords) {
            if (word == lcPrefix) {
                candidates.add(CorrectionCandidate(word, 0, 1.0))
                continue
            }
            val dist = levenshtein(lcPrefix, word)
            if (dist <= 2 && word.length >= lcPrefix.length - 1 && word.length <= lcPrefix.length + 3) {
                val prox = proximityScore(lcPrefix, word)
                candidates.add(CorrectionCandidate(word, dist, prox))
            }
        }

        return candidates
            .sortedWith(compareBy({ it.editDistance }, { -it.proximityScore }))
            .take(maxCandidates)
    }

    fun score(
        typed: String,
        candidate: String,
        qwenScore: Double? = null,
    ): Double {
        val lcTyped = typed.lowercase()
        val lcCandidate = candidate.lowercase()
        val dist = levenshtein(lcTyped, lcCandidate)
        val edScore = 1.0 / (1.0 + dist)
        val prox = proximityScore(lcTyped, lcCandidate)
        val lengthRatio = lcCandidate.length.toDouble() / max(lcTyped.length, 1).coerceAtLeast(1)

        val qwenPart = (qwenScore ?: 0.5) * 0.4
        val edPart = edScore * 0.3
        val proxPart = prox * 0.2
        val lenPart = lengthRatio.coerceIn(0.0, 1.0) * 0.1

        return qwenPart + edPart + proxPart + lenPart
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }

    private fun proximityScore(typed: String, candidate: String): Double {
        val typedLc = typed.lowercase()
        val candLc = candidate.lowercase()
        var matches = 0.0
        var total = 0.0

        val minLen = min(typedLc.length, candLc.length)
        for (i in 0 until minLen) {
            val t = typedLc[i]
            val c = candLc[i]
            if (t == c) {
                matches += 1.0
            } else {
                val adj = adjacency[t] ?: emptySet()
                if (c in adj) {
                    matches += 0.5
                }
            }
            total += 1.0
        }

        val extraPenalty = max(0, typedLc.length - candLc.length) * 0.2
        return (matches / max(total, 1.0)) - extraPenalty
    }

    companion object {
        private val adjacency = mapOf(
            'q' to setOf('w', 'a'),
            'w' to setOf('q', 'e', 's', 'a'),
            'e' to setOf('w', 'r', 'd', 's'),
            'r' to setOf('e', 't', 'f', 'd'),
            't' to setOf('r', 'y', 'g', 'f'),
            'y' to setOf('t', 'u', 'h', 'g'),
            'u' to setOf('y', 'i', 'j', 'h'),
            'i' to setOf('u', 'o', 'k', 'j'),
            'o' to setOf('i', 'p', 'l', 'k'),
            'p' to setOf('o', 'l'),
            'a' to setOf('q', 'w', 's', 'z'),
            's' to setOf('a', 'w', 'e', 'd', 'x', 'z'),
            'd' to setOf('s', 'e', 'r', 'f', 'c', 'x'),
            'f' to setOf('d', 'r', 't', 'g', 'v', 'c'),
            'g' to setOf('f', 't', 'y', 'h', 'b', 'v'),
            'h' to setOf('g', 'y', 'u', 'j', 'n', 'b'),
            'j' to setOf('h', 'u', 'i', 'k', 'm', 'n'),
            'k' to setOf('j', 'i', 'o', 'l', 'm'),
            'l' to setOf('k', 'o', 'p'),
            'z' to setOf('a', 's', 'x'),
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k'),
        )
    }
}
