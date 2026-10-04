package com.palmnote.domain.model

import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 轨迹导入结果：只有抽稀后的点会被持久化，原始文件内容不入库。 */
data class ImportedTrack(
    val points: List<MapTrackPoint>,
    val stats: MapTrack
)

sealed interface TrackImportResult {
    data class Success(val track: ImportedTrack) : TrackImportResult
    data class Failure(val reason: TrackImportFailure) : TrackImportResult
}

enum class TrackImportFailure {
    EMPTY,
    UNSUPPORTED_KML_OVERLAY,
    PARSE_ERROR,
    INVALID_POINTS
}

private const val MAX_TRACK_POINTS = 300
private const val MIN_POINT_DISTANCE_M = 10.0
private const val MAX_JUMP_DISTANCE_M = 5_000.0
private const val ELEVATION_GAP_RESET_M = 20.0

/**
 * 解析 GPX / KML 轨迹。
 *
 * 导入阶段先按 10m 距离降采样，再用 Douglas-Peucker 压到 300 点以内；持久化的只有
 * [ImportedTrack.points]，因此详情页打开时不需要再次解析原始文件。
 */
fun importTrack(xml: String): TrackImportResult {
    val rootName = runCatching { rootElementName(xml) }.getOrNull()
    if (rootName == null) return TrackImportResult.Failure(TrackImportFailure.EMPTY)
    if (rootName.equals("kml", ignoreCase = true) && containsUnsupportedKmlContent(xml)) {
        return TrackImportResult.Failure(TrackImportFailure.UNSUPPORTED_KML_OVERLAY)
    }
    val parsed = runCatching {
        if (rootName.equals("gpx", ignoreCase = true)) parseGpx(xml) else parseKml(xml)
    }.getOrElse { return TrackImportResult.Failure(TrackImportFailure.PARSE_ERROR) }
    return if (parsed.size < 2) {
        TrackImportResult.Failure(TrackImportFailure.EMPTY)
    } else {
        TrackImportResult.Success(buildImportedTrack(parsed))
    }
}

private fun rootElementName(xml: String): String? {
    val start = xml.indexOf('<') ?: return null
    val end = xml.indexOf('>', start + 1)
    if (end < 0) return null
    val raw = xml.substring(start + 1, end).trim()
    if (raw.startsWith("?") || raw.startsWith("!")) {
        return rootElementName(xml.substring(end + 1))
    }
    return raw.substringBefore(' ').substringBefore('/').trim().ifBlank { null }
}

/** KML 红线：三个会引入外部图层/网络的节点一律拒绝。 */
private fun containsUnsupportedKmlContent(xml: String): Boolean {
    val lower = xml.lowercase()
    return lower.contains("<groundoverlay") ||
        lower.contains("<screenoverlay") ||
        lower.contains("<networklink")
}

private fun parseGpx(xml: String): List<MapTrackPoint> = tags(xml, "trkpt").mapNotNull { tag ->
    val lat = tag.attr("lat")?.toDoubleOrNull() ?: return@mapNotNull null
    val lng = tag.attr("lon")?.toDoubleOrNull() ?: return@mapNotNull null
    MapTrackPoint(lat, lng, tag.num("ele"), tag.textOrNull("time")?.let(::parseIsoTime))
}

private fun parseKml(xml: String): List<MapTrackPoint> {
    val points = mutableListOf<MapTrackPoint>()
    for (tag in tags(xml, "coordinates")) {
        tag.body.split(Regex("\\s+")).forEach { token ->
            val parts = token.split(',')
            val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: return@forEach
            val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
            points += MapTrackPoint(lat, lng, parts.getOrNull(2)?.toDoubleOrNull(), null)
        }
    }
    return points
}

private fun parseIsoTime(text: String): Long? = runCatching { Instant.parse(text).epochSecond }.getOrNull()

