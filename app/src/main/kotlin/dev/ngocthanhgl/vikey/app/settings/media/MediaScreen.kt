package dev.ngocthanhgl.vikey.app.settings.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    var shouldDelete by remember { mutableStateOf<ShouldDelete?>(null) }
    val scope = rememberCoroutineScope()

    content {
        M3ListPreference(
            pref = prefs.emoji.preferredSkinTone,
            title = stringRes(R.string.prefs__media__emoji_preferred_skin_tone),
            entries = enumDisplayEntriesOf(EmojiSkinTone::class),
        )

        Text(
            text = stringRes(R.string.prefs__media__emoji_history__title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val historyEnabled by prefs.emoji.historyEnabled.collectAsState()
        M3SwitchPreference(
            pref = prefs.emoji.historyEnabled,
            title = stringRes(R.string.prefs__media__emoji_history_enabled),
            summary = stringRes(R.string.prefs__media__emoji_history_enabled__summary),
        )
        M3ListPreference(
            pref = prefs.emoji.historyPinnedUpdateStrategy,
            title = stringRes(R.string.prefs__media__emoji_history_pinned_update_strategy),
            entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
            enabled = historyEnabled,
        )
        M3ListPreference(
            pref = prefs.emoji.historyRecentUpdateStrategy,
            title = stringRes(R.string.prefs__media__emoji_history_recent_update_strategy),
            entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
            enabled = historyEnabled,
        )
        M3DialogSliderPreference(
            primaryPref = prefs.emoji.historyPinnedMaxSize,
            secondaryPref = prefs.emoji.historyRecentMaxSize,
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
            pref = prefs.emoji.suggestionEnabled,
            title = stringRes(R.string.prefs__media__emoji_suggestion_enabled),
            summary = stringRes(R.string.prefs__media__emoji_suggestion_enabled__summary),
        )
        M3ListPreference(
            pref = prefs.emoji.suggestionType,
            title = stringRes(R.string.prefs__media__emoji_suggestion_type),
            entries = enumDisplayEntriesOf(EmojiSuggestionType::class),
            enabled = suggestionEnabled,
        )
        M3SwitchPreference(
            pref = prefs.emoji.suggestionUpdateHistory,
            title = stringRes(R.string.prefs__media__emoji_suggestion_update_history),
            summary = stringRes(R.string.prefs__media__emoji_suggestion_update_history__summary),
            enabled = suggestionEnabled && historyEnabled,
        )
        M3SwitchPreference(
            pref = prefs.emoji.suggestionCandidateShowName,
            title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name),
            summary = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name__summary),
            enabled = suggestionEnabled,
        )
        M3DialogSliderPreference(
            pref = prefs.emoji.suggestionQueryMinLength,
            title = stringRes(R.string.prefs__media__emoji_suggestion_query_min_length),
            valueLabel = { length -> pluralsRes(R.plurals.unit__characters__written, length, "v" to length) },
            min = 1, max = 5, stepIncrement = 1,
            enabled = suggestionEnabled,
        )
        M3DialogSliderPreference(
            pref = prefs.emoji.suggestionCandidateMaxCount,
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
