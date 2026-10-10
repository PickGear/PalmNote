@file:Suppress("TooManyFunctions")

package com.palmnote.ui.life

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.BorderStroke
import coil3.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.palmnote.ui.theme.Spacing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.data.db.entity.BuiltinFieldText
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.ChecklistRow
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.encodeChecklist
import com.palmnote.domain.model.parseChecklist
import com.palmnote.ui.theme.TypeScale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

/**
 * 记录填写页的分组内嵌式控件套件：
 * 连续"图标 + 标签左 / 值右"的字段合并进一张白卡（发丝分隔线），
 * 复合控件（chips / 滑条 / 多行 / 媒体）各自独立成卡，
 * 空态媒体与清单用虚线添加区。
 */

/** 这些类型渲染成"图标+标签左 / 值右"的单行；其余类型各自独立成卡。 */
internal val INLINE_FORM_TYPES = setOf(
    FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.URL, FieldType.EMAIL, FieldType.PHONE,
    FieldType.LOCATION, FieldType.NUMBER, FieldType.CURRENCY,
    FieldType.DURATION, FieldType.DATE, FieldType.DATETIME, FieldType.TIME, FieldType.RATING,
    FieldType.BOOLEAN
)

/** 连续的 inline 字段合并成一张分组卡；复合字段单独一组。 */
internal fun buildFieldGroups(fields: List<FieldConfig>): List<List<FieldConfig>> {
    val groups = mutableListOf<List<FieldConfig>>()
    var run = mutableListOf<FieldConfig>()
    for (f in fields) {
        if (f.type in INLINE_FORM_TYPES) {
            run += f
        } else {
            if (run.isNotEmpty()) groups += run.toList()
            run = mutableListOf()
            groups += listOf(f)
        }
    }
    if (run.isNotEmpty()) groups += run.toList()
    return groups
}

@Suppress("CyclomaticComplexMethod")
/** 同型多字段时的备选图标池：出行计划里两枚日历并排很傻，第二枚起换装。 */
private val ICON_ALTERNATES: Map<FieldType, List<ImageVector>> = mapOf(
    FieldType.DATE to listOf(Icons.Filled.CalendarMonth, Icons.Filled.Event, Icons.Filled.DateRange),
    FieldType.CURRENCY to listOf(Icons.Filled.Paid, Icons.Filled.Savings, Icons.Filled.CreditCard),
    FieldType.DURATION to listOf(Icons.Filled.Schedule, Icons.Filled.Timer),
    FieldType.NUMBER to listOf(Icons.Filled.Numbers, Icons.Filled.Calculate),
    FieldType.TEXT to listOf(Icons.AutoMirrored.Filled.Notes, Icons.Filled.Edit)
)

@Suppress("CyclomaticComplexMethod")
@Composable
internal fun fieldTypeIcon(type: FieldType): ImageVector = when (type) {
    FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT -> Icons.AutoMirrored.Filled.Notes
    FieldType.NUMBER -> Icons.Filled.Numbers
    FieldType.CURRENCY -> Icons.Filled.Paid
    FieldType.DURATION, FieldType.TIME -> Icons.Filled.Schedule
    FieldType.PERCENT, FieldType.PERCENTAGE, FieldType.SLIDER -> Icons.Filled.Percent
    FieldType.DATE, FieldType.DATETIME -> Icons.Filled.CalendarMonth
    FieldType.RANGE -> Icons.Filled.DateRange
    FieldType.SELECT -> Icons.Filled.List
    FieldType.TAG, FieldType.MULTI_SELECT -> Icons.Filled.Label
    FieldType.BOOLEAN -> Icons.Filled.ToggleOn
    FieldType.RATING -> Icons.Filled.Star
    FieldType.URL -> Icons.Filled.Link
    FieldType.EMAIL -> Icons.Filled.Email
    FieldType.PHONE -> Icons.Filled.Phone
    FieldType.LOCATION -> Icons.Filled.Place
    FieldType.PERSON -> Icons.Filled.Person
    FieldType.IMAGE -> Icons.Filled.Image
    FieldType.CHECKLIST -> Icons.Filled.Checklist
    FieldType.MAP -> Icons.Filled.Map
    FieldType.COLOR -> Icons.Filled.Palette
    FieldType.VIDEO -> Icons.Filled.PlayArrow
    FieldType.AUDIO -> Icons.Filled.MusicNote
    FieldType.FILE -> Icons.Filled.AttachFile
    else -> Icons.Filled.Edit
}

