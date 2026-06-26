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

package dev.ngocthanhgl.vikey.app.ext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.lib.ext.ExtensionMaintainer
import dev.ngocthanhgl.vikey.lib.util.launchUrl

@Composable
fun ExtensionMaintainerChip(
    maintainer: ExtensionMaintainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }

    val hasEmail = maintainer.email != null
    val hasUrl = maintainer.url != null

    Surface(
        modifier = modifier
            .clickable(enabled = hasEmail || hasUrl) { showDialog = true },
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = maintainer.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (hasEmail || hasUrl) {
                Spacer(Modifier.width(4.dp))
                if (hasEmail) {
                    Icon(
                        imageVector = Icons.Rounded.Mail,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
                if (hasUrl) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(maintainer.name) },
            text = {
                Column {
                    if (maintainer.email != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { context.launchUrl("mailto:${maintainer.email}") }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mail,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = maintainer.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (maintainer.url != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { context.launchUrl(maintainer.url) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = maintainer.url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(org.florisboard.lib.compose.stringRes(dev.ngocthanhgl.vikey.R.string.action__done))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewChipNameOnly() {
    val maintainer = ExtensionMaintainer(
        name = "Jane Doe",
        email = null,
        url = null,
    )
    ExtensionMaintainerChip(maintainer)
}

@Preview(showBackground = true)
@Composable
private fun PreviewChipNameAndEmail() {
    val maintainer = ExtensionMaintainer(
        name = "Jane Doe",
        email = "jane.doe@example.com",
        url = null,
    )
    ExtensionMaintainerChip(maintainer)
}

@Preview(showBackground = true)
@Composable
private fun PreviewChipNameAndUrl() {
    val maintainer = ExtensionMaintainer(
        name = "Jane Doe",
        email = null,
        url = "jane-doe.example.com",
    )
    ExtensionMaintainerChip(maintainer)
}
