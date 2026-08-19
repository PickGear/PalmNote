package com.palmnote.ui.life.plan

import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.util.DateUtils
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

const val CHECKIN_KEY = "checkin_days"

enum class SubtaskKind { DAILY, WEEKLY, MONTHLY, MILESTONE }

fun LifeItem.subtaskKind(): SubtaskKind {
    val rec = recurring
    return when {
        rec == "WEEKDAY" || (rec != null && rec.startsWith("DAILY")) -> SubtaskKind.DAILY
        rec != null && rec.startsWith("WEEKLY") -> SubtaskKind.WEEKLY
        rec != null && rec.startsWith("MONTHLY") -> SubtaskKind.MONTHLY
        else -> SubtaskKind.MILESTONE
    }
}

fun LifeItem.anchorDate(): LocalDate? =
    dueDate?.let { DateUtils.millisToLocalDate(it) } ?: DateUtils.millisToLocalDate(createdAt)

fun checkinDaysOf(fieldsData: String): Set<LocalDate> {
    return try {
        val obj = Json.parseToJsonElement(fieldsData.ifBlank { "{}" }).jsonObject
        (obj[CHECKIN_KEY] as? JsonArray)?.mapNotNull { el ->
            (el as? JsonPrimitive)?.content?.let { s -> try { LocalDate.parse(s) } catch (_: Exception) { null } }
        }?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }
}

fun withCheckinDay(fieldsData: String, day: LocalDate, checked: Boolean): String {
    val obj = parseFields(fieldsData)
    val days = checkinDaysOf(fieldsData).toMutableSet()
    if (checked) days.add(day) else days.remove(day)
    return JsonObject(obj + (CHECKIN_KEY to toJsonArray(days.sorted()))).toString()
}

fun withAllDatesChecked(sub: LifeItem, planStart: LocalDate, planEnd: LocalDate?, today: LocalDate): String {
    val obj = parseFields(sub.fieldsData)
    val dates = sortedOccurrences(sub, planStart, planEnd, upper = today)
    return JsonObject(obj + (CHECKIN_KEY to toJsonArray(dates))).toString()
}

private fun parseFields(fieldsData: String): JsonObject =
    try {
        Json.parseToJsonElement(fieldsData.ifBlank { "{}" }).jsonObject
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }

private fun toJsonArray(days: List<LocalDate>): JsonArray =
    JsonArray(days.map { JsonPrimitive(it.toString()) })

/** 判断某天是否是某子任务的计划日（在计划起止内、且满足该子任务的发生规则）。 */
fun occursOn(sub: LifeItem, day: LocalDate, planStart: LocalDate, planEnd: LocalDate?): Boolean {
    if (day.isBefore(planStart)) return false
    if (planEnd != null && day.isAfter(planEnd)) return false
    val anchor = sub.anchorDate() ?: planStart
    return when (sub.subtaskKind()) {
        SubtaskKind.DAILY -> !day.isBefore(anchor)
        SubtaskKind.WEEKLY -> day.dayOfWeek == anchor.dayOfWeek
        SubtaskKind.MONTHLY -> day.dayOfMonth == anchor.dayOfMonth
        SubtaskKind.MILESTONE -> anchor == day
    }
}

fun isCheckedOn(sub: LifeItem, day: LocalDate): Boolean = when (sub.subtaskKind()) {
    SubtaskKind.MILESTONE -> sub.status == "COMPLETED" || checkinDaysOf(sub.fieldsData).contains(day)
    else -> checkinDaysOf(sub.fieldsData).contains(day)
}

/** 从 anchor/planStart 到 min(upper, planEnd) 的所有计划日。 */
fun sortedOccurrences(sub: LifeItem, planStart: LocalDate, planEnd: LocalDate?, upper: LocalDate): List<LocalDate> {
    val end = if (planEnd != null && planEnd.isBefore(upper)) planEnd else upper
    if (end.isBefore(planStart)) return emptyList()
    val out = ArrayList<LocalDate>()
    var day = planStart
    while (!day.isAfter(end)) {
        if (occursOn(sub, day, planStart, planEnd)) out.add(day)
        day = day.plusDays(1)
    }
    return out
}

/** 子任务在当前状态（至 today 或 planEnd）下是否已达成。 */
fun isSubtaskDone(sub: LifeItem, planStart: LocalDate, planEnd: LocalDate?, today: LocalDate): Boolean {
    if (sub.subtaskKind() == SubtaskKind.MILESTONE) {
        val d = sub.dueDate?.let { DateUtils.millisToLocalDate(it) }
        return d != null && !d.isBefore(planStart) && (planEnd == null || !d.isAfter(planEnd)) &&
            isCheckedOn(sub, d)
    }
    val occ = sortedOccurrences(sub, planStart, planEnd, upper = today)
    return occ.isNotEmpty() && occ.all { isCheckedOn(sub, it) }
}

fun planDoneCount(subtasks: List<LifeItem>, planStart: LocalDate, planEnd: LocalDate?, today: LocalDate): Int =
    subtasks.count { isSubtaskDone(it, planStart, planEnd, today) }

fun planProgress(subtasks: List<LifeItem>, planStart: LocalDate, planEnd: LocalDate?, today: LocalDate): Float {
    if (subtasks.isEmpty()) return 0f
    return planDoneCount(subtasks, planStart, planEnd, today).toFloat() / subtasks.size
}

fun streakCount(subtasks: List<LifeItem>, planStart: LocalDate, planEnd: LocalDate?, today: LocalDate): Int {
    var day = today
    var streak = 0
    while (!day.isBefore(planStart) && streak < 10000) {
        val scheduled = subtasks.filter { occursOn(it, day, planStart, planEnd) }
        if (scheduled.isNotEmpty() && scheduled.any { !isCheckedOn(it, day) }) break
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/** 3.9 剩余清单：返回 (标题, 剩余次数)。里程碑 count=1。 */
fun remainingTasks(subtasks: List<LifeItem>, planStart: LocalDate, planEnd: LocalDate?, today: LocalDate): List<Pair<String, Int>> {
    return subtasks.filterNot { isSubtaskDone(it, planStart, planEnd, today) }.map { sub ->
        if (sub.subtaskKind() == SubtaskKind.MILESTONE) {
            sub.title to 1
        } else {
            val miss = sortedOccurrences(sub, planStart, planEnd, upper = today).count { !isCheckedOn(sub, it) }
            sub.title to (if (miss > 0) miss else 1)
        }
    }
}
