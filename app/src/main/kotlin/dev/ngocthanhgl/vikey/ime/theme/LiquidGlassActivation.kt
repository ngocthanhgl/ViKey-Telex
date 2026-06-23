package dev.ngocthanhgl.vikey.ime.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.ngocthanhgl.vikey.app.FlorisPreferenceModel
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.datastore.model.collectAsState

val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

private const val LIQUID_GLASS_EXTENSION_ID = "dev.ngocthanhgl.vikey.themes.liquidglass"

@Composable
fun ProvideLiquidGlassEnabled(
    activeThemeName: ExtensionComponentName,
    prefs: FlorisPreferenceModel,
    content: @Composable () -> Unit,
) {
    val liquidGlassEnabled by prefs.liquidGlass.enabled.collectAsState()
    val enabled = liquidGlassEnabled || activeThemeName.extensionId == LIQUID_GLASS_EXTENSION_ID
    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides enabled,
        content = content,
    )
}
