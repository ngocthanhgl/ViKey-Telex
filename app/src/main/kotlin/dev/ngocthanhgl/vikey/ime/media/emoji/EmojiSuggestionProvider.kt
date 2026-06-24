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
    private val emojiUsageStats = mutableMapOf<String, MutableMap<String, Int>>()

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
            it.length >= 2 && it.all { c -> c.isLetter() }
        } ?: return emptyList()
        val lastWordLc = lastWord.lowercase()
        val contextualEmojis = CONTEXTUAL_MAP[lastWordLc] ?: return emptyList()
        return contextualEmojis.map { emoji ->
            EmojiSuggestionCandidate(
                emoji = emoji,
                showName = showName,
                sourceProvider = this@EmojiSuggestionProvider,
            )
        }
    }

    companion object {
        // Each word maps to multiple emojis, ranked by relevance
        private val CONTEXTUAL_MAP = mapOf(
            // === EMOTIONS / FEELINGS ===
            "happy" to listOf(
                Emoji("\uD83D\uDE04", "smile", emptyList()),
                Emoji("\uD83D\uDE01", "grin", emptyList()),
                Emoji("\uD83E\uDD23", "rofl", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
            ),
            "vui" to listOf(
                Emoji("\uD83D\uDE04", "smile", emptyList()),
                Emoji("\uD83D\uDE01", "grin", emptyList()),
                Emoji("\uD83E\uDD23", "rofl", emptyList()),
            ),
            "love" to listOf(
                Emoji("\u2764\uFE0F", "heart", emptyList()),
                Emoji("\uD83D\uDC9B", "yellow_heart", emptyList()),
                Emoji("\uD83D\uDC9C", "purple_heart", emptyList()),
                Emoji("\uD83D\uDC96", "sparkling_heart", emptyList()),
            ),
            "thương" to listOf(
                Emoji("\u2764\uFE0F", "heart", emptyList()),
                Emoji("\uD83D\uDC90", "bouquet", emptyList()),
                Emoji("\uD83E\uDD70", "smiling_face_with_3_hearts", emptyList()),
            ),
            "sad" to listOf(
                Emoji("\uD83D\uDE22", "cry", emptyList()),
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
                Emoji("\uD83D\uDE1E", "disappointed", emptyList()),
                Emoji("\uD83D\uDE14", "pensive", emptyList()),
            ),
            "laugh" to listOf(
                Emoji("\uD83D\uDE02", "joy", emptyList()),
                Emoji("\uD83D\uDE04", "smile", emptyList()),
                Emoji("\uD83E\uDD23", "rofl", emptyList()),
            ),
            "cry" to listOf(
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
                Emoji("\uD83D\uDE22", "cry", emptyList()),
                Emoji("\uD83D\uDE25", "disappointed_relieved", emptyList()),
            ),
            "khóc" to listOf(
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
                Emoji("\uD83D\uDE22", "cry", emptyList()),
                Emoji("\uD83D\uDE25", "disappointed_relieved", emptyList()),
            ),
            "angry" to listOf(
                Emoji("\uD83D\uDE20", "angry", emptyList()),
                Emoji("\uD83D\uDE21", "rage", emptyList()),
                Emoji("\uD83D\uDE24", "triumph", emptyList()),
            ),
            "giận" to listOf(
                Emoji("\uD83D\uDE20", "angry", emptyList()),
                Emoji("\uD83D\uDE21", "rage", emptyList()),
                Emoji("\uD83D\uDE24", "triumph", emptyList()),
            ),
            "surprised" to listOf(
                Emoji("\uD83D\uDE2E", "open_mouth", emptyList()),
                Emoji("\uD83D\uDE32", "astonished", emptyList()),
                Emoji("\uD83E\uDD2F", "shocked", emptyList()),
            ),
            "ngạc nhiên" to listOf(
                Emoji("\uD83D\uDE2E", "open_mouth", emptyList()),
                Emoji("\uD83D\uDE32", "astonished", emptyList()),
                Emoji("\uD83E\uDD2F", "shocked", emptyList()),
            ),
            "scared" to listOf(
                Emoji("\uD83D\uDE31", "scream", emptyList()),
                Emoji("\uD83D\uDE28", "fearful", emptyList()),
                Emoji("\uD83D\uDE30", "cold_sweat", emptyList()),
            ),
            "sợ" to listOf(
                Emoji("\uD83D\uDE31", "scream", emptyList()),
                Emoji("\uD83D\uDE28", "fearful", emptyList()),
                Emoji("\uD83D\uDE30", "cold_sweat", emptyList()),
            ),
            "cool" to listOf(
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
                Emoji("\uD83D\uDE08", "smiling_imp", emptyList()),
                Emoji("\uD83D\uDD25", "fire", emptyList()),
            ),
            "wink" to listOf(
                Emoji("\uD83D\uDE09", "wink", emptyList()),
                Emoji("\uD83D\uDE1C", "stuck_out_tongue_winking_eye", emptyList()),
            ),
            "cute" to listOf(
                Emoji("\uD83D\uDE18", "kiss", emptyList()),
                Emoji("\uD83D\uDE17", "kissing", emptyList()),
                Emoji("\uD83E\uDD70", "smiling_face_with_3_hearts", emptyList()),
                Emoji("\uD83D\uDC95", "two_hearts", emptyList()),
            ),
            "dễ thương" to listOf(
                Emoji("\uD83D\uDE18", "kiss", emptyList()),
                Emoji("\uD83D\uDE17", "kissing", emptyList()),
                Emoji("\uD83E\uDD70", "smiling_face_with_3_hearts", emptyList()),
            ),
            "tired" to listOf(
                Emoji("\uD83D\uDE2B", "tired", emptyList()),
                Emoji("\uD83D\uDE34", "sleep", emptyList()),
                Emoji("\uD83D\uDE14", "pensive", emptyList()),
            ),
            "mệt" to listOf(
                Emoji("\uD83D\uDE2B", "tired", emptyList()),
                Emoji("\uD83D\uDE34", "sleep", emptyList()),
                Emoji("\uD83D\uDE14", "pensive", emptyList()),
            ),
            "bored" to listOf(
                Emoji("\uD83D\uDE12", "unamused", emptyList()),
                Emoji("\uD83D\uDE15", "confused", emptyList()),
                Emoji("\uD83D\uDE10", "neutral", emptyList()),
            ),
            "chán" to listOf(
                Emoji("\uD83D\uDE12", "unamused", emptyList()),
                Emoji("\uD83D\uDE15", "confused", emptyList()),
                Emoji("\uD83D\uDE10", "neutral", emptyList()),
            ),
            "excited" to listOf(
                Emoji("\uD83E\uDD29", "star_struck", emptyList()),
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
                Emoji("\uD83D\uDE01", "grin", emptyList()),
            ),
            "hào hứng" to listOf(
                Emoji("\uD83E\uDD29", "star_struck", emptyList()),
                Emoji("\uD83D\uDE01", "grin", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "shy" to listOf(
                Emoji("\uD83D\uDE33", "flushed", emptyList()),
                Emoji("\uD83E\uDD0A", "zany_face", emptyList()),
                Emoji("\uD83D\uDE05", "sweat_smile", emptyList()),
            ),
            "ngại" to listOf(
                Emoji("\uD83D\uDE33", "flushed", emptyList()),
                Emoji("\uD83D\uDE05", "sweat_smile", emptyList()),
                Emoji("\uD83E\uDD0A", "zany_face", emptyList()),
            ),
            "proud" to listOf(
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
                Emoji("\uD83D\uDE04", "smile", emptyList()),
            ),
            "tự hào" to listOf(
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
                Emoji("\uD83D\uDE04", "smile", emptyList()),
            ),
            "jealous" to listOf(
                Emoji("\uD83D\uDE1F", "worried", emptyList()),
                Emoji("\uD83D\uDC7A", "japanese_ogre", emptyList()),
            ),
            "ghen" to listOf(
                Emoji("\uD83D\uDE1F", "worried", emptyList()),
                Emoji("\uD83D\uDC7A", "japanese_ogre", emptyList()),
                Emoji("\uD83D\uDC96", "sparkling_heart", emptyList()),
            ),
            "confused" to listOf(
                Emoji("\uD83D\uDE15", "confused", emptyList()),
                Emoji("\uD83D\uDE35", "dizzy", emptyList()),
                Emoji("\uD83E\uDD14", "thinking", emptyList()),
            ),
            "bối rối" to listOf(
                Emoji("\uD83D\uDE15", "confused", emptyList()),
                Emoji("\uD83D\uDE35", "dizzy", emptyList()),
                Emoji("\uD83E\uDD14", "thinking", emptyList()),
            ),

            // === GREETINGS / SOCIAL ===
            "hello" to listOf(
                Emoji("\uD83D\uDC4B", "wave", emptyList()),
                Emoji("\uD83D\uDC4F", "clap", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "chào" to listOf(
                Emoji("\uD83D\uDC4B", "wave", emptyList()),
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "hi" to listOf(
                Emoji("\uD83D\uDC4B", "wave", emptyList()),
                Emoji("\u270B", "raised_hand", emptyList()),
            ),
            "bye" to listOf(
                Emoji("\uD83D\uDC4B", "wave", emptyList()),
                Emoji("\uD83D\uDE4B", "raised_hands", emptyList()),
                Emoji("\u270B", "raised_hand", emptyList()),
            ),
            "tạm biệt" to listOf(
                Emoji("\uD83D\uDC4B", "wave", emptyList()),
                Emoji("\uD83D\uDE4B", "raised_hands", emptyList()),
                Emoji("\uD83D\uDC95", "two_hearts", emptyList()),
            ),
            "thank" to listOf(
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
            ),
            "cảm ơn" to listOf(
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
            ),
            "please" to listOf(
                Emoji("\uD83E\uDD1E", "pleading", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
            ),
            "làm ơn" to listOf(
                Emoji("\uD83E\uDD1E", "pleading", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
            ),
            "sorry" to listOf(
                Emoji("\uD83D\uDE25", "disappointed_relieved", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDE22", "cry", emptyList()),
            ),
            "xin lỗi" to listOf(
                Emoji("\uD83D\uDE25", "disappointed_relieved", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
            ),
            "yes" to listOf(
                Emoji("\u2705", "white_check_mark", emptyList()),
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
            ),
            "có" to listOf(
                Emoji("\u2705", "white_check_mark", emptyList()),
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
            ),
            "no" to listOf(
                Emoji("\u274C", "cross_mark", emptyList()),
                Emoji("\uD83D\uDC4E", "thumbsdown", emptyList()),
                Emoji("\uD83D\uDE45", "no_good", emptyList()),
            ),
            "không" to listOf(
                Emoji("\u274C", "cross_mark", emptyList()),
                Emoji("\uD83D\uDC4E", "thumbsdown", emptyList()),
                Emoji("\uD83D\uDE45", "no_good", emptyList()),
            ),
            "ok" to listOf(
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\u2705", "white_check_mark", emptyList()),
            ),
            "good" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\u2705", "white_check_mark", emptyList()),
            ),
            "tốt" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\u2705", "white_check_mark", emptyList()),
            ),
            "bad" to listOf(
                Emoji("\uD83D\uDC4E", "thumbsdown", emptyList()),
                Emoji("\u274C", "cross_mark", emptyList()),
                Emoji("\uD83D\uDE45", "no_good", emptyList()),
            ),
            "xấu" to listOf(
                Emoji("\uD83D\uDC4E", "thumbsdown", emptyList()),
                Emoji("\u274C", "cross_mark", emptyList()),
                Emoji("\uD83D\uDE45", "no_good", emptyList()),
            ),
            "great" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
            ),
            "tuyệt" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
            ),
            "awesome" to listOf(
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83D\uDD25", "fire", emptyList()),
            ),
            "amazing" to listOf(
                Emoji("\uD83E\uDD29", "star_struck", emptyList()),
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
            ),
            "nice" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\u2705", "white_check_mark", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
            ),
            "đẹp" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\uD83D\uDC90", "bouquet", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
            ),
            "beautiful" to listOf(
                Emoji("\uD83D\uDC90", "bouquet", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\uD83C\uDF39", "rose", emptyList()),
            ),
            "perfect" to listOf(
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
            ),
            "hoàn hảo" to listOf(
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
            ),
            "funny" to listOf(
                Emoji("\uD83E\uDD23", "rofl", emptyList()),
                Emoji("\uD83D\uDE02", "joy", emptyList()),
                Emoji("\uD83D\uDE04", "smile", emptyList()),
            ),
            "hài" to listOf(
                Emoji("\uD83E\uDD23", "rofl", emptyList()),
                Emoji("\uD83D\uDE02", "joy", emptyList()),
                Emoji("\uD83D\uDE04", "smile", emptyList()),
            ),
            "agree" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\u2705", "white_check_mark", emptyList()),
            ),
            "đồng ý" to listOf(
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\u2705", "white_check_mark", emptyList()),
            ),

            // === FOOD & DRINK ===
            "food" to listOf(
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
                Emoji("\uD83C\uDF2D", "hotdog", emptyList()),
            ),
            "ăn" to listOf(
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
                Emoji("\uD83C\uDF2D", "hotdog", emptyList()),
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
            ),
            "eat" to listOf(
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
            ),
            "ngon" to listOf(
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
                Emoji("\uD83D\uDE04", "smile", emptyList()),
                Emoji("\uD83C\uDF54", "burger", emptyList()),
            ),
            "delicious" to listOf(
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
                Emoji("\uD83D\uDE04", "smile", emptyList()),
                Emoji("\uD83C\uDF54", "burger", emptyList()),
            ),
            "pizza" to listOf(
                Emoji("\uD83C\uDF55", "pizza", emptyList()),
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83C\uDF5F", "fries", emptyList()),
            ),
            "coffee" to listOf(
                Emoji("\u2615", "coffee", emptyList()),
                Emoji("\uD83C\uDF75", "tea", emptyList()),
                Emoji("\uD83E\uDD5C", "milk", emptyList()),
            ),
            "cà phê" to listOf(
                Emoji("\u2615", "coffee", emptyList()),
                Emoji("\uD83C\uDF75", "tea", emptyList()),
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
            ),
            "beer" to listOf(
                Emoji("\uD83C\uDF7A", "beer", emptyList()),
                Emoji("\uD83C\uDF7B", "clinking_glasses", emptyList()),
                Emoji("\uD83C\uDF78", "cocktail", emptyList()),
            ),
            "bia" to listOf(
                Emoji("\uD83C\uDF7A", "beer", emptyList()),
                Emoji("\uD83C\uDF7B", "clinking_glasses", emptyList()),
                Emoji("\uD83C\uDF78", "cocktail", emptyList()),
            ),
            "wine" to listOf(
                Emoji("\uD83C\uDF77", "wine", emptyList()),
                Emoji("\uD83C\uDF78", "cocktail", emptyList()),
                Emoji("\uD83C\uDF7A", "beer", emptyList()),
            ),
            "drink" to listOf(
                Emoji("\uD83C\uDF78", "cocktail", emptyList()),
                Emoji("\u2615", "coffee", emptyList()),
                Emoji("\uD83C\uDF7A", "beer", emptyList()),
            ),
            "uống" to listOf(
                Emoji("\uD83C\uDF78", "cocktail", emptyList()),
                Emoji("\u2615", "coffee", emptyList()),
                Emoji("\uD83C\uDF7A", "beer", emptyList()),
            ),
            "cake" to listOf(
                Emoji("\uD83C\uDF70", "cake", emptyList()),
                Emoji("\uD83C\uDF82", "birthday", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "bánh" to listOf(
                Emoji("\uD83C\uDF70", "cake", emptyList()),
                Emoji("\uD83C\uDF82", "birthday", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
            ),
            "sweet" to listOf(
                Emoji("\uD83C\uDF6F", "honey_pot", emptyList()),
                Emoji("\uD83C\uDF6D", "lollipop", emptyList()),
                Emoji("\uD83C\uDF68", "ice_cream", emptyList()),
            ),
            "ngọt" to listOf(
                Emoji("\uD83C\uDF6F", "honey_pot", emptyList()),
                Emoji("\uD83C\uDF6D", "lollipop", emptyList()),
                Emoji("\uD83C\uDF68", "ice_cream", emptyList()),
            ),
            "rice" to listOf(
                Emoji("\uD83C\uDF5A", "rice", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
                Emoji("\uD83E\uDD62", "chopsticks", emptyList()),
            ),
            "cơm" to listOf(
                Emoji("\uD83C\uDF5A", "rice", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
                Emoji("\uD83E\uDD62", "chopsticks", emptyList()),
            ),
            "fruit" to listOf(
                Emoji("\uD83C\uDF4E", "apple", emptyList()),
                Emoji("\uD83C\uDF4C", "banana", emptyList()),
                Emoji("\uD83C\uDF53", "strawberry", emptyList()),
            ),
            "trái cây" to listOf(
                Emoji("\uD83C\uDF4E", "apple", emptyList()),
                Emoji("\uD83C\uDF4C", "banana", emptyList()),
                Emoji("\uD83C\uDF53", "strawberry", emptyList()),
            ),
            "chocolate" to listOf(
                Emoji("\uD83C\uDF6B", "chocolate", emptyList()),
                Emoji("\uD83C\uDF6C", "candy", emptyList()),
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
            ),
            "sô cô la" to listOf(
                Emoji("\uD83C\uDF6B", "chocolate", emptyList()),
                Emoji("\uD83C\uDF6C", "candy", emptyList()),
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
            ),
            "hot" to listOf(
                Emoji("\uD83C\uDF36\uFE0F", "hot_pepper", emptyList()),
                Emoji("\uD83D\uDD25", "fire", emptyList()),
                Emoji("\u2615", "coffee", emptyList()),
            ),
            "nóng" to listOf(
                Emoji("\uD83C\uDF36\uFE0F", "hot_pepper", emptyList()),
                Emoji("\uD83D\uDD25", "fire", emptyList()),
                Emoji("\u2600\uFE0F", "sun", emptyList()),
            ),
            "cold" to listOf(
                Emoji("\uD83E\uDDCA", "ice_cube", emptyList()),
                Emoji("\u2744\uFE0F", "snowflake", emptyList()),
                Emoji("\uD83C\uDF68", "ice_cream", emptyList()),
            ),
            "lạnh" to listOf(
                Emoji("\uD83E\uDDCA", "ice_cube", emptyList()),
                Emoji("\u2744\uFE0F", "snowflake", emptyList()),
                Emoji("\uD83D\uDE31", "scream", emptyList()),
            ),

            // === ANIMALS ===
            "dog" to listOf(
                Emoji("\uD83D\uDC36", "dog", emptyList()),
                Emoji("\uD83D\uDC3E", "paw_prints", emptyList()),
                Emoji("\uD83D\uDC31", "cat", emptyList()),
            ),
            "chó" to listOf(
                Emoji("\uD83D\uDC36", "dog", emptyList()),
                Emoji("\uD83D\uDC3E", "paw_prints", emptyList()),
                Emoji("\uD83D\uDC31", "cat", emptyList()),
            ),
            "cat" to listOf(
                Emoji("\uD83D\uDC31", "cat", emptyList()),
                Emoji("\uD83D\uDC08", "cat2", emptyList()),
                Emoji("\uD83D\uDC36", "dog", emptyList()),
            ),
            "mèo" to listOf(
                Emoji("\uD83D\uDC31", "cat", emptyList()),
                Emoji("\uD83D\uDC08", "cat2", emptyList()),
                Emoji("\uD83D\uDC36", "dog", emptyList()),
            ),
            "bird" to listOf(
                Emoji("\uD83D\uDC26", "bird", emptyList()),
                Emoji("\uD83D\uDD4A\uFE0F", "dove", emptyList()),
                Emoji("\uD83E\uDD85", "eagle", emptyList()),
            ),
            "chim" to listOf(
                Emoji("\uD83D\uDC26", "bird", emptyList()),
                Emoji("\uD83D\uDD4A\uFE0F", "dove", emptyList()),
                Emoji("\uD83E\uDD85", "eagle", emptyList()),
            ),
            "fish" to listOf(
                Emoji("\uD83D\uDC1F", "fish", emptyList()),
                Emoji("\uD83D\uDC20", "tropical_fish", emptyList()),
                Emoji("\uD83D\uDC2C", "dolphin", emptyList()),
            ),
            "cá" to listOf(
                Emoji("\uD83D\uDC1F", "fish", emptyList()),
                Emoji("\uD83D\uDC20", "tropical_fish", emptyList()),
                Emoji("\uD83D\uDC2C", "dolphin", emptyList()),
            ),
            "horse" to listOf(
                Emoji("\uD83D\uDC0E", "horse", emptyList()),
                Emoji("\uD83D\uDC34", "racehorse", emptyList()),
                Emoji("\uD83C\uDFC7", "horse_racing", emptyList()),
            ),
            "ngựa" to listOf(
                Emoji("\uD83D\uDC0E", "horse", emptyList()),
                Emoji("\uD83D\uDC34", "racehorse", emptyList()),
                Emoji("\uD83C\uDFC7", "horse_racing", emptyList()),
            ),
            "cow" to listOf(
                Emoji("\uD83D\uDC04", "cow", emptyList()),
                Emoji("\uD83D\uDC2E", "cow2", emptyList()),
                Emoji("\uD83E\uDD5B", "milk", emptyList()),
            ),
            "bò" to listOf(
                Emoji("\uD83D\uDC04", "cow", emptyList()),
                Emoji("\uD83D\uDC2E", "cow2", emptyList()),
                Emoji("\uD83E\uDD5B", "milk", emptyList()),
            ),
            "pig" to listOf(
                Emoji("\uD83D\uDC37", "pig", emptyList()),
                Emoji("\uD83D\uDC16", "pig2", emptyList()),
                Emoji("\uD83D\uDC3D", "pig_nose", emptyList()),
            ),
            "heo" to listOf(
                Emoji("\uD83D\uDC37", "pig", emptyList()),
                Emoji("\uD83D\uDC16", "pig2", emptyList()),
                Emoji("\uD83D\uDC3D", "pig_nose", emptyList()),
            ),
            "rabbit" to listOf(
                Emoji("\uD83D\uDC30", "rabbit", emptyList()),
                Emoji("\uD83D\uDC07", "rabbit2", emptyList()),
                Emoji("\uD83D\uDC3E", "paw_prints", emptyList()),
            ),
            "thỏ" to listOf(
                Emoji("\uD83D\uDC30", "rabbit", emptyList()),
                Emoji("\uD83D\uDC07", "rabbit2", emptyList()),
                Emoji("\uD83D\uDC3E", "paw_prints", emptyList()),
            ),
            "monkey" to listOf(
                Emoji("\uD83D\uDC35", "monkey", emptyList()),
                Emoji("\uD83D\uDC12", "monkey_face", emptyList()),
                Emoji("\uD83C\uDF35", "cactus", emptyList()),
            ),
            "khỉ" to listOf(
                Emoji("\uD83D\uDC35", "monkey", emptyList()),
                Emoji("\uD83D\uDC12", "monkey_face", emptyList()),
            ),
            "elephant" to listOf(
                Emoji("\uD83D\uDC18", "elephant", emptyList()),
                Emoji("\uD83E\uDD93", "zebra", emptyList()),
                Emoji("\uD83E\uDD92", "giraffe", emptyList()),
            ),
            "voi" to listOf(
                Emoji("\uD83D\uDC18", "elephant", emptyList()),
                Emoji("\uD83E\uDD93", "zebra", emptyList()),
                Emoji("\uD83E\uDD92", "giraffe", emptyList()),
            ),
            "bear" to listOf(
                Emoji("\uD83D\uDC3B", "bear", emptyList()),
                Emoji("\uD83D\uDC3A", "wolf", emptyList()),
                Emoji("\uD83D\uDC05", "tiger", emptyList()),
            ),
            "gấu" to listOf(
                Emoji("\uD83D\uDC3B", "bear", emptyList()),
                Emoji("\uD83D\uDC3A", "wolf", emptyList()),
                Emoji("\uD83D\uDC05", "tiger", emptyList()),
            ),
            "lion" to listOf(
                Emoji("\uD83E\uDD81", "lion", emptyList()),
                Emoji("\uD83D\uDC05", "tiger", emptyList()),
                Emoji("\uD83D\uDC06", "leopard", emptyList()),
            ),
            "sư tử" to listOf(
                Emoji("\uD83E\uDD81", "lion", emptyList()),
                Emoji("\uD83D\uDC05", "tiger", emptyList()),
                Emoji("\uD83D\uDC06", "leopard", emptyList()),
            ),
            "snake" to listOf(
                Emoji("\uD83D\uDC0D", "snake", emptyList()),
                Emoji("\uD83E\uDD8E", "lizard", emptyList()),
                Emoji("\uD83D\uDC22", "turtle", emptyList()),
            ),
            "rắn" to listOf(
                Emoji("\uD83D\uDC0D", "snake", emptyList()),
                Emoji("\uD83E\uDD8E", "lizard", emptyList()),
                Emoji("\uD83D\uDC22", "turtle", emptyList()),
            ),
            "butterfly" to listOf(
                Emoji("\uD83E\uDD8B", "butterfly", emptyList()),
                Emoji("\uD83D\uDC1D", "bee", emptyList()),
                Emoji("\uD83D\uDC90", "bouquet", emptyList()),
            ),
            "bướm" to listOf(
                Emoji("\uD83E\uDD8B", "butterfly", emptyList()),
                Emoji("\uD83D\uDC1D", "bee", emptyList()),
                Emoji("\uD83D\uDC90", "bouquet", emptyList()),
            ),
            "chicken" to listOf(
                Emoji("\uD83D\uDC14", "chicken", emptyList()),
                Emoji("\uD83D\uDC13", "rooster", emptyList()),
                Emoji("\uD83E\uDD5A", "egg", emptyList()),
            ),
            "gà" to listOf(
                Emoji("\uD83D\uDC14", "chicken", emptyList()),
                Emoji("\uD83D\uDC13", "rooster", emptyList()),
                Emoji("\uD83E\uDD5A", "egg", emptyList()),
            ),
            "duck" to listOf(
                Emoji("\uD83E\uDD86", "duck", emptyList()),
                Emoji("\uD83D\uDC24", "hatching_chick", emptyList()),
                Emoji("\uD83D\uDC14", "chicken", emptyList()),
            ),
            "vịt" to listOf(
                Emoji("\uD83E\uDD86", "duck", emptyList()),
                Emoji("\uD83D\uDC24", "hatching_chick", emptyList()),
                Emoji("\uD83D\uDC14", "chicken", emptyList()),
            ),
            "frog" to listOf(
                Emoji("\uD83D\uDC38", "frog", emptyList()),
                Emoji("\uD83D\uDC22", "turtle", emptyList()),
                Emoji("\uD83D\uDC0C", "snail", emptyList()),
            ),
            "ếch" to listOf(
                Emoji("\uD83D\uDC38", "frog", emptyList()),
                Emoji("\uD83D\uDC22", "turtle", emptyList()),
            ),
            "panda" to listOf(
                Emoji("\uD83D\uDC3C", "panda", emptyList()),
                Emoji("\uD83D\uDC3B", "bear", emptyList()),
            ),
            "gấu trúc" to listOf(
                Emoji("\uD83D\uDC3C", "panda", emptyList()),
                Emoji("\uD83D\uDC3B", "bear", emptyList()),
            ),

            // === NATURE / WEATHER ===
            "sun" to listOf(
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\uD83C\uDF1E", "sun_with_face", emptyList()),
                Emoji("\uD83C\uDF24\uFE0F", "sun_behind_small_cloud", emptyList()),
            ),
            "nắng" to listOf(
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\uD83C\uDF1E", "sun_with_face", emptyList()),
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
            ),
            "moon" to listOf(
                Emoji("\uD83C\uDF19", "moon", emptyList()),
                Emoji("\uD83C\uDF12", "new_moon", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "trăng" to listOf(
                Emoji("\uD83C\uDF19", "moon", emptyList()),
                Emoji("\uD83C\uDF12", "new_moon", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "star" to listOf(
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
            ),
            "sao" to listOf(
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
            ),
            "fire" to listOf(
                Emoji("\uD83D\uDD25", "fire", emptyList()),
                Emoji("\uD83D\uDD25", "fire", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
            ),
            "lửa" to listOf(
                Emoji("\uD83D\uDD25", "fire", emptyList()),
                Emoji("\uD83D\uDCA5", "boom", emptyList()),
                Emoji("\uD83D\uDCA3", "bomb", emptyList()),
            ),
            "rain" to listOf(
                Emoji("\uD83C\uDF27\uFE0F", "rain", emptyList()),
                Emoji("\u2614", "umbrella", emptyList()),
                Emoji("\u26C8\uFE0F", "thunder_cloud", emptyList()),
            ),
            "mưa" to listOf(
                Emoji("\uD83C\uDF27\uFE0F", "rain", emptyList()),
                Emoji("\u2614", "umbrella", emptyList()),
                Emoji("\u26C8\uFE0F", "thunder_cloud", emptyList()),
            ),
            "snow" to listOf(
                Emoji("\u2744\uFE0F", "snowflake", emptyList()),
                Emoji("\u26C4", "snowman", emptyList()),
                Emoji("\uD83C\uDF28\uFE0F", "cloud_snow", emptyList()),
            ),
            "tuyết" to listOf(
                Emoji("\u2744\uFE0F", "snowflake", emptyList()),
                Emoji("\u26C4", "snowman", emptyList()),
                Emoji("\uD83D\uDE31", "scream", emptyList()),
            ),
            "wind" to listOf(
                Emoji("\uD83C\uDF2C\uFE0F", "wind", emptyList()),
                Emoji("\uD83C\uDF2A\uFE0F", "fog", emptyList()),
            ),
            "gió" to listOf(
                Emoji("\uD83C\uDF2C\uFE0F", "wind", emptyList()),
                Emoji("\uD83C\uDF2A\uFE0F", "fog", emptyList()),
            ),
            "storm" to listOf(
                Emoji("\u26C8\uFE0F", "thunder_cloud", emptyList()),
                Emoji("\uD83C\uDF29\uFE0F", "lightning", emptyList()),
                Emoji("\uD83C\uDF27\uFE0F", "rain", emptyList()),
            ),
            "bão" to listOf(
                Emoji("\u26C8\uFE0F", "thunder_cloud", emptyList()),
                Emoji("\uD83C\uDF29\uFE0F", "lightning", emptyList()),
                Emoji("\uD83C\uDF2C\uFE0F", "wind", emptyList()),
            ),
            "tree" to listOf(
                Emoji("\uD83C\uDF33", "tree", emptyList()),
                Emoji("\uD83C\uDF34", "palm_tree", emptyList()),
                Emoji("\uD83C\uDF32", "evergreen", emptyList()),
            ),
            "cây" to listOf(
                Emoji("\uD83C\uDF33", "tree", emptyList()),
                Emoji("\uD83C\uDF34", "palm_tree", emptyList()),
                Emoji("\uD83C\uDF32", "evergreen", emptyList()),
            ),
            "flower" to listOf(
                Emoji("\uD83C\uDF39", "rose", emptyList()),
                Emoji("\uD83C\uDF3A", "hibiscus", emptyList()),
                Emoji("\uD83C\uDF38", "cherry_blossom", emptyList()),
            ),
            "hoa" to listOf(
                Emoji("\uD83C\uDF39", "rose", emptyList()),
                Emoji("\uD83C\uDF3A", "hibiscus", emptyList()),
                Emoji("\uD83C\uDF38", "cherry_blossom", emptyList()),
            ),
            "sea" to listOf(
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
                Emoji("\uD83C\uDFD6\uFE0F", "beach", emptyList()),
                Emoji("\uD83D\uDC20", "tropical_fish", emptyList()),
            ),
            "biển" to listOf(
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
                Emoji("\uD83C\uDFD6\uFE0F", "beach", emptyList()),
                Emoji("\uD83D\uDC20", "tropical_fish", emptyList()),
            ),
            "mountain" to listOf(
                Emoji("\u26F0\uFE0F", "mountain", emptyList()),
                Emoji("\uD83C\uDFD4\uFE0F", "snow_capped_mountain", emptyList()),
                Emoji("\uD83C\uDF0B", "volcano", emptyList()),
            ),
            "núi" to listOf(
                Emoji("\u26F0\uFE0F", "mountain", emptyList()),
                Emoji("\uD83C\uDFD4\uFE0F", "snow_capped_mountain", emptyList()),
                Emoji("\uD83C\uDF0B", "volcano", emptyList()),
            ),
            "river" to listOf(
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
                Emoji("\uD83C\uDFD6\uFE0F", "beach", emptyList()),
            ),
            "sông" to listOf(
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
                Emoji("\uD83C\uDFD6\uFE0F", "beach", emptyList()),
            ),
            "sunset" to listOf(
                Emoji("\uD83C\uDF07", "sunset", emptyList()),
                Emoji("\uD83C\uDF06", "sunrise", emptyList()),
                Emoji("\uD83C\uDF05", "city_sunset", emptyList()),
            ),
            "hoàng hôn" to listOf(
                Emoji("\uD83C\uDF07", "sunset", emptyList()),
                Emoji("\uD83C\uDF06", "sunrise", emptyList()),
            ),
            "rainbow" to listOf(
                Emoji("\uD83C\uDF08", "rainbow", emptyList()),
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\uD83C\uDF27\uFE0F", "rain", emptyList()),
            ),
            "cầu vồng" to listOf(
                Emoji("\uD83C\uDF08", "rainbow", emptyList()),
                Emoji("\u2600\uFE0F", "sun", emptyList()),
            ),

            // === ACTIVITIES / SPORTS ===
            "run" to listOf(
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\uD83C\uDFC3\u200D\u2640\uFE0F", "woman_running", emptyList()),
                Emoji("\uD83C\uDFC2", "snowboarder", emptyList()),
            ),
            "chạy" to listOf(
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\uD83C\uDFC3\u200D\u2640\uFE0F", "woman_running", emptyList()),
                Emoji("\uD83D\uDEB4", "bike", emptyList()),
            ),
            "swim" to listOf(
                Emoji("\uD83C\uDFCA", "swimmer", emptyList()),
                Emoji("\uD83C\uDFCA\u200D\u2640\uFE0F", "woman_swimming", emptyList()),
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
            ),
            "bơi" to listOf(
                Emoji("\uD83C\uDFCA", "swimmer", emptyList()),
                Emoji("\uD83C\uDFCA\u200D\u2640\uFE0F", "woman_swimming", emptyList()),
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
            ),
            "dance" to listOf(
                Emoji("\uD83D\uDD7A", "dancer", emptyList()),
                Emoji("\uD83D\uDC83", "woman_dancing", emptyList()),
                Emoji("\uD83C\uDFB5", "music", emptyList()),
            ),
            "múa" to listOf(
                Emoji("\uD83D\uDD7A", "dancer", emptyList()),
                Emoji("\uD83D\uDC83", "woman_dancing", emptyList()),
                Emoji("\uD83C\uDFB5", "music", emptyList()),
            ),
            "nhảy" to listOf(
                Emoji("\uD83D\uDD7A", "dancer", emptyList()),
                Emoji("\uD83D\uDC83", "woman_dancing", emptyList()),
                Emoji("\uD83C\uDFB5", "music", emptyList()),
            ),
            "sing" to listOf(
                Emoji("\uD83C\uDFA4", "microphone", emptyList()),
                Emoji("\uD83C\uDFB5", "music", emptyList()),
                Emoji("\uD83C\uDFB6", "notes", emptyList()),
            ),
            "hát" to listOf(
                Emoji("\uD83C\uDFA4", "microphone", emptyList()),
                Emoji("\uD83C\uDFB5", "music", emptyList()),
                Emoji("\uD83C\uDFB6", "notes", emptyList()),
            ),
            "music" to listOf(
                Emoji("\uD83C\uDFB5", "music", emptyList()),
                Emoji("\uD83C\uDFB6", "notes", emptyList()),
                Emoji("\uD83C\uDFA4", "microphone", emptyList()),
            ),
            "nhạc" to listOf(
                Emoji("\uD83C\uDFB5", "music", emptyList()),
                Emoji("\uD83C\uDFB6", "notes", emptyList()),
                Emoji("\uD83C\uDFA4", "microphone", emptyList()),
            ),
            "sport" to listOf(
                Emoji("\u26BD", "soccer", emptyList()),
                Emoji("\uD83C\uDFC0", "basketball", emptyList()),
                Emoji("\uD83C\uDFBE", "tennis", emptyList()),
            ),
            "thể thao" to listOf(
                Emoji("\u26BD", "soccer", emptyList()),
                Emoji("\uD83C\uDFC0", "basketball", emptyList()),
                Emoji("\uD83C\uDFBE", "tennis", emptyList()),
            ),
            "soccer" to listOf(
                Emoji("\u26BD", "soccer", emptyList()),
                Emoji("\uD83C\uDFC8", "football", emptyList()),
                Emoji("\uD83D\uDC4F", "clap", emptyList()),
            ),
            "đá bóng" to listOf(
                Emoji("\u26BD", "soccer", emptyList()),
                Emoji("\uD83C\uDFC8", "football", emptyList()),
                Emoji("\uD83D\uDC4F", "clap", emptyList()),
            ),
            "basketball" to listOf(
                Emoji("\uD83C\uDFC0", "basketball", emptyList()),
                Emoji("\uD83C\uDFC8", "football", emptyList()),
            ),
            "bóng rổ" to listOf(
                Emoji("\uD83C\uDFC0", "basketball", emptyList()),
                Emoji("\uD83C\uDFC8", "football", emptyList()),
            ),
            "travel" to listOf(
                Emoji("\u2708\uFE0F", "airplane", emptyList()),
                Emoji("\uD83D\uDEEC\uFE0F", "airplane_departure", emptyList()),
                Emoji("\uD83C\uDF0D", "globe", emptyList()),
            ),
            "du lịch" to listOf(
                Emoji("\u2708\uFE0F", "airplane", emptyList()),
                Emoji("\uD83D\uDEEC\uFE0F", "airplane_departure", emptyList()),
                Emoji("\uD83C\uDF0D", "globe", emptyList()),
            ),
            "đi" to listOf(
                Emoji("\u2708\uFE0F", "airplane", emptyList()),
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\uD83D\uDE97", "taxi", emptyList()),
            ),
            "sleep" to listOf(
                Emoji("\uD83D\uDE34", "sleep", emptyList()),
                Emoji("\uD83D\uDE35", "dizzy", emptyList()),
                Emoji("\uD83D\uDE2B", "tired", emptyList()),
            ),
            "ngủ" to listOf(
                Emoji("\uD83D\uDE34", "sleep", emptyList()),
                Emoji("\uD83D\uDE35", "dizzy", emptyList()),
                Emoji("\uD83D\uDE2B", "tired", emptyList()),
            ),
            "read" to listOf(
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83D\uDCD6", "book", emptyList()),
                Emoji("\uD83E\uDDD0", "nerd", emptyList()),
            ),
            "đọc" to listOf(
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83D\uDCD6", "book", emptyList()),
                Emoji("\uD83E\uDDD0", "nerd", emptyList()),
            ),
            "write" to listOf(
                Emoji("\u270F\uFE0F", "pencil", emptyList()),
                Emoji("\uD83D\uDCDD", "memo", emptyList()),
                Emoji("\uD83D\uDCD6", "book", emptyList()),
            ),
            "viết" to listOf(
                Emoji("\u270F\uFE0F", "pencil", emptyList()),
                Emoji("\uD83D\uDCDD", "memo", emptyList()),
                Emoji("\uD83D\uDCD6", "book", emptyList()),
            ),
            "draw" to listOf(
                Emoji("\uD83C\uDFA8", "art", emptyList()),
                Emoji("\u270F\uFE0F", "pencil", emptyList()),
                Emoji("\uD83D\uDD8C\uFE0F", "fountain_pen", emptyList()),
            ),
            "vẽ" to listOf(
                Emoji("\uD83C\uDFA8", "art", emptyList()),
                Emoji("\u270F\uFE0F", "pencil", emptyList()),
                Emoji("\uD83D\uDD8C\uFE0F", "fountain_pen", emptyList()),
            ),
            "cook" to listOf(
                Emoji("\uD83C\uDF73", "cooking", emptyList()),
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
            ),
            "nấu" to listOf(
                Emoji("\uD83C\uDF73", "cooking", emptyList()),
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
            ),
            "work" to listOf(
                Emoji("\uD83D\uDCBC", "briefcase", emptyList()),
                Emoji("\uD83D\uDC77", "construction", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "làm" to listOf(
                Emoji("\uD83D\uDCBC", "briefcase", emptyList()),
                Emoji("\uD83D\uDC77", "construction", emptyList()),
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
            ),
            "study" to listOf(
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83C\uDF93", "graduation", emptyList()),
                Emoji("\uD83C\uDFEB", "school", emptyList()),
            ),
            "học" to listOf(
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83C\uDF93", "graduation", emptyList()),
                Emoji("\uD83C\uDFEB", "school", emptyList()),
            ),
            "play" to listOf(
                Emoji("\u26BD", "soccer", emptyList()),
                Emoji("\uD83C\uDFAE", "video_game", emptyList()),
                Emoji("\uD83C\uDFB0", "slot_machine", emptyList()),
            ),
            "chơi" to listOf(
                Emoji("\u26BD", "soccer", emptyList()),
                Emoji("\uD83C\uDFAE", "video_game", emptyList()),
                Emoji("\uD83C\uDFB0", "slot_machine", emptyList()),
            ),
            "game" to listOf(
                Emoji("\uD83C\uDFAE", "video_game", emptyList()),
                Emoji("\uD83C\uDFB0", "slot_machine", emptyList()),
                Emoji("\uD83C\uDF9F\uFE0F", "joystick", emptyList()),
            ),
            "trò chơi" to listOf(
                Emoji("\uD83C\uDFAE", "video_game", emptyList()),
                Emoji("\uD83C\uDFB0", "slot_machine", emptyList()),
                Emoji("\uD83C\uDF9F\uFE0F", "joystick", emptyList()),
            ),
            "photo" to listOf(
                Emoji("\uD83D\uDCF7", "camera", emptyList()),
                Emoji("\uD83D\uDCF8", "camera_flash", emptyList()),
                Emoji("\uD83C\uDFA8", "art", emptyList()),
            ),
            "chụp" to listOf(
                Emoji("\uD83D\uDCF7", "camera", emptyList()),
                Emoji("\uD83D\uDCF8", "camera_flash", emptyList()),
            ),
            "shop" to listOf(
                Emoji("\uD83D\uDED2", "shopping_cart", emptyList()),
                Emoji("\uD83D\uDECD\uFE0F", "shopping_bags", emptyList()),
                Emoji("\uD83D\uDCB5", "dollar", emptyList()),
            ),
            "mua" to listOf(
                Emoji("\uD83D\uDED2", "shopping_cart", emptyList()),
                Emoji("\uD83D\uDECD\uFE0F", "shopping_bags", emptyList()),
                Emoji("\uD83D\uDCB5", "dollar", emptyList()),
            ),
            "sạch" to listOf(
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\uD83E\uDDFC", "soap", emptyList()),
                Emoji("\uD83D\uDCA6", "sweat_drops", emptyList()),
            ),
            "clean" to listOf(
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\uD83E\uDDFC", "soap", emptyList()),
                Emoji("\uD83D\uDCA6", "sweat_drops", emptyList()),
            ),

            // === PLACES / OBJECTS ===
            "home" to listOf(
                Emoji("\uD83C\uDFE0", "house", emptyList()),
                Emoji("\uD83C\uDFE1", "house_with_garden", emptyList()),
                Emoji("\uD83C\uDFD8\uFE0F", "houses", emptyList()),
            ),
            "nhà" to listOf(
                Emoji("\uD83C\uDFE0", "house", emptyList()),
                Emoji("\uD83C\uDFE1", "house_with_garden", emptyList()),
                Emoji("\uD83D\uDC6A", "family", emptyList()),
            ),
            "school" to listOf(
                Emoji("\uD83C\uDFEB", "school", emptyList()),
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83C\uDF93", "graduation", emptyList()),
            ),
            "trường" to listOf(
                Emoji("\uD83C\uDFEB", "school", emptyList()),
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83C\uDF93", "graduation", emptyList()),
            ),
            "hospital" to listOf(
                Emoji("\uD83C\uDFE5", "hospital", emptyList()),
                Emoji("\uD83D\uDE91", "ambulance", emptyList()),
                Emoji("\uD83E\uDDD1\u200D\u2695\uFE0F", "doctor", emptyList()),
            ),
            "bệnh viện" to listOf(
                Emoji("\uD83C\uDFE5", "hospital", emptyList()),
                Emoji("\uD83D\uDE91", "ambulance", emptyList()),
                Emoji("\uD83D\uDC8A", "pill", emptyList()),
            ),
            "church" to listOf(
                Emoji("\u26EA", "church", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "nhà thờ" to listOf(
                Emoji("\u26EA", "church", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "temple" to listOf(
                Emoji("\uD83D\uDED5", "hindu_temple", emptyList()),
                Emoji("\uD83C\uDFDB\uFE0F", "pagoda", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "chùa" to listOf(
                Emoji("\uD83C\uDFDB\uFE0F", "pagoda", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "car" to listOf(
                Emoji("\uD83D\uDE97", "taxi", emptyList()),
                Emoji("\uD83D\uDE99", "car", emptyList()),
                Emoji("\uD83D\uDE95", "bus", emptyList()),
            ),
            "xe" to listOf(
                Emoji("\uD83D\uDE97", "taxi", emptyList()),
                Emoji("\uD83D\uDE99", "car", emptyList()),
                Emoji("\uD83D\uDE95", "bus", emptyList()),
            ),
            "bike" to listOf(
                Emoji("\uD83D\uDEB2", "bike", emptyList()),
                Emoji("\uD83D\uDEB4", "cyclist", emptyList()),
                Emoji("\uD83D\uDEB5", "mountain_biker", emptyList()),
            ),
            "xe đạp" to listOf(
                Emoji("\uD83D\uDEB2", "bike", emptyList()),
                Emoji("\uD83D\uDEB4", "cyclist", emptyList()),
            ),
            "plane" to listOf(
                Emoji("\u2708\uFE0F", "airplane", emptyList()),
                Emoji("\uD83D\uDEEB\uFE0F", "airplane_arrival", emptyList()),
                Emoji("\uD83D\uDEEC\uFE0F", "airplane_departure", emptyList()),
            ),
            "máy bay" to listOf(
                Emoji("\u2708\uFE0F", "airplane", emptyList()),
                Emoji("\uD83D\uDEEB\uFE0F", "airplane_arrival", emptyList()),
                Emoji("\uD83D\uDEEC\uFE0F", "airplane_departure", emptyList()),
            ),
            "phone" to listOf(
                Emoji("\uD83D\uDCF1", "phone", emptyList()),
                Emoji("\uD83D\uDCDE", "telephone", emptyList()),
                Emoji("\uD83D\uDCF2", "calling", emptyList()),
            ),
            "điện thoại" to listOf(
                Emoji("\uD83D\uDCF1", "phone", emptyList()),
                Emoji("\uD83D\uDCDE", "telephone", emptyList()),
                Emoji("\uD83D\uDCF2", "calling", emptyList()),
            ),
            "computer" to listOf(
                Emoji("\uD83D\uDCBB", "laptop", emptyList()),
                Emoji("\uD83D\uDDA5\uFE0F", "desktop", emptyList()),
                Emoji("\uD83D\uDCF1", "phone", emptyList()),
            ),
            "máy tính" to listOf(
                Emoji("\uD83D\uDCBB", "laptop", emptyList()),
                Emoji("\uD83D\uDDA5\uFE0F", "desktop", emptyList()),
                Emoji("\uD83D\uDCF1", "phone", emptyList()),
            ),
            "book" to listOf(
                Emoji("\uD83D\uDCD6", "book", emptyList()),
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83D\uDCD5", "closed_book", emptyList()),
            ),
            "sách" to listOf(
                Emoji("\uD83D\uDCD6", "book", emptyList()),
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83D\uDCD5", "closed_book", emptyList()),
            ),
            "key" to listOf(
                Emoji("\uD83D\uDD11", "key", emptyList()),
                Emoji("\uD83D\uDD10", "closed_lock", emptyList()),
                Emoji("\uD83D\uDD12", "lock", emptyList()),
            ),
            "chìa khóa" to listOf(
                Emoji("\uD83D\uDD11", "key", emptyList()),
                Emoji("\uD83D\uDD12", "lock", emptyList()),
            ),
            "light" to listOf(
                Emoji("\uD83D\uDCA1", "bulb", emptyList()),
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\uD83D\uDD05", "light_bulb", emptyList()),
            ),
            "đèn" to listOf(
                Emoji("\uD83D\uDCA1", "bulb", emptyList()),
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\uD83D\uDD05", "light_bulb", emptyList()),
            ),
            "ring" to listOf(
                Emoji("\uD83D\uDC8D", "ring", emptyList()),
                Emoji("\uD83D\uDC5C", "handbag", emptyList()),
                Emoji("\uD83D\uDCB0", "money", emptyList()),
            ),
            "nhẫn" to listOf(
                Emoji("\uD83D\uDC8D", "ring", emptyList()),
                Emoji("\u2764\uFE0F", "heart", emptyList()),
                Emoji("\uD83D\uDC96", "sparkling_heart", emptyList()),
            ),
            "hat" to listOf(
                Emoji("\uD83C\uDFA9", "tophat", emptyList()),
                Emoji("\uD83E\uDDD4", "elf", emptyList()),
                Emoji("\u26F1\uFE0F", "umbrella", emptyList()),
            ),
            "mũ" to listOf(
                Emoji("\uD83C\uDFA9", "tophat", emptyList()),
                Emoji("\uD83E\uDDD4", "elf", emptyList()),
            ),
            "shoe" to listOf(
                Emoji("\uD83D\uDC5F", "athletic_shoe", emptyList()),
                Emoji("\uD83D\uDC60", "high_heel", emptyList()),
                Emoji("\uD83D\uDC61", "sandal", emptyList()),
            ),
            "giày" to listOf(
                Emoji("\uD83D\uDC5F", "athletic_shoe", emptyList()),
                Emoji("\uD83D\uDC60", "high_heel", emptyList()),
                Emoji("\uD83D\uDC61", "sandal", emptyList()),
            ),
            "dress" to listOf(
                Emoji("\uD83D\uDC57", "dress", emptyList()),
                Emoji("\uD83D\uDC5A", "kimono", emptyList()),
                Emoji("\uD83D\uDC5C", "handbag", emptyList()),
            ),
            "váy" to listOf(
                Emoji("\uD83D\uDC57", "dress", emptyList()),
                Emoji("\uD83D\uDC5A", "kimono", emptyList()),
            ),
            "shirt" to listOf(
                Emoji("\uD83D\uDC55", "shirt", emptyList()),
                Emoji("\uD83D\uDC56", "jeans", emptyList()),
                Emoji("\uD83D\uDC57", "dress", emptyList()),
            ),
            "áo" to listOf(
                Emoji("\uD83D\uDC55", "shirt", emptyList()),
                Emoji("\uD83D\uDC56", "jeans", emptyList()),
                Emoji("\uD83C\uDFA9", "tophat", emptyList()),
            ),
            "watch" to listOf(
                Emoji("\u23F0", "clock", emptyList()),
                Emoji("\u231A", "watch", emptyList()),
                Emoji("\u23F1\uFE0F", "stopwatch", emptyList()),
            ),
            "đồng hồ" to listOf(
                Emoji("\u23F0", "clock", emptyList()),
                Emoji("\u231A", "watch", emptyList()),
            ),
            "camera" to listOf(
                Emoji("\uD83D\uDCF7", "camera", emptyList()),
                Emoji("\uD83D\uDCF8", "camera_flash", emptyList()),
                Emoji("\uD83C\uDFA5", "movie_camera", emptyList()),
            ),
            "time" to listOf(
                Emoji("\u23F0", "clock", emptyList()),
                Emoji("\u231A", "watch", emptyList()),
                Emoji("\uD83D\uDD50", "clock1", emptyList()),
            ),
            "thời gian" to listOf(
                Emoji("\u23F0", "clock", emptyList()),
                Emoji("\u231A", "watch", emptyList()),
                Emoji("\uD83D\uDD50", "clock1", emptyList()),
            ),

            // === PEOPLE ===
            "friend" to listOf(
                Emoji("\uD83D\uDC65", "busts", emptyList()),
                Emoji("\uD83D\uDC64", "bust", emptyList()),
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
            ),
            "bạn" to listOf(
                Emoji("\uD83D\uDC65", "busts", emptyList()),
                Emoji("\uD83D\uDC64", "bust", emptyList()),
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
            ),
            "family" to listOf(
                Emoji("\uD83D\uDC6A", "family", emptyList()),
                Emoji("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC66", "family_mhb", emptyList()),
                Emoji("\uD83C\uDFE1", "house_with_garden", emptyList()),
            ),
            "gia đình" to listOf(
                Emoji("\uD83D\uDC6A", "family", emptyList()),
                Emoji("\uD83C\uDFE1", "house_with_garden", emptyList()),
                Emoji("\uD83C\uDFE0", "house", emptyList()),
            ),
            "baby" to listOf(
                Emoji("\uD83D\uDC76", "baby", emptyList()),
                Emoji("\uD83E\uDDD2", "child", emptyList()),
                Emoji("\uD83D\uDC78", "princess", emptyList()),
            ),
            "em bé" to listOf(
                Emoji("\uD83D\uDC76", "baby", emptyList()),
                Emoji("\uD83E\uDDD2", "child", emptyList()),
                Emoji("\uD83D\uDC78", "princess", emptyList()),
            ),
            "king" to listOf(
                Emoji("\uD83D\uDC51", "crown", emptyList()),
                Emoji("\uD83E\uDD34", "prince", emptyList()),
                Emoji("\uD83D\uDC78", "princess", emptyList()),
            ),
            "vua" to listOf(
                Emoji("\uD83D\uDC51", "crown", emptyList()),
                Emoji("\uD83E\uDD34", "prince", emptyList()),
            ),
            "teacher" to listOf(
                Emoji("\uD83E\uDDD1\u200D\uD83C\uDFEB", "teacher", emptyList()),
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83C\uDF93", "graduation", emptyList()),
            ),
            "thầy" to listOf(
                Emoji("\uD83E\uDDD1\u200D\uD83C\uDFEB", "teacher", emptyList()),
                Emoji("\uD83D\uDCDA", "books", emptyList()),
                Emoji("\uD83C\uDFEB", "school", emptyList()),
            ),
            "doctor" to listOf(
                Emoji("\uD83E\uDDD1\u200D\u2695\uFE0F", "doctor", emptyList()),
                Emoji("\uD83D\uDE91", "ambulance", emptyList()),
                Emoji("\uD83C\uDFE5", "hospital", emptyList()),
            ),
            "bác sĩ" to listOf(
                Emoji("\uD83E\uDDD1\u200D\u2695\uFE0F", "doctor", emptyList()),
                Emoji("\uD83D\uDE91", "ambulance", emptyList()),
                Emoji("\uD83C\uDFE5", "hospital", emptyList()),
            ),
            "police" to listOf(
                Emoji("\uD83D\uDC6E", "police", emptyList()),
                Emoji("\uD83D\uDE93", "police_car", emptyList()),
                Emoji("\uD83D\uDEA8", "siren", emptyList()),
            ),
            "công an" to listOf(
                Emoji("\uD83D\uDC6E", "police", emptyList()),
                Emoji("\uD83D\uDE93", "police_car", emptyList()),
            ),
            "artist" to listOf(
                Emoji("\uD83C\uDFA8", "art", emptyList()),
                Emoji("\uD83E\uDDD1\u200D\uD83C\uDFA8", "artist", emptyList()),
                Emoji("\uD83C\uDFB5", "music", emptyList()),
            ),
            "nghệ sĩ" to listOf(
                Emoji("\uD83C\uDFA8", "art", emptyList()),
                Emoji("\uD83E\uDDD1\u200D\uD83C\uDFA8", "artist", emptyList()),
                Emoji("\uD83C\uDFB5", "music", emptyList()),
            ),

            // === EVENTS / CELEBRATIONS ===
            "party" to listOf(
                Emoji("\uD83C\uDF89", "party", emptyList()),
                Emoji("\uD83C\uDF8A", "confetti", emptyList()),
                Emoji("\uD83C\uDF81", "gift", emptyList()),
            ),
            "tiệc" to listOf(
                Emoji("\uD83C\uDF89", "party", emptyList()),
                Emoji("\uD83C\uDF8A", "confetti", emptyList()),
                Emoji("\uD83C\uDF81", "gift", emptyList()),
            ),
            "birthday" to listOf(
                Emoji("\uD83C\uDF82", "birthday", emptyList()),
                Emoji("\uD83C\uDF70", "cake", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "sinh nhật" to listOf(
                Emoji("\uD83C\uDF82", "birthday", emptyList()),
                Emoji("\uD83C\uDF70", "cake", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "gift" to listOf(
                Emoji("\uD83C\uDF81", "gift", emptyList()),
                Emoji("\uD83C\uDF70", "cake", emptyList()),
                Emoji("\uD83C\uDF8A", "confetti", emptyList()),
            ),
            "quà" to listOf(
                Emoji("\uD83C\uDF81", "gift", emptyList()),
                Emoji("\uD83C\uDF70", "cake", emptyList()),
                Emoji("\uD83C\uDF8A", "confetti", emptyList()),
            ),
            "wedding" to listOf(
                Emoji("\uD83D\uDC8D", "ring", emptyList()),
                Emoji("\uD83D\uDC91", "couple", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "đám cưới" to listOf(
                Emoji("\uD83D\uDC8D", "ring", emptyList()),
                Emoji("\uD83D\uDC91", "couple", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "christmas" to listOf(
                Emoji("\uD83C\uDF84", "christmas_tree", emptyList()),
                Emoji("\uD83C\uDF85", "santa", emptyList()),
                Emoji("\u2744\uFE0F", "snowflake", emptyList()),
            ),
            "giáng sinh" to listOf(
                Emoji("\uD83C\uDF84", "christmas_tree", emptyList()),
                Emoji("\uD83C\uDF85", "santa", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "new year" to listOf(
                Emoji("\uD83C\uDF86", "fireworks", emptyList()),
                Emoji("\uD83C\uDF87", "sparkler", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "năm mới" to listOf(
                Emoji("\uD83C\uDF86", "fireworks", emptyList()),
                Emoji("\uD83C\uDF87", "sparkler", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "tết" to listOf(
                Emoji("\uD83C\uDF86", "fireworks", emptyList()),
                Emoji("\uD83C\uDF87", "sparkler", emptyList()),
                Emoji("\uD83C\uDF8A", "confetti", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),

            // === MONEY / BUSINESS ===
            "money" to listOf(
                Emoji("\uD83D\uDCB0", "money", emptyList()),
                Emoji("\uD83D\uDCB5", "dollar", emptyList()),
                Emoji("\uD83D\uDCB8", "moneybag", emptyList()),
            ),
            "tiền" to listOf(
                Emoji("\uD83D\uDCB0", "money", emptyList()),
                Emoji("\uD83D\uDCB5", "dollar", emptyList()),
                Emoji("\uD83D\uDCB8", "moneybag", emptyList()),
            ),
            "rich" to listOf(
                Emoji("\uD83D\uDCB8", "moneybag", emptyList()),
                Emoji("\uD83D\uDCB0", "money", emptyList()),
                Emoji("\uD83E\uDD11", "money_mouth", emptyList()),
            ),
            "giàu" to listOf(
                Emoji("\uD83D\uDCB8", "moneybag", emptyList()),
                Emoji("\uD83D\uDCB0", "money", emptyList()),
                Emoji("\uD83E\uDD11", "money_mouth", emptyList()),
            ),

            // === HEALTH / BODY ===
            "strong" to listOf(
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
                Emoji("\uD83C\uDFCB\uFE0F", "weight_lifter", emptyList()),
                Emoji("\uD83D\uDC4A", "fist", emptyList()),
            ),
            "khỏe" to listOf(
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
                Emoji("\uD83C\uDFCB\uFE0F", "weight_lifter", emptyList()),
                Emoji("\uD83D\uDC4A", "fist", emptyList()),
            ),
            "sick" to listOf(
                Emoji("\uD83E\uDD12", "mask", emptyList()),
                Emoji("\uD83D\uDE37", "mask", emptyList()),
                Emoji("\uD83D\uDE16", "confounded", emptyList()),
            ),
            "ốm" to listOf(
                Emoji("\uD83E\uDD12", "mask", emptyList()),
                Emoji("\uD83D\uDE37", "mask", emptyList()),
                Emoji("\uD83D\uDE16", "confounded", emptyList()),
            ),
            "đau" to listOf(
                Emoji("\uD83D\uDE16", "confounded", emptyList()),
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
                Emoji("\uD83C\uDFE5", "hospital", emptyList()),
            ),
            "hurt" to listOf(
                Emoji("\uD83D\uDE16", "confounded", emptyList()),
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
                Emoji("\uD83D\uDE28", "fearful", emptyList()),
            ),
            "beauty" to listOf(
                Emoji("\uD83D\uDC90", "bouquet", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\uD83C\uDF39", "rose", emptyList()),
            ),
            "sắc đẹp" to listOf(
                Emoji("\uD83D\uDC90", "bouquet", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\uD83C\uDF39", "rose", emptyList()),
            ),

            // === ADDITIONAL VIETNAMESE WORDS ===
            "nước" to listOf(
                Emoji("\uD83D\uDCA7", "droplet", emptyList()),
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
                Emoji("\u2615", "coffee", emptyList()),
            ),
            "trà" to listOf(
                Emoji("\uD83C\uDF75", "tea", emptyList()),
                Emoji("\u2615", "coffee", emptyList()),
            ),
            "rượu" to listOf(
                Emoji("\uD83C\uDF77", "wine", emptyList()),
                Emoji("\uD83C\uDF7A", "beer", emptyList()),
                Emoji("\uD83C\uDF78", "cocktail", emptyList()),
            ),
            "lúa" to listOf(
                Emoji("\uD83C\uDF3E", "rice", emptyList()),
                Emoji("\uD83C\uDF3D", "corn", emptyList()),
            ),
            "hoa quả" to listOf(
                Emoji("\uD83C\uDF4E", "apple", emptyList()),
                Emoji("\uD83C\uDF4C", "banana", emptyList()),
                Emoji("\uD83C\uDF53", "strawberry", emptyList()),
            ),
            "rau" to listOf(
                Emoji("\uD83E\uDD55", "carrot", emptyList()),
                Emoji("\uD83C\uDF3F", "herb", emptyList()),
                Emoji("\uD83C\uDF45", "tomato", emptyList()),
            ),
            "thịt" to listOf(
                Emoji("\uD83C\uDF56", "meat", emptyList()),
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83C\uDF57", "poultry", emptyList()),
            ),
            "tôm" to listOf(
                Emoji("\uD83E\uDD90", "shrimp", emptyList()),
                Emoji("\uD83E\uDD99", "lobster", emptyList()),
                Emoji("\uD83D\uDC1F", "fish", emptyList()),
            ),
            "cua" to listOf(
                Emoji("\uD83E\uDD80", "crab", emptyList()),
                Emoji("\uD83E\uDD99", "lobster", emptyList()),
            ),
            "phở" to listOf(
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
                Emoji("\uD83C\uDF71", "bento", emptyList()),
                Emoji("\uD83C\uDF72", "curry", emptyList()),
            ),
            "bún" to listOf(
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
                Emoji("\uD83C\uDF5D", "spaghetti", emptyList()),
            ),
            "trời" to listOf(
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\u2601\uFE0F", "cloud", emptyList()),
                Emoji("\uD83C\uDF27\uFE0F", "rain", emptyList()),
            ),
            "đất" to listOf(
                Emoji("\uD83C\uDF0D", "globe", emptyList()),
                Emoji("\u26F0\uFE0F", "mountain", emptyList()),
                Emoji("\uD83C\uDF33", "tree", emptyList()),
            ),
            "rừng" to listOf(
                Emoji("\uD83C\uDF33", "tree", emptyList()),
                Emoji("\uD83C\uDF34", "palm_tree", emptyList()),
                Emoji("\uD83C\uDF32", "evergreen", emptyList()),
            ),
            "vườn" to listOf(
                Emoji("\uD83C\uDF3F", "herb", emptyList()),
                Emoji("\uD83C\uDF31", "seedling", emptyList()),
                Emoji("\uD83C\uDF39", "rose", emptyList()),
            ),
            "hồ" to listOf(
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
                Emoji("\uD83D\uDCA7", "droplet", emptyList()),
                Emoji("\uD83D\uDC1F", "fish", emptyList()),
            ),
            "thành phố" to listOf(
                Emoji("\uD83C\uDFD9\uFE0F", "city", emptyList()),
                Emoji("\uD83C\uDF03", "night", emptyList()),
                Emoji("\uD83C\uDF0D", "globe", emptyList()),
            ),
            "làng" to listOf(
                Emoji("\uD83C\uDFE0", "house", emptyList()),
                Emoji("\uD83C\uDF33", "tree", emptyList()),
                Emoji("\uD83C\uDF3E", "rice", emptyList()),
            ),
            "chợ" to listOf(
                Emoji("\uD83D\uDED2", "shopping_cart", emptyList()),
                Emoji("\uD83C\uDFEA", "convenience", emptyList()),
                Emoji("\uD83D\uDECD\uFE0F", "shopping_bags", emptyList()),
            ),
            "cửa hàng" to listOf(
                Emoji("\uD83C\uDFEA", "convenience", emptyList()),
                Emoji("\uD83D\uDED2", "shopping_cart", emptyList()),
            ),
            "ngân hàng" to listOf(
                Emoji("\uD83C\uDFE6", "bank", emptyList()),
                Emoji("\uD83D\uDCB0", "money", emptyList()),
                Emoji("\uD83D\uDCB5", "dollar", emptyList()),
            ),
            "bưu điện" to listOf(
                Emoji("\uD83C\uDFE4", "post_office", emptyList()),
                Emoji("\u2709\uFE0F", "envelope", emptyList()),
            ),
            "sân bay" to listOf(
                Emoji("\u2708\uFE0F", "airplane", emptyList()),
                Emoji("\uD83D\uDEEB\uFE0F", "airplane_arrival", emptyList()),
            ),
            "bến xe" to listOf(
                Emoji("\uD83D\uDE8C", "bus", emptyList()),
                Emoji("\uD83D\uDE95", "bus", emptyList()),
            ),
            "ga" to listOf(
                Emoji("\uD83D\uDE82", "train", emptyList()),
                Emoji("\uD83D\uDE86", "train2", emptyList()),
                Emoji("\uD83D\uDE84", "bullettrain", emptyList()),
            ),
            "tàu" to listOf(
                Emoji("\uD83D\uDE82", "train", emptyList()),
                Emoji("\uD83D\uDE86", "train2", emptyList()),
                Emoji("\uD83D\uDEA2", "ship", emptyList()),
            ),
            "thuyền" to listOf(
                Emoji("\u26F5", "sailboat", emptyList()),
                Emoji("\uD83D\uDEA2", "ship", emptyList()),
                Emoji("\uD83D\uDEE5\uFE0F", "motorboat", emptyList()),
            ),
            "ánh sáng" to listOf(
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83D\uDCA1", "bulb", emptyList()),
            ),
            "bóng tối" to listOf(
                Emoji("\uD83C\uDF19", "moon", emptyList()),
                Emoji("\uD83C\uDF03", "night", emptyList()),
            ),
            "nhiệt" to listOf(
                Emoji("\uD83D\uDD25", "fire", emptyList()),
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\uD83C\uDF21\uFE0F", "thermometer", emptyList()),
            ),
            "mát" to listOf(
                Emoji("\uD83C\uDF2C\uFE0F", "wind", emptyList()),
                Emoji("\uD83D\uDCA6", "sweat_drops", emptyList()),
                Emoji("\uD83C\uDF27\uFE0F", "rain", emptyList()),
            ),
            "ẩm" to listOf(
                Emoji("\uD83D\uDCA7", "droplet", emptyList()),
                Emoji("\uD83C\uDF27\uFE0F", "rain", emptyList()),
            ),
            "khô" to listOf(
                Emoji("\u2600\uFE0F", "sun", emptyList()),
                Emoji("\uD83D\uDD25", "fire", emptyList()),
            ),
            "sương" to listOf(
                Emoji("\uD83C\uDF2B\uFE0F", "fog", emptyList()),
                Emoji("\uD83D\uDCA7", "droplet", emptyList()),
                Emoji("\u2744\uFE0F", "snowflake", emptyList()),
            ),
            "băng" to listOf(
                Emoji("\u2744\uFE0F", "snowflake", emptyList()),
                Emoji("\uD83E\uDDCA", "ice_cube", emptyList()),
                Emoji("\u26C4", "snowman", emptyList()),
            ),
            "vàng" to listOf(
                Emoji("\uD83D\uDCB0", "money", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83D\uDC8D", "ring", emptyList()),
            ),
            "bạc" to listOf(
                Emoji("\uD83D\uDCB0", "money", emptyList()),
                Emoji("\uD83D\uDC8D", "ring", emptyList()),
            ),
            "đá" to listOf(
                Emoji("\uD83E\uDEA8", "rock", emptyList()),
                Emoji("\u26F0\uFE0F", "mountain", emptyList()),
                Emoji("\uD83C\uDFD4\uFE0F", "snow_capped_mountain", emptyList()),
            ),
            "cát" to listOf(
                Emoji("\uD83C\uDFD6\uFE0F", "beach", emptyList()),
                Emoji("\uD83C\uDF0A", "wave", emptyList()),
                Emoji("\u2600\uFE0F", "sun", emptyList()),
            ),
            "bụi" to listOf(
                Emoji("\uD83C\uDF2A\uFE0F", "fog", emptyList()),
                Emoji("\uD83C\uDF2C\uFE0F", "wind", emptyList()),
            ),
            "khói" to listOf(
                Emoji("\uD83D\uDCA8", "dash", emptyList()),
                Emoji("\uD83D\uDD25", "fire", emptyList()),
                Emoji("\uD83C\uDF2A\uFE0F", "fog", emptyList()),
            ),
            "điện" to listOf(
                Emoji("\uD83D\uDCA1", "bulb", emptyList()),
                Emoji("\u26A1", "zap", emptyList()),
                Emoji("\uD83C\uDF29\uFE0F", "lightning", emptyList()),
            ),
            "lời" to listOf(
                Emoji("\uD83D\uDCAC", "speech", emptyList()),
                Emoji("\uD83D\uDCE2", "loudspeaker", emptyList()),
            ),
            "im lặng" to listOf(
                Emoji("\uD83E\uDD2B", "shush", emptyList()),
                Emoji("\uD83D\uDE10", "neutral", emptyList()),
                Emoji("\uD83D\uDE30", "cold_sweat", emptyList()),
            ),
            "nói" to listOf(
                Emoji("\uD83D\uDCAC", "speech", emptyList()),
                Emoji("\uD83D\uDDE3\uFE0F", "speaking_head", emptyList()),
                Emoji("\uD83D\uDCE2", "loudspeaker", emptyList()),
            ),
            "cười" to listOf(
                Emoji("\uD83D\uDE04", "smile", emptyList()),
                Emoji("\uD83D\uDE02", "joy", emptyList()),
                Emoji("\uD83E\uDD23", "rofl", emptyList()),
            ),
            "khóc" to listOf(
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
                Emoji("\uD83D\uDE22", "cry", emptyList()),
                Emoji("\uD83D\uDE25", "disappointed_relieved", emptyList()),
            ),
            "cãi" to listOf(
                Emoji("\uD83D\uDE20", "angry", emptyList()),
                Emoji("\uD83D\uDE24", "triumph", emptyList()),
                Emoji("\uD83D\uDE21", "rage", emptyList()),
            ),
            "hôn" to listOf(
                Emoji("\uD83D\uDC8B", "kiss", emptyList()),
                Emoji("\uD83D\uDE18", "kiss", emptyList()),
                Emoji("\u2764\uFE0F", "heart", emptyList()),
            ),
            "ôm" to listOf(
                Emoji("\uD83E\uDD1F", "hug", emptyList()),
                Emoji("\uD83D\uDC91", "couple", emptyList()),
                Emoji("\u2764\uFE0F", "heart", emptyList()),
            ),
            "cưới" to listOf(
                Emoji("\uD83D\uDC8D", "ring", emptyList()),
                Emoji("\uD83D\uDC91", "couple", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "giúp" to listOf(
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDE4C", "ok_hand", emptyList()),
            ),
            "help" to listOf(
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDE4C", "ok_hand", emptyList()),
            ),
            "cố gắng" to listOf(
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
            ),
            "thử" to listOf(
                Emoji("\uD83D\uDE42", "smirk", emptyList()),
                Emoji("\uD83E\uDD14", "thinking", emptyList()),
                Emoji("\uD83C\uDF1F", "glowing_star", emptyList()),
            ),
            "nhớ" to listOf(
                Emoji("\uD83E\uDD0D", "thinking", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
                Emoji("\uD83D\uDC96", "sparkling_heart", emptyList()),
            ),
            "quên" to listOf(
                Emoji("\uD83D\uDE35", "dizzy", emptyList()),
                Emoji("\uD83E\uDD14", "thinking", emptyList()),
                Emoji("\uD83D\uDE33", "flushed", emptyList()),
            ),
            "forget" to listOf(
                Emoji("\uD83D\uDE35", "dizzy", emptyList()),
                Emoji("\uD83E\uDD14", "thinking", emptyList()),
                Emoji("\uD83D\uDE33", "flushed", emptyList()),
            ),
            "chờ" to listOf(
                Emoji("\u23F3", "hourglass", emptyList()),
                Emoji("\uD83D\uDE10", "neutral", emptyList()),
                Emoji("\uD83D\uDE12", "unamused", emptyList()),
            ),
            "wait" to listOf(
                Emoji("\u23F3", "hourglass", emptyList()),
                Emoji("\uD83D\uDE10", "neutral", emptyList()),
                Emoji("\uD83D\uDE12", "unamused", emptyList()),
            ),
            "vội" to listOf(
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\u23F0", "clock", emptyList()),
                Emoji("\uD83D\uDE05", "sweat_smile", emptyList()),
            ),
            "chậm" to listOf(
                Emoji("\uD83D\uDC22", "turtle", emptyList()),
                Emoji("\uD83D\uDC0C", "snail", emptyList()),
                Emoji("\u23F3", "hourglass", emptyList()),
            ),
            "slow" to listOf(
                Emoji("\uD83D\uDC22", "turtle", emptyList()),
                Emoji("\uD83D\uDC0C", "snail", emptyList()),
                Emoji("\u23F3", "hourglass", emptyList()),
            ),
            "fast" to listOf(
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\u26A1", "zap", emptyList()),
                Emoji("\uD83D\uDE80", "rocket", emptyList()),
            ),
            "nhanh" to listOf(
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\u26A1", "zap", emptyList()),
                Emoji("\uD83D\uDE80", "rocket", emptyList()),
            ),
            "xa" to listOf(
                Emoji("\uD83C\uDF0D", "globe", emptyList()),
                Emoji("\u2708\uFE0F", "airplane", emptyList()),
                Emoji("\uD83D\uDEEC\uFE0F", "airplane_departure", emptyList()),
            ),
            "gần" to listOf(
                Emoji("\uD83D\uDC65", "busts", emptyList()),
                Emoji("\uD83C\uDFE0", "house", emptyList()),
                Emoji("\uD83E\uDD1D", "handshake", emptyList()),
            ),
            "cao" to listOf(
                Emoji("\uD83C\uDFD4\uFE0F", "snow_capped_mountain", emptyList()),
                Emoji("\uD83C\uDF33", "tree", emptyList()),
                Emoji("\uD83C\uDFC2", "snowboarder", emptyList()),
            ),
            "thấp" to listOf(
                Emoji("\uD83D\uDE22", "cry", emptyList()),
                Emoji("\uD83D\uDC30", "rabbit", emptyList()),
                Emoji("\uD83D\uDC22", "turtle", emptyList()),
            ),
            "to" to listOf(
                Emoji("\uD83D\uDC18", "elephant", emptyList()),
                Emoji("\uD83D\uDC3B", "bear", emptyList()),
                Emoji("\uD83C\uDF33", "tree", emptyList()),
            ),
            "nhỏ" to listOf(
                Emoji("\uD83D\uDC35", "monkey", emptyList()),
                Emoji("\uD83D\uDC30", "rabbit", emptyList()),
                Emoji("\uD83D\uDC39", "hamster", emptyList()),
            ),
            "lớn" to listOf(
                Emoji("\uD83D\uDC18", "elephant", emptyList()),
                Emoji("\uD83D\uDC3B", "bear", emptyList()),
                Emoji("\uD83C\uDFD4\uFE0F", "snow_capped_mountain", emptyList()),
            ),
            "dài" to listOf(
                Emoji("\uD83D\uDC0D", "snake", emptyList()),
                Emoji("\uD83D\uDC27", "giraffe", emptyList()),
                Emoji("\uD83D\uDE84", "bullettrain", emptyList()),
            ),
            "ngắn" to listOf(
                Emoji("\uD83D\uDC30", "rabbit", emptyList()),
                Emoji("\uD83D\uDC39", "hamster", emptyList()),
                Emoji("\uD83D\uDC35", "monkey", emptyList()),
            ),
            "đầy" to listOf(
                Emoji("\uD83D\uDCAF", "full", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "rỗng" to listOf(
                Emoji("\u26AA", "white_circle", emptyList()),
                Emoji("\u274C", "cross_mark", emptyList()),
            ),
            "mới" to listOf(
                Emoji("\uD83C\uDF89", "party", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "cũ" to listOf(
                Emoji("\uD83C\uDFE0", "house", emptyList()),
                Emoji("\uD83D\uDCF1", "phone", emptyList()),
                Emoji("\uD83D\uDCBB", "laptop", emptyList()),
            ),
            "non" to listOf(
                Emoji("\uD83E\uDDD2", "child", emptyList()),
                Emoji("\uD83C\uDF31", "seedling", emptyList()),
                Emoji("\uD83D\uDC76", "baby", emptyList()),
            ),
            "già" to listOf(
                Emoji("\uD83E\uDDD3", "older_adult", emptyList()),
                Emoji("\uD83D\uDC74", "older_man", emptyList()),
                Emoji("\uD83E\uDDD1", "adult", emptyList()),
            ),
            "trẻ" to listOf(
                Emoji("\uD83D\uDC76", "baby", emptyList()),
                Emoji("\uD83E\uDDD2", "child", emptyList()),
                Emoji("\uD83C\uDF31", "seedling", emptyList()),
            ),

            // === ADDITIONAL ENGLISH WORDS ===
            "smile" to listOf(
                Emoji("\uD83D\uDE04", "smile", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
                Emoji("\uD83D\uDE01", "grin", emptyList()),
            ),
            "wow" to listOf(
                Emoji("\uD83D\uDE2E", "open_mouth", emptyList()),
                Emoji("\uD83D\uDE32", "astonished", emptyList()),
                Emoji("\uD83E\uDD2F", "shocked", emptyList()),
            ),
            "omg" to listOf(
                Emoji("\uD83D\uDE31", "scream", emptyList()),
                Emoji("\uD83D\uDE2E", "open_mouth", emptyList()),
                Emoji("\uD83D\uDE32", "astonished", emptyList()),
            ),
            "lol" to listOf(
                Emoji("\uD83D\uDE02", "joy", emptyList()),
                Emoji("\uD83E\uDD23", "rofl", emptyList()),
                Emoji("\uD83D\uDE06", "sweat_smile", emptyList()),
            ),
            "omw" to listOf(
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\uD83D\uDE97", "taxi", emptyList()),
                Emoji("\uD83D\uDE95", "bus", emptyList()),
            ),
            "idk" to listOf(
                Emoji("\uD83E\uDD37", "shrug", emptyList()),
                Emoji("\uD83E\uDD14", "thinking", emptyList()),
                Emoji("\uD83D\uDE15", "confused", emptyList()),
            ),
            "ty" to listOf(
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
            ),
            "np" to listOf(
                Emoji("\uD83D\uDC4C", "ok_hand", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
            ),
            "brb" to listOf(
                Emoji("\uD83C\uDFC3", "runner", emptyList()),
                Emoji("\u23F3", "hourglass", emptyList()),
            ),
            "afk" to listOf(
                Emoji("\uD83D\uDE34", "sleep", emptyList()),
                Emoji("\uD83D\uDCBB", "laptop", emptyList()),
            ),
            "gg" to listOf(
                Emoji("\uD83D\uDC4F", "clap", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83D\uDC4D", "thumbsup", emptyList()),
            ),
            "rip" to listOf(
                Emoji("\uD83E\uDEAE", "headstone", emptyList()),
                Emoji("\uD83D\uDE22", "cry", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "win" to listOf(
                Emoji("\uD83C\uDFC6", "trophy", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "lose" to listOf(
                Emoji("\uD83D\uDE22", "cry", emptyList()),
                Emoji("\uD83D\uDE2D", "sob", emptyList()),
                Emoji("\uD83D\uDE1E", "disappointed", emptyList()),
            ),
            "hungry" to listOf(
                Emoji("\uD83C\uDF54", "burger", emptyList()),
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
                Emoji("\uD83C\uDF5C", "ramen", emptyList()),
            ),
            "thirsty" to listOf(
                Emoji("\u2615", "coffee", emptyList()),
                Emoji("\uD83C\uDF77", "wine", emptyList()),
                Emoji("\uD83C\uDF7A", "beer", emptyList()),
            ),
            "full" to listOf(
                Emoji("\uD83D\uDE0B", "yum", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
            ),
            "brave" to listOf(
                Emoji("\uD83E\uDD81", "lion", emptyList()),
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
                Emoji("\uD83D\uDE0E", "sunglasses", emptyList()),
            ),
            "lazy" to listOf(
                Emoji("\uD83D\uDE34", "sleep", emptyList()),
                Emoji("\uD83D\uDE10", "neutral", emptyList()),
                Emoji("\uD83D\uDE2B", "tired", emptyList()),
            ),
            "smart" to listOf(
                Emoji("\uD83E\uDDD0", "nerd", emptyList()),
                Emoji("\uD83E\uDD13", "nerd_face", emptyList()),
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
            ),
            "crazy" to listOf(
                Emoji("\uD83E\uDD2A", "crazy", emptyList()),
                Emoji("\uD83D\uDE35", "dizzy", emptyList()),
                Emoji("\uD83D\uDE02", "joy", emptyList()),
            ),
            "genius" to listOf(
                Emoji("\uD83E\uDDD0", "nerd", emptyList()),
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "winner" to listOf(
                Emoji("\uD83C\uDFC6", "trophy", emptyList()),
                Emoji("\uD83E\uDD47", "first_place", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "loser" to listOf(
                Emoji("\uD83D\uDE1E", "disappointed", emptyList()),
                Emoji("\uD83D\uDE22", "cry", emptyList()),
            ),
            "champion" to listOf(
                Emoji("\uD83C\uDFC6", "trophy", emptyList()),
                Emoji("\uD83E\uDD47", "first_place", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "hero" to listOf(
                Emoji("\uD83E\uDD8F", "superhero", emptyList()),
                Emoji("\uD83E\uDDB8", "supervillain", emptyList()),
                Emoji("\uD83D\uDCAA", "muscle", emptyList()),
            ),
            "victory" to listOf(
                Emoji("\u270C\uFE0F", "victory", emptyList()),
                Emoji("\uD83C\uDFC6", "trophy", emptyList()),
                Emoji("\uD83C\uDF89", "party", emptyList()),
            ),
            "peace" to listOf(
                Emoji("\u270C\uFE0F", "victory", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
                Emoji("\uD83D\uDE4C", "ok_hand", emptyList()),
            ),
            "war" to listOf(
                Emoji("\u2694\uFE0F", "crossed_swords", emptyList()),
                Emoji("\uD83D\uDCA3", "bomb", emptyList()),
                Emoji("\uD83D\uDCA5", "boom", emptyList()),
            ),
            "danger" to listOf(
                Emoji("\u26A0\uFE0F", "warning", emptyList()),
                Emoji("\uD83D\uDE31", "scream", emptyList()),
                Emoji("\uD83D\uDCA5", "boom", emptyList()),
            ),
            "safe" to listOf(
                Emoji("\uD83D\uDEE1\uFE0F", "shield", emptyList()),
                Emoji("\u2705", "white_check_mark", emptyList()),
                Emoji("\uD83D\uDE0A", "blush", emptyList()),
            ),
            "luck" to listOf(
                Emoji("\uD83C\uDF40", "four_leaf_clover", emptyList()),
                Emoji("\uD83E\uDD1E", "fingers_crossed", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "wish" to listOf(
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF20", "shooting_star", emptyList()),
                Emoji("\uD83D\uDE4F", "pray", emptyList()),
            ),
            "dream" to listOf(
                Emoji("\uD83D\uDE34", "sleep", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83C\uDF19", "moon", emptyList()),
            ),
            "magic" to listOf(
                Emoji("\uD83E\uDDE9", "magic_wand", emptyList()),
                Emoji("\u2728", "sparkles", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
            ),
            "secret" to listOf(
                Emoji("\uD83E\uDD2B", "shush", emptyList()),
                Emoji("\uD83D\uDD12", "lock", emptyList()),
                Emoji("\uD83D\uDD11", "key", emptyList()),
            ),
            "truth" to listOf(
                Emoji("\uD83D\uDCAC", "speech", emptyList()),
                Emoji("\u2B50", "star", emptyList()),
                Emoji("\uD83D\uDD0D", "magic_right", emptyList()),
            ),
            "lie" to listOf(
                Emoji("\uD83D\uDC5C", "handbag", emptyList()),
                Emoji("\uD83E\uDD14", "thinking", emptyList()),
                Emoji("\uD83D\uDE45", "no_good", emptyList()),
            ),
        )
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val updateHistory = prefs.emoji.suggestionUpdateHistory.get()
        if (candidate is EmojiSuggestionCandidate) {
            if (updateHistory) {
                EmojiHistoryHelper.markEmojiUsed(prefs, candidate.emoji)
            }
            val word = candidate.secondaryText?.toString()?.lowercase() ?: return
            val stats = emojiUsageStats.getOrPut(word) { mutableMapOf() }
            val emojiStr = candidate.text.toString()
            stats[emojiStr] = (stats[emojiStr] ?: 0) + 1
        }
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
