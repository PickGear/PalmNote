package com.palmnote.ui.life.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.toComposeColor

// ============================================================
// 17 Field Types
// ============================================================

data class FieldDef(
    val key: String,
    val label: String,
    val type: String,
    val required: Boolean = false,
    val unit: String = "",
    val defaultValue: String = "",
    val options: List<String> = emptyList(),
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0
)

@Composable
fun FieldInput(field: FieldDef, value: String, onValueChange: (String) -> Unit) {
    when (field.type) {
        "TEXT" -> TextInput(field, value, onValueChange, multiline = true)
        "SHORT_TEXT" -> TextInput(field, value, onValueChange, multiline = false)
        "NUMBER" -> NumberInput(field, value, onValueChange)
        "DATE" -> DateInput(value, onValueChange)
        "TIME" -> TimeInput(value, onValueChange)
        "BOOLEAN" -> BooleanInput(value, onValueChange)
        "SELECT" -> SelectInput(field, value, onValueChange)
        "MULTI_SELECT" -> MultiSelectInput(field, value, onValueChange)
        "RATING" -> RatingInput(value, onValueChange)
        "SLIDER" -> SliderInput(field, value, onValueChange)
        "PERCENTAGE" -> PercentageInput(value, onValueChange)
        "URL" -> UrlInput(value, onValueChange)
        "EMAIL" -> EmailInput(value, onValueChange)
        "PHONE" -> PhoneInput(value, onValueChange)
        "COLOR" -> ColorInput(value, onValueChange)
        "IMAGE" -> ImageInput(value, onValueChange)
        "LOCATION" -> LocationInput(value, onValueChange)
        "DURATION" -> DurationInput(value, onValueChange)
        else -> TextInput(field, value, onValueChange, multiline = true)
    }
}

// ---- TEXT / SHORT_TEXT ----
@Composable
private fun TextInput(field: FieldDef, value: String, onValueChange: (String) -> Unit, multiline: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(if (field.unit.isNotEmpty()) "${field.label}${field.unit}" else field.label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline, minLines = if (multiline) 3 else 1,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), focusedBorderColor = MaterialTheme.colorScheme.outline)
    )
}

// ---- NUMBER ----
@Composable
private fun NumberInput(field: FieldDef, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' || c == '-' }) onValueChange(it) },
        placeholder = { Text(field.label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = if (field.unit.isNotEmpty()) {{ Text(field.unit, fontSize = 12.sp) }} else null,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), focusedBorderColor = MaterialTheme.colorScheme.outline)
    )
}

// ---- DATE ----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateInput(value: String, onValueChange: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    val displayText = value.toLongOrNull()?.let { millis ->
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(millis))
    } ?: value
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayText, onValueChange = { }, readOnly = true,
            placeholder = { Text(stringResource(R.string.field_select_date)) },
            trailingIcon = { IconButton(onClick = { picker = true }) { Icon(Icons.Default.DateRange, stringResource(R.string.field_select_date)) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = MaterialTheme.shapes.medium
        )
        Box(modifier = Modifier.matchParentSize().clickable { picker = true })
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.toLongOrNull())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            tonalElevation = 0.dp,
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { onValueChange(it.toString()) }; picker = false }) { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { picker = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
            }
        ) { DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
    }
}

// ---- TIME ----
@Composable
private fun TimeInput(value: String, onValueChange: (String) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value.ifEmpty { "" }, onValueChange = { }, readOnly = true,
        placeholder = { Text(stringResource(R.string.field_select_time)) },
        trailingIcon = { Icon(Icons.Default.Schedule, null) },
        modifier = Modifier.fillMaxWidth().clickable { picker = true },
        singleLine = true, shape = MaterialTheme.shapes.medium
    )
    if (picker) {
        val state = rememberTimePickerState()
        AppDialog(
            onDismissRequest = { picker = false },
            title = { Text(stringResource(R.string.field_select_time), fontWeight = FontWeight.Bold) },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { onValueChange("${state.hour}:${state.minute.toString().padStart(2, '0')}"); picker = false }) { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

// ---- BOOLEAN ----
@Composable
private fun BooleanInput(value: String, onValueChange: (String) -> Unit) {
    val checked = value.toBooleanStrictOrNull() ?: false
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = { onValueChange(it.toString()) })
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (checked) stringResource(R.string.field_enabled) else stringResource(R.string.field_disabled), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- SELECT ----
@Composable
private fun SelectInput(field: FieldDef, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val opts = field.options.ifEmpty { listOf(stringResource(R.string.field_option, "1"), stringResource(R.string.field_option, "2"), stringResource(R.string.field_option, "3")) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.ifEmpty { opts.first() },
            onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
            singleLine = true, shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opts.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onValueChange(opt); expanded = false })
            }
        }
    }
}

