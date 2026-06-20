package dev.ngocthanhgl.vikey.app.settings.dictionary
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.dictionary.DictionaryManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.ngocthanhgl.vikey.ime.nlp.vietnamese.QwenSuggestionProvider
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
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
        val scope = rememberCoroutineScope()
        val enableSystemUserDictionary by prefs.dictionary.enableSystemUserDictionary.collectAsState()
        val enableFlorisUserDictionary by prefs.dictionary.enableFlorisUserDictionary.collectAsState()

        M3SwitchPreference(
            checked = enableSystemUserDictionary,
            onCheckedChange = { scope.launch { prefs.dictionary.enableSystemUserDictionary.set(it) } },
            title = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__summary),
        )
        M3ClickablePreference(
            title = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__summary),
            onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM)) },
            enabled = enableSystemUserDictionary,
        )
        M3SwitchPreference(
            checked = enableFlorisUserDictionary,
            onCheckedChange = { scope.launch { prefs.dictionary.enableFlorisUserDictionary.set(it) } },
            title = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__summary),
        )
        M3ClickablePreference(
            title = stringRes(R.string.pref__dictionary__manage_floris_user_dictionary__label),
            summary = stringRes(R.string.pref__dictionary__manage_floris_user_dictionary__summary),
            onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS)) },
            enabled = enableFlorisUserDictionary,
        )
        M3ClickablePreference(
            title = stringRes(R.string.pref__dictionary__clear_learned_words__label),
            summary = stringRes(R.string.pref__dictionary__clear_learned_words__summary),
            onClick = { showClearDialog = true },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringRes(R.string.pref__dictionary__clear_learned_words__confirm_title)) },
            text = {
                Text(text = stringRes(R.string.pref__dictionary__clear_learned_words__confirm_body))
            },
            confirmButton = {
                Button(onClick = {
                    showClearDialog = false
                    File(context.filesDir, ".cleared").writeText("1")
                    File(context.filesDir, "personal_dict.json").delete()
                    File(context.filesDir, ".qwen_cleared").writeText("1")
                    File(context.filesDir, "qwen_personal_dict.json").delete()
                    QwenSuggestionProvider.getInstance()?.clearAll()
                    try {
                        DictionaryManager.default().florisUserDictionaryDao()?.deleteAll()
                    } catch (_: Exception) {}
                }) {
                    Text(stringRes(R.string.action__delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringRes(R.string.action__cancel))
                }
            },
        )
    }
}
