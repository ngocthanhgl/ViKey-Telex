/*
 * Copyright (C) 2026 ViKey Telex contributors
 *
 * Licensed under the GNU General Public License v3.0 or later.
 */

package dev.ngocthanhgl.vikey.ime.text.gestures

import android.content.Context
import dev.ngocthanhgl.vikey.editorInstance
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.keyboard.KeyData
import dev.ngocthanhgl.vikey.ime.nlp.ngramTokens
import dev.ngocthanhgl.vikey.ime.text.keyboard.TextKey

/**
 * English glide typing backed by the FUTO Swipe encoder + decoder + context LM.
 * The models are fully offline and bundled in the APK.
 */
class FutoGlideTypingClassifier(private val context: Context) : GlideTypingClassifier {

    companion object {
        private val CONTRACTIONS = mapOf(
            "dont" to "don't",
            "doesnt" to "doesn't",
            "didnt" to "didn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't",
            "havent" to "haven't",
            "hasnt" to "hasn't",
            "hadnt" to "hadn't",
            "im" to "i'm",
            "ive" to "i've",
            "youre" to "you're",
            "youve" to "you've",
            "theyre" to "they're",
            "theyve" to "they've",
            "thats" to "that's",
            "whats" to "what's",
            "wheres" to "where's",
            "theres" to "there's",
            "heres" to "here's",
        )
        private const val MAX_POINTS = 4000
    }

    private data class Point(val x: Float, val y: Float, val time: Long)
    private data class LetterKey(val letter: Char, val cx: Float, val cy: Float)

    private val engine = FutoSwipeEngine(context)
    private val editorInstance by context.editorInstance()
    private val points = ArrayList<Point>(256)
    private var letterKeys: List<LetterKey> = emptyList()
    private var layoutKeyRefs: List<TextKey> = emptyList()
    private var layoutSubtype: Subtype? = null
    private var layoutValid = false

    override val ready: Boolean
        get() = layoutValid && engine.isReady

    override val supportsPreview: Boolean
        get() = false

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        if (points.size >= MAX_POINTS) return
        points.add(Point(position.x, position.y, position.time))
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        points.clear()
        pointerData.positions.forEach { addGesturePoint(it) }
    }

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        if (layoutSubtype == subtype && layoutKeyRefs == keyViews) return
        layoutSubtype = subtype
        layoutKeyRefs = keyViews

        val byLetter = HashMap<Char, LetterKey>(32)
        for (key in keyViews) {
            val code = (key.data as? KeyData)?.code ?: continue
            val letter = code.toChar().lowercaseChar()
            if (letter !in 'a'..'z') continue
            val center = key.visibleBounds.center
            byLetter[letter] = LetterKey(letter, center.x, center.y)
        }
        if (byLetter.size != 26) {
            layoutValid = false
            letterKeys = emptyList()
            return
        }

        letterKeys = ('a'..'z').map { byLetter.getValue(it) }
        val minX = letterKeys.minOf { it.cx }
        val maxX = letterKeys.maxOf { it.cx }
        val minY = letterKeys.minOf { it.cy }
        val maxY = letterKeys.maxOf { it.cy }
        val spanX = maxX - minX
        val spanY = maxY - minY
        if (spanX <= 1f || spanY <= 1f) {
            layoutValid = false
            return
        }

        layoutValid = true
        engine.setLayout(
            FutoSwipeEngine.Layout(
                letters = letterKeys.joinToString("") { it.letter.toString() },
                xs = FloatArray(26) { (letterKeys[it].cx - minX) / spanX },
                ys = FloatArray(26) { (letterKeys[it].cy - minY) / spanY },
            )
        )
    }

    override fun setWordData(subtype: Subtype) {
        // The FUTO dictionary is static; no word-data rebuild required.
    }

    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<String> {
        if (!gestureCompleted || !ready || points.size < 2) return emptyList()

        val n = points.size
        val xs = FloatArray(n) { points[it].x }
        val ys = FloatArray(n) { points[it].y }
        val firstTime = points.first().time
        val times = FloatArray(n) { i ->
            val raw = points[i].time - firstTime
            if (raw > 0L) raw.toFloat() else 0f
        }
        // Some MotionEvent streams (or injected events) have no useful clock.
        if (times.all { it <= 0f }) {
            for (i in 0 until n) times[i] = i * (1000f / 60f)
        } else {
            for (i in 1 until n) {
                if (times[i] <= times[i - 1]) times[i] = times[i - 1] + 0.1f
            }
        }

        val words = engine.recognize(
            x = xs,
            y = ys,
            t = times,
            contextWords = contextWords(),
            topK = maxSuggestionCount.coerceIn(1, 8),
        )
        if (words.isEmpty()) return emptyList()
        return words.map { CONTRACTIONS[it] ?: it }
    }

    override fun decodeTrailLetters(): String? {
        if (points.isEmpty() || letterKeys.isEmpty()) return null
        val sb = StringBuilder()
        var last: Char? = null
        for (p in points) {
            var best: LetterKey? = null
            var bestDistance = Float.MAX_VALUE
            for (key in letterKeys) {
                val dx = key.cx - p.x
                val dy = key.cy - p.y
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = key
                }
            }
            val letter = best?.letter ?: continue
            if (letter != last) {
                sb.append(letter)
                last = letter
            }
            if (sb.length >= 24) break
        }
        return sb.toString().takeIf { it.length >= 2 }
    }

    override fun clear() {
        points.clear()
    }

    fun close() {
        engine.close()
    }

    private fun contextWords(): List<String> {
        val text = editorInstance.activeContent.textBeforeSelection
        return ngramTokens(text).takeLast(10)
    }
}
