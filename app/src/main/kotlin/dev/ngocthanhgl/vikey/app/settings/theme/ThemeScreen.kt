package dev.ngocthanhgl.vikey.app.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.ngocthanhgl.vikey.app.settings.components.M3ColorPickerPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3LocalTimePickerPreference
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.ime.theme.ThemeMode
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.color.ColorMappings
import dev.patrickgold.jetpref.datastore.model.LocalTime
import org.florisboard.lib.compose.stringRes

@Composable
fun ThemeScreen() {
    val navController = LocalNavController.current
    val prefs by FlorisPreferenceStore

    SettingsScaffold(title = stringRes(R.string.settings__theme__title)) {
        val scope = rememberCoroutineScope()
        val themeMode by prefs.theme.mode.collectAsState()
        val sunriseTime by prefs.theme.sunriseTime.collectAsState()
        val sunsetTime by prefs.theme.sunsetTime.collectAsState()
        val accentColor by prefs.theme.accentColor.collectAsState()

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
                icon = Icons.Rounded.BrightnessAuto,
                value = themeMode,
                onSelect = { scope.launch { prefs.theme.mode.set(ThemeMode.valueOf(it)) } },
                title = stringRes(R.string.pref__theme__mode__label),
                entries = enumDisplayEntriesOf(ThemeMode::class).map { it.key.toString() to it.label },
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Rounded.WbSunny,
                title = stringRes(R.string.pref__theme__day),
                enabled = themeMode != ThemeMode.ALWAYS_NIGHT,
                onClick = {
                    navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_DAY))
                },
            )
            SettingsDivider()
            M3ClickablePreference(
                icon = Icons.Rounded.DarkMode,
                title = stringRes(R.string.pref__theme__night),
                enabled = themeMode != ThemeMode.ALWAYS_DAY,
                onClick = {
                    navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_NIGHT))
                },
            )
            SettingsDivider()
            M3LocalTimePickerPreference(
                icon = Icons.Rounded.WbSunny,
                title = stringRes(R.string.pref__theme__sunrise_time__label),
                currentHour = sunriseTime.hour,
                currentMinute = sunriseTime.minute,
                onTimeSelected = { hour, minute ->
                    scope.launch { prefs.theme.sunriseTime.set(LocalTime(hour, minute)) }
                },
            )
            SettingsDivider()
            M3LocalTimePickerPreference(
                icon = Icons.Rounded.WbSunny,
                title = stringRes(R.string.pref__theme__sunset_time__label),
                currentHour = sunsetTime.hour,
                currentMinute = sunsetTime.minute,
                onTimeSelected = { hour, minute ->
                    scope.launch { prefs.theme.sunsetTime.set(LocalTime(hour, minute)) }
                },
            )
            SettingsDivider()
            M3ColorPickerPreference(
                icon = Icons.Rounded.Palette,
                title = stringRes(R.string.pref__theme__theme_accent_color__label),
                currentColor = accentColor,
                onColorSelected = { scope.launch { prefs.theme.accentColor.set(it) } },
                defaultColors = ColorMappings.colors.toList(),
                defaultValueLabel = stringRes(R.string.action__default),
            )
        }

        val kcr by prefs.theme.keyCornerRadius.collectAsState()
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            Icons.Rounded.Square,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringRes(R.string.theme__key_corner_radius),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (kcr == 0) "Default" else "$kcr dp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                var sliderValue by remember(kcr) { mutableFloatStateOf(kcr.toFloat()) }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        scope.launch { prefs.theme.keyCornerRadius.set(sliderValue.toInt()) }
                    },
                    valueRange = 0f..30f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth().padding(start = 56.dp),
                )
            }
        }

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
            LiquidGlassSettingsPanel(prefs)
        }

        AddonManagementReferenceBox(type = ExtensionListScreenType.EXT_THEME)
    }
}