/** 行标签：图标 + 文字 + 必填星（星号紧跟标签）。 */
@Composable
private fun RowLabel(
    icon: ImageVector,
    accent: Color,
    label: String,
    required: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        if (required) {
            Spacer(Modifier.width(3.dp))
            Text("*", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 值胶囊（日期/时间等只读点选值）。
 *
 * [muted] = 这是**占位提示**而不是真实值。必须视觉可分：此前日期字段被预填成真实值，
 * 而占位符长得一模一样，用户根本分不出「已经填了今天」和「还没填」——
 * 这正是「打开表单直接保存、就把今天存成生日」那类缺陷的起点。
 */
@Composable
internal fun PillValue(text: String, onClick: () -> Unit, muted: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        onClick = onClick
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** 胶囊数字输入（直接键入，无步进按钮；参考图的"1" "10"小胶囊）。 */
@Composable
private fun PillNumberInput(
    value: String,
    placeholder: String,
    isCurrency: Boolean,
    onValueChange: (String) -> Unit,
    min: Double?,
    max: Double?,
    modifier: Modifier = Modifier
) {
    var editing by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        BasicTextField(
            value = editing,
            onValueChange = { raw ->
                editing = raw
                raw.toDoubleOrNull()?.let { v ->
                    var r = v
                    min?.let { r = maxOf(r, it) }
                    max?.let { r = minOf(r, it) }
                    onValueChange(formatNum(r))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .onFocusChanged { st ->
                    focused = st.isFocused
                    if (!st.isFocused) editing = value
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrency) {
                        Text("¥", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (editing.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        inner()
                    }
                }
            }
        )
    }
}

/** 虚线添加区（空态媒体/清单：虚线框 + 提示 + 添加动作）。 */
@Composable
private fun DashedArea(content: @Composable () -> Unit) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dash)
                )
            }
            .padding(12.dp)
    ) {
        content()
    }
}

/** 简单值行分组卡：连续 inline 字段一张卡，发丝分隔线内缩对齐图标后。 */
@Composable
internal fun SimpleRowsCard(
    fields: List<FieldConfig>,
    accent: Color,
    state: CreateRecordUiState,
    host: FieldFormHost
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                2.dp, RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            val usedIcons = mutableMapOf<ImageVector, Int>()
            fields.forEachIndexed { i, cfg ->
                if (i > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
                val fieldError = state.missingRequiredKey == cfg.key
                // 同型第二枚起从备选池取，避免一屏两个相同图标
                val baseIcon = fieldTypeIcon(cfg.type)
                val pool = ICON_ALTERNATES[cfg.type] ?: listOf(baseIcon)
                val used = usedIcons.getOrDefault(baseIcon, 0)
                val rowIcon = pool[used % pool.size]
                usedIcons[baseIcon] = used + 1
                when (cfg.type) {
                    FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.URL, FieldType.EMAIL,
                    FieldType.PHONE, FieldType.LOCATION, FieldType.PERSON ->
                        InlineTextRow(cfg, rowIcon, accent, state, host, fieldError)
                    FieldType.NUMBER, FieldType.CURRENCY, FieldType.DURATION ->
                        NumberPillRow(cfg, rowIcon, accent, state, host, fieldError)
                    FieldType.DATE -> DatePillRow(cfg, rowIcon, accent, state, host, fieldError)
                    FieldType.DATETIME -> DateTimePillRow(cfg, rowIcon, accent, state, host, fieldError)
                    FieldType.TIME -> TimePillRow(cfg, rowIcon, accent, state, host, fieldError)
                    FieldType.RATING -> RatingRow(cfg, rowIcon, accent, state, host, fieldError)
                    FieldType.BOOLEAN -> BoolRow(cfg, rowIcon, accent, state, host, fieldError)
                    // 兜底成文本框而**不是什么都不画**：将来有人往 INLINE_FORM_TYPES 里加了类型
                    // 却忘了加分支，字段会「静默消失」——那是最难查的一类缺陷（原来这里是 `else -> {}`）。
                    else -> InlineTextRow(cfg, rowIcon, accent, state, host, fieldError)
                }
            }
        }
    }
}

// ───────────────────────── 各类型的单行实现 ─────────────────────────

@Composable
private fun InlineTextRow(
    cfg: FieldConfig,
    rowIcon: ImageVector,
    accent: Color, state: CreateRecordUiState, host: FieldFormHost, fieldError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(rowIcon, accent, cfg.label, false, fieldError, Modifier.weight(1f))
        FormTextField(
            value = state.values[cfg.key].orEmpty(),
            onValueChange = { host.updateValue(cfg.key, it) },
            placeholder = cfg.placeholder.ifBlank { cfg.label },
            modifier = Modifier.weight(1.1f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End
            )
        )
    }
}

