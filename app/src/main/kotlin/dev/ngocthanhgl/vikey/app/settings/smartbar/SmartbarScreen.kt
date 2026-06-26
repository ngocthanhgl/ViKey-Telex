package dev.ngocthanhgl.vikey.app.settings.smartbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Widgets
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
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.smartbar.CandidatesDisplayMode
import dev.ngocthanhgl.vikey.ime.smartbar.ExtendedActionsPlacement
import dev.ngocthanhgl.vikey.ime.smartbar.SmartbarLayout
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun SmartbarScreen() {
    val prefs by FlorisPreferenceStore

    SettingsScaffold(title = stringRes(R.string.settings__smartbar__title)) {
        val scope = rememberCoroutineScope()
        val enabled by prefs.smartbar.enabled.collectAsState()
        val layout by prefs.smartbar.layout.collectAsState()
        val displayMode by prefs.suggestion.displayMode.collectAsState()
        val flipToggles by prefs.smartbar.flipToggles.collectAsState()
        val sharedActionsAutoExpandCollapse by prefs.smartbar.sharedActionsAutoExpandCollapse.collectAsState()
        val extendedActionsPlacement by prefs.smartbar.extendedActionsPlacement.collectAsState()

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
                icon = Icons.Rounded.Widgets,
                checked = enabled,
                onCheckedChange = { scope.launch { prefs.smartbar.enabled.set(it) } },
                title = stringRes(R.string.pref__smartbar__enabled__label),
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.ViewList,
                value = layout,
                onSelect = { scope.launch { prefs.smartbar.layout.set(SmartbarLayout.valueOf(it)) } },
                title = stringRes(R.string.pref__smartbar__layout__label),
                entries = enumDisplayEntriesOf(SmartbarLayout::class).map { it.key.toString() to it.label },
                enabled = enabled,
            )
        }

        Text(
            text = stringRes(R.string.pref__smartbar__group_layout_specific__label),
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
            if (layout != SmartbarLayout.ACTIONS_ONLY) {
                M3ListPreference(
                    icon = Icons.Rounded.Visibility,
                    value = displayMode,
                    onSelect = { scope.launch { prefs.suggestion.displayMode.set(CandidatesDisplayMode.valueOf(it)) } },
                    title = stringRes(R.string.pref__suggestion__display_mode__label),
                    entries = enumDisplayEntriesOf(CandidatesDisplayMode::class).map { it.key.toString() to it.label },
                    enabled = enabled,
                )
                SettingsDivider()
            }
            if (layout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED || layout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED) {
                M3SwitchPreference(
                    icon = Icons.Rounded.SwapHoriz,
                    checked = flipToggles,
                    onCheckedChange = { scope.launch { prefs.smartbar.flipToggles.set(it) } },
                    title = stringRes(R.string.pref__smartbar__flip_toggles__label),
                    enabled = enabled,
                )
                SettingsDivider()
            }
            M3SwitchPreference(
                icon = Icons.Rounded.UnfoldMore,
                checked = sharedActionsAutoExpandCollapse,
                onCheckedChange = { scope.launch { prefs.smartbar.sharedActionsAutoExpandCollapse.set(it) } },
                title = stringRes(R.string.pref__smartbar__shared_actions_auto_expand_collapse__label),
                enabled = enabled,
            )
            if (layout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED) {
                SettingsDivider()
                M3ListPreference(
                    icon = Icons.Rounded.Extension,
                    value = extendedActionsPlacement,
                    onSelect = { scope.launch { prefs.smartbar.extendedActionsPlacement.set(ExtendedActionsPlacement.valueOf(it)) } },
                    title = stringRes(R.string.pref__smartbar__extended_actions_placement__label),
                    entries = enumDisplayEntriesOf(ExtendedActionsPlacement::class).map { it.key.toString() to it.label },
                    enabled = enabled,
                )
            }
        }
    }
}
