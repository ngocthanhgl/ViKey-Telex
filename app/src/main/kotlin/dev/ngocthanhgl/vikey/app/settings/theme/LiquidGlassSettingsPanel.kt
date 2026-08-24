package dev.ngocthanhgl.vikey.app.settings.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.compose.stringRes
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.ngocthanhgl.vikey.ime.text.GradientPreset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.ngocthanhgl.vikey.app.FlorisPreferenceModel
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.settings.components.SettingsDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val CROP_WIDTH_FRACTION = 0.9f

private object LgDefaults {
    // iOS-tuned: subtle idle refraction keeps resting labels crisp; a stronger
    // press pop plus a slightly softer horizontal displacement sells the glass.
    const val lensIdle = 300
    const val lensPeak = 900
    const val heightMultiplier = 250
    const val amountMultiplier = 120
    const val textLift = 140
    const val pressScale = 108
    const val chromaticEnabled = true
    const val depthEnabled = true
    const val rippleEnabled = true
    const val reboundDamping = 28
    const val reboundStiffness = 220
}

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
    val keyboardAspectRatio by prefs.backgroundPhoto.lastKeyboardAspectRatio.collectAsState()
    val gradPresetId by prefs.backgroundPhoto.gradientPreset.collectAsState()
    val context = LocalContext.current
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var showGradientPicker by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) cropUri = uri
    }
    val enabled by prefs.liquidGlass.enabled.collectAsState()

    SettingsSwitch(
        icon = Icons.Rounded.ToggleOn,
        label = stringRes(R.string.liquid_glass__enable),
        checked = enabled,
        onCheckedChange = { scope.launch { prefs.liquidGlass.enabled.set(it) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.BlurOn,
        label = stringRes(R.string.liquid_glass__lens_idle),
        value = lensIdle / 100f,
        valueRange = 0f..20f,
        steps = 9,
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.lensIdle.set((v * 100).toInt()) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.CenterFocusStrong,
        label = stringRes(R.string.liquid_glass__lens_peak),
        value = lensPeak / 100f,
        valueRange = 0f..30f,
        steps = 14,
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.lensPeak.set((v * 100).toInt()) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.LineWeight,
        label = stringRes(R.string.liquid_glass__height_multiplier),
        value = heightMult / 100f,
        valueRange = 0.5f..5.0f,
        steps = 8,
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.heightMultiplier.set((v * 100).toInt()) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.Straighten,
        label = stringRes(R.string.liquid_glass__amount_multiplier),
        value = amountMult / 100f,
        valueRange = 0.5f..3.0f,
        steps = 4,
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.amountMultiplier.set((v * 100).toInt()) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.TextIncrease,
        label = stringRes(R.string.liquid_glass__text_lift),
        value = textLiftVal / 100f,
        valueRange = 1.0f..2.0f,
        steps = 4,
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.textLift.set((v * 100).toInt()) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.ZoomIn,
        label = stringRes(R.string.liquid_glass__press_scale),
        value = pressScaleVal / 100f,
        valueRange = 1.0f..1.5f,
        steps = 9,
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.pressScale.set((v * 100).toInt()) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.Speed,
        label = stringRes(R.string.liquid_glass__rebound_damping),
        value = damping / 100f,
        valueRange = 0.05f..0.95f,
        steps = 17,
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.reboundDamping.set((v * 100).toInt()) } },
    )
    SettingsDivider()

    SettingsSlider(
        icon = Icons.Rounded.Bolt,
        label = stringRes(R.string.liquid_glass__rebound_stiffness),
        value = stiffness.toFloat(),
        valueRange = 50f..500f,
        steps = 8,
        formatValue = { it.toInt().toString() },
        onValueChangeFinished = { v -> scope.launch { prefs.liquidGlass.reboundStiffness.set(v.toInt()) } },
    )
    SettingsDivider()

    SettingsSwitch(
        icon = Icons.Rounded.Palette,
        label = stringRes(R.string.liquid_glass__chromatic_aberration),
        checked = chromatic,
        onCheckedChange = { scope.launch { prefs.liquidGlass.chromaticEnabled.set(it) } },
    )
    SettingsDivider()

    SettingsSwitch(
        icon = Icons.Rounded.Layers,
        label = stringRes(R.string.liquid_glass__depth_effect),
        checked = depth,
        onCheckedChange = { scope.launch { prefs.liquidGlass.depthEnabled.set(it) } },
    )
    SettingsDivider()

    SettingsSwitch(
        icon = Icons.Rounded.WaterDrop,
        label = stringRes(R.string.liquid_glass__ripple_wave),
        checked = ripple,
        onCheckedChange = { scope.launch { prefs.liquidGlass.rippleEnabled.set(it) } },
    )

    Spacer(Modifier.height(16.dp))

    Text(
        text = stringRes(R.string.liquid_glass__background_photo),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
    )

    if (bgPath.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val file = File(context.filesDir, bgPath)
                        if (file.exists()) file.delete()
                        prefs.backgroundPhoto.imagePath.set("")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(50.dp),
            ) { Text(stringRes(R.string.liquid_glass__remove_photo)) }
            OutlinedButton(
                onClick = { showGradientPicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50.dp),
            ) { Text("Gradient") }
        }
    } else if (gradPresetId.isNotBlank()) {
        val preset = GradientPreset.byId[gradPresetId]
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = preset?.label ?: gradPresetId,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = {
                scope.launch { prefs.backgroundPhoto.gradientPreset.set("") }
            }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove gradient",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    scope.launch { prefs.backgroundPhoto.gradientPreset.set("") }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(50.dp),
            ) { Text("Remove") }
            OutlinedButton(
                onClick = { showGradientPicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50.dp),
            ) { Text("Change") }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50.dp),
            ) { Text(stringRes(R.string.liquid_glass__choose_photo)) }
            OutlinedButton(
                onClick = { showGradientPicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50.dp),
            ) { Text("Gradient") }
        }
    }

    cropUri?.let { uri ->
        CropPhotoDialog(
            imageUri = uri,
            context = context,
            aspectRatio = keyboardAspectRatio,
            initialVisibility = bgVisibility.toFloat(),
            initialBlur = bgBlur.toFloat(),
            onSave = { path, vis, blur ->
                scope.launch {
                    prefs.backgroundPhoto.imagePath.set(path)
                    prefs.backgroundPhoto.gradientPreset.set("")
                    prefs.backgroundPhoto.visibility.set(vis)
                    prefs.backgroundPhoto.blurRadius.set(blur)
                    cropUri = null
                }
            },
            onDismiss = { cropUri = null },
        )
    }

    if (showGradientPicker) {
        GradientPickerDialog(
            currentId = gradPresetId.ifEmpty { null },
            onSelect = { preset ->
                scope.launch {
                    prefs.backgroundPhoto.imagePath.set("")
                    prefs.backgroundPhoto.gradientPreset.set(preset.id)
                }
                showGradientPicker = false
            },
            onDismiss = { showGradientPicker = false },
        )
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = {
            scope.launch {
                prefs.liquidGlass.lensIdle.set(LgDefaults.lensIdle)
                prefs.liquidGlass.lensPeak.set(LgDefaults.lensPeak)
                prefs.liquidGlass.heightMultiplier.set(LgDefaults.heightMultiplier)
                prefs.liquidGlass.amountMultiplier.set(LgDefaults.amountMultiplier)
                prefs.liquidGlass.textLift.set(LgDefaults.textLift)
                prefs.liquidGlass.pressScale.set(LgDefaults.pressScale)
                prefs.liquidGlass.chromaticEnabled.set(LgDefaults.chromaticEnabled)
                prefs.liquidGlass.depthEnabled.set(LgDefaults.depthEnabled)
                prefs.liquidGlass.rippleEnabled.set(LgDefaults.rippleEnabled)
                prefs.liquidGlass.reboundDamping.set(LgDefaults.reboundDamping)
                prefs.liquidGlass.reboundStiffness.set(LgDefaults.reboundStiffness)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(50.dp),
    ) {
        Text(stringRes(R.string.liquid_glass__reset_to_defaults))
    }
}

@Composable
private fun SettingsIconCircle(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconCircle(icon)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSlider(
    icon: ImageVector,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    formatValue: (Float) -> String = { "%.2f".format(it) },
    onValueChangeFinished: (Float) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { sliderValue = value }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconCircle(icon)
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatValue(sliderValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp),
        )
    }
}

