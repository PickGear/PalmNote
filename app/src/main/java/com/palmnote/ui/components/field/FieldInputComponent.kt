package com.palmnote.ui.components.field

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.ui.theme.Spacing

@Composable
fun FieldInputComponent(
    config: FieldConfig,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (config.type) {
            FieldType.TEXT -> TextInputField(config, value, onValueChange)
            FieldType.NUMBER -> NumberInputField(config, value, onValueChange)
            FieldType.DATE -> DateInputField(config, value, onValueChange)
            FieldType.BOOLEAN -> BooleanInputField(config, value, onValueChange)
            FieldType.SELECT -> SelectInputField(config, value, onValueChange)
            FieldType.MULTI_SELECT -> MultiSelectInputField(config, value, onValueChange)
            FieldType.TIME -> TimeInputField(config, value, onValueChange)
            FieldType.PERCENT -> PercentInputField(config, value, onValueChange)
            FieldType.RATING -> RatingInputField(config, value, onValueChange)
            else -> TextInputField(config, value, onValueChange)
        }
    }
}

@Composable
private fun TextInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(config.label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = if (config.placeholder.isNotEmpty()) ({ Text(config.placeholder) }) else null
    )
}

@Composable
private fun NumberInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { newVal -> if (newVal.all { it.isDigit() || it == '.' }) onValueChange(newVal) },
        label = { Text("${config.label}${if (config.unit.isNotEmpty()) " (${config.unit})" else ""}") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DateInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val displayText = if (value.isNotEmpty()) {
        try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(value.toLong())) } catch (_: Exception) { value }
    } else ""
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            label = { Text(config.label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            trailingIcon = { Icon(Icons.Default.DateRange, stringResource(R.string.field_select_date)) }
        )
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value.toLongOrNull())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            tonalElevation = 0.dp,
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onValueChange(it.toString()) }
                    showPicker = false
                }) { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) } }
        ) { DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
    }
}

@Composable
private fun BooleanInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(config.label)
        Switch(
            checked = value.toBoolean(),
            onCheckedChange = { onValueChange(it.toString()) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(config.label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            config.options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    val selected = value.split(",").filter { it.isNotBlank() }
    Column {
        Text(config.label, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(Spacing.xxs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            config.options.forEach { option ->
                val isSelected = option in selected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) selected - option else selected + option
                        onValueChange(updated.joinToString(","))
                    },
                    label = { Text(option, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun TimeInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(config.label) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("HH:mm") }
    )
}

@Composable
private fun PercentInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    val sliderValue = value.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
    Column {
        Text("${config.label}: ${sliderValue.toInt()}%")
        Slider(
            value = sliderValue,
            onValueChange = { onValueChange(it.toInt().toString()) },
            valueRange = 0f..100f,
            steps = 99
        )
    }
}

@Composable
private fun RatingInputField(config: FieldConfig, value: String, onValueChange: (String) -> Unit) {
    val rating = value.toIntOrNull() ?: 0
    Row(modifier = Modifier.padding(vertical = Spacing.xs)) {
        Text(config.label, modifier = Modifier.padding(end = Spacing.sm))
        (1..5).forEach { star ->
            val filled = star * 2 <= rating
            IconButton(onClick = { onValueChange((star * 2).toString()) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = if (filled) Color(0xFFFFCA28) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
