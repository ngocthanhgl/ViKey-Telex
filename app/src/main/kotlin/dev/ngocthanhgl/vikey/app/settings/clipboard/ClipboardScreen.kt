package dev.ngocthanhgl.vikey.app.settings.clipboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
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
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.ngocthanhgl.vikey.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

@Composable
fun ClipboardScreen() {
    val prefs by FlorisPreferenceStore

    SettingsScaffold(title = stringRes(R.string.settings__clipboard__title)) {
        val scope = rememberCoroutineScope()
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
                icon = Icons.Rounded.ContentPaste,
                checked = useInternalClipboard,
                onCheckedChange = { scope.launch { prefs.clipboard.useInternalClipboard.set(it) } },
                title = stringRes(R.string.pref__clipboard__use_internal_clipboard__label),
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.CloudDownload,
                value = syncToFloris,
                onSelect = { scope.launch { prefs.clipboard.syncToFloris.set(ClipboardSyncBehavior.valueOf(it)) } },
                title = stringRes(R.string.pref__clipboard__sync_from_system_clipboard__label),
                entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class).map { it.key.toString() to it.label },
                enabled = useInternalClipboard,
            )
            SettingsDivider()
            M3ListPreference(
                icon = Icons.Rounded.CloudUpload,
                value = syncToSystem,
                onSelect = { scope.launch { prefs.clipboard.syncToSystem.set(ClipboardSyncBehavior.valueOf(it)) } },
                title = stringRes(R.string.pref__clipboard__sync_to_system_clipboard__label),
                entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class).map { it.key.toString() to it.label },
                enabled = useInternalClipboard,
            )
        }

        Text(
            text = stringRes(R.string.pref__clipboard__group_clipboard_suggestion__label),
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
            M3SwitchPreference(
                icon = Icons.Rounded.Lightbulb,
                checked = suggestionEnabled,
                onCheckedChange = { scope.launch { prefs.clipboard.suggestionEnabled.set(it) } },
                title = stringRes(R.string.pref__clipboard__suggestion_enabled__label),
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Rounded.Timer,
                value = suggestionTimeout,
                onChange = { scope.launch { prefs.clipboard.suggestionTimeout.set(it) } },
                title = stringRes(R.string.pref__clipboard__suggestion_timeout__label),
                valueLabel = { stringRes(R.string.pref__clipboard__suggestion_timeout__summary, "v" to it) },
                min = 30, max = 300, stepIncrement = 5,
                enabled = suggestionEnabled,
            )
        }

        Text(
            text = stringRes(R.string.pref__clipboard__group_clipboard_history__label),
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
            M3SwitchPreference(
                icon = Icons.Rounded.History,
                checked = historyEnabled,
                onCheckedChange = { scope.launch { prefs.clipboard.historyEnabled.set(it) } },
                title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Rounded.GridOn,
                primaryValue = historyNumGridColumnsPortrait,
                onPrimaryChange = { scope.launch { prefs.clipboard.historyNumGridColumnsPortrait.set(it) } },
                secondaryValue = historyNumGridColumnsLandscape,
                onSecondaryChange = { scope.launch { prefs.clipboard.historyNumGridColumnsLandscape.set(it) } },
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
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Rounded.AutoDelete,
                checked = historyAutoCleanOldEnabled,
                onCheckedChange = { scope.launch { prefs.clipboard.historyAutoCleanOldEnabled.set(it) } },
                title = stringRes(R.string.pref__clipboard__clean_up_old__label),
                enabled = historyEnabled,
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Rounded.Timer,
                value = historyAutoCleanOldAfter,
                onChange = { scope.launch { prefs.clipboard.historyAutoCleanOldAfter.set(it) } },
                title = stringRes(R.string.pref__clipboard__clean_up_after__label),
                valueLabel = { pluralsRes(R.plurals.unit__minutes__written, it, "v" to it) },
                min = 0, max = 120, stepIncrement = 5,
                enabled = historyEnabled && historyAutoCleanOldEnabled,
            )
            if (AndroidVersion.ATLEAST_API33_T) {
                SettingsDivider()
                M3SwitchPreference(
                    icon = Icons.Rounded.Shield,
                    checked = historyAutoCleanSensitiveEnabled,
                    onCheckedChange = { scope.launch { prefs.clipboard.historyAutoCleanSensitiveEnabled.set(it) } },
                    title = stringRes(R.string.pref__clipboard__auto_clean_sensitive__label),
                    enabled = historyEnabled,
                )
                SettingsDivider()
                M3DialogSliderPreference(
                    icon = Icons.Rounded.Timer,
                    value = historyAutoCleanSensitiveAfter,
                    onChange = { scope.launch { prefs.clipboard.historyAutoCleanSensitiveAfter.set(it) } },
                    title = stringRes(R.string.pref__clipboard__auto_clean_sensitive_after__label),
                    valueLabel = { pluralsRes(R.plurals.unit__seconds__written, it, "v" to it) },
                    min = 0, max = 300, stepIncrement = 10,
                    enabled = historyEnabled && historyAutoCleanSensitiveEnabled,
                )
            }
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Rounded.DataUsage,
                checked = historySizeLimitEnabled,
                onCheckedChange = { scope.launch { prefs.clipboard.historySizeLimitEnabled.set(it) } },
                title = stringRes(R.string.pref__clipboard__limit_history_size__label),
                enabled = historyEnabled,
            )
            SettingsDivider()
            M3DialogSliderPreference(
                icon = Icons.Rounded.Storage,
                value = historySizeLimit,
                onChange = { scope.launch { prefs.clipboard.historySizeLimit.set(it) } },
                title = stringRes(R.string.pref__clipboard__max_history_size__label),
                valueLabel = { pluralsRes(R.plurals.unit__items__written, it, "v" to it) },
                min = 5, max = 100, stepIncrement = 5,
                enabled = historyEnabled && historySizeLimitEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Rounded.VisibilityOff,
                checked = historyHideOnPaste,
                onCheckedChange = { scope.launch { prefs.clipboard.historyHideOnPaste.set(it) } },
                title = stringRes(R.string.pref__clipboard__history_hide_on_paste__label),
                enabled = historyEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Rounded.VisibilityOff,
                checked = historyHideOnNextTextField,
                onCheckedChange = { scope.launch { prefs.clipboard.historyHideOnNextTextField.set(it) } },
                title = stringRes(R.string.pref__clipboard__history_hide_on_next_text_field__label),
                enabled = historyEnabled,
            )
            SettingsDivider()
            M3SwitchPreference(
                icon = Icons.Rounded.Delete,
                checked = clearPrimaryClipAffectsHistoryIfUnpinned,
                onCheckedChange = { scope.launch { prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.set(it) } },
                title = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__label),
                enabled = historyEnabled,
            )
        }
    }
}
