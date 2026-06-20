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

package dev.ngocthanhgl.vikey.app

import android.content.Intent
import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.apptheme.FlorisAppTheme
import dev.ngocthanhgl.vikey.app.ext.ExtensionImportScreenType
import dev.ngocthanhgl.vikey.app.setup.NotificationPermissionState
import dev.ngocthanhgl.vikey.appContext
import dev.ngocthanhgl.vikey.cacheManager
import dev.ngocthanhgl.vikey.lib.compose.LocalPreviewFieldController
import dev.ngocthanhgl.vikey.lib.compose.PreviewKeyboardPill
import dev.ngocthanhgl.vikey.lib.compose.rememberPreviewFieldController
import dev.ngocthanhgl.vikey.lib.util.AppVersionUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import java.util.concurrent.atomic.AtomicBoolean
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.collectIn

enum class AppTheme(val id: String) {
    AUTO("auto"),
    AUTO_AMOLED("auto_amoled"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED_DARK("amoled_dark");
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("LocalNavController not initialized")
}

class FlorisAppActivity : ComponentActivity() {
    private val prefs by FlorisPreferenceStore
    private val appContext by appContext()
    private val cacheManager by cacheManager()
    private var appTheme by mutableStateOf(AppTheme.AUTO)
    private var intentToBeHandled by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen should be installed before calling super.onCreate()
        installSplashScreen().apply {
            setKeepOnScreenCondition { !appContext.preferenceStoreLoaded.value }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs.other.settingsTheme.asFlow().collectIn(lifecycleScope) {
            appTheme = it
        }

        // We defer the setContent call until the datastore model is loaded, until then the splash screen stays drawn
        val isModelLoaded = AtomicBoolean(false)
        appContext.preferenceStoreLoaded.collectIn(lifecycleScope) { loaded ->
            if (!loaded || isModelLoaded.getAndSet(true)) return@collectIn
            // Check if android 13+ is running and the NotificationPermission is not set
            if (AndroidVersion.ATLEAST_API33_T &&
                prefs.internal.notificationPermissionState.get() == NotificationPermissionState.NOT_SET
            ) {
                // update pref value to show the setup screen again
                prefs.internal.isImeSetUp.set(false)
            }
            AppVersionUtils.updateVersionOnInstallAndLastUse(this, prefs)
            setContent {
                ProvideLocalizedResources(resourcesContext = this, appName = R.string.app_name) {
                    FlorisAppTheme(theme = appTheme) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            AppContent()
                        }
                    }
                }
            }
            onNewIntent(intent)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.clipData != null) {
            intentToBeHandled = intent
            return
        }
        intentToBeHandled = null
    }

    @Composable
    private fun AppContent() {
        val navController = rememberNavController()
        val previewFieldController = rememberPreviewFieldController()

        val isImeSetUp by prefs.internal.isImeSetUp.collectAsState()

        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalPreviewFieldController provides previewFieldController,
        ) {
            ProvideDefaultDialogPrefStrings(
                confirmLabel = stringRes(R.string.action__ok),
                dismissLabel = stringRes(R.string.action__cancel),
                neutralLabel = stringRes(R.string.action__default),
            ) {
                Column(
                    modifier = Modifier
                        //.statusBarsPadding()
                        .navigationBarsPadding()
                        .conditional(LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            displayCutoutPadding()
                        }
                        .imePadding(),
                ) {
                    Routes.AppNavHost(
                        modifier = Modifier.weight(1.0f),
                        navController = navController,
                        startDestination = if (isImeSetUp) Routes.Settings.Home::class else Routes.Setup.Screen::class,
                    )
                    PreviewKeyboardPill(previewFieldController)
                }
            }
        }

        LaunchedEffect(intentToBeHandled) {
            val intent = intentToBeHandled
            if (intent != null) {
                if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
                    navController.handleDeepLink(intent)
                } else {
                    val data = if (intent.action == Intent.ACTION_VIEW) {
                        intent.data!!
                    } else {
                        intent.clipData!!.getItemAt(0).uri
                    }
                    val workspace = runCatching { cacheManager.readFromUriIntoCache(data) }.getOrNull()
                    navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY, workspace?.uuid))
                }
            }
            intentToBeHandled = null
        }
    }
}
