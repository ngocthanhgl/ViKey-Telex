package dev.ngocthanhgl.vikey.app.settings.dictionary

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.app.settings.theme.DialogProperty
import dev.ngocthanhgl.vikey.ime.dictionary.DictionaryManager
import dev.ngocthanhgl.vikey.ime.dictionary.FREQUENCY_MAX
import dev.ngocthanhgl.vikey.ime.dictionary.FREQUENCY_MIN
import dev.ngocthanhgl.vikey.ime.dictionary.UserDictionaryDao
import dev.ngocthanhgl.vikey.ime.dictionary.UserDictionaryEntry
import dev.ngocthanhgl.vikey.ime.dictionary.UserDictionaryValidation
import dev.ngocthanhgl.vikey.lib.FlorisLocale
import dev.ngocthanhgl.vikey.lib.compose.Validation
import dev.ngocthanhgl.vikey.lib.rememberValidationResult
import dev.ngocthanhgl.vikey.lib.util.launchActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.stringRes

private val AllLanguagesLocale = FlorisLocale.from(language = "zz")
private val UserDictionaryEntryToAdd = UserDictionaryEntry(id = 0, "", 255, null, null)
private const val SystemUserDictionaryUiIntentAction = "android.settings.USER_DICTIONARY_SETTINGS"

enum class UserDictionaryType(val id: String) {
    FLORIS("floris"),
    SYSTEM("system");
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDictionaryScreen(type: UserDictionaryType) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val dictionaryManager = DictionaryManager.default()
    val scope = rememberCoroutineScope()

    var currentLocale by remember { mutableStateOf<FlorisLocale?>(null) }
    var languageList by remember { mutableStateOf(emptyList<FlorisLocale>()) }
    var wordList by remember { mutableStateOf(emptyList<UserDictionaryEntry>()) }
    var userDictionaryEntryForDialog by remember { mutableStateOf<UserDictionaryEntry?>(null) }

    fun userDictionaryDao(): UserDictionaryDao? {
        return when (type) {
            UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDao()
            UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDao()
        }
    }

    @Composable
    fun getDisplayNameForLocale(locale: FlorisLocale): String {
        return if (locale == AllLanguagesLocale) {
            stringRes(R.string.settings__udm__all_languages)
        } else {
            locale.displayName()
        }
    }

    fun buildUi() {
        if (currentLocale != null) {
            val locale = if (currentLocale == AllLanguagesLocale) null else currentLocale
            wordList = userDictionaryDao()?.queryAll(locale) ?: emptyList()
            if (wordList.isEmpty()) {
                currentLocale = null
            }
        }
        if (currentLocale == null) {
            languageList = userDictionaryDao()
                ?.queryLanguageList()
                ?.sortedBy { it?.displayLanguage() }
                ?.map { it ?: AllLanguagesLocale }
                ?: emptyList()
        }
    }

