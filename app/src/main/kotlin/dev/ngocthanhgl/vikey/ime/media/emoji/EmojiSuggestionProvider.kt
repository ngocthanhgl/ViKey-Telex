/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.ngocthanhgl.vikey.ime.media.emoji

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.stream.Collectors
import android.content.Context
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.editor.EditorContent
import dev.ngocthanhgl.vikey.ime.nlp.EmojiSuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionProvider
import dev.ngocthanhgl.vikey.lib.FlorisLocale
import io.github.reactivecircus.cache4k.Cache

/**
 * Provides emoji suggestions within a text input context.
 *
 * This class handles the following tasks:
 * - Initializes and maintains a list of supported emojis.
 * - Generates and returns emoji suggestions based on user input and preferences.
 *
 * @param context The application context.
 */
class EmojiSuggestionProvider(private val context: Context) : SuggestionProvider {
    override val providerId = "org.florisboard.nlp.providers.emoji"

    private val prefs by FlorisPreferenceStore
    private val lettersRegex = "^[A-Za-z]*$".toRegex()

    private val cachedEmojiMappings = Cache.Builder<FlorisLocale, EmojiDataBySkinTone>().build()

    override suspend fun create() {
    }

    override suspend fun preload(subtype: Subtype) {
        subtype.locales().forEach { locale ->
            cachedEmojiMappings.get(locale) {
                EmojiData.get(context, locale).bySkinTone
            }
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean
    ): List<SuggestionCandidate> {
        val preferredSkinTone = prefs.emoji.preferredSkinTone.get()
        val showName = prefs.emoji.suggestionCandidateShowName.get()

        val emojiQuery = validateInputQuery(content.composingText)
        if (emojiQuery != null) {
            val emojis = cachedEmojiMappings.get(subtype.primaryLocale)?.get(preferredSkinTone) ?: emptyList()
            val candidates = withContext(Dispatchers.Default) {
                emojis.parallelStream()
                    .map { emoji ->
                        val nameWeight = emoji.name.containsWeighted(emojiQuery, ignoreCase = true)
                        val keywordWeight = emoji.keywords
                            .any { it.contains(emojiQuery, ignoreCase = true) }
                            .let { if (it) 1.0 else 0.0 }
                        emoji to (nameWeight * 0.7 + keywordWeight * 0.3)
                    }
                    .sorted { (_, a), (_, b) -> b.compareTo(a) }
                    .limit(maxCandidateCount.toLong())
                    .filter { (_, a) -> a > 0 }
                    .map { (emoji, _) ->
                        EmojiSuggestionCandidate(
                            emoji = emoji,
                            showName = showName,
                            sourceProvider = this@EmojiSuggestionProvider,
                        )
                    }
                    .collect(Collectors.toList())
            }
            return candidates
        }

        val textBefore = content.textBeforeSelection
        if (textBefore.isBlank()) return emptyList()
        val lastWord = textBefore.trimEnd().split(Regex("\\s+")).lastOrNull {
            it.length >= 3 && it.all { c -> c.isLetter() }
        } ?: return emptyList()
        val lastWordLc = lastWord.lowercase()
        val contextualEmoji = CONTEXTUAL_MAP[lastWordLc] ?: return emptyList()
        return listOf(
            EmojiSuggestionCandidate(
                emoji = contextualEmoji,
                showName = showName,
                sourceProvider = this@EmojiSuggestionProvider,
            ),
        )
    }

    companion object {
        private val CONTEXTUAL_MAP = mapOf(
            "happy" to Emoji("\uD83D\uDE04", "smile", emptyList()),
            "love" to Emoji("\u2764\uFE0F", "heart", emptyList()),
            "sad" to Emoji("\uD83D\uDE22", "cry", emptyList()),
            "laugh" to Emoji("\uD83D\uDE02", "joy", emptyList()),
            "cry" to Emoji("\uD83D\uDE2D", "sob", emptyList()),
            "angry" to Emoji("\uD83D\uDE20", "angry", emptyList()),
            "cool" to Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
            "wink" to Emoji("\uD83D\uDE09", "wink", emptyList()),
            "food" to Emoji("\uD83C\uDF54", "burger", emptyList()),
            "pizza" to Emoji("\uD83C\uDF55", "pizza", emptyList()),
            "coffee" to Emoji("\u2615", "coffee", emptyList()),
            "beer" to Emoji("\uD83C\uDF7A", "beer", emptyList()),
            "wine" to Emoji("\uD83C\uDF77", "wine", emptyList()),
            "music" to Emoji("\uD83C\uDFB5", "music", emptyList()),
            "dog" to Emoji("\uD83D\uDC36", "dog", emptyList()),
            "cat" to Emoji("\uD83D\uDC31", "cat", emptyList()),
            "sun" to Emoji("\u2600\uFE0F", "sun", emptyList()),
            "moon" to Emoji("\uD83C\uDF19", "moon", emptyList()),
            "star" to Emoji("\u2B50", "star", emptyList()),
            "fire" to Emoji("\uD83D\uDD25", "fire", emptyList()),
            "ok" to Emoji("\uD83D\uDC4C", "ok", emptyList()),
            "yes" to Emoji("\u2705", "yes", emptyList()),
            "no" to Emoji("\u274C", "no", emptyList()),
            "thank" to Emoji("\uD83D\uDE4F", "pray", emptyList()),
            "please" to Emoji("\uD83E\uDD1E", "please", emptyList()),
            "sorry" to Emoji("\uD83D\uDE25", "sorry", emptyList()),
            "hello" to Emoji("\uD83D\uDC4B", "wave", emptyList()),
            "bye" to Emoji("\uD83D\uDC4B", "wave", emptyList()),
            "good" to Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
            "bad" to Emoji("\uD83D\uDC4E", "thumbsdown", emptyList()),
            "great" to Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
            "awesome" to Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
            "beautiful" to Emoji("\uD83D\uDC90", "bouquet", emptyList()),
            "party" to Emoji("\uD83C\uDF89", "party", emptyList()),
            "cake" to Emoji("\uD83C\uDF70", "cake", emptyList()),
            "gift" to Emoji("\uD83C\uDF81", "gift", emptyList()),
            "money" to Emoji("\uD83D\uDCB0", "money", emptyList()),
            "time" to Emoji("\u23F0", "clock", emptyList()),
            "sleep" to Emoji("\uD83D\uDE34", "sleep", emptyList()),
            "tired" to Emoji("\uD83D\uDE2B", "tired", emptyList()),
            "cute" to Emoji("\uD83D\uDE18", "kiss", emptyList()),
            "strong" to Emoji("\uD83D\uDCAA", "muscle", emptyList()),
            "run" to Emoji("\uD83C\uDFC3", "runner", emptyList()),
            "book" to Emoji("\uD83D\uDCDA", "books", emptyList()),
        )
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val updateHistory = prefs.emoji.suggestionUpdateHistory.get()
        if (!updateHistory || candidate !is EmojiSuggestionCandidate) {
            return
        }
        EmojiHistoryHelper.markEmojiUsed(prefs, candidate.emoji)
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // No-op
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate) = false

    override suspend fun getListOfWords(subtype: Subtype) = emptyList<String>()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String) = 0.0

    override suspend fun destroy() {
        cachedEmojiMappings.invalidateAll()
    }

    /**
     * Validates the user input query for emoji suggestions.
     */
    private fun validateInputQuery(composingText: CharSequence): String? {
        val prefix = prefs.emoji.suggestionType.get().prefix
        val queryMinLength = prefs.emoji.suggestionQueryMinLength.get() + prefix.length
        if (prefix.isNotEmpty() && !composingText.startsWith(prefix)) {
            return null
        }
        if (composingText.length < queryMinLength) {
            return null
        }
        val emojiPartialName = composingText.substring(prefix.length)
        if (!lettersRegex.matches(emojiPartialName)) {
            return null
        }
        return emojiPartialName
    }
}

private fun String.containsWeighted(other: String, ignoreCase: Boolean = false): Double = let { str ->
    if (str.contains(other, ignoreCase = ignoreCase)) {
        other.length.toDouble() / str.length.toDouble()
    } else {
        0.0
    }
}
