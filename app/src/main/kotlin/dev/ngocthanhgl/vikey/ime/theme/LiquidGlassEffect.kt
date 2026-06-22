package dev.ngocthanhgl.vikey.ime.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.ngocthanhgl.vikey.FlorisImeService

@Composable
fun LiquidGlassEffect(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val window = remember(context) {
        (context as? FlorisImeService)?.window?.window
    }

    LaunchedEffect(enabled, window) {
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(if (enabled) 30 else 0)
        }
    }

    content()
}
