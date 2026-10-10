@file:Suppress("TooManyFunctions")

package com.palmnote.ui.life

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
fun LifeCreateRecordScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: LifeCreateRecordViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val accent = runCatching { Color(android.graphics.Color.parseColor(state.templateColor)) }
        .getOrDefault(ModuleLife)
    var showDiscard by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.dirty && !state.saved) showDiscard = true else onBack()
    }
    BackHandler { requestBack() }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }
    // 轨迹导入结果反馈（此前 trackImportMessage 无人消费，导入成功无感知）
    LaunchedEffect(state.trackImportTick) {
        state.trackImportMessage?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 二级页头统一（排版与其他生活二级页一致）；标题保留模板身份色圆标
            SecondaryTopAppBar(
                backgroundColor = Color.Transparent,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Surface(
                            color = accent.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                iconFor(state.templateIcon),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Text(
                            state.templateName.ifBlank {
                                stringResource(if (state.isEdit) R.string.life_record_edit_title else R.string.life_record_new_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                },
                actions = {
                    // 保存前看一眼：用**已经填进去的值**预览卡片与详情页（与模板编辑器共用同一份渲染）
                    IconButton(onClick = { showPreview = true }) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.life_template_preview),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            Spacer(Modifier.height(Spacing.sm))
            // 一字段一卡（分组内嵌式，与编辑器预览态/详情结构区同一卡片语言）
            // 分组表单：连续"图标+标签左/值右"的字段合并进一张卡，复合字段单独成卡
            EmptyFormNotice(visible = state.fields.isEmpty())
            val groups = remember(state.fields) { buildFieldGroups(state.fields) }
            groups.forEach { group ->
                val single = group.singleOrNull()
                if (single != null && single.type !in INLINE_FORM_TYPES) {
                    ComplexFieldCard(single, accent, state, vm, context)
                } else {
                    SimpleRowsCard(group, accent, state, vm)
                }
            }
            DisabledFieldsHint(state.disabledFieldCount)
            Spacer(Modifier.height(Spacing.md))
            // 底部全宽主按钮：与记账 AddBill 同形态（拇指可达）
            Button(
                onClick = { vm.save() },
                // 判断在状态层（`CreateRecordUiState.canSave`）：没有字段时置灰，而不是"点了没反应"
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(22.dp), Color.White, 2.dp)
                } else {
                    Text(
                        stringResource(if (state.isEdit) R.string.save else R.string.save),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }

    if (showPreview) {
        RecordPreviewSheet(
            input = RecordPreviewInput(
                name = state.templateName,
                iconKey = state.templateIcon,
                colorHex = state.templateColor,
                fields = state.fields,
                // 用**用户已经填进去的值**预览 —— 这正是"保存前看一眼"的意义所在
                values = state.values,
                // 「每年重复」的模板：预览与真实读数必须同口径（都按下一次周年滚动）
                repeatYearly = state.templateRepeatYearly,
                // 填写页本身就是「填写」态，不再提供那一档
                withFillTab = false
            ),
            onDismiss = { showPreview = false }
        )
    }

    if (showDiscard) {
        AppDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.life_discard_changes_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_discard_changes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onBack()
                }) {
                    Text(stringResource(R.string.life_discard), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.settings_cancel)) } }
        )
    }
}

/**
 * 空表单提示：**说清"为什么没有字段"**，而不是让用户对着空白点保存 ——
 * `save()` 会被 `fields.isEmpty()` 守卫静默拦掉，那样看起来就是"点了没反应"。
 *
 * 这条路径真实存在：模板加载失败、或模板本身一个字段都没有。
 * 判断放在这里（而不是调用点）是为了不给 [LifeCreateRecordScreen] 增加分支 —— 它已顶到
 * detekt 的圈复杂度阈值。
 */
