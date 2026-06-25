package dev.ngocthanhgl.vikey.ime.text.composing

import android.content.Context
import org.json.JSONObject

class TextExpander(context: Context) {
    private val shortcuts = mutableMapOf<String, String>()

    init {
        try {
            val raw = context.assets.open("ime/dict/shortcuts.json")
                .bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            for (key in json.keys()) {
                shortcuts[key.lowercase()] = json.getString(key)
            }
        } catch (_: Exception) {}
    }

    fun expand(word: String): String? {
        return shortcuts[word.lowercase()]
    }

    fun isShortcut(word: String): Boolean {
        return word.lowercase() in shortcuts
    }
}
