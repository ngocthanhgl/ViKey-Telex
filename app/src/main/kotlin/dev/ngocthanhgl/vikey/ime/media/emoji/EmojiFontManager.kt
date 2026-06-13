package dev.ngocthanhgl.vikey.ime.media.emoji

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.net.URL

object EmojiFontManager {
    private const val APPLE_EMOJI_URL = "https://github.com/samuelngs/apple-emoji-linux/releases/download/v18.0.0/AppleColorEmoji.ttf"
    private const val FONT_DIR = "emoji_fonts"
    private const val FONT_FILE = "AppleColorEmoji.ttf"
    private var cachedTypeface: Typeface? = null

    private fun getFontFile(context: Context): File {
        return File(context.filesDir, "$FONT_DIR/$FONT_FILE")
    }

    fun isAppleEmojiAvailable(context: Context): Boolean {
        return getFontFile(context).exists()
    }

    fun loadAppleEmojiTypeface(context: Context): Typeface? {
        cachedTypeface?.let { return it }
        val file = getFontFile(context)
        if (!file.exists()) return null
        return try {
            Typeface.createFromFile(file).also { cachedTypeface = it }
        } catch (e: Exception) {
            null
        }
    }

    fun downloadAppleEmoji(context: Context): Boolean {
        val dir = File(context.filesDir, FONT_DIR)
        dir.mkdirs()
        val file = File(dir, FONT_FILE)
        if (file.exists()) return true
        return try {
            val url = URL(APPLE_EMOJI_URL)
            val connection = url.openConnection()
            connection.setRequestProperty("User-Agent", "ViKey-Telex")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.getInputStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cachedTypeface = null
            true
        } catch (e: Exception) {
            file.delete()
            false
        }
    }

    fun clearCache() {
        cachedTypeface = null
    }
}
