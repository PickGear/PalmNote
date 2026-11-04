package com.palmnote.domain.model

import com.palmnote.data.db.entity.Budget
import kotlin.math.roundToInt

/**
 * 一条预算在**当前周期窗口**里的执行情况。
 *
 * [windowStart]/[windowEnd] 是这条预算这一期的时间范围（含首含尾，毫秒），
 * 既用于「本期」文案，也用于通知去重（同一期只提醒一次）。
 */
data class BudgetProgress(
    val budget: Budget,
    val spent: Long,
    val windowStart: Long,
    val windowEnd: Long
) {
    val limit: Long get() = budget.totalBudget

    /** 已用比例，仅用于**展示与排序**；阈值判定见 [reachedThreshold]。额度为 0 按 0 处理。 */
    val ratio: Float get() = if (limit > 0L) spent.toFloat() / limit else 0f

    /** 剩余额度，可为负（超支）。 */
    val remaining: Long get() = limit - spent

    val isOver: Boolean get() = limit > 0L && spent > limit

    /**
     * 已用百分比（整数显示）。四舍五入而不是截断：82.7% 显示成 82% 会在「恰好设了
     * 82% 阈值」这种边界上让用户看到「已用 82% 却没提醒」。预算页弹窗、通知、
     * 小组件共用这一个口径，别在调用处再各算一遍。
     */
    val usedPercent: Int get() =
        if (limit > 0L) (spent.toDouble() * 100 / limit).roundToInt() else 0

    /**
     * 是否已达到（或超过）这条预算自己的提醒阈值。
     *
     * 必须走 Double：`ratio` 是 Float，0.9f 提升成 Double 后是 0.899999976，
     * 「恰好用掉 90%」会被判成没达标；阈值本身是 Double，混类型比较会随十进制值漂移。
     */
    val reachedThreshold: Boolean get() = limit > 0L &&
        spent.toDouble() / limit.toDouble() >= budget.reminderThreshold

    /** 严重程度：超支 2 / 达阈值 1 / 正常 0，用于挑「最该提醒的那条」。 */
    val severity: Int get() = when {
        isOver -> 2
        reachedThreshold -> 1
        else -> 0
    }

    /**
     * 本期**已经过去**多少（0f~1f），用来跟 [ratio] 对照「花得比时间快还是慢」。
     *
     * 支出 82% 而时间只过 13% → 花得偏快；反过来则是还有余量。
     * 窗口非法（end <= start）时返回 0。
     *
     * 只在预算页脚注显示成「已过 13%」——曾经在进度条上画过一道对应刻度的竖线，
     * 真机上被误认成屏幕坏点，已移除。
     */
    fun elapsedRatio(now: Long = System.currentTimeMillis()): Float {
        val span = windowEnd - windowStart
        if (span <= 0L) return 0f
        return ((now - windowStart).toFloat() / span).coerceIn(0f, 1f)
    }
}
