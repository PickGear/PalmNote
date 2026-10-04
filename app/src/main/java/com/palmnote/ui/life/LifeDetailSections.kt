package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.theme.ListCardShape
import com.palmnote.ui.theme.RatingStar
import com.palmnote.ui.theme.SharePalette
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.Success
import com.palmnote.ui.theme.TypeScale
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * ② 主指标行 与 ③ 结构区（§14.2 / §14.12(4)(5)）。
 *
 * - ② **只在英雄区不是指标型时出现**（P2 / 变化型一律不出现，避免同屏两处进度）。
 * - ③ 按字段的「手」分组：**每组一张卡**、**组标题在卡外**（本仓库既有 UI 纪律：
 *   标题只在卡片外，卡内不再印一遍），**组内行间**加分割线、首行不加。
 */

/** 分区卡片：③ 结构区用 12dp 圆角（§14.12(2)(1)）。 */
@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 极浅的柔影代替纯描边的"硬"感（个人开发者风格的卡片质感）；描边保留兜底暗色主题
            .shadow(2.dp, ListCardShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), ListCardShape),
        shape = ListCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

/** ② 主指标行：等宽 n 等分，值 15sp/700、标签 10sp，分隔竖线 @0.35。 */
@Composable
fun MetricRow(metrics: List<DetailMetric>, modifier: Modifier = Modifier) {
    if (metrics.isEmpty()) return
    DetailCard(modifier) {
        Row(modifier = Modifier.height(Spacing.xxl), verticalAlignment = Alignment.CenterVertically) {
            metrics.forEachIndexed { index, m ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 值 15sp / 700、标签 10sp（设计稿 dtl_01–16 的 ② 指标行实测）
                    Text(
                        if (m.formatRes != 0) stringResource(m.formatRes, m.value) else m.value,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = TypeScale.metricValue),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (m.labelRes != 0) stringResource(m.labelRes) else m.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.labelS),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** ③ 一组字段：组标题（**卡外**）+ 一张卡；组内首行不加分割线（§14.12(5)）。 */
@Composable
fun StructureGroupBlock(
    group: DetailGroup,
    accent: Color,
    actions: DetailRowActions = DetailRowActions(),
    config: FieldConfig? = null,
    rawValue: String? = null,
    /**
     * 是否显示组标题。**只有一组时不显示** —— 市面上的做法：不出现无信息的标签。
     * 多组时手名（「数值 / 文本 / 明细与清单」）才有分组的意义。
     */
    showTitle: Boolean = true
) {
    if (group.rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTitle) {
            Row(
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 组标题带身份色小圆点：页面纵向读起来有色彩节奏（纯装饰，不承担语义）
                Box(Modifier.size(5.dp).clip(CircleShape).background(accent.copy(alpha = 0.75f)))
                Text(
                    groupTitle(group),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DetailCard {
            group.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(horizontal = Spacing.sm)
                    )
                }
                DetailRowView(row, accent, group.fieldKey, actions, config, rawValue)
            }
        }
    }
}

/**
 * 组标题：**手名**（`titleRes`，如「数值 / 文本 / 明细与清单」）或模板专属块名。
 *
 * §14.2 定案：结构区按**字段的手**分组 —— 组标题是手名，**不是字段名**。
 * （此前是一字段一张卡、组标题 = 字段名，于是组标题与卡内标签重复一遍。）
 * 兜底用 `group.title`：仅旅行模板的显式分组会走到（它本来就用 `titleRes`）。
 */
@Composable
private fun groupTitle(group: DetailGroup): String = when {
    group.titleRes != 0 -> stringResource(group.titleRes)
    else -> group.title
}

@Composable
private fun RowShell(height: Dp, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = Spacing.sm)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** 组内展开/收起切换（渐进披露：截断阈值之上给「展开全部 N 条」）。 */
@Composable
private fun ExpandToggle(shown: Int, total: Int, expanded: Boolean, onToggle: () -> Unit) {
    Text(
        if (expanded) stringResource(R.string.life_detail_show_less)
        else stringResource(R.string.life_detail_show_all, total),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.sm, vertical = 6.dp)
    )
}

/**
 * 只读开关（真值来自数据；**详情页不给改** —— 它不是「一步完成」的动作，§14.12(8)）。
 *
 * 复用工程既有的 [CapsuleSwitch] 外形（与设置页同一套视觉语言，不新造控件），
 * 但 `onCheckedChange = null` ⟹ **点击不产生任何状态变化**；`indication = null` 也
 * 不出涟漪，因此不会给出「按了会变」的假反馈。
 */
@Composable
private fun ReadOnlySwitch(checked: Boolean, accent: Color) {
    CapsuleSwitch(
        checked = checked,
        onCheckedChange = null,
        checkedTrackColor = accent
    )
}

/**
 * 结构区可以**就地做**的三件事，打包成一个对象。
 *
 * 打包的理由是接线成本：这三件事要从屏幕 → `DetailBody` → `StructureGroupBlock` →
 * `DetailRowView` 走四层，每加一个动作就多一个参数（`DetailBody` 已经因此顶到 detekt 的
 * 参数上限）。打包后加动作只改这里一处。
 */
data class DetailRowActions(
    val onToggleChecklist: ((String, Int) -> Unit)? = null,
    val onSetProgress: ((String, Double) -> Unit)? = null,
    /**
     * −/+ 的**增量**推进：`(fieldKey, delta, min, max)`。
     *
     * 与 [onSetProgress]（绝对值，来自"点数值就地编辑"）分开，是因为两者在界面上相邻：
     * 敲完数字再点 ± 时，绝对值那条会用界面旧值算，把刚提交的覆盖掉（见
     * `LifeDetailViewModel.nudgeProgress` 的说明）。
     */
    val onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)? = null,
    val onToggleCell: ((String, Int, Int) -> Unit)? = null,
    /** 点值即改：`(fieldKey, 表单字符串)`。表单字符串的编码口径见 `LifeFieldCodec`。 */
    val onEditField: ((String, String) -> Unit)? = null
)

