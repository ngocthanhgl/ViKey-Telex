/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.ngocthanhgl.vikey.ime.keyboard

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRightAlt
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.ime.core.DisplayLanguageNamesIn
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.editor.FlorisEditorInfo
import dev.ngocthanhgl.vikey.ime.editor.ImeOptions
import dev.ngocthanhgl.vikey.ime.input.InputShiftState
import dev.ngocthanhgl.vikey.ime.text.key.KeyCode
import dev.ngocthanhgl.vikey.ime.text.key.KeyType
import dev.ngocthanhgl.vikey.lib.FlorisLocale
import dev.ngocthanhgl.vikey.lib.compose.vectorResource
import org.florisboard.lib.compose.icons.ForwardDelete

interface ComputingEvaluator {
    val version: Int

    val keyboard: Keyboard

    val editorInfo: FlorisEditorInfo

    val state: KeyboardState

    val subtype: Subtype

    fun context(): Context?

    fun displayLanguageNamesIn(): DisplayLanguageNamesIn

    fun evaluateEnabled(data: KeyData): Boolean

    fun evaluateVisible(data: KeyData): Boolean

    fun isSlot(data: KeyData): Boolean

    fun slotData(data: KeyData): KeyData?
}

object DefaultComputingEvaluator : ComputingEvaluator {
    override val version = -1

    override val keyboard = PlaceholderLoadingKeyboard

    override val editorInfo = FlorisEditorInfo.Unspecified

    override val state = KeyboardState.new()

    override val subtype = Subtype.DEFAULT

    override fun context(): Context? = null

    override fun displayLanguageNamesIn() = DisplayLanguageNamesIn.NATIVE_LOCALE

    override fun evaluateEnabled(data: KeyData): Boolean = true

    override fun evaluateVisible(data: KeyData): Boolean = true

    override fun isSlot(data: KeyData): Boolean = false

    override fun slotData(data: KeyData): KeyData? = null
}

private var cachedDisplayNameState = Triple(FlorisLocale.ROOT, DisplayLanguageNamesIn.SYSTEM_LOCALE, "")

/**
 * Compute language name with a cache to prevent repetitive calling of `locale.displayName()`, which invokes the
 * underlying `LocaleNative.getLanguageName()` method and in turn uses the rather slow ICU data table to look up the
 * language name. This only caches the last display name, but that's more than enough, as a one-time re-computation when
 * the subtype changes does not hurt, the repetitive computation for the same language hurts.
 */
private fun computeLanguageDisplayName(locale: FlorisLocale, displayLanguageNamesIn: DisplayLanguageNamesIn): String {
    val (cachedLocale, cachedDisplayLanguageNamesIn, cachedDisplayName) = cachedDisplayNameState
    if (cachedLocale == locale && cachedDisplayLanguageNamesIn == displayLanguageNamesIn) {
        return cachedDisplayName
    }
    val displayName = when (displayLanguageNamesIn) {
        DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
        DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
    }
    cachedDisplayNameState = Triple(locale, displayLanguageNamesIn, displayName)
    return displayName
}

fun ComputingEvaluator.computeLabel(data: KeyData): String? {
    val evaluator = this
    return if (data.type == KeyType.CHARACTER && data.code != KeyCode.SPACE && data.code != KeyCode.CJK_SPACE
        && data.code != KeyCode.HALF_SPACE && data.code != KeyCode.KESHIDA || data.type == KeyType.NUMERIC
    ) {
        data.asString(isForDisplay = true)
    } else {
        when (data.code) {
            KeyCode.PHONE_PAUSE -> evaluator.context()?.getString(R.string.key__phone_pause)
            KeyCode.PHONE_WAIT -> evaluator.context()?.getString(R.string.key__phone_wait)
            KeyCode.SPACE, KeyCode.CJK_SPACE -> {
                when (evaluator.keyboard.mode) {
                    KeyboardMode.CHARACTERS -> evaluator.subtype.primaryLocale.let { locale ->
                        computeLanguageDisplayName(locale, evaluator.displayLanguageNamesIn())
                    }
                    else -> null
                }
            }
            KeyCode.IME_UI_MODE_TEXT,
            KeyCode.VIEW_CHARACTERS -> {
                evaluator.context()?.getString(R.string.key__view_characters)
            }
            KeyCode.VIEW_NUMERIC -> {
                evaluator.context()?.getString(R.string.key__view_numeric)
            }
            KeyCode.VIEW_NUMERIC_ADVANCED -> null
            KeyCode.VIEW_PHONE -> {
                evaluator.context()?.getString(R.string.key__view_phone)
            }
            KeyCode.VIEW_PHONE2 -> {
                evaluator.context()?.getString(R.string.key__view_phone2)
            }
            KeyCode.VIEW_SYMBOLS -> {
                evaluator.context()?.getString(R.string.key__view_symbols)
            }
            KeyCode.VIEW_SYMBOLS2 -> {
                evaluator.context()?.getString(R.string.key__view_symbols2)
            }
            KeyCode.HALF_SPACE -> {
                evaluator.context()?.getString(R.string.key__view_half_space)
            }
            KeyCode.KESHIDA -> {
                evaluator.context()?.getString(R.string.key__view_keshida)
            }
            else -> null
        }
    }
}

