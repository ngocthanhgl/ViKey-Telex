package dev.ngocthanhgl.vikey.app.settings.gestures

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
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
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.text.gestures.SwipeAction
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun GesturesScreen() {
    val prefs by FlorisPreferenceStore

    SettingsScaffold(title = stringRes(R.string.settings__gestures__title)) {
        val scope = rememberCoroutineScope()
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

        Text(
            text = stringRes(R.string.pref__gestures__general_title),
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
                icon = Icons.Rounded.KeyboardArrowUp,
                value = swipeUp,
                onSelect = { scope.launch { prefs.gestures.swipeUp.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.KeyboardArrowDown,
                value = swipeDown,
                onSelect = { scope.launch { prefs.gestures.swipeDown.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__swipe_down__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.KeyboardArrowLeft,
                value = swipeLeft,
                onSelect = { scope.launch { prefs.gestures.swipeLeft.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.KeyboardArrowRight,
                value = swipeRight,
                onSelect = { scope.launch { prefs.gestures.swipeRight.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
        }

        Text(
            text = stringRes(R.string.pref__gestures__space_bar_title),
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
                icon = Icons.Rounded.KeyboardArrowUp,
                value = spaceBarSwipeUp,
                onSelect = { scope.launch { prefs.gestures.spaceBarSwipeUp.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__space_bar_swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.KeyboardArrowLeft,
                value = spaceBarSwipeLeft,
                onSelect = { scope.launch { prefs.gestures.spaceBarSwipeLeft.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__space_bar_swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.KeyboardArrowRight,
                value = spaceBarSwipeRight,
                onSelect = { scope.launch { prefs.gestures.spaceBarSwipeRight.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__space_bar_swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.PanTool,
                value = spaceBarLongPress,
                onSelect = { scope.launch { prefs.gestures.spaceBarLongPress.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__space_bar_long_press__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general").map { it.key.toString() to it.label },
            )
        }

        Text(
            text = stringRes(R.string.pref__gestures__other_title),
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
                icon = Icons.Rounded.KeyboardArrowLeft,
                value = deleteKeySwipeLeft,
                onSelect = { scope.launch { prefs.gestures.deleteKeySwipeLeft.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__delete_key_swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.PanTool,
                value = deleteKeyLongPress,
                onSelect = { scope.launch { prefs.gestures.deleteKeyLongPress.set(SwipeAction.valueOf(it)) } },
                title = stringRes(R.string.pref__gestures__delete_key_long_press__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress").map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Rounded.Speed,
                value = swipeVelocityThreshold,
                onChange = { scope.launch { prefs.gestures.swipeVelocityThreshold.set(it) } },
                title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
                min = 400, max = 4000, stepIncrement = 100,
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Rounded.TouchApp,
                value = swipeDistanceThreshold,
                onChange = { scope.launch { prefs.gestures.swipeDistanceThreshold.set(it) } },
                title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 12, max = 72, stepIncrement = 1,
            )
        }
    }
}
