package dev.ngocthanhgl.vikey.app.settings.advanced

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.AppTheme
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.app.enumDisplayEntriesOf
import dev.ngocthanhgl.vikey.app.settings.components.M3ClickablePreference
import dev.ngocthanhgl.vikey.app.settings.components.M3ListPreference
import dev.ngocthanhgl.vikey.app.settings.components.M3SwitchPreference
import dev.ngocthanhgl.vikey.ime.core.DisplayLanguageNamesIn
import dev.ngocthanhgl.vikey.lib.FlorisLocale
import dev.ngocthanhgl.vikey.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

@Composable
fun OtherScreen() = FlorisScreen {
    title = stringRes(R.string.settings__other__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val settingsTheme by prefs.other.settingsTheme.collectAsState()
        val settingsLanguage by prefs.other.settingsLanguage.collectAsState()
        val showAppIcon by prefs.other.showAppIcon.collectAsState()

        M3ListPreference(
            value = settingsTheme,
            onSelect = { prefs.other.settingsTheme.set(it) },
            icon = Icons.Default.Palette,
            title = stringRes(R.string.pref__other__settings_theme__label),
            entries = enumDisplayEntriesOf(AppTheme::class).map { it.key.toString() to it.label },
        )
        ColorPickerPreference(
            pref = prefs.other.accentColor,
            title = stringRes(R.string.pref__other__settings_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.FormatColorFill,
            defaultColors = ColorMappings.colors,
            showAlphaSlider = false,
            enableAdvancedLayout = true,
            colorOverride = {
                if (it.isMaterialYou(context)) {
                    Color.Unspecified
                } else {
                    it
                }
            }
        )
        M3ListPreference(
            value = settingsLanguage,
            onSelect = { prefs.other.settingsLanguage.set(it) },
            icon = Icons.Default.Language,
            title = stringRes(R.string.pref__other__settings_language__label),
            entries = listOf(
                "auto",
                "ar",
                "bg",
                "bs",
                "ca",
                "ckb",
                "cs",
                "da",
                "de",
                "el",
                "en",
                "eo",
                "es",
                "fa",
                "fi",
                "fr",
                "hr",
                "hu",
                "in",
                "it",
                "iw",
                "ja",
                "ko-KR",
                "ku",
                "lv-LV",
                "mk",
                "nds-DE",
                "nl",
                "no",
                "pl",
                "pt",
                "pt-BR",
                "ru",
                "sk",
                "sl",
                "sr",
                "sv",
                "tr",
                "uk",
                "zgh",
                "zh-CN",
            ).map { languageTag ->
                if (languageTag == "auto") {
                    "auto" to stringRes(R.string.settings__system_default)
                } else {
                    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
                    val locale = FlorisLocale.fromTag(languageTag)
                    locale.languageTag() to when (displayLanguageNamesIn) {
                        DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
                        DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
                        else -> locale.displayName()
                    }
                }
            }
        )
        M3SwitchPreference(
            checked = showAppIcon,
            onCheckedChange = { prefs.other.showAppIcon.set(it) },
            icon = Icons.Default.Preview,
            title = stringRes(R.string.pref__other__show_app_icon__label),
            summary = when {
                AndroidVersion.ATLEAST_API29_Q -> stringRes(R.string.pref__other__show_app_icon__summary_atleast_q)
                else -> null
            },
            enabled = AndroidVersion.ATMOST_API28_P,
        )
        M3ClickablePreference(
            icon = ImageVector.vectorResource(R.drawable.ic_keyboard_keys),
            title = stringRes(R.string.physical_keyboard__title),
            onClick = { navController.navigate(Routes.Settings.PhysicalKeyboard) },
        )
        M3ClickablePreference(
            icon = Icons.Default.Adb,
            title = stringRes(R.string.devtools__title),
            onClick = { navController.navigate(Routes.Devtools.Home) },
        )

        Text(
            text = stringRes(R.string.backup_and_restore__title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        M3ClickablePreference(
            icon = Icons.Default.Archive,
            title = stringRes(R.string.backup_and_restore__back_up__title),
            summary = stringRes(R.string.backup_and_restore__back_up__summary),
            onClick = { navController.navigate(Routes.Settings.Backup) },
        )
        M3ClickablePreference(
            icon = Icons.Default.SettingsBackupRestore,
            title = stringRes(R.string.backup_and_restore__restore__title),
            summary = stringRes(R.string.backup_and_restore__restore__summary),
            onClick = { navController.navigate(Routes.Settings.Restore) },
        )
    }
}