fun ComputingEvaluator.computeImageVector(data: KeyData): ImageVector? {
    val evaluator = this
    return when (data.code) {
        KeyCode.ARROW_LEFT -> {
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft
        }
        KeyCode.ARROW_RIGHT -> {
            Icons.AutoMirrored.Rounded.KeyboardArrowRight
        }
        KeyCode.ARROW_UP -> {
            Icons.Rounded.KeyboardArrowUp
        }
        KeyCode.ARROW_DOWN -> {
            Icons.Rounded.KeyboardArrowDown
        }
        KeyCode.CLIPBOARD_COPY -> {
            Icons.Rounded.ContentCopy
        }
        KeyCode.CLIPBOARD_CUT -> {
            Icons.Rounded.ContentCut
        }
        KeyCode.CLIPBOARD_PASTE -> {
            Icons.Rounded.ContentPasteGo
        }
        KeyCode.CLIPBOARD_SELECT_ALL -> {
            Icons.Rounded.SelectAll
        }
        KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
            Icons.Rounded.DeleteSweep
        }
        KeyCode.COMPACT_LAYOUT_TO_LEFT,
        KeyCode.COMPACT_LAYOUT_TO_RIGHT,
        KeyCode.TOGGLE_COMPACT_LAYOUT -> {
            Icons.Rounded.AccessibilityNew
        }
        KeyCode.TOGGLE_FLOATING_WINDOW -> {
            Icons.Rounded.PictureInPictureAlt
        }
        KeyCode.TOGGLE_RESIZE_MODE -> {
            Icons.Rounded.AspectRatio
        }
        KeyCode.VOICE_INPUT -> {
            Icons.Rounded.KeyboardVoice
        }
        KeyCode.IME_HIDE_UI -> {
            Icons.Rounded.KeyboardHide
        }
        KeyCode.DELETE -> {
            Icons.AutoMirrored.Rounded.Backspace
        }
        KeyCode.ENTER -> {
            val imeOptions = evaluator.editorInfo.imeOptions
            val inputAttributes = evaluator.editorInfo.inputAttributes
            if (imeOptions.flagNoEnterAction || inputAttributes.flagTextMultiLine) {
                Icons.AutoMirrored.Rounded.KeyboardReturn
            } else {
                when (imeOptions.action) {
                    ImeOptions.Action.DONE -> Icons.Rounded.Done
                    ImeOptions.Action.GO -> Icons.AutoMirrored.Rounded.ArrowRightAlt
                    ImeOptions.Action.NEXT -> Icons.AutoMirrored.Rounded.ArrowRightAlt
                    ImeOptions.Action.NONE -> Icons.AutoMirrored.Rounded.KeyboardReturn
                    ImeOptions.Action.PREVIOUS -> Icons.AutoMirrored.Rounded.ArrowRightAlt
                    ImeOptions.Action.SEARCH -> Icons.Rounded.Search
                    ImeOptions.Action.SEND -> Icons.AutoMirrored.Rounded.Send
                    ImeOptions.Action.UNSPECIFIED -> Icons.AutoMirrored.Rounded.KeyboardReturn
                }
            }
        }
        KeyCode.FORWARD_DELETE -> {
            Icons.AutoMirrored.Rounded.ForwardDelete
        }
        KeyCode.IME_UI_MODE_MEDIA -> {
            Icons.Rounded.SentimentSatisfiedAlt
        }
        KeyCode.IME_UI_MODE_CLIPBOARD -> {
            Icons.AutoMirrored.Rounded.Assignment
        }
        KeyCode.LANGUAGE_SWITCH -> {
            Icons.Rounded.Language
        }
        KeyCode.SETTINGS -> {
            Icons.Rounded.Settings
        }
        KeyCode.SHIFT -> {
            when (evaluator.state.inputShiftState != InputShiftState.UNSHIFTED) {
                true -> Icons.Rounded.KeyboardCapslock
                else -> Icons.Rounded.KeyboardArrowUp
            }
        }
        KeyCode.SPACE, KeyCode.CJK_SPACE -> {
            when (evaluator.keyboard.mode) {
                KeyboardMode.CHARACTERS -> null
                else -> Icons.Rounded.SpaceBar
            }
        }
        KeyCode.VIEW_NUMERIC_ADVANCED -> {
            Icons.Rounded.Numbers
        }
        KeyCode.UNDO -> {
            Icons.AutoMirrored.Rounded.Undo
        }
        KeyCode.REDO -> {
            Icons.AutoMirrored.Rounded.Redo
        }
        KeyCode.TOGGLE_ACTIONS_OVERFLOW -> {
            Icons.Rounded.MoreHoriz
        }
        KeyCode.TOGGLE_INCOGNITO_MODE -> {
            Icons.Rounded.VisibilityOff
        }
        KeyCode.TOGGLE_AUTOCORRECT -> {
            Icons.Rounded.Spellcheck
        }
        KeyCode.KANA_SWITCHER -> {
            if (evaluator.state.isKanaKata) {
                this.context()?.vectorResource(R.drawable.ic_keyboard_kana_switcher_kata)
            } else {
                this.context()?.vectorResource(R.drawable.ic_keyboard_kana_switcher_hira)
            }
        }
        KeyCode.CHAR_WIDTH_SWITCHER -> {
            if (evaluator.state.isCharHalfWidth) {
                this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_full)
            } else {
                this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_half)
            }
        }
        KeyCode.CHAR_WIDTH_FULL -> {
            this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_full)
        }
        KeyCode.CHAR_WIDTH_HALF -> {
            this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_half)
        }
        KeyCode.DRAG_MARKER -> {
            if (evaluator.state.debugShowDragAndDropHelpers) Icons.Rounded.Close else null
        }
        KeyCode.NOOP -> {
            Icons.Rounded.Close
        }
        else -> null
    }
}