/**
 * 「距某天的天数」按方向措辞：还有 N 天 / 已过 N 天 / 就是今天。
 *
 * 与首页卡片同口径（卡片侧是 `LifeRecordCard.cardFieldText`）。
 * 数字解析不出来（占位符 `—`、或用户给该字段配了单位）就原样返回，不猜。
 */
@Composable
private fun elapsedDaysText(value: String?): String {
    val days = value?.trim()?.toLongOrNull()
    return when {
        days == null -> value ?: DetailCtx.PLACEHOLDER
        days == 0L -> stringResource(R.string.life_card_elapsed_today)
        // quantity 参数要 Int（days 是 Long），格式化参数仍用原值
        days > 0 -> pluralStringResource(R.plurals.dashboard_days_until, days.toInt(), days)
        else -> pluralStringResource(R.plurals.dashboard_days_passed, (-days).toInt(), -days)
    }
}

@Composable
fun DetailRowView(
    row: DetailRowModel,
    accent: Color,
    groupFieldKey: String? = null,
    actions: DetailRowActions = DetailRowActions(),
    /** 这一行所属字段的配置与**原始表单值**（[rawValue] 用于就地编辑的初值，
     *  不能用行里的 `value` —— 那是格式化后的显示串，写回会把「9月26日」存进日期字段）。 */
    config: FieldConfig? = null,
    rawValue: String? = null
) {
    when (row) {
        is DetailRowModel.CheckInStats -> Column(modifier = Modifier.fillMaxWidth()) {
            StatTripleRow(
                stats = buildList {
                    add(stringResource(R.string.life_detail_stat_week) to "${row.hits}/${row.window} · ${row.percent}%")
                    row.monthCount?.let { add(stringResource(R.string.life_detail_stat_month) to it.toString()) }
                    row.total?.let { add(stringResource(R.string.life_detail_stat_total) to it.toString()) }
                }
            )
        }

        is DetailRowModel.Kv -> {
            var editingField by remember(row.label) { mutableStateOf(false) }
            val cfg = config
            val canEdit = cfg != null && isInlineEditable(cfg.type) && actions.onEditField != null
            // 点值即改（其他 todo / 计划类 app 的做法）：**值本身就是入口**，
            // 不必「⋯ → 编辑记录 → 找字段 → 保存」四步。
            RowShell(32.dp, onClick = if (canEdit) ({ editingField = true }) else null) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                val display = if (cfg?.type == FieldType.ELAPSED) {
                    // 方向类字段与首页卡片同口径：还有 / 已过 / 就是今天
                    //（此前直接把带符号的数字摊出来：标签写「剩余天数」而值是 `-3`，两个说法互相打脸）
                    elapsedDaysText(row.value)
                } else {
                    row.value ?: DetailCtx.PLACEHOLDER
                }
                val isPlaceholder = display == DetailCtx.PLACEHOLDER
                Text(
                    display,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = TypeScale.bodyRead,
                    // §14.2 规则 2：空值「显示 `—` 与**浅提示**」—— 占位符不能与真实值同色同粗。
                    // 此前 Kv 行是 onSurface + SemiBold，看着像"真有一个值"；而清单 / 明细表的空态
                    // 用的是浅色 → 同一页两种写法。
                    fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isPlaceholder) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (editingField && cfg != null && actions.onEditField != null) {
                val commit: (String) -> Unit = { actions.onEditField.invoke(cfg.key, it) }
                val close = { editingField = false }
                when (cfg.type) {
                    // 日期 / 时间直接用系统选择器 —— 少一层弹窗
                    FieldType.DATE -> LifeDatePickerDialog(
                        initial = runCatching { LocalDate.parse(rawValue.orEmpty()) }.getOrNull(),
                        onPick = { commit(it.toString()); close() },
                        onDismiss = close
                    )
                    FieldType.TIME -> LifeTimePickerDialog(
                        initial = runCatching { LocalTime.parse(rawValue.orEmpty()) }.getOrNull() ?: LocalTime.now(),
                        onPick = { commit(it.format(DateTimeFormatter.ofPattern("HH:mm"))); close() },
                        onDismiss = close
                    )
                    FieldType.DATETIME -> {
                        // 只改日期、保住原时刻（与表单里 DATETIME 那对胶囊同一约定）
                        val (date, time) = parseDateTimeParts(rawValue.orEmpty())
                        LifeDatePickerDialog(
                            initial = date,
                            onPick = { commit(storeDateTime(it, time)); close() },
                            onDismiss = close
                        )
                    }
                    else -> QuickEditDialog(
                        config = cfg,
                        initial = rawValue.orEmpty(),
                        onCommit = commit,
                        onDismiss = close
                    )
                }
            }
        }

        // 布尔就地开关：点一下即翻，属「一步完成、可撤销」的动作
        is DetailRowModel.Toggle -> RowShell(
            32.dp,
            onClick = if (actions.onEditField != null && groupFieldKey != null) ({
                actions.onEditField.invoke(groupFieldKey, if (row.checked) "false" else "true")
            }) else null
        ) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            ReadOnlySwitch(checked = row.checked, accent = accent)
        }

        // 色值不是开关：`ctx.flag` 从 "#RRGGBB" 里取布尔恒为 false，这里曾经永远画成「关」。
        is DetailRowModel.Swatch -> RowShell(32.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(row.hex))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                row.hex,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }

        is DetailRowModel.Link -> {
            // 画了「›」就必须能点（affordance 一致性）：http(s) 链接直接打开
            val uriHandler = LocalUriHandler.current
            val openable = row.value.startsWith("http://") || row.value.startsWith("https://")
            RowShell(32.dp, onClick = if (openable) ({ uriHandler.openUri(row.value) }) else null) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                // 空值不是链接：不能画成身份色加粗（那看着像可点），也不该带「›」
                val isPlaceholder = row.value == DetailCtx.PLACEHOLDER
                Text(
                    row.value,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isPlaceholder) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        accent
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isPlaceholder) Text(" ›", style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }

        is DetailRowModel.Stars -> RowShell(32.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            repeat(row.max) { i ->
                Icon(
                    if (i < row.score) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = if (i < row.score) RatingStar else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (i < row.max - 1) Spacer(Modifier.width(Spacing.xxs))
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "${row.score} / ${row.max}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is DetailRowModel.Bar -> if (isRingProgressForm(row.form)) {
            RingBarRow(row, accent)
        } else {
            TrackBarRow(row, accent, actions.onSetProgress, actions.onNudgeProgress, config)
        }

        is DetailRowModel.CheckList -> Column(modifier = Modifier.fillMaxWidth()) {
            if (row.rows.isEmpty()) {
                RowShell(34.dp) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(DetailCtx.PLACEHOLDER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 渐进披露：默认 6 条，超出给「展开全部」；展开后勾圈可用（此前第 7 条起全 App 无法勾选）
                // 以字段 key 为身份：位置型状态在"同一屏换了记录 / 字段顺序变了"时会串
                var expanded by remember(groupFieldKey) { mutableStateOf(false) }
                val visibleRows = if (expanded) row.rows else row.rows.take(6)
                val haptics = LocalHapticFeedback.current
                visibleRows.forEachIndexed { i, (text, done) ->
                    val canToggle = actions.onToggleChecklist != null && groupFieldKey != null
                    RowShell(34.dp, onClick = if (canToggle) ({
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onToggleChecklist?.invoke(groupFieldKey, i)
                    }) else null) {
                        Box(
                            modifier = Modifier
                                // 视觉 16dp、触达扩到 48dp（展开后行可整体点，这里保底）
                                .minimumInteractiveComponentSize()
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (done) Success else Color.Transparent)
                                .border(1.5.dp, if (done) Success else MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (row.rows.size > 6) {
                    ExpandToggle(shown = 6, total = row.rows.size, expanded = expanded) { expanded = !expanded }
                }
            }
        }

        is DetailRowModel.TableRows -> Column(modifier = Modifier.fillMaxWidth()) {
            if (row.rows.isEmpty()) {
                RowShell(32.dp) {
                    Text(row.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.life_detail_no_value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 设计稿 dtl_04「行程明细」：**行高 32、不画表头**；首列作为左值、
                // 其余非空列用「 · 」拼成主行，最后一列作右值（醒目值）。
                // 之前的「表头 + 等宽列」是通用表格外观，与图不符。
                var tableExpanded by remember(groupFieldKey) { mutableStateOf(false) }
                val visibleTableRows = if (tableExpanded) row.rows else row.rows.take(6)
                // 布尔列不参与「左值 · 右值」的文字拼接：它画成可点的勾圈（购物「已买」），
                // 否则用户看到的是字面量 "true"，而且点不动。
                val boolIndices = row.columnTypes.indices.filter { row.columnTypes[it] == FieldType.BOOLEAN }
                // 日期列也用全 app 一致的短格式（同年 MM-dd），而不是把 ISO 原文摊在详情页上
                val dateIndices = row.columnTypes.indices.filter {
                    row.columnTypes[it] == FieldType.DATE || row.columnTypes[it] == FieldType.DATETIME
                }
                val todayRef = LocalDate.now()
                fun cellText(cells: List<String>, index: Int): String {
                    val raw = cells.getOrNull(index).orEmpty()
                    if (index !in dateIndices) return raw
                    val ms = DateUtils.parseDateValueOrNull(raw) ?: return raw
                    return fmtDateShort(DateUtils.millisToLocalDate(ms), todayRef) ?: raw
                }
                val cellHaptics = LocalHapticFeedback.current
                visibleTableRows.forEachIndexed { rowIndex, cells ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val textIndices = cells.indices.filter { it !in boolIndices }
                        val left = textIndices.dropLast(1)
                            .map { cellText(cells, it) }
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        val right = textIndices.lastOrNull()?.let { cellText(cells, it) }.orEmpty()
                        Text(
                            left.ifBlank { row.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (right.isNotBlank()) {
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                right,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = TypeScale.bodyRead,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val fieldKey = groupFieldKey
                        boolIndices.forEach { colIndex ->
                            Spacer(Modifier.width(Spacing.xs))
                            TickCircle(
                                checked = isTickCell(cells.getOrNull(colIndex)),
                                enabled = actions.onToggleCell != null && fieldKey != null
                            ) {
                                if (actions.onToggleCell != null && fieldKey != null) {
                                    cellHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actions.onToggleCell.invoke(fieldKey, rowIndex, colIndex)
                                }
                            }
                        }
                    }
                }
                if (row.rows.size > 6) {
                    ExpandToggle(shown = 6, total = row.rows.size, expanded = tableExpanded) { tableExpanded = !tableExpanded }
                }

            }
        }

        is DetailRowModel.Chips -> RowShell(38.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            if (row.values.isEmpty()) {
                Text(DetailCtx.PLACEHOLDER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // 渐进披露：默认一行 3 枚，超出给「展开全部」——不再静默丢弃
                var chipsExpanded by remember(groupFieldKey) { mutableStateOf(false) }
                val indexedValues = row.values.mapIndexed { idx, v -> v to (row.valueRes.getOrNull(idx) ?: 0) }
                val visible = if (chipsExpanded) indexedValues else indexedValues.take(3)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visible.chunked(3).forEach { chunk ->
                        Row {
                            chunk.forEachIndexed { i, (v, valueRes) ->
                                if (i > 0) Spacer(Modifier.width(6.dp))
                                ReadOnlyChip(
                                    text = if (valueRes != 0) stringResource(valueRes) else v,
                                    accent = accent
                                )
                            }
                        }
                    }
                }
                if (row.values.size > 3) {
                    ExpandToggle(shown = 3, total = row.values.size, expanded = chipsExpanded) { chipsExpanded = !chipsExpanded }
                }
            }
        }

        is DetailRowModel.Paragraph -> Column(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
            Text(
                row.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                row.text.ifBlank { DetailCtx.PLACEHOLDER },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        is DetailRowModel.Persons -> RowShell(40.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            if (row.names.isEmpty()) {
                Text(DetailCtx.PLACEHOLDER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // 设计稿 dtl_04「同行 → 我 · 小林」：纯文本右值，**不铺头像圆**。
                Text(
                    row.names.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = TypeScale.bodyRead,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        is DetailRowModel.Media -> Column(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
            Text(row.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (row.paths.isEmpty()) {
                Text(
                    stringResource(R.string.life_detail_no_value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 设计稿 dtl_04「相册」：**等宽多格**（4 格 66.5×50、间隔 7），
                // 不是「1 大 + N 小」。格宽用 weight 自适应，缩略图高度锁 50。
                var mediaExpanded by remember(groupFieldKey) { mutableStateOf(false) }
                val visiblePaths = if (mediaExpanded) row.paths else row.paths.take(4)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    visiblePaths.chunked(4).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            chunk.forEach { path ->
                                Thumb(path, accent, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                            // 末行不满 4 格补透明占位，保持等宽网格
                            repeat(4 - chunk.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (row.paths.size > 4) {
                    ExpandToggle(shown = 4, total = row.paths.size, expanded = mediaExpanded) { mediaExpanded = !mediaExpanded }
                }
            }
        }

        is DetailRowModel.StackBar -> StackShareRow(row, accent)

        is DetailRowModel.Heat -> HeatGrid(row, accent)

        is DetailRowModel.Timeline -> Column(modifier = Modifier.fillMaxWidth()) {
            row.entries.forEachIndexed { i, e ->
                if (i > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(horizontal = Spacing.sm)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        listOfNotNull(e.day, e.weather, e.mood).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (e.photoCount > 0) {
                            pluralStringResource(R.plurals.life_detail_timeline_photos, e.photoCount, e.photoCount)
                        } else {
                            stringResource(R.string.life_detail_timeline_no_photo)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = TypeScale.bodyRead),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        is DetailRowModel.FocusSessions -> Column(modifier = Modifier.fillMaxWidth()) {
            row.entries.forEachIndexed { i, entry ->
                if (i > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(horizontal = Spacing.sm)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.life_detail_focus_minutes, entry.minutes) + " · " + entry.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        entry.time,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = TypeScale.bodyRead),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

        is DetailRowModel.WeekBars -> FocusWeekBars(row, accent)

        is DetailRowModel.MapMini -> RowShell(32.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.life_detail_route_points, row.pointCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 分类占比堆叠条（设计稿 dtl_02 / dtl_15「分类占比 · MULTI_SELECT + TAG 皮」）。
 *
 * 条 12 高、轨底圆角 6、分段直角；图例「名称 N%」按**真实计数**折算，
 * 不编数字 —— 计数全部由 [DetailAggregates.multiSelectCounts] 从真实记录聚合而来。
 */
@Composable
private fun StackShareRow(row: DetailRowModel.StackBar, accent: Color) {
    val total = row.segments.sumOf { it.count }.coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
        Text(
            row.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            row.segments.forEachIndexed { index, segment ->
                Box(
                    modifier = Modifier
                        .weight(segment.count.coerceAtLeast(1).toFloat() / total)
                        .fillMaxHeight()
                        .background(shareColor(index))
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        row.segments.forEachIndexed { index, segment ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(Spacing.xs).clip(CircleShape).background(shareColor(index)))
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    segment.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${(segment.count * 100.0 / total).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 占比段配色：走设计稿实测的四色表，**按段序取用**（超过 4 段循环）。 */
private fun shareColor(index: Int): Color = SharePalette[index % SharePalette.size]

@Composable
// internal（而非 private）是为了让 `DetailRowHeightSpecTest` 能**直接量**它 ——
// 实测它是 68dp，而规格（§14.12(5) bar）是 34dp，需要量进去定位，不能再靠猜。
internal fun TrackBarRow(
    row: DetailRowModel.Bar,
    accent: Color,
    onSetProgress: ((String, Double) -> Unit)?,
    onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)?,
    config: FieldConfig? = null
) {
    // 设计稿 dtl_04「已订 ¥1,800 / ¥4,000」：文字左、百分比右（同色 600），
    // 7dp 通栏进度条另起一行；整行高 34（文字 16 + 条 7 + 余量）。
    val stepper = row.stepper
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.value.isNotBlank()) {
                Spacer(Modifier.width(Spacing.xs))
                // **数值本身就是入口**：点它原地变成输入框（计划 / todo 类的做法）。
                // 不再另设「自定义」按钮 —— 那是给隐藏入口打的补丁，徒增噪声。
                InlineNumberValue(
                    spec = InlineValueSpec(
                        display = row.value,
                        current = stepper?.current ?: 0.0,
                        min = stepper?.min,
                        max = stepper?.max,
                        unit = config?.unit.orEmpty(),
                        enabled = stepper != null && onSetProgress != null
                    ),
                    accent = accent,
                    onCommit = { v -> stepper?.let { onSetProgress?.invoke(it.fieldKey, v) } }
                )
            }
            // 固定步长：把「进度只能看」变成「进度能推」（用户真机反馈）。
            if (stepper != null && onSetProgress != null) {
                Spacer(Modifier.width(Spacing.xs))
                ProgressStepper(step = stepper.step, accent = accent) { delta ->
                    // 增量交给 VM 在写入那刻基于库里最新值计算：见 LifeDetailViewModel.nudgeProgress
                    onNudgeProgress?.invoke(stepper.fieldKey, delta, stepper.min, stepper.max)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LifeTrackBar(fraction = row.fraction, color = accent, height = 7.dp)
    }
}

@Composable
private fun RingBarRow(row: DetailRowModel.Bar, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            row.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(Spacing.xs))
        LifeProgressForm(
            form = row.form,
            fraction = row.fraction,
            color = accent,
            ringSize = 110.dp,
            segments = row.segments
        ) {
            Text(
                row.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FocusWeekBars(row: DetailRowModel.WeekBars, accent: Color) {
    if (row.bars.isEmpty()) return
    val maxDuration = row.bars.maxOfOrNull { it.durationMs }?.coerceAtLeast(1L) ?: 1L
    val weekLabels = weekdayHeaderRes()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        row.bars.forEach { bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.height(48.dp).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val height = if (bar.durationMs <= 0L) {
                        6.dp
                    } else {
                        6.dp + 38.dp * (bar.durationMs.toFloat() / maxDuration.toFloat())
                    }
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(height)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (bar.durationMs > 0L) accent else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    stringResource(weekLabels[bar.dayOfWeek.value - 1]),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 单张媒体缩略。
 *
 * [modifier] 传尺寸：相册用 `weight(1f).fillMaxHeight()` 均分，
 * 其它单图入口传固定 `size(width, height)`。
 */
@Composable
private fun Thumb(path: String, accent: Color, modifier: Modifier) {
    // content://（相册选图）直接可用；文件路径才需要存在性检查
    val exists = remember(path) { path.startsWith("content:") || File(path).exists() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.10f))
    ) {
        if (exists) {
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 月热力网格（§14.12(6)）：格 13 / 间距 5 / rx3.5、**7 列（周一起始）**、最多 35 格。
 *
 * ⚠️ **两种语义不许混**：`BINARY`（打卡）同色深浅读「有没有」；`MOOD`（心情）每格取
 * **当天情绪色本身**读「是什么」。若把心情按打卡实现，它会退化成「有 / 无」的深浅格子。
 */
@Composable
private fun HeatGrid(row: DetailRowModel.Heat, accent: Color) {
    val emptySlot = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            weekdayHeaderRes().forEach { res ->
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val total = row.leadingBlanks + row.cells.size
        val weeks = (total + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(weeks) { w ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { c ->
                        val index = w * 7 + c - row.leadingBlanks
                        val cell = row.cells.getOrNull(index)
                        HeatCellBox(
                            cell = cell,
                            isMood = row.mode == HeatMode.MOOD,
                            accent = accent,
                            emptyColor = emptySlot
                        )
                    }
                }
            }
        }
        if (row.mode == HeatMode.BINARY) {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.life_habit_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(emptySlot))
                Spacer(Modifier.width(Spacing.xxs))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.life_habit_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 单元格：二元/未知保持色块；心情有记录 = 粉彩 wash 底 + emoji（MOODA 式点阵）。 */
@Composable
private fun RowScope.HeatCellBox(cell: HeatCell?, isMood: Boolean, accent: Color, emptyColor: Color) {
    val hasMood = isMood && cell?.moodKey != null
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    hasMood -> lerp(
                        MoodVisuals.colorOf(cell?.moodKey) ?: emptyColor,
                        MaterialTheme.colorScheme.surface,
                        0.82f
                    )
                    else -> heatColor(cell, if (isMood) HeatMode.MOOD else HeatMode.BINARY, accent, emptyColor)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (hasMood) {
            Text(
                MoodVisuals.emojiOf(cell?.moodKey),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 一格的取色：二元看「有没有」，心情看「是什么」。色表收口在 [MoodVisuals]。 */
private fun heatColor(cell: HeatCell?, mode: HeatMode, accent: Color, empty: Color): Color = when {
    cell == null || cell.count == 0 -> empty
    mode == HeatMode.BINARY -> accent
    else -> MoodVisuals.colorOf(cell.moodKey) ?: empty
}

/** 表头：**周一起始**（复用既有星期文案，不新造词）。 */
private fun weekdayHeaderRes(): List<Int> = listOf(
    R.string.date_weekday_short_mon,
    R.string.date_weekday_short_tue,
    R.string.date_weekday_short_wed,
    R.string.date_weekday_short_thu,
    R.string.date_weekday_short_fri,
    R.string.date_weekday_short_sat,
    R.string.date_weekday_short_sun
)

/**
 * ④ 时间与关联（§14.2 四段骨架的最后一段）：一行小字，**更新在前、创建在后**（dtl_01 口径）。
 *
 * 复用既有文案 `life_detail_time_footer`（「更新于 %1$s · 创建于 %2$s」），
 * 时间格式走 core 的 [DateUtils.formatDisplayDateTime]（单一真源）。
 * **不重复展示结构区里已有的日期字段**（那是「这条记录里的日期」，这里是「这条记录的记录时间」）。
 */
@Composable
fun TimeFooter(createdAt: Long, updatedAt: Long, relationCounts: RelationCounts = RelationCounts()) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Text(
            stringResource(
                R.string.life_detail_time_footer,
                DateUtils.formatDisplayDateTime(updatedAt),
                DateUtils.formatDisplayDateTime(createdAt)
            ),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.footer),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!relationCounts.isEmpty) {
            Spacer(Modifier.height(Spacing.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (relationCounts.bills > 0) {
                    Text(
                        stringResource(R.string.life_detail_relation_bills, relationCounts.bills),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.footer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (relationCounts.bills > 0 && relationCounts.notes > 0) {
                    Text(
                        " · ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.footer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (relationCounts.notes > 0) {
                    Text(
                        stringResource(R.string.life_detail_relation_notes, relationCounts.notes),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.footer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 打卡统计三联行：小标签 + 加粗数值，均分三列（与统计页指标卡同语言、更轻）。 */
@Composable
private fun StatTripleRow(stats: List<Pair<String, String>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        stats.forEach { (label, value) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
