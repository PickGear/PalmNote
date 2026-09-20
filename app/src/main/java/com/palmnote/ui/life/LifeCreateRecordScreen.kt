package com.palmnote.ui.life

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LifeCreateRecordScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: LifeCreateRecordViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Surface(
                            color = runCatching { Color(android.graphics.Color.parseColor(state.templateColor)) }
                                .getOrDefault(ModuleLife)
                                .copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                iconFor(state.templateIcon),
                                contentDescription = null,
                                tint = runCatching { Color(android.graphics.Color.parseColor(state.templateColor)) }
                                    .getOrDefault(ModuleLife),
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Text(state.templateName.ifBlank { "新建记录" })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save() },
                        enabled = !state.saving
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold, color = ModuleLife)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Spacer(Modifier.height(Spacing.xs))
            state.fields.forEach { cfg ->
                FieldInput(
                    config = cfg,
                    value = state.values[cfg.key].orEmpty(),
                    onValueChange = { vm.updateValue(cfg.key, it) }
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

// ───────────────────────── 字段输入分发 ─────────────────────────

@Composable
private fun FieldInput(
    config: FieldConfig,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                config.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (config.required) {
                Text(" *", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            }
            if (config.unit.isNotBlank()) {
                Text(config.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (config.type) {
            // ── 文本（最后手段）──
            FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT ->
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(config.placeholder.ifBlank { "输入${config.label}" }) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (config.type == FieldType.RICH_TEXT) 3 else 1,
                    maxLines = if (config.type == FieldType.RICH_TEXT) 8 else 3
                )
            // ── 步进数值（§3.2「能点就不要敲」）──
            FieldType.NUMBER, FieldType.DURATION ->
                StepperField(value = value, onValueChange = onValueChange, step = config.step ?: 1.0, unit = config.unit, min = config.min, max = config.max)
            FieldType.CURRENCY ->
                StepperField(value = value, onValueChange = onValueChange, step = config.step ?: 100.0, unit = "¥", isCurrency = true, min = config.min, max = config.max)
            FieldType.SLIDER ->
                StepperField(value = value, onValueChange = onValueChange, step = config.step ?: 1.0, unit = config.unit, min = config.min, max = config.max)
            // ── 拖动 ──
            FieldType.PERCENT, FieldType.PERCENTAGE ->
                SliderField(value = value, onValueChange = onValueChange, range = 0f..100f)
            // ── 点选-真实状态 ──
            FieldType.RATING ->
                RatingField(value = value, onValueChange = onValueChange)
            FieldType.BOOLEAN ->
                SwitchField(value = value, onValueChange = onValueChange, label = config.label)
            // ── 点选-选项（capsule chips）──
            FieldType.SELECT, FieldType.TAG ->
                SingleChoiceField(options = config.options, value = value, onValueChange = onValueChange)
            FieldType.MULTI_SELECT ->
                MultiChoiceField(options = config.options, value = value, onValueChange = onValueChange)
            // ── 点选-快捷（§3.2 quick chips）──
            FieldType.DATE ->
                DateQuickField(value = value, onValueChange = onValueChange)
            FieldType.TIME ->
                TimeQuickField(value = value, onValueChange = onValueChange)
            FieldType.DATETIME ->
                DateTimeQuickField(value = value, onValueChange = onValueChange)
            // ── 文本变体 ──
            FieldType.URL ->
                OutlinedTextField(
                    value = value, onValueChange = onValueChange,
                    placeholder = { Text("https://") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            FieldType.EMAIL ->
                OutlinedTextField(
                    value = value, onValueChange = onValueChange,
                    placeholder = { Text("email@example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            FieldType.PHONE ->
                OutlinedTextField(
                    value = value, onValueChange = onValueChange,
                    placeholder = { Text("手机号码") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            // ── 复合 ──
            FieldType.CHECKLIST ->
                ChecklistField(value = value, onValueChange = onValueChange)
            // ── 兜底 ──
            else ->
                OutlinedTextField(
                    value = value, onValueChange = onValueChange,
                    placeholder = { Text(config.placeholder.ifBlank { "输入${config.label}" }) },
                    modifier = Modifier.fillMaxWidth()
                )
        }
    }
}

// ───────────────────────── Stepper（步进数值）─────────────────────────

@Composable
private fun StepperField(
    value: String,
    onValueChange: (String) -> Unit,
    step: Double,
    unit: String = "",
    isCurrency: Boolean = false,
    min: Double? = null,
    max: Double? = null
) {
    val current = value.toDoubleOrNull() ?: 0.0
    val numStr = if (isCurrency) "¥${formatNum(current)}" else formatNum(current)
    val display = if (unit.isNotBlank() && !isCurrency) "$numStr $unit" else numStr
    val canDec = min == null || current - step >= min
    val canInc = max == null || current + step <= max

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { if (canDec) onValueChange(formatNum(current - step)) },
            enabled = canDec,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "减少", tint = if (canDec) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            display,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { if (canInc) onValueChange(formatNum(current + step)) },
            enabled = canInc,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "增加", tint = if (canInc) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Locale.US：数值锁 ASCII 小数点（德/法等 locale 会把 1.5 输出成 1,5，回填解析会炸）
private fun formatNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

// ───────────────────────── Slider（拖动）─────────────────────────

@Composable
private fun SliderField(value: String, onValueChange: (String) -> Unit, range: ClosedFloatingPointRange<Float>) {
    val numeric = value.toFloatOrNull() ?: 0f
    Column {
        Slider(
            value = numeric.coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.toInt().toString()) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
        Text("${numeric.toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────────────────── Rating（星级）─────────────────────────

@Composable
private fun RatingField(value: String, onValueChange: (String) -> Unit) {
    val rating = value.toIntOrNull() ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        // for 而非 (1..5).forEach：range 上的 forEach 有装箱开销（detekt ForEachOnRange）
        for (i in 1..5) {
            TextButton(onClick = { onValueChange(i.toString()) }, contentPadding = PaddingValues(4.dp)) {
                Text(
                    if (i <= rating) "★" else "☆",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (i <= rating) Color(0xFFFFCA28) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────── Switch（布尔）─────────────────────────

@Composable
private fun SwitchField(value: String, onValueChange: (String) -> Unit, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = value.toBooleanStrictOrNull() ?: false,
            onCheckedChange = { onValueChange(it.toString()) }
        )
    }
}

// ───────────────────────── 选项 Chips ─────────────────────────

@Composable
private fun SingleChoiceField(options: List<String>, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { opt ->
                    FilterChip(
                        selected = value == opt,
                        onClick = { onValueChange(opt) },
                        label = { Text(opt, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiChoiceField(options: List<String>, value: String, onValueChange: (String) -> Unit) {
    val selected = remember(value) { value.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { opt ->
                    FilterChip(
                        selected = opt in selected,
                        onClick = {
                            if (opt in selected) selected.remove(opt) else selected.add(opt)
                            onValueChange(selected.joinToString(","))
                        },
                        label = { Text(opt, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }
    }
}

// ───────────────────────── DATE / TIME 快捷 Chips ─────────────────────────

@Composable
private fun DateQuickField(value: String, onValueChange: (String) -> Unit) {
    val today = LocalDate.now()
    val todayStr = today.toString()
    val tomorrowStr = today.plusDays(1).toString()
    val weekEndStr = today.with(java.time.DayOfWeek.SUNDAY).toString()
    val monthEndStr = today.withDayOfMonth(today.lengthOfMonth()).toString()

    val quickOptions = listOf(
        "今天" to todayStr,
        "明天" to tomorrowStr,
        "周末" to weekEndStr,
        "月末" to monthEndStr
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            quickOptions.forEach { (label, dateStr) ->
                FilterChip(
                    selected = value == dateStr,
                    onClick = { onValueChange(dateStr) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("yyyy-MM-dd") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true
        )
    }
}

@Composable
private fun TimeQuickField(value: String, onValueChange: (String) -> Unit) {
    val now = LocalTime.now()
    val morning = "08:00"
    val noon = "12:00"
    val afternoon = "14:00"
    val evening = "18:00"
    val night = "21:00"

    val quickOptions = listOf(
        "早上" to morning,
        "中午" to noon,
        "下午" to afternoon,
        "傍晚" to evening,
        "晚上" to night,
        "现在" to now.format(DateTimeFormatter.ofPattern("HH:mm"))
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        quickOptions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { (label, timeStr) ->
                    FilterChip(
                        selected = value == timeStr,
                        onClick = { onValueChange(timeStr) },
                        label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("HH:mm") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true
        )
    }
}

@Composable
private fun DateTimeQuickField(value: String, onValueChange: (String) -> Unit) {
    val todayStr = LocalDate.now().toString()
    val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    val quickOptions = listOf(
        "今天此刻" to "$todayStr $nowTime",
        "明天此刻" to "${LocalDate.now().plusDays(1)} $nowTime"
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            quickOptions.forEach { (label, dtStr) ->
                FilterChip(
                    selected = value == dtStr,
                    onClick = { onValueChange(dtStr) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("yyyy-MM-dd HH:mm") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true
        )
    }
}

// ───────────────────────── Checklist（待办）─────────────────────────

@Composable
private fun ChecklistField(value: String, onValueChange: (String) -> Unit) {
    val items = remember(value) { value.lines().filter { it.isNotBlank() }.toMutableStateList() }
    var newItem by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        items.forEachIndexed { i, item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = false, onCheckedChange = null)
                Text(item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            placeholder = { Text("添加项目") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = {
                if (newItem.isNotBlank()) {
                    items.add(newItem)
                    newItem = ""
                    onValueChange(items.joinToString("\n"))
                }
            })
        )
    }
}
