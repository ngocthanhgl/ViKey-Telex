package dev.ngocthanhgl.vikey.app.settings.typing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.keyboard.IncognitoMode
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.ngocthanhgl.vikey.ime.nlp.SpellingLanguageMode
import dev.ngocthanhgl.vikey.lib.compose.FlorisHyperlinkText
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.stringRes

@Composable
fun TypingScreen() = FlorisScreen {
    title = stringRes(R.string.settings__typing__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        val autoSpacePunctuationEnabled by prefs.correction.autoSpacePunctuation.collectAsState()

        FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = """
                Spell checking is not available in this release. All
                preferences in the "Corrections" group are properly implemented though.
            """.trimIndent().replace('\n', ' '),
        )

        Text(
            text = stringRes(R.string.pref__correction__title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3SwitchPreference(
            pref = prefs.correction.autoCapitalization,
            title = stringRes(R.string.pref__correction__auto_capitalization__label),
            summary = stringRes(R.string.pref__correction__auto_capitalization__summary),
        )
        M3SwitchPreference(
            pref = prefs.correction.autoSpacePunctuation,
            title = stringRes(R.string.pref__correction__auto_space_punctuation__label),
            summary = stringRes(R.string.pref__correction__auto_space_punctuation__summary),
        )
        if (autoSpacePunctuationEnabled) {
            Card(modifier = Modifier.padding(8.dp)) {
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
        M3SwitchPreference(
            pref = prefs.correction.rememberCapsLockState,
            title = stringRes(R.string.pref__correction__remember_caps_lock_state__label),
            summary = stringRes(R.string.pref__correction__remember_caps_lock_state__summary),
        )
        M3SwitchPreference(
            pref = prefs.correction.doubleSpacePeriod,
            title = stringRes(R.string.pref__correction__double_space_period__label),
            summary = stringRes(R.string.pref__correction__double_space_period__summary),
        )
        M3SwitchPreference(
            pref = prefs.correction.autoCorrect,
            title = stringRes(R.string.pref__correction__auto_correct__label),
            summary = stringRes(R.string.pref__correction__auto_correct__summary),
        )

        Text(
            text = stringRes(R.string.pref__spelling__title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val florisSpellCheckerEnabled = remember { mutableStateOf(false) }
        SpellCheckerServiceSelector(florisSpellCheckerEnabled)
        M3ListPreference(
            pref = prefs.spelling.languageMode,
            title = stringRes(R.string.pref__spelling__language_mode__label),
            entries = enumDisplayEntriesOf(SpellingLanguageMode::class),
            enabled = florisSpellCheckerEnabled.value,
        )
        M3SwitchPreference(
            pref = prefs.spelling.useContacts,
            title = stringRes(R.string.pref__spelling__use_contacts__label),
            summary = stringRes(R.string.pref__spelling__use_contacts__summary),
            enabled = florisSpellCheckerEnabled.value,
        )
        M3SwitchPreference(
            pref = prefs.spelling.useUdmEntries,
            title = stringRes(R.string.pref__spelling__use_udm_entries__label),
            summary = stringRes(R.string.pref__spelling__use_udm_entries__summary),
            enabled = florisSpellCheckerEnabled.value,
        )

        Text(
            text = stringRes(R.string.settings__dictionary__title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ClickablePreference(
            title = stringRes(R.string.settings__dictionary__title),
            onClick = { navController.navigate(Routes.Settings.Dictionary) },
        )
    }
}