@Composable
private fun NumberPillRow(
    cfg: FieldConfig,
    rowIcon: ImageVector,
    accent: Color, state: CreateRecordUiState, host: FieldFormHost, fieldError: Boolean
) {
    val step = progressStepFor(cfg, state.values[cfg.progressTargetKey]?.toDoubleOrNull())
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(rowIcon, accent, cfg.label, cfg.required, fieldError, Modifier.weight(0.9f))
        // 有步长就给 −/+（与详情页同一个控件，手感一致）；没有就只留输入框。
        if (step != null) {
            ProgressStepper(step = step, accent = accent) { delta ->
                // 表单侧是本地状态（同步），直接算新值即可；边界与详情页同一套钳制口径
                var next = (state.values[cfg.key]?.toDoubleOrNull() ?: 0.0) + delta
                // 与详情页同一套：步长是整数时取整（计数类字段不该出现 9.2 节这种值）
                next = snapNudged(next, step)
                cfg.min?.let { next = next.coerceAtLeast(it) }
                cfg.max?.let { next = next.coerceAtMost(it) }
                host.updateValue(cfg.key, formatNum(next))
            }
            Spacer(Modifier.width(6.dp))
        }
        PillNumberInput(
            value = state.values[cfg.key].orEmpty(),
            placeholder = "0",
            isCurrency = cfg.type == FieldType.CURRENCY,
            onValueChange = { host.updateValue(cfg.key, it) },
            min = cfg.min,
            max = cfg.max,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun DatePillRow(
    cfg: FieldConfig,
    rowIcon: ImageVector,
    accent: Color, state: CreateRecordUiState, host: FieldFormHost, fieldError: Boolean
) {
    var show by remember(cfg.key) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(rowIcon, accent, cfg.label, cfg.required, fieldError, Modifier.weight(1f))
        val date = runCatching { LocalDate.parse(state.values[cfg.key].orEmpty()) }.getOrNull()
        PillValue(
            text = date?.format(dateTimePattern()) ?: stringResource(R.string.life_record_today),
            onClick = { show = true },
            muted = date == null
        )
        if (show) {
            LifeDatePickerDialog(
                initial = date,
                onPick = { host.updateValue(cfg.key, it.toString()) },
                onDismiss = { show = false }
            )
        }
    }
}

@Composable
private fun TimePillRow(
    cfg: FieldConfig,
    rowIcon: ImageVector,
    accent: Color, state: CreateRecordUiState, host: FieldFormHost, fieldError: Boolean
) {
    var show by remember(cfg.key) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(rowIcon, accent, cfg.label, cfg.required, fieldError, Modifier.weight(1f))
        val time = runCatching { LocalTime.parse(state.values[cfg.key].orEmpty()) }.getOrNull()
        PillValue(
            text = time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: stringResource(R.string.life_record_now),
            onClick = { show = true },
            muted = time == null
        )
        if (show) {
            LifeTimePickerDialog(
                initial = time ?: LocalTime.now(),
                onPick = { host.updateValue(cfg.key, it.format(DateTimeFormatter.ofPattern("HH:mm"))) },
                onDismiss = { show = false }
            )
        }
    }
}

// ───────────────────────── DATETIME ─────────────────────────

/**
 * DATETIME 的**存储形态** `yyyy-MM-dd HH:mm`。
 *
 * 必须与三处对齐，否则又会掉进「同一事实两种写法」的老坑：
 * - `LifeCreateRecordViewModel` 的智能默认值（`"${LocalDate.now()} ${…HH:mm}"`）；
 * - `DateUtils.parseDateValueOrNull` 认的第二种写法（执行列 `dueDate` 的镜像靠它）；
 * - `LifeDetailModel` 详情页的 DATETIME 行。
 */
private val DATE_TIME_STORE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val TIME_HHMM_FMT = DateTimeFormatter.ofPattern("HH:mm")

/** 只有一半时另一半用「今天 / 现在」补齐，避免存出解析不出来的半成品。 */
internal fun storeDateTime(date: LocalDate?, time: LocalTime?): String =
    DATE_TIME_STORE_FMT.format(LocalDateTime.of(date ?: LocalDate.now(), time ?: LocalTime.now()))

/** 宽容解析存量 DATETIME 值：认完整 `yyyy-MM-dd HH:mm`，也认只有日期 / 只有时间的半成品。 */
internal fun parseDateTimeParts(raw: String): Pair<LocalDate?, LocalTime?> {
    if (raw.isBlank()) return null to null
    runCatching { LocalDateTime.parse(raw, DATE_TIME_STORE_FMT) }
        .getOrNull()?.let { return it.toLocalDate() to it.toLocalTime() }
    runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it to null }
    return null to runCatching { LocalTime.parse(raw, TIME_HHMM_FMT) }.getOrNull()
}

