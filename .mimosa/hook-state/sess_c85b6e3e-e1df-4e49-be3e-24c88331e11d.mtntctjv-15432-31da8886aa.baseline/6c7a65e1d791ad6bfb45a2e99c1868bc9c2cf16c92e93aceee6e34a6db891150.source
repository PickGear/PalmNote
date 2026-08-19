package com.palmnote.ui.life.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.toComposeColor
import com.palmnote.ui.theme.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

@Composable
fun PlanCard(tpl: LifeTemplate, previews: List<LifeItem>, iconColor: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    val tplColor = tpl.color.toComposeColor(iconColor)
    val previewItem = previews.firstOrNull()
    val itemStatus = previewItem?.status ?: "ACTIVE"
    val isDone = itemStatus == "COMPLETED" || itemStatus == "ARCHIVED"
    val statusLabel = when (itemStatus) { "COMPLETED" -> stringResource(R.string.life_card_status_completed); "ARCHIVED" -> stringResource(R.string.life_card_status_archived); else -> stringResource(R.string.life_card_status_ongoing) }
    val statusColor = when (itemStatus) { "COMPLETED" -> MaterialTheme.colorScheme.tertiary; "ARCHIVED" -> MaterialTheme.colorScheme.outline; else -> tplColor }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onClick).alpha(if (isDone) 0.6f else 1f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(3.dp).height(72.dp).align(Alignment.CenterStart).background(if (isDone) MaterialTheme.colorScheme.tertiary else tplColor, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp).alpha(if (isDone) 0.6f else 1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFromName(tpl.icon), null, tint = if (isDone) MaterialTheme.colorScheme.tertiary else tplColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tpl.displayName(), fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (previewItem != null) {
                        Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = statusColor, modifier = Modifier.background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                if (previews.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val fieldSummary = previewItem?.let { extractFieldSummary(context, it, tpl) } ?: previews.joinToString(" \u00B7 ") { it.title }
                    Text(fieldSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = previewItem?.let { calcProgress(it, tpl) } ?: 0f
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = progress.coerceIn(0f, 1f)).height(4.dp).background(tplColor, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun TimeCard(tpl: LifeTemplate, previews: List<LifeItem>, iconColor: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    val tplColor = tpl.color.toComposeColor(iconColor)
    val nearestDay = previews.firstOrNull()?.let { calcDays(it) }
    val dayText = when {
        nearestDay == null -> "--"
        nearestDay > 0 -> "${nearestDay}"
        nearestDay == 0L -> stringResource(R.string.life_card_today)
        nearestDay > -1000 -> "${stringResource(R.string.life_card_expired_prefix)}${-nearestDay}"
        else -> "${stringResource(R.string.life_card_expired_prefix)}${stringResource(R.string.life_card_expired_overflow)}"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFromName(tpl.icon), null, tint = tplColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tpl.displayName(), fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val moreCount = previews.size - 1
                val dateStr = previews.firstOrNull()?.let { extractDateSummary(it) } ?: ""
                Text(if (dateStr.isNotEmpty()) dateStr else if (moreCount > 0) "${previews.firstOrNull()?.title ?: stringResource(R.string.life_card_tap_to_view)}${stringResource(R.string.life_card_more_events, moreCount)}" else (previews.firstOrNull()?.title ?: stringResource(R.string.life_card_tap_to_view)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(dayText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tplColor, maxLines = 1)
                Text(stringResource(R.string.life_card_days_unit), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

private fun extractFieldSummary(context: android.content.Context, item: LifeItem, tpl: LifeTemplate): String {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        val configs = Json.decodeFromString<List<FieldConfig>>(tpl.fieldsConfig)
        val showable = configs.filter { it.showInCard }.sortedBy { it.sortOrder }
        val parts = mutableListOf<String>()
        showable.forEach { cfg ->
            val raw = (obj[cfg.key] as? JsonPrimitive)?.content
            if (raw.isNullOrBlank()) return@forEach
            parts.add(formatSummaryValue(context, cfg.type, raw, cfg.unit))
        }
        if (parts.isEmpty()) item.title else parts.joinToString(" \u00B7 ")
    } catch (_: Exception) { item.title }
}

@Suppress("CyclomaticComplexMethod")
private fun formatSummaryValue(context: android.content.Context, type: FieldType, raw: String, unit: String): String {
    return when (type) {
        FieldType.DATE, FieldType.DATETIME -> raw.toLongOrNull()?.let { millis ->
            val d = DateUtils.millisToLocalDate(millis)
            "${d.year}-${d.monthValue.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
        } ?: raw
        FieldType.CURRENCY -> "\u00A5$raw${unit}"
        FieldType.NUMBER -> "$raw${unit}"
        FieldType.PERCENT, FieldType.PERCENTAGE, FieldType.RATING, FieldType.SLIDER ->
            "$raw${if (type == FieldType.PERCENT || type == FieldType.PERCENTAGE) "%" else unit}"
        FieldType.BOOLEAN -> {
            val enabled = raw.toBooleanStrictOrNull() == true
            context.getString(if (enabled) R.string.field_enabled else R.string.field_disabled)
        }
        FieldType.TIME -> raw
        FieldType.DURATION -> "$raw${unit}"
        else -> raw.take(30) + if (raw.length > 30) "..." else ""
    }
}

private fun extractDateSummary(item: LifeItem): String {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        val dateStr = (obj["targetDate"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: obj["target_date"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: obj["date"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return "")
        val d = DateUtils.millisToLocalDate(dateStr)
        d.toString()
    } catch (_: Exception) { "" }
}

private fun calcProgress(item: LifeItem, tpl: LifeTemplate): Float {
    if (item.fieldsData.isBlank() || item.fieldsData == "{}") return 0f
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        val progressConfigs = Json.decodeFromString<List<FieldConfig>>(tpl.fieldsConfig)
            .filter { it.showAsProgress }.sortedBy { it.sortOrder }
        if (progressConfigs.isNotEmpty()) progressFromConfigs(obj, progressConfigs) else progressFromKnownKeys(obj)
    } catch (_: Exception) { 0f }
}

private fun progressFromConfigs(obj: JsonObject, configs: List<FieldConfig>): Float {
    val nums = configs.mapNotNull { c -> (obj[c.key] as? JsonPrimitive)?.content?.toDoubleOrNull() }
    return when {
        nums.size >= 2 -> (nums[0] / nums[1]).toFloat().coerceIn(0f, 1f)
        nums.size == 1 -> {
            val max = configs.firstOrNull { it.max != null && it.max!! > 0 }?.max ?: 0.0
            if (max > 0) (nums[0] / max).toFloat().coerceIn(0f, 1f) else 0f
        }
        else -> 0f
    }
}

private fun progressFromKnownKeys(obj: JsonObject): Float {
    val cur = (obj["currentAmount"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: obj["currentPage"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: obj["saved_amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: obj["current"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: 0.0)
    val tot = (obj["targetAmount"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: obj["totalPages"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: obj["target_amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: obj["total"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: 0.0)
    return if (tot > 0) (cur / tot).toFloat().coerceIn(0f, 1f) else 0f
}

private fun calcDays(item: LifeItem): Long? {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        val dateStr = (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (obj["targetDate"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (obj["birthday_date"] as? JsonPrimitive)?.content?.toLongOrNull()
        if (dateStr != null) {
            val target = DateUtils.millisToLocalDate(dateStr)
            ChronoUnit.DAYS.between(LocalDate.now(), target)
        } else null
    } catch (_: Exception) { null }
}

// ============================================================
// RichCard: 三变体 + 纯函数 tint/gradient（零新依赖）
// ============================================================

private val lifeCardShape = RoundedCornerShape(16.dp)

private fun lifeCardTint(color: Color, alpha: Float = 0.12f): Color = color.copy(alpha = alpha)

private fun lifeCardGradient(color: Color): List<Color> = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.05f))

@Suppress("LongMethod")
@Composable
private fun LifeProgressCard(
    tpl: LifeTemplate,
    item: LifeItem,
    iconColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val tplColor = tpl.color.toComposeColor(iconColor)
    val progress = calcProgress(item, tpl)
    val isDone = item.status == "COMPLETED" || item.status == "ARCHIVED"
    val accent = if (isDone) MaterialTheme.colorScheme.tertiary else tplColor
    val gradient = lifeCardGradient(accent)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(lifeCardShape)
            .clickable(onClick = onClick)
            .alpha(if (isDone) 0.6f else 1f),
        shape = lifeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.tertiaryContainer else tplColor.copy(alpha = 0.08f)
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(4.dp).height(96.dp).background(Brush.verticalGradient(gradient)))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFromName(tpl.icon), null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        item.title.ifBlank { tpl.displayName() },
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.size(34.dp)) {
                        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)))
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(30.dp).align(Alignment.Center),
                            color = accent,
                            trackColor = accent.copy(alpha = 0.15f),
                            strokeWidth = 3.dp
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                val summary = extractFieldSummary(context, item, tpl)
                Text(
                    summary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.5.dp)).background(accent.copy(alpha = 0.15f))) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Brush.horizontalGradient(gradient))
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun LifeDateCountCard(
    tpl: LifeTemplate,
    item: LifeItem,
    iconColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val tplColor = tpl.color.toComposeColor(iconColor)
    val days = calcDays(item)
    val isOverdue = days != null && days < 0
    val accent = if (isOverdue) MaterialTheme.colorScheme.error else tplColor
    val dayText = when {
        days == null -> "--"
        days > 0 -> days.toString()
        days == 0L -> "0"
        else -> (days * -1).toString()
    }
    val dateLabel = extractDateSummary(item)
    val relativeLabel = when {
        days == null -> context.getString(R.string.life_card_tap_to_view)
        days > 0 -> context.getString(R.string.life_card_days_left, days)
        days == 0L -> context.getString(R.string.life_card_today)
        else -> context.getString(R.string.life_card_expired_days, -days)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(lifeCardShape).clickable(onClick = onClick),
        shape = lifeCardShape,
        colors = CardDefaults.cardColors(containerColor = lifeCardTint(accent))
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Brush.verticalGradient(lifeCardGradient(accent))), contentAlignment = Alignment.Center) {
                Text(dayText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFromName(tpl.icon), null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        item.title.ifBlank { tpl.displayName() },
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    relativeLabel,
                    fontSize = 11.sp,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dateLabel.isNotEmpty()) {
                    Text(
                        dateLabel,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isOverdue) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            }
        }
    }
}

@Composable
private fun LifeRecordCard(
    tpl: LifeTemplate,
    item: LifeItem,
    iconColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val tplColor = tpl.color.toComposeColor(iconColor)
    val relative = formatRelativeTime(context, item.updatedAt)
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(lifeCardShape).clickable(onClick = onClick),
        shape = lifeCardShape,
        colors = CardDefaults.cardColors(containerColor = lifeCardTint(tplColor, alpha = 0.08f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(lifeCardGradient(tplColor))), contentAlignment = Alignment.Center) {
                Icon(iconFromName(tpl.icon), null, tint = tplColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { tpl.displayName() },
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(relative, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RichCard(
    tpl: LifeTemplate,
    item: LifeItem,
    iconColor: Color,
    variant: String = "AUTO",
    onClick: () -> Unit
) {
    val resolved = if (variant != "AUTO") variant else when (tpl.category) {
        "\u65F6\u95F4" -> "DATECOUNT"
        "\u8BB0\u5F55" -> "RECORD"
        else -> "PROGRESS"
    }
    when (resolved) {
        "DATECOUNT" -> LifeDateCountCard(tpl, item, iconColor, onClick)
        "RECORD" -> LifeRecordCard(tpl, item, iconColor, onClick)
        else -> LifeProgressCard(tpl, item, iconColor, onClick)
    }
}
