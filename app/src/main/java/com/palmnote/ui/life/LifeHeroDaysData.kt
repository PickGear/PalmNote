package com.palmnote.ui.life

import com.nlf.calendar.Solar
import com.palmnote.app.R
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date

/** 注脚标签词用 @StringRes（0 = 无）：本地化在 Composable 成型，数据层只给资源 id。 */
internal data class LifeHeroDaysData(
    val days: Long?,
    val from: String,
    val to: String,
    val extra: String,
    val fromLabelRes: Int = 0,
    val toLabelRes: Int = 0
)

internal fun daysData(ctx: DetailCtx): LifeHeroDaysData {
    // 只有**倒计时**按目标日期算剩余天数，其余（含生日/纪念日/计数/周期）走共同的天数口径。
    // 这里原本是 icon 字面量比较，收敛到 kind 后用户改图标不会再改变天数算法。
    // 注意 "trending_up"/"build" 两个分支保留为 icon 判据：它们是**显示形态**而非行为身份，
    // 且未纳入 LifeTemplateKind（见 LifeTemplateKind 的 KDoc）。
    val kind = ctx.template.getKind()
    return when {
        kind == LifeTemplateKind.COUNTDOWN -> targetDateDays(ctx)
        ctx.template.icon == "trending_up" -> elapsedDays(ctx)
        ctx.template.icon == "build" -> cycleDays(ctx)
        else -> anniversaryDays(ctx)
    }
}

private fun targetDateDays(ctx: DetailCtx): LifeHeroDaysData {
    val target = ctx.date("targetDate")
    val days = target?.let { ChronoUnit.DAYS.between(ctx.today, it) }
        ?: ctx.cfg("remainDays")?.let { ctx.derived(it) }?.toLong()
    return LifeHeroDaysData(
        days,
        fmtDateShort(ctx.today, ctx.today).orEmpty(),
        fmtDateShort(target, ctx.today).orEmpty(),
        "",
        fromLabelRes = R.string.life_detail_word_today
    )
}

private fun elapsedDays(ctx: DetailCtx): LifeHeroDaysData {
    val start = ctx.date("start_date")
    val passed = start?.let { ChronoUnit.DAYS.between(it, ctx.today) }
    return LifeHeroDaysData(
        passed,
        fmtDateShort(start, ctx.today).orEmpty(),
        fmtDateShort(ctx.today, ctx.today).orEmpty(),
        "",
        fromLabelRes = R.string.life_detail_word_since,
        toLabelRes = R.string.life_detail_word_today
    )
}

private fun cycleDays(ctx: DetailCtx): LifeHeroDaysData {
    // 物品维护：距下次更换 =（更换日期 + 周期）− 今天；算不出就不给天数。
    val bought = ctx.date("boughtAt")
    val cycle = ctx.cfg("cycle")?.let { ctx.num(it.key) }
    val next = if (bought != null && cycle != null) bought.plusDays(cycle.toLong()) else null
    val days = next?.let { ChronoUnit.DAYS.between(ctx.today, it) }
    return LifeHeroDaysData(days, fmtDateShort(bought, ctx.today).orEmpty(), fmtDateShort(next, ctx.today).orEmpty(), "")
}

private fun anniversaryDays(ctx: DetailCtx): LifeHeroDaysData {
    // 生日 / 纪念日：**按年复现**，算到下一个周年（与仪表盘纪念日卡同一口径）。
    val raw = ctx.date("date")
    val next = raw?.let { anniversaryNext(it, ctx.today) }
    val isCelebration = ctx.template.getKind() == LifeTemplateKind.ANNIVERSARY
    return LifeHeroDaysData(
        next?.let { ChronoUnit.DAYS.between(ctx.today, it) },
        fmtDateShort(raw, ctx.today).orEmpty(),
        fmtDateShort(next, ctx.today).orEmpty(),
        "",
        fromLabelRes = if (isCelebration) R.string.life_detail_word_since else 0,
        toLabelRes = if (isCelebration) R.string.life_detail_word_anniversary else 0
    )
}

internal fun anniversaryNext(date: LocalDate, today: LocalDate): LocalDate {
    val thisYear = date.withYear(today.year)
    return if (thisYear >= today) thisYear else date.withYear(today.year + 1)
}

/** 生日的 `lunar` 是 BOOLEAN 字段，历史数据里也可能是字符串或数字。 */
internal fun parseLunarFlag(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
    "true", "1", "\"true\"", "\"1\"" -> true
    else -> false
}

/**
 * 公历日期 → 农历月日锚点（如「农历八月十六」）。
 *
 * 设计稿 D09 的语义是「按农历每年重复」：这里只锚定这条原始公历日期对应的农历月日，
 * 不把它换算成今年的下一次周年；右侧公历日期仍由 [daysData] 给出。
 */
internal fun lunarMonthDayOf(date: LocalDate): String? = runCatching {
    val solar = Solar.fromDate(Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
    val lunar = solar.lunar
    val rawMonth = lunar.month
    val leap = if (rawMonth < 0) "闰" else ""
    "农历$leap${lunarMonthName(kotlin.math.abs(rawMonth))}${lunarDayName(lunar.day)}"
}.getOrNull()

private fun lunarMonthName(month: Int): String = LunarMonthNames.getOrElse(month) { "${month}月" }

private fun lunarDayName(day: Int): String = when (day) {
    10 -> "初十"
    20 -> "二十"
    30 -> "三十"
    else -> LunarDayTens.getOrElse(day / 10) { "三" } + LunarDayOnes.getOrElse(day % 10) { "" }
}

private val LunarMonthNames = listOf(
    "", "正月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "冬月", "腊月"
)

private val LunarDayTens = listOf("初", "十", "廿")

private val LunarDayOnes = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
