package com.palmnote.ui.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.MapModel
import com.palmnote.domain.model.MapPoint
import com.palmnote.domain.model.MapTrackPoint
import com.palmnote.ui.theme.BigCardShape
import com.palmnote.ui.theme.Spacing
import java.util.Locale
import kotlin.math.abs
import android.graphics.Paint as NativePaint

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
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    // DrawScope 不是组合上下文：所有文案先在组合期解析，再作为参数传进去
    val legendTrack = stringResource(R.string.life_route_legend_track)
    val legendPlan = stringResource(R.string.life_route_legend_plan)
    val startLabel = stringResource(R.string.life_route_start)
    val endLabel = stringResource(R.string.life_route_end)
    val scaleKm = stringResource(R.string.life_route_scale_km)
    val scale500m = stringResource(R.string.life_route_scale_500m)

    val route = model.route
    val track = model.track?.pts.orEmpty()
    val trackStatsValue = model.track
    val altitudeCoverage = if (track.isEmpty()) 0f else track.count { it.ele != null }.toFloat() / track.size
    val showProfile = altitudeCoverage > 0.8f

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 设计稿 dtl_04 实测：英雄卡总高 150，其中路线板 76（上下留出 chip 与
            // disclaimer）；带海拔剖面时把剖面和统计行的高度一并加回。
            .height(if (showProfile) 186.dp else 150.dp)
            .clip(BigCardShape)
            .background(surfaceTint.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RouteCanvas(
                route = route,
                track = track,
                accent = accent,
                connect = model.connect,
                gridColor = gridColor,
                nameColor = nameColor,
                noteColor = noteColor,
                legendTrack = legendTrack,
                legendPlan = legendPlan,
                startLabel = startLabel,
                endLabel = endLabel,
                scaleKm = scaleKm,
                scale500m = scale500m,
                modifier = Modifier.weight(1f)
            )
            trackStatsValue?.let {
                RouteStatsLine(
                    track = it,
                    mutedColor = mutedColor,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
            if (showProfile) {
                RouteAltitudeProfile(track = track, accent = accent)
            }
            RouteDisclaimer(mutedColor)
        }
        if (chip.isNotBlank()) {
            HeroChip(
                text = chip,
                accent = accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Spacing.xs, end = Spacing.xs)
            )
        }
    }
}

@Composable
private fun RouteCanvas(
    route: List<MapPoint>,
    track: List<MapTrackPoint>,
    accent: Color,
    connect: Boolean,
    gridColor: Color,
    nameColor: Int,
    noteColor: Int,
    legendTrack: String,
    legendPlan: String,
    startLabel: String,
    endLabel: String,
    scaleKm: String,
    scale500m: String,
    modifier: Modifier = Modifier
) {
    // 全 Canvas 内容对读屏不可见（审计 #27）：补一条路线概要语义
    val boardDesc = stringResource(R.string.life_route_board_desc, route.size)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 2.dp)
            .semantics { contentDescription = boardDesc }
    ) {
        drawGrid(gridColor)
        val placed = normalize(route)
        if (track.size >= 2) drawTrack(track, accent)
        if (placed.size >= 2) drawRoute(placed, connect, accent)
        drawCompass(accent)
        // 图例：实测轨迹 / 计划路线 各自出现时才画（不出现就不占位）
        drawLegend(
            accent,
            hasTrack = track.size >= 2,
            hasRoute = placed.size >= 2,
            trackLabel = legendTrack,
            planLabel = legendPlan
        )
        drawLabels(placed, nameColor, noteColor)
        drawTrackEndpoints(track, nameColor, startLabel, endLabel)
        drawScaleBar(accent, track, nameColor, scaleKm, scale500m)
    }
}

@Composable
private fun RouteAltitudeProfile(track: List<MapTrackPoint>, accent: Color) {
    AltitudeProfile(
        points = track.mapNotNull { it.ele },
        accent = accent,
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(horizontal = 10.dp)
    )
}

