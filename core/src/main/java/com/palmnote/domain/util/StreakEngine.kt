package com.palmnote.domain.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** STREAK 求值结果。 */
data class StreakResult(val current: Int, val longest: Int) {
    companion object {
        val EMPTY = StreakResult(0, 0)
    }
}

/**
 * 连续打卡引擎（总纲 §3.5 F 组：STREAK 是唯一需要跨记录聚合的派生字段，归 P4）。
 * 纯函数：日期集合 → 当前连续 / 历史最长。
 *
 * 「当前连续」口径：今天有记录从今天倒推；今天没有但昨天有，连续延续（今天还可补卡）；
 * 两者都没有 → 0。「最长连续」对全序列扫描。
 */
object StreakEngine {

    fun compute(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): StreakResult {
        if (days.isEmpty()) return StreakResult.EMPTY
        val sorted = days.sorted()

        // 历史最长
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == 1L) run + 1 else 1
            if (run > longest) longest = run
        }

        // 当前连续
        val anchor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return StreakResult(0, longest)
        }
        var current = 1
        var cursor = anchor
        while (cursor.minusDays(1) in days) {
            current++
            cursor = cursor.minusDays(1)
        }
        return StreakResult(current, longest)
    }

    /** 'yyyy-MM-dd' 字符串集合（FieldValueDao.activeDays 直出）→ 引擎输入。 */
    fun compute(dayStrings: List<String>, today: LocalDate = LocalDate.now()): StreakResult =
        compute(dayStrings.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(), today)
}
