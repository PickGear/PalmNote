package com.palmnote.ui.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 进度形态族里详情页用到的那几种（§3.5.1；本节**不新造形态**）。
 *
 * 三条硬数字照 §3.5.1 执行：**只用两色**（身份色 + 轨道色）、**圆头端帽**、
 * 比例与排印级差留给调用方（值 ∶ 标签 ≥ 3×）。像素值来自 §14.12(3)(4)。
 */

/** ④ 细轨 + 端锚点 / ⑩ 分段条的横向载体（§14.12：money3 用 高 8 / rx4，满宽）。 */
@Composable
fun LifeTrackBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    segments: Int = 0
) {
    val track = color.copy(alpha = 0.15f)
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        if (segments > 1) {
            // ⑩ 分段齿环的横向兄弟：段标记条（学习计划 hero）。
            val gap = size.height * 0.35f
            val segW = (size.width - gap * (segments - 1)) / segments
            val filled = (f * segments + 0.5f).toInt()
            repeat(segments) { i ->
                drawRoundRect(
                    color = if (i < filled) color else track,
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW.coerceAtLeast(0f), size.height),
                    cornerRadius = radius
                )
            }
        } else {
            drawRoundRect(color = track, size = size, cornerRadius = radius)
            if (f > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(size.width * f, size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}

/**
 * ⑥ 厚环 / ⑨ 细环巨字：媒介型（阅读封面）、计时器（专注）用。
 * 从 -90° 起、顺时针；空轨同色低透明 —— 与条同一套两色纪律。
 */
@Composable
fun LifeRing(
    fraction: Float,
    color: Color,
    size: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier
) {
    val f = fraction.coerceIn(0f, 1f)
    val track = color.copy(alpha = 0.15f)
    Canvas(modifier = modifier.size(size)) {
        val diameter = size.toPx()
        val sw = strokeWidth.toPx()
        val inset = sw / 2f
        val topLeft = Offset((this.size.width - diameter) / 2f + inset, inset)
        val arcSize = Size(diameter - sw, diameter - sw)
        drawArc(
            color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round)
        )
        if (f > 0f) {
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
    }
}
