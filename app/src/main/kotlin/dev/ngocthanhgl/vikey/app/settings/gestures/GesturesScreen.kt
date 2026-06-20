package dev.ngocthanhgl.vikey.app.settings.gestures

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.ime.text.gestures.SwipeAction
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@Composable
fun GesturesScreen() = FlorisScreen {
    title = stringRes(R.string.settings__gestures__title)
    previewFieldVisible = true

    content {
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = """
                Glide typing is currently not available and will be re-implemented from the ground up with word suggestions & the new keyboard layout engine. DO NOT file an issue for this missing functionality.
            """.trimIndent()
        )

        Text(
            text = stringRes(R.string.pref__gestures__general_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ListPreference(
            pref = prefs.gestures.swipeUp,
            title = stringRes(R.string.pref__gestures__swipe_up__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        M3ListPreference(
            pref = prefs.gestures.swipeDown,
            title = stringRes(R.string.pref__gestures__swipe_down__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        M3ListPreference(
            pref = prefs.gestures.swipeLeft,
            title = stringRes(R.string.pref__gestures__swipe_left__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        M3ListPreference(
            pref = prefs.gestures.swipeRight,
            title = stringRes(R.string.pref__gestures__swipe_right__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )

        Text(
            text = stringRes(R.string.pref__gestures__space_bar_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ListPreference(
            pref = prefs.gestures.spaceBarSwipeUp,
            title = stringRes(R.string.pref__gestures__space_bar_swipe_up__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        M3ListPreference(
            pref = prefs.gestures.spaceBarSwipeLeft,
            title = stringRes(R.string.pref__gestures__space_bar_swipe_left__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        M3ListPreference(
            pref = prefs.gestures.spaceBarSwipeRight,
            title = stringRes(R.string.pref__gestures__space_bar_swipe_right__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        M3ListPreference(
            pref = prefs.gestures.spaceBarLongPress,
            title = stringRes(R.string.pref__gestures__space_bar_long_press__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )

        Text(
            text = stringRes(R.string.pref__gestures__other_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ListPreference(
            pref = prefs.gestures.deleteKeySwipeLeft,
            title = stringRes(R.string.pref__gestures__delete_key_swipe_left__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe"),
        )
        M3ListPreference(
            pref = prefs.gestures.deleteKeyLongPress,
            title = stringRes(R.string.pref__gestures__delete_key_long_press__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress"),
        )
        M3DialogSliderPreference(
            pref = prefs.gestures.swipeVelocityThreshold,
            title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
            valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
            min = 400, max = 4000, stepIncrement = 100,
        )
        M3DialogSliderPreference(
            pref = prefs.gestures.swipeDistanceThreshold,
            title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
            valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
            min = 12, max = 72, stepIncrement = 1,
        )
    }
}
