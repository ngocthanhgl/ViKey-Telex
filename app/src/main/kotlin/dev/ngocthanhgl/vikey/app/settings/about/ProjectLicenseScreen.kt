package dev.ngocthanhgl.vikey.app.settings.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import dev.ngocthanhgl.vikey.lib.io.FlorisRef
import dev.ngocthanhgl.vikey.lib.io.loadTextAsset
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.stringRes

@Composable
fun ProjectLicenseScreen() {
    val context = LocalContext.current

    SettingsScaffold(
        title = stringRes(R.string.about__project_license__title),
        scrollable = false,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .florisVerticalScroll()
                    .florisHorizontalScroll(),
            ) {
                val licenseText = FlorisRef.assets("license/project_license.txt").loadTextAsset(
                    context
                ).getOrElse {
                    stringRes(R.string.about__project_license__error_license_text_failed, "error_message" to (it.message ?: ""))
                }
                Text(
                    text = licenseText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    softWrap = false,
                )
            }
        }
    }
}
