package dev.ngocthanhgl.vikey.app.settings.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton

import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.apptheme.Shapes
import dev.ngocthanhgl.vikey.app.ext.ExtensionComponentView
import dev.ngocthanhgl.vikey.app.settings.components.M3Dropdown
import dev.ngocthanhgl.vikey.ime.theme.FlorisImeUi
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionComponent
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionComponentEditor
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionEditor
import dev.ngocthanhgl.vikey.ime.theme.ThemeManager
import dev.ngocthanhgl.vikey.ime.theme.extPreviewTheme
import dev.ngocthanhgl.vikey.lib.cache.CacheManager
import dev.ngocthanhgl.vikey.lib.compose.PreviewKeyboardPill
import dev.ngocthanhgl.vikey.lib.compose.Validation
import dev.ngocthanhgl.vikey.lib.compose.rememberPreviewFieldController
import dev.ngocthanhgl.vikey.lib.ext.ExtensionValidation
import dev.ngocthanhgl.vikey.lib.rememberValidationResult
import dev.ngocthanhgl.vikey.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.color.MaterialYouFlagsSaver
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.snygg.SnyggAnnotationRule
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggJsonConfiguration
import org.florisboard.lib.snygg.SnyggMultiplePropertySetsEditor
import org.florisboard.lib.snygg.SnyggRule
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.SnyggSinglePropertySetEditor
import org.florisboard.lib.snygg.SnyggSpec
import org.florisboard.lib.snygg.SnyggSpecDecl
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.SnyggStylesheetEditor
import org.florisboard.lib.snygg.ui.Saver

internal val PrettyPrintConfig = SnyggJsonConfiguration.of(
    prettyPrint = true,
    prettyPrintIndent = "  ",
)

private val LenientConfig = SnyggJsonConfiguration.of(
    ignoreMissingSchema = true,
    ignoreInvalidSchema = true,
    ignoreUnsupportedSchema = true,
    ignoreInvalidRules = true,
    ignoreInvalidProperties = true,
    ignoreInvalidValues = true,
)

private enum class StylesheetLoadingStrategy {
    TRY_LOAD_OR_ASK_ON_CONFLICT,
    TRY_LOAD_OR_EMPTY,
    TRY_LOAD_OR_PARSE_LENIENT;
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    editor: ThemeExtensionComponentEditor,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val themeManager by context.themeManager()

    val scope = rememberCoroutineScope()
    val previewFieldController = rememberPreviewFieldController().also { it.isVisible = true }

    var stylesheetLoadingStrategy by rememberSaveable {
        mutableStateOf(StylesheetLoadingStrategy.TRY_LOAD_OR_ASK_ON_CONFLICT)
    }
    var stylesheetEditorFailure by remember { mutableStateOf<Throwable?>(null) }
    val stylesheetEditor = remember(stylesheetLoadingStrategy) {
        editor.stylesheetEditor ?: run {
            stylesheetEditorFailure = null
            val stylesheetPath = editor.stylesheetPath()
            editor.stylesheetPathOnLoad = stylesheetPath
            val stylesheetFile = workspace.extDir.subFile(stylesheetPath)
            val stylesheetEditor = if (stylesheetFile.exists()) {
                try {
                    val stylesheetJson = stylesheetFile.readText()
                    val config = when (stylesheetLoadingStrategy) {
                        StylesheetLoadingStrategy.TRY_LOAD_OR_PARSE_LENIENT -> LenientConfig
                        else -> PrettyPrintConfig
                    }
                    SnyggStylesheet.fromJson(stylesheetJson, config).getOrThrow().edit(CustomRuleComparator)
                } catch (error: Throwable) {
                    stylesheetEditorFailure = when (stylesheetLoadingStrategy) {
                        StylesheetLoadingStrategy.TRY_LOAD_OR_ASK_ON_CONFLICT -> error
                        else -> null
                    }
                    SnyggStylesheetEditor(SnyggStylesheet.SCHEMA_V2, comparator = CustomRuleComparator)
                }
            } else {
                SnyggStylesheetEditor(SnyggStylesheet.SCHEMA_V2, comparator = CustomRuleComparator)
            }
            stylesheetEditor.rules.putIfAbsent(SnyggAnnotationRule.Defines, SnyggSinglePropertySetEditor())
            stylesheetEditor
        }.also { editor.stylesheetEditor = it }
    }

