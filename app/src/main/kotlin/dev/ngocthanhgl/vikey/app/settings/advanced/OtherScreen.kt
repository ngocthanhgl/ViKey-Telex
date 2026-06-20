package dev.ngocthanhgl.vikey.app.settings.advanced
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.AppTheme
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

@Composable
fun OtherScreen() = FlorisScreen {
    title = stringRes(R.string.settings__other__title)

    val context = LocalContext.current

    content {
        val scope = rememberCoroutineScope()
        val settingsTheme by prefs.other.settingsTheme.collectAsState()

        M3ListPreference(
            value = settingsTheme,
            onSelect = { scope.launch { prefs.other.settingsTheme.set(AppTheme.valueOf(it)) } },
            icon = Icons.Default.Palette,
            title = stringRes(R.string.pref__other__settings_theme__label),
            entries = enumDisplayEntriesOf(AppTheme::class).map { it.key.toString() to it.label },
        )
        ColorPickerPreference(
            pref = prefs.other.accentColor,
            title = stringRes(R.string.pref__other__settings_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.WaterDrop,
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
    }
}
