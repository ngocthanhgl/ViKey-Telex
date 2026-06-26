package dev.ngocthanhgl.vikey.app.settings.dictionary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.dictionary.DictionaryManager
import dev.ngocthanhgl.vikey.ime.nlp.vietnamese.QwenSuggestionProvider
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import java.io.File

@Composable
fun DictionaryScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    var showClearDialog by remember { mutableStateOf(false) }

    SettingsScaffold(title = stringRes(R.string.settings__dictionary__title)) {
        val scope = rememberCoroutineScope()
        val enableSystemUserDictionary by prefs.dictionary.enableSystemUserDictionary.collectAsState()
        val enableFlorisUserDictionary by prefs.dictionary.enableFlorisUserDictionary.collectAsState()

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            M3SwitchPreference(
                icon = Icons.Rounded.LibraryBooks,
                checked = enableSystemUserDictionary,
                onCheckedChange = { scope.launch { prefs.dictionary.enableSystemUserDictionary.set(it) } },
                title = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__label),
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Rounded.Edit,
                title = stringRes(R.string.pref__dictionary__manage_system_user_dictionary__label),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM)) },
                enabled = enableSystemUserDictionary,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Rounded.Book,
                checked = enableFlorisUserDictionary,
                onCheckedChange = { scope.launch { prefs.dictionary.enableFlorisUserDictionary.set(it) } },
                title = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__label),
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Rounded.Edit,
                title = stringRes(R.string.pref__dictionary__manage_floris_user_dictionary__label),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS)) },
                enabled = enableFlorisUserDictionary,
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Rounded.Delete,
                title = stringRes(R.string.pref__dictionary__clear_learned_words__label),
                onClick = { showClearDialog = true },
            )
        }
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
