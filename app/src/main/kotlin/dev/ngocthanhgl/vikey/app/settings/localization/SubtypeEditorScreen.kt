package dev.ngocthanhgl.vikey.app.settings.localization

import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.core.DisplayLanguageNamesIn
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.core.SubtypeJsonConfig
import dev.ngocthanhgl.vikey.ime.core.SubtypeLayoutMap
import dev.ngocthanhgl.vikey.ime.core.SubtypeNlpProviderMap
import dev.ngocthanhgl.vikey.ime.core.SubtypePreset
import dev.ngocthanhgl.vikey.ime.keyboard.LayoutArrangementComponent
import dev.ngocthanhgl.vikey.ime.keyboard.LayoutType
import dev.ngocthanhgl.vikey.ime.keyboard.extCorePopupMapping
import dev.ngocthanhgl.vikey.ime.nlp.FallbackNlpProvider

import dev.ngocthanhgl.vikey.ime.nlp.vietnamese.QwenNatives
import dev.ngocthanhgl.vikey.ime.nlp.vietnamese.QwenSuggestionProvider
import dev.ngocthanhgl.vikey.keyboardManager
import dev.ngocthanhgl.vikey.lib.devtools.flogDebug
import dev.ngocthanhgl.vikey.lib.FlorisLocale
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponentName
import dev.ngocthanhgl.vikey.subtypeManager
import kotlinx.coroutines.launch
import dev.patrickgold.jetpref.datastore.model.collectAsState
import java.io.File
import org.florisboard.lib.compose.stringRes
import androidx.compose.material.icons.filled.ArrowDropDown
import dev.ngocthanhgl.vikey.app.settings.components.M3Dropdown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton


private val SelectComponentName = ExtensionComponentName("00", "00")
private val SelectNlpProviderId = SelectComponentName.toString()
private val SelectNlpProviders = SubtypeNlpProviderMap(
    spelling = SelectNlpProviderId,
)
private val SelectLayoutMap = SubtypeLayoutMap(
    characters = SelectComponentName,
    symbols = SelectComponentName,
    symbols2 = SelectComponentName,
    numeric = SelectComponentName,
    numericAdvanced = SelectComponentName,
    numericRow = SelectComponentName,
    phone = SelectComponentName,
    phone2 = SelectComponentName,
)
private val SelectLocale = FlorisLocale.from("00", "00")
private val SelectListKeys = listOf(SelectComponentName)

private class SubtypeEditorState(init: Subtype?) {
    companion object {
        val Saver = Saver<SubtypeEditorState, String>(
            save = { editor ->
                val subtype = Subtype(
                    id = editor.id.value,
                    primaryLocale = editor.primaryLocale.value,
                    secondaryLocales = editor.secondaryLocales.value,
                    nlpProviders = editor.nlpProviders.value,
                    composer = editor.composer.value,
                    currencySet = editor.currencySet.value,
                    punctuationRule = editor.punctuationRule.value,
                    popupMapping = editor.popupMapping.value,
                    layoutMap = editor.layoutMap.value,
                )
                SubtypeJsonConfig.encodeToString(subtype)
            },
            restore = { str ->
                val subtype = SubtypeJsonConfig.decodeFromString<Subtype>(str)
                SubtypeEditorState(subtype)
            },
        )
    }

    val id: MutableState<Long> = mutableLongStateOf(init?.id ?: -1)
    val primaryLocale: MutableState<FlorisLocale> = mutableStateOf(init?.primaryLocale ?: SelectLocale)
    val secondaryLocales: MutableState<List<FlorisLocale>> = mutableStateOf(init?.secondaryLocales ?: listOf())
    val nlpProviders: MutableState<SubtypeNlpProviderMap> = mutableStateOf(init?.nlpProviders ?: Subtype.DEFAULT.nlpProviders)
    val composer: MutableState<ExtensionComponentName> = mutableStateOf(init?.composer ?: SelectComponentName)
    val currencySet: MutableState<ExtensionComponentName> = mutableStateOf(init?.currencySet ?: SelectComponentName)
    val punctuationRule: MutableState<ExtensionComponentName> = mutableStateOf(init?.punctuationRule ?: Subtype.DEFAULT.punctuationRule)
    val popupMapping: MutableState<ExtensionComponentName> = mutableStateOf(init?.popupMapping ?: SelectComponentName)
    val layoutMap: MutableState<SubtypeLayoutMap> = mutableStateOf(init?.layoutMap ?: SelectLayoutMap)

