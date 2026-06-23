/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.ngocthanhgl.vikey.lib.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ngocthanhgl.vikey.R
import org.florisboard.lib.compose.stringRes

@Composable
fun FlorisConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    what: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.action__delete_confirm_title)) },
        text = { Text(stringRes(R.string.action__delete_confirm_message, "name" to what)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.action__delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
        },
    )
}

@Composable
fun FlorisUnsavedChangesDialog(
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.action__discard_confirm_title)) },
        text = { Text(stringRes(R.string.action__discard_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringRes(R.string.action__save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(
                    stringRes(R.string.action__discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}
