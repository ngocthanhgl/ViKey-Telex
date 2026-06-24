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

package dev.ngocthanhgl.vikey.app.settings.typing

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.lib.util.launchActivity
import org.florisboard.lib.android.AndroidSettings
import org.florisboard.lib.compose.observeAsState
import org.florisboard.lib.compose.stringRes

@Composable
fun SpellCheckerServiceSelector(florisSpellCheckerEnabled: MutableState<Boolean>) {
    val context = LocalContext.current

    val systemSpellCheckerId by AndroidSettings.Secure.observeAsState(
        key = "selected_spell_checker",
        foregroundOnly = true,
    )
    val systemSpellCheckerEnabled by AndroidSettings.Secure.observeAsState(
        key = "spell_checker_enabled",
        foregroundOnly = true,
    )
    val systemSpellCheckerPkgName = remember(systemSpellCheckerId) {
        runCatching {
            ComponentName.unflattenFromString(systemSpellCheckerId!!)!!.packageName
        }.getOrDefault("null")
    }
    val openSystemSpellCheckerSettings = {
        val componentToLaunch = ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$SpellCheckersSettingsActivity",
        )
        context.launchActivity {
            it.addCategory(Intent.CATEGORY_DEFAULT)
            it.component = componentToLaunch
        }
    }
    florisSpellCheckerEnabled.value =
        systemSpellCheckerEnabled == "1" &&
        systemSpellCheckerPkgName == context.packageName

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (systemSpellCheckerEnabled == "1") {
            if (systemSpellCheckerId == null) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    onClick = openSystemSpellCheckerSettings,
                ) {
                    Text(
                        text = stringRes(R.string.pref__spelling__active_spellchecker__summary_none),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                var spellCheckerIcon: Drawable?
                var spellCheckerLabel = "Unknown"
                try {
                    val pm = context.packageManager
                    val remoteAppInfo = pm.getApplicationInfo(systemSpellCheckerPkgName, 0)
                    spellCheckerIcon = pm.getApplicationIcon(remoteAppInfo)
                    spellCheckerLabel = pm.getApplicationLabel(remoteAppInfo).toString()
                } catch (e: Exception) {
                    spellCheckerIcon = null
                }
                val iconBitmap = remember(spellCheckerIcon) {
                    spellCheckerIcon?.let {
                        val bmp = Bitmap.createBitmap(
                            it.intrinsicWidth.coerceAtLeast(1),
                            it.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        it.setBounds(0, 0, canvas.width, canvas.height)
                        it.draw(canvas)
                        bmp
                    }
                }
                val iconPainter = remember(iconBitmap) {
                    iconBitmap?.let { BitmapPainter(it.asImageBitmap()) }
                }
                ElevatedCard(
                    onClick = openSystemSpellCheckerSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (iconPainter != null) {
                            androidx.compose.foundation.Image(
                                painter = iconPainter,
                                contentDescription = null,
                                modifier = Modifier
                                    .requiredSize(32.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.requiredSize(32.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = spellCheckerLabel,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = systemSpellCheckerPkgName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
    }
}