    fun applySubtype(subtype: Subtype) {
        id.value = subtype.id
        primaryLocale.value = subtype.primaryLocale
        secondaryLocales.value = subtype.secondaryLocales
        composer.value = subtype.composer
        nlpProviders.value = subtype.nlpProviders
        currencySet.value = subtype.currencySet
        punctuationRule.value = subtype.punctuationRule
        popupMapping.value = subtype.popupMapping
        layoutMap.value = subtype.layoutMap
    }

    fun toSubtype() = runCatching {
        check(primaryLocale.value != SelectLocale)
        check(nlpProviders.value.spelling != SelectNlpProviderId)
        check(nlpProviders.value.suggestion != SelectNlpProviderId)
        check(composer.value != SelectComponentName)
        check(currencySet.value != SelectComponentName)
        check(punctuationRule.value != SelectComponentName)
        check(popupMapping.value != SelectComponentName)
        check(layoutMap.value.characters != SelectComponentName)
        check(layoutMap.value.symbols != SelectComponentName)
        check(layoutMap.value.symbols2 != SelectComponentName)
        check(layoutMap.value.numeric != SelectComponentName)
        check(layoutMap.value.numericAdvanced != SelectComponentName)
        check(layoutMap.value.numericRow != SelectComponentName)
        check(layoutMap.value.phone != SelectComponentName)
        check(layoutMap.value.phone2 != SelectComponentName)
        Subtype(
            id.value, primaryLocale.value, secondaryLocales.value, nlpProviders.value, composer.value,
            currencySet.value, punctuationRule.value, popupMapping.value, layoutMap.value,
        )
    }
}

