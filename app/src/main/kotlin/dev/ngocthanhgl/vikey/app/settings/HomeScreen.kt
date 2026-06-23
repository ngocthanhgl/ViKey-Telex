package dev.ngocthanhgl.vikey.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Widgets
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.lib.util.InputMethodUtils
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

private data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val summary: String?,
    val onClick: () -> Unit,
)

private data class SettingsSection(
    val header: String,
    val items: List<SettingsItem>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }

    val allSections = remember(navController) {
        listOf(
            SettingsSection(
                header = stringRes(R.string.settings__section__input),
                items = listOf(
                    SettingsItem(Icons.Outlined.Keyboard, stringRes(R.string.settings__keyboard__title), null) {
                        navController.navigate(Routes.Settings.Keyboard)
                    },
                    SettingsItem(Icons.Outlined.Spellcheck, stringRes(R.string.settings__typing__title), null) {
                        navController.navigate(Routes.Settings.Typing)
                    },
                    SettingsItem(Icons.Outlined.PanTool, stringRes(R.string.settings__gestures__title), null) {
                        navController.navigate(Routes.Settings.Gestures)
                    },
                    SettingsItem(Icons.Outlined.Language, stringRes(R.string.settings__localization__title), null) {
                        navController.navigate(Routes.Settings.Localization)
                    },
                ),
            ),
            SettingsSection(
                header = stringRes(R.string.settings__section__personalization),
                items = listOf(
                    SettingsItem(Icons.Outlined.Palette, stringRes(R.string.settings__theme__title), null) {
                        navController.navigate(Routes.Settings.Theme)
                    },
                    SettingsItem(Icons.Outlined.Widgets, stringRes(R.string.settings__smartbar__title), null) {
                        navController.navigate(Routes.Settings.Smartbar)
                    },
                ),
            ),
            SettingsSection(
                header = stringRes(R.string.settings__section__features),
                items = listOf(
                    SettingsItem(Icons.Outlined.FormatListBulleted, stringRes(R.string.settings__clipboard__title), null) {
                        navController.navigate(Routes.Settings.Clipboard)
                    },
                    SettingsItem(Icons.Outlined.EmojiEmotions, stringRes(R.string.settings__media__title), null) {
                        navController.navigate(Routes.Settings.Media)
                    },
                ),
            ),
            SettingsSection(
                header = stringRes(R.string.settings__section__system),
                items = listOf(
                    SettingsItem(Icons.Outlined.Extension, stringRes(R.string.ext__home__title), null) {
                        navController.navigate(Routes.Ext.Home)
                    },
                    SettingsItem(Icons.Outlined.Settings, stringRes(R.string.settings__other__title), null) {
                        navController.navigate(Routes.Settings.Other)
                    },
                    SettingsItem(Icons.Outlined.Info, stringRes(R.string.about__title), null) {
                        navController.navigate(Routes.Settings.About)
                    },
                ),
            ),
        )
    }

    val filteredSections by remember {
        derivedStateOf {
            if (searchQuery.isBlank()) allSections
            else allSections.mapNotNull { section ->
                val filtered = section.items.filter { item ->
                    item.title.contains(searchQuery, ignoreCase = true) ||
                        item.summary?.contains(searchQuery, ignoreCase = true) == true
                }
                if (filtered.isEmpty()) null
                else SettingsSection(section.header, filtered)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.settings__home__title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ImeStatusHero()

            Spacer(Modifier.height(8.dp))
            SettingsSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )
            Spacer(Modifier.height(8.dp))

            for (section in filteredSections) {
                SettingsSectionHeader(text = section.header)
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
                    section.items.forEachIndexed { index, item ->
                        SettingsEntryItem(item = item)
                        if (index < section.items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImeStatusHero() {
    val context = LocalContext.current
    val isViKeyEnabled by InputMethodUtils.observeIsViKeyEnabled(foregroundOnly = true)
    val isViKeySelected by InputMethodUtils.observeIsViKeySelected(foregroundOnly = true)

    when {
        !isViKeyEnabled -> FlorisErrorCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            showIcon = false,
            text = stringRes(R.string.settings__home__ime_not_enabled),
            onClick = { InputMethodUtils.showImeEnablerActivity(context) },
        )
        !isViKeySelected -> FlorisWarningCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            showIcon = false,
            text = stringRes(R.string.settings__home__ime_not_selected),
            onClick = { InputMethodUtils.showImePicker(context) },
        )
        else -> ElevatedCard(
            onClick = { InputMethodUtils.showImePicker(context) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(R.string.settings__home__keyboard_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringRes(R.string.settings__home__status_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text(stringRes(R.string.settings__home__search_placeholder)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
    )
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsEntryItem(item: SettingsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (item.summary != null) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
            modifier = Modifier.size(24.dp),
        )
    }
}
