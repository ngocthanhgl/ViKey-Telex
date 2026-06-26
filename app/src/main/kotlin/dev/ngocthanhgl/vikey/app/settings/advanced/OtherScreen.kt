package dev.ngocthanhgl.vikey.app.settings.advanced

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.AppTheme
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.M3ColorPickerPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

@Composable
fun OtherScreen() {
    val prefs by FlorisPreferenceStore

    SettingsScaffold(title = stringRes(R.string.settings__other__title)) {
        val scope = rememberCoroutineScope()
        val settingsTheme by prefs.other.settingsTheme.collectAsState()
        val accentColor by prefs.other.accentColor.collectAsState()

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
                icon = Icons.Rounded.Palette,
                value = settingsTheme,
                onSelect = { scope.launch { prefs.other.settingsTheme.set(AppTheme.valueOf(it)) } },
                title = stringRes(R.string.pref__other__settings_theme__label),
                entries = enumDisplayEntriesOf(AppTheme::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ColorPickerPreference(
                icon = Icons.Rounded.WaterDrop,
                title = stringRes(R.string.pref__other__settings_accent_color__label),
                currentColor = accentColor,
                onColorSelected = { scope.launch { prefs.other.accentColor.set(it) } },
                defaultColors = ColorMappings.colors.toList(),
                defaultValueLabel = stringRes(R.string.action__default),
            )
        }
    }
}
