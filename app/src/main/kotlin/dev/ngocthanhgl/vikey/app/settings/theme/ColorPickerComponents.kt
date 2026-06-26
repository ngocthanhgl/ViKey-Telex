package dev.ngocthanhgl.vikey.app.settings.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.florisboard.lib.color.Hsv
import org.florisboard.lib.color.toColor
import org.florisboard.lib.color.toHsv

private val hueGradientStops = listOf(
    0f to Color.Red, 1f / 6f to Color.Yellow, 2f / 6f to Color.Green,
    3f / 6f to Color.Cyan, 4f / 6f to Color.Blue, 5f / 6f to Color.Magenta, 1f to Color.Red,
)

@Composable
fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (hue / 360f).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset -> onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f) }
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.horizontalGradient(*hueGradientStops.toTypedArray()),
                cornerRadius = CornerRadius(14.dp.toPx()),
                size = size,
            )
        }
        Box(
            modifier = Modifier
                .offset(x = (maxWidth * fraction - 10.dp).coerceAtLeast(0.dp))
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

@Composable
fun SatBrightPanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onSatBrightChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = remember(hue) { Hsv(hue, 1f, 1f).toColor() }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSatBrightChange(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        (1f - offset.y / size.height).coerceIn(0f, 1f),
                    )
                }
                detectDragGestures { change, _ ->
                    change.consume()
                    onSatBrightChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (1f - change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(color = hueColor, cornerRadius = CornerRadius(12.dp.toPx()), size = size)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
                cornerRadius = CornerRadius(12.dp.toPx()),
                size = size,
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                cornerRadius = CornerRadius(12.dp.toPx()),
                size = size,
            )
        }
        val cx = with(density) { saturation * 200.dp.toPx() }
        val cy = with(density) { (1f - value) * 200.dp.toPx() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color.White, 7.dp.toPx(), Offset(cx, cy), style = Stroke(2.5.dp.toPx()))
            drawCircle(Color.Black, 7.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
fun ColorPreviewCircle(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (color.alpha < 0.05f) {
            Text("#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetSwatches(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (color in colors) {
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .background(color)
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (color.toHsv().value > 0.6f && color.toHsv().saturation < 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    defaultColors: List<Color>,
    defaultValueLabel: String?,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    onResetDefault: () -> Unit,
) {
    val initHsv = if (initialColor == Color.Unspecified) Hsv(0f, 0f, 1f) else initialColor.toHsv()
    var hue by remember { mutableStateOf(initHsv.hue) }
    var saturation by remember { mutableStateOf(initHsv.saturation) }
    var value by remember { mutableStateOf(initHsv.value) }
    var hexInput by remember {
        mutableStateOf(
            "#${initialColor.value.toLong().toString(16).takeLast(6).uppercase()}"
        )
    }

    fun syncHex(c: Color) {
        hexInput = "#${c.value.toLong().toString(16).takeLast(6).uppercase()}"
    }

    fun fromHex(hex: String) {
        val h = hex.removePrefix("#")
        if (h.length == 6) {
            runCatching {
                val c = Color(h.toLong(16) or 0xFF000000UL.toLong())
                val hsv = c.toHsv()
                hue = hsv.hue; saturation = hsv.saturation; value = hsv.value
            }
        }
        hexInput = hex
    }

    val currentColor = Hsv(hue, saturation, value).toColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ColorPreviewCircle(color = currentColor)
                Spacer(Modifier.height(16.dp))
                HueSlider(hue = hue, onHueChange = { h -> hue = h; syncHex(Hsv(h, saturation, value).toColor()) })
                Spacer(Modifier.height(12.dp))
                SatBrightPanel(
                    hue = hue, saturation = saturation, value = value,
                    onSatBrightChange = { s, v ->
                        saturation = s; value = v; syncHex(Hsv(hue, s, v).toColor())
                    },
                )
                Spacer(Modifier.height(16.dp))
                PresetSwatches(
                    colors = defaultColors, selectedColor = currentColor,
                    onColorSelected = { c ->
                        val hsv = c.toHsv(); hue = hsv.hue
                        saturation = hsv.saturation; value = hsv.value; syncHex(c)
                    },
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { fromHex(it) },
                    label = { Text("Hex") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("OK") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (defaultValueLabel != null) {
                    TextButton(onClick = onResetDefault) { Text(defaultValueLabel) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
