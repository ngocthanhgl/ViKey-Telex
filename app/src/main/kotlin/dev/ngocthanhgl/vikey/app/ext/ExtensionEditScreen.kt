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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.settings.advanced.RadioListItem
import dev.ngocthanhgl.vikey.app.settings.theme.DialogProperty
import dev.ngocthanhgl.vikey.app.settings.theme.PrettyPrintConfig
import dev.ngocthanhgl.vikey.app.settings.theme.ThemeEditorScreen
import dev.ngocthanhgl.vikey.cacheManager
import dev.ngocthanhgl.vikey.extensionManager
import dev.ngocthanhgl.vikey.ime.keyboard.KeyboardExtension
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtension
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionComponent
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionComponentEditor
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionComponentImpl
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionEditor
import dev.ngocthanhgl.vikey.lib.ValidationResult
import dev.ngocthanhgl.vikey.lib.cache.CacheManager
import dev.ngocthanhgl.vikey.lib.compose.FlorisUnsavedChangesDialog
import dev.ngocthanhgl.vikey.lib.compose.Validation
import dev.ngocthanhgl.vikey.lib.ext.Extension
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponent
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponentName
import dev.ngocthanhgl.vikey.lib.ext.ExtensionDefaults
import dev.ngocthanhgl.vikey.lib.ext.ExtensionEditor
import dev.ngocthanhgl.vikey.lib.ext.ExtensionJsonConfig
import dev.ngocthanhgl.vikey.lib.ext.ExtensionMaintainer
import dev.ngocthanhgl.vikey.lib.ext.ExtensionManager
import dev.ngocthanhgl.vikey.lib.ext.ExtensionMeta
import dev.ngocthanhgl.vikey.lib.ext.ExtensionValidation
import dev.ngocthanhgl.vikey.lib.ext.validate
import dev.ngocthanhgl.vikey.lib.io.FlorisRef
import dev.ngocthanhgl.vikey.lib.io.ZipUtils
import dev.ngocthanhgl.vikey.lib.rememberValidationResult
import dev.ngocthanhgl.vikey.themeManager
import java.io.File
import java.util.*
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson
import kotlin.reflect.KClass

private val TextFieldVerticalPadding = 8.dp
private val MetaDataContentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)

private const val AnimationDuration = 300

private val ActionScreenEnterTransition = fadeIn(tween(AnimationDuration))
private val ActionScreenExitTransition = fadeOut(tween(AnimationDuration))

sealed class EditorAction {
    object ManageMetaData : EditorAction()
    object ManageDependencies : EditorAction()
    object ManageFiles : EditorAction()
    data class CreateComponent<T : ExtensionComponent>(val type: KClass<T>) : EditorAction()
    data class ManageComponent(val editor: ExtensionComponent) : EditorAction()
}

