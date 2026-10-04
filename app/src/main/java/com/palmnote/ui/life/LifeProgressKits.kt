package com.palmnote.ui.life

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.palmnote.domain.model.ProgressForm
import kotlin.math.cos
import kotlin.math.sin

/**
 * 分段条的每段填充比例。
 *
 * 已完成段为 1，当前段保留小数部分，后续段为 0；非法比例按 0 处理，比例本身钳制到 [0, 1]。
 */
internal fun segmentFillFractions(fraction: Float, segments: Int): List<Float> {
    if (segments <= 0) return emptyList()
    val clamped = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    val scaled = clamped * segments
    val fullSegments = scaled.toInt().coerceIn(0, segments)
    val partialFraction = (scaled - fullSegments).coerceIn(0f, 1f)
    return List(segments) { index ->
        when {
            index < fullSegments -> 1f
            index == fullSegments -> partialFraction
            else -> 0f
        }
    }
}

/** 分段形态的段数：目标落在 2..60 取目标（清单 4 项就是 4 段），否则回落 30（⑩ 的固定值）。 */
fun progressSegmentCount(target: Double?): Int = target?.toInt()?.takeIf { it in 2..60 } ?: 30

/** 只有分段形态才需要段数：横向族与圆环族其余形态一律 0（不画段）。 */
fun progressSegmentsFor(form: ProgressForm, target: Double?): Int {
    val segmented = form == ProgressForm.SEGMENTED_RING || form == ProgressForm.SEGMENTED_BAR
    return if (segmented) progressSegmentCount(target) else 0
}

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
    // 入场填充动画（独立开发者 App 的"活感"签名）：进度从 0 长到目标值
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "trackFill"
    )
    val f = animated
    val track = color.copy(alpha = 0.15f)
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        if (segments > 1) {
            // ⑩ 分段齿环的横向兄弟：段标记条（学习计划 hero）。
            val gap = size.height * 0.35f
            val segW = (size.width - gap * (segments - 1)) / segments
            val fillFractions = segmentFillFractions(f, segments)
            repeat(segments) { i ->
                drawRoundRect(
                    color = track,
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW.coerceAtLeast(0f), size.height),
                    cornerRadius = radius
                )
                val fill = fillFractions[i]
                if (fill > 0f) {
                    val filledWidth = segW * fill
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * (segW + gap), 0f),
                        size = Size(filledWidth.coerceAtLeast(0f), size.height),
                        cornerRadius = CornerRadius(
                            radius.x.coerceAtMost(filledWidth / 2f),
                            radius.y
                        )
                    )
                }
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

/** 圆环族判定：详情结构行与英雄区共用。 */
fun isRingProgressForm(form: ProgressForm): Boolean = when (form) {
    ProgressForm.THICK_RING,
    ProgressForm.THIN_RING,
    ProgressForm.SEGMENTED_RING,
    ProgressForm.BEADED_RING -> true
    ProgressForm.THICK_CAPSULE,
    ProgressForm.THIN_TRACK,
    ProgressForm.SEGMENTED_BAR -> false
}

/** 圆环粗细默认值；调用方仍可显式覆盖。 */
fun ringStrokeWidthFor(form: ProgressForm): Dp = when (form) {
    ProgressForm.THICK_RING -> 26.dp
    ProgressForm.THIN_RING -> 7.dp
    ProgressForm.SEGMENTED_RING -> 14.dp
    ProgressForm.BEADED_RING -> 22.dp
    ProgressForm.THICK_CAPSULE,
    ProgressForm.THIN_TRACK,
    ProgressForm.SEGMENTED_BAR -> 8.dp
}

/**
 * 圆环族环径（§9.3 / §3.5.1 尺寸表）：厚环 / 珠环 170dp、分段齿环 166dp、细环 150dp。
 * 横向族不画环，回落 110dp（环心数字可读下限，仅防御）。
 */
fun ringSizeFor(form: ProgressForm): Dp = when (form) {
    ProgressForm.THICK_RING, ProgressForm.BEADED_RING -> 170.dp
    ProgressForm.SEGMENTED_RING -> 166.dp
    ProgressForm.THIN_RING -> 150.dp
    ProgressForm.THICK_CAPSULE, ProgressForm.THIN_TRACK, ProgressForm.SEGMENTED_BAR -> 110.dp
}