@Composable
private fun CropPhotoDialog(
    imageUri: Uri,
    context: Context,
    aspectRatio: Float,
    initialVisibility: Float = 100f,
    initialBlur: Float = 0f,
    onSave: (String, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(imageUri) {
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            val opts = BitmapFactory.Options().apply {
                inMutable = true
            }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }
    if (bitmap == null) {
        onDismiss()
        return
    }

    DisposableEffect(bitmap) {
        onDispose { bitmap.recycle() }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var visibility by remember { mutableFloatStateOf(initialVisibility) }
    var blurRadius by remember { mutableFloatStateOf(initialBlur) }
    var displayW by remember { mutableFloatStateOf(0f) }
    var displayH by remember { mutableFloatStateOf(0f) }
    var imageLayoutSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {},
        text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) }
                Text(
                    text = stringRes(R.string.liquid_glass__crop_photo),
                    style = MaterialTheme.typography.titleMedium,
                )
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
                                val fs = dw / bw
                                val ox = 0f
                                val oy = (dh - (bh * fs)) / 2f
                                val cw = dw * CROP_WIDTH_FRACTION
                                val ch = cw / aspectRatio
                                val cl = (dw - cw) / 2f
                                val ct = (dh - ch) / 2f
                                val cx = dw / 2f
                                val cy = dh / 2f
                                fun ix(px: Float) = (px - cx - offsetX) / scale + cx
                                fun iy(py: Float) = (py - cy - offsetY) / scale + cy
                                val bl = ((ix(cl) - ox) / fs).toInt().coerceIn(0, bitmap.width)
                                val bt = ((iy(ct) - oy) / fs).toInt().coerceIn(0, bitmap.height)
                                val br = ((ix(cl + cw) - ox) / fs).toInt().coerceIn(bl + 1, bitmap.width)
                                val bb = ((iy(ct + ch) - oy) / fs).toInt().coerceIn(bt + 1, bitmap.height)
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
                        } catch (e: Exception) {
                            context.showShortToastSync("Crop failed: ${e.message}")
                            onSave("", 100, 0)
                        }
                    },
                    shape = RoundedCornerShape(50.dp),
                ) { Text(stringRes(R.string.action__apply)) }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        displayW = coords.size.width.toFloat()
                        displayH = coords.size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                            val bw = bitmap.width.toFloat()
                            val bh = bitmap.height.toFloat()
                            val dw = displayW
                            val dh = displayH
                            if (dw > 0f && dh > 0f) {
                                val contentW = dw * scale
                                val contentH = (bh * dw / bw) * scale
                                val cropW = dw * CROP_WIDTH_FRACTION
                                val cropH = cropW / aspectRatio
                                val maxOffX = ((contentW - cropW) / 2f).coerceAtLeast(0f)
                                val maxOffY = ((contentH - cropH) / 2f).coerceAtLeast(0f)
                                offsetX = offsetX.coerceIn(-maxOffX, maxOffX)
                                offsetY = offsetY.coerceIn(-maxOffY, maxOffY)
                            }
                            // Re-clamp using actual Image layout size from onGloballyPositioned
                            val ls = imageLayoutSize
                            if (ls.width > 0 && ls.height > 0 && dw > 0f && dh > 0f) {
                                val actualContentW = ls.width.toFloat() * scale
                                val actualContentH = ls.height.toFloat() * scale
                                val cropW2 = dw * CROP_WIDTH_FRACTION
                                val cropH2 = cropW2 / aspectRatio
                                val maxOffX2 = ((actualContentW - cropW2) / 2f).coerceAtLeast(0f)
                                val maxOffY2 = ((actualContentH - cropH2) / 2f).coerceAtLeast(0f)
                                offsetX = offsetX.coerceIn(-maxOffX2, maxOffX2)
                                offsetY = offsetY.coerceIn(-maxOffY2, maxOffY2)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            imageLayoutSize = coords.size
                        }
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
                    val cropW = size.width * CROP_WIDTH_FRACTION
                    val cropH = cropW / aspectRatio
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

            Spacer(Modifier.height(12.dp))

            Text(stringRes(R.string.liquid_glass__visibility), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = visibility,
                onValueChange = { visibility = it },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringRes(R.string.liquid_glass__blur), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = blurRadius,
                onValueChange = { blurRadius = it },
                valueRange = 0f..30f,
                steps = 29,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    },
)
}

@Composable
private fun GradientPreview(preset: GradientPreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = preset.colors.map { Color(it) },
            ),
        )
    )
}

@Composable
private fun GradientPickerDialog(
    currentId: String?,
    onSelect: (GradientPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gradient Presets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientPreset.ALL.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelect(preset) }
                                    .then(
                                        if (preset.id == currentId) {
                                            Modifier.border(
                                                2.dp,
                                                Color(0xFF6200EE),
                                                RoundedCornerShape(12.dp),
                                            )
                                        } else Modifier
                                    ),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GradientPreview(
                                        preset = preset,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    )
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                                    )
                                }
                            }
                        }
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
