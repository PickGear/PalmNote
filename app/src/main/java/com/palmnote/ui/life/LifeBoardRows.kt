package com.palmnote.ui.life

import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.util.DateUtils
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * 看板行的日期投影：首页逾期区 / 当日安排 / 月历热力与完整清单页共用的**唯一口径**。
 *
 * ## 「每年重复」先滚到下一次周年，再参与一切日期判定
 *
 * 卡片与详情页的「距某天」读数早已按 [nextYearlyOccurrence] 滚动（见 YearlyRepeat.kt），
 * 若逾期 / 日历 / 当日安排仍按**原始 `dueDate`**，就会自相矛盾：
 * 生日 1990-05-20（每年重复）的卡片说「还有 300 天」，红区却把它挂成逾期，
 * 「推迟到今天」还会把周年锚点改写掉。
 * 滚动后的日期恒为**今天或未来**，因此 repeatYearly 行自然退出逾期区 ——
 * 循环任务逾期不堆积、吸回下一个未来档期；周年也本就不存在"逾期"。
 */
internal fun LifeItemDao.LifeBoardItemRow.effectiveDueMillis(
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): Long? {
    val ts = dueDate ?: return null
    if (!repeatYearly) return ts
    val anchor = DateUtils.millisToLocalDate(ts)
    val lunar = parseLunarFlag(yearlyLunarRaw(fieldsData))
    return nextYearlyOccurrence(anchor, today, lunar)
        .atStartOfDay(zone).toInstant().toEpochMilli()
}

/** `fieldsData.lunar` 原始值（布尔字段的历史数据也可能是字符串/数字）；解析失败视为公历。 */
private fun yearlyLunarRaw(fieldsData: String): String? =
    (runCatching { CARD_JSON.parseToJsonElement(fieldsData).jsonObject }.getOrNull()
        ?.get("lunar") as? JsonPrimitive)?.contentOrNull

/**
 * 当日安排投影：**滚动后的到期日**落在选中日（本地时区）的行。
 *
 * 月历热力（LifeCalendarViewModel.calendarDayMap）用同一投影取日期，维持
 * 「日历格有底色/圆点 ⟺ 那天的列表里有条目」的不变量；重复行按滚动后的日期计，
 * 原始 `dueDate` 当天不再重复出现（同一天只算一次）。
 */
internal fun List<LifeItemDao.LifeBoardItemRow>.scheduledOn(
    date: LocalDate,
    today: LocalDate
): List<LifeItemDao.LifeBoardItemRow> {
    val zone = ZoneId.systemDefault()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return mapNotNull { row ->
        row.effectiveDueMillis(today, zone)?.let { row to it }
    }
        .filter { (row, due) -> row.status != "ARCHIVED" && due in start until end }
        .map { it.first }
}

/**
 * 逾期投影：滚动后的到期日**早于今天零点**、未完成、非归档、排除记录类（见 LifeCalendarViewModel 注释）。
 * 按到期日**升序**——最久逾期的排最前，才配得上「逾期置顶」的位置（此前继承 boardRows 的
 * updatedAt 排序，最久的反而可能被首页预览折叠掉）。
 */
internal fun List<LifeItemDao.LifeBoardItemRow>.overdueOn(
    today: LocalDate
): List<LifeItemDao.LifeBoardItemRow> {
    val zone = ZoneId.systemDefault()
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    return mapNotNull { row ->
        row.effectiveDueMillis(today, zone)?.let { row to it }
    }
        .filter { (row, due) ->
            due < todayStart &&
                row.status != "COMPLETED" &&
                row.status != "ARCHIVED" &&
                row.category != BuiltinTemplates.RECORD_CATEGORY &&
                // 起点型模板不进逾期：正数日（戒烟/坚持了 N 天）的日期是**起点**，
                // 落在过去是它的常态语义，判成"逾期"是错的（倒计时的目标日过了才算逾期）。
                row.icon !in START_ANCHOR_ICONS
        }
        .sortedBy { it.second }
        .map { it.first }
}

/** 起点型模板（日期=起点而非截止）：它们的日期落在过去是常态，不算逾期。分类详情页徽标同用此表。 */
internal val START_ANCHOR_ICONS = setOf("trending_up")