/**
 * 进度形态统一入口：横向族走条，圆环族走环。
 *
 * [content] 只在圆环族里居中叠加（值 / 单位等）；横向族不占用该插槽。
 */
@Composable
fun LifeProgressForm(
    form: ProgressForm,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 0,
    barHeight: Dp = 8.dp,
    ringSize: Dp = ringSizeFor(form),
    ringStrokeWidth: Dp = ringStrokeWidthFor(form),
    content: @Composable BoxScope.() -> Unit = {}
) {
    if (isRingProgressForm(form)) {
        Box(
            modifier = modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            LifeRing(
                fraction = fraction,
                color = color,
                size = ringSize,
                strokeWidth = ringStrokeWidth,
                form = form,
                segments = segments
            )
            content()
        }
    } else {
        LifeTrackBar(
            fraction = fraction,
            color = color,
            modifier = modifier,
            height = barHeight,
            segments = segments
        )
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
    modifier: Modifier = Modifier,
    form: ProgressForm = ProgressForm.THICK_RING,
    segments: Int = 0
) {
    // 入场动画 + 同色系微渐变弧（提亮 ≤25%，仍在身份色族内；轨道两色纪律不变）
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "ringFill"
    )
    val f = animated
    val arcBrush = Brush.sweepGradient(listOf(color, lerp(color, Color.White, 0.25f), color))
    val track = color.copy(alpha = 0.15f)
    Canvas(modifier = modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val geo = ringGeometry(size.toPx(), strokePx, this.size)
        if (form == ProgressForm.SEGMENTED_RING) {
            drawSegmentedRing(geo, f, track, color, strokePx, segments)
        } else {
            drawContinuousRing(geo, f, track, arcBrush, strokePx)
            if (form == ProgressForm.BEADED_RING && f > 0f) {
                drawRingBead(geo, f, color)
            }
        }
    }
}

/** 环的几何：外接画布内缩半个环宽，避免圆头端帽被裁。 */
private class RingGeometry(val topLeft: Offset, val arcSize: Size) {
    val radius: Float get() = arcSize.width / 2f
}

private fun ringGeometry(diameter: Float, strokePx: Float, canvas: Size): RingGeometry {
    val inset = strokePx / 2f
    return RingGeometry(
        topLeft = Offset((canvas.width - diameter) / 2f + inset, inset),
        arcSize = Size(diameter - strokePx, diameter - strokePx)
    )
}

/** 连续环（⑥⑨）：整圈轨道 + 从 12 点顺时针增长的完成弧。 */
private fun DrawScope.drawContinuousRing(
    geo: RingGeometry,
    fraction: Float,
    track: Color,
    arcBrush: Brush,
    strokePx: Float
) {
    drawArc(track, -90f, 360f, false, geo.topLeft, geo.arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
    if (fraction > 0f) {
        drawArc(arcBrush, -90f, 360f * fraction, false, geo.topLeft, geo.arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
    }
}

/** 分段齿环（⑩）：等分槽位、逐段填充，段间留白。 */
private fun DrawScope.drawSegmentedRing(
    geo: RingGeometry,
    fraction: Float,
    track: Color,
    color: Color,
    strokePx: Float,
    segments: Int
) {
    val count = segments.takeIf { it in 2..60 } ?: 30
    val slot = 360f / count
    val gap = minOf(3.8f, slot * 0.35f)
    val sweep = slot - gap
    val fills = segmentFillFractions(fraction, count)
    repeat(count) { index ->
        val start = -90f + index * slot
        drawArc(track, start, sweep, false, geo.topLeft, geo.arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        val fill = fills[index]
        if (fill > 0f) {
            drawArc(color, start, sweep * fill, false, geo.topLeft, geo.arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
    }
}

/** 环上珠子（⑪）：弧末端一颗白心珠。 */
private fun DrawScope.drawRingBead(geo: RingGeometry, fraction: Float, color: Color) {
    val radians = Math.toRadians((-90f + 360f * fraction).toDouble())
    val beadCenter = Offset(
        x = geo.topLeft.x + geo.radius + cos(radians).toFloat() * geo.radius,
        y = geo.topLeft.y + geo.radius + sin(radians).toFloat() * geo.radius
    )
    drawCircle(color = color.copy(alpha = 0.22f), radius = 16.dp.toPx(), center = beadCenter)
    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = beadCenter)
}
