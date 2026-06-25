/*
 * Copyright (C) 2026 NgocThanhGL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.ngocthanhgl.vikey.ime.text.composing

import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import java.text.Normalizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@SerialName("telex-algorithm")
class AlgorithmicTelex(
    override val id: String = "telex",
    override val label: String = "Telex",
) : Composer {

    override val toRead = 32

    @Transient
    private val prefs by FlorisPreferenceStore

    private val telexWEnabled: Boolean
        get() = try { prefs.keyboard.telexWEnabled.get() } catch (_: Exception) { true }

    private val englishFallbackEnabled: Boolean
        get() = try { prefs.keyboard.englishFallbackEnabled.get() } catch (_: Exception) { true }

    // ── Character classification ──────────────────────────────────

    private val toneKeys = setOf('s', 'f', 'r', 'x', 'j')

    private val baseVowels = setOf(
        'a', 'ă', 'â', 'e', 'ê', 'i',
        'o', 'ô', 'ơ', 'u', 'ư', 'y',
    )

    private val consonantLetters = setOf(
        'b', 'c', 'd', 'đ', 'g', 'h', 'j', 'k', 'l', 'm', 'n',
        'p', 'q', 'r', 's', 't', 'v', 'x',
    )

    // ── Vietnamese onset consonants (longest first) ───────────────

    private val knownOnsets = listOf(
        "ngh", "ng", "ch", "gh", "gi", "kh", "nh", "ph", "th", "tr", "qu",
        "b", "c", "d", "đ", "g", "h", "k", "l", "m", "n",
        "p", "r", "s", "t", "v", "x",
    )

    // ── Vietnamese coda consonants ────────────────────────────────

    private val knownCodas = listOf(
        "ch", "ng", "nh", "c", "m", "n", "p", "t",
    )

    private val semivowelCodas = setOf('u', 'i', 'y', 'o')

    // ── Telex shortcut maps ───────────────────────────────────────

    private val shortcuts2 = mapOf(
        "aw" to 'ă',
        "aa" to 'â',
        "ee" to 'ê',
        "oo" to 'ô',
        "ow" to 'ơ',
        "uw" to 'ư',
        "dd" to 'đ',
    )

    private val shortcuts3 = mapOf(
        "uow" to "ươ",
    )

    private val reverseShortcuts = mapOf(
        'ă' to ('a' to 'w'),
        'â' to ('a' to 'a'),
        'ê' to ('e' to 'e'),
        'ô' to ('o' to 'o'),
        'ơ' to ('o' to 'w'),
        'ư' to ('u' to 'w'),
        'đ' to ('d' to 'd'),
    )

    // ── Tone maps ─────────────────────────────────────────────────

    private val toneMaps = mapOf(
        's' to mapOf(
            'a' to 'á', 'ă' to 'ắ', 'â' to 'ấ',
            'e' to 'é', 'ê' to 'ế',
            'i' to 'í',
            'o' to 'ó', 'ô' to 'ố', 'ơ' to 'ớ',
            'u' to 'ú', 'ư' to 'ứ', 'y' to 'ý',
        ),
        'f' to mapOf(
            'a' to 'à', 'ă' to 'ằ', 'â' to 'ầ',
            'e' to 'è', 'ê' to 'ề',
            'i' to 'ì',
            'o' to 'ò', 'ô' to 'ồ', 'ơ' to 'ờ',
            'u' to 'ù', 'ư' to 'ừ', 'y' to 'ỳ',
        ),
        'r' to mapOf(
            'a' to 'ả', 'ă' to 'ẳ', 'â' to 'ẩ',
            'e' to 'ẻ', 'ê' to 'ể',
            'i' to 'ỉ',
            'o' to 'ỏ', 'ô' to 'ổ', 'ơ' to 'ở',
            'u' to 'ủ', 'ư' to 'ử', 'y' to 'ỷ',
        ),
        'x' to mapOf(
            'a' to 'ã', 'ă' to 'ẵ', 'â' to 'ẫ',
            'e' to 'ẽ', 'ê' to 'ễ',
            'i' to 'ĩ',
            'o' to 'õ', 'ô' to 'ỗ', 'ơ' to 'ỡ',
            'u' to 'ũ', 'ư' to 'ữ', 'y' to 'ỹ',
        ),
        'j' to mapOf(
            'a' to 'ạ', 'ă' to 'ặ', 'â' to 'ậ',
            'e' to 'ẹ', 'ê' to 'ệ',
            'i' to 'ị',
            'o' to 'ọ', 'ô' to 'ộ', 'ơ' to 'ợ',
            'u' to 'ụ', 'ư' to 'ự', 'y' to 'ỵ',
        ),
    )

    // ── Vietnamese orthographic tone placement rules ──────────────

    private val toneRules = mapOf(
        "oa" to 'a', "oe" to 'e', "uy" to 'y',
        "ưa" to 'ư', "ươ" to 'ơ', "uô" to 'ô',
        "ua" to 'u', "iê" to 'ê', "yê" to 'ê',
        "uyê" to 'ê', "uya" to 'y', "uye" to 'y',
        "uôi" to 'ô', "ươi" to 'ơ', "ươu" to 'ơ',
        "oai" to 'a', "oay" to 'a', "uay" to 'a',
        "oeo" to 'e', "oeu" to 'e',
        "ia" to 'i', "ya" to 'y',
        "iêu" to 'ê', "yêu" to 'ê',
        "ai" to 'a', "ay" to 'a', "au" to 'a', "ao" to 'a',
        "oi" to 'o', "ôi" to 'ô', "ơi" to 'ơ',
        "ui" to 'u', "ưi" to 'ư',
        "eo" to 'e', "êu" to 'ê',
        "iu" to 'i', "ưu" to 'ư',
        "ây" to 'â',
    )

    // ── English fallback patterns ─────────────────────────────────

    private val englishPatterns = listOf(
        "tion", "ness", "ship", "less", "able", "ment",
        "sch", "ck", "dge", "scr", "str",
        "ould", "ight", "ough",
    )

    // ──────────────────────────────────────────────────────────────
    //  Syllable model
    // ──────────────────────────────────────────────────────────────

    @Suppress("unused")
    private data class Syllable(
        val onset: String = "",
        val nucleus: String = "",
        val coda: String = "",
        val tone: Char? = null,
    )

    // ──────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────

    override fun getActions(precedingText: String, toInsert: String): Pair<Int, String> {
        if (toInsert.length != 1) return 0 to toInsert

        val normalized = Normalizer.normalize(precedingText, Normalizer.Form.NFC)
        val ch = toInsert[0]

        if (normalized.isEmpty()) return 0 to firstChar(ch)
        if (!normalized.last().isLetter()) return 0 to ch.toString()

        if (ch.lowercaseChar() == 'z') {
            return handleCancel(normalized)
        }

        val word = lastWord(normalized)
        if (word.isEmpty()) {
            return 0 to firstChar(ch)
        }

        return processWord(word, ch)
    }

    // ──────────────────────────────────────────────────────────────
    //  First character in a new word
    // ──────────────────────────────────────────────────────────────

    private fun firstChar(ch: Char): String {
        if (ch.lowercaseChar() == 'w') {
            if (!telexWEnabled) return ch.toString()
            return if (ch.isUpperCase()) "Ư" else "ư"
        }
        return ch.toString()
    }

    // ──────────────────────────────────────────────────────────────
    //  Process a keypress on the current word (syllable recomposition)
    // ──────────────────────────────────────────────────────────────

    private fun processWord(word: String, ch: Char): Pair<Int, String> {
        val lowerCh = ch.lowercaseChar()

        if (lowerCh in toneKeys) {
            if (word.isNotEmpty()) {
                val candidate = "${word.last().lowercaseChar()}$lowerCh"
                if (knownOnsets.contains(candidate)) {
                    return word.length to (word + ch)
                }
            }
            if (isEnglishLikely(word)) {
                return word.length to (word + ch)
            }
            return handleTone(word, ch)
        }

        if (lowerCh == 'w' && word.all { it.lowercaseChar() == 'w' }) {
            return word.length to (word + ch)
        }

        if (lowerCh == 'w' && word.length == 1 && word.single().lowercaseChar() == 'ư') {
            return word.length to ch.toString()
        }

        if (lowerCh == 'w' && word.last().lowercaseChar() == 'ư' && word.length > 1) {
            return word.length to (word.dropLast(1) + ch)
        }

        if (isShortcutUndo(word, ch)) {
            return doShortcutUndo(word, ch)
        }

        val shortcut = applyShortcut(word, ch)
        if (shortcut != null) {
            return word.length to shortcut
        }

        if (lowerCh == 'w') {
            return handleW(word, ch)
        }

        return word.length to (word + ch)
    }

    // ──────────────────────────────────────────────────────────────
    //  Tone handling
    // ──────────────────────────────────────────────────────────────

    private fun handleTone(word: String, ch: Char): Pair<Int, String> {
        val toneKey = ch.lowercaseChar()
        val clean = stripTones(word)

        val syllable = parseSyllable(clean.lowercase())
        if (syllable == null || syllable.nucleus.isEmpty()) {
            return word.length to (word + ch)
        }

        val tonePos = resolveTonePosition(clean, syllable)
        if (tonePos < 0) {
            return word.length to (word + ch)
        }

        val current = word[tonePos]
        val base = toBaseForm(current)
        val toned = toneMaps[toneKey]?.get(base) ?: current

        if (current.lowercaseChar() == toned) {
            val before = word.substring(0, tonePos)
            val after = word.substring(tonePos + 1)
            val casedBase = if (current.isUpperCase()) base.uppercaseChar() else base
            return word.length to (before + casedBase + after + ch)
        }

        val chars = word.toCharArray()
        chars[tonePos] = if (current.isUpperCase()) toned.uppercaseChar() else toned
        return word.length to String(chars)
    }

    private fun handleCancel(precedingText: String): Pair<Int, String> {
        val word = lastWord(precedingText)
        if (word.isEmpty()) return 0 to "z"

        val clean = stripTones(word)
        if (clean == word) {
            return word.length to (word + "z")
        }
        return word.length to clean
    }

    // ──────────────────────────────────────────────────────────────
    //  Shortcut handling
    // ──────────────────────────────────────────────────────────────

    private fun isShortcutUndo(word: String, ch: Char): Boolean {
        if (word.isEmpty()) return false
        val last = word.last().lowercaseChar()
        val expected = reverseShortcuts[last]?.second ?: return false
        return ch.lowercaseChar() == expected
    }

    private fun doShortcutUndo(word: String, ch: Char): Pair<Int, String> {
        val last = word.last().lowercaseChar()
        val pair = reverseShortcuts[last] ?: return 0 to (word + ch)
        val prefix = word.dropLast(1)
        val first = if (word.last().isUpperCase()) {
            pair.first.uppercaseChar()
        } else {
            pair.first
        }
        val second = if (ch.isUpperCase()) pair.second.uppercaseChar() else pair.second
        return word.length to (prefix + first + second)
    }

    private fun applyShortcut(word: String, ch: Char): String? {
        val lowerCh = ch.lowercaseChar()

        if (word.length >= 2) {
            val tail2 = word.substring(word.length - 2).lowercase()
            val key3 = tail2 + lowerCh
            val result3 = shortcuts3[key3]
            if (result3 != null) {
                val mode = casingMode(word.substring(word.length - 2))
                return word.dropLast(2) + applyCasing(result3, mode)
            }
        }

        val last = word.last().lowercaseChar()
        val key2 = "$last$lowerCh"
        val result2 = shortcuts2[key2]
        if (result2 != null) {
            val mode = casingMode(word.last().toString())
            return word.dropLast(1) + applyCasing(result2.toString(), mode)
        }

        return null
    }

    // ──────────────────────────────────────────────────────────────
    //  Standalone w → ư / Ư
    // ──────────────────────────────────────────────────────────────

    private fun handleW(word: String, ch: Char): Pair<Int, String> {
        if (ch.isLetter().not()) return word.length to (word + ch)
        if (!telexWEnabled) return word.length to (word + ch)
        val last = word.lastOrNull()

        if (last?.lowercaseChar() == 'ư' && word.length > 1) {
            val uChar = if (last.isUpperCase()) 'U' else 'u'
            return word.length to (word.dropLast(1) + uChar + ch)
        }

        if (last?.lowercaseChar() == 'w') {
            return word.length to (word + ch)
        }

        val lastBase = last?.let { toBaseForm(it.lowercaseChar()) }
        if (lastBase != null && lastBase in baseVowels) {
            return word.length to (word + ch)
        }

        val uChar = if (ch.isUpperCase()) 'Ư' else 'ư'
        return word.length to (word + uChar)
    }

    // ──────────────────────────────────────────────────────────────
    //  Syllable parser
    // ──────────────────────────────────────────────────────────────

    private fun parseSyllable(clean: String): Syllable? {
        if (clean.isEmpty()) return null

        var remaining = clean
        var onset = ""

        for (o in knownOnsets) {
            if (remaining.startsWith(o)) {
                val candidate = remaining.removePrefix(o)
                val hasVowel = candidate.any { toBaseForm(it) in baseVowels }
                val multiEndsInVowel = o.length > 1 && toBaseForm(o.last()) in baseVowels
                if (hasVowel || o.length == 1 || !multiEndsInVowel) {
                    onset = o
                    remaining = candidate
                    break
                }
            }
        }

        if (remaining.isEmpty()) return Syllable(onset = onset)

        var coda = ""

        for (c in knownCodas) {
            if (remaining.endsWith(c)) {
                coda = c
                remaining = remaining.removeSuffix(c)
                break
            }
        }

        if (!coda.isEmpty() && remaining.isEmpty()) {
            return Syllable(onset = onset, nucleus = "", coda = coda)
        }

        if (remaining.endsWith('u') || remaining.endsWith('i') ||
            remaining.endsWith('y') || remaining.endsWith('o')
        ) {
            val last = remaining.last()
            if (remaining.length > 1 && last in semivowelCodas) {
                val before = remaining.dropLast(1)
                if (before.any { toBaseForm(it) in baseVowels }) {
                    coda = last.toString()
                    remaining = before
                }
            }
        }

        if (remaining.isEmpty()) return Syllable(onset = onset, nucleus = "", coda = coda)

        val nucleus = remaining
        return Syllable(onset = onset, nucleus = nucleus, coda = coda)
    }

    // ──────────────────────────────────────────────────────────────
    //  Tone position resolver (Vietnamese orthographic rules)
    // ──────────────────────────────────────────────────────────────

    private fun resolveTonePosition(word: String, syllable: Syllable): Int {
        val vowelPositions = findVowelPositions(word)
        if (vowelPositions.isEmpty()) return -1
        if (vowelPositions.size == 1) return vowelPositions[0]

        val vowelCluster = buildString {
            for (pos in vowelPositions) {
                append(toBaseForm(word[pos].lowercaseChar()))
            }
        }

        val rule = toneRules[vowelCluster]
        if (rule != null) {
            for (pos in vowelPositions) {
                if (toBaseForm(word[pos].lowercaseChar()) == rule) {
                    return pos
                }
            }
        }

        for (pos in vowelPositions) {
            val b = toBaseForm(word[pos].lowercaseChar())
            if (b == 'ê' || b == 'ơ') return pos
        }

        for (pos in vowelPositions) {
            val b = toBaseForm(word[pos].lowercaseChar())
            if (b == 'â' || b == 'ă' || b == 'ô') return pos
        }

        return vowelPositions.last()
    }

    // ──────────────────────────────────────────────────────────────
    //  Vowel position finder (handles gi/qu exceptions)
    // ──────────────────────────────────────────────────────────────

    private fun findVowelPositions(word: String): List<Int> {
        val lower = word.lowercase()
        val result = mutableListOf<Int>()

        for (i in lower.indices) {
            val c = lower[i]

            if (toBaseForm(c) !in baseVowels) continue

            if (c == 'i' && i == 1 && lower.startsWith("gi") && lower.length > 2) continue

            if (c == 'u' && i == 1 && lower.startsWith("qu") && lower.length > 2) continue

            result.add(i)
        }

        return result
    }

    // ── English fallback detection ─────────────────────────────────

    private val vietnameseChars = setOf(
        'ă', 'â', 'đ', 'ê', 'ô', 'ơ', 'ư',
        'á', 'à', 'ả', 'ã', 'ạ',
        'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ',
        'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ',
        'é', 'è', 'ẻ', 'ẽ', 'ẹ',
        'ế', 'ề', 'ể', 'ễ', 'ệ',
        'í', 'ì', 'ỉ', 'ĩ', 'ị',
        'ó', 'ò', 'ỏ', 'õ', 'ọ',
        'ố', 'ồ', 'ổ', 'ỗ', 'ộ',
        'ớ', 'ờ', 'ở', 'ỡ', 'ợ',
        'ú', 'ù', 'ủ', 'ũ', 'ụ',
        'ứ', 'ừ', 'ử', 'ữ', 'ự',
        'ý', 'ỳ', 'ỷ', 'ỹ', 'ỵ',
    )

    private val extendedEnglishPatterns = listOf(
        "ing", "ful", "ive", "ure", "sion", "ist",
        "ize", "ise", "ward", "wise", "like",
        "hood", "dom", "ous", "ly", "ed", "er", "est",
    )

    private val extendedClusters = setOf(
        "mp", "ld", "nk", "rk", "rm", "rn", "rt", "sk", "sp",
        "ft", "pt", "ct", "lp", "lf", "lk", "lm", "ln",
    )

    private val validVietnameseOnsets = setOf("ch", "gh", "gi", "kh", "nh", "ng", "ph", "qu", "th", "tr")

    companion object {
        private const val VIET_DIGRAPHS =
            "ưa|ươ|uô|iê|yê|uya|uyê|ươi|ươu|uôi|oai|oay"
        private val vietDigraphList = VIET_DIGRAPHS.split("|")
    }

    private fun isEnglishLikely(word: String): Boolean {
        val lower = word.lowercase()

        // ── Original checks (run in both modes) ──
        if (englishPatterns.any { lower.contains(it) }) return true

        if (lower.length <= 4) {
            val hasVietDigraph = vietDigraphList.any { lower.contains(it) }
            if (!hasVietDigraph) {
                if (lower.endsWith("ck") || lower.endsWith("sh") ||
                    lower.endsWith("ch") || lower.endsWith("th") ||
                    lower.endsWith("ph") || lower.endsWith("nd") ||
                    lower.endsWith("nt") || lower.endsWith("st")
                ) return true
            }
        }

        for (codaLen in minOf(3, lower.length - 1) downTo 1) {
            val suffix = lower.takeLast(codaLen)
            if (suffix.all { it in consonantLetters }) {
                if (isInvalidVietnameseCoda(suffix)) return true
                break
            }
        }

        val cleaned = stripTones(lower)
        val consonantRun = cleaned.split(Regex("[aeiouyăâêôơ]")).filter { it.isNotEmpty() }
        if (consonantRun.any { it.length > 3 }) return true

        val vowelCount = lower.count { toBaseForm(it) in baseVowels }
        if (vowelCount == 0 && lower.any { it in consonantLetters }) return true

        // ── Enhanced checks (only when toggle ON) ──
        if (!englishFallbackEnabled) return false

        // Word already has Vietnamese diacritics → definitely Vietnamese
        if (lower.any { it in vietnameseChars }) return false

        // Extended English patterns
        if (extendedEnglishPatterns.any { lower.contains(it) }) return true

        // Extended coda clusters (invalid Vietnamese codas)
        if (lower.length >= 2) {
            val suffix2 = lower.takeLast(2)
            if (suffix2 in extendedClusters) return true
        }

        // Onset cluster check — start of word has cluster invalid in Vietnamese
        if (lower.length >= 2) {
            val firstTwo = lower.take(2)
            if (firstTwo.all { it in consonantLetters } && firstTwo !in validVietnameseOnsets) return true
            if (lower.length >= 3) {
                val firstThree = lower.take(3)
                if (firstThree.all { it in consonantLetters } && firstThree != "ngh") return true
            }
        }

        return false
    }

    private val validSingleCodas = setOf('c', 'm', 'n', 'p', 't')

    private fun isInvalidVietnameseCoda(coda: String): Boolean {
        if (coda.length == 1) {
            return coda[0].lowercaseChar() !in validSingleCodas
        }
        if (coda.length == 2) {
            return coda !in listOf("ch", "ng", "nh")
        }
        if (coda.length == 3) {
            return coda != "ngh"
        }
        return true
    }

    // ──────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────

    private fun lastWord(text: String): String {
        val t = text.trimEnd()
        val i = t.lastIndexOf(' ')
        val candidate = if (i < 0) t else t.substring(i + 1)
        return candidate.takeLastWhile { it.isLetter() }
    }

    private fun stripTones(text: String): String {
        return buildString {
            for (c in text) {
                append(toBaseForm(c))
            }
        }
    }

    private fun toBaseForm(c: Char): Char {
        return when (c.lowercaseChar()) {
            'a', 'á', 'à', 'ả', 'ã', 'ạ' -> 'a'
            'ă', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ' -> 'ă'
            'â', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ' -> 'â'
            'e', 'é', 'è', 'ẻ', 'ẽ', 'ẹ' -> 'e'
            'ê', 'ế', 'ề', 'ể', 'ễ', 'ệ' -> 'ê'
            'i', 'í', 'ì', 'ỉ', 'ĩ', 'ị' -> 'i'
            'o', 'ó', 'ò', 'ỏ', 'õ', 'ọ' -> 'o'
            'ô', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ' -> 'ô'
            'ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ' -> 'ơ'
            'u', 'ú', 'ù', 'ủ', 'ũ', 'ụ' -> 'u'
            'ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự' -> 'ư'
            'y', 'ý', 'ỳ', 'ỷ', 'ỹ', 'ỵ' -> 'y'
            'đ' -> 'd'
            else -> c
        }
    }

    private fun casingMode(sample: String): CaseMode {
        val letters = sample.filter { it.isLetter() }
        if (letters.isEmpty()) return CaseMode.LOWER
        if (letters.all { it.isUpperCase() }) return CaseMode.UPPER
        if (letters.first().isUpperCase() && letters.drop(1).all { it.isLowerCase() }) {
            return CaseMode.CAPITALIZED
        }
        return CaseMode.LOWER
    }

    private fun applyCasing(text: String, mode: CaseMode): String {
        return when (mode) {
            CaseMode.UPPER -> text.uppercase()
            CaseMode.CAPITALIZED -> text.replaceFirstChar { it.uppercase() }
            CaseMode.LOWER -> text
        }
    }

    private enum class CaseMode { LOWER, CAPITALIZED, UPPER }
}
