package dev.ngocthanhgl.vikey.app.settings.keyboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.input.HapticVibrationMode
import dev.ngocthanhgl.vikey.ime.input.InputFeedbackActivationMode
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.systemVibratorOrNull
import org.florisboard.lib.android.vibrate
import org.florisboard.lib.compose.stringRes

@Composable
fun InputFeedbackScreen() {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val vibrator = context.systemVibratorOrNull()

    SettingsScaffold(title = stringRes(R.string.settings__input_feedback__title)) {
        val scope = rememberCoroutineScope()
        val audioEnabled by prefs.inputFeedback.audioEnabled.collectAsState()
        val hapticEnabled by prefs.inputFeedback.hapticEnabled.collectAsState()
        val hapticVibrationMode by prefs.inputFeedback.hapticVibrationMode.collectAsState()
        val audioActivationMode by prefs.inputFeedback.audioActivationMode.collectAsState()
        val audioVolume by prefs.inputFeedback.audioVolume.collectAsState()
        val audioFeatKeyPress by prefs.inputFeedback.audioFeatKeyPress.collectAsState()
        val audioFeatKeyLongPress by prefs.inputFeedback.audioFeatKeyLongPress.collectAsState()
        val audioFeatKeyRepeatedAction by prefs.inputFeedback.audioFeatKeyRepeatedAction.collectAsState()
        val audioFeatGestureSwipe by prefs.inputFeedback.audioFeatGestureSwipe.collectAsState()
        val audioFeatGestureMovingSwipe by prefs.inputFeedback.audioFeatGestureMovingSwipe.collectAsState()
        val hapticActivationMode by prefs.inputFeedback.hapticActivationMode.collectAsState()
        val hapticVibrationDuration by prefs.inputFeedback.hapticVibrationDuration.collectAsState()
        val hapticVibrationStrength by prefs.inputFeedback.hapticVibrationStrength.collectAsState()
        val hapticFeatKeyPress by prefs.inputFeedback.hapticFeatKeyPress.collectAsState()
        val hapticFeatKeyLongPress by prefs.inputFeedback.hapticFeatKeyLongPress.collectAsState()
        val hapticFeatKeyRepeatedAction by prefs.inputFeedback.hapticFeatKeyRepeatedAction.collectAsState()
        val hapticFeatGestureSwipe by prefs.inputFeedback.hapticFeatGestureSwipe.collectAsState()
        val hapticFeatGestureMovingSwipe by prefs.inputFeedback.hapticFeatGestureMovingSwipe.collectAsState()
        val hasVibrator = vibrator != null && vibrator.hasVibrator()
        val hasAmplitudeControl = vibrator != null && vibrator.hasAmplitudeControl()

        Text(
            text = stringRes(R.string.pref__input_feedback__group_audio__label),
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
            M3SwitchListPreference(
                icon = Icons.Outlined.VolumeUp,
                switchChecked = audioEnabled,
                onSwitchChange = { scope.launch { prefs.inputFeedback.audioEnabled.set(it) } },
                listValue = audioActivationMode,
                onListSelect = { scope.launch { prefs.inputFeedback.audioActivationMode.set(InputFeedbackActivationMode.valueOf(it)) } },
                title = stringRes(R.string.pref__input_feedback__audio_enabled__label),
                summarySwitchDisabled = stringRes(R.string.pref__input_feedback__audio_enabled__summary_disabled),
                entries = enumDisplayEntriesOf(InputFeedbackActivationMode::class, "audio").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Outlined.VolumeDown,
                value = audioVolume,
                onChange = { scope.launch { prefs.inputFeedback.audioVolume.set(it) } },
                title = stringRes(R.string.pref__input_feedback__audio_volume__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 1, max = 100, stepIncrement = 1,
                enabled = audioEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = audioFeatKeyPress,
                onCheckedChange = { scope.launch { prefs.inputFeedback.audioFeatKeyPress.set(it) } },
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
                enabled = audioEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = audioFeatKeyLongPress,
                onCheckedChange = { scope.launch { prefs.inputFeedback.audioFeatKeyLongPress.set(it) } },
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_long_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
                enabled = audioEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = audioFeatKeyRepeatedAction,
                onCheckedChange = { scope.launch { prefs.inputFeedback.audioFeatKeyRepeatedAction.set(it) } },
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_repeated_action__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
                enabled = audioEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.KeyboardArrowRight,
                checked = audioFeatGestureSwipe,
                onCheckedChange = { scope.launch { prefs.inputFeedback.audioFeatGestureSwipe.set(it) } },
                title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_swipe__summary),
                enabled = audioEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.KeyboardArrowRight,
                checked = audioFeatGestureMovingSwipe,
                onCheckedChange = { scope.launch { prefs.inputFeedback.audioFeatGestureMovingSwipe.set(it) } },
                title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
                enabled = audioEnabled,
            )
        }

        Text(
            text = stringRes(R.string.pref__input_feedback__group_haptic__label),
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
            M3SwitchListPreference(
                icon = Icons.Outlined.PanTool,
                switchChecked = hapticEnabled,
                onSwitchChange = { scope.launch { prefs.inputFeedback.hapticEnabled.set(it) } },
                listValue = hapticActivationMode,
                onListSelect = { scope.launch { prefs.inputFeedback.hapticActivationMode.set(InputFeedbackActivationMode.valueOf(it)) } },
                title = stringRes(R.string.pref__input_feedback__haptic_enabled__label),
                summarySwitchDisabled = stringRes(R.string.pref__input_feedback__haptic_enabled__summary_disabled),
                entries = enumDisplayEntriesOf(InputFeedbackActivationMode::class, "haptic").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Outlined.Settings,
                value = hapticVibrationMode,
                onSelect = { scope.launch { prefs.inputFeedback.hapticVibrationMode.set(HapticVibrationMode.valueOf(it)) } },
                title = stringRes(R.string.pref__input_feedback__haptic_vibration_mode__label),
                entries = enumDisplayEntriesOf(HapticVibrationMode::class).map { it.key.toString() to it.label },
                enabled = hapticEnabled,
            )
            if (hapticEnabled && hapticVibrationMode == HapticVibrationMode.USE_VIBRATOR_DIRECTLY && hasVibrator) {
                SettingsDivider()
                M3DialogSliderPreference(
                    icon = Icons.Outlined.Timer,
                    value = hapticVibrationDuration,
                    onChange = { scope.launch { prefs.inputFeedback.hapticVibrationDuration.set(it) } },
                    title = stringRes(R.string.pref__input_feedback__haptic_vibration_duration__label),
                    valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                    min = 1, max = 100, stepIncrement = 1,
                )
            }
            if (hapticEnabled && hapticVibrationMode == HapticVibrationMode.USE_VIBRATOR_DIRECTLY && hasVibrator && hasAmplitudeControl) {
                SettingsDivider()
                M3DialogSliderPreference(
                    icon = Icons.Outlined.Star,
                    value = hapticVibrationStrength,
                    onChange = { scope.launch { prefs.inputFeedback.hapticVibrationStrength.set(it) } },
                    title = stringRes(R.string.pref__input_feedback__haptic_vibration_strength__label),
                    valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                    min = 1, max = 100, stepIncrement = 1,
                )
            }
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = hapticFeatKeyPress,
                onCheckedChange = { scope.launch { prefs.inputFeedback.hapticFeatKeyPress.set(it) } },
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
                enabled = hapticEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = hapticFeatKeyLongPress,
                onCheckedChange = { scope.launch { prefs.inputFeedback.hapticFeatKeyLongPress.set(it) } },
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_long_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
                enabled = hapticEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.Keyboard,
                checked = hapticFeatKeyRepeatedAction,
                onCheckedChange = { scope.launch { prefs.inputFeedback.hapticFeatKeyRepeatedAction.set(it) } },
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_repeated_action__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
                enabled = hapticEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.KeyboardArrowRight,
                checked = hapticFeatGestureSwipe,
                onCheckedChange = { scope.launch { prefs.inputFeedback.hapticFeatGestureSwipe.set(it) } },
                title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_swipe__summary),
                enabled = hapticEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Outlined.KeyboardArrowRight,
                checked = hapticFeatGestureMovingSwipe,
                onCheckedChange = { scope.launch { prefs.inputFeedback.hapticFeatGestureMovingSwipe.set(it) } },
                title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_moving_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
                enabled = hapticEnabled,
            )
        }
    }
}
