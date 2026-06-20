package dev.ngocthanhgl.vikey.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.lib.util.InputMethodUtils
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current

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
            val isViKeyEnabled by InputMethodUtils.observeIsViKeyEnabled(foregroundOnly = true)
            val isViKeySelected by InputMethodUtils.observeIsViKeySelected(foregroundOnly = true)
            if (!isViKeyEnabled) {
                FlorisErrorCard(
                    modifier = Modifier.padding(8.dp),
                    showIcon = false,
                    text = stringRes(R.string.settings__home__ime_not_enabled),
                    onClick = { InputMethodUtils.showImeEnablerActivity(context) },
                )
            } else if (!isViKeySelected) {
                FlorisWarningCard(
                    modifier = Modifier.padding(8.dp),
                    showIcon = false,
                    text = stringRes(R.string.settings__home__ime_not_selected),
                    onClick = { InputMethodUtils.showImePicker(context) },
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                M3ClickablePreference(
                    icon = Icons.Default.Language,
                    title = stringRes(R.string.settings__localization__title),
                    onClick = { navController.navigate(Routes.Settings.Localization) },
                )
                M3ClickablePreference(
                    icon = Icons.Outlined.Palette,
                    title = stringRes(R.string.settings__theme__title),
                    onClick = { navController.navigate(Routes.Settings.Theme) },
                )
                M3ClickablePreference(
                    icon = Icons.Outlined.Keyboard,
                    title = stringRes(R.string.settings__keyboard__title),
                    onClick = { navController.navigate(Routes.Settings.Keyboard) },
                )
                M3ClickablePreference(
                    icon = Icons.Default.SmartButton,
                    title = stringRes(R.string.settings__smartbar__title),
                    onClick = { navController.navigate(Routes.Settings.Smartbar) },
                )
                M3ClickablePreference(
                    icon = Icons.Default.Spellcheck,
                    title = stringRes(R.string.settings__typing__title),
                    onClick = { navController.navigate(Routes.Settings.Typing) },
                )
                M3ClickablePreference(
                    icon = Icons.Default.Gesture,
                    title = stringRes(R.string.settings__gestures__title),
                    onClick = { navController.navigate(Routes.Settings.Gestures) },
                )
                M3ClickablePreference(
                    icon = Icons.Outlined.Assignment,
                    title = stringRes(R.string.settings__clipboard__title),
                    onClick = { navController.navigate(Routes.Settings.Clipboard) },
                )
                M3ClickablePreference(
                    icon = Icons.Default.SentimentSatisfiedAlt,
                    title = stringRes(R.string.settings__media__title),
                    onClick = { navController.navigate(Routes.Settings.Media) },
                )
                M3ClickablePreference(
                    icon = Icons.Default.Extension,
                    title = stringRes(R.string.ext__home__title),
                    onClick = { navController.navigate(Routes.Ext.Home) },
                )
                M3ClickablePreference(
                    icon = Icons.Outlined.Build,
                    title = stringRes(R.string.settings__other__title),
                    onClick = { navController.navigate(Routes.Settings.Other) },
                )
                M3ClickablePreference(
                    icon = Icons.Outlined.Info,
                    title = stringRes(R.string.about__title),
                    onClick = { navController.navigate(Routes.Settings.About) },
                )
            }
        }
    }
}
