package dev.ngocthanhgl.vikey.app.settings.localization

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.core.DisplayLanguageNamesIn
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.keyboard.LayoutType
import dev.ngocthanhgl.vikey.keyboardManager
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.ngocthanhgl.vikey.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.serialization.json.Json
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

internal val SubtypeSaver = Saver<MutableState<Subtype?>, String>(
    save = {
        Json.encodeToString<Subtype?>(it.value)
    },
    restore = {
        mutableStateOf(Json.decodeFromString(it))
    },
)

@Composable
fun LocalizationScreen() = FlorisScreen {
    title = stringRes(R.string.settings__localization__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    var chosenSubtypeToDelete: Subtype? by rememberSaveable(saver = SubtypeSaver) { mutableStateOf(null) }

    floatingActionButton {
        ExtendedFloatingActionButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            text = {
                Text(
                    text = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            shape = FloatingActionButtonDefaults.extendedFabShape,
            onClick = { navController.navigate(Routes.Settings.SubtypeAdd) },
        )
    }

    content {
        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
        val displayKeyboardLabelsInSubtypeLanguage by prefs.localization.displayKeyboardLabelsInSubtypeLanguage.collectAsState()

        M3ListPreference(
            value = displayLanguageNamesIn,
            onSelect = { prefs.localization.displayLanguageNamesIn.set(it) },
            title = stringRes(R.string.settings__localization__display_language_names_in__label),
            entries = enumDisplayEntriesOf(DisplayLanguageNamesIn::class).map { it.key.toString() to it.label },
        )
        M3SwitchPreference(
            checked = displayKeyboardLabelsInSubtypeLanguage,
            onCheckedChange = { prefs.localization.displayKeyboardLabelsInSubtypeLanguage.set(it) },
            title = stringRes(R.string.settings__localization__display_keyboard_labels_in_subtype_language),
        )
        M3ClickablePreference(
            title = stringRes(R.string.settings__localization__language_pack_title),
            summary = stringRes(R.string.settings__localization__language_pack_summary),
            onClick = {
                navController.navigate(Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE))
            },
        )

        Text(
            text = stringRes(R.string.settings__localization__group_subtypes__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val subtypes by subtypeManager.subtypesFlow.collectAsState()
        if (subtypes.isEmpty()) {
            FlorisWarningCard(
                modifier = Modifier.padding(all = 8.dp),
                text = stringRes(R.string.settings__localization__subtype_no_subtypes_configured_warning),
            )
        } else {
            val currencySets by keyboardManager.resources.currencySets.collectAsState()
            val layouts by keyboardManager.resources.layouts.collectAsState()
            val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
            for (subtype in subtypes) {
                val cMeta = layouts[LayoutType.CHARACTERS]?.get(subtype.layoutMap.characters)
                val sMeta = layouts[LayoutType.SYMBOLS]?.get(subtype.layoutMap.symbols)
                val currMeta = currencySets[subtype.currencySet]
                val summary = stringRes(
                    id = R.string.settings__localization__subtype_summary,
                    "characters_name" to (cMeta?.label ?: "null"),
                    "symbols_name" to (sMeta?.label ?: "null"),
                    "currency_set_name" to (currMeta?.label ?: "null"),
                )
                M3ClickablePreference(
                    title = when (displayLanguageNamesIn) {
                        DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtype.primaryLocale.displayName()
                        DisplayLanguageNamesIn.NATIVE_LOCALE -> subtype.primaryLocale.displayName(subtype.primaryLocale)
                    },
                    summary = summary,
                    onClick = {
                        navController.navigate(Routes.Settings.SubtypeEdit(subtype.id))
                    },
                    onLongClick = {
                        chosenSubtypeToDelete = subtype
                    },
                )
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
