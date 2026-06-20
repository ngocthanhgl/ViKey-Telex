package dev.ngocthanhgl.vikey.app.settings.advanced

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.ngocthanhgl.vikey.BuildConfig
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceModel
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.cacheManager
import dev.ngocthanhgl.vikey.clipboardManager
import dev.ngocthanhgl.vikey.ime.clipboard.provider.ClipboardFileStorage
import dev.ngocthanhgl.vikey.ime.clipboard.provider.ClipboardItem
import dev.ngocthanhgl.vikey.ime.clipboard.provider.ItemType
import dev.ngocthanhgl.vikey.lib.cache.CacheManager
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.ngocthanhgl.vikey.lib.ext.ExtensionManager
import dev.ngocthanhgl.vikey.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.florisboard.lib.android.readToFile
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisCardDefaults
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisOutlinedButton
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

object Restore {
    const val MIN_VERSION_CODE = 64
    const val PACKAGE_NAME = "dev.ngocthanhgl.vikey"
    const val BACKUP_ARCHIVE_FILE_NAME = "backup.zip"
}

@Composable
fun RestoreScreen() = FlorisScreen {
    title = stringRes(R.string.backup_and_restore__restore__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()

    val restoreFilesSelector = remember { Backup.FilesSelector() }
    var importStrategy by remember { mutableStateOf(ImportStrategy.Merge) }
    val restoreScope = remember { CoroutineScope(Dispatchers.Main) }
    var restoreWorkspace by remember {
        mutableStateOf<CacheManager.BackupAndRestoreWorkspace?>(null)
    }

    val restoreDataFromFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                restoreWorkspace?.close()
                restoreWorkspace = null
                val workspace = cacheManager.backupAndRestore.new()
                workspace.zipFile = workspace.inputDir.subFile(Restore.BACKUP_ARCHIVE_FILE_NAME)
                context.contentResolver.readToFile(uri, workspace.zipFile)
                ZipUtils.unzip(workspace.zipFile, workspace.outputDir)
                workspace.metadata = try {
                    workspace.outputDir.subFile(Backup.METADATA_JSON_NAME).readJson()
                } catch (e: FileNotFoundException) {
                    error("Invalid archive: either backup_metadata.json is missing or file is not a ZIP archive.")
                }
                workspace.restoreWarningId = when {
                    workspace.metadata.versionCode != BuildConfig.VERSION_CODE -> {
                        R.string.backup_and_restore__restore__metadata_warn_different_version
                    }
                    !workspace.metadata.packageName.startsWith(Restore.PACKAGE_NAME) -> {
                        R.string.backup_and_restore__restore__metadata_warn_different_vendor
                    }
                    else -> null
                }
                workspace.restoreErrorId = when {
                    workspace.metadata.packageName.isBlank() || workspace.metadata.versionCode < Restore.MIN_VERSION_CODE -> {
                        R.string.backup_and_restore__restore__metadata_error_invalid_metadata
                    }
                    else -> null
                }
                restoreWorkspace = workspace
            }.onFailure { error ->
                context.showLongToastSync(
                    R.string.backup_and_restore__restore__failure,
                    "error_message" to error.localizedMessage,
                )
            }
        },
    )

    suspend fun performRestore() {
        val workspace = restoreWorkspace!!
        val shouldReset = importStrategy == ImportStrategy.Erase
        if (restoreFilesSelector.jetprefDatastore) {
            val file = workspace.outputDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
            if (file.exists()) {
                val fileBasedStorage = FileBasedStorage(file.path)
                FlorisPreferenceStore.import(importStrategy, fileBasedStorage).getOrThrow()
            }
        }
        val workspaceFilesDir = workspace.outputDir.subDir("files")
        if (restoreFilesSelector.imeKeyboard) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            if (shouldReset) {
                dstDir.deleteContentsRecursively()
            }
            if (srcDir.exists()) {
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        if (restoreFilesSelector.imeTheme) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH)
            if (shouldReset) {
                dstDir.deleteContentsRecursively()
            }
            if (srcDir.exists()) {
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        val clipboardManager = context.clipboardManager().value
        if (shouldReset) {
            clipboardManager.clearFullHistory()
            ClipboardFileStorage.resetClipboardFileStorage(context)
        }

        if (restoreFilesSelector.provideClipboardItems()) {
            val clipboardFilesDir = workspace.outputDir.subDir("clipboard")

            if (restoreFilesSelector.clipboardTextItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                if (clipboardItems.exists()) {
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    clipboardManager.restoreHistory(items = clipboardItemsList.filter { it.type == ItemType.TEXT })
                }
            }
            if (restoreFilesSelector.clipboardImageItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                if (clipboardItems.exists()) {
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    for (item in clipboardItemsList.filter { it.type == ItemType.IMAGE }) {
                        ClipboardFileStorage.insertFileFromBackupIfNotExisting(
                            context,
                            clipboardFilesDir.subFile(
                                relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/${
                                    item.uri!!.path!!.split('/').last()
                                }"
                            )
                        )
                    }
                    clipboardManager.restoreHistory(items = clipboardItemsList.filter { it.type == ItemType.IMAGE })
                }
            }
            if (restoreFilesSelector.clipboardVideoItems) {
                val clipboardItems = clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                if (clipboardItems.exists()) {
                    val clipboardItemsList = clipboardItems.readJson<List<ClipboardItem>>()
                    for (item in clipboardItemsList.filter { it.type == ItemType.VIDEO }) {
                        ClipboardFileStorage.insertFileFromBackupIfNotExisting(
                            context,
                            clipboardFilesDir.subFile(
                                relPath = "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/${
                                    item.uri!!.path!!.split('/').last()
                                }"
                            )
                        )
                    }
                    clipboardManager.restoreHistory(items = clipboardItemsList.filter { it.type == ItemType.VIDEO })
                }
            }
        }
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                onClick = {
                    restoreWorkspace?.close()
                    navController.navigateUp()
                },
                text = stringRes(R.string.action__cancel),
            )
            ButtonBarButton(
                onClick = {
                    restoreScope.launch(Dispatchers.Main) {
                        try {
                            performRestore()
                            context.showLongToast(R.string.backup_and_restore__restore__success)
                            navController.navigateUp()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            context.showLongToast(
                                R.string.backup_and_restore__restore__failure,
                                "error_message" to e.localizedMessage,
                            )
                        }
                    }
                },
                text = stringRes(R.string.action__restore),
                enabled = restoreWorkspace != null && restoreWorkspace?.restoreErrorId == null,
            )
        }
    }

    content {
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.backup_and_restore__restore__mode),
        ) {
            M3RadioListItem(
                onClick = { importStrategy = ImportStrategy.Merge },
                selected = importStrategy == ImportStrategy.Merge,
                text = stringRes(R.string.backup_and_restore__restore__mode_merge),
            )
            M3RadioListItem(
                onClick = { importStrategy = ImportStrategy.Erase },
                selected = importStrategy == ImportStrategy.Erase,
                text = stringRes(R.string.backup_and_restore__restore__mode_erase_and_overwrite),
            )
        }
        FlorisOutlinedButton(
            onClick = {
                runCatching {
                    restoreDataFromFileSystemLauncher.launch("*/*")
                }.onFailure { error ->
                    context.showLongToastSync(
                        R.string.backup_and_restore__restore__failure,
                        "error_message" to error.localizedMessage,
                    )
                }
            },
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally),
            text = stringRes(R.string.action__select_file),
        )
        val workspace = restoreWorkspace
        if (workspace == null) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                text = stringRes(R.string.state__no_file_selected),
                fontStyle = FontStyle.Italic,
            )
        } else {
            FlorisOutlinedBox(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                title = stringRes(R.string.backup_and_restore__restore__metadata),
            ) {
                M3InfoListItem(
                    icon = Icons.Default.Code,
                    text = workspace.metadata.packageName,
                )
                M3InfoListItem(
                    icon = Icons.Outlined.Info,
                    text = "${workspace.metadata.versionName} (${workspace.metadata.versionCode})",
                )
                M3InfoListItem(
                    icon = Icons.Default.Schedule,
                    text = remember(workspace.metadata.timestamp) {
                        val formatter = DateFormat.getDateTimeInstance()
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        calendar.timeInMillis = workspace.metadata.timestamp
                        formatter.format(calendar.time)
                    },
                )
                if (workspace.restoreErrorId != null) {
                    Column(modifier = Modifier.padding(FlorisCardDefaults.ContentPadding)) {
                        HorizontalDivider()
                        Text(
                            text = stringRes(workspace.restoreErrorId!!),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                } else if (workspace.restoreWarningId != null) {
                    Column(modifier = Modifier.padding(FlorisCardDefaults.ContentPadding)) {
                        HorizontalDivider()
                        Text(
                            text = stringRes(workspace.restoreWarningId!!),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
            if (workspace.restoreErrorId == null) {
                M3BackupFilesSelector(
                    filesSelector = restoreFilesSelector,
                    title = stringRes(R.string.backup_and_restore__restore__files),
                )
            }
        }
    }
}

@Composable
internal fun M3InfoListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