    val definedVariables = remember(stylesheetEditor.rules, workspace.version) {
        stylesheetEditor.rules.firstNotNullOfOrNull { (rule, propertySet) ->
            if (rule is SnyggAnnotationRule.Defines && propertySet is SnyggSinglePropertySetEditor) {
                propertySet.properties
            } else {
                null
            }
        } ?: emptyMap()
    }

    val fontNames = remember(stylesheetEditor.rules, workspace.version) {
        stylesheetEditor.rules.mapNotNull { (rule, _) ->
            if (rule is SnyggAnnotationRule.Font) {
                rule.fontName
            } else {
                null
            }
        }
    }

    val snyggLevel by prefs.theme.editorLevel.collectAsState()
    val colorRepresentation by prefs.theme.editorColorRepresentation.collectAsState()
    var snyggRuleToEdit by rememberSaveable(stateSaver = SnyggRule.Saver) { mutableStateOf(null) }
    var snyggPropertyToEdit by remember { mutableStateOf<PropertyInfo?>(null) }
    var snyggPropertySetForEditing = remember<SnyggSinglePropertySetEditor?> { null }
    var showEditComponentMetaDialog by rememberSaveable { mutableStateOf(false) }
    var showFineTuneDialog by rememberSaveable { mutableStateOf(false) }