/** 轻量标签：足够 GPX/KML 这类简单文档，且不依赖 Android 自带 XMLPullParser。 */
private data class XmlTag(
    val name: String,
    val attrs: String,
    val body: String
) {
    fun attr(key: String): String? = Regex("""\b${Regex.escape(key)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(attrs)?.groupValues?.get(1)

    fun num(key: String): Double? = rawChild(key)?.toDoubleOrNull()

    fun textOrNull(key: String): String? = rawChild(key)

    private fun rawChild(key: String): String? = Regex("""<\s*$key(?:\s[^>]*)?>([^<]*)<\s*/\s*$key\s*>""", RegexOption.IGNORE_CASE)
        .find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/** 扫描指定名称的标签；只提取导入需要的 trkpt / coordinates。 */
private fun tags(xml: String, wanted: String): List<XmlTag> {
    val result = mutableListOf<XmlTag>()
    var index = 0
    var tag = nextTag(xml, wanted, index)
    while (tag != null) {
        result.add(tag.tag)
        index = tag.nextIndex
        tag = nextTag(xml, wanted, index)
    }
    return result
}

private fun nextTag(xml: String, wanted: String, start: Int): TagRead? {
    var index = start
    while (index < xml.length) {
        val open = xml.indexOf("<$wanted", index, ignoreCase = true)
        if (open < 0) return null
        val nameEnd = open + wanted.length + 1
        if (!isTagBoundary(xml.getOrNull(nameEnd))) {
            index = nameEnd
            continue
        }
        val close = xml.indexOf('>', nameEnd)
        return if (close < 0) null else readTag(xml, wanted, nameEnd, close)
    }
    return null
}

private fun readTag(xml: String, wanted: String, nameEnd: Int, close: Int): TagRead {
    val inner = xml.substring(nameEnd, close)
    val contentStart = close + 1
    val selfClosing = inner.trimEnd().endsWith("/")
    val content = inner.removeSuffix("/").trim()
    if (selfClosing) return TagRead(XmlTag(wanted, content.trimEnd('/').trim(), ""), contentStart)
    val endTag = Regex("""</\s*$wanted\s*>""", RegexOption.IGNORE_CASE).find(xml, contentStart)
    val bodyEnd = endTag?.range?.first ?: xml.length
    val nextIndex = endTag?.range?.last?.plus(1) ?: xml.length
    return TagRead(XmlTag(wanted, content, xml.substring(contentStart, bodyEnd)), nextIndex)
}

private data class TagRead(val tag: XmlTag, val nextIndex: Int)

private fun isTagBoundary(next: Char?): Boolean = next != null && (next == '>' || next == '/' || next.isWhitespace())

/** 测试入口：对已解析点执行与导入完全相同的清理、降采样、抽稀和统计。 */
fun buildImportedTrack(raw: List<MapTrackPoint>): ImportedTrack {
    val cleaned = removeJumps(raw)
    val sampled = downsampleByDistance(cleaned, MIN_POINT_DISTANCE_M)
    val simplified = if (sampled.size > MAX_TRACK_POINTS) {
        douglasPeucker(sampled, MAX_TRACK_POINTS)
    } else {
        sampled
    }
    val points = simplified.map { MapTrackPoint(it.lat, it.lng, it.ele, it.t) }
    val stats = trackStats(points)
    return ImportedTrack(points, stats)
}

private fun removeJumps(points: List<MapTrackPoint>): List<MapTrackPoint> {
    if (points.size < 2) return points
    val result = mutableListOf(points.first())
    var last = points.first()
    for (i in 1 until points.size) {
        val current = points[i]
        if (haversineM(last.lat, last.lng, current.lat, current.lng) > MAX_JUMP_DISTANCE_M) {
            continue
        }
        result += current
        last = current
    }
    return result
}

private fun downsampleByDistance(points: List<MapTrackPoint>, minDistanceM: Double): List<MapTrackPoint> {
    if (points.size < 2) return points
    val result = mutableListOf(points.first())
    var last = points.first()
    for (i in 1 until points.size - 1) {
        val current = points[i]
        if (haversineM(last.lat, last.lng, current.lat, current.lng) >= minDistanceM) {
            result += current
            last = current
        }
    }
    result += points.last()
    return result
}

/**
 * Douglas-Peucker：优先保留首末点和最大垂距点，直到点数进入预算。
 * 轨迹不需要像素级保真，300 点足够覆盖一屏和海拔剖面。
 */
internal fun douglasPeucker(points: List<MapTrackPoint>, maxPoints: Int): List<MapTrackPoint> {
    if (points.size <= maxPoints) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.lastIndex)
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        var maxDistance = -1.0
        var maxIndex = -1
        for (i in start + 1 until end) {
            val d = perpendicularDistanceM(points[i], points[start], points[end])
            if (d > maxDistance) {
                maxDistance = d
                maxIndex = i
            }
        }
        if (maxIndex > 0 && keep.count { it } < maxPoints) {
            keep[maxIndex] = true
            stack.addLast(start to maxIndex)
            stack.addLast(maxIndex to end)
        }
    }
    val kept = points.filterIndexed { index, _ -> keep[index] }
    return if (kept.size <= maxPoints) kept else kept.take(maxPoints - 1) + kept.last()
}

private fun perpendicularDistanceM(point: MapTrackPoint, start: MapTrackPoint, end: MapTrackPoint): Double {
    val x = point.lng
    val y = point.lat
    val x1 = start.lng
    val y1 = start.lat
    val x2 = end.lng
    val y2 = end.lat
    val dx = x2 - x1
    val dy = y2 - y1
    if (abs(dx) < 1e-12 && abs(dy) < 1e-12) return haversineM(y, x, y1, x1)
    val t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)
    val projX = x1 + t.coerceIn(0.0, 1.0) * dx
    val projY = y1 + t.coerceIn(0.0, 1.0) * dy
    return haversineM(y, x, projY, projX)
}

internal fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLng = (lng2 - lng1) * PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

internal fun trackStats(points: List<MapTrackPoint>): MapTrack {
    var dist = 0.0
    var ascent = 0.0
    var descent = 0.0
    var pendingAscent = 0.0
    var pendingDescent = 0.0
    for (i in 1 until points.size) {
        dist += haversineM(points[i - 1].lat, points[i - 1].lng, points[i].lat, points[i].lng)
        val delta = elevationDelta(points[i - 1], points[i]) ?: continue
        when {
            abs(delta) < ELEVATION_GAP_RESET_M && delta > 0 -> pendingAscent += delta
            abs(delta) < ELEVATION_GAP_RESET_M -> pendingDescent += -delta
            delta > 0 -> {
                ascent += pendingAscent + delta
                descent += pendingDescent
                pendingAscent = 0.0
                pendingDescent = 0.0
            }
            else -> {
                ascent += pendingAscent
                descent += pendingDescent + -delta
                pendingAscent = 0.0
                pendingDescent = 0.0
            }
        }
    }
    ascent += pendingAscent
    descent += pendingDescent
    val duration = trackDurationSec(points)
    return MapTrack(
        pts = points,
        distM = dist,
        ascentM = ascent,
        descentM = descent,
        durSec = duration
    )
}

private fun elevationDelta(previous: MapTrackPoint, current: MapTrackPoint): Double? {
    val from = previous.ele ?: return null
    val to = current.ele ?: return null
    return to - from
}

private fun trackDurationSec(points: List<MapTrackPoint>): Long {
    val times = points.mapNotNull { it.t }.sorted()
    return if (times.size >= 2) (times.last() - times.first()).coerceAtLeast(0L) else 0L
}
