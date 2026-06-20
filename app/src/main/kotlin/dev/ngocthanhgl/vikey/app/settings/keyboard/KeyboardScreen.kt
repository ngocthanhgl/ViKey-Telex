package dev.ngocthanhgl.vikey.app.settings.keyboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.input.CapitalizationBehavior
import dev.ngocthanhgl.vikey.ime.keyboard.SpaceBarMode
import dev.ngocthanhgl.vikey.ime.landscapeinput.LandscapeInputUiMode
import dev.ngocthanhgl.vikey.ime.smartbar.IncognitoDisplayMode
import dev.ngocthanhgl.vikey.ime.text.key.KeyHintMode
import dev.ngocthanhgl.vikey.ime.text.key.UtilityKeyAction
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes

@Composable
fun KeyboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        val numberRow by prefs.keyboard.numberRow.collectAsState()
        val utilityKeyEnabled by prefs.keyboard.utilityKeyEnabled.collectAsState()

        M3SwitchPreference(
            pref = prefs.keyboard.numberRow,
            title = stringRes(R.string.pref__keyboard__number_row__label),
            summary = stringRes(R.string.pref__keyboard__number_row__summary),
        )
        M3SwitchListPreference(
            switchPref = prefs.keyboard.hintedNumberRowEnabled,
            listPref = prefs.keyboard.hintedNumberRowMode,
            title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class),
            enabled = !numberRow,
        )
        M3SwitchListPreference(
            switchPref = prefs.keyboard.hintedSymbolsEnabled,
            listPref = prefs.keyboard.hintedSymbolsMode,
            title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class),
        )
        M3SwitchPreference(
            pref = prefs.keyboard.utilityKeyEnabled,
            title = stringRes(R.string.pref__keyboard__utility_key_enabled__label),
            summary = stringRes(R.string.pref__keyboard__utility_key_enabled__summary),
        )
        M3ListPreference(
            pref = prefs.keyboard.utilityKeyAction,
            title = stringRes(R.string.pref__keyboard__utility_key_action__label),
            entries = enumDisplayEntriesOf(UtilityKeyAction::class),
            enabled = utilityKeyEnabled,
        )
        M3ListPreference(
            pref = prefs.keyboard.spaceBarMode,
            title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
            entries = enumDisplayEntriesOf(SpaceBarMode::class),
        )
        M3ListPreference(
            pref = prefs.keyboard.capitalizationBehavior,
            title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
            entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
        )
        M3DialogSliderPreference(
            primaryPref = prefs.keyboard.fontSizeMultiplierPortrait,
            secondaryPref = prefs.keyboard.fontSizeMultiplierLandscape,
            title = stringRes(R.string.pref__keyboard__font_size_multiplier__label),
            primaryLabel = stringRes(R.string.screen_orientation__portrait),
            secondaryLabel = stringRes(R.string.screen_orientation__landscape),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50, max = 150, stepIncrement = 5,
        )
        M3ListPreference(
            pref = prefs.keyboard.incognitoDisplayMode,
            title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
            entries = enumDisplayEntriesOf(IncognitoDisplayMode::class),
        )

        Text(
            text = stringRes(R.string.pref__keyboard__group_layout__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ListPreference(
            pref = prefs.keyboard.landscapeInputUiMode,
            title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
            entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
        )
        M3DialogSliderPreference(
            primaryPref = prefs.keyboard.keySpacingVertical,
            secondaryPref = prefs.keyboard.keySpacingHorizontal,
            title = stringRes(R.string.pref__keyboard__key_spacing__label),
            primaryLabel = stringRes(R.string.screen_orientation__vertical),
            secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50, max = 150, stepIncrement = 5,
        )
        M3DialogSliderPreference(
            primaryPref = prefs.keyboard.bottomPaddingPortrait,
            secondaryPref = prefs.keyboard.bottomPaddingLandscape,
            title = stringRes(R.string.pref__keyboard__bottom_padding__label),
            primaryLabel = stringRes(R.string.screen_orientation__portrait),
            secondaryLabel = stringRes(R.string.screen_orientation__landscape),
            valueLabel = { v: Int -> "$v px" },
            min = 0, max = 200, stepIncrement = 5,
        )

        Text(
            text = stringRes(R.string.pref__keyboard__group_keypress__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ClickablePreference(
            title = stringRes(R.string.settings__input_feedback__title),
            onClick = { navController.navigate(Routes.Settings.InputFeedback) },
        )
        M3SwitchPreference(
            pref = prefs.keyboard.popupEnabled,
            title = stringRes(R.string.pref__keyboard__popup_enabled__label),
            summary = stringRes(R.string.pref__keyboard__popup_enabled__summary),
        )
        M3SwitchPreference(
            pref = prefs.keyboard.mergeHintPopupsEnabled,
            title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
            summary = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__summary),
        )
        M3DialogSliderPreference(
            pref = prefs.keyboard.longPressDelay,
            title = stringRes(R.string.pref__keyboard__long_press_delay__label),
            valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
            min = 100, max = 700, stepIncrement = 10,
        )
        M3SwitchPreference(
            pref = prefs.keyboard.spaceBarSwitchesToCharacters,
            title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
            summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
        )
    }
}