@Composable
fun ExtensionEditScreen(id: String, createSerialType: String?) {
    val context = LocalContext.current
    val cacheManager by context.cacheManager()
    val extensionManager by context.extensionManager()

    @Suppress("unchecked_cast")
    fun <W : CacheManager.ExtEditorWorkspace<T>, T : ExtensionEditor> getOrCreateWorkspace(
        uuid: String,
        container: CacheManager.WorkspacesContainer<W>,
        ext: Extension,
    ): W {
        val workspace = container.getWorkspaceByUuid(uuid)
        return workspace ?: container.new(uuid).also { newWorkspace ->
            val sourceRef = ext.sourceRef
            if (createSerialType == null) {
                checkNotNull(sourceRef) { "Extension source ref must not be null" }
                ZipUtils.unzip(context, sourceRef, newWorkspace.extDir)
            }
            newWorkspace.ext = ext
            newWorkspace.editor = ext.edit() as? T
        }
    }

    val ext = extensionManager.getExtensionById(id) ?: remember {
        val meta = ExtensionMeta(
            id = ExtensionDefaults.createLocalId("themes", System.currentTimeMillis().toString()),
            version = "0.0.0",
            title = "My themes",
            maintainers = listOf(ExtensionMaintainer(name = "Local")),
            license = "(none specified)",
        )
        when (createSerialType) {
            ThemeExtension.SERIAL_TYPE -> ThemeExtension(meta, null, emptyList())
            else -> null
        }
    }
    if (ext != null) {
        val uuid = rememberSaveable { UUID.randomUUID().toString() }
        val cacheWorkspace = remember {
            runCatching {
                when (ext) {
                    is ThemeExtension -> {
                        getOrCreateWorkspace(uuid, cacheManager.themeExtEditor, ext)
                    }
                    else -> null
                }
            }
        }
        cacheWorkspace.onSuccess { workspace ->
            if (workspace?.editor != null) {
                ExtensionEditScreenSheetSwitcher(workspace, isCreateExt = createSerialType != null)
            } else {
                ExtensionNotFoundScreen(id = id)
            }
        }.onFailure { error ->
            Text(text = remember(error) { error.stackTraceToString() })
        }
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ExtensionEditScreenSheetSwitcher(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    isCreateExt: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        EditScreen(workspace, isCreateExt)
        AnimatedVisibility(
            visible = workspace.currentAction != null,
            enter = ActionScreenEnterTransition,
            exit = ActionScreenExitTransition,
        ) {
            when (val action = workspace.currentAction) {
                is EditorAction.ManageMetaData -> {
                    ManageMetaDataScreen(workspace, isCreateExt)
                }
                is EditorAction.ManageDependencies -> {
                    ManageDependenciesScreen(workspace)
                }
                is EditorAction.ManageFiles -> {
                    ExtensionEditFilesScreen(workspace)
                }
                is EditorAction.CreateComponent<*> -> {
                    CreateComponentScreen(workspace, action.type)
                }
                is EditorAction.ManageComponent -> when (action.editor) {
                    is ThemeExtensionComponentEditor -> {
                        ThemeEditorScreen(workspace, action.editor)
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    isCreateExt: Boolean,
) {
    val extEditor = workspace.editor ?: return
    val context = LocalContext.current
    val navController = LocalNavController.current
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showInvalidMetadataDialog by remember { mutableStateOf(false) }

    val title = stringRes(if (isCreateExt) {
        when (workspace.ext) {
            is KeyboardExtension -> R.string.ext__editor__title_create_keyboard
            is ThemeExtension -> R.string.ext__editor__title_create_theme
            else -> R.string.ext__editor__title_create_any
        }
    } else {
        when (workspace.ext) {
            is KeyboardExtension -> R.string.ext__editor__title_edit_keyboard
            is ThemeExtension -> R.string.ext__editor__title_edit_theme
            else -> R.string.ext__editor__title_edit_any
        }
    })

    fun handleBackPress() {
        if (workspace.isModified) {
            showUnsavedChangesDialog = true
        } else {
            workspace.close()
            navController.popBackStack()
        }
    }

    fun handleSave() {
        if (!extEditor.meta.validate()) {
            showUnsavedChangesDialog = false
            showInvalidMetadataDialog = true
            return
        }
        val manifest = extEditor.build()
        workspace.saverDir.deleteContentsRecursively()
        val manifestFile = workspace.saverDir.subFile(ExtensionDefaults.MANIFEST_FILE_NAME)
        manifestFile.writeJson(manifest, ExtensionJsonConfig)
        when (extEditor) {
            is ThemeExtensionEditor -> {
                val fonts = workspace.extDir.subDir("fonts")
                if (fonts.exists()) {
                    fonts.copyRecursively(workspace.saverDir.subDir("fonts"), overwrite = true)
                }
                val images = workspace.extDir.subDir("images")
                if (images.exists()) {
                    images.copyRecursively(workspace.saverDir.subDir("images"), overwrite = true)
                }
                for (theme in extEditor.themes) {
                    val stylesheetFile = workspace.saverDir.subFile(theme.stylesheetPath())
                    stylesheetFile.parentFile?.mkdirs()
                    val stylesheetEditor = theme.stylesheetEditor
                    if (stylesheetEditor != null) {
                        runCatching {
                            val stylesheet = stylesheetEditor.build().toJson(PrettyPrintConfig).getOrThrow()
                            stylesheetFile.writeText(stylesheet)
                        }.onFailure {
                            context.showLongToastSync(it.message.toString())
                            return
                        }
                    } else {
                        val unmodifiedStylesheetFile = workspace.extDir.subFile(theme.stylesheetPath())
                        if (unmodifiedStylesheetFile.exists()) {
                            unmodifiedStylesheetFile.copyTo(stylesheetFile, overwrite = true)
                        }
                    }
                }
            }
            else -> { }
        }
        val flexArchiveName = ExtensionDefaults.createFlexName(extEditor.meta.id)
        val flexArchiveFile = workspace.dir.subFile(flexArchiveName)
        ZipUtils.zip(workspace.saverDir, flexArchiveFile)
        val sourceRef = if (isCreateExt) {
            FlorisRef.internal(ExtensionManager.IME_THEME_PATH).subRef(flexArchiveName)
        } else {
            workspace.ext!!.sourceRef!!
        }
        flexArchiveFile.copyTo(sourceRef.absoluteFile(context), overwrite = true)
        workspace.close()
        navController.popBackStack()
    }

    BackHandler {
        handleBackPress()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
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
            androidx.compose.material3.Surface(
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
                    TextButton(onClick = { handleBackPress() }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { handleSave() }) {
                        Text(stringRes(R.string.action__save))
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
                EditorMenuRow(
                    icon = Icons.Default.Code,
                    title = stringRes(R.string.ext__editor__metadata__title),
                    onClick = { workspace.currentAction = EditorAction.ManageMetaData },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                EditorMenuRow(
                    icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                    title = stringRes(R.string.ext__editor__dependencies__title),
                    onClick = { workspace.currentAction = EditorAction.ManageDependencies },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                EditorMenuRow(
                    icon = ImageVector.vectorResource(R.drawable.ic_file_blank),
                    title = stringRes(R.string.ext__editor__files__title),
                    onClick = { workspace.currentAction = EditorAction.ManageFiles },
                )
            }

            when (extEditor) {
                is ThemeExtensionEditor -> {
                    ExtensionComponentListView(
                        title = stringRes(R.string.ext__meta__components_theme),
                        components = extEditor.themes,
                        onCreateBtnClick = {
                            workspace.currentAction = EditorAction.CreateComponent(ThemeExtensionComponent::class)
                        },
                    ) { component ->
                        ExtensionComponentView(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            meta = extEditor.meta,
                            component = component,
                            onDeleteBtnClick = { workspace.update { extEditor.themes.remove(component) } },
                            onEditBtnClick = { workspace.currentAction = EditorAction.ManageComponent(component) },
                        )
                    }
                }
                else -> { }
            }

            if (showUnsavedChangesDialog) {
                AlertDialog(
                    onDismissRequest = { showUnsavedChangesDialog = false },
                    title = { Text(stringRes(R.string.action__discard_confirm_title)) },
                    text = { Text(stringRes(R.string.action__discard_confirm_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            handleSave()
                            showUnsavedChangesDialog = false
                        }) {
                            Text(stringRes(R.string.action__save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            navController.popBackStack()
                            showUnsavedChangesDialog = false
                        }) {
                            Text(stringRes(R.string.action__discard))
                        }
                    },
                    neutralButton = {
                        TextButton(onClick = { showUnsavedChangesDialog = false }) {
                            Text(stringRes(R.string.action__cancel))
                        }
                    },
                )
            }

            if (showInvalidMetadataDialog) {
                AlertDialog(
                    onDismissRequest = { showInvalidMetadataDialog = false },
                    title = { Text(stringRes(R.string.ext__editor__metadata__title_invalid)) },
                    text = { Text(stringRes(R.string.ext__editor__metadata__message_invalid)) },
                    confirmButton = {
                        TextButton(onClick = { showInvalidMetadataDialog = false }) {
                            Text(stringRes(R.string.action__ok))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageMetaDataScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    isCreateExt: Boolean,
) {
    val meta = workspace.editor?.meta ?: return
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    var id by rememberSaveable { mutableStateOf(meta.id) }
    val idValidation = rememberValidationResult(ExtensionValidation.MetaId, id)
    var version by rememberSaveable { mutableStateOf(meta.version) }
    val versionValidation = rememberValidationResult(ExtensionValidation.MetaVersion, version)
    var title by rememberSaveable { mutableStateOf(meta.title) }
    val titleValidation = rememberValidationResult(ExtensionValidation.MetaTitle, title)
    var description by rememberSaveable { mutableStateOf(meta.description ?: "") }
    var keywords by rememberSaveable { mutableStateOf(meta.keywords?.joinToString("\n") ?: "") }
    var homepage by rememberSaveable { mutableStateOf(meta.homepage ?: "") }
    var issueTracker by rememberSaveable { mutableStateOf(meta.issueTracker ?: "") }
    var maintainers by rememberSaveable { mutableStateOf(meta.maintainers.joinToString("\n")) }
    val maintainersValidation = rememberValidationResult(ExtensionValidation.MetaMaintainers, maintainers)
    var license by rememberSaveable { mutableStateOf(meta.license) }
    val licenseValidation = rememberValidationResult(ExtensionValidation.MetaLicense, license)

    fun handleBackPress() { workspace.currentAction = null }

    fun handleApply() {
        val invalid = idValidation.isInvalid() ||
            versionValidation.isInvalid() ||
            titleValidation.isInvalid() ||
            maintainersValidation.isInvalid() ||
            licenseValidation.isInvalid()
        if (invalid) {
            showValidationErrors = true
        } else {
            workspace.update {
                workspace.editor?.meta = ExtensionMeta(
                    id = id.trim(),
                    version = version.trim(),
                    title = title.trim(),
                    description = description.trim().takeIf { it.isNotBlank() },
                    keywords = keywords.lines().map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() },
                    homepage = homepage.trim().takeIf { it.isNotBlank() },
                    issueTracker = issueTracker.trim().takeIf { it.isNotBlank() },
                    maintainers = maintainers.lines().map { it.trim() }.filter { it.isNotBlank() }
                        .map { ExtensionMaintainer.fromOrTakeRaw(it) },
                    license = license.trim(),
                )
            }
            workspace.currentAction = null
        }
    }

    BackHandler { handleBackPress() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.ext__editor__metadata__title)) },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(
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
                    TextButton(onClick = { handleBackPress() }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { handleApply() }) {
                        Text(stringRes(R.string.action__apply))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MetaDataContentPadding),
        ) {
            EditorSheetTextField(
                enabled = isCreateExt,
                isRequired = true,
                value = id,
                onValueChange = { id = it },
                label = stringRes(R.string.ext__meta__id),
                showValidationError = showValidationErrors,
                validationResult = idValidation,
            )
            EditorSheetTextField(
                isRequired = true,
                value = version,
                onValueChange = { version = it },
                label = stringRes(R.string.ext__meta__version),
                showValidationError = showValidationErrors,
                validationResult = versionValidation,
            )
            EditorSheetTextField(
                isRequired = true,
                value = title,
                onValueChange = { title = it },
                label = stringRes(R.string.ext__meta__title),
                showValidationError = showValidationErrors,
                validationResult = titleValidation,
            )
            EditorSheetTextField(
                value = description,
                onValueChange = { description = it },
                label = stringRes(R.string.ext__meta__description),
            )
            EditorSheetTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = stringRes(R.string.ext__meta__keywords),
                singleLine = false,
            )
            EditorSheetTextField(
                value = homepage,
                onValueChange = { homepage = it },
                label = stringRes(R.string.ext__meta__homepage),
            )
            EditorSheetTextField(
                value = issueTracker,
                onValueChange = { issueTracker = it },
                label = stringRes(R.string.ext__meta__issue_tracker),
            )
            EditorSheetTextField(
                isRequired = true,
                value = maintainers,
                onValueChange = { maintainers = it },
                label = stringRes(R.string.ext__meta__maintainers),
                singleLine = false,
                showValidationError = showValidationErrors,
                validationResult = maintainersValidation,
            )
            EditorSheetTextField(
                isRequired = true,
                value = license,
                onValueChange = { license = it },
                label = stringRes(R.string.ext__meta__license),
                showValidationError = showValidationErrors,
                validationResult = licenseValidation,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageDependenciesScreen(workspace: CacheManager.ExtEditorWorkspace<*>) {
    val dependencyList = workspace.editor?.dependencies ?: return

    fun handleBackPress() { workspace.currentAction = null }

    BackHandler { handleBackPress() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.ext__editor__dependencies__title)) },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding),
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
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = """
                        Dependencies are currently not implemented, but are already somewhat
                        integrated as a placeholder for the future.
                    """.trimIndent().replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (dependencyList.isEmpty()) {
                Text(
                    text = "no deps found",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (dependency in dependencyList) {
                    Text(
                        text = dependency,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private enum class CreateFrom {
    EMPTY,
    EXISTING;
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : ExtensionComponent> CreateComponentScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    type: KClass<T>,
) {
    val context = LocalContext.current
    val extensionManager by context.extensionManager()
    val themeManager by context.themeManager()

    val title = stringRes(when (type) {
        ThemeExtensionComponent::class -> R.string.ext__editor__create_component__title_theme
        else -> R.string.ext__editor__create_component__title
    })

    var createFrom by rememberSaveable { mutableStateOf(CreateFrom.EXISTING) }
    val extId = workspace.editor?.meta?.id ?: "null"
    val components = remember<Map<ExtensionComponentName, ExtensionComponent>> {
        when (val editor = workspace.editor) {
            is ThemeExtensionEditor -> buildMap {
                for (theme in editor.themes) {
                    put(ExtensionComponentName(extId, theme.id), theme)
                }
                for ((componentName, theme) in themeManager.indexedThemeConfigs.value.first) {
                    if (componentName.extensionId != extId) {
                        put(componentName, theme)
                    }
                }
            }
            else -> emptyMap()
        }
    }
    var selectedComponentName by rememberSaveable(stateSaver = ExtensionComponentName.Saver) {
        mutableStateOf(null)
    }
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    var newId by rememberSaveable { mutableStateOf("") }
    val newIdValidation = rememberValidationResult(ExtensionValidation.ComponentId, newId)
    var newLabel by rememberSaveable { mutableStateOf("") }
    val newLabelValidation = rememberValidationResult(ExtensionValidation.ComponentLabel, newLabel)
    var newAuthors by rememberSaveable { mutableStateOf("") }
    val newAuthorsValidation = rememberValidationResult(ExtensionValidation.ComponentAuthors, newAuthors)

    fun handleBackPress() { workspace.currentAction = null }

    fun handleCreate() {
        val invalid = createFrom == CreateFrom.EMPTY && (newIdValidation.isInvalid() ||
            newLabelValidation.isInvalid() || newAuthorsValidation.isInvalid())
        if (invalid) {
            showValidationErrors = true
        } else {
            when (val editor = workspace.editor) {
                is ThemeExtensionEditor -> {
                    when (createFrom) {
                        CreateFrom.EMPTY -> {
                            if (editor.themes.any { it.id == newId.trim() }) {
                                context.showLongToastSync("A theme with this ID already exists!")
                            } else {
                                val componentEditor = ThemeExtensionComponentEditor(
                                    id = newId.trim(),
                                    label = newLabel.trim(),
                                    authors = newAuthors.lines().map { it.trim() }.filter { it.isNotBlank() },
                                )
                                editor.themes.add(componentEditor)
                                workspace.currentAction = null
                            }
                        }
                        CreateFrom.EXISTING -> {
                            val componentName = selectedComponentName ?: return
                            val componentId = if (editor.themes.any { it.id == componentName.componentId }) {
                                var suffix = 1
                                var tempId: String
                                do {
                                    tempId = "${componentName.componentId}_${suffix++}"
                                } while (editor.themes.any { it.id == tempId })
                                tempId
                            } else {
                                componentName.componentId
                            }
                            if (componentName.extensionId == extId) {
                                val srcComponent = editor.themes.find { it.id == componentName.componentId } ?: return
                                val componentEditor = ThemeExtensionComponentEditor(
                                    componentId, srcComponent.label, srcComponent.authors, srcComponent.isNightTheme, stylesheetPath = "",
                                ).also { it.stylesheetEditor = srcComponent.stylesheetEditor }
                                if (componentEditor.stylesheetEditor != null) {
                                    val stylesheetFile = workspace.extDir.subFile(componentEditor.stylesheetPath())
                                    stylesheetFile.parentFile?.mkdirs()
                                    val stylesheet = componentEditor.stylesheetEditor!!.build().toJson(PrettyPrintConfig).getOrThrow()
                                    stylesheetFile.writeText(stylesheet)
                                    componentEditor.stylesheetEditor = null
                                } else {
                                    val srcStylesheetFile = workspace.extDir.subFile(component.stylesheetPath())
                                    val dstStylesheetFile = workspace.extDir.subFile(componentEditor.stylesheetPath())
                                    dstStylesheetFile.parentFile?.mkdirs()
                                    srcStylesheetFile.copyTo(dstStylesheetFile, overwrite = true)
                                }
                                editor.themes.add(componentEditor)
                            } else {
                                val component = themeManager.indexedThemeConfigs.value.first.get(componentName) ?: return
                                val componentEditor = (component as? ThemeExtensionComponentImpl)?.edit() ?: return
                                componentEditor.id = componentId
                                componentEditor.stylesheetPath = ""
                                val externalExt = extensionManager.getExtensionById(componentName.extensionId) ?: return
                                val stylesheetJson = ZipUtils.readFileFromArchive(
                                    context, externalExt.sourceRef!!, component.stylesheetPath()
                                ).getOrNull() ?: return
                                val dstStylesheetFile = workspace.extDir.subFile(componentEditor.stylesheetPath())
                                dstStylesheetFile.parentFile?.mkdirs()
                                dstStylesheetFile.writeText(stylesheetJson)
                                editor.themes.add(componentEditor)
                            }
                            workspace.currentAction = null
                        }
                    }
                }
            }
        }
    }

    fun hasSufficientInfoForCreating(): Boolean {
        return when (createFrom) {
            CreateFrom.EMPTY -> newId.isNotBlank() && newLabel.isNotBlank() && newAuthors.isNotBlank()
            CreateFrom.EXISTING -> components.containsKey(selectedComponentName)
        }
    }

    BackHandler { handleBackPress() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(
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
                    TextButton(onClick = { handleBackPress() }) {
                        Text(stringRes(R.string.action__cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { handleCreate() },
                        enabled = hasSufficientInfoForCreating(),
                    ) {
                        Text(stringRes(R.string.action__create))
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
                RadioListItem(
                    onClick = { createFrom = CreateFrom.EXISTING },
                    selected = createFrom == CreateFrom.EXISTING,
                    text = stringRes(R.string.ext__editor__create_component__from_existing),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                RadioListItem(
                    onClick = { createFrom = CreateFrom.EMPTY },
                    selected = createFrom == CreateFrom.EMPTY,
                    text = stringRes(R.string.ext__editor__create_component__from_empty),
                )
            }

            if (createFrom == CreateFrom.EXISTING) {
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
                    components.forEach { (componentName, component) ->
                        RadioListItem(
                            onClick = { selectedComponentName = componentName },
                            selected = selectedComponentName == componentName,
                            text = component.label,
                            secondaryText = componentName.toString(),
                        )
                    }
                }
            } else if (createFrom == CreateFrom.EMPTY) {
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
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = stringRes(R.string.ext__editor__create_component__from_empty_warning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DialogProperty(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringRes(R.string.ext__meta__id),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = newId,
                        onValueChange = { newId = it },
                        singleLine = true,
                    )
                    Validation(showValidationErrors, newIdValidation)
                }
                DialogProperty(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringRes(R.string.ext__meta__label),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        singleLine = true,
                    )
                    Validation(showValidationErrors, newLabelValidation)
                }
                DialogProperty(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringRes(R.string.ext__meta__authors),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = newAuthors,
                        onValueChange = { newAuthors = it },
                    )
                    Validation(showValidationErrors, newAuthorsValidation)
                }
            }
        }
    }
}

@Composable
private fun EditorSheetTextField(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRequired: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    showValidationError: Boolean = false,
    validationResult: ValidationResult? = null,
) {
    Column(modifier = Modifier.padding(vertical = TextFieldVerticalPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = TextFieldVerticalPadding),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            if (isRequired) {
                Text(
                    text = "*",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        OutlinedTextField(
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
        )
        Validation(showValidationError, validationResult)
    }
}
