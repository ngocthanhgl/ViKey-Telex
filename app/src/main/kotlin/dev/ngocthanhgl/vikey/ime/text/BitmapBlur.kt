/*
 * Copyright (C) 2025 ViKey Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.ngocthanhgl.vikey.ime.text

import android.graphics.Bitmap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fast approximate gaussian blur implemented as three successive box-blur passes,
 * pure Kotlin so it works on every API level without the deprecated RenderScript
 * and without adding a native dependency.
 *
 * Intended for one-shot preprocessing of large bitmaps (keyboard background photo);
 * callers must cache the result and never invoke this per frame.
 */
internal object BitmapBlur {

    fun blur(source: Bitmap, radiusPx: Int): Bitmap {
        if (radiusPx <= 0 || source.isRecycled || source.width <= 2 || source.height <= 2) {
            return source
        }
        return try {
            val w = source.width
            val h = source.height
            var curr = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
            var next = IntArray(curr.size)
            // Cap the radius so total work stays bounded on huge photos.
            val boxes = boxesForGauss(radiusPx.coerceAtMost(120).toFloat(), 3)
            repeat(3) { i ->
                val r = ((boxes[i] - 1) / 2).toInt().coerceAtLeast(1)
                boxBlurH(curr, next, w, h, r)
                val t1 = curr; curr = next; next = t1
                boxBlurV(curr, next, w, h, r)
                val t2 = curr; curr = next; next = t2
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
                out.setPixels(curr, 0, w, 0, 0, w, h)
            }
        } catch (_: Throwable) {
            // Fall back to the sharp bitmap rather than dropping the background entirely.
            source
        }
    }

    /// Ivan Kutskir's boxes-for-gauss: three box sizes approximating a gaussian with [sigma].
    private fun boxesForGauss(sigma: Float, n: Int): DoubleArray {
        val wIdeal = sqrt((12.0 * sigma * sigma / n) + 1.0)
        var wl = wIdeal.toInt()
        if (wl % 2 == 0) wl--
        if (wl < 1) wl = 1
        val wu = wl + 2
        val mIdeal = (12.0 * sigma * sigma - n * wl.toDouble() * wl - 4.0 * n * wl - 3.0 * n) /
            (-4.0 * wl - 4.0)
        val m = mIdeal.roundToInt().coerceIn(0, n - 1)
        return DoubleArray(n) { i -> if (i < m) wl.toDouble() else wu.toDouble() }
    }

    // Pack ARGB channels into separate 16-bit lanes of a Long so sliding-window sums
    // never overflow into the neighbouring channel (max lane sum = 255 * div < 2^16).
    private inline fun channels(p: Int): Long =
        (((p ushr 24) and 0xFF).toLong() shl 48) or
            (((p ushr 16) and 0xFF).toLong() shl 32) or
            (((p ushr 8) and 0xFF).toLong() shl 16) or
            (p and 0xFF).toLong()

    private fun packFromSum(sum: Long, div: Long): Int {
        val a = ((sum shr 48) / div).toInt().coerceIn(0, 255)
        val r = (((sum shr 32) and 0xFFFFL) / div).toInt().coerceIn(0, 255)
        val g = (((sum shr 16) and 0xFFFFL) / div).toInt().coerceIn(0, 255)
        val b = ((sum and 0xFFFFL) / div).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = (r + r + 1).toLong()
        for (y in 0 until h) {
            val rowStart = y * w
            var sum = 0L
            for (i in -r..r) {
                sum += channels(src[rowStart + i.coerceIn(0, w - 1)])
            }
            for (x in 0 until w) {
                dst[rowStart + x] = packFromSum(sum, div)
                sum += channels(src[rowStart + (x + r + 1).coerceAtMost(w - 1)]) -
                    channels(src[rowStart + (x - r).coerceAtLeast(0)])
            }
        }
    }

    private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = (r + r + 1).toLong()
        for (x in 0 until w) {
            var sum = 0L
            for (i in -r..r) {
                sum += channels(src[i.coerceIn(0, h - 1) * w + x])
            }
            for (y in 0 until h) {
                dst[y * w + x] = packFromSum(sum, div)
                sum += channels(src[(y + r + 1).coerceAtMost(h - 1) * w + x]) -
                    channels(src[(y - r).coerceAtLeast(0) * w + x])
            }
        }
    }
}
