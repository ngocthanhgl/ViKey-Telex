/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.ngocthanhgl.vikey.ime.text

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.ime.theme.LocalWallpaperBackdrop
import dev.ngocthanhgl.vikey.ime.smartbar.IncognitoDisplayMode
import dev.ngocthanhgl.vikey.ime.smartbar.InlineSuggestionsStyleCache
import dev.ngocthanhgl.vikey.ime.smartbar.Smartbar
import dev.ngocthanhgl.vikey.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.ngocthanhgl.vikey.ime.text.GradientPreset
import dev.ngocthanhgl.vikey.ime.text.keyboard.BackgroundPhotoState
import dev.ngocthanhgl.vikey.ime.text.keyboard.TextKeyboardLayout
import dev.ngocthanhgl.vikey.ime.theme.FlorisImeUi
import dev.ngocthanhgl.vikey.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.snygg.ui.SnyggIcon
import java.io.File
import kotlin.math.roundToInt

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
    bottomPaddingDp: Dp = 0.dp,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()

    val bgPhotoPath by prefs.backgroundPhoto.imagePath.collectAsState()
    val bgPhotoVis by prefs.backgroundPhoto.visibility.collectAsState()
    val bgPhotoBlur by prefs.backgroundPhoto.blurRadius.collectAsState()
    val gradPresetId by prefs.backgroundPhoto.gradientPreset.collectAsState()

    var bgBitmap by remember(bgPhotoPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bgPhotoPath) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, bgPhotoPath)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeFile(file.absolutePath, this)
                    val maxDim = 1920
                    inSampleSize = 1
                    while (outWidth / inSampleSize > maxDim || outHeight / inSampleSize > maxDim) {
                        inSampleSize *= 2
                    }
                    inJustDecodeBounds = false
                }
                try {
                    bgBitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                } catch (_: OutOfMemoryError) {
                    bgBitmap = null
                }
            } else {
                bgBitmap = null
            }
        }
    }

    var photoWindowPos by remember { mutableStateOf(Offset.Zero) }
    var photoBoxSize by remember { mutableStateOf(IntSize.Zero) }

    // Pre-blur the photo ONCE so the per-key glass slices sample exactly what the
    // background shows. Compose's Modifier.blur cannot be captured into the key
    // backdrop; a cached pre-blurred bitmap keeps every key slice pixel-identical
    // to the surrounding background (iOS liquid-glass behaviour) and also removes
    // a per-frame GPU blur.
    val density = LocalDensity.current
    val blurPx = with(density) { bgPhotoBlur.dp.toPx() }.roundToInt()
    var blurredBitmap by remember(blurPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bgBitmap, blurPx) {
        val src = bgBitmap
        blurredBitmap = if (src != null && blurPx > 0) {
            withContext(Dispatchers.Default) { BitmapBlur.blur(src, blurPx) }
        } else {
            null
        }
    }

    val gradBitmap = remember(gradPresetId, photoBoxSize) {
        if (gradPresetId.isNotBlank() && photoBoxSize.width > 0 && photoBoxSize.height > 0) {
            val w = photoBoxSize.width
            val h = photoBoxSize.height
            val preset = GradientPreset.ALL.find { it.id == gradPresetId }
            if (preset != null) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val rad = preset.angleDeg * Math.PI / 180.0
                val cosA = Math.cos(rad).toFloat()
                val sinA = Math.sin(rad).toFloat()
                val len = Math.sqrt((w * w + h * h).toDouble()).toFloat() * 0.65f
                val cx = w / 2f
                val cy = h / 2f
                val shader = android.graphics.LinearGradient(
                    cx - len * cosA, cy - len * sinA,
                    cx + len * cosA, cy + len * sinA,
                    preset.colors.toIntArray(),
                    null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
                android.graphics.Paint().let { paint ->
                    paint.shader = shader
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                }
                bmp
            } else null
        } else null
    }

    val bgBitmapFromSource = if (bgPhotoPath.isNotBlank()) bgBitmap else gradBitmap
    // Prefer the pre-blurred photo while it is being computed; fall back to the raw
    // decode for the brief first-frame window (and whenever blur is disabled).
    val displaySource = if (bgPhotoPath.isNotBlank() && blurPx > 0) {
        blurredBitmap ?: bgBitmapFromSource
    } else {
        bgBitmapFromSource
    }
    val bgPhotoBitmap = remember(displaySource) { displaySource?.asImageBitmap() }

    // Single shared backdrop capturing the wallpaper image. Key glass refracts THIS
    // layer, so the bent content is exactly the background the user sees — no
    // duplicated copy is drawn inside keys.
    val bgBackdrop = rememberLayerBackdrop()

    val bgPhotoState = remember(bgPhotoBitmap, photoWindowPos, photoBoxSize, bgPhotoVis) {
        bgPhotoBitmap?.let { bitmap ->
            if (photoBoxSize != IntSize.Zero) {
                BackgroundPhotoState(
                    bitmap = bitmap,
                    boxSize = photoBoxSize,
                    windowPos = photoWindowPos,
                    alpha = bgPhotoVis / 100f,
                )
            } else null
        }
    }

    InlineSuggestionsStyleCache()

    LaunchedEffect(photoBoxSize) {
        if (photoBoxSize.width > 0 && photoBoxSize.height > 0) {
            val newRatio = photoBoxSize.width.toFloat() / photoBoxSize.height.toFloat()
            val currentRatio = prefs.backgroundPhoto.lastKeyboardAspectRatio.get()
            if (kotlin.math.abs(newRatio - currentRatio) > 0.01f) {
                prefs.backgroundPhoto.lastKeyboardAspectRatio.set(newRatio)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .onGloballyPositioned { coords ->
                photoWindowPos = coords.positionInWindow()
                photoBoxSize = coords.size
            },
    ) {
        if ((bgPhotoPath.isNotBlank() || gradPresetId.isNotBlank()) && bgPhotoBitmap != null) {
            Image(
                bitmap = bgPhotoBitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(bgBackdrop)
                    .alpha(bgPhotoVis / 100f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .then(
                        if ((bgPhotoPath.isNotBlank() || gradPresetId.isNotBlank()) && bgBitmapFromSource != null) {
                            Modifier.drawBehind {
                                val overlayAlpha = (bgPhotoVis / 100f) * 0.35f
                                drawRect(Color.Black.copy(alpha = overlayAlpha), size = size)
                            }
                        } else Modifier
                    ),
            ) {
                Smartbar()
            }
            if (state.isActionsOverflowVisible) {
                QuickActionsOverflowPanel()
            } else {
                Box {
                    val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.collectAsState()
                    val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                        incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                    if (showIncognitoIcon) {
                        SnyggIcon(
                            FlorisImeUi.IncognitoModeIndicator.elementName,
                            modifier = Modifier
                                .matchParentSize()
                                .align(Alignment.Center),
                            imageVector = Icons.Rounded.VisibilityOff,
                        )
                    }
                    CompositionLocalProvider(LocalWallpaperBackdrop provides bgBackdrop) {
            TextKeyboardLayout(evaluator = evaluator, backgroundPhoto = bgPhotoState)
        }
                }
            }
            if (bottomPaddingDp > 0.dp) {
                Spacer(modifier = Modifier.height(bottomPaddingDp))
            }
        }
    }
}