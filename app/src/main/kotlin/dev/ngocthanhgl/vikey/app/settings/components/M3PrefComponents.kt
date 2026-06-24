package dev.ngocthanhgl.vikey.app.settings.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
private fun SettingsRowLayout(
    icon: ImageVector,
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3Dropdown(
    modifier: Modifier = Modifier,
    options: List<String>,
    selectedOptionIndex: Int,
    isError: Boolean = false,
    enabled: Boolean = true,
    onSelectOption: (Int) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = options.getOrElse(selectedOptionIndex) { "" },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            isError = isError,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectOption(index)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
fun M3SwitchPreference(
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = summary,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun M3ClickablePreference(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val clickModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            enabled = enabled, onClick = { onClick?.invoke() }, onLongClick = onLongClick,
        )
        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
        else -> Modifier
    }
    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = summary,
        modifier = modifier.then(clickModifier),
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

@Composable
fun M3ListPreference(
    icon: ImageVector,
    value: Any,
    onSelect: (String) -> Unit,
    title: String,
    entries: List<Pair<String, String>>,
    enabled: Boolean = true,
) {
    val selectedLabel = entries.find { it.first == value.toString() }?.second ?: value.toString()
    var showDialog by remember { mutableStateOf(false) }

    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = selectedLabel,
        modifier = Modifier.clickable(enabled = enabled, onClick = { showDialog = true }),
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                modifier = Modifier.size(24.dp),
            )
        },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEach { (key, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = value.toString() == key,
                                    onClick = { onSelect(key); showDialog = false },
                                )
                            },
                            modifier = Modifier.clickable { onSelect(key); showDialog = false },
                        )
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
    icon: ImageVector,
    switchChecked: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    listValue: Any,
    onListSelect: (String) -> Unit,
    title: String,
    summarySwitchDisabled: String,
    entries: List<Pair<String, String>>,
    enabled: Boolean = true,
) {
    val selectedLabel = entries.find { it.first == listValue.toString() }?.second ?: listValue.toString()
    var showDialog by remember { mutableStateOf(false) }

    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = if (switchChecked) selectedLabel else summarySwitchDisabled,
        modifier = Modifier.clickable(enabled = enabled && switchChecked, onClick = { showDialog = true }),
        trailing = {
            Switch(
                checked = switchChecked,
                onCheckedChange = onSwitchChange,
                enabled = enabled,
            )
        },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEach { (key, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = listValue.toString() == key,
                                    onClick = { onListSelect(key); showDialog = false },
                                )
                            },
                            modifier = Modifier.clickable { onListSelect(key); showDialog = false },
                        )
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
    icon: ImageVector,
    value: Int,
    onChange: (Int) -> Unit,
    title: String,
    valueLabel: @Composable (Int) -> String,
    min: Int,
    max: Int,
    stepIncrement: Int = 0,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    var tmpValue by remember { mutableFloatStateOf(value.toFloat()) }

    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = valueLabel(value),
        modifier = Modifier.clickable(enabled = enabled, onClick = { tmpValue = value.toFloat(); showDialog = true }),
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                modifier = Modifier.size(24.dp),
            )
        },
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
                TextButton(onClick = { onChange(tmpValue.toInt()); showDialog = false }) {
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
    icon: ImageVector,
    primaryValue: Int,
    onPrimaryChange: (Int) -> Unit,
    secondaryValue: Int,
    onSecondaryChange: (Int) -> Unit,
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    valueLabel: @Composable (Int) -> String,
    min: Int,
    max: Int,
    stepIncrement: Int = 0,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    var tmpPrimary by remember { mutableFloatStateOf(primaryValue.toFloat()) }
    var tmpSecondary by remember { mutableFloatStateOf(secondaryValue.toFloat()) }

    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = "${valueLabel(primaryValue)} / ${valueLabel(secondaryValue)}",
        modifier = Modifier.clickable(enabled = enabled, onClick = {
            tmpPrimary = primaryValue.toFloat(); tmpSecondary = secondaryValue.toFloat(); showDialog = true
        }),
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                modifier = Modifier.size(24.dp),
            )
        },
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
                TextButton(onClick = {
                    onPrimaryChange(tmpPrimary.toInt())
                    onSecondaryChange(tmpSecondary.toInt())
                    showDialog = false
                }) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun M3ColorPickerPreference(
    icon: ImageVector,
    title: String,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    defaultColors: List<Color>,
    defaultValueLabel: String? = null,
    colorOverride: ((Color) -> Color)? = null,
    enabled: Boolean = true,
) {
    val displayColor = colorOverride?.invoke(currentColor) ?: currentColor
    var showDialog by remember { mutableStateOf(false) }
    var tmpColor by remember { mutableStateOf(displayColor) }
    var hexInput by remember { mutableStateOf("") }

    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = if (displayColor == Color.Unspecified) defaultValueLabel
                  else "#${displayColor.value.toHexString().takeLast(6).uppercase()}",
        modifier = Modifier.clickable(enabled = enabled, onClick = { showDialog = true }),
        trailing = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .background(if (displayColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerHighest else displayColor),
            )
        },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (color in defaultColors) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (tmpColor == color) 2.dp else 0.dp,
                                        color = if (tmpColor == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .background(color)
                                    .clickable {
                                        tmpColor = color
                                        hexInput = "#${color.value.toHexString().takeLast(6).uppercase()}"
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (tmpColor == color) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = if (color == Color.Black || color == Color.DarkGray) Color.White else Color.Black,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            hexInput = input
                            runCatching {
                                val hex = input.removePrefix("#")
                                if (hex.length == 6) {
                                    tmpColor = Color(hex.toLong(16) or 0xFF000000UL.toLong())
                                }
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onColorSelected(tmpColor)
                    showDialog = false
                }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3LocalTimePickerPreference(
    icon: ImageVector,
    title: String,
    currentHour: Int,
    currentMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    enabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsRowLayout(
        icon = icon,
        title = title,
        summary = "${currentHour.toString().padStart(2, '0')}:${currentMinute.toString().padStart(2, '0')}",
        modifier = Modifier.clickable(enabled = enabled, onClick = { showDialog = true }),
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                modifier = Modifier.size(24.dp),
            )
        },
    )

    if (showDialog) {
        val timePickerState = remember {
            androidx.compose.material3.TimePickerState(
                initialHour = currentHour,
                initialMinute = currentMinute,
                is24Hour = true,
            )
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                    showDialog = false
                }) {
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
