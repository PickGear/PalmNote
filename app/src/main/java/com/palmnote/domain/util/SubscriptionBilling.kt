package com.palmnote.domain.util

import java.time.LocalDate

/**
 * 订阅的下次扣费日（纯函数，可单测）。口径与提醒链路一致：
 * - 扣费日是「几号」[billingDay]，短月自动钳到当月最后一天（31 号在 2 月落到 28/29）；
 * - [cycle] 决定跨几个月：monthly / quarterly / yearly，其余按 monthly；
 * - [lastBilledDate] 非空时，至少要超过「上次扣费日 + 一个周期」才算下一次，避免同一周期重复提醒；
 * - 结果恒为**今天或之后**；[billingDay] 不在 1..31 时返回 null。
 */
fun nextBillingDate(
    billingDay: Int,
    cycle: String,
    lastBilledDate: LocalDate?,
    today: LocalDate
): LocalDate? {
    if (billingDay !in 1..31) return null
    val months = when (cycle) {
        "yearly" -> 12L
        "quarterly" -> 3L
        else -> 1L
    }
    val earliest = lastBilledDate?.plusMonths(months)

    var candidate = billingDayIn(today, billingDay)
    if (candidate.isBefore(today)) candidate = billingDayIn(today.plusMonths(1), billingDay)
    // 上次已扣过：按周期往后推，直到晚于「上次 + 一个周期」
    while (earliest != null && candidate.isBefore(earliest)) {
        candidate = billingDayIn(candidate.plusMonths(months), billingDay)
    }
    return candidate
}

/** 某月的第 [day] 号；短月钳到最后一天。 */
private fun billingDayIn(month: LocalDate, day: Int): LocalDate =
    month.withDayOfMonth(day.coerceAtMost(month.lengthOfMonth()))
