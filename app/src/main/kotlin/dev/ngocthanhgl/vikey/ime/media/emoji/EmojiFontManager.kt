package dev.ngocthanhgl.vikey.ime.media.emoji

import android.content.Context
import android.graphics.Typeface

object EmojiFontManager {
    private const val FONT_ASSET = "fonts/AppleColorEmoji.ttf"
    private var cachedTypeface: Typeface? = null

    fun loadAppleEmojiTypeface(context: Context): Typeface? {
        cachedTypeface?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, FONT_ASSET).also { cachedTypeface = it }
        } catch (e: Exception) {
            null
        }
    }
}
