package dev.ngocthanhgl.vikey.app.settings.localization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.core.DisplayLanguageNamesIn
import dev.ngocthanhgl.vikey.lib.FlorisLocale
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes

const val SelectLocaleScreenResultLanguageTag = "SelectLocaleScreen.languageTag"

@Composable
fun SelectLocaleScreen() {
    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current

    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
    val context = LocalContext.current
    val systemLocales =
        FlorisLocale.extendedAvailableLocales(context)
            .filter { locale -> locale.language == "en" || locale.language == "vi" }
            .sortedBy { locale ->
                when (displayLanguageNamesIn) {
                    DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
                    DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
                }.lowercase()
            }

    SettingsScaffold(
        title = stringRes(R.string.settings__localization__subtype_select_locale),
        scrollable = false,
    ) {
        val state = rememberLazyListState()
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = state,
            ) {
                item {
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
                        systemLocales.forEachIndexed { index, systemLocale ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set(SelectLocaleScreenResultLanguageTag, systemLocale.languageTag())
                                        navController.popBackStack()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    text = when (displayLanguageNamesIn) {
                                        DisplayLanguageNamesIn.SYSTEM_LOCALE -> systemLocale.displayName()
                                        DisplayLanguageNamesIn.NATIVE_LOCALE -> systemLocale.displayName(systemLocale)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            if (index < systemLocales.lastIndex) {
                                SettingsDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