/**
 * DATETIME 行：**日期胶囊 + 时间胶囊**两枚，各开各的选择器。
 *
 * 此前 DATETIME 落到 `ComplexFieldCard` 的 `else -> FormTextField`，用户得手打
 * `2026-09-29 20:00` 这种字符串；而 ViewModel 又已经给它预填了默认值，于是变成
 * 「预填了却没好好改的办法」。拆成两枚胶囊后，改一半不会丢掉另一半。
 */
@Composable
private fun DateTimePillRow(
    cfg: FieldConfig,
    rowIcon: ImageVector,
    accent: Color, state: CreateRecordUiState, host: FieldFormHost, fieldError: Boolean
) {
    var showDate by remember(cfg.key) { mutableStateOf(false) }
    var showTime by remember(cfg.key) { mutableStateOf(false) }
    val (date, time) = parseDateTimeParts(state.values[cfg.key].orEmpty())
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(rowIcon, accent, cfg.label, cfg.required, fieldError, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PillValue(
                text = date?.format(dateTimePattern()) ?: stringResource(R.string.life_record_today),
                onClick = { showDate = true },
                muted = date == null
            )
            PillValue(
                text = time?.format(TIME_HHMM_FMT) ?: stringResource(R.string.life_record_now),
                onClick = { showTime = true },
                muted = time == null
            )
        }
        if (showDate) {
            LifeDatePickerDialog(
                initial = date,
                onPick = { picked ->
                    host.updateValue(cfg.key, storeDateTime(picked, time))
                    showDate = false
                },
                onDismiss = { showDate = false }
            )
        }
        if (showTime) {
            LifeTimePickerDialog(
                initial = time ?: LocalTime.now(),
                onPick = { picked ->
                    host.updateValue(cfg.key, storeDateTime(date, picked))
                    showTime = false
                },
                onDismiss = { showTime = false }
            )
        }
    }
}

@Composable
private fun RatingRow(
    cfg: FieldConfig,
    rowIcon: ImageVector,
    accent: Color, state: CreateRecordUiState, host: FieldFormHost, fieldError: Boolean
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(rowIcon, accent, cfg.label, cfg.required, fieldError, Modifier.weight(1f))
        val rating = state.values[cfg.key].orEmpty().toIntOrNull() ?: 0
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 1..5) {
                Text(
                    if (i <= rating) "★" else "☆",
                    color = if (i <= rating) Color(0xFFFFCA28) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            host.updateValue(cfg.key, i.toString())
                        }
                        .padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun BoolRow(
    cfg: FieldConfig,
    rowIcon: ImageVector,
    accent: Color, state: CreateRecordUiState, host: FieldFormHost, fieldError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(rowIcon, accent, cfg.label, false, fieldError, Modifier.weight(1f))
        Switch(
            checked = state.values[cfg.key].orEmpty().toBooleanStrictOrNull() ?: false,
            onCheckedChange = { host.updateValue(cfg.key, it.toString()) }
        )
    }
}


/** 复合字段卡：标签行在上、控件在下（chips / 滑条 / 多行 / 媒体）。 */
@Composable
internal fun ComplexFieldCard(
    cfg: FieldConfig,
    accent: Color,
    state: CreateRecordUiState,
    host: FieldFormHost,
    context: android.content.Context
) {
    val fieldError = state.missingRequiredKey == cfg.key
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                2.dp, RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            RowLabel(fieldTypeIcon(cfg.type), accent, cfg.label, cfg.required, fieldError)
            Spacer(Modifier.height(8.dp))
            when (cfg.type) {
                FieldType.RICH_TEXT -> FormTextField(
                    value = state.values[cfg.key].orEmpty(),
                    onValueChange = { host.updateValue(cfg.key, it) },
                    placeholder = cfg.placeholder.ifBlank { "输入${cfg.label}" },
                    singleLine = false, minLines = 4, maxLines = 10
                )
                FieldType.SELECT -> SelectSection(cfg, accent, state, host, context)
                FieldType.TAG -> TagSection(cfg, accent, state, host)
                FieldType.MULTI_SELECT -> MultiSelectSection(cfg, state, host)
                FieldType.PERCENT, FieldType.PERCENTAGE -> PercentSection(cfg, accent, state, host)
                FieldType.PERSON -> PersonChipsField(cfg, accent, state, host)
                FieldType.IMAGE, FieldType.VIDEO, FieldType.AUDIO, FieldType.FILE -> MediaControl(cfg, state, host)
                FieldType.CHECKLIST, FieldType.MAP, FieldType.TABLE, FieldType.RANGE ->
                    CompoundControl(cfg, accent, state, host, context)
                FieldType.SLIDER -> SliderSection(cfg, accent, state, host)
                FieldType.COLOR -> ColorSection(cfg, accent, state, host)
                else -> FormTextField(
                    value = state.values[cfg.key].orEmpty(),
                    onValueChange = { host.updateValue(cfg.key, it) },
                    placeholder = cfg.placeholder.ifBlank { "输入${cfg.label}" }
                )
            }
        }
    }
}

