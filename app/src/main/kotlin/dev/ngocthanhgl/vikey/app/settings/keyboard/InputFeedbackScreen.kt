package dev.ngocthanhgl.vikey.app.settings.keyboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.input.HapticVibrationMode
import dev.ngocthanhgl.vikey.ime.input.InputFeedbackActivationMode
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.android.systemVibratorOrNull
import org.florisboard.lib.android.vibrate
import org.florisboard.lib.compose.stringRes

@Composable
fun InputFeedbackScreen() = FlorisScreen {
    title = stringRes(R.string.settings__input_feedback__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val context = LocalContext.current
    val vibrator = context.systemVibratorOrNull()

    content {
        val audioEnabled by prefs.inputFeedback.audioEnabled.collectAsState()
        val hapticEnabled by prefs.inputFeedback.hapticEnabled.collectAsState()
        val hapticVibrationMode by prefs.inputFeedback.hapticVibrationMode.collectAsState()
        val hasVibrator = vibrator != null && vibrator.hasVibrator()
        val hasAmplitudeControl = vibrator != null && vibrator.hasAmplitudeControl()

        Text(
            text = stringRes(R.string.pref__input_feedback__group_audio__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3SwitchListPreference(
            switchPref = prefs.inputFeedback.audioEnabled,
            listPref = prefs.inputFeedback.audioActivationMode,
            title = stringRes(R.string.pref__input_feedback__audio_enabled__label),
            summarySwitchDisabled = stringRes(R.string.pref__input_feedback__audio_enabled__summary_disabled),
            entries = enumDisplayEntriesOf(InputFeedbackActivationMode::class, "audio"),
        )
        M3DialogSliderPreference(
            pref = prefs.inputFeedback.audioVolume,
            title = stringRes(R.string.pref__input_feedback__audio_volume__label),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 1, max = 100, stepIncrement = 1,
            enabled = audioEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.audioFeatKeyPress,
            title = stringRes(R.string.pref__input_feedback__audio_feat_key_press__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
            enabled = audioEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.audioFeatKeyLongPress,
            title = stringRes(R.string.pref__input_feedback__audio_feat_key_long_press__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
            enabled = audioEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.audioFeatKeyRepeatedAction,
            title = stringRes(R.string.pref__input_feedback__audio_feat_key_repeated_action__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
            enabled = audioEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.audioFeatGestureSwipe,
            title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_swipe__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_swipe__summary),
            enabled = audioEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.audioFeatGestureMovingSwipe,
            title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
            summary = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
            enabled = audioEnabled,
        )

        Text(
            text = stringRes(R.string.pref__input_feedback__group_haptic__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3SwitchListPreference(
            switchPref = prefs.inputFeedback.hapticEnabled,
            listPref = prefs.inputFeedback.hapticActivationMode,
            title = stringRes(R.string.pref__input_feedback__haptic_enabled__label),
            summarySwitchDisabled = stringRes(R.string.pref__input_feedback__haptic_enabled__summary_disabled),
            entries = enumDisplayEntriesOf(InputFeedbackActivationMode::class, "haptic"),
        )
        M3ListPreference(
            pref = prefs.inputFeedback.hapticVibrationMode,
            title = stringRes(R.string.pref__input_feedback__haptic_vibration_mode__label),
            entries = enumDisplayEntriesOf(HapticVibrationMode::class),
            enabled = hapticEnabled,
        )
        M3DialogSliderPreference(
            pref = prefs.inputFeedback.hapticVibrationDuration,
            title = stringRes(R.string.pref__input_feedback__haptic_vibration_duration__label),
            valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
            min = 1, max = 100, stepIncrement = 1,
            enabled = hapticEnabled && hapticVibrationMode == HapticVibrationMode.USE_VIBRATOR_DIRECTLY && hasVibrator,
        )
        M3DialogSliderPreference(
            pref = prefs.inputFeedback.hapticVibrationStrength,
            title = stringRes(R.string.pref__input_feedback__haptic_vibration_strength__label),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 1, max = 100, stepIncrement = 1,
            enabled = hapticEnabled && hapticVibrationMode == HapticVibrationMode.USE_VIBRATOR_DIRECTLY && hasVibrator && hasAmplitudeControl,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.hapticFeatKeyPress,
            title = stringRes(R.string.pref__input_feedback__haptic_feat_key_press__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
            enabled = hapticEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.hapticFeatKeyLongPress,
            title = stringRes(R.string.pref__input_feedback__haptic_feat_key_long_press__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
            enabled = hapticEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.hapticFeatKeyRepeatedAction,
            title = stringRes(R.string.pref__input_feedback__haptic_feat_key_repeated_action__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
            enabled = hapticEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.hapticFeatGestureSwipe,
            title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_swipe__label),
            summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_swipe__summary),
            enabled = hapticEnabled,
        )
        M3SwitchPreference(
            pref = prefs.inputFeedback.hapticFeatGestureMovingSwipe,
            title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_moving_swipe__label),
            summary = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
            enabled = hapticEnabled,
        )
    }
}
