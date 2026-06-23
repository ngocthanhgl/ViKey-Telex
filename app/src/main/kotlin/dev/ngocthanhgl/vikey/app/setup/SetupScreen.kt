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

package dev.ngocthanhgl.vikey.app.setup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.FlorisAppActivity
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.app.LocalNavController
import dev.ngocthanhgl.vikey.app.Routes
import dev.ngocthanhgl.vikey.lib.util.InputMethodUtils
import dev.ngocthanhgl.vikey.lib.util.launchActivity
import dev.ngocthanhgl.vikey.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.stringRes

private data class StepState(
    val currentAuto: Int,
    val currentManual: Int,
) {
    fun getCurrent(): Int = if (currentManual >= 0 && currentAuto >= currentManual) currentManual else currentAuto
    fun isCompleted(stepId: Int): Boolean = stepId <= currentAuto
    fun isActive(stepId: Int): Boolean = stepId == getCurrent()

    companion object {
        val Saver = Saver<StepState, ArrayList<Int>>(
            save = { arrayListOf(it.currentAuto, it.currentManual) },
            restore = { StepState(it[0], it[1]) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val isViKeyEnabled by InputMethodUtils.observeIsViKeyEnabled(foregroundOnly = true)
    val isViKeySelected by InputMethodUtils.observeIsViKeySelected(foregroundOnly = true)
    val hasNotificationPermission by prefs.internal.notificationPermissionState.collectAsState()

    val requestNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        scope.launch {
            prefs.internal.notificationPermissionState.set(
                if (isGranted) NotificationPermissionState.GRANTED else NotificationPermissionState.DENIED
            )
        }
    }

    val autoStep = remember { mutableStateOf(Steps.EnableIme.id) }
    LaunchedEffect(isViKeyEnabled, isViKeySelected, hasNotificationPermission) {
        autoStep.value = when {
            !isViKeyEnabled -> Steps.EnableIme.id
            !isViKeySelected -> Steps.SelectIme.id
            hasNotificationPermission == NotificationPermissionState.NOT_SET && AndroidVersion.ATLEAST_API33_T -> Steps.SelectNotification.id
            else -> Steps.FinishUp.id
        }
    }

    var stepState by rememberSaveable(saver = StepState.Saver) {
        val initStep = when {
            !isViKeyEnabled -> Steps.EnableIme.id
            !isViKeySelected -> Steps.SelectIme.id
            hasNotificationPermission == NotificationPermissionState.NOT_SET && AndroidVersion.ATLEAST_API33_T -> Steps.SelectNotification.id
            else -> Steps.FinishUp.id
        }
        mutableStateOf(StepState(initStep, -1))
    }

    val state = stepState.copy(
        currentAuto = autoStep.value,
        currentManual = if (stepState.currentManual >= 0 &&
            autoStep.value >= stepState.currentManual
        ) stepState.currentManual else -1,
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(200L)
            val isEnabled = InputMethodUtils.isViKeyEnabled(context)
            if (state.getCurrent() == Steps.EnableIme.id &&
                !isViKeyEnabled &&
                !isViKeySelected &&
                hasNotificationPermission == NotificationPermissionState.NOT_SET &&
                isEnabled
            ) {
                context.launchActivity(FlorisAppActivity::class) {
                    it.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.setup__title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringRes(R.string.setup__intro_message),
                textAlign = TextAlign.Justify,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            val allSteps = listOf(
                Steps.EnableIme,
                Steps.SelectIme,
                Steps.SelectNotification,
                Steps.FinishUp,
            )

            allSteps.forEach { step ->
                val hasStep = step != Steps.SelectNotification || AndroidVersion.ATLEAST_API33_T
                if (!hasStep) return@forEach

                val isActive = state.isActive(step.id)
                val primary = MaterialTheme.colorScheme.primary
                val bgColor = if (isActive || state.isCompleted(step.id)) primary
                    else primary.copy(alpha = 0.38f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = bgColor,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (step) {
                                    Steps.EnableIme -> "1"
                                    Steps.SelectIme -> "2"
                                    Steps.SelectNotification -> "3"
                                    Steps.FinishUp -> "4"
                                },
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Surface(
                        modifier = Modifier
                            .height(32.dp)
                            .weight(1f)
                            .clickable(enabled = state.isCompleted(step.id) && !isActive) {
                                stepState = StepState(autoStep.value, step.id)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = when (step) {
                                    Steps.EnableIme -> stringRes(R.string.setup__enable_ime__title)
                                    Steps.SelectIme -> stringRes(R.string.setup__select_ime__title)
                                    Steps.SelectNotification -> stringRes(R.string.setup__grant_notification_permission__title)
                                    Steps.FinishUp -> stringRes(R.string.setup__finish_up__title)
                                },
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isActive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 8.dp),
                    ) {
                        when (step) {
                            Steps.EnableIme -> {
                                Text(
                                    text = stringRes(R.string.setup__enable_ime__description),
                                    textAlign = TextAlign.Justify,
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Button(
                                        onClick = { InputMethodUtils.showImeEnablerActivity(context) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Text(stringRes(R.string.setup__enable_ime__open_settings_btn))
                                    }
                                }
                            }
                            Steps.SelectIme -> {
                                Text(
                                    text = stringRes(R.string.setup__select_ime__description),
                                    textAlign = TextAlign.Justify,
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Button(
                                        onClick = { InputMethodUtils.showImePicker(context) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Text(stringRes(R.string.setup__select_ime__switch_keyboard_btn))
                                    }
                                }
                            }
                            Steps.SelectNotification -> {
                                Text(
                                    text = stringRes(R.string.setup__grant_notification_permission__description),
                                    textAlign = TextAlign.Justify,
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Button(
                                        onClick = {
                                            requestNotification.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Text(stringRes(R.string.setup__grant_notification_permission__btn))
                                    }
                                }
                            }
                            Steps.FinishUp -> {
                                Text(
                                    text = stringRes(R.string.setup__finish_up__description_p1),
                                    textAlign = TextAlign.Justify,
                                )
                                Text(
                                    text = stringRes(R.string.setup__finish_up__description_p2),
                                    textAlign = TextAlign.Justify,
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch { prefs.internal.isImeSetUp.set(true) }
                                            navController.navigate(Routes.Settings.Home) {
                                                popUpTo(Routes.Setup.Screen) { inclusive = true }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Text(stringRes(R.string.setup__finish_up__finish_btn))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val repositoryUrl = stringRes(R.string.florisboard__repo_url)
                TextButton(onClick = { context.launchUrl(repositoryUrl) }) {
                    Text(text = stringRes(R.string.setup__footer__repository))
                }
            }
        }
    }
}

private sealed class Steps(val id: Int) {
    data object EnableIme : Steps(id = 1)
    data object SelectIme : Steps(id = 2)
    data object SelectNotification : Steps(id = 3)
    data object FinishUp : Steps(id = 4)
}