@Composable
private fun SelectSection(
    cfg: FieldConfig, accent: Color, state: CreateRecordUiState, host: FieldFormHost, context: android.content.Context
) {
    when {
        cfg.key == "mood" -> MoodEmojiField(cfg, accent, state, host)
        cfg.key == "weather" -> EmojiChips(
            cfg.options, WEATHER_EMOJI, state.values[cfg.key].orEmpty()
        ) { host.updateValue(cfg.key, it) }
        else -> FlowChips(
            labels = cfg.options.map { selectOptionLabel(context, it) },
            values = cfg.options,
            selected = state.values[cfg.key].orEmpty(),
            onPick = { host.updateValue(cfg.key, it) }
        )
    }
}

@Composable
private fun TagSection(cfg: FieldConfig, accent: Color, state: CreateRecordUiState, host: FieldFormHost) {
    if (cfg.options.isEmpty()) {
        FormTextField(
            value = state.values[cfg.key].orEmpty(),
            onValueChange = { host.updateValue(cfg.key, it) },
            placeholder = "输入标签，用逗号分隔"
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.values[cfg.key].orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }.take(6).forEach { tag ->
                Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(7.dp)) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    } else {
        FlowChips(cfg.options, cfg.options, state.values[cfg.key].orEmpty()) { host.updateValue(cfg.key, it) }
    }
}

@Composable
private fun MultiSelectSection(cfg: FieldConfig, state: CreateRecordUiState, host: FieldFormHost) {
    val context = LocalContext.current
    // 选项**既是显示也是存库的值**（MULTI_SELECT 没有独立的 key），所以显示与值必须同源：
    // 只翻显示、值留中文的话，英文界面里新建的记录会存下中文，详情页再原样渲染出中文。
    val options = remember(cfg.options, context) {
        cfg.options.map { BuiltinFieldText.localize(context, it) }
    }
    FlowChips(
        labels = options,
        values = options,
        selectedList = state.values[cfg.key].orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() },
        onToggle = { opt ->
            val cur = state.values[cfg.key].orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
            val next = if (opt in cur) cur - opt else cur + opt
            host.updateValue(cfg.key, next.joinToString(","))
        }
    )
}

@Composable
private fun PercentSection(cfg: FieldConfig, accent: Color, state: CreateRecordUiState, host: FieldFormHost) {
    val pct = state.values[cfg.key].orEmpty().toFloatOrNull() ?: 0f
    Text(
        "${pct.toInt()}%",
        style = MaterialTheme.typography.labelLarge,
        color = accent,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End
    )
    Slider(
        value = pct.coerceIn(0f, 100f),
        onValueChange = { host.updateValue(cfg.key, it.toInt().toString()) },
        valueRange = 0f..100f
    )
}

// ───────────────────────── SLIDER / COLOR ─────────────────────────

/**
 * 滑条控件（SLIDER）：区间取 `cfg.min` / `cfg.max`（默认 0–100），步长取 `cfg.step`。
 *
 * 此前 SLIDER 落在 `else -> FormTextField`，用户得手打数字。存的是**数值字符串**，
 * 与 NUMBER 同一形态，编辑态回填（`formatNumForEdit`）不会丢精度。
 */
@Composable
private fun SliderSection(
    cfg: FieldConfig, accent: Color, state: CreateRecordUiState, host: FieldFormHost
) {
    val min = (cfg.min ?: 0.0).toFloat()
    val max = (cfg.max ?: 100.0).toFloat().coerceAtLeast(min)
    val step = (cfg.step ?: 1.0).toFloat().takeIf { it > 0f } ?: 1f
    val raw = state.values[cfg.key]?.toFloatOrNull()
    val value = (raw ?: min).coerceIn(min, max)
    // steps = 两端点之间的**离散点数**。只在数量合理时启用：`0..100000 step 1` 会生成十万个刻度。
    val steps = ((max - min) / step).toInt() - 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = value,
            onValueChange = { host.updateValue(cfg.key, snapSliderValue(it, step)) },
            valueRange = min..max,
            steps = steps.takeIf { it in 0..20 } ?: 0,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = trimNumber(value) + cfg.unit,
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
    }
}

/** 按步长吸附后存字符串；整数不带小数点。 */
internal fun snapSliderValue(v: Float, step: Float): String {
    val snapped = if (step > 0f) round(v / step) * step else v
    return trimNumber(snapped)
}