@Composable
fun SubtypeEditorScreen(id: Long?) {
    val selectValue = stringRes(R.string.settings__localization__subtype_select_placeholder)
    val selectListValues = remember(selectValue) { listOf(selectValue) }

    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()

    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
    val composers by keyboardManager.resources.composers.collectAsState()
    val currencySets by keyboardManager.resources.currencySets.collectAsState()
    val layoutExtensions by keyboardManager.resources.layouts.collectAsState()
    val popupMappings by keyboardManager.resources.popupMappings.collectAsState()
    val subtypePresets by keyboardManager.resources.subtypePresets.collectAsState()

    val subtypeEditor = rememberSaveable(saver = SubtypeEditorState.Saver) {
        val subtype = id?.let { subtypeManager.getSubtypeById(it) }
        SubtypeEditorState(subtype)
    }
    var primaryLocale by subtypeEditor.primaryLocale
    var composer by subtypeEditor.composer
    var currencySet by subtypeEditor.currencySet
    var popupMapping by subtypeEditor.popupMapping
    var layoutMap by subtypeEditor.layoutMap
    var nlpProviders by subtypeEditor.nlpProviders

    var showSubtypePresetsDialog by rememberSaveable { mutableStateOf(id == null) }
    var showSelectAsError by rememberSaveable { mutableStateOf(false) }
    var errorDialogStrId by rememberSaveable { mutableStateOf<Int?>(null) }

    val selectLocaleScreenResult = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>(SelectLocaleScreenResultLanguageTag)
    DisposableEffect(selectLocaleScreenResult, lifecycleOwner) {
        val observer = Observer<String> { languageTag ->
            val locale = FlorisLocale.fromTag(languageTag)
            primaryLocale = locale
            val preset = subtypeManager.getSubtypePresetForLocale(locale)
            popupMapping = preset?.popupMapping ?: extCorePopupMapping("default")
        }
        selectLocaleScreenResult?.observe(lifecycleOwner, observer)
        onDispose { selectLocaleScreenResult?.removeObserver(observer) }
    }


    @Composable
    fun SubtypePropertyDropdown(
        title: String,
        layoutType: LayoutType,
    ) {
        SubtypeProperty(title) {
            SubtypeLayoutDropdown(
                layoutType = layoutType,
                layouts = layoutExtensions[layoutType] ?: mapOf(),
                showSelectAsError = showSelectAsError,
                layoutMap = layoutMap,
                onLayoutMapChanged = { layoutMap = it },
                selectListValues = selectListValues,
            )
        }
    }

    fun saveAction() {
        subtypeEditor.toSubtype().onSuccess { subtype ->
            if (id == null) {
                if (!subtypeManager.addSubtype(subtype)) {
                    errorDialogStrId = R.string.settings__localization__subtype_error_already_exists
                    return@onSuccess
                }
            } else {
                subtypeManager.modifySubtypeWithSameId(subtype)
            }
            navController.popBackStack()
        }.onFailure {
            showSelectAsError = true
            errorDialogStrId = R.string.settings__localization__subtype_error_fields_no_value
        }
    }

    SettingsScaffold(
        title = stringRes(if (id == null) {
            R.string.settings__localization__subtype_add_title
        } else {
            R.string.settings__localization__subtype_edit_title
        }),
        actions = {
            if (id != null) {
                IconButton(onClick = {
                    val subtype = subtypeManager.getSubtypeById(id)
                    if (subtype != null) {
                        subtypeManager.removeSubtype(subtype)
                        navController.popBackStack()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                    )
                }
            }
        },
    ) {
        if (id == null) {
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
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                    text = stringRes(R.string.settings__localization__suggested_subtype_presets),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val systemLocales = remember {
                    val list = mutableListOf<FlorisLocale>()
                    val localeList = configuration.locales
                    for (n in 0 until localeList.size()) {
                        list.add(FlorisLocale.from(localeList.get(n)))
                    }
                    list
                }
                val suggestedPresets = remember(subtypePresets) {
                    val presets = mutableListOf<SubtypePreset>()
                    for (systemLocale in systemLocales) {
                        subtypePresets.find { it.locale == systemLocale }?.let { presets.add(it) }
                    }
                    presets
                }
                if (suggestedPresets.isNotEmpty()) {
                    suggestedPresets.forEachIndexed { index, suggestedPreset ->
                        Text(
                            modifier = Modifier
                                .clickable { subtypeEditor.applySubtype(suggestedPreset.toSubtype()) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            text = when (displayLanguageNamesIn) {
                                DisplayLanguageNamesIn.SYSTEM_LOCALE -> suggestedPreset.locale.displayName()
                                DisplayLanguageNamesIn.NATIVE_LOCALE -> suggestedPreset.locale.displayName(suggestedPreset.locale)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (index < suggestedPresets.lastIndex) {
                            SettingsDivider()
                        }
                    }
                } else {
                    Text(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        text = stringRes(R.string.settings__localization__suggested_subtype_presets_none_found),
                    )
                }
                Button(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .align(Alignment.End),
                    onClick = { showSubtypePresetsDialog = true },
                ) {
                    Text(text = stringRes(R.string.settings__localization__subtype_presets_view_all))
                }
            }

            GroupSpacer()
        }

        Text(
            text = stringRes(R.string.settings__localization__subtype_locale),
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
            SubtypeProperty(stringRes(R.string.settings__localization__subtype_locale)) {
                OutlinedButton(
                    onClick = { navController.navigate(Routes.Settings.SelectLocale) },
                    shape = RoundedCornerShape(28.dp),
                    colors = if (showSelectAsError && primaryLocale == SelectLocale) {
                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                ) {
                    Text(
                        text = if (primaryLocale == SelectLocale) selectValue else when (displayLanguageNamesIn) {
                            DisplayLanguageNamesIn.SYSTEM_LOCALE -> primaryLocale.displayName()
                            DisplayLanguageNamesIn.NATIVE_LOCALE -> primaryLocale.displayName(primaryLocale)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            SettingsDivider()
            SubtypeProperty(stringRes(R.string.settings__localization__subtype_popup_mapping)) {
                val popupMappingIds = remember(popupMappings) {
                    SelectListKeys + popupMappings.keys
                }
                val popupMappingLabels = remember(popupMappings) {
                    selectListValues + popupMappings.values.map { it.label }
                }
                val selectedIndex = popupMappingIds.indexOf(popupMapping).coerceAtLeast(0)
                M3Dropdown(
                    options = popupMappingLabels,
                    selectedOptionIndex = selectedIndex,
                    isError = showSelectAsError && selectedIndex == 0,
                    onSelectOption = { popupMapping = popupMappingIds[it] },
                )
            }
            SettingsDivider()
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_characters_layout), LayoutType.CHARACTERS)
        }

        GroupSpacer()

        Text(
            text = stringRes(R.string.settings__localization__subtype_suggestion_provider),
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
            SubtypeProperty(stringRes(R.string.settings__localization__subtype_suggestion_provider)) {
                val nlpProviderMappings = mapOf(
                    FallbackNlpProvider.providerId to "None",
                    QwenSuggestionProvider.ProviderId to "Vietnamese (Qwen)"
                )

                val nlpProviderMappingIds = remember(nlpProviderMappings) {
                    nlpProviderMappings.keys.toList()
                }
                val nlpProviderMappingLabels = remember(nlpProviderMappings) {
                    nlpProviderMappings.values.toList()
                }
                val selectedIndex = nlpProviderMappingIds.indexOf(nlpProviders.suggestion).coerceAtLeast(0)
                M3Dropdown(
                    options = nlpProviderMappingLabels,
                    selectedOptionIndex = selectedIndex,
                    onSelectOption = { nlpProviders = SubtypeNlpProviderMap(
                        suggestion = nlpProviderMappingIds[it],
                        spelling = nlpProviderMappingIds[it]
                    ) },
                )
            }

            if (nlpProviders.suggestion == QwenSuggestionProvider.ProviderId && QwenNatives.isAvailable) {
                val scope = rememberCoroutineScope()
                val dir = context.filesDir

                fun scanModel(): File? = dir.listFiles { f -> f.extension == "gguf" && f.length() > 0 }
                    ?.maxByOrNull { it.length() }

                val modelFile = remember { mutableStateOf(scanModel()) }
                val modelExists = modelFile.value != null

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    File(dir, "model.gguf").outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                modelFile.value = scanModel()
                                if (modelFile.value != null) {
                                    QwenSuggestionProvider.getInstance()?.reloadModel()
                                }
                            } catch (e: Exception) {
                                flogDebug { "Import model failed: ${e.message}" }
                            }
                        }
                    }
                }

                SettingsDivider()
                SubtypeProperty("Model") {
                    if (modelExists) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Model: ${modelFile.value?.name ?: "unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    QwenSuggestionProvider.getInstance()?.removeModel()
                                    modelFile.value = null
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove model",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    } else {
                        Column {
                            Button(onClick = { importLauncher.launch("application/octet-stream") }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Select GGUF file")
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/ngocthanhgl/ViKey-Telex/releases/tag/Model")
                                )
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Download from GitHub")
                            }
                        }
                    }
                }
            }
        }

        GroupSpacer()

        Text(
            text = "Layouts",
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
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_symbols_layout), LayoutType.SYMBOLS)
            SettingsDivider()
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_symbols2_layout), LayoutType.SYMBOLS2)
            SettingsDivider()
            SubtypeProperty(stringRes(R.string.settings__localization__subtype_composer)) {
                val composerIds = remember(composers) {
                    SelectListKeys + composers.keys
                }
                val composerNames = remember(composers) {
                    selectListValues + composers.values.map { it.label }
                }
                M3Dropdown(
                    options = composerNames,
                    selectedOptionIndex = composerIds.indexOf(composer).coerceAtLeast(0),
                    isError = showSelectAsError && composer == SelectComponentName,
                    onSelectOption = { composer = composerIds[it] },
                )
            }
            SettingsDivider()
            SubtypeProperty(stringRes(R.string.settings__localization__subtype_currency_set)) {
                val currencySetIds = remember(currencySets) {
                    SelectListKeys + currencySets.keys
                }
                val currencySetNames = remember(currencySets) {
                    selectListValues + currencySets.values.map { it.label }
                }
                M3Dropdown(
                    options = currencySetNames,
                    selectedOptionIndex = currencySetIds.indexOf(currencySet).coerceAtLeast(0),
                    isError = showSelectAsError && currencySet == SelectComponentName,
                    onSelectOption = { currencySet = currencySetIds[it] },
                )
            }
        }

        GroupSpacer()

        Text(
            text = "Numeric",
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
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_numeric_layout), LayoutType.NUMERIC)
            SettingsDivider()
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_numeric_advanced_layout), LayoutType.NUMERIC_ADVANCED)
            SettingsDivider()
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_numeric_row_layout), LayoutType.NUMERIC_ROW)
        }

        GroupSpacer()

        Text(
            text = "Phone",
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
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_phone_layout), LayoutType.PHONE)
            SettingsDivider()
            SubtypePropertyDropdown(stringRes(R.string.settings__localization__subtype_phone2_layout), LayoutType.PHONE2)
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringRes(R.string.action__cancel))
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { saveAction() },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringRes(R.string.action__save))
            }
        }
        Spacer(Modifier.height(16.dp))

        if (showSubtypePresetsDialog) {
            AlertDialog(
                onDismissRequest = { showSubtypePresetsDialog = false },
                title = { Text(stringRes(R.string.settings__localization__subtype_presets)) },
                dismissButton = {
                    TextButton(onClick = { showSubtypePresetsDialog = false }) {
                        Text(stringRes(android.R.string.cancel))
                    }
                },
                text = {
                    Column {
                        HorizontalDivider()
                        val lazyListState = rememberLazyListState()
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        ) {
                            items(subtypePresets) { subtypePreset ->
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            subtypeEditor.applySubtype(subtypePreset.toSubtype())
                                            showSubtypePresetsDialog = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when (displayLanguageNamesIn) {
                                                DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtypePreset.locale.displayName()
                                                DisplayLanguageNamesIn.NATIVE_LOCALE -> subtypePreset.locale.displayName(subtypePreset.locale)
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = subtypePreset.preferred.characters.componentId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                },
            )
        }

        errorDialogStrId?.let { strId ->
            AlertDialog(
                onDismissRequest = { errorDialogStrId = null },
                title = { Text(stringRes(R.string.error__title)) },
                confirmButton = {
                    TextButton(onClick = { errorDialogStrId = null }) {
                        Text(stringRes(android.R.string.ok))
                    }
                },
                text = { Text(text = stringRes(strId)) },
            )
        }
    }
}

@Composable
private fun SubtypeProperty(text: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = text,
            style = MaterialTheme.typography.titleSmall,
        )
        content()
    }
}

@Composable
private fun SubtypeLayoutDropdown(
    layoutType: LayoutType,
    layouts: Map<ExtensionComponentName, LayoutArrangementComponent>,
    showSelectAsError: Boolean,
    layoutMap: SubtypeLayoutMap,
    onLayoutMapChanged: (SubtypeLayoutMap) -> Unit,
    selectListValues: List<String>,
) {
    val layoutIds = remember(layouts) { SelectListKeys + layouts.keys.toList() }
    val layoutLabels = remember(layouts) { selectListValues + layouts.values.map { it.label } }
    val layoutId = remember(layoutMap) { layoutMap[layoutType] }
    val selectedIndex = layoutIds.indexOf(layoutId).coerceAtLeast(0)
    M3Dropdown(
        options = layoutLabels,
        selectedOptionIndex = selectedIndex,
        isError = showSelectAsError && selectedIndex == 0,
        onSelectOption = { onLayoutMapChanged(layoutMap.copy(layoutType = layoutType, componentName = layoutIds[it])!!) },
    )
}

@Composable
private fun GroupSpacer() {
    Spacer(modifier = Modifier
        .fillMaxWidth()
        .height(8.dp))
}
