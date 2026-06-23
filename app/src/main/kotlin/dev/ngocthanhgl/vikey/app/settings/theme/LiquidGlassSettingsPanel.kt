package dev.ngocthanhgl.vikey.app.settings.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.ngocthanhgl.vikey.app.FlorisPreferenceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
    val bgPath by prefs.backgroundPhoto.imagePath.collectAsState()
    val bgVisibility by prefs.backgroundPhoto.visibility.collectAsState()
    val bgBlur by prefs.backgroundPhoto.blurRadius.collectAsState()
    val context = LocalContext.current
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) cropUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
            steps = 9,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.lensIdle.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Lens Peak",
            value = lensPeak / 100f,
            valueRange = 0f..30f,
            steps = 14,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.lensPeak.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Height Multiplier",
            value = heightMult / 100f,
            valueRange = 0.5f..5.0f,
            steps = 8,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.heightMultiplier.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Amount Multiplier",
            value = amountMult / 100f,
            valueRange = 0.5f..3.0f,
            steps = 4,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.amountMultiplier.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Text Lift",
            value = textLiftVal / 100f,
            valueRange = 1.0f..2.0f,
            steps = 4,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.textLift.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Press Scale",
            value = pressScaleVal / 100f,
            valueRange = 1.0f..1.5f,
            steps = 9,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.pressScale.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Rebound Damping",
            value = damping / 100f,
            valueRange = 0.05f..0.95f,
            steps = 17,
            onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.reboundDamping.set((v * 100).toInt()) } },
        )
        PrefSlider(
            label = "Rebound Stiffness",
            value = stiffness.toFloat(),
            valueRange = 50f..500f,
            steps = 8,
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

        Text(
            text = "Background Photo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        if (bgPath.isNotBlank()) {
            Text(
                text = "Photo selected",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.56f),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    scope.launch {
                        val file = File(context.filesDir, bgPath)
                        if (file.exists()) file.delete()
                        prefs.backgroundPhoto.imagePath.set("")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(50.dp),
            ) {
                Text("Remove Photo")
            }
        } else {
            Button(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50.dp),
            ) {
                Text("Choose Photo")
            }
        }

        cropUri?.let { uri ->
            CropPhotoDialog(
                imageUri = uri,
                context = context,
                onSave = { path, vis, blur ->
                    scope.launch {
                        prefs.backgroundPhoto.imagePath.set(path)
                        prefs.backgroundPhoto.visibility.set(vis)
                        prefs.backgroundPhoto.blurRadius.set(blur)
                    }
                    cropUri = null
                },
                onDismiss = { cropUri = null },
            )
        }

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
            shape = RoundedCornerShape(50.dp),
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
private fun CropPhotoDialog(
    imageUri: Uri,
    context: Context,
    onSave: (String, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(imageUri) {
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }
    if (bitmap == null) {
        onDismiss()
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var visibility by remember { mutableFloatStateOf(100f) }
    var blurRadius by remember { mutableFloatStateOf(0f) }
    var displayW by remember { mutableFloatStateOf(0f) }
    var displayH by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop Photo") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            displayW = coords.size.width.toFloat()
                            displayH = coords.size.height.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(visibility / 100f)
                            .blur(radius = blurRadius.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val cropW = size.width * 0.9f
                        val cropH = cropW / 2.2f
                        val left = (size.width - cropW) / 2f
                        val top = (size.height - cropH) / 2f
                        val overlay = Color.Black.copy(alpha = 0.5f)
                        val guide = Color.White

                        drawRect(overlay, Offset(0f, 0f), Size(size.width, top))
                        drawRect(overlay, Offset(0f, top + cropH), Size(size.width, size.height - top - cropH))
                        drawRect(overlay, Offset(0f, top), Size(left, cropH))
                        drawRect(overlay, Offset(left + cropW, top), Size(size.width - left - cropW, cropH))

                        drawRect(guide, Offset(left, top), Size(cropW, cropH), style = Stroke(2.dp.toPx()))

                        val r = 6.dp.toPx()
                        drawCircle(guide, r, Offset(left, top))
                        drawCircle(guide, r, Offset(left + cropW, top))
                        drawCircle(guide, r, Offset(left, top + cropH))
                        drawCircle(guide, r, Offset(left + cropW, top + cropH))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Visibility", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = visibility,
                    onValueChange = { visibility = it },
                    valueRange = 0f..100f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Blur", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = blurRadius,
                    onValueChange = { blurRadius = it },
                    valueRange = 0f..30f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dir = File(context.filesDir, "photos")
                    dir.mkdirs()
                    val file = File(dir, "bg.jpg")
                    try {
                        val bw = bitmap.width.toFloat()
                        val bh = bitmap.height.toFloat()
                        val dw = displayW
                        val dh = displayH
                        if (dw > 0f && dh > 0f && scale > 0f) {
                            val fs = if (dw / bw < dh / bh) dw / bw else dh / bh
                            val rw = bw * fs
                            val rh = bh * fs
                            val ox = (dw - rw) / 2f
                            val oy = (dh - rh) / 2f
                            val cw = dw * 0.9f
                            val ch = cw / 2.2f
                            val cl = (dw - cw) / 2f
                            val ct = (dh - ch) / 2f
                            val cx = dw / 2f
                            val cy = dh / 2f
                            fun ix(px: Float) = (px - cx - offsetX) / scale + cx
                            fun iy(py: Float) = (py - cy - offsetY) / scale + cy
                            val bl = ((ix(cl) - ox) / fs).toInt().coerceIn(0, bitmap.width)
                            val bt = ((iy(ct) - oy) / fs).toInt().coerceIn(0, bitmap.height)
                            val br = ((ix(cl + cw) - ox) / fs).toInt().coerceIn(1, bitmap.width)
                            val bb = ((iy(ct + ch) - oy) / fs).toInt().coerceIn(1, bitmap.height)
                            val cropped = Bitmap.createBitmap(bitmap, bl, bt, br - bl, bb - bt)
                            FileOutputStream(file).use { out ->
                                cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            cropped.recycle()
                        } else {
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                        }
                        onSave("photos/bg.jpg", visibility.toInt(), blurRadius.toInt())
                    } catch (_: Exception) {
                        onSave("", 100, 0)
                    }
                },
                shape = RoundedCornerShape(50.dp),
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
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
