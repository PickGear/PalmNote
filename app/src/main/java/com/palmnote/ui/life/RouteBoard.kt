package com.palmnote.ui.life

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.MapModel
import com.palmnote.domain.model.MapPoint
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.BigCardShape

/**
 * 路线板（旅行计划 ① 英雄区，§14.4 / §14.12(3) `route` 形态）。
 *
 * ⚠️ **合规红线（§14.5 / §14.7(7) / §14.12(9)，非风格偏好）**：
 * 板内**只允许**「网格底纹 + 折线 + 节点 + 名称」四类；**禁止**海岸线、省界、河流、
 * 地形、经纬网及任何行政区划要素。所以本组件**不引入任何地图 SDK / 瓦片 / 矢量底图**——
 * 它看起来像地图（点按真实经纬度摆相对位置），但**不画任何一条边界线**；
 * 板底**必须**常驻「示意图 · 非地理底图 · 不渲染任何版图要素」。
 *
 * 坐标系：**不做 GCJ-02 ↔ WGS-84 纠偏**（§14.5 判据）——无底图叠加时偏移不可见，
 * 「不纠偏」同时也是合规上更干净的写法。
 */
@Composable
fun RouteBoard(
    model: MapModel,
    accent: Color,
    chip: String,
    modifier: Modifier = Modifier
) {
    // 颜色在组合期取出（DrawScope 不是 @Composable 上下文，不能在里面读 MaterialTheme）
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceTint = MaterialTheme.colorScheme.surfaceVariant
    val nameColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val noteColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val route = model.route
    val track = model.track?.pts.orEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 高度按稿等比（dtl_04：280×76 ⟹ 内容宽约 284dp 时高约 90dp）
            .height(90.dp)
            .clip(BigCardShape)
            .background(surfaceTint.copy(alpha = 0.5f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 22.dp)) {
            drawGrid(gridColor)
            val placed = normalize(route)
            drawRoute(placed, model.connect, accent)
            drawTrack(track, accent)
            drawLabels(placed, nameColor, noteColor)
        }
        if (chip.isNotBlank()) {
            HeroChip(
                text = chip,
                accent = accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Spacing.xs, end = Spacing.xs)
            )
        }
        Text(
            stringResource(R.string.life_route_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp)
        )
    }
}

/** 网格底纹：16dp 间距、@0.25（板内允许的四类内容之一）。 */
private fun DrawScope.drawGrid(grid: Color) {
    val step = 16.dp.toPx()
    val line = grid.copy(alpha = 0.25f)
    var x = 0f
    while (x <= size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/** 折线：sw2 / @0.55；节点首末 r6、中途 r4.5 + 外光晕。 */
private fun DrawScope.drawRoute(placed: List<Placed>, connect: Boolean, accent: Color) {
    if (connect && placed.size >= 2) {
        for (i in 0 until placed.size - 1) {
            drawLine(
                color = accent.copy(alpha = 0.55f),
                start = placed[i].at(size),
                end = placed[i + 1].at(size),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
    placed.forEachIndexed { i, p ->
        val o = p.at(size)
        val end = i == 0 || i == placed.lastIndex
        val r = if (end) 6.dp.toPx() else 4.5.dp.toPx()
        if (end) drawCircle(accent.copy(alpha = 0.18f), r + 3.5.dp.toPx(), o)
        drawCircle(accent, r, o)
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.4f, o)
    }
}

/** 轨迹（可选）：虚线，与 route 折线区分——**route 与 track 是两件事**（§14.7）。 */
private fun DrawScope.drawTrack(track: List<com.palmnote.domain.model.MapTrackPoint>, accent: Color) {
    if (track.size < 2) return
    val pts = normalizeTrack(track.map { it.lat to it.lng })
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    for (i in 0 until pts.size - 1) {
        drawLine(
            color = accent.copy(alpha = 0.35f),
            start = Offset(pts[i].first * size.width, pts[i].second * size.height),
            end = Offset(pts[i + 1].first * size.width, pts[i + 1].second * size.height),
            strokeWidth = 1.5f.dp.toPx(),
            pathEffect = dash
        )
    }
}

/** 节点名称 + 一行备注（板上文字走原生 canvas：DrawScope 无法直接排文字）。 */
private fun DrawScope.drawLabels(placed: List<Placed>, nameColor: Int, noteColor: Int) {
    if (placed.isEmpty()) return
    val namePaint = NativePaint().apply {
        isAntiAlias = true
        textAlign = NativePaint.Align.CENTER
        textSize = 10.dp.toPx()
    }
    val notePaint = NativePaint().apply {
        isAntiAlias = true
        textAlign = NativePaint.Align.CENTER
        textSize = 8.5f.dp.toPx()
    }
    drawIntoCanvas { canvas ->
        placed.forEachIndexed { i, p ->
            val o = p.at(size)
            val above = i % 2 == 0
            val dy = if (above) -8.dp.toPx() else 15.dp.toPx()
            val name = p.point.name
            if (name.isBlank()) return@forEachIndexed
            val half = namePaint.measureText(name) / 2f
            val cx = o.x.coerceIn(half + 2f, (size.width - half - 2f).coerceAtLeast(half + 2f))
            namePaint.color = nameColor
            canvas.nativeCanvas.drawText(name, cx, o.y + dy, namePaint)
            val note = p.point.note
            if (note.isNotBlank()) {
                notePaint.color = noteColor
                canvas.nativeCanvas.drawText(note, cx, o.y + dy + 11.dp.toPx(), notePaint)
            }
        }
    }
}

/** 归一化后的落点（**只做相对摆放**，不掺任何地理要素）。 */
private data class Placed(val point: MapPoint, val x: Float, val y: Float) {
    fun at(size: androidx.compose.ui.geometry.Size) = Offset(x * size.width, y * size.height)
}

private fun normalize(route: List<MapPoint>): List<Placed> {
    val usable = route.filter { it.lat != null && it.lng != null }
    if (usable.isEmpty()) return emptyList()
    val lats = usable.map { it.lat ?: 0.0 }
    val lngs = usable.map { it.lng ?: 0.0 }
    val minLat = lats.min(); val maxLat = lats.max()
    val minLng = lngs.min(); val maxLng = lngs.max()
    val spanLat = (maxLat - minLat).takeIf { it > 1e-6 } ?: 1.0
    val spanLng = (maxLng - minLng).takeIf { it > 1e-6 } ?: 1.0
    return usable.map { p ->
        val nx = ((p.lng ?: 0.0) - minLng) / spanLng
        val ny = 1.0 - ((p.lat ?: 0.0) - minLat) / spanLat
        Placed(p, (0.08 + nx * 0.84).toFloat(), (0.12 + ny * 0.72).toFloat())
    }
}

private fun normalizeTrack(pts: List<Pair<Double, Double>>): List<Pair<Float, Float>> {
    if (pts.isEmpty()) return emptyList()
    val lats = pts.map { it.first }
    val lngs = pts.map { it.second }
    val minLat = lats.min(); val maxLat = lats.max()
    val minLng = lngs.min(); val maxLng = lngs.max()
    val spanLat = (maxLat - minLat).takeIf { it > 1e-6 } ?: 1.0
    val spanLng = (maxLng - minLng).takeIf { it > 1e-6 } ?: 1.0
    return pts.map { (lat, lng) ->
        val nx = (lng - minLng) / spanLng
        val ny = 1.0 - (lat - minLat) / spanLat
        (0.08 + nx * 0.84).toFloat() to (0.12 + ny * 0.72).toFloat()
    }
}
