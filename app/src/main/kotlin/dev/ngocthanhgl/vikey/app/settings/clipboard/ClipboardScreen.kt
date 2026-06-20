package dev.ngocthanhgl.vikey.app.settings.clipboard

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
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.ngocthanhgl.vikey.ime.clipboard.ClipboardSyncBehavior
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

@Composable
fun ClipboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__clipboard__title)
    previewFieldVisible = true

    content {
        val useInternalClipboard by prefs.clipboard.useInternalClipboard.collectAsState()
        val historyEnabled by prefs.clipboard.historyEnabled.collectAsState()
        val historyAutoCleanOldEnabled by prefs.clipboard.historyAutoCleanOldEnabled.collectAsState()
        val historySizeLimitEnabled by prefs.clipboard.historySizeLimitEnabled.collectAsState()
        val historyAutoCleanSensitiveEnabled by prefs.clipboard.historyAutoCleanSensitiveEnabled.collectAsState()
        val suggestionEnabled by prefs.clipboard.suggestionEnabled.collectAsState()
        val syncToFloris by prefs.clipboard.syncToFloris.collectAsState()
        val syncToSystem by prefs.clipboard.syncToSystem.collectAsState()
        val suggestionTimeout by prefs.clipboard.suggestionTimeout.collectAsState()
        val historyNumGridColumnsPortrait by prefs.clipboard.historyNumGridColumnsPortrait.collectAsState()
        val historyNumGridColumnsLandscape by prefs.clipboard.historyNumGridColumnsLandscape.collectAsState()
        val historyAutoCleanOldAfter by prefs.clipboard.historyAutoCleanOldAfter.collectAsState()
        val historyAutoCleanSensitiveAfter by prefs.clipboard.historyAutoCleanSensitiveAfter.collectAsState()
        val historySizeLimit by prefs.clipboard.historySizeLimit.collectAsState()
        val historyHideOnPaste by prefs.clipboard.historyHideOnPaste.collectAsState()
        val historyHideOnNextTextField by prefs.clipboard.historyHideOnNextTextField.collectAsState()
        val clearPrimaryClipAffectsHistoryIfUnpinned by prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.collectAsState()

        M3SwitchPreference(
            checked = useInternalClipboard,
            onCheckedChange = { prefs.clipboard.useInternalClipboard.set(it) },
            title = stringRes(R.string.pref__clipboard__use_internal_clipboard__label),
            summary = stringRes(R.string.pref__clipboard__use_internal_clipboard__summary),
        )
        M3ListPreference(
            value = syncToFloris,
            onSelect = { prefs.clipboard.syncToFloris.set(it) },
            title = stringRes(R.string.pref__clipboard__sync_from_system_clipboard__label),
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class).map { it.key.toString() to it.label },
            enabled = useInternalClipboard,
        )
        M3ListPreference(
            value = syncToSystem,
            onSelect = { prefs.clipboard.syncToSystem.set(it) },
            title = stringRes(R.string.pref__clipboard__sync_to_system_clipboard__label),
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class).map { it.key.toString() to it.label },
            enabled = useInternalClipboard,
        )

        Text(
            text = stringRes(R.string.pref__clipboard__group_clipboard_suggestion__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3SwitchPreference(
            checked = suggestionEnabled,
            onCheckedChange = { prefs.clipboard.suggestionEnabled.set(it) },
            title = stringRes(R.string.pref__clipboard__suggestion_enabled__label),
            summary = stringRes(R.string.pref__clipboard__suggestion_enabled__summary),
        )
        M3DialogSliderPreference(
            value = suggestionTimeout,
            onChange = { prefs.clipboard.suggestionTimeout.set(it) },
            title = stringRes(R.string.pref__clipboard__suggestion_timeout__label),
            valueLabel = { stringRes(R.string.pref__clipboard__suggestion_timeout__summary, "v" to it) },
            min = 30, max = 300, stepIncrement = 5,
            enabled = suggestionEnabled,
        )

        Text(
            text = stringRes(R.string.pref__clipboard__group_clipboard_history__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3SwitchPreference(
            checked = historyEnabled,
            onCheckedChange = { prefs.clipboard.historyEnabled.set(it) },
            title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
            summary = stringRes(R.string.pref__clipboard__enable_clipboard_history__summary),
        )
        M3DialogSliderPreference(
            primaryValue = historyNumGridColumnsPortrait,
            onPrimaryChange = { prefs.clipboard.historyNumGridColumnsPortrait.set(it) },
            secondaryValue = historyNumGridColumnsLandscape,
            onSecondaryChange = { prefs.clipboard.historyNumGridColumnsLandscape.set(it) },
            title = stringRes(R.string.pref__clipboard__num_history_grid_columns__label),
            primaryLabel = stringRes(R.string.screen_orientation__portrait),
            secondaryLabel = stringRes(R.string.screen_orientation__landscape),
            valueLabel = { numGridColumns ->
                if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                    stringRes(R.string.general__auto)
                } else {
                    numGridColumns.toString()
                }
            },
            min = 0, max = 10, stepIncrement = 1,
            enabled = historyEnabled,
        )
        M3SwitchPreference(
            checked = historyAutoCleanOldEnabled,
            onCheckedChange = { prefs.clipboard.historyAutoCleanOldEnabled.set(it) },
            title = stringRes(R.string.pref__clipboard__clean_up_old__label),
            enabled = historyEnabled,
        )
        M3DialogSliderPreference(
            value = historyAutoCleanOldAfter,
            onChange = { prefs.clipboard.historyAutoCleanOldAfter.set(it) },
            title = stringRes(R.string.pref__clipboard__clean_up_after__label),
            valueLabel = { pluralsRes(R.plurals.unit__minutes__written, it, "v" to it) },
            min = 0, max = 120, stepIncrement = 5,
            enabled = historyEnabled && historyAutoCleanOldEnabled,
        )
        if (AndroidVersion.ATLEAST_API33_T) {
            M3SwitchPreference(
                checked = historyAutoCleanSensitiveEnabled,
                onCheckedChange = { prefs.clipboard.historyAutoCleanSensitiveEnabled.set(it) },
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive__label),
                enabled = historyEnabled,
            )
            M3DialogSliderPreference(
                value = historyAutoCleanSensitiveAfter,
                onChange = { prefs.clipboard.historyAutoCleanSensitiveAfter.set(it) },
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive_after__label),
                valueLabel = { pluralsRes(R.plurals.unit__seconds__written, it, "v" to it) },
                min = 0, max = 300, stepIncrement = 10,
                enabled = historyEnabled && historyAutoCleanSensitiveEnabled,
            )
        }
        M3SwitchPreference(
            checked = historySizeLimitEnabled,
            onCheckedChange = { prefs.clipboard.historySizeLimitEnabled.set(it) },
            title = stringRes(R.string.pref__clipboard__limit_history_size__label),
            enabled = historyEnabled,
        )
        M3DialogSliderPreference(
            value = historySizeLimit,
            onChange = { prefs.clipboard.historySizeLimit.set(it) },
            title = stringRes(R.string.pref__clipboard__max_history_size__label),
            valueLabel = { pluralsRes(R.plurals.unit__items__written, it, "v" to it) },
            min = 5, max = 100, stepIncrement = 5,
            enabled = historyEnabled && historySizeLimitEnabled,
        )
        M3SwitchPreference(
            checked = historyHideOnPaste,
            onCheckedChange = { prefs.clipboard.historyHideOnPaste.set(it) },
            title = stringRes(R.string.pref__clipboard__history_hide_on_paste__label),
            enabled = historyEnabled,
        )
        M3SwitchPreference(
            checked = historyHideOnNextTextField,
            onCheckedChange = { prefs.clipboard.historyHideOnNextTextField.set(it) },
            title = stringRes(R.string.pref__clipboard__history_hide_on_next_text_field__label),
            enabled = historyEnabled,
        )
        M3SwitchPreference(
            checked = clearPrimaryClipAffectsHistoryIfUnpinned,
            onCheckedChange = { prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.set(it) },
            title = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__label),
            summary = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__summary),
            enabled = historyEnabled,
        )
    }
}
