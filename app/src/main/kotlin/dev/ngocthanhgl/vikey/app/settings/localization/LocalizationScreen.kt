package dev.ngocthanhgl.vikey.app.settings.localization

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.core.DisplayLanguageNamesIn
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.keyboardManager
import dev.ngocthanhgl.vikey.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalizationScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    val prefs by FlorisPreferenceStore
    var chosenSubtypeToDelete: Subtype? by rememberSaveable(saver = SubtypeSaver) { mutableStateOf(null) }

    SettingsScaffold(
        title = stringRes(R.string.settings__localization__title),
        fab = {
            ExtendedFloatingActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringRes(R.string.settings__localization__subtype_add_title),
                    )
                },
                text = {
                    Text(stringRes(R.string.settings__localization__subtype_add_title))
                },
                shape = FloatingActionButtonDefaults.extendedFabShape,
                onClick = { navController.navigate(Routes.Settings.SubtypeAdd) },
            )
        },
    ) {
        val scope = rememberCoroutineScope()
        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
        val displayKeyboardLabelsInSubtypeLanguage by prefs.localization.displayKeyboardLabelsInSubtypeLanguage.collectAsState()

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
            M3ListPreference(
                icon = Icons.Outlined.Language,
                value = displayLanguageNamesIn,
                onSelect = { scope.launch { prefs.localization.displayLanguageNamesIn.set(DisplayLanguageNamesIn.valueOf(it)) } },
                title = stringRes(R.string.settings__localization__display_language_names_in__label),
                entries = enumDisplayEntriesOf(DisplayLanguageNamesIn::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = displayKeyboardLabelsInSubtypeLanguage,
                onCheckedChange = { scope.launch { prefs.localization.displayKeyboardLabelsInSubtypeLanguage.set(it) } },
                title = stringRes(R.string.settings__localization__display_keyboard_labels_in_subtype_language),
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Outlined.Extension,
                title = stringRes(R.string.settings__localization__language_pack_title),
                onClick = {
                    navController.navigate(Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE))
                },
            )
        }

        Text(
            text = stringRes(R.string.settings__localization__group_subtypes__label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
        )
        val subtypes by subtypeManager.subtypesFlow.collectAsState()
        if (subtypes.isEmpty()) {
            FlorisWarningCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                text = stringRes(R.string.settings__localization__subtype_no_subtypes_configured_warning),
            )
        } else {
            val layouts by keyboardManager.resources.layouts.collectAsState()
            val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
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
                subtypes.forEachIndexed { index, subtype ->
                    M3ClickablePreference(
                        icon = Icons.Outlined.Language,
                        title = when (displayLanguageNamesIn) {
                            DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtype.primaryLocale.displayName()
                            DisplayLanguageNamesIn.NATIVE_LOCALE -> subtype.primaryLocale.displayName(subtype.primaryLocale)
                        },
                        onClick = {
                            navController.navigate(Routes.Settings.SubtypeEdit(subtype.id))
                        },
                        onLongClick = {
                            chosenSubtypeToDelete = subtype
                        },
                    )
                    if (index < subtypes.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }

    DeleteSubtypeConfirmationDialog(
        subtypeToDelete = chosenSubtypeToDelete,
        onDismiss = { chosenSubtypeToDelete = null },
        onConfirm = {
            chosenSubtypeToDelete?.let { subtypeManager.removeSubtype(subtypeToRemove = it) }
            chosenSubtypeToDelete = null
        },
    )
}

private val SubtypeSaver = Saver<MutableState<Subtype?>, String>(
    save = {
        Json.encodeToString<Subtype?>(it.value)
    },
    restore = {
        mutableStateOf(Json.decodeFromString(it))
    },
)

@Composable
private fun DeleteSubtypeConfirmationDialog(
    subtypeToDelete: Subtype?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    subtypeToDelete?.let {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringRes(R.string.settings__localization__subtype_delete_confirmation_title)) },
            text = { Text(stringRes(R.string.settings__localization__subtype_delete_confirmation_warning)) },
            confirmButton = {
                Button(onClick = onConfirm) { Text(stringRes(R.string.action__yes)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__no)) }
            },
        )
    }
}
