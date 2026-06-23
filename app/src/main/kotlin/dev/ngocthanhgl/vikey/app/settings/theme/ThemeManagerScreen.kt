package dev.ngocthanhgl.vikey.app.settings.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import dev.ngocthanhgl.vikey.extensionManager
import dev.ngocthanhgl.vikey.ime.theme.ThemeExtensionComponent
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponentName
import dev.ngocthanhgl.vikey.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

enum class ThemeManagerScreenAction(val id: String) {
    SELECT_DAY("select-day"),
    SELECT_NIGHT("select-night");
}

@Composable
fun ThemeManagerScreen(action: ThemeManagerScreenAction?) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val extensionManager by context.extensionManager()
    val themeManager by context.themeManager()
    val scope = rememberCoroutineScope()

    val indexedThemeExtensions by extensionManager.themes.collectAsState()
    val extGroupedThemes = remember(indexedThemeExtensions) {
        buildMap<String, List<ThemeExtensionComponent>> {
            for (ext in indexedThemeExtensions) {
                put(ext.meta.id, ext.themes)
            }
        }.mapValues { (_, configs) -> configs.sortedBy { it.label } }
    }

    fun getThemeIdPref() = when (action) {
        ThemeManagerScreenAction.SELECT_DAY -> prefs.theme.dayThemeId
        ThemeManagerScreenAction.SELECT_NIGHT -> prefs.theme.nightThemeId
        null -> error("ThemeManager screen action must not be null")
    }

    fun setTheme(extId: String, componentId: String) {
        val extComponentName = ExtensionComponentName(extId, componentId)
        when (action) {
            ThemeManagerScreenAction.SELECT_DAY,
            ThemeManagerScreenAction.SELECT_NIGHT -> scope.launch {
                getThemeIdPref().set(extComponentName)
            }
            null -> {}
        }
    }

    val activeThemeId by when (action) {
        ThemeManagerScreenAction.SELECT_DAY,
        ThemeManagerScreenAction.SELECT_NIGHT
            -> getThemeIdPref().collectAsState()
        null -> mutableStateOf<ExtensionComponentName?>(null)
    }

    SettingsScaffold(title = stringRes(when (action) {
        ThemeManagerScreenAction.SELECT_DAY -> R.string.settings__theme_manager__title_day
        ThemeManagerScreenAction.SELECT_NIGHT -> R.string.settings__theme_manager__title_night
        else -> error("Theme manager screen action must not be null")
    })) {
        DisposableEffect(activeThemeId) {
            themeManager.previewThemeId.value = activeThemeId
            onDispose {
                themeManager.previewThemeId.value = null
            }
        }

        for ((extensionId, configs) in extGroupedThemes) key(extensionId) {
            val ext = extensionManager.getExtensionById(extensionId)!!
            Text(
                text = ext.meta.title,
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
                configs.forEachIndexed { index, config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setTheme(extensionId, config.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = activeThemeId.extensionId == extensionId &&
                                activeThemeId.componentId == config.id,
                            onClick = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = config.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            imageVector = if (config.isNightTheme) {
                                Icons.Default.DarkMode
                            } else {
                                Icons.Default.LightMode
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                        )
                    }
                    if (index < configs.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }

        Text(
            text = "Liquid Glass",
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
            LiquidGlassSettingsPanel(prefs)
        }
    }
}
