package dev.ngocthanhgl.vikey.app.settings.gestures

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.ime.text.gestures.SwipeAction
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@Composable
fun GesturesScreen() = FlorisScreen {
    title = stringRes(R.string.settings__gestures__title)
    previewFieldVisible = true

    content {
        val swipeUp by prefs.gestures.swipeUp.collectAsState()
        val swipeDown by prefs.gestures.swipeDown.collectAsState()
        val swipeLeft by prefs.gestures.swipeLeft.collectAsState()
        val swipeRight by prefs.gestures.swipeRight.collectAsState()
        val spaceBarSwipeUp by prefs.gestures.spaceBarSwipeUp.collectAsState()
        val spaceBarSwipeLeft by prefs.gestures.spaceBarSwipeLeft.collectAsState()
        val spaceBarSwipeRight by prefs.gestures.spaceBarSwipeRight.collectAsState()
        val spaceBarLongPress by prefs.gestures.spaceBarLongPress.collectAsState()
        val deleteKeySwipeLeft by prefs.gestures.deleteKeySwipeLeft.collectAsState()
        val deleteKeyLongPress by prefs.gestures.deleteKeyLongPress.collectAsState()
        val swipeVelocityThreshold by prefs.gestures.swipeVelocityThreshold.collectAsState()
        val swipeDistanceThreshold by prefs.gestures.swipeDistanceThreshold.collectAsState()

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
            value = swipeUp,
            onSelect = { prefs.gestures.swipeUp.set(it) },
            title = stringRes(R.string.pref__gestures__swipe_up__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = swipeDown,
            onSelect = { prefs.gestures.swipeDown.set(it) },
            title = stringRes(R.string.pref__gestures__swipe_down__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = swipeLeft,
            onSelect = { prefs.gestures.swipeLeft.set(it) },
            title = stringRes(R.string.pref__gestures__swipe_left__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = swipeRight,
            onSelect = { prefs.gestures.swipeRight.set(it) },
            title = stringRes(R.string.pref__gestures__swipe_right__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )

        Text(
            text = stringRes(R.string.pref__gestures__space_bar_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ListPreference(
            value = spaceBarSwipeUp,
            onSelect = { prefs.gestures.spaceBarSwipeUp.set(it) },
            title = stringRes(R.string.pref__gestures__space_bar_swipe_up__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = spaceBarSwipeLeft,
            onSelect = { prefs.gestures.spaceBarSwipeLeft.set(it) },
            title = stringRes(R.string.pref__gestures__space_bar_swipe_left__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = spaceBarSwipeRight,
            onSelect = { prefs.gestures.spaceBarSwipeRight.set(it) },
            title = stringRes(R.string.pref__gestures__space_bar_swipe_right__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = spaceBarLongPress,
            onSelect = { prefs.gestures.spaceBarLongPress.set(it) },
            title = stringRes(R.string.pref__gestures__space_bar_long_press__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
        )

        Text(
            text = stringRes(R.string.pref__gestures__other_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ListPreference(
            value = deleteKeySwipeLeft,
            onSelect = { prefs.gestures.deleteKeySwipeLeft.set(it) },
            title = stringRes(R.string.pref__gestures__delete_key_swipe_left__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe").map { it.key.toString() to it.label },
        )
        M3ListPreference(
            value = deleteKeyLongPress,
            onSelect = { prefs.gestures.deleteKeyLongPress.set(it) },
            title = stringRes(R.string.pref__gestures__delete_key_long_press__label),
            entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress").map { it.key.toString() to it.label },
        )
        M3DialogSliderPreference(
            value = swipeVelocityThreshold,
            onChange = { prefs.gestures.swipeVelocityThreshold.set(it) },
            title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
            valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
            min = 400, max = 4000, stepIncrement = 100,
        )
        M3DialogSliderPreference(
            value = swipeDistanceThreshold,
            onChange = { prefs.gestures.swipeDistanceThreshold.set(it) },
            title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
            valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
            min = 12, max = 72, stepIncrement = 1,
        )
    }
}
