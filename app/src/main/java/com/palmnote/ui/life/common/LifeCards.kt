package com.palmnote.ui.life.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.ui.theme.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.*

@Composable
fun PlanCard(tpl: LifeTemplate, previews: List<LifeItem>, iconColor: Color, onClick: () -> Unit) {
    val tplColor = try { Color(android.graphics.Color.parseColor(tpl.color)) } catch (_: Exception) { iconColor }
    val previewItem = previews.firstOrNull()
    val itemStatus = previewItem?.status ?: "ACTIVE"
    val isDone = itemStatus == "COMPLETED" || itemStatus == "ARCHIVED"
    val statusLabel = when (itemStatus) { "COMPLETED" -> "\u5DF2\u5B8C\u6210"; "ARCHIVED" -> "\u5DF2\u5F52\u6863"; else -> "\u8FDB\u884C\u4E2D" }
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
                    val fieldSummary = previewItem?.let { extractFieldSummary(it, tpl) } ?: previews.joinToString(" \u00B7 ") { it.title }
                    Text(fieldSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = previewItem?.let { calcProgress(it) } ?: 0f
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
    val tplColor = try { Color(android.graphics.Color.parseColor(tpl.color)) } catch (_: Exception) { iconColor }
    val nearestDay = previews.firstOrNull()?.let { calcDays(it) }
    val dayText = when {
        nearestDay == null -> "--"
        nearestDay > 0 -> "${nearestDay}"
        nearestDay == 0L -> "\u4ECA\u5929"
        nearestDay > -1000 -> "\u5DF2\u8FC7${-nearestDay}"
        else -> "\u5DF2\u8FC7999+"
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
                Text(if (dateStr.isNotEmpty()) dateStr else if (moreCount > 0) "${previews.firstOrNull()?.title ?: "\u70B9\u51FB\u67E5\u770B"}\uFF08\u8FD8\u6709${moreCount}\u4E2A\u4E8B\u4EF6\uFF09" else (previews.firstOrNull()?.title ?: "\u70B9\u51FB\u67E5\u770B"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(dayText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tplColor, maxLines = 1)
                Text("\u5929", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

private fun extractFieldSummary(item: LifeItem, tpl: LifeTemplate): String {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        val parts = mutableListOf<String>()
        obj.entries.forEach { (key, value) ->
            when (key) {
                "targetAmount", "target_amount" -> { val v = value.jsonPrimitive.content; parts.add("\u00A5${v}") }
                "currentAmount", "saved_amount" -> { val v = value.jsonPrimitive.content; parts.add("\u00A5${v}") }
                "deadline" -> { val v = value.jsonPrimitive.content.toLongOrNull(); if (v != null) { val d = LocalDate.ofEpochDay(v / 86400000L); parts.add("\u76EE\u6807 ${d.year}-${d.monthValue}") } }
                "totalPages", "total_pages" -> parts.add("${value.jsonPrimitive.content} \u9875")
                "currentPage", "current_page" -> parts.add("${value.jsonPrimitive.content} \u9875")
                "author" -> parts.add(value.jsonPrimitive.content)
                "budget" -> parts.add("\u9884\u7B97 \u00A5${value.jsonPrimitive.content}")
                "destination" -> parts.add(value.jsonPrimitive.content)
                "startDate", "start_date" -> { val v = value.jsonPrimitive.content.toLongOrNull(); if (v != null) { val d = LocalDate.ofEpochDay(v / 86400000L); parts.add(d.toString()) } }
                "content" -> { val v = value.jsonPrimitive.content; parts.add(v.take(30) + if (v.length > 30) "..." else "") }
                "currentStreak" -> { val v = value.jsonPrimitive.content; parts.add("\u8FDE\u7EED${v}\u5929") }
                "targetDays" -> { val v = value.jsonPrimitive.content; parts.add("\u76EE\u6807${v}\u5929") }
                "courseName" -> parts.add(value.jsonPrimitive.content)
                "completedLessons" -> { val v = value.jsonPrimitive.content; parts.add("\u5DF2\u5B8C\u6210${v}\u8282") }
                "totalLessons" -> { val v = value.jsonPrimitive.content; parts.add("\u5171${v}\u8282") }
                "price" -> { val v = value.jsonPrimitive.content; parts.add("\u00A5${v}") }
                "billingCycle" -> parts.add(value.jsonPrimitive.content)
                "store" -> parts.add(value.jsonPrimitive.content)
            }
        }
        if (parts.isEmpty()) item.title else parts.joinToString(" \u00B7 ")
    } catch (_: Exception) { item.title }
}

private fun extractDateSummary(item: LifeItem): String {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        val dateStr = (obj["targetDate"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: obj["target_date"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: obj["date"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return "")
        val d = LocalDate.ofEpochDay(dateStr / 86400000L)
        d.toString()
    } catch (_: Exception) { "" }
}

private fun calcProgress(item: LifeItem): Float {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
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
        if (tot > 0) (cur / tot).toFloat() else 0f
    } catch (_: Exception) { 0f }
}

private fun calcDays(item: LifeItem): Long? {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        val dateStr = (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (obj["targetDate"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: (obj["birthday_date"] as? JsonPrimitive)?.content?.toLongOrNull()
        if (dateStr != null) {
            val target = LocalDate.ofEpochDay(dateStr / 86400000L)
            ChronoUnit.DAYS.between(LocalDate.now(), target)
        } else null
    } catch (_: Exception) { null }
}
