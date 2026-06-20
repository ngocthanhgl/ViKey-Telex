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

        M3SwitchPreference(
            pref = prefs.clipboard.useInternalClipboard,
            title = stringRes(R.string.pref__clipboard__use_internal_clipboard__label),
            summary = stringRes(R.string.pref__clipboard__use_internal_clipboard__summary),
        )
        M3ListPreference(
            pref = prefs.clipboard.syncToFloris,
            title = stringRes(R.string.pref__clipboard__sync_from_system_clipboard__label),
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
            enabled = useInternalClipboard,
        )
        M3ListPreference(
            pref = prefs.clipboard.syncToSystem,
            title = stringRes(R.string.pref__clipboard__sync_to_system_clipboard__label),
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
            enabled = useInternalClipboard,
        )

        Text(
            text = stringRes(R.string.pref__clipboard__group_clipboard_suggestion__label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3SwitchPreference(
            pref = prefs.clipboard.suggestionEnabled,
            title = stringRes(R.string.pref__clipboard__suggestion_enabled__label),
            summary = stringRes(R.string.pref__clipboard__suggestion_enabled__summary),
        )
        M3DialogSliderPreference(
            pref = prefs.clipboard.suggestionTimeout,
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
            pref = prefs.clipboard.historyEnabled,
            title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
            summary = stringRes(R.string.pref__clipboard__enable_clipboard_history__summary),
        )
        M3DialogSliderPreference(
            primaryPref = prefs.clipboard.historyNumGridColumnsPortrait,
            secondaryPref = prefs.clipboard.historyNumGridColumnsLandscape,
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
            pref = prefs.clipboard.historyAutoCleanOldEnabled,
            title = stringRes(R.string.pref__clipboard__clean_up_old__label),
            enabled = historyEnabled,
        )
        M3DialogSliderPreference(
            pref = prefs.clipboard.historyAutoCleanOldAfter,
            title = stringRes(R.string.pref__clipboard__clean_up_after__label),
            valueLabel = { pluralsRes(R.plurals.unit__minutes__written, it, "v" to it) },
            min = 0, max = 120, stepIncrement = 5,
            enabled = historyEnabled && historyAutoCleanOldEnabled,
        )
        if (AndroidVersion.ATLEAST_API33_T) {
            M3SwitchPreference(
                pref = prefs.clipboard.historyAutoCleanSensitiveEnabled,
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive__label),
                enabled = historyEnabled,
            )
            M3DialogSliderPreference(
                pref = prefs.clipboard.historyAutoCleanSensitiveAfter,
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive_after__label),
                valueLabel = { pluralsRes(R.plurals.unit__seconds__written, it, "v" to it) },
                min = 0, max = 300, stepIncrement = 10,
                enabled = historyEnabled && historyAutoCleanSensitiveEnabled,
            )
        }
        M3SwitchPreference(
            pref = prefs.clipboard.historySizeLimitEnabled,
            title = stringRes(R.string.pref__clipboard__limit_history_size__label),
            enabled = historyEnabled,
        )
        M3DialogSliderPreference(
            pref = prefs.clipboard.historySizeLimit,
            title = stringRes(R.string.pref__clipboard__max_history_size__label),
            valueLabel = { pluralsRes(R.plurals.unit__items__written, it, "v" to it) },
            min = 5, max = 100, stepIncrement = 5,
            enabled = historyEnabled && historySizeLimitEnabled,
        )
        M3SwitchPreference(
            pref = prefs.clipboard.historyHideOnPaste,
            title = stringRes(R.string.pref__clipboard__history_hide_on_paste__label),
            enabled = historyEnabled,
        )
        M3SwitchPreference(
            pref = prefs.clipboard.historyHideOnNextTextField,
            title = stringRes(R.string.pref__clipboard__history_hide_on_next_text_field__label),
            enabled = historyEnabled,
        )
        M3SwitchPreference(
            pref = prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned,
            title = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__label),
            summary = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__summary),
            enabled = historyEnabled,
        )
    }
}
