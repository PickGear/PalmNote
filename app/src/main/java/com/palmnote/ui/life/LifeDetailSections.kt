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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.ui.theme.TypeScale
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.ListCardShape
import com.palmnote.ui.theme.RatingStar
import com.palmnote.ui.theme.Success
import java.io.File

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
        modifier = modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, ListCardShape),
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
                        m.value,
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
fun StructureGroupBlock(group: DetailGroup, accent: Color) {
    if (group.rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            groupTitle(group),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        DetailCard {
            group.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(horizontal = Spacing.sm)
                    )
                }
                DetailRowView(row, accent)
            }
        }
    }
}

/** 组标题：模板专属块用 `titleRes`，其余用字段名（照设计稿：每张卡带自己的字段名小标题）。 */
@Composable
private fun groupTitle(group: DetailGroup): String = when {
    group.titleRes != 0 -> stringResource(group.titleRes)
    else -> group.title
}

@Composable
private fun RowShell(height: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 只读开关（真值来自数据，**详情页不给改**，§14.12(8)：就地操作只允许三处）。
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

@Composable
fun DetailRowView(row: DetailRowModel, accent: Color) {
    when (row) {
        is DetailRowModel.Kv -> RowShell(32.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                row.value ?: DetailCtx.PLACEHOLDER,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 真实状态由数据决定；**详情页不给改**（§14.12(8)：就地操作只允许三处）
        is DetailRowModel.Toggle -> RowShell(32.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            ReadOnlySwitch(checked = row.checked, accent = accent)
        }

        is DetailRowModel.Link -> RowShell(32.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                row.value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(" ›", style = MaterialTheme.typography.labelSmall, color = accent)
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

        is DetailRowModel.Bar -> RowShell(34.dp) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Box(Modifier.width(120.dp)) { LifeTrackBar(fraction = row.fraction, color = accent, height = 7.dp) }
            Spacer(Modifier.width(Spacing.xs))
            Text(row.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
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
                row.rows.take(6).forEach { (text, done) ->
                    RowShell(34.dp) {
                        Box(
                            modifier = Modifier
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
            }
        }

        is DetailRowModel.TableRows -> Column(modifier = Modifier.fillMaxWidth()) {
            if (row.rows.isEmpty()) {
                RowShell(32.dp) {
                    Text(row.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.life_detail_no_value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 表头（列名）只作语义提示，不重复字段名
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.columns.forEachIndexed { i, col ->
                        Text(
                            col,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(if (i == 0) 1.4f else 1f)
                        )
                    }
                }
                row.rows.take(6).forEach { cells ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(Spacing.xl).padding(horizontal = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cells.forEachIndexed { i, cell ->
                            Text(
                                cell,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (i == 0) FontWeight.Normal else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(if (i == 0) 1.4f else 1f)
                            )
                        }
                    }
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
                Row {
                    row.values.take(3).forEachIndexed { i, v ->
                        if (i > 0) Spacer(Modifier.width(6.dp))
                        ReadOnlyChip(text = v, accent = accent)
                    }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    row.names.take(4).forEach { n ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(n.take(1), style = MaterialTheme.typography.labelSmall, color = accent)
                        }
                        Spacer(Modifier.width(Spacing.xxs))
                    }
                    Text(row.names.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
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
                // 相册规格（§14.12(5) img / §14.11 #4）：**1 大 + N 小**
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(row.paths.first(), accent, width = 96.dp, height = 72.dp)
                    row.paths.drop(1).take(3).forEach { path ->
                        Spacer(Modifier.width(6.dp))
                        Thumb(path, accent, width = 48.dp, height = 48.dp)
                    }
                }
            }
        }

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
                        e.day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        e.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    listOfNotNull(e.weather, e.mood).take(2).forEach {
                        Spacer(Modifier.width(6.dp))
                        ReadOnlyChip(text = it, accent = accent)
                    }
                }
            }
        }

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

/** 单张媒体缩略（相册 1 大 + N 小共用）。 */
@Composable
private fun Thumb(path: String, accent: Color, width: Dp, height: Dp) {
    // 磁盘 IO 只在路径变化时做一次，不跟重组走
    val exists = remember(path) { File(path).exists() }
    Box(
        modifier = Modifier
            .size(width = width, height = height)
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(heatColor(cell, row.mode, accent, emptySlot))
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
fun TimeFooter(createdAt: Long, updatedAt: Long) {
    Text(
        stringResource(
            R.string.life_detail_time_footer,
            DateUtils.formatDisplayDateTime(updatedAt),
            DateUtils.formatDisplayDateTime(createdAt)
        ),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.footer),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
    )
}
