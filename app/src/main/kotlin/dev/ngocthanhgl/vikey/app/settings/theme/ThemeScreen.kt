package dev.ngocthanhgl.vikey.app.settings.theme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

import com.composables.lucide.Lucide
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.ext.AddonManagementReferenceBox
import dev.ngocthanhgl.vikey.app.ext.ExtensionListScreenType
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.ime.theme.ThemeManager
import dev.ngocthanhgl.vikey.ime.theme.ThemeMode
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponentName
import dev.ngocthanhgl.vikey.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.LocalTimePickerPreference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

@Composable
fun ThemeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__theme__title)

    val context = LocalContext.current
    val navController = LocalNavController.current
    val themeManager by context.themeManager()

    @Composable
    fun ThemeManager.getThemeLabel(id: ExtensionComponentName): String {
        val configs by indexedThemeConfigs.collectAsState()
        configs.first[id]?.let { return it.label }
        return id.toString()
    }

    content {
        val scope = rememberCoroutineScope()
        val dayThemeId by prefs.theme.dayThemeId.collectAsState()
        val nightThemeId by prefs.theme.nightThemeId.collectAsState()
        val themeMode by prefs.theme.mode.collectAsState()

        M3ListPreference(
            value = themeMode,
            onSelect = { scope.launch { prefs.theme.mode.set(ThemeMode.valueOf(it)) } },
            icon = Lucide.SunMoon,
            title = stringRes(R.string.pref__theme__mode__label),
            entries = enumDisplayEntriesOf(ThemeMode::class).map { it.key.toString() to it.label },
        )
        M3ClickablePreference(
            icon = Lucide.Sun,
            title = stringRes(R.string.pref__theme__day),
            summary = themeManager.getThemeLabel(dayThemeId),
            enabled = themeMode != ThemeMode.ALWAYS_NIGHT,
            onClick = {
                navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_DAY))
            },
        )
        M3ClickablePreference(
            icon = Lucide.Moon,
            title = stringRes(R.string.pref__theme__night),
            summary = themeManager.getThemeLabel(nightThemeId),
            enabled = themeMode != ThemeMode.ALWAYS_DAY,
            onClick = {
                navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_NIGHT))
            },
        )
        LocalTimePickerPreference(
            pref = prefs.theme.sunriseTime,
            title = stringRes(R.string.pref__theme__sunrise_time__label),
            icon = Lucide.Sunrise,
        )
        LocalTimePickerPreference(
            pref = prefs.theme.sunsetTime,
            title = stringRes(R.string.pref__theme__sunset_time__label),
            icon = Lucide.Sunset,
        )
        ColorPickerPreference(
            pref = prefs.theme.accentColor,
            title = stringRes(R.string.pref__theme__theme_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Lucide.Palette,
            defaultColors = ColorMappings.colors,
            showAlphaSlider = false,
            enableAdvancedLayout = true,
            colorOverride = {
                if (it.isMaterialYou(context)) {
                    Color.Unspecified
                } else {
                    it
                }
            }
        )

        AddonManagementReferenceBox(type = ExtensionListScreenType.EXT_THEME)
    }
}