// ---- MULTI_SELECT ----
@Composable
private fun MultiSelectInput(field: FieldDef, value: String, onValueChange: (String) -> Unit) {
    val selected = value.split(",").filter { it.isNotBlank() }.toMutableSet()
    val opts = field.options.ifEmpty { listOf(stringResource(R.string.field_option, "1"), stringResource(R.string.field_option, "2"), stringResource(R.string.field_option, "3")) }
    Column {
        opts.forEach { opt ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Checkbox(checked = opt in selected, onCheckedChange = {
                    if (opt in selected) selected.remove(opt) else selected.add(opt)
                    onValueChange(selected.joinToString(","))
                })
                Spacer(modifier = Modifier.width(4.dp))
                Text(opt, fontSize = 14.sp)
            }
        }
    }
}

// ---- RATING ----
@Composable
private fun RatingInput(value: String, onValueChange: (String) -> Unit) {
    val rating = value.toIntOrNull() ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { i ->
            Icon(
                if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                null,
                tint = if (i <= rating) Color(0xFFFFCA28) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onValueChange(i.toString()) }
            )
        }
    }
}

// ---- SLIDER ----
@Composable
private fun SliderInput(field: FieldDef, value: String, onValueChange: (String) -> Unit) {
    val v = value.toFloatOrNull() ?: field.min.toFloat()
    Column {
        Slider(
            value = v.coerceIn(field.min.toFloat(), field.max.toFloat()),
            onValueChange = { onValueChange(it.toInt().toString()) },
            valueRange = field.min.toFloat()..field.max.toFloat(),
            steps = ((field.max - field.min) / field.step).toInt() - 1
        )
        Text("${v.toInt()}${field.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- PERCENTAGE ----
@Composable
private fun PercentageInput(value: String, onValueChange: (String) -> Unit) {
    val v = value.toFloatOrNull() ?: 0f
    Column {
        Slider(
            value = v.coerceIn(0f, 100f),
            onValueChange = { onValueChange(it.toInt().toString()) },
            valueRange = 0f..100f
        )
        Text("${v.toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- URL ----
@Composable
private fun UrlInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.field_url_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Link, null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        shape = MaterialTheme.shapes.medium
    )
}

// ---- EMAIL ----
@Composable
private fun EmailInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.field_email_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Email, null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        shape = MaterialTheme.shapes.medium
    )
}

// ---- PHONE ----
@Composable
private fun PhoneInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { if (it.all { c -> c.isDigit() || c == '-' || c == '+' }) onValueChange(it) },
        placeholder = { Text(stringResource(R.string.field_phone_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Phone, null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = MaterialTheme.shapes.medium
    )
}

// ---- COLOR ----
@Composable
private fun ColorInput(value: String, onValueChange: (String) -> Unit) {
    val palette = listOf("#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#607D8B")
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            palette.take(9).forEach { hex ->
                val c = hex.toComposeColor(Color.Gray)
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(c).border(if (value == hex) 2.dp else 0.dp, if (value == hex) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onValueChange(hex) })
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            palette.drop(9).forEach { hex ->
                val c = hex.toComposeColor(Color.Gray)
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(c).border(if (value == hex) 2.dp else 0.dp, if (value == hex) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onValueChange(hex) })
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange, placeholder = { Text(stringResource(R.string.field_color_placeholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
    }
}

// ---- IMAGE (placeholder) ----
@Composable
private fun ImageInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.field_image_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Image, null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true, shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(stringResource(R.string.field_image_helper), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
}

// ---- LOCATION ----
@Composable
private fun LocationInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.field_location_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Place, null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true, shape = MaterialTheme.shapes.medium
    )
}

// ---- DURATION ----
@Composable
private fun DurationInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { if (it.all { c -> c.isDigit() || c == ':' }) onValueChange(it) },
        placeholder = { Text(stringResource(R.string.field_duration_format)) },
        leadingIcon = { Icon(Icons.Default.Timer, null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true, shape = MaterialTheme.shapes.medium
    )
}

// ============================================================
// Display-only components (used in ItemDetailScreen)
// ============================================================

@Composable
fun FieldDisplay(field: FieldDef, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(field.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        when (field.type) {
            "NUMBER" -> {
                val isCurrency = field.unit == "\u5143" || field.unit == "\u00A5"
                Text("${if (isCurrency) "\u00A5" else ""}${value}${field.unit}", 
                    style = if (isCurrency) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            "BOOLEAN" -> {
                val checked = value.toBooleanStrictOrNull() ?: false
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                        tint = if (checked) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (checked) "\u2713" else "\u2717", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            "RATING" -> {
                val rating = value.toIntOrNull() ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { i ->
                        Icon(if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder, null,
                            tint = if (i <= rating) Color(0xFFFFCA28) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
            "COLOR" -> {
                val c = value.toComposeColor(Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(c))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "URL" -> {
                Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            "PERCENTAGE" -> {
                val pct = value.toFloatOrNull() ?: 0f
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                Spacer(modifier = Modifier.height(2.dp))
                Text("${pct.toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            "SLIDER" -> {
                Text("${value}${field.unit}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
            }
            "DATE" -> {
                val dateStr = value.toLongOrNull()?.let { millis ->
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(millis))
                } ?: value.ifEmpty { "-" }
                Text(dateStr, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
            }
            else -> {
                Text(value.ifEmpty { "-" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 5, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
            }
        }
    }
}
