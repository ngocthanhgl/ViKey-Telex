package dev.ngocthanhgl.vikey.ime.text.keyboard

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect

private const val KEY_LENS_SHADER = """
uniform float2 resolution;
uniform shader inputImage;
uniform float refractionAmount;

half4 main(float2 fragCoord) {
    vec2 uv = fragCoord / resolution.xy;
    vec2 center = vec2(0.5, 0.5);
    vec2 offsetVec = uv - center;
    float dist = length(offsetVec);

    float distortion = 1.0 + refractionAmount * 0.2 * dist * dist;
    vec2 distortedUV = center + offsetVec * distortion;

    float ca = refractionAmount * 0.025 * dist * dist;

    vec2 redUV = clamp(distortedUV + offsetVec * ca, 0.0, 1.0);
    vec2 greenUV = clamp(distortedUV, 0.0, 1.0);
    vec2 blueUV = clamp(distortedUV - offsetVec * ca, 0.0, 1.0);

    float r = inputImage.eval(redUV * resolution).r;
    float g = inputImage.eval(greenUV * resolution).g;
    float b = inputImage.eval(blueUV * resolution).b;
    float a = inputImage.eval(greenUV * resolution).a;

    return half4(r, g, b, a);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun rememberLensRenderEffect(
    isPressed: Boolean,
    keySize: Size,
): RenderEffect? {
    val shader = remember { RuntimeShader(KEY_LENS_SHADER) }
    val refraction by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "lensRefraction",
    )
    SideEffect {
        shader.setFloatUniform("resolution", keySize.width, keySize.height)
        shader.setFloatUniform("refractionAmount", refraction)
    }
    return remember(shader) {
        RenderEffect.createRuntimeShaderEffect(shader, "inputImage")
    }
}
