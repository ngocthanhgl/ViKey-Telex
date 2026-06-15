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

package dev.ngocthanhgl.vikey.app.settings.dictionary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.ime.dictionary.DictionaryManager
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import org.florisboard.lib.compose.stringRes
import java.io.File

@Composable
fun DictionaryScreen() = FlorisScreen {
    title = stringRes(R.string.settings__dictionary__title)
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    content {
        SwitchPreference(
            prefs.dictionary.enableSystemUserDictionary,
            title = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__summary),
        )
        Preference(
            title = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__summary),
            onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM)) },
            enabledIf = { prefs.dictionary.enableSystemUserDictionary isEqualTo true },
        )
        SwitchPreference(
            prefs.dictionary.enableFlorisUserDictionary,
            title = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__summary),
        )
        Preference(
            title = stringRes(R.string.pref__dictionary__manage_floris_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__manage_floris_user_dictionary__summary),
            onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS)) },
            enabledIf = { prefs.dictionary.enableFlorisUserDictionary isEqualTo true },
        )
        Preference(
            title = stringRes(R.string.pref__dictionary__clear_learned_words__label),
            summary = stringRes(R.string.pref__dictionary__clear_learned_words__summary),
            onClick = { showClearDialog = true },
        )
    }

    if (showClearDialog) {
        JetPrefAlertDialog(
            title = stringRes(R.string.pref__dictionary__clear_learned_words__confirm_title),
            confirmLabel = stringRes(R.string.action__delete),
            onConfirm = {
                showClearDialog = false
                File(context.filesDir, "personal_dict.json").delete()
                try {
                    DictionaryManager.default().florisUserDictionaryDao()?.deleteAll()
                } catch (_: Exception) {}
            },
            dismissLabel = stringRes(R.string.action__cancel),
            onDismiss = { showClearDialog = false },
        ) {
            androidx.compose.material3.Text(
                text = "This will remove all KenLM learned words. This action cannot be undone."
            )
        }
    }
}
