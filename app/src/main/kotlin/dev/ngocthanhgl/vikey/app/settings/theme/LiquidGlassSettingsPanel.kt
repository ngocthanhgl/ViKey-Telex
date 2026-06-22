package dev.ngocthanhgl.vikey.app.settings.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.patrickgold.jetpref.datastore.model.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.app.FlorisPreferenceModel
import kotlinx.coroutines.launch

@Composable
fun LiquidGlassSettingsPanel(prefs: FlorisPreferenceModel) {
    val scope = rememberCoroutineScope()

    val lensIdle by prefs.liquidGlass.lensIdle.collectAsState()
    val lensPeak by prefs.liquidGlass.lensPeak.collectAsState()
    val heightMult by prefs.liquidGlass.heightMultiplier.collectAsState()
    val amountMult by prefs.liquidGlass.amountMultiplier.collectAsState()
    val textLiftVal by prefs.liquidGlass.textLift.collectAsState()
    val pressScaleVal by prefs.liquidGlass.pressScale.collectAsState()
    val chromatic by prefs.liquidGlass.chromaticEnabled.collectAsState()
    val depth by prefs.liquidGlass.depthEnabled.collectAsState()
    val ripple by prefs.liquidGlass.rippleEnabled.collectAsState()
    val damping by prefs.liquidGlass.reboundDamping.collectAsState()
    val stiffness by prefs.liquidGlass.reboundStiffness.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Liquid Glass",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        PrefSlider(
            label = "Lens Idle",
            value = lensIdle / 100f,
            valueRange = 0f..20f,
            steps = 39,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.lensIdle.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Lens Peak",
            value = lensPeak / 100f,
            valueRange = 0f..30f,
            steps = 59,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.lensPeak.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Height Multiplier",
            value = heightMult / 100f,
            valueRange = 0.5f..5.0f,
            steps = 44,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.heightMultiplier.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Amount Multiplier",
            value = amountMult / 100f,
            valueRange = 0.5f..3.0f,
            steps = 24,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.amountMultiplier.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Text Lift",
            value = textLiftVal / 100f,
            valueRange = 1.0f..2.0f,
            steps = 19,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.textLift.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Press Scale",
            value = pressScaleVal / 100f,
            valueRange = 1.0f..1.5f,
            steps = 49,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.pressScale.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Rebound Damping",
            value = damping / 100f,
            valueRange = 0.05f..0.95f,
            steps = 89,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.reboundDamping.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Rebound Stiffness",
            value = stiffness.toFloat(),
            valueRange = 50f..500f,
            steps = 44,
            formatValue = { it.toInt().toString() },
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.reboundStiffness.set(v.toInt()) } },
        )

        Spacer(Modifier.height(8.dp))

        PrefSwitch(
            label = "Chromatic Aberration",
            checked = chromatic,
            onCheckedChange = { scope.launch { prefs.liquidGlass.chromaticEnabled.set(it) } },
        )
        PrefSwitch(
            label = "Depth Effect",
            checked = depth,
            onCheckedChange = { scope.launch { prefs.liquidGlass.depthEnabled.set(it) } },
        )
        PrefSwitch(
            label = "Ripple Wave",
            checked = ripple,
            onCheckedChange = { scope.launch { prefs.liquidGlass.rippleEnabled.set(it) } },
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    prefs.liquidGlass.lensIdle.set(500)
                    prefs.liquidGlass.lensPeak.set(800)
                    prefs.liquidGlass.heightMultiplier.set(250)
                    prefs.liquidGlass.amountMultiplier.set(150)
                    prefs.liquidGlass.textLift.set(140)
                    prefs.liquidGlass.pressScale.set(108)
                    prefs.liquidGlass.chromaticEnabled.set(true)
                    prefs.liquidGlass.depthEnabled.set(true)
                    prefs.liquidGlass.rippleEnabled.set(true)
                    prefs.liquidGlass.reboundDamping.set(28)
                    prefs.liquidGlass.reboundStiffness.set(220)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Reset to Defaults")
        }
    }
}

@Composable
private fun PrefSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    formatValue: (Float) -> String = { "%.2f".format(it) },
    onValueChangeFinished: (Float) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { sliderValue = value }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = formatValue(sliderValue),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.56f),
        )
    }
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChangeFinished(sliderValue) },
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrefSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
