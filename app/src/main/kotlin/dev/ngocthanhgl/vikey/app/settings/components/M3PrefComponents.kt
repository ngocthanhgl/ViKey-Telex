package dev.ngocthanhgl.vikey.app.settings.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.patrickgold.jetpref.datastore.model.JetPref
import dev.patrickgold.jetpref.datastore.model.collectAsState

@Composable
fun M3SwitchPreference(
    pref: JetPref<Boolean>,
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val value by pref.collectAsState()
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Switch(checked = value, onCheckedChange = { pref.set(it) }, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun M3ClickablePreference(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = if (onLongClick != null) {
            modifier.then(Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick))
        } else {
            modifier.then(Modifier.clickable(enabled = enabled, onClick = onClick))
        },
    )
}

@Composable
fun M3ListPreference(
    pref: JetPref<String>,
    icon: ImageVector? = null,
    title: String,
    entries: List<Pair<String, String>>,
    enabled: Boolean = true,
) {
    val value by pref.collectAsState()
    val selectedLabel = entries.find { it.first == value }?.second ?: value
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(selectedLabel, style = MaterialTheme.typography.bodySmall) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = { showDialog = true }),
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pref.set(key); showDialog = false }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = value == key,
                                onClick = { pref.set(key); showDialog = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(org.florisboard.lib.compose.stringRes(dev.ngocthanhgl.vikey.R.string.action__ok))
                }
            },
        )
    }
}

@Composable
fun M3SwitchListPreference(
    switchPref: JetPref<Boolean>,
    listPref: JetPref<String>,
    icon: ImageVector? = null,
    title: String,
    summarySwitchDisabled: String,
    entries: List<Pair<String, String>>,
    enabled: Boolean = true,
) {
    val switchValue by switchPref.collectAsState()
    val listValue by listPref.collectAsState()
    val selectedLabel = entries.find { it.first == listValue }?.second ?: listValue
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                if (switchValue) selectedLabel else summarySwitchDisabled,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Switch(
                checked = switchValue,
                onCheckedChange = { switchPref.set(it) },
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled && switchValue, onClick = { showDialog = true }),
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { listPref.set(key); showDialog = false }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = listValue == key,
                                onClick = { listPref.set(key); showDialog = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(org.florisboard.lib.compose.stringRes(dev.ngocthanhgl.vikey.R.string.action__ok))
                }
            },
        )
    }
}

@Composable
fun M3DialogSliderPreference(
    pref: JetPref<Int>,
    icon: ImageVector? = null,
    title: String,
    valueLabel: @Composable (Int) -> String,
    min: Int,
    max: Int,
    stepIncrement: Int = 0,
    enabled: Boolean = true,
) {
    val value by pref.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var tmpValue by remember { mutableFloatStateOf(value.toFloat()) }

    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(valueLabel(value), style = MaterialTheme.typography.bodySmall) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = { tmpValue = value.toFloat(); showDialog = true }),
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(valueLabel(tmpValue.toInt()), style = MaterialTheme.typography.headlineSmall)
                    Slider(
                        value = tmpValue,
                        onValueChange = { tmpValue = it },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = if (stepIncrement > 0) ((max - min) / stepIncrement) - 1 else 0,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pref.set(tmpValue.toInt()); showDialog = false }) {
                    Text(org.florisboard.lib.compose.stringRes(dev.ngocthanhgl.vikey.R.string.action__ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(org.florisboard.lib.compose.stringRes(dev.ngocthanhgl.vikey.R.string.action__cancel))
                }
            },
        )
    }
}

@Composable
fun M3DialogSliderPreference(
    primaryPref: JetPref<Int>,
    secondaryPref: JetPref<Int>,
    icon: ImageVector? = null,
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    valueLabel: @Composable (Int) -> String,
    min: Int,
    max: Int,
    stepIncrement: Int = 0,
    enabled: Boolean = true,
) {
    val primaryValue by primaryPref.collectAsState()
    val secondaryValue by secondaryPref.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var tmpPrimary by remember { mutableFloatStateOf(primaryValue.toFloat()) }
    var tmpSecondary by remember { mutableFloatStateOf(secondaryValue.toFloat()) }

    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                "${valueLabel(primaryValue)} / ${valueLabel(secondaryValue)}",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = {
            tmpPrimary = primaryValue.toFloat(); tmpSecondary = secondaryValue.toFloat(); showDialog = true
        }),
    )

    if (showDialog) {
        val steps = if (stepIncrement > 0) ((max - min) / stepIncrement) - 1 else 0
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(primaryLabel, style = MaterialTheme.typography.titleSmall)
                    Text(valueLabel(tmpPrimary.toInt()), style = MaterialTheme.typography.headlineSmall)
                    Slider(
                        value = tmpPrimary,
                        onValueChange = { tmpPrimary = it },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = steps,
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text(secondaryLabel, style = MaterialTheme.typography.titleSmall)
                    Text(valueLabel(tmpSecondary.toInt()), style = MaterialTheme.typography.headlineSmall)
                    Slider(
                        value = tmpSecondary,
                        onValueChange = { tmpSecondary = it },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = steps,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { primaryPref.set(tmpPrimary.toInt()); secondaryPref.set(tmpSecondary.toInt()); showDialog = false }) {
                    Text(org.florisboard.lib.compose.stringRes(dev.ngocthanhgl.vikey.R.string.action__ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(org.florisboard.lib.compose.stringRes(dev.ngocthanhgl.vikey.R.string.action__cancel))
                }
            },
        )
    }
}
