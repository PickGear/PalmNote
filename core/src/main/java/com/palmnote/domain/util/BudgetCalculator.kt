package com.palmnote.domain.util

import com.palmnote.data.db.dao.BudgetSpendRow
import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.model.BudgetScope

/**
 * 预算进度的纯计算：给定「预算模板 + 区间支出聚合行 + 当前时刻」，算出每条预算用掉多少。
 *
 * 刻意不碰数据库与 Flow——周/月/年窗口和三种口径的换算全在这里，
 * 用 JVM 单测就能钉住，不必起 Robolectric。
 */
object BudgetCalculator {

    /** 某周期在当前时刻的窗口（含首含尾，毫秒）。周与全 App 口径一致：周一起始。 */
    fun windowOf(period: BudgetPeriod, now: Long): Pair<Long, Long> = when (period) {
        BudgetPeriod.WEEK -> DateUtils.getWeekStartForDate(now) to DateUtils.getWeekEndForDate(now)
        BudgetPeriod.MONTH -> DateUtils.getMonthStartForDate(now) to DateUtils.getMonthEndForDate(now)
        BudgetPeriod.YEAR -> DateUtils.getYearStartForDate(now) to DateUtils.getYearEndForDate(now)
    }

    /**
     * 一条预算用掉多少（分）。
     *
     * - 总额：所有账本所有分类求和；
     * - 账本：该账本的支出；
     * - 分类：该分类的支出（跨账本）。
     */
    fun spentOf(budget: Budget, rows: List<BudgetSpendRow>): Long = when (budget.scopeEnum) {
        BudgetScope.TOTAL -> rows.sumOf { it.total }
        BudgetScope.BOOK -> rows.filter { it.accountBookId == budget.bookId }.sumOf { it.total }
        BudgetScope.CATEGORY -> rows.filter { it.category == budget.category }.sumOf { it.total }
    }

    /**
     * 把全部预算换算成进度列表。
     *
     * [rowsByPeriod] 是每个周期的支出聚合行（仓库层一次查好三种窗口，避免每条约一次 SQL）。
     * 结果按「严重程度降序、比例降序」排，调用方取第一条就是最该提醒的那条。
     */
    fun compute(
        budgets: List<Budget>,
        rowsByPeriod: Map<BudgetPeriod, List<BudgetSpendRow>>,
        now: Long
    ): List<BudgetProgress> = budgets.map { budget ->
        val period = budget.periodEnum
        val (start, end) = windowOf(period, now)
        BudgetProgress(
            budget = budget,
            spent = spentOf(budget, rowsByPeriod[period].orEmpty()),
            windowStart = start,
            windowEnd = end
        )
    }.sortedWith(compareByDescending<BudgetProgress> { it.severity }.thenByDescending { it.ratio })
}
