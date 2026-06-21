package dev.ngocthanhgl.vikey.ime.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

@Composable
fun LiquidGlassEffect(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val blurPx = with(density) { 16.dp.toPx() }
    val refractionHeightPx = with(density) { 10.dp.toPx() }
    val refractionAmountPx = with(density) { 14.dp.toPx() }

    Box(
        modifier = Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    blur(radius = blurPx)
                    colorControls(brightness = 0.03f, saturation = 1.3f)
                    lens(
                        refractionHeight = refractionHeightPx,
                        refractionAmount = refractionAmountPx,
                        chromaticAberration = true,
                    )
                },
                highlight = { Highlight.Ambient },
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            content()
        }
    }
}
