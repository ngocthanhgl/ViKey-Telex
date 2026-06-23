package dev.ngocthanhgl.vikey.app.settings.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.ext.AddonManagementReferenceBox
import dev.ngocthanhgl.vikey.app.ext.ExtensionListScreenType
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.theme.ThemeManager
import dev.ngocthanhgl.vikey.ime.theme.ThemeMode
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponentName
import dev.ngocthanhgl.vikey.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.LocalTimePickerPreference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import kotlinx.coroutines.launch
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

@Composable
fun ThemeScreen() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val prefs by FlorisPreferenceStore
    val themeManager by context.themeManager()

    @Composable
    fun ThemeManager.getThemeLabel(id: ExtensionComponentName): String {
        val configs by indexedThemeConfigs.collectAsState()
        configs.first[id]?.let { return it.label }
        return id.toString()
    }

    SettingsScaffold(title = stringRes(R.string.settings__theme__title)) {
        val scope = rememberCoroutineScope()
        val dayThemeId by prefs.theme.dayThemeId.collectAsState()
        val nightThemeId by prefs.theme.nightThemeId.collectAsState()
        val themeMode by prefs.theme.mode.collectAsState()

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
            M3ListPreference(
                icon = Icons.Outlined.BrightnessAuto,
                value = themeMode,
                onSelect = { scope.launch { prefs.theme.mode.set(ThemeMode.valueOf(it)) } },
                title = stringRes(R.string.pref__theme__mode__label),
                entries = enumDisplayEntriesOf(ThemeMode::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Outlined.WbSunny,
                title = stringRes(R.string.pref__theme__day),
                enabled = themeMode != ThemeMode.ALWAYS_NIGHT,
                onClick = {
                    navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_DAY))
                },
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Outlined.DarkMode,
                title = stringRes(R.string.pref__theme__night),
                enabled = themeMode != ThemeMode.ALWAYS_DAY,
                onClick = {
                    navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_NIGHT))
                },
            )
            SettingsDivider()
            LocalTimePickerPreference(
                pref = prefs.theme.sunriseTime,
                title = stringRes(R.string.pref__theme__sunrise_time__label),
                icon = Icons.Outlined.WbSunny,
                modifier = Modifier.heightIn(min = 56.dp),
            )
            SettingsDivider()
            LocalTimePickerPreference(
                pref = prefs.theme.sunsetTime,
                title = stringRes(R.string.pref__theme__sunset_time__label),
                icon = Icons.Outlined.WbSunny,
                modifier = Modifier.heightIn(min = 56.dp),
            )
            SettingsDivider()
            ColorPickerPreference(
                pref = prefs.theme.accentColor,
                title = stringRes(R.string.pref__theme__theme_accent_color__label),
                defaultValueLabel = stringRes(R.string.action__default),
                icon = Icons.Outlined.Palette,
                defaultColors = ColorMappings.colors,
                showAlphaSlider = false,
                enableAdvancedLayout = true,
                colorOverride = {
                    if (it.isMaterialYou(context)) {
                        Color.Unspecified
                    } else {
                        it
                    }
                },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }

        AddonManagementReferenceBox(type = ExtensionListScreenType.EXT_THEME)
    }
}
