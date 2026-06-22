/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

package dev.ngocthanhgl.vikey.ime.window

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import dev.ngocthanhgl.vikey.FlorisImeService
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.ime.input.LocalInputFeedbackController
import dev.ngocthanhgl.vikey.ime.theme.FlorisImeTheme
import org.florisboard.lib.compose.ProvideLocalizedResources

/**
 * Provides the [ImeWindowController] instance this composition tree is associated with.
 */
val LocalWindowController = staticCompositionLocalOf<ImeWindowController> {
    error("This composition local provider is only available within an IME view")
}

/**
 * The main entry point and bridge between the IME dialog view and the composables. It will fill the maximum area
 * available within the accompanying dialog view, and also draw under system bars.
 *
 * The layout direction will be forced to [LayoutDirection.Ltr], to ensure the window positioning logic's left/right
 * corresponds to the physical left/right. For UI components that need to conform to the actual system layout
 * direction, the UI components should be wrapped with [org.florisboard.lib.compose.ProvideActualLayoutDirection].
 *
 * @see ImeRootWindow
 */
@SuppressLint("ViewConstructor")
class ImeRootView(val ims: FlorisImeService) : AbstractComposeView(ims) {
    init {
        isHapticFeedbackEnabled = true
        layoutParams = LayoutParams(
            /* width = */ LayoutParams.MATCH_PARENT,
            /* height = */ LayoutParams.MATCH_PARENT,
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (right == 0 || bottom == 0) return
        val wc = ims.windowController
        if (wc.activeRootInsets.value == ImeInsets.Root.Zero) {
            val bounds = IntRect(left, top, right, bottom)
            val metrics = ims.resources.displayMetrics
            val d = Density(metrics.density, metrics.scaledDensity)
            with(d) { wc.updateRootInsets(ImeInsets.Root.of(bounds)) }
        }
    }

    @Composable
    override fun Content() {
        CompositionLocalProvider(
            LocalInputFeedbackController provides ims.inputFeedbackController,
            LocalWindowController provides ims.windowController,
        ) {
            ProvideLocalizedResources(
                resourcesContext = ims.resourcesContext,
                appName = R.string.app_name,
                forceLayoutDirection = LayoutDirection.Ltr,
            ) {
                FlorisImeTheme {
                    ImeRootWindow()
                }
            }
        }
    }

    override fun getAccessibilityClassName(): CharSequence? {
        return this::class.simpleName
    }
}
