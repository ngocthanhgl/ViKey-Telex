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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.ime.smartbar.IncognitoDisplayMode
import dev.ngocthanhgl.vikey.ime.smartbar.InlineSuggestionsStyleCache
import dev.ngocthanhgl.vikey.ime.smartbar.Smartbar
import dev.ngocthanhgl.vikey.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.ngocthanhgl.vikey.ime.text.keyboard.TextKeyboardLayout
import dev.ngocthanhgl.vikey.ime.theme.FlorisImeUi
import dev.ngocthanhgl.vikey.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.snygg.ui.SnyggIcon
import java.io.File

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

    var bgBitmap by remember(bgPhotoPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bgPhotoPath) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, bgPhotoPath)
            bgBitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    InlineSuggestionsStyleCache()

    Box(
        modifier = modifier
            .wrapContentWidth()
            .wrapContentHeight(),
    ) {
        if (bgPhotoPath.isNotBlank() && bgBitmap != null) {
            Image(
                bitmap = bgBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(bgPhotoVis / 100f)
                    .blur(radius = bgPhotoBlur.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            Smartbar()
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
                            painter = painterResource(R.drawable.ic_incognito),
                        )
                    }
                    TextKeyboardLayout(evaluator = evaluator)
                }
            }
            if (bottomPaddingDp > 0.dp) {
                Spacer(modifier = Modifier.height(bottomPaddingDp))
            }
        }
    }
}