    fun handleBackPress() {
        workspace.currentAction = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.ext__editor__edit_component__title_theme)) },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showFineTuneDialog = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
                text = { Text(text = stringRes(R.string.settings__theme_editor__add_rule)) },
                onClick = { snyggRuleToEdit = SnyggEmptyRuleForAdding },
            )
        },
        bottomBar = {
            PreviewKeyboardPill(previewFieldController)
        },
    ) { padding ->
        stylesheetEditorFailure?.let { failure ->
            AlertDialog(
                onDismissRequest = {
                    editor.stylesheetEditor = null
                    stylesheetLoadingStrategy = StylesheetLoadingStrategy.TRY_LOAD_OR_EMPTY
                },
                title = { Text(stringRes(R.string.settings__theme_editor__stylesheet_error_title)) },
                text = {
                    Column {
                        Text(
                            modifier = Modifier.padding(bottom = 8.dp),
                            text = failure.message.toString(),
                            fontStyle = FontStyle.Italic,
                        )
                        Text(
                            text = stringRes(R.string.settings__theme_editor__stylesheet_error_description),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        editor.stylesheetEditor = null
                        stylesheetLoadingStrategy = StylesheetLoadingStrategy.TRY_LOAD_OR_PARSE_LENIENT
                    }) {
                        Text(stringRes(R.string.action__yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        editor.stylesheetEditor = null
                        stylesheetLoadingStrategy = StylesheetLoadingStrategy.TRY_LOAD_OR_EMPTY
                    }) {
                        Text(stringRes(R.string.action__no))
                    }
                },
            )
        }

        BackHandler { handleBackPress() }

        LaunchedEffect(showEditComponentMetaDialog, showFineTuneDialog, snyggRuleToEdit, snyggPropertyToEdit) {
            val visible = showEditComponentMetaDialog || showFineTuneDialog ||
                snyggRuleToEdit != null || snyggPropertyToEdit != null
            if (visible) focusManager.clearFocus()
            else delay(250)
        }

        DisposableEffect(workspace.version) {
            themeManager.previewThemeInfo.value = ThemeManager.ThemeInfo.DEFAULT.copy(
                name = extPreviewTheme(System.currentTimeMillis().toString()),
                config = editor.build(),
                stylesheet = stylesheetEditor.build(),
                loadedDir = workspace.extDir,
            )
            onDispose { themeManager.previewThemeInfo.value = null }
        }

        val lazyListState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.padding(padding),
            state = lazyListState,
        ) {
            item {
                ExtensionComponentView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    meta = workspace.editor!!.meta,
                    component = editor,
                    onEditBtnClick = { showEditComponentMetaDialog = true },
                )
                if (stylesheetEditor.rules.isEmpty() ||
                    (stylesheetEditor.rules.size == 1 && stylesheetEditor.rules.all { (rule, _) -> rule == SnyggAnnotationRule.Defines })
                ) {
                    Text(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        text = stringRes(R.string.settings__theme_editor__no_rules_defined),
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            items(stylesheetEditor.rules.toList()) { (rule, propertySet) -> key(rule) {
                val propertySetSpec = SnyggSpec.propertySetSpecOf(rule)
                val isVariablesRule = rule == SnyggAnnotationRule.Defines

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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SnyggRuleRow(
                            rule = rule,
                            level = snyggLevel,
                            showEditBtn = !isVariablesRule,
                            onEditRuleBtnClick = { snyggRuleToEdit = rule },
                            onAddPropertyBtnClick = {
                                when(propertySet) {
                                    is SnyggMultiplePropertySetsEditor -> {
                                        workspace.update { propertySet.sets.add(SnyggSinglePropertySetEditor()) }
                                    }
                                    is SnyggSinglePropertySetEditor -> {
                                        snyggPropertySetForEditing = propertySet
                                        snyggPropertyToEdit = SnyggEmptyPropertyInfoForAdding.copy(rule = rule)
                                    }
                                }
                            },
                        )
                        if (isVariablesRule) {
                            Text(
                                modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
                                text = stringRes(R.string.snygg__rule_annotation__defines_description),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                            )
                        }

                        @Composable
                        fun SinglePropertySetEditor(
                            propertySet: SnyggSinglePropertySetEditor,
                        ) {
                            for ((propertyName, propertySpec) in propertySetSpec?.properties.orEmpty()) {
                                if (propertySpec.required && !propertySet.properties.containsKey(propertyName)) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                    ) {
                                        Text(
                                            modifier = Modifier.padding(12.dp),
                                            text = stringRes(R.string.theme_editor__required_property_missing, propertyName),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                            propertySet.properties.forEach { (propertyName, propertyValue) ->
                                Text(
                                    modifier = Modifier
                                        .clickable {
                                            snyggPropertySetForEditing = propertySet
                                            snyggPropertyToEdit = PropertyInfo(rule, propertyName, propertyValue)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    text = context.translatePropertyName(propertyName, snyggLevel),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        when (propertySet) {
                            is SnyggSinglePropertySetEditor -> {
                                SinglePropertySetEditor(propertySet)
                            }
                            is SnyggMultiplePropertySetsEditor -> {
                                val sets = propertySet.sets
                                sets.forEachIndexed { propertySetIndex, ps ->
                                    key(ps.uuid) {
                                        ElevatedCard(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.elevatedCardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            ),
                                        ) {
                                            Row {
                                                Text(
                                                    stringRes(R.string.theme_editor__source_set),
                                                    modifier = Modifier.padding(start = 16.dp).align(Alignment.CenterVertically),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Spacer(Modifier.weight(1f))
                                                FlorisIconButton(
                                                    onClick = {
                                                        workspace.update {
                                                            if (propertySetIndex > 0) {
                                                                val s = sets.removeAt(propertySetIndex)
                                                                sets.add(propertySetIndex - 1, s)
                                                            }
                                                        }
                                                    },
                                                    icon = Icons.Rounded.KeyboardArrowUp,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                    enabled = propertySetIndex > 0,
                                                )
                                                FlorisIconButton(
                                                    onClick = {
                                                        workspace.update {
                                                            if (propertySetIndex + 1 < sets.size) {
                                                                val s = sets.removeAt(propertySetIndex)
                                                                sets.add(propertySetIndex + 1, s)
                                                            }
                                                        }
                                                    },
                                                    icon = Icons.Rounded.KeyboardArrowDown,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                    enabled = propertySetIndex + 1 < sets.size,
                                                )
                                                FlorisIconButton(
                                                    onClick = { workspace.update { sets.removeAt(propertySetIndex) } },
                                                    icon = Icons.Rounded.Delete,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                )
                                                FlorisIconButton(
                                                    onClick = {
                                                        snyggPropertySetForEditing = ps
                                                        snyggPropertyToEdit = SnyggEmptyPropertyInfoForAdding.copy(rule = rule)
                                                    },
                                                    icon = Icons.Rounded.Add,
                                                    iconColor = MaterialTheme.colorScheme.primary,
                                                    iconModifier = Modifier.size(ButtonDefaults.IconSize),
                                                )
                                            }
                                            SinglePropertySetEditor(ps)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        if (showEditComponentMetaDialog) {
            ComponentMetaEditorDialog(
                workspace = workspace,
                editor = editor,
                onConfirm = { showEditComponentMetaDialog = false },
                onDismiss = { showEditComponentMetaDialog = false },
            )
        }

        if (showFineTuneDialog) {
            FineTuneDialog(onDismiss = { showFineTuneDialog = false })
        }

        val ruleToEdit = snyggRuleToEdit
        if (ruleToEdit != null) {
            EditRuleDialog(
                initRule = ruleToEdit,
                level = snyggLevel,
                onConfirmRule = { oldRule, newRule ->
                    val rules = stylesheetEditor.rules
                    when {
                        oldRule == newRule -> { snyggRuleToEdit = null; true }
                        rules.contains(newRule) -> false
                        else -> workspace.update {
                            val set = rules.remove(oldRule)
                            when {
                                set != null -> {
                                    rules[newRule] = set
                                    snyggRuleToEdit = null
                                    scope.launch { lazyListState.animateScrollToItem(index = rules.keys.indexOf(newRule)) }
                                    true
                                }
                                oldRule == SnyggEmptyRuleForAdding -> {
                                    when (SnyggSpec.propertySetSpecOf(newRule)!!.type) {
                                        SnyggSpecDecl.PropertySet.Type.SINGLE_SET -> rules[newRule] = SnyggSinglePropertySetEditor()
                                        SnyggSpecDecl.PropertySet.Type.MULTIPLE_SETS -> rules[newRule] = SnyggMultiplePropertySetsEditor()
                                    }
                                    snyggRuleToEdit = null
                                    scope.launch { lazyListState.animateScrollToItem(index = rules.keys.indexOf(newRule)) }
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                },
                onDeleteRule = { rule ->
                    workspace.update { stylesheetEditor.rules.remove(rule) }
                    snyggRuleToEdit = null
                },
                onDismiss = { snyggRuleToEdit = null },
            )
        }

        val propertyToEdit = snyggPropertyToEdit
        if (propertyToEdit != null) {
            EditPropertyDialog(
                initProperty = propertyToEdit,
                level = snyggLevel,
                colorRepresentation = colorRepresentation,
                definedVariables = definedVariables,
                fontNames = fontNames,
                workspace = workspace,
                onConfirmNewValue = { name, value ->
                    val properties = snyggPropertySetForEditing?.properties ?: return@EditPropertyDialog false
                    if (propertyToEdit == SnyggEmptyPropertyInfoForAdding && properties.containsKey(name)) {
                        return@EditPropertyDialog false
                    }
                    workspace.update { properties[name] = value }
                    snyggPropertyToEdit = null
                    true
                },
                onDelete = {
                    workspace.update { snyggPropertySetForEditing?.properties?.remove(propertyToEdit.name) }
                    snyggPropertyToEdit = null
                },
                onDismiss = { snyggPropertyToEdit = null },
            )
        }
    }
}

@Composable
private fun ComponentMetaEditorDialog(
    workspace: CacheManager.ExtEditorWorkspace<*>,
    editor: ThemeExtensionComponentEditor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }

    var id by rememberSaveable { mutableStateOf(editor.id) }
    val idValidation = rememberValidationResult(ExtensionValidation.ComponentId, id)
    var label by rememberSaveable { mutableStateOf(editor.label) }
    val labelValidation = rememberValidationResult(ExtensionValidation.ComponentLabel, label)
    var authors by rememberSaveable { mutableStateOf(editor.authors.joinToString("\n")) }
    val authorsValidation = rememberValidationResult(ExtensionValidation.ComponentAuthors, authors)
    var isNightTheme by rememberSaveable { mutableStateOf(editor.isNightTheme) }
    var materialYouFlags by rememberSaveable(stateSaver = MaterialYouFlagsSaver) { mutableStateOf(editor.materialYouFlags) }
    var stylesheetPath by rememberSaveable { mutableStateOf(editor.stylesheetPath) }
    val stylesheetPathValidation = rememberValidationResult(ExtensionValidation.ThemeComponentStylesheetPath, stylesheetPath)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.ext__editor__metadata__title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogProperty(text = stringRes(R.string.ext__meta__id)) {
                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Validation(showValidationErrors, idValidation)
                }
                DialogProperty(text = stringRes(R.string.ext__meta__label)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Validation(showValidationErrors, labelValidation)
                }
                DialogProperty(text = stringRes(R.string.ext__meta__authors)) {
                    OutlinedTextField(
                        value = authors,
                        onValueChange = { authors = it },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Validation(showValidationErrors, authorsValidation)
                }
                Text(
                    modifier = Modifier
                        .toggleable(isNightTheme) { isNightTheme = it }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    text = stringRes(R.string.settings__theme_editor__component_meta_is_night_theme),
                    style = MaterialTheme.typography.bodyLarge,
                )
                DialogProperty(text = stringRes(R.string.settings__theme_editor__component_meta_stylesheet_path)) {
                    OutlinedTextField(
                        value = stylesheetPath,
                        onValueChange = { stylesheetPath = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                        placeholder = if (stylesheetPath.isEmpty()) {
                            { Text(ThemeExtensionComponent.defaultStylesheetPath(id.trim())) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Validation(showValidationErrors, stylesheetPathValidation)
                }

                DialogProperty(text = stringRes(R.string.settings__theme_editor__component_meta_material_you__title)) {
                    M3Dropdown(
                        modifier = Modifier.padding(bottom = 8.dp),
                        options = PaletteStyle.entries.map { it.name },
                        selectedOptionIndex = materialYouFlags.paletteStyle.ordinal,
                        onSelectOption = { materialYouFlags = materialYouFlags.copy(paletteStyle = PaletteStyle.entries[it]) },
                    )
                    M3Dropdown(
                        modifier = Modifier.padding(bottom = 8.dp),
                        options = Contrast.entries.map { it.name },
                        selectedOptionIndex = materialYouFlags.contrastLevel.ordinal,
                        onSelectOption = { materialYouFlags = materialYouFlags.copy(contrastLevel = Contrast.entries[it]) },
                    )
                    M3Dropdown(
                        modifier = Modifier.padding(bottom = 8.dp),
                        options = ColorSpec.SpecVersion.entries.map { it.name },
                        selectedOptionIndex = materialYouFlags.specVersion.ordinal,
                        onSelectOption = { materialYouFlags = materialYouFlags.copy(specVersion = ColorSpec.SpecVersion.entries[it]) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val allFieldsValid = idValidation.isValid() &&
                    labelValidation.isValid() &&
                    authorsValidation.isValid() &&
                    stylesheetPathValidation.isValid()
                if (!allFieldsValid) {
                    showValidationErrors = true
                } else if (id != editor.id && (workspace.editor as? ThemeExtensionEditor)?.themes?.find { it.id == id.trim() } != null) {
                    context.showLongToastSync(stringRes(R.string.theme_editor__duplicate_id))
                } else {
                    workspace.update {
                        editor.id = id.trim()
                        editor.label = label.trim()
                        editor.authors = authors.lines().map { it.trim() }.filter { it.isNotBlank() }
                        editor.isNightTheme = isNightTheme
                        editor.stylesheetPath = stylesheetPath.trim()
                        editor.materialYouFlags = materialYouFlags
                    }
                    onConfirm()
                }
            }) {
                Text(stringRes(R.string.action__apply))
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
private fun SnyggRuleRow(
    rule: SnyggRule,
    level: SnyggLevel,
    showEditBtn: Boolean,
    onEditRuleBtnClick: () -> Unit,
    onAddPropertyBtnClick: () -> Unit,
) {
    val context = LocalContext.current

    @Composable
    fun Selector(text: String) {
        Text(
            modifier = Modifier
                .padding(end = 8.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = Shapes.small),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    @Composable
    fun AttributesList(text: String, list: String) {
        Text(
            text = "$text = $list",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.56f),
            fontFamily = FontFamily.Monospace,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp, horizontal = 10.dp),
        ) {
            if (rule is SnyggElementRule) {
                Text(
                    text = context.translateElementName(rule, level),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (rule.selector == SnyggSelector.PRESSED) {
                        Selector(text = when (level) { SnyggLevel.DEVELOPER -> SnyggSelector.PRESSED.id; else -> stringRes(R.string.snygg__rule_selector__pressed) })
                    }
                    if (rule.selector == SnyggSelector.FOCUS) {
                        Selector(text = when (level) { SnyggLevel.DEVELOPER -> SnyggSelector.FOCUS.id; else -> stringRes(R.string.snygg__rule_selector__focus) })
                    }
                    if (rule.selector == SnyggSelector.HOVER) {
                        Selector(text = when (level) { SnyggLevel.DEVELOPER -> SnyggSelector.HOVER.id; else -> stringRes(R.string.snygg__rule_selector__hover) })
                    }
                    if (rule.selector == SnyggSelector.DISABLED) {
                        Selector(text = when (level) { SnyggLevel.DEVELOPER -> SnyggSelector.DISABLED.id; else -> stringRes(R.string.snygg__rule_selector__disabled) })
                    }
                }
                for ((attrKey, attrValue) in rule.attributes) {
                    AttributesList(text = attrKey, list = attrValue.toString())
                }
            } else {
                Text(text = rule.toString())
            }
        }
        if (showEditBtn) {
            FlorisIconButton(
                onClick = onEditRuleBtnClick,
                icon = Icons.Rounded.Edit,
                iconColor = MaterialTheme.colorScheme.primary,
                iconModifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        FlorisIconButton(
            onClick = onAddPropertyBtnClick,
            icon = Icons.Rounded.Add,
            iconColor = MaterialTheme.colorScheme.secondary,
            iconModifier = Modifier.size(ButtonDefaults.IconSize),
        )
    }
}

@Composable
fun DialogProperty(
    text: String,
    modifier: Modifier = Modifier,
    trailingIconTitle: @Composable () -> Unit = { },
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                text = text,
                style = MaterialTheme.typography.titleMedium,
            )
            trailingIconTitle()
        }
        content()
    }
}

private object CustomRuleComparator : Comparator<SnyggRule> {
    @Suppress("IfThenToElvis")
    override fun compare(a: SnyggRule, b: SnyggRule): Int {
        return if (a !is SnyggElementRule || b !is SnyggElementRule || a.elementName == b.elementName) {
            a.compareTo(b)
        } else {
            val aOrdinal = FlorisImeUi.elementNamesToOrdinals[a.elementName]
            val bOrdinal = FlorisImeUi.elementNamesToOrdinals[b.elementName]
            if (aOrdinal == null && bOrdinal == null) {
                a.elementName.compareTo(b.elementName)
            } else if (bOrdinal == null) {
                -1
            } else if (aOrdinal == null) {
                1
            } else {
                aOrdinal.compareTo(bOrdinal)
            }
        }
    }
}
