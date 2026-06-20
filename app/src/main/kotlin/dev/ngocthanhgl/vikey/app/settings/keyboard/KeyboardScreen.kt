package dev.ngocthanhgl.vikey.app.settings.keyboard
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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
        val scope = rememberCoroutineScope()
        val numberRow by prefs.keyboard.numberRow.collectAsState()
        val utilityKeyEnabled by prefs.keyboard.utilityKeyEnabled.collectAsState()
        val hintedNumberRowEnabled by prefs.keyboard.hintedNumberRowEnabled.collectAsState()
        val hintedNumberRowMode by prefs.keyboard.hintedNumberRowMode.collectAsState()
        val hintedSymbolsEnabled by prefs.keyboard.hintedSymbolsEnabled.collectAsState()
        val hintedSymbolsMode by prefs.keyboard.hintedSymbolsMode.collectAsState()
        val utilityKeyAction by prefs.keyboard.utilityKeyAction.collectAsState()
        val spaceBarMode by prefs.keyboard.spaceBarMode.collectAsState()
        val capitalizationBehavior by prefs.keyboard.capitalizationBehavior.collectAsState()
        val fontSizeMultiplierPortrait by prefs.keyboard.fontSizeMultiplierPortrait.collectAsState()
        val fontSizeMultiplierLandscape by prefs.keyboard.fontSizeMultiplierLandscape.collectAsState()
        val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.collectAsState()
        val landscapeInputUiMode by prefs.keyboard.landscapeInputUiMode.collectAsState()
        val keySpacingVertical by prefs.keyboard.keySpacingVertical.collectAsState()
        val keySpacingHorizontal by prefs.keyboard.keySpacingHorizontal.collectAsState()
        val bottomPaddingPortrait by prefs.keyboard.bottomPaddingPortrait.collectAsState()
        val bottomPaddingLandscape by prefs.keyboard.bottomPaddingLandscape.collectAsState()
        val popupEnabled by prefs.keyboard.popupEnabled.collectAsState()
        val mergeHintPopupsEnabled by prefs.keyboard.mergeHintPopupsEnabled.collectAsState()
        val longPressDelay by prefs.keyboard.longPressDelay.collectAsState()
        val spaceBarSwitchesToCharacters by prefs.keyboard.spaceBarSwitchesToCharacters.collectAsState()

        M3SwitchPreference(
            checked = numberRow,
            onCheckedChange = { scope.launch { prefs.keyboard.numberRow.set(it) } },
            title = stringRes(R.string.pref__keyboard__number_row__label),
            summary = stringRes(R.string.pref__keyboard__number_row__summary),
        )
        M3SwitchListPreference(
            switchChecked = hintedNumberRowEnabled,
            onSwitchChange = { scope.launch { prefs.keyboard.hintedNumberRowEnabled.set(it) } },
            listValue = hintedNumberRowMode,
            onListSelect = { scope.launch { prefs.keyboard.hintedNumberRowMode.set(KeyHintMode.valueOf(it)) } },
            title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class).map { it.key.toString() to it.label },
            enabled = !numberRow,
        )
        M3SwitchListPreference(
            switchChecked = hintedSymbolsEnabled,
            onSwitchChange = { scope.launch { prefs.keyboard.hintedSymbolsEnabled.set(it) } },
            listValue = hintedSymbolsMode,
            onListSelect = { scope.launch { prefs.keyboard.hintedSymbolsMode.set(KeyHintMode.valueOf(it)) } },
            title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class).map { it.key.toString() to it.label },
        )
        M3SwitchPreference(
            checked = utilityKeyEnabled,
            onCheckedChange = { scope.launch { prefs.keyboard.utilityKeyEnabled.set(it) } },
            title = stringRes(R.string.pref__keyboard__utility_key_enabled__label),
            summary = stringRes(R.string.pref__keyboard__utility_key_enabled__summary),
        )
        M3ListPreference(
            value = utilityKeyAction,
            onSelect = { scope.launch { prefs.keyboard.utilityKeyAction.set(UtilityKeyAction.valueOf(it)) } },
            title = stringRes(R.string.pref__keyboard__utility_key_action__label),
            entries = enumDisplayEntriesOf(UtilityKeyAction::class).map { it.key.toString() to it.label },
            enabled = utilityKeyEnabled,
        )
        M3ListPreference(
            value = spaceBarMode,
            onSelect = { scope.launch { prefs.keyboard.spaceBarMode.set(SpaceBarMode.valueOf(it)) } },
            title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
            entries = enumDisplayEntriesOf(SpaceBarMode::class).map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = capitalizationBehavior,
            onSelect = { scope.launch { prefs.keyboard.capitalizationBehavior.set(CapitalizationBehavior.valueOf(it)) } },
            title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
            entries = enumDisplayEntriesOf(CapitalizationBehavior::class).map { it.key.toString() to it.label },
        )
        M3DialogSliderPreference(
            primaryValue = fontSizeMultiplierPortrait,
            onPrimaryChange = { scope.launch { prefs.keyboard.fontSizeMultiplierPortrait.set(it) } },
            secondaryValue = fontSizeMultiplierLandscape,
            onSecondaryChange = { scope.launch { prefs.keyboard.fontSizeMultiplierLandscape.set(it) } },
            title = stringRes(R.string.pref__keyboard__font_size_multiplier__label),
            primaryLabel = stringRes(R.string.screen_orientation__portrait),
            secondaryLabel = stringRes(R.string.screen_orientation__landscape),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50, max = 150, stepIncrement = 5,
        )
        M3ListPreference(
            value = incognitoDisplayMode,
            onSelect = { scope.launch { prefs.keyboard.incognitoDisplayMode.set(IncognitoDisplayMode.valueOf(it)) } },
            title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
            entries = enumDisplayEntriesOf(IncognitoDisplayMode::class).map { it.key.toString() to it.label },
        )

        Text(
            text = stringRes(R.string.pref__keyboard__group_layout__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ListPreference(
            value = landscapeInputUiMode,
            onSelect = { scope.launch { prefs.keyboard.landscapeInputUiMode.set(LandscapeInputUiMode.valueOf(it)) } },
            title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
            entries = enumDisplayEntriesOf(LandscapeInputUiMode::class).map { it.key.toString() to it.label },
        )
        M3DialogSliderPreference(
            primaryValue = keySpacingVertical,
            onPrimaryChange = { scope.launch { prefs.keyboard.keySpacingVertical.set(it) } },
            secondaryValue = keySpacingHorizontal,
            onSecondaryChange = { scope.launch { prefs.keyboard.keySpacingHorizontal.set(it) } },
            title = stringRes(R.string.pref__keyboard__key_spacing__label),
            primaryLabel = stringRes(R.string.screen_orientation__vertical),
            secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50, max = 150, stepIncrement = 5,
        )
        M3DialogSliderPreference(
            primaryValue = bottomPaddingPortrait,
            onPrimaryChange = { scope.launch { prefs.keyboard.bottomPaddingPortrait.set(it) } },
            secondaryValue = bottomPaddingLandscape,
            onSecondaryChange = { scope.launch { prefs.keyboard.bottomPaddingLandscape.set(it) } },
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
            checked = popupEnabled,
            onCheckedChange = { scope.launch { prefs.keyboard.popupEnabled.set(it) } },
            title = stringRes(R.string.pref__keyboard__popup_enabled__label),
            summary = stringRes(R.string.pref__keyboard__popup_enabled__summary),
        )
        M3SwitchPreference(
            checked = mergeHintPopupsEnabled,
            onCheckedChange = { scope.launch { prefs.keyboard.mergeHintPopupsEnabled.set(it) } },
            title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
            summary = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__summary),
        )
        M3DialogSliderPreference(
            value = longPressDelay,
            onChange = { scope.launch { prefs.keyboard.longPressDelay.set(it) } },
            title = stringRes(R.string.pref__keyboard__long_press_delay__label),
            valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
            min = 100, max = 700, stepIncrement = 10,
        )
        M3SwitchPreference(
            checked = spaceBarSwitchesToCharacters,
            onCheckedChange = { scope.launch { prefs.keyboard.spaceBarSwitchesToCharacters.set(it) } },
            title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
            summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
        )
    }
}
