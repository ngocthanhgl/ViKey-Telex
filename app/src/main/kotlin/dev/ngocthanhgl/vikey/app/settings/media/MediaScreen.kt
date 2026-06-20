package dev.ngocthanhgl.vikey.app.settings.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3DialogSliderPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.media.emoji.EmojiHistory
import dev.ngocthanhgl.vikey.ime.media.emoji.EmojiHistoryHelper
import dev.ngocthanhgl.vikey.ime.media.emoji.EmojiSkinTone
import dev.ngocthanhgl.vikey.ime.media.emoji.EmojiSuggestionType
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

@Composable
fun MediaScreen() = FlorisScreen {
    title = stringRes(R.string.settings__media__title)
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    var shouldDelete by remember { mutableStateOf<ShouldDelete?>(null) }
    val scope = rememberCoroutineScope()

    content {
        val preferredSkinTone by prefs.emoji.preferredSkinTone.collectAsState()
        val historyPinnedUpdateStrategy by prefs.emoji.historyPinnedUpdateStrategy.collectAsState()
        val historyRecentUpdateStrategy by prefs.emoji.historyRecentUpdateStrategy.collectAsState()
        val historyPinnedMaxSize by prefs.emoji.historyPinnedMaxSize.collectAsState()
        val historyRecentMaxSize by prefs.emoji.historyRecentMaxSize.collectAsState()
        val suggestionType by prefs.emoji.suggestionType.collectAsState()
        val suggestionUpdateHistory by prefs.emoji.suggestionUpdateHistory.collectAsState()
        val suggestionCandidateShowName by prefs.emoji.suggestionCandidateShowName.collectAsState()
        val suggestionQueryMinLength by prefs.emoji.suggestionQueryMinLength.collectAsState()
        val suggestionCandidateMaxCount by prefs.emoji.suggestionCandidateMaxCount.collectAsState()

        M3ListPreference(
            value = preferredSkinTone,
            onSelect = { scope.launch { prefs.emoji.preferredSkinTone.set(EmojiSkinTone.valueOf(it)) } },
            title = stringRes(R.string.prefs__media__emoji_preferred_skin_tone),
            entries = enumDisplayEntriesOf(EmojiSkinTone::class).map { it.key.toString() to it.label },
        )

        Text(
            text = stringRes(R.string.prefs__media__emoji_history__title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val historyEnabled by prefs.emoji.historyEnabled.collectAsState()
        M3SwitchPreference(
            checked = historyEnabled,
            onCheckedChange = { scope.launch { prefs.emoji.historyEnabled.set(it) } },
            title = stringRes(R.string.prefs__media__emoji_history_enabled),
            summary = stringRes(R.string.prefs__media__emoji_history_enabled__summary),
        )
        M3ListPreference(
            value = historyPinnedUpdateStrategy,
            onSelect = { scope.launch { prefs.emoji.historyPinnedUpdateStrategy.set(EmojiHistory.UpdateStrategy.valueOf(it)) } },
            title = stringRes(R.string.prefs__media__emoji_history_pinned_update_strategy),
            entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class).map { it.key.toString() to it.label },
            enabled = historyEnabled,
        )
        M3ListPreference(
            value = historyRecentUpdateStrategy,
            onSelect = { scope.launch { prefs.emoji.historyRecentUpdateStrategy.set(EmojiHistory.UpdateStrategy.valueOf(it)) } },
            title = stringRes(R.string.prefs__media__emoji_history_recent_update_strategy),
            entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class).map { it.key.toString() to it.label },
            enabled = historyEnabled,
        )
        M3DialogSliderPreference(
            primaryValue = historyPinnedMaxSize,
            onPrimaryChange = { scope.launch { prefs.emoji.historyPinnedMaxSize.set(it) } },
            secondaryValue = historyRecentMaxSize,
            onSecondaryChange = { scope.launch { prefs.emoji.historyRecentMaxSize.set(it) } },
            title = stringRes(R.string.prefs__media__emoji_history_max_size),
            primaryLabel = stringRes(R.string.emoji__history__pinned),
            secondaryLabel = stringRes(R.string.emoji__history__recent),
            valueLabel = { maxSize ->
                if (maxSize == EmojiHistory.MaxSizeUnlimited) {
                    stringRes(R.string.general__unlimited)
                } else {
                    pluralsRes(R.plurals.unit__items__written, maxSize, "v" to maxSize)
                }
            },
            min = 0, max = 120, stepIncrement = 1,
            enabled = historyEnabled,
        )
        M3ClickablePreference(
            title = stringRes(R.string.prefs__media__emoji_history_pinned_reset),
            onClick = { shouldDelete = ShouldDelete(true) },
            enabled = historyEnabled,
        )
        M3ClickablePreference(
            title = stringRes(R.string.prefs__media__emoji_history_reset),
            onClick = { shouldDelete = ShouldDelete(false) },
            enabled = historyEnabled,
        )

        Text(
            text = stringRes(R.string.prefs__media__emoji_suggestion__title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val suggestionEnabled by prefs.emoji.suggestionEnabled.collectAsState()
        M3SwitchPreference(
            checked = suggestionEnabled,
            onCheckedChange = { scope.launch { prefs.emoji.suggestionEnabled.set(it) } },
            title = stringRes(R.string.prefs__media__emoji_suggestion_enabled),
            summary = stringRes(R.string.prefs__media__emoji_suggestion_enabled__summary),
        )
        M3ListPreference(
            value = suggestionType,
            onSelect = { scope.launch { prefs.emoji.suggestionType.set(EmojiSuggestionType.valueOf(it)) } },
            title = stringRes(R.string.prefs__media__emoji_suggestion_type),
            entries = enumDisplayEntriesOf(EmojiSuggestionType::class).map { it.key.toString() to it.label },
            enabled = suggestionEnabled,
        )
        M3SwitchPreference(
            checked = suggestionUpdateHistory,
            onCheckedChange = { scope.launch { prefs.emoji.suggestionUpdateHistory.set(it) } },
            title = stringRes(R.string.prefs__media__emoji_suggestion_update_history),
            summary = stringRes(R.string.prefs__media__emoji_suggestion_update_history__summary),
            enabled = suggestionEnabled && historyEnabled,
        )
        M3SwitchPreference(
            checked = suggestionCandidateShowName,
            onCheckedChange = { scope.launch { prefs.emoji.suggestionCandidateShowName.set(it) } },
            title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name),
            summary = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name__summary),
            enabled = suggestionEnabled,
        )
        M3DialogSliderPreference(
            value = suggestionQueryMinLength,
            onChange = { scope.launch { prefs.emoji.suggestionQueryMinLength.set(it) } },
            title = stringRes(R.string.prefs__media__emoji_suggestion_query_min_length),
            valueLabel = { length -> pluralsRes(R.plurals.unit__characters__written, length, "v" to length) },
            min = 1, max = 5, stepIncrement = 1,
            enabled = suggestionEnabled,
        )
        M3DialogSliderPreference(
            value = suggestionCandidateMaxCount,
            onChange = { scope.launch { prefs.emoji.suggestionCandidateMaxCount.set(it) } },
            title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_max_count),
            valueLabel = { count -> pluralsRes(R.plurals.unit__candidates__written, count, "v" to count) },
            min = 1, max = 10, stepIncrement = 1,
            enabled = suggestionEnabled,
        )
    }

    DeleteEmojiHistoryConfirmDialog(
        shouldDelete = shouldDelete,
        onDismiss = { shouldDelete = null },
        onConfirm = {
            shouldDelete?.let {
                scope.launch {
                    if (it.pinned) {
                        EmojiHistoryHelper.deletePinned(prefs = prefs)
                    } else {
                        EmojiHistoryHelper.deleteHistory(prefs = prefs)
                    }
                }
                shouldDelete = null
            }
        },
    )
}

@Composable
private fun DeleteEmojiHistoryConfirmDialog(
    shouldDelete: ShouldDelete?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    shouldDelete?.let {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringRes(R.string.action__reset_confirm_title)) },
            text = {
                if (it.pinned) {
                    Text(stringRes(R.string.action__reset_confirm_message, "name" to "pinned emojis"))
                } else {
                    Text(stringRes(R.string.action__reset_confirm_message, "name" to "emoji history"))
                }
            },
            confirmButton = {
                Button(onClick = onConfirm) { Text(stringRes(R.string.action__yes)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__no)) }
            },
        )
    }
}

private data class ShouldDelete(val pinned: Boolean)
