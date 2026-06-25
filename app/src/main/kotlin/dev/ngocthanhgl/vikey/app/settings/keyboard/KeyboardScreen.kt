package dev.ngocthanhgl.vikey.app.settings.keyboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.input.CapitalizationBehavior
import dev.ngocthanhgl.vikey.ime.keyboard.SpaceBarMode
import dev.ngocthanhgl.vikey.ime.landscapeinput.LandscapeInputUiMode
import dev.ngocthanhgl.vikey.ime.smartbar.IncognitoDisplayMode
import dev.ngocthanhgl.vikey.ime.text.key.KeyHintMode
import dev.ngocthanhgl.vikey.ime.text.key.UtilityKeyAction
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun KeyboardScreen() {
    val navController = LocalNavController.current
    val prefs by FlorisPreferenceStore

    SettingsScaffold(title = stringRes(R.string.settings__keyboard__title)) {
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
                icon = Icons.Outlined.Dialpad,
                checked = numberRow,
                onCheckedChange = { scope.launch { prefs.keyboard.numberRow.set(it) } },
                title = stringRes(R.string.pref__keyboard__number_row__label),
            )
            SettingsDivider()
            M3SwitchListPreference(
                icon = Icons.Outlined.Dialpad,
                switchChecked = hintedNumberRowEnabled,
                onSwitchChange = { scope.launch { prefs.keyboard.hintedNumberRowEnabled.set(it) } },
                listValue = hintedNumberRowMode,
                onListSelect = { scope.launch { prefs.keyboard.hintedNumberRowMode.set(KeyHintMode.valueOf(it)) } },
                title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
                summarySwitchDisabled = stringRes(R.string.state__disabled),
                entries = enumDisplayEntriesOf(KeyHintMode::class).map { it.key.toString() to it.label },
                enabled = !numberRow,
            )
            SettingsDivider()
            M3SwitchListPreference(
                icon = Icons.Outlined.AlternateEmail,
                switchChecked = hintedSymbolsEnabled,
                onSwitchChange = { scope.launch { prefs.keyboard.hintedSymbolsEnabled.set(it) } },
                listValue = hintedSymbolsMode,
                onListSelect = { scope.launch { prefs.keyboard.hintedSymbolsMode.set(KeyHintMode.valueOf(it)) } },
                title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
                summarySwitchDisabled = stringRes(R.string.state__disabled),
                entries = enumDisplayEntriesOf(KeyHintMode::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = utilityKeyEnabled,
                onCheckedChange = { scope.launch { prefs.keyboard.utilityKeyEnabled.set(it) } },
                title = stringRes(R.string.pref__keyboard__utility_key_enabled__label),
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Outlined.Settings,
                value = utilityKeyAction,
                onSelect = { scope.launch { prefs.keyboard.utilityKeyAction.set(UtilityKeyAction.valueOf(it)) } },
                title = stringRes(R.string.pref__keyboard__utility_key_action__label),
                entries = enumDisplayEntriesOf(UtilityKeyAction::class).map { it.key.toString() to it.label },
                enabled = utilityKeyEnabled,
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Outlined.Keyboard,
                value = spaceBarMode,
                onSelect = { scope.launch { prefs.keyboard.spaceBarMode.set(SpaceBarMode.valueOf(it)) } },
                title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
                entries = enumDisplayEntriesOf(SpaceBarMode::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Outlined.TextFormat,
                value = capitalizationBehavior,
                onSelect = { scope.launch { prefs.keyboard.capitalizationBehavior.set(CapitalizationBehavior.valueOf(it)) } },
                title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
                entries = enumDisplayEntriesOf(CapitalizationBehavior::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Outlined.FormatSize,
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
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Outlined.VisibilityOff,
                value = incognitoDisplayMode,
                onSelect = { scope.launch { prefs.keyboard.incognitoDisplayMode.set(IncognitoDisplayMode.valueOf(it)) } },
                title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
                entries = enumDisplayEntriesOf(IncognitoDisplayMode::class).map { it.key.toString() to it.label },
            )
        }

        Text(
            text = stringRes(R.string.pref__keyboard__group_layout__label),
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
            M3ListPreference(
                icon = Icons.Outlined.ScreenRotation,
                value = landscapeInputUiMode,
                onSelect = { scope.launch { prefs.keyboard.landscapeInputUiMode.set(LandscapeInputUiMode.valueOf(it)) } },
                title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
                entries = enumDisplayEntriesOf(LandscapeInputUiMode::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Outlined.ViewColumn,
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
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Outlined.VerticalAlignBottom,
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
        }

        Text(
            text = stringRes(R.string.pref__keyboard__group_keypress__label),
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
                icon = Icons.Outlined.PanTool,
                title = stringRes(R.string.settings__input_feedback__title),
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.OpenInNew,
                checked = popupEnabled,
                onCheckedChange = { scope.launch { prefs.keyboard.popupEnabled.set(it) } },
                title = stringRes(R.string.pref__keyboard__popup_enabled__label),
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.CallMerge,
                checked = mergeHintPopupsEnabled,
                onCheckedChange = { scope.launch { prefs.keyboard.mergeHintPopupsEnabled.set(it) } },
                title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Outlined.Timer,
                value = longPressDelay,
                onChange = { scope.launch { prefs.keyboard.longPressDelay.set(it) } },
                title = stringRes(R.string.pref__keyboard__long_press_delay__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 100, max = 700, stepIncrement = 10,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.SwapHoriz,
                checked = spaceBarSwitchesToCharacters,
                onCheckedChange = { scope.launch { prefs.keyboard.spaceBarSwitchesToCharacters.set(it) } },
                title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
            )
        }

        Text(
            text = "Vietnamese Telex",
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
            val telexWEnabled by prefs.keyboard.telexWEnabled.collectAsState()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = telexWEnabled,
                onCheckedChange = { scope.launch { prefs.keyboard.telexWEnabled.set(it) } },
                title = "w = ư",
            )
            val englishFallbackEnabled by prefs.keyboard.englishFallbackEnabled.collectAsState()
            M3SwitchPreference(
                icon = Icons.Outlined.Language,
                checked = englishFallbackEnabled,
                onCheckedChange = { scope.launch { prefs.keyboard.englishFallbackEnabled.set(it) } },
                title = "English fallback",
            )
        }
    }
}
