package dev.ngocthanhgl.vikey.ime.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.ngocthanhgl.vikey.lib.ext.ExtensionComponentName

val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

private const val LIQUID_GLASS_EXTENSION_ID = "dev.ngocthanhgl.vikey.themes.liquidglass"

@Composable
fun ProvideLiquidGlassEnabled(
    activeThemeName: ExtensionComponentName,
    content: @Composable () -> Unit,
) {
    val enabled = activeThemeName.extensionId == LIQUID_GLASS_EXTENSION_ID
    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides enabled,
        content = content,
    )
}
