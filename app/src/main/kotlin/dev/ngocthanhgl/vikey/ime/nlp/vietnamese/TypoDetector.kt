package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

class TypoDetector(private val seedWords: Set<String>) {

    fun detect(typed: String): List<String> {
        val lcTyped = typed.lowercase()
        if (lcTyped.length < 2) return emptyList()

        val corrections = mutableSetOf<String>()

        corrections.addAll(missingToneKey(lcTyped))
        corrections.addAll(adjacentKeySwap(lcTyped))
        corrections.addAll(extraKeyInserted(lcTyped))
        corrections.addAll(missingVowelModifier(lcTyped))
        corrections.addAll(wrongToneKey(lcTyped))

        return corrections
            .filter { it in seedWords }
            .distinct()
            .take(10)
    }

    fun detectAndScore(typed: String): List<Pair<String, Double>> {
        val corrections = detect(typed)
        return corrections.mapIndexed { idx, word ->
            val score = (corrections.size - idx).toDouble() / corrections.size
            word to score
        }
    }

    private fun missingToneKey(typed: String): List<String> {
        val toneKeys = setOf('s', 'f', 'r', 'x', 'j')
        if (typed.last() in toneKeys) return emptyList()
        val result = mutableListOf<String>()
        for (tone in toneKeys) {
            result.add(typed + tone)
        }
        return result
    }

    private fun adjacentKeySwap(typed: String): List<String> {
        val result = mutableListOf<String>()
        val chars = typed.toCharArray()
        for (i in 0 until typed.length - 1) {
            val temp = chars[i]
            chars[i] = chars[i + 1]
            chars[i + 1] = temp
            result.add(String(chars))
            val temp2 = chars[i]
            chars[i] = chars[i + 1]
            chars[i + 1] = temp2
        }
        return result
    }

    private fun extraKeyInserted(typed: String): List<String> {
        val result = mutableListOf<String>()
        for (i in typed.indices) {
            result.add(typed.removeRange(i, i + 1))
        }
        return result
    }

    private fun missingVowelModifier(typed: String): List<String> {
        val vowelModifiers = mapOf(
            "a" to listOf("aa", "aw"),
            "e" to listOf("ee"),
            "o" to listOf("oo", "ow"),
            "u" to listOf("uw"),
        )
        val result = mutableListOf<String>()
        val chars = typed.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            val replacements = vowelModifiers[c.toString()] ?: continue
            for (rep in replacements) {
                val sb = StringBuilder(typed)
                sb.deleteCharAt(i)
                sb.insert(i, rep)
                result.add(sb.toString())
            }
        }
        return result
    }

    private fun wrongToneKey(typed: String): List<String> {
        val tonePairs = listOf(
            's' to 'f', 's' to 'r', 's' to 'x', 's' to 'j',
            'f' to 's', 'f' to 'r', 'f' to 'x', 'f' to 'j',
            'r' to 's', 'r' to 'f', 'r' to 'x', 'r' to 'j',
            'x' to 's', 'x' to 'f', 'x' to 'r', 'x' to 'j',
            'j' to 's', 'j' to 'f', 'j' to 'r', 'j' to 'x',
        )
        val result = mutableListOf<String>()
        val chars = typed.toCharArray()
        for (i in chars.indices) {
            for ((from, to) in tonePairs) {
                if (chars[i] == from) {
                    chars[i] = to
                    result.add(String(chars))
                    chars[i] = from
                }
            }
        }
        return result
    }
}
