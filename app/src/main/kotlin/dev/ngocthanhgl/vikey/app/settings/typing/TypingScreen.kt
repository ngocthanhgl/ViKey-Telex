package dev.ngocthanhgl.vikey.app.settings.typing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import dev.ngocthanhgl.vikey.ime.keyboard.IncognitoMode
import dev.ngocthanhgl.vikey.ime.nlp.SpellingLanguageMode
import dev.ngocthanhgl.vikey.lib.compose.FlorisHyperlinkText
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun TypingScreen() {
    val navController = LocalNavController.current
    val prefs by FlorisPreferenceStore

    SettingsScaffold(title = stringRes(R.string.settings__typing__title)) {
        val scope = rememberCoroutineScope()
        val autoSpacePunctuationEnabled by prefs.correction.autoSpacePunctuation.collectAsState()
        val autoCapitalization by prefs.correction.autoCapitalization.collectAsState()
        val rememberCapsLockState by prefs.correction.rememberCapsLockState.collectAsState()
        val doubleSpacePeriod by prefs.correction.doubleSpacePeriod.collectAsState()
        val autoCorrect by prefs.correction.autoCorrect.collectAsState()
        val languageMode by prefs.spelling.languageMode.collectAsState()
        val useContacts by prefs.spelling.useContacts.collectAsState()
        val useUdmEntries by prefs.spelling.useUdmEntries.collectAsState()

        Text(
            text = stringRes(R.string.pref__correction__title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
        )
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
                icon = Icons.Outlined.TextFormat,
                checked = autoCapitalization,
                onCheckedChange = { scope.launch { prefs.correction.autoCapitalization.set(it) } },
                title = stringRes(R.string.pref__correction__auto_capitalization__label),
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.FormatAlignLeft,
                checked = autoSpacePunctuationEnabled,
                onCheckedChange = { scope.launch { prefs.correction.autoSpacePunctuation.set(it) } },
                title = stringRes(R.string.pref__correction__auto_space_punctuation__label),
            )
            if (autoSpacePunctuationEnabled) {
                SettingsDivider()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = """
                                Auto-space after punctuation is an experimental feature which may break or behave
                                unexpectedly. If you want, please give feedback about it in below linked feedback
                                thread. This helps a lot in improving this feature. Thanks!
                            """.trimIndent().replace('\n', ' '),
                        )
                        FlorisHyperlinkText(
                            text = "Feedback thread (GitHub)",
                            url = "https://github.com/florisboard/florisboard/discussions/1935",
                        )
                    }
                }
            }
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Lock,
                checked = rememberCapsLockState,
                onCheckedChange = { scope.launch { prefs.correction.rememberCapsLockState.set(it) } },
                title = stringRes(R.string.pref__correction__remember_caps_lock_state__label),
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.FormatAlignLeft,
                checked = doubleSpacePeriod,
                onCheckedChange = { scope.launch { prefs.correction.doubleSpacePeriod.set(it) } },
                title = stringRes(R.string.pref__correction__double_space_period__label),
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Spellcheck,
                checked = autoCorrect,
                onCheckedChange = { scope.launch { prefs.correction.autoCorrect.set(it) } },
                title = stringRes(R.string.pref__correction__auto_correct__label),
            )
        }

        Text(
            text = stringRes(R.string.pref__spelling__title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
        )
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
            val florisSpellCheckerEnabled = remember { mutableStateOf(false) }
            SpellCheckerServiceSelector(florisSpellCheckerEnabled)
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Outlined.Language,
                value = languageMode,
                onSelect = { scope.launch { prefs.spelling.languageMode.set(SpellingLanguageMode.valueOf(it)) } },
                title = stringRes(R.string.pref__spelling__language_mode__label),
                entries = enumDisplayEntriesOf(SpellingLanguageMode::class).map { it.key.toString() to it.label },
                enabled = florisSpellCheckerEnabled.value,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Contacts,
                checked = useContacts,
                onCheckedChange = { scope.launch { prefs.spelling.useContacts.set(it) } },
                title = stringRes(R.string.pref__spelling__use_contacts__label),
                enabled = florisSpellCheckerEnabled.value,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Bookmark,
                checked = useUdmEntries,
                onCheckedChange = { scope.launch { prefs.spelling.useUdmEntries.set(it) } },
                title = stringRes(R.string.pref__spelling__use_udm_entries__label),
                enabled = florisSpellCheckerEnabled.value,
            )
        }

        Text(
            text = stringRes(R.string.settings__dictionary__title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
        )
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
            M3ClickablePreference(
                icon = Icons.Outlined.MenuBook,
                title = stringRes(R.string.settings__dictionary__title),
                onClick = { navController.navigate(Routes.Settings.Dictionary) },
            )
        }
    }
}
