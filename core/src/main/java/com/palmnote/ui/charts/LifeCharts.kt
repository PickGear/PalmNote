package com.palmnote.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * 通用统计图表组件（总纲 P4：抽到 core，生活统计页与详情页结构区共用）。
 * 全部纯 Canvas 零依赖；颜色由调用方传入（身份色 / 状态色），文字走 MaterialTheme。
 */

/** 折线图（含渐变填充与端点）。points 为空 / 单点时不绘制。 */
@Composable
fun LineChart(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 96
) {
    val fill = color.copy(alpha = 0.14f)
    Canvas(modifier = modifier.fillMaxWidth().height(height.dp)) {
        if (points.isEmpty()) return@Canvas
        val minV = (points.minOrNull() ?: 0f)
        val maxV = points.maxOrNull() ?: 0f
        val range = (maxV - minV).takeIf { it > 0f } ?: 1f
        val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
        val yOf: (Float) -> Float = { v -> size.height - ((v - minV) / range) * (size.height * 0.82f) - size.height * 0.09f }
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = stepX * i
            val y = yOf(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fillPath = Path().apply {
            addPath(path)
            lineTo(stepX * (points.size - 1), size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fillPath, fill)
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        // 端点
        val lastX = stepX * (points.size - 1)
        val lastY = yOf(points.last())
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(lastX, lastY))
    }
}

/** 柱状图（等宽柱 + 最大值高亮；values 为 (标签, 值)，标签超长省略）。 */
@Composable
fun BarChart(
    values: List<Pair<String, Float>>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 96
) {
    if (values.isEmpty()) return
    val maxV = max(values.maxOf { it.second }, 0.0001f)
    val dim = color.copy(alpha = 0.28f)
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Canvas(modifier = modifier.fillMaxWidth().height(height.dp)) {
        val gap = 6.dp.toPx()
        val barW = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, (_, v) ->
            val x = i * (barW + gap)
            val h = (v / maxV).coerceIn(0.02f, 1f) * size.height
            // 背景轨
            drawRoundRect(track, topLeft = Offset(x, 0f), size = Size(barW, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRoundRect(if (v == values.maxOf { it.second }) color else dim, topLeft = Offset(x, size.height - h), size = Size(barW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEach { (label, _) ->
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

/** 环形统计图（百分比大数字居中；区别于进度环——这是统计占比）。 */
@Composable
fun RingChart(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 120,
    label: String? = null
) {
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        val track = color.copy(alpha = 0.14f)
        Canvas(modifier = Modifier.size(size.dp)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val side = size.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(side - stroke, side - stroke)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(inset, inset), size = arcSize)
            drawArc(color, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(inset, inset), size = arcSize)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fraction * 100).toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            if (label != null) Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 热力图（GitHub 风格月格）：days = 日期 → 强度值（0 = 空格）。
 * weeks 列 × 7 行，旧日期在左。level 0..4 五档透明度。
 */
@Composable
fun HeatmapChart(
    days: Map<String, Int>,
    color: Color,
    modifier: Modifier = Modifier,
    weeks: Int = 16,
    lastDate: java.time.LocalDate = java.time.LocalDate.now()
) {
    val empty = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val cell = 12.dp
    val gap = 3.dp
    Canvas(modifier = modifier.fillMaxWidth().height((7 * 12 + 6 * 3).dp)) {
        val cellPx = cell.toPx()
        val gapPx = gap.toPx()
        for (w in 0 until weeks) {
            for (d in 0 until 7) {
                // 列 w 从最旧往回数：末列 = 本周
                val daysAgo = (weeks - 1 - w) * 7 + (6 - d)
                val date = lastDate.minusDays(daysAgo.toLong())
                val level = days[date.toString()] ?: 0
                val c = when {
                    level <= 0 -> empty
                    level == 1 -> color.copy(alpha = 0.28f)
                    level == 2 -> color.copy(alpha = 0.5f)
                    level == 3 -> color.copy(alpha = 0.72f)
                    else -> color
                }
                drawRoundRect(
                    c,
                    topLeft = Offset(w * (cellPx + gapPx), d * (cellPx + gapPx)),
                    size = Size(cellPx, cellPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/** 分布图（水平堆叠条 + 图例）：items = (标签, 计数)，按序堆叠。 */
@Composable
fun DistributionChart(
    items: List<Pair<String, Int>>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val total = items.sumOf { it.second }.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            var x = 0f
            items.forEachIndexed { i, (_, cnt) ->
                val w = (cnt.toFloat() / total) * size.width
                drawRect(colors.getOrElse(i) { Color.Gray }, topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, (label, cnt) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(8.dp)) { drawCircle(colors.getOrElse(i) { Color.Gray }) }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$cnt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