/** 数值 → 紧凑字符串：`50.0` → `50`，`50.5` → `50.5`（固定 Locale.US，避免逗号小数点）。 */
internal fun trimNumber(v: Float): String {
    val asLong = v.toLong()
    if (asLong.toFloat() == v) return asLong.toString()
    return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}

/** 预设色板：与模板身份色同一写法（`#RRGGBB`），不引第三方取色器。 */
private val COLOR_SWATCHES = listOf(
    "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00ACC1", "#1E88E5",
    "#5E35B1", "#D81B60", "#6D4C41", "#546E7A", "#000000", "#FFFFFF"
)

/**
 * 取色控件（COLOR）：预设色板点选，存 `#RRGGBB`。
 *
 * 此前 COLOR 落在 `else -> FormTextField`，用户得手打十六进制。**保留「清除」**：
 * 换回文本框之前是能清空的，不能因为换成色板就把「取消选择」的能力弄丢。
 */
@Composable
private fun ColorSection(
    cfg: FieldConfig, accent: Color, state: CreateRecordUiState, host: FieldFormHost
) {
    val selected = state.values[cfg.key].orEmpty().uppercase(Locale.US)
    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            COLOR_SWATCHES.forEach { hex ->
                ColorSwatch(
                    color = parseHexColor(hex),
                    selected = selected == hex.uppercase(Locale.US),
                    accent = accent
                ) { host.updateValue(cfg.key, hex) }
            }
        }
        if (selected.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.life_form_clear),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { host.updateValue(cfg.key, "") }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                BorderStroke(
                    if (selected) 3.dp else 1.dp,
                    if (selected) accent else MaterialTheme.colorScheme.outlineVariant
                ),
                CircleShape
            )
            .clickable(onClick = onClick)
    )
}

/** `#RRGGBB` / `#AARRGGBB` → Color；认不出返回透明，绝不抛（脏值不该让整页打不开）。 */
internal fun parseHexColor(hex: String): Color = runCatching {
    val raw = hex.removePrefix("#")
    when (raw.length) {
        6 -> Color(0xFF000000L or raw.toLong(16))
        8 -> Color(raw.toLong(16))
        else -> Color.Transparent
    }
}.getOrDefault(Color.Transparent)


/** 单选 chips（labels 显示 / values 提交，按索引对应）。 */
@Composable
internal fun FlowChips(labels: List<String>, values: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { i, v ->
            FilterChip(
                selected = selected == v,
                onClick = { onPick(v) },
                label = { Text(labels[i], style = MaterialTheme.typography.bodySmall) }
            )
        }
    }
}

/** 多选 chips。 */
@Composable
internal fun FlowChips(labels: List<String>, values: List<String>, selectedList: List<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { i, v ->
            FilterChip(
                selected = v in selectedList,
                onClick = { onToggle(v) },
                label = { Text(labels[i], style = MaterialTheme.typography.bodySmall) }
            )
        }
    }
}

@Composable
private fun PersonChips(
    people: List<String>,
    accent: Color,
    haptics: HapticFeedback,
    onRemove: (List<String>) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        people.forEach { person ->
            Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.12f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Icon(Icons.Filled.Person, null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(person, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRemove(people - person)
                    }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.Close, "移除" + person, modifier = Modifier.size(13.dp), tint = accent)
                    }
                }
            }
        }
    }
}

/** 人物字段：多人（逗号分隔存储），chip 可删、输入后添加。 */
@Composable
private fun PersonChipsField(cfg: FieldConfig, accent: Color, state: CreateRecordUiState, host: FieldFormHost) {
    val haptics = LocalHapticFeedback.current
    var input by remember { mutableStateOf("") }
    val people = state.values[cfg.key].orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (people.isNotEmpty()) {
            PersonChips(people, accent, haptics) { remaining ->
                host.updateValue(cfg.key, remaining.joinToString(","))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FormTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = if (people.isEmpty()) cfg.placeholder.ifBlank { "输入" + cfg.label } else "再添加一位",
                modifier = Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    if (input.isNotBlank()) {
                        host.updateValue(cfg.key, (people + input.trim()).joinToString(","))
                        input = ""
                    }
                })
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = accent.copy(alpha = 0.14f),
                onClick = {
                    if (input.isNotBlank()) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        host.updateValue(cfg.key, (people + input.trim()).joinToString(","))
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Add, "添加", tint = accent, modifier = Modifier.padding(8.dp).size(18.dp))
            }
        }
    }
}