    val importDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val db = when (type) {
                UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDatabase()
                UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
            }
            if (db == null) {
                context.showLongToastSync(R.string.user_dict__import_db_null)
                return@rememberLauncherForActivityResult
            }
            runCatching {
                db.importCombinedList(context, uri)
            }.onSuccess {
                buildUi()
                context.showLongToastSync(R.string.settings__udm__dictionary_import_success)
            }.onFailure { error ->
                context.showLongToastSync(context.getString(R.string.user_dict__error_format, error.localizedMessage))
            }
        },
    )

    val exportDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val db = when (type) {
                UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDatabase()
                UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
            }
            if (db == null) {
                context.showLongToastSync(R.string.user_dict__export_db_null)
                return@rememberLauncherForActivityResult
            }
            runCatching {
                db.exportCombinedList(context, uri)
            }.onSuccess {
                buildUi()
                context.showLongToastSync(R.string.settings__udm__dictionary_export_success)
            }.onFailure { error ->
                context.showLongToastSync(context.getString(R.string.user_dict__error_format, error.localizedMessage))
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(when (type) {
                    UserDictionaryType.FLORIS -> R.string.settings__udm__title_floris
                    UserDictionaryType.SYSTEM -> R.string.settings__udm__title_system
                })) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentLocale != null) {
                                currentLocale = null
                                buildUi()
                            } else {
                                navController.popBackStack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (currentLocale != null) {
                                Icons.Rounded.Close
                            } else {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            },
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                importDictionary.launch("*/*")
                                expanded = false
                            },
                            text = { Text(text = stringRes(R.string.action__import)) },
                        )
                        DropdownMenuItem(
                            onClick = {
                                exportDictionary.launch(context.getString(R.string.user_dict__export_filename))
                                expanded = false
                            },
                            text = { Text(text = stringRes(R.string.action__export)) },
                        )
                        if (type == UserDictionaryType.SYSTEM) {
                            DropdownMenuItem(
                                onClick = {
                                    context.launchActivity { it.action = SystemUserDictionaryUiIntentAction }
                                    expanded = false
                                },
                                text = { Text(text = stringRes(R.string.settings__udm__open_system_manager_ui)) },
                            )
                        }
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
                onClick = { userDictionaryEntryForDialog = UserDictionaryEntryToAdd },
                icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
                text = { Text(text = stringRes(R.string.settings__udm__dialog__title_add)) },
            )
        },
    ) { padding ->
        BackHandler(currentLocale != null) {
            currentLocale = null
            buildUi()
        }

        LaunchedEffect(Unit) {
            dictionaryManager.loadUserDictionariesIfNecessary()
            buildUi()
        }

        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                if (languageList.isEmpty() && currentLocale == null) {
                    Text(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        text = stringRes(R.string.settings__udm__no_words_in_dictionary),
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
            if (currentLocale == null) {
                item {
                    ElevatedCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        languageList.forEachIndexed { index, language ->
                            Text(
                                modifier = Modifier
                                    .clickable {
                                        scope.launch {
                                            delay(150)
                                            currentLocale = language
                                            buildUi()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                text = getDisplayNameForLocale(language),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (index < languageList.lastIndex) {
                                SettingsDivider()
                            }
                        }
                    }
                }
            } else {
                items(wordList) { wordEntry ->
                    ElevatedCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Text(
                            modifier = Modifier
                                .clickable { userDictionaryEntryForDialog = wordEntry }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            text = wordEntry.word,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        val wordEntry = userDictionaryEntryForDialog
        if (wordEntry != null) {
            var showValidationErrors by rememberSaveable { mutableStateOf(false) }
            val isAddWord = wordEntry === UserDictionaryEntryToAdd
            var word by rememberSaveable { mutableStateOf(wordEntry.word) }
            val wordValidation = rememberValidationResult(UserDictionaryValidation.Word, word)
            var freq by rememberSaveable { mutableStateOf(wordEntry.freq.toString()) }
            val freqValidation = rememberValidationResult(UserDictionaryValidation.Freq, freq)
            var shortcut by rememberSaveable { mutableStateOf(wordEntry.shortcut ?: "") }
            val shortcutValidation = rememberValidationResult(UserDictionaryValidation.Shortcut, shortcut)
            var locale by rememberSaveable { mutableStateOf(wordEntry.locale ?: "") }
            val localeValidation = rememberValidationResult(UserDictionaryValidation.Locale, locale)

            AlertDialog(
                onDismissRequest = { userDictionaryEntryForDialog = null },
                title = { Text(stringRes(if (isAddWord) {
                    R.string.settings__udm__dialog__title_add
                } else {
                    R.string.settings__udm__dialog__title_edit
                })) },
                text = {
                    Column {
                        DialogProperty(text = stringRes(R.string.settings__udm__dialog__word_label)) {
                            OutlinedTextField(
                                value = word,
                                onValueChange = { word = it },
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
                            Validation(showValidationErrors, wordValidation)
                        }
                        DialogProperty(text = stringRes(
                            R.string.settings__udm__dialog__freq_label,
                            "f_min" to FREQUENCY_MIN, "f_max" to FREQUENCY_MAX,
                        )) {
                            OutlinedTextField(
                                value = freq,
                                onValueChange = { freq = it },
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
                            Validation(showValidationErrors, freqValidation)
                        }
                        DialogProperty(text = stringRes(R.string.settings__udm__dialog__shortcut_label)) {
                            OutlinedTextField(
                                value = shortcut,
                                onValueChange = { shortcut = it },
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
                            Validation(showValidationErrors, shortcutValidation)
                        }
                        DialogProperty(text = stringRes(R.string.settings__udm__dialog__locale_label)) {
                            OutlinedTextField(
                                value = locale,
                                onValueChange = { locale = it },
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
                            Validation(showValidationErrors, localeValidation)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val isInvalid = wordValidation.isInvalid() ||
                            freqValidation.isInvalid() ||
                            shortcutValidation.isInvalid() ||
                            localeValidation.isInvalid()
                        if (isInvalid) {
                            showValidationErrors = true
                        } else {
                            val entry = UserDictionaryEntry(
                                id = wordEntry.id,
                                word = word.trim(),
                                freq = freq.toInt(10),
                                shortcut = shortcut.trim().takeIf { it.isNotBlank() },
                                locale = locale.trim().takeIf { it.isNotBlank() }?.let {
                                    FlorisLocale.fromTag(it).localeTag()
                                },
                            )
                            if (isAddWord) {
                                userDictionaryDao()?.insert(entry)
                            } else {
                                userDictionaryDao()?.update(entry)
                            }
                            userDictionaryEntryForDialog = null
                            buildUi()
                        }
                    }) {
                        Text(stringRes(if (isAddWord) {
                            R.string.action__add
                        } else {
                            R.string.action__apply
                        }))
                    }
                },
                dismissButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isAddWord) {
                            TextButton(
                                onClick = {
                                    userDictionaryDao()?.delete(wordEntry)
                                    userDictionaryEntryForDialog = null
                                    buildUi()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(stringRes(R.string.action__delete))
                            }
                        }
                        TextButton(onClick = { userDictionaryEntryForDialog = null }) {
                            Text(stringRes(R.string.action__cancel))
                        }
                    }
                },
            )
        }
    }
}
