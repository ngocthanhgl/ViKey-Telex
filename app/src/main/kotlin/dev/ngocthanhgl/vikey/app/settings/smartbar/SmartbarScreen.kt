package dev.ngocthanhgl.vikey.app.settings.smartbar

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.smartbar.CandidatesDisplayMode
import dev.ngocthanhgl.vikey.ime.smartbar.ExtendedActionsPlacement
import dev.ngocthanhgl.vikey.ime.smartbar.SmartbarLayout
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes

@Composable
fun SmartbarScreen() = FlorisScreen {
    title = stringRes(R.string.settings__smartbar__title)
    previewFieldVisible = true

    content {
        val enabled by prefs.smartbar.enabled.collectAsState()
        val layout by prefs.smartbar.layout.collectAsState()

        M3SwitchPreference(
            pref = prefs.smartbar.enabled,
            title = stringRes(R.string.pref__smartbar__enabled__label),
            summary = stringRes(R.string.pref__smartbar__enabled__summary),
        )
        M3ListPreference(
            pref = prefs.smartbar.layout,
            title = stringRes(R.string.pref__smartbar__layout__label),
            entries = enumDisplayEntriesOf(SmartbarLayout::class),
            enabled = enabled,
        )

        Text(
            text = stringRes(R.string.pref__smartbar__group_layout_specific__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        if (layout != SmartbarLayout.ACTIONS_ONLY) {
            M3ListPreference(
                pref = prefs.suggestion.displayMode,
                title = stringRes(R.string.pref__suggestion__display_mode__label),
                entries = enumDisplayEntriesOf(CandidatesDisplayMode::class),
                enabled = enabled,
            )
        }
        if (layout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED || layout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED) {
            M3SwitchPreference(
                pref = prefs.smartbar.flipToggles,
                title = stringRes(R.string.pref__smartbar__flip_toggles__label),
                summary = stringRes(R.string.pref__smartbar__flip_toggles__summary),
                enabled = enabled,
            )
        }
        M3SwitchPreference(
            pref = prefs.smartbar.sharedActionsAutoExpandCollapse,
            title = stringRes(R.string.pref__smartbar__shared_actions_auto_expand_collapse__label),
            summary = "[Since v0.4.1] Always enabled due to UX issues",
            enabled = false,
        )
        if (layout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED) {
            M3ListPreference(
                pref = prefs.smartbar.extendedActionsPlacement,
                title = stringRes(R.string.pref__smartbar__extended_actions_placement__label),
                entries = enumDisplayEntriesOf(ExtendedActionsPlacement::class),
                enabled = enabled,
            )
        }
    }
}
