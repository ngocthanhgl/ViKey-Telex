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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.extensionManager
import dev.ngocthanhgl.vikey.ime.nlp.LanguagePackExtension
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtension
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionComponentImpl
import dev.ngocthanhgl.vikey.lib.ext.Extension
import dev.ngocthanhgl.vikey.lib.ext.ExtensionMaintainer
import dev.ngocthanhgl.vikey.lib.ext.ExtensionMeta
import dev.ngocthanhgl.vikey.lib.io.FlorisRef
import dev.ngocthanhgl.vikey.lib.util.launchUrl
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.stringRes

@Composable
fun ExtensionViewScreen(id: String) {
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    val ext = extensionManager.getExtensionById(id)
    if (ext != null) {
        ViewScreen(ext)
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ViewScreen(ext: Extension) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    var extToDelete by remember { mutableStateOf<Extension?>(null) }

    SettingsScaffold(title = ext.meta.title) {
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
            ext.meta.description?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MetaRowScrollable(label = stringRes(R.string.ext__meta__maintainers)) {
                ext.meta.maintainers.forEachIndexed { index, maintainer ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    ExtensionMaintainerChip(maintainer)
                }
            }
            MetaRowSimple(label = stringRes(R.string.ext__meta__id)) {
                Text(text = ext.meta.id)
            }
            MetaRowSimple(label = stringRes(R.string.ext__meta__version)) {
                Text(text = ext.meta.version)
            }
            if (!ext.meta.keywords.isNullOrEmpty()) {
                MetaRowScrollable(label = stringRes(R.string.ext__meta__keywords)) {
                    val localKeywords = ext.meta.keywords!!
                    localKeywords.forEachIndexed { index, keyword ->
                        if (index > 0) Spacer(Modifier.width(8.dp))
                        ExtensionKeywordChip(keyword)
                    }
                }
            }
            if (!ext.meta.homepage.isNullOrBlank()) {
                MetaRowSimple(label = stringRes(R.string.ext__meta__homepage)) {
                    Text(
                        text = FlorisRef.fromUrl(ext.meta.homepage!!).authority,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.launchUrl(ext.meta.homepage!!)
                        },
                    )
                }
            }
            if (!ext.meta.issueTracker.isNullOrBlank()) {
                MetaRowSimple(label = stringRes(R.string.ext__meta__issue_tracker)) {
                    Text(
                        text = FlorisRef.fromUrl(ext.meta.issueTracker!!).authority,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.launchUrl(ext.meta.issueTracker!!)
                        },
                    )
                }
            }
            MetaRowSimple(label = stringRes(R.string.ext__meta__license)) {
                Text(text = ext.meta.license)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            if (extensionManager.canDelete(ext)) {
                OutlinedButton(
                    onClick = { extToDelete = ext },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(text = stringRes(R.string.action__delete))
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    navController.navigate(Routes.Ext.Export(ext.meta.id))
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(text = stringRes(R.string.action__export))
            }
        }

        when (ext) {
            is ThemeExtension -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_theme),
                    components = ext.themes,
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        meta = ext.meta,
                        component = component,
                    )
                }
            }
            is LanguagePackExtension -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_language_pack),
                    components = ext.items,
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        meta = ext.meta,
                        component = component,
                    )
                }
            }
            else -> { }
        }

        if (extToDelete != null) {
            AlertDialog(
                onDismissRequest = { extToDelete = null },
                title = { Text(stringRes(R.string.action__delete_confirm_title)) },
                text = {
                    Text(stringRes(R.string.action__delete_confirm_message, "name" to extToDelete!!.meta.title))
                },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching {
                            extensionManager.delete(extToDelete!!)
                        }.onSuccess {
                            navController.popBackStack()
                        }.onFailure { error ->
                            context.showLongToastSync(
                                R.string.error__snackbar_message,
                                "error_message" to error.localizedMessage,
                            )
                        }
                        extToDelete = null
                    }) {
                        Text(stringRes(R.string.action__delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { extToDelete = null }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun MetaRowSimple(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (showDividerAbove) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 24.dp),
        )
        content()
    }
}

@Composable
private fun MetaRowScrollable(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (showDividerAbove) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 24.dp),
        )
        Row(
            modifier = Modifier
                .weight(1.0f, fill = false)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PreviewExtensionViewerScreen() {
    val testExtension = ThemeExtension(
        meta = ExtensionMeta(
            id = "com.example.theme.test",
            version = "2.4.3",
            title = "Test theme",
            description = "This is a test theme to preview the extension viewer screen UI.",
            keywords = listOf("Beach", "Sea", "Sun"),
            homepage = "https://example.com",
            issueTracker = "https://git.example.com/issues",
            maintainers = listOf(
                "Max Mustermann <max.mustermann@example.com> (maxmustermann.example.com)",
            ).map { ExtensionMaintainer.fromOrTakeRaw(it) },
            license = "apache-2.0",
        ),
        dependencies = null,
        themes = listOf(
            ThemeExtensionComponentImpl(id = "test", label = "Test", authors = listOf(), stylesheetPath = "test.json"),
        ),
    )
    ViewScreen(ext = testExtension)
}