@Composable
private fun EmptyFormNotice(visible: Boolean) {
    if (!visible) return
    GroupCard {
        Text(
            stringResource(R.string.life_template_no_fields),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.life_template_no_fields_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 停用字段提示：停用的字段**不进表单**是对的（那是模板层的选择），
 * 但"少了什么"得说一句 —— 否则用户只会觉得表单莫名其妙缺了东西。
 */
@Composable
private fun DisabledFieldsHint(count: Int) {
    if (count <= 0) return
    Text(
        stringResource(R.string.life_record_disabled_fields, count),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

// ───────────────────────── 日期 / 时间选择器（M3，与记账页同一套视觉）─────────────────────────

@Composable
/**
 * 日期选择器。上方给**快捷项**（今天 / 明天 / 周末）—— todo / 计划类 app 的惯例：
 * 八成的情况就落在这三天里，先给一排按钮，省掉在小日历里找格子。
 *
 * 表单的日期胶囊与详情页的「点值即改」共用这个弹窗，所以两处一起受益。
 */
internal fun LifeDatePickerDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // M3 DatePicker 的 selectedDateMillis 是 **UTC 零点**：直接除以当日毫秒数取 epochDay，避免时区偏移差一天
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toEpochDay()?.times(86_400_000L)
    )
    val today = LocalDate.now()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { LocalDate.ofEpochDay(it / 86_400_000L) }?.let(onPick)
                onDismiss()
            }) { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuickDatePick(stringResource(R.string.life_record_today), today, onPick, onDismiss)
            QuickDatePick(stringResource(R.string.life_record_tomorrow), today.plusDays(1), onPick, onDismiss)
            QuickDatePick(stringResource(R.string.life_record_weekend), upcomingWeekend(today), onPick, onDismiss)
        }
        DatePicker(state = state)
    }
}

/** 快捷项点了就**直接确认**（不再要求再按一次「确定」）——「快捷」就得是一步。 */
@Composable
private fun QuickDatePick(
    label: String,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    TextButton(onClick = {
        onPick(date)
        onDismiss()
    }) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 「周末」= 即将到来的周六（今天就是周六则就是今天）。
 * 用取模算，不引 `TemporalAdjusters`/`DayOfWeek` 之外的东西。
 */
internal fun upcomingWeekend(from: LocalDate): LocalDate {
    val daysUntilSaturday = (DayOfWeek.SATURDAY.value - from.dayOfWeek.value + 7) % 7
    return from.plusDays(daysUntilSaturday.toLong())
}

@Composable
internal fun LifeTimePickerDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) }
    )
}

// ───────────────────────── 保留的共享控件（kit 引用）─────────────────────────

@Suppress("LongParameterList")
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 3,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (() -> Unit)? = null
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        keyboardActions = keyboardActions,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Row(
                verticalAlignment = if (minLines > 1) Alignment.Top else Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(Spacing.xs))
                    trailing()
                }
            }
        }
    )
}

@Composable
internal fun MapImportField(
    value: String,
    importing: Boolean,
    onImport: (Uri, String?) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')
            onImport(uri, name)
        }
    }
    val model = com.palmnote.domain.model.parseMap(value)
    val track = model.track
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedButton(
            onClick = {
                launcher.launch(
                    arrayOf(
                        "application/gpx+xml",
                        "application/vnd.google-earth.kml+xml",
                        "application/xml",
                        "text/xml",
                        "*/*"
                    )
                )
            },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (importing) Icons.Filled.Map else Icons.Filled.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                if (importing) stringResource(R.string.life_record_importing) else stringResource(R.string.life_map_import_track),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (track != null) {
            Text(
                stringResource(R.string.life_map_track_stats, track.pts.size, formatDistance(track.distM), track.ascentM.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (value.isNotBlank()) {
            Text(
                stringResource(R.string.life_map_points, model.route.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDistance(meters: Double): String = if (meters >= 1000) {
    String.format(Locale.US, "%.2f km", meters / 1000.0)
} else {
    String.format(Locale.US, "%.0f m", meters)
}

internal fun formatNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

internal fun selectOptionLabel(context: Context, option: String): String = when (option) {
    "monthly" -> context.getString(R.string.life_detail_cycle_monthly)
    "quarterly" -> context.getString(R.string.life_detail_cycle_quarterly)
    "yearly" -> context.getString(R.string.life_detail_cycle_yearly)
    else -> option
}

internal val WEATHER_EMOJI = mapOf("晴" to "☀️", "阴" to "☁️", "雨" to "🌧️", "雪" to "❄️")