@Composable
private fun RouteDisclaimer(mutedColor: Color) {
    Text(
        stringResource(R.string.life_route_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = mutedColor,
        modifier = Modifier.padding(start = 10.dp, top = 1.dp, bottom = 6.dp)
    )
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

/** 计划路线：虚线 2/6、sw2 / @0.55；节点首末 r6、中途 r4.5 + 外光晕。 */
private fun DrawScope.drawRoute(placed: List<Placed>, connect: Boolean, accent: Color) {
    if (connect && placed.size >= 2) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f)
        for (i in 0 until placed.size - 1) {
            drawLine(
                color = accent.copy(alpha = 0.55f),
                start = placed[i].at(size),
                end = placed[i + 1].at(size),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dash
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

/** 实测轨迹：实线 sw3.2 / @0.9；起终点用光晕强调。 */
private fun DrawScope.drawTrack(track: List<MapTrackPoint>, accent: Color) {
    // Chaikin 平滑**仅作用于渲染**（M14 第 ④ 步：只影响画出来的线，不写回数据）。
    val pts = chaikin(chaikin(normalizeTrack(track.map { it.lat to it.lng }), closed = false), closed = false)
    for (i in 0 until pts.size - 1) {
        drawLine(
            color = accent.copy(alpha = 0.9f),
            start = Offset(pts[i].first * size.width, pts[i].second * size.height),
            end = Offset(pts[i + 1].first * size.width, pts[i + 1].second * size.height),
            strokeWidth = 3.2f.dp.toPx()
        )
    }
    if (pts.isNotEmpty()) {
        listOf(pts.first(), pts.last()).forEach { p ->
            val o = Offset(p.first * size.width, p.second * size.height)
            drawCircle(accent.copy(alpha = 0.16f), 8.dp.toPx(), o)
            drawCircle(accent, 4.dp.toPx(), o)
        }
    }
}

/** 指北针（板内允许的“名称/方向”标注，不引入任何地理底图）。 */
private fun DrawScope.drawCompass(accent: Color) {
    val x = size.width - 14.dp.toPx()
    val y = 14.dp.toPx()
    val top = Offset(x, y - 10.dp.toPx())
    val left = Offset(x - 4.dp.toPx(), y + 4.dp.toPx())
    val right = Offset(x + 4.dp.toPx(), y + 4.dp.toPx())
    drawLine(accent.copy(alpha = 0.75f), left, top, strokeWidth = 1.4f.dp.toPx())
    drawLine(accent.copy(alpha = 0.75f), right, top, strokeWidth = 1.4f.dp.toPx())
    drawLine(accent.copy(alpha = 0.75f), left, right, strokeWidth = 1.4f.dp.toPx())
    drawCircle(accent, 2.dp.toPx(), Offset(x, y - 1.dp.toPx()))
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

@Composable
private fun RouteStatsLine(
    track: com.palmnote.domain.model.MapTrack,
    mutedColor: Color,
    modifier: Modifier = Modifier
) {
    val duration = formatDuration(track.durSec)
    Text(
        listOfNotNull(
            "爬升 ${track.ascentM.toInt()} m",
            formatDistance(track.distM),
            duration.takeIf { it.isNotBlank() }
        ).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = mutedColor,
        modifier = modifier
    )
}

private fun formatDistance(meters: Double): String = if (meters >= 1000) {
    String.format(Locale.US, "%.2f km", meters / 1000.0)
} else {
    String.format(Locale.US, "%.0f m", meters)
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "$hours 小时 $minutes 分"
        else -> "$minutes 分钟"
    }
}

/** 海拔剖面：只在不低于 80% 的点带 ele 时出现；不标黄、不画底图。 */
@Composable
private fun AltitudeProfile(points: List<Double>, accent: Color, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val min = points.min()
    val max = points.max()
    Canvas(modifier = modifier) {
        val span = (max - min).takeIf { abs(it) > 1e-6 } ?: 1.0
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        var previous = Offset(0f, size.height * (1f - ((points[0] - min) / span).toFloat()))
        for (i in 1 until points.size) {
            val current = Offset(
                step * i,
                size.height * (1f - ((points[i] - min) / span).toFloat())
            )
            drawLine(accent.copy(alpha = 0.85f), previous, current, strokeWidth = 1.5f.dp.toPx())
            previous = current
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
    val minLat = lats.min()
    val maxLat = lats.max()
    val minLng = lngs.min()
    val maxLng = lngs.max()
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
    val minLat = lats.min()
    val maxLat = lats.max()
    val minLng = lngs.min()
    val maxLng = lngs.max()
    val spanLat = (maxLat - minLat).takeIf { it > 1e-6 } ?: 1.0
    val spanLng = (maxLng - minLng).takeIf { it > 1e-6 } ?: 1.0
    return pts.map { (lat, lng) ->
        val nx = (lng - minLng) / spanLng
        val ny = 1.0 - (lat - minLat) / spanLat
        (0.08 + nx * 0.84).toFloat() to (0.12 + ny * 0.72).toFloat()
    }
}

/**
 * Chaikin 角切平滑（**仅渲染**，M14 第 ④ 步「Chaikin 平滑（仅渲染，不存）」）。
 *
 * 每次迭代把每条线段按 1/4、3/4 处切成两点，折角被磨圆；
 * [closed] = false 时首末点原样保留，避免轨迹端点被推出画面。
 * 只在画线前调用，**不写回任何持久化数据**。
 */
private fun chaikin(pts: List<Pair<Float, Float>>, closed: Boolean): List<Pair<Float, Float>> {
    if (pts.size < 3) return pts
    val out = ArrayList<Pair<Float, Float>>(pts.size * 2)
    if (!closed) out.add(pts.first())
    val limit = if (closed) pts.size else pts.size - 1
    for (i in 0 until limit) {
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        out.add(a.first + (b.first - a.first) * 0.25f to a.second + (b.second - a.second) * 0.25f)
        out.add(a.first + (b.first - a.first) * 0.75f to a.second + (b.second - a.second) * 0.75f)
    }
    if (!closed) out.add(pts.last())
    return out
}

/**
 * 图例（设计稿 dtl_04 板底：实测轨迹 / 计划路线）——**只有对应图元真的画了才出现**，
 * 不做「图例列了但线上没有」的假标注。实线 sw3.2 对应轨迹，虚线 2/6 对应计划。
 */
private fun DrawScope.drawLegend(
    accent: Color,
    hasTrack: Boolean,
    hasRoute: Boolean,
    trackLabel: String,
    planLabel: String
) {
    if (!hasTrack && !hasRoute) return
    val textSize = 9.dp.toPx()
    val y = size.height - 12.dp.toPx()
    val paint = NativePaint().apply {
        isAntiAlias = true
        textAlign = NativePaint.Align.LEFT
        this.textSize = textSize
        color = accent.copy(alpha = 0.95f).toArgb()
    }
    var x = 12.dp.toPx()
    drawIntoCanvas { canvas ->
        if (hasTrack) {
            drawLine(
                accent.copy(alpha = 0.9f),
                Offset(x, y),
                Offset(x + 14.dp.toPx(), y),
                strokeWidth = 3.2f.dp.toPx()
            )
            x += 18.dp.toPx()
            canvas.nativeCanvas.drawText(trackLabel, x, y + textSize / 3f, paint)
            x += paint.measureText(trackLabel) + 12.dp.toPx()
        }
        if (hasRoute) {
            drawLine(
                accent.copy(alpha = 0.55f),
                Offset(x, y),
                Offset(x + 14.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f)
            )
            x += 18.dp.toPx()
            canvas.nativeCanvas.drawText(planLabel, x, y + textSize / 3f, paint)
        }
    }
}

/**
 * 实测轨迹的起终点文字（M16：左下「起点」、右下「终点」）。
 * 只在轨迹存在时画；名称与尺寸与板内其它标注一致。
 */
private fun DrawScope.drawTrackEndpoints(
    track: List<MapTrackPoint>,
    nameColor: Int,
    startLabel: String,
    endLabel: String
) {
    if (track.size < 2) return
    val pts = normalizeTrack(track.map { it.lat to it.lng })
    if (pts.isEmpty()) return
    val paint = NativePaint().apply {
        isAntiAlias = true
        textSize = 10.dp.toPx()
        color = nameColor
    }
    val first = pts.first()
    val last = pts.last()
    drawIntoCanvas { canvas ->
        // 起点靠左对齐、终点靠右对齐（照 M16 的两端贴边口径，避免压住折线）
        paint.textAlign = NativePaint.Align.LEFT
        canvas.nativeCanvas.drawText(
            startLabel,
            10.dp.toPx(),
            (first.second * size.height).coerceIn(12.dp.toPx(), size.height - 18.dp.toPx()),
            paint
        )
        paint.textAlign = NativePaint.Align.RIGHT
        canvas.nativeCanvas.drawText(
            endLabel,
            size.width - 10.dp.toPx(),
            (last.second * size.height).coerceIn(12.dp.toPx(), size.height - 18.dp.toPx()),
            paint
        )
    }
}

/**
 * 1 km 参考比例尺（M16「1 km（参考）」）——**由真实轨迹的经纬度跨度折算**，不拍脑袋定长度。
 *
 * 用等距圆柱近似把归一化坐标还原成米：横向 1° 经度 ≈ 111320·cos(lat) m、1° 纬度 ≈ 110540 m。
 * 板宽对应 [spanM] 米，则 1 km 的像素长 = 板宽 / spanM × 1000；
 * 超过板宽 45% 时改画 500 m（同一条尺子，不换语义）；轨迹不足两点时不画。
 */
private fun DrawScope.drawScaleBar(
    accent: Color,
    track: List<MapTrackPoint>,
    nameColor: Int,
    scaleKm: String,
    scale500m: String
) {
    if (track.size < 2) return
    val lats = track.map { it.lat }
    val lngs = track.map { it.lng }
    val spanLat = (lats.max() - lats.min()).coerceAtLeast(1e-6)
    val spanLng = (lngs.max() - lngs.min()).coerceAtLeast(1e-6)
    val midLat = (lats.max() + lats.min()) / 2.0
    val spanM = spanLng * 111_320.0 * kotlin.math.cos(Math.toRadians(midLat))
    if (spanM <= 0.0) return
    // 归一化时横向只占板宽的 0.84，故比例尺也要按同一系数折算
    val usableWidthPx = size.width * 0.84f
    val mPerPx = spanM / usableWidthPx
    val oneKmPx = (1000.0 / mPerPx).toFloat()
    val barPx = if (oneKmPx > size.width * 0.45f) (500.0 / mPerPx).toFloat() else oneKmPx
    if (barPx < 8.dp.toPx()) return
    val label = if (barPx == oneKmPx) scaleKm else scale500m
    val y = size.height - 12.dp.toPx()
    val x0 = size.width - 12.dp.toPx() - barPx
    drawLine(accent.copy(alpha = 0.8f), Offset(x0, y), Offset(x0 + barPx, y), strokeWidth = 1.4f.dp.toPx())
    drawLine(accent.copy(alpha = 0.8f), Offset(x0, y - 3.dp.toPx()), Offset(x0, y + 3.dp.toPx()), strokeWidth = 1.4f.dp.toPx())
    drawLine(
        accent.copy(alpha = 0.8f),
        Offset(x0 + barPx, y - 3.dp.toPx()),
        Offset(x0 + barPx, y + 3.dp.toPx()),
        strokeWidth = 1.4f.dp.toPx()
    )
    val paint = NativePaint().apply {
        isAntiAlias = true
        textAlign = NativePaint.Align.CENTER
        textSize = 9.dp.toPx()
        color = nameColor
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(label, x0 + barPx / 2f, y - 5.dp.toPx(), paint)
    }
}
