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

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.cacheManager
import dev.ngocthanhgl.vikey.extensionManager
import dev.ngocthanhgl.vikey.ime.keyboard.KeyboardExtension
import dev.ngocthanhgl.vikey.ime.nlp.LanguagePackExtension
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtension
import dev.ngocthanhgl.vikey.lib.NATIVE_NULLPTR
import dev.ngocthanhgl.vikey.lib.cache.CacheManager
import dev.ngocthanhgl.vikey.lib.io.FileRegistry
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.resultOk

enum class ExtensionImportScreenType(
    val id: String,
    @StringRes val titleResId: Int,
    val supportedFiles: List<FileRegistry.Entry>,
) {
    EXT_ANY(
        id = "ext-any",
        titleResId = R.string.ext__import__ext_any,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_KEYBOARD(
        id = "ext-keyboard",
        titleResId = R.string.ext__import__ext_keyboard,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__import__ext_theme,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    ),
    EXT_LANGUAGEPACK(
        id = "ext-languagepack",
        titleResId = R.string.ext__import__ext_languagepack,
        supportedFiles = listOf(FileRegistry.FlexExtension),
    );
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionImportScreen(type: ExtensionImportScreenType, initUuid: String?) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val extensionManager by context.extensionManager()

    fun getSkipReason(fileInfo: CacheManager.FileInfo): Int {
        return when {
            !FileRegistry.matchesFileFilter(fileInfo, type.supportedFiles) -> {
                R.string.ext__import__file_skip_unsupported
            }
            fileInfo.ext != null -> {
                val ext = fileInfo.ext
                if (extensionManager.getExtensionById(ext.meta.id)?.sourceRef?.isAssets == true) {
                    R.string.ext__import__file_skip_ext_core
                } else {
                    NATIVE_NULLPTR.toInt()
                }
            }
            else -> {
                R.string.ext__import__file_skip_ext_corrupted
            }
        }
    }

    fun Result<CacheManager.ImporterWorkspace>.mapSkipReasons(): Result<CacheManager.ImporterWorkspace> {
        return this.map { workspace ->
            workspace.inputFileInfos.forEach { fileInfo ->
                fileInfo.skipReason = getSkipReason(fileInfo)
            }
            workspace
        }
    }

    var importResult by remember(initUuid) {
        val workspace = initUuid?.let { cacheManager.importer.getWorkspaceByUuid(it) }
            ?.let { resultOk(it) }
            ?.mapSkipReasons()
        mutableStateOf(workspace)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uriList ->
            if (uriList.isEmpty()) return@rememberLauncherForActivityResult
            importResult?.getOrNull()?.close()
            importResult = runCatching { cacheManager.readFromUriIntoCache(uriList) }.mapSkipReasons()
        },
    )

    val importEnabled = remember(importResult) {
        importResult?.getOrNull()?.takeIf { workspace ->
            workspace.inputFileInfos.any { it.skipReason == NATIVE_NULLPTR.toInt() }
        } != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(type.titleResId)) },
                navigationIcon = {
                    IconButton(onClick = {
                        importResult?.getOrNull()?.close()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        importResult?.getOrNull()?.close()
                        navController.popBackStack()
                    }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val workspace = importResult!!.getOrThrow()
                            runCatching {
                                for (fileInfo in workspace.inputFileInfos) {
                                    if (fileInfo.skipReason != NATIVE_NULLPTR.toInt()) continue
                                    val ext = fileInfo.ext
                                    when (type) {
                                        ExtensionImportScreenType.EXT_ANY -> {
                                            ext?.let { extensionManager.import(it) }
                                        }
                                        ExtensionImportScreenType.EXT_KEYBOARD -> {
                                            ext.takeIf { it is KeyboardExtension }?.let { extensionManager.import(it) }
                                        }
                                        ExtensionImportScreenType.EXT_THEME -> {
                                            ext.takeIf { it is ThemeExtension }?.let { extensionManager.import(it) }
                                        }
                                        ExtensionImportScreenType.EXT_LANGUAGEPACK -> {
                                            ext.takeIf { it is LanguagePackExtension }?.let { extensionManager.import(it) }
                                        }
                                    }
                                }
                            }.onSuccess {
                                workspace.close()
                                context.showLongToastSync(R.string.ext__import__success)
                                navController.popBackStack()
                            }.onFailure { error ->
                                context.showLongToastSync(R.string.ext__import__failure, "error_message" to error.localizedMessage)
                            }
                        },
                        enabled = importEnabled,
                    ) {
                        Text(stringRes(R.string.action__import))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (initUuid == null) {
                OutlinedButton(
                    onClick = { importLauncher.launch("*/*") },
                    modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally),
                ) {
                    Text(stringRes(R.string.action__select_files))
                }
            }

            val result = importResult
            when {
                result == null -> {
                    Text(
                        text = stringRes(R.string.state__no_files_selected),
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp),
                    )
                }
                result.isSuccess -> {
                    val workspace = result.getOrThrow()
                    for (fileInfo in workspace.inputFileInfos) {
                        FileInfoView(fileInfo)
                    }
                }
                result.isFailure -> {
                    Text(
                        text = stringRes(R.string.ext__import__error_unexpected_exception),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SelectionContainer {
                        Text(
                            text = result.exceptionOrNull()?.stackTraceToString() ?: "null",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoView(
    fileInfo: CacheManager.FileInfo,
) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = fileInfo.file.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = fileInfo.mediaType ?: "application/unknown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val grayColor = LocalContentColor.current.copy(alpha = 0.56f)
            val ext = fileInfo.ext
            Row {
                Text(
                    text = Formatter.formatShortFileSize(LocalContext.current, fileInfo.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = grayColor,
                )
                if (ext != null) {
                    Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = grayColor,
                    )
                    Text(
                        text = ext.meta.id,
                        style = MaterialTheme.typography.bodyMedium,
                        color = grayColor,
                    )
                    Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = grayColor,
                    )
                    Text(
                        text = ext.meta.version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = grayColor,
                    )
                }
            }
            if (ext != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ext.meta.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ext.meta.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val maintainers = remember(ext) {
                    ext.meta.maintainers.joinToString { it.name }
                }
                Text(
                    text = stringRes(R.string.ext__meta__maintainers_by, "maintainers" to maintainers),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                for (component in ext.components()) {
                    Text(
                        text = component.id,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (fileInfo.skipReason != NATIVE_NULLPTR.toInt()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = stringRes(R.string.ext__import__file_skip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringRes(fileInfo.skipReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
