package org.florisboard.lib.color

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class Hsv(val hue: Float, val saturation: Float, val value: Float)

fun Color.toHsv(): Hsv {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6)
        max == g -> 60f * (((b - r) / delta) + 2)
        else -> 60f * (((r - g) / delta) + 4)
    }
    val s = if (max == 0f) 0f else delta / max
    return Hsv(hue = (h + 360) % 360, saturation = s, value = max)
}

fun Hsv.toColor(alpha: Float = 1f): Color {
    val h = hue / 60f; val c = value * saturation; val x = c * (1f - abs(h % 2 - 1)); val m = value - c
    val (r, g, b) = when {
        h < 1f -> Triple(c, x, 0f); h < 2f -> Triple(x, c, 0f); h < 3f -> Triple(0f, c, x)
        h < 4f -> Triple(0f, x, c); h < 5f -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    return Color(red = r + m, green = g + m, blue = b + m, alpha = alpha)
}
