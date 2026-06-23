/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.ngocthanhgl.vikey.app.settings.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.PreferenceLayout
import dev.patrickgold.jetpref.material.ui.ColorRepresentation
import org.florisboard.lib.compose.stringRes

@Composable
fun FineTuneDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.settings__theme_editor__fine_tune__title)) },
        text = {
            PreferenceLayout(FlorisPreferenceStore, iconSpaceReserved = false) {
                ListPreference(
                    listPref = prefs.theme.editorLevel,
                    title = stringRes(R.string.settings__theme_editor__fine_tune__level),
                    entries = enumDisplayEntriesOf(SnyggLevel::class),
                )
                ListPreference(
                    listPref = prefs.theme.editorColorRepresentation,
                    title = stringRes(R.string.settings__theme_editor__fine_tune__color_representation),
                    entries = enumDisplayEntriesOf(ColorRepresentation::class),
                )
                ListPreference(
                    listPref = prefs.theme.editorDisplayKbdAfterDialogs,
                    title = stringRes(R.string.settings__theme_editor__fine_tune__display_kbd_after_dialogs),
                    entries = enumDisplayEntriesOf(DisplayKbdAfterDialogs::class),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(android.R.string.ok))
            }
        },
    )
}