/** MOODA 式心情选择（emoji 走 MoodVisuals 单一真源）。 */
@Composable
private fun MoodEmojiField(cfg: FieldConfig, accent: Color, state: CreateRecordUiState, host: FieldFormHost) {
    val haptics = LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cfg.options.forEach { opt ->
            val selected = state.values[cfg.key] == opt
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (selected) BorderStroke(1.5.dp, accent) else null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    host.updateValue(cfg.key, opt)
                }
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(MoodVisuals.emojiOf(opt), fontSize = 24.sp)
                    Text(
                        opt,
                        fontSize = TypeScale.labelS,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 天气 emoji chips。 */
@Composable
private fun EmojiChips(values: List<String>, emojiMap: Map<String, String>, selected: String, onPick: (String) -> Unit) {
    FlowChips(
        labels = values.map { "${emojiMap[it].orEmpty()} $it" },
        values = values,
        selected = selected,
        onPick = onPick
    )
}

/** 日期胶囊显示格式：中文环境用 yyyy年M月d日，其余 MMM d, yyyy。 */
internal fun dateTimePattern(): DateTimeFormatter =
    if (Locale.getDefault().language == "zh") {
        DateTimeFormatter.ofPattern("yyyy年M月d日")
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy")
    }
@Composable
internal fun ImagePickField(value: String, onValueChange: (String) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onValueChange(it.toString()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (value.isNotBlank()) {
            AsyncImage(
                model = value,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("更换", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("移除", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("选择图片")
            }
        }
    }
}

/**
 * 已选文件的**用户可读名字**（`OpenableColumns.DISPLAY_NAME`）。
 *
 * `content://` URI 本身是一串不透明 id，直接显示等于没显示。解析不到就依次退回
 * URI 末段、最后退回原串 —— **绝不返回空串**，否则用户会以为「没选上」。
 */
internal fun displayNameOf(context: android.content.Context, uriString: String): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
    val queried = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
    return queried?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: uriString
}

/**
 * 视频 / 语音 / 附件：按 mime 拉起 SAF（`ActivityResultContracts.OpenDocument`），
 * 存 **URI 字符串**（与 IMAGE 同形态：`buildFieldsData` 的 `else` 分支原样落库，
 * `decodeValue` 原样读回，所以 ViewModel 无需改动）。
 *
 * 与 [ImagePickField] 的区别：不做预览（视频帧 / 音频波形要额外解码器），改为显示**文件名**；
 * 整行可点即「换一个」，右侧 × 清除。
 */
@Composable
internal fun MediaPickField(
    value: String,
    mimeTypes: Array<String>,
    typeLabel: String,
    icon: ImageVector,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onValueChange(it.toString()) }
    }
    val launch = { picker.launch(mimeTypes) }
    if (value.isBlank()) {
        OutlinedButton(onClick = launch, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(typeLabel)
        }
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            onClick = launch
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = displayNameOf(context, value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        stringResource(R.string.life_form_clear),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChecklistField(value: String, onValueChange: (String) -> Unit) {
    val rows = remember(value) {
        parseChecklist(value).takeIf { it.isNotEmpty() || value.isBlank() }
            ?: value.lines().filter { it.isNotBlank() }.map { ChecklistRow(it, false) }
    }
    var newItem by remember { mutableStateOf("") }

    fun commit(next: List<ChecklistRow>) = onValueChange(encodeChecklist(next))

    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        rows.forEachIndexed { i, row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = row.done,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        commit(rows.toMutableList().also { it[i] = row.copy(done = checked) })
                    }
                )
                Text(
                    row.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (row.done) TextDecoration.LineThrough else null,
                    color = if (row.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { commit(rows.toMutableList().also { it.removeAt(i) }) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.life_record_delete_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        FormTextField(
            value = newItem,
            onValueChange = { newItem = it },
            placeholder = stringResource(R.string.life_record_add_item),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = {
                if (newItem.isNotBlank()) {
                    commit(rows + ChecklistRow(newItem.trim(), false))
                    newItem = ""
                }
            })
        )
    }
}


@Composable
private fun ImageSection(cfg: FieldConfig, state: CreateRecordUiState, host: FieldFormHost) {
    val value = state.values[cfg.key].orEmpty()
    if (value.isBlank()) {
        DashedArea {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("添加图片", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("选一张照片放在最显眼的位置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ImagePickField(value = "", onValueChange = { host.updateValue(cfg.key, it) })
            }
        }
    } else {
        ImagePickField(value = value, onValueChange = { host.updateValue(cfg.key, it) })
    }
}

/**
 * E 组复合字段（清单 / 地图 / 明细表 / 区间）的共用分派。
 *
 * 与 [MediaControl] 同一个理由拆出来：这四个把 [ComplexFieldCard] 的 `when` 顶到了 detekt
 * 的复杂度阈值，而它们本来就同属一组（`FieldContract` 的 COMPOUND 组）。
 */
@Composable
private fun CompoundControl(
    cfg: FieldConfig,
    accent: Color,
    state: CreateRecordUiState,
    host: FieldFormHost,
    context: android.content.Context
) {
    when (cfg.type) {
        FieldType.CHECKLIST -> ChecklistSection(cfg, state, host)
        FieldType.MAP -> MapSection(cfg, state, host, context)
        FieldType.TABLE -> TableSection(cfg, accent, state, host)
        FieldType.RANGE -> RangeSection(cfg, state, host)
        // 兜底成文本框而不是什么都不画（与其它分派同一个理由）。
        else -> FormTextField(
            value = state.values[cfg.key].orEmpty(),
            onValueChange = { host.updateValue(cfg.key, it) },
            placeholder = cfg.placeholder.ifBlank { "输入${cfg.label}" }
        )
    }
}

/**
 * 媒体类四种（图片 / 视频 / 语音 / 附件）的共用分派：mime 与图标收在一处。
 *
 * 单独拆出来是为了让 [ComplexFieldCard] 的 `when` 不因为「多了三个媒体类型」而超出
 * detekt 的复杂度/长度阈值 —— 结构上本来也该分（这四种的差异只有 mime 与图标）。
 */
@Composable
private fun MediaControl(cfg: FieldConfig, state: CreateRecordUiState, host: FieldFormHost) {
    when (cfg.type) {
        FieldType.IMAGE -> ImageSection(cfg, state, host)
        FieldType.VIDEO -> MediaSection(
            cfg, state, host,
            mimeTypes = arrayOf("video/*"),
            hintRes = R.string.life_field_hint_video,
            typeRes = R.string.life_field_type_video,
            icon = Icons.Filled.PlayArrow
        )
        FieldType.AUDIO -> MediaSection(
            cfg, state, host,
            mimeTypes = arrayOf("audio/*"),
            hintRes = R.string.life_field_hint_audio,
            typeRes = R.string.life_field_type_audio,
            icon = Icons.Filled.MusicNote
        )
        FieldType.FILE -> MediaSection(
            cfg, state, host,
            mimeTypes = arrayOf("*/*"),
            hintRes = R.string.life_field_hint_file,
            typeRes = R.string.life_template_field_file,
            icon = Icons.Filled.AttachFile
        )
        // 兜底成文本框而不是什么都不画（与 SimpleRowsCard 同一个理由）。
        else -> FormTextField(
            value = state.values[cfg.key].orEmpty(),
            onValueChange = { host.updateValue(cfg.key, it) },
            placeholder = cfg.placeholder.ifBlank { "输入${cfg.label}" }
        )
    }
}

/** 媒体字段卡（视频 / 语音 / 附件）：空态虚线区 + 提示；有值就显示文件名。 */
@Composable
private fun MediaSection(
    cfg: FieldConfig,
    state: CreateRecordUiState,
    host: FieldFormHost,
    mimeTypes: Array<String>,
    hintRes: Int,
    typeRes: Int,
    icon: ImageVector
) {
    val value = state.values[cfg.key].orEmpty()
    val pick: (String) -> Unit = { host.updateValue(cfg.key, it) }
    if (value.isBlank()) {
        DashedArea {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(hintRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                MediaPickField(
                    value = "",
                    mimeTypes = mimeTypes,
                    typeLabel = stringResource(typeRes),
                    icon = icon,
                    onValueChange = pick
                )
            }
        }
    } else {
        MediaPickField(
            value = value,
            mimeTypes = mimeTypes,
            typeLabel = stringResource(typeRes),
            icon = icon,
            onValueChange = pick
        )
    }
}

@Composable
private fun ChecklistSection(cfg: FieldConfig, state: CreateRecordUiState, host: FieldFormHost) {
    val value = state.values[cfg.key].orEmpty()
    val hasRows = runCatching { parseChecklist(value) }.getOrDefault(emptyList()).isNotEmpty()
    if (hasRows) {
        ChecklistField(value = value, onValueChange = { host.updateValue(cfg.key, it) })
    } else {
        DashedArea {
            ChecklistField(value = value, onValueChange = { host.updateValue(cfg.key, it) })
        }
    }
}

@Composable
private fun MapSection(cfg: FieldConfig, state: CreateRecordUiState, host: FieldFormHost, context: android.content.Context) {
    val value = state.values[cfg.key].orEmpty()
    if (value.isBlank()) {
        DashedArea {
            MapImportField(
                value = value,
                importing = state.importingTrack && state.trackImportFieldKey == cfg.key,
                onImport = { uri, name -> host.importTrack(context, uri, name ?: cfg.key) }
            )
        }
    } else {
        MapImportField(
            value = value,
            importing = state.importingTrack && state.trackImportFieldKey == cfg.key,
            onImport = { uri, name -> host.importTrack(context, uri, name ?: cfg.key) }
        )
    }
}
