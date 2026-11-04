package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetScope
import com.palmnote.domain.model.DemoOrigin

/**
 * 预算模板（v21 起是「长期有效」的一条规则，不再是「一个月一行」）。
 *
 * 维度组合决定这条预算管什么：
 * - [period] 周 / 月 / 年；
 * - [scope] 全账本合计 / 指定账本 / 指定分类；
 * - [bookId] 仅 `scope = BOOK` 时有值；[category] 仅 `scope = CATEGORY` 时有值。
 *
 * 同一组维度只应有一条（仓库层按「先查再改」保证；不用唯一索引是因为
 * `bookId`/`category` 可空而 SQLite 唯一索引不把 NULL 当重复）。
 *
 * 金额单位一律**分**，与 bills 同口径，避免浮点误差。
 */
@Entity(tableName = "budgets")
@Immutable
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 周期名（[BudgetPeriod] 的 name），入库为 TEXT。 */
    val period: String = BudgetPeriod.MONTH.name,
    /** 范围名（[BudgetScope] 的 name），入库为 TEXT。 */
    val scope: String = BudgetScope.TOTAL.name,
    /** `scope = BOOK` 时的账本 id。 */
    val bookId: Long? = null,
    /** `scope = CATEGORY` 时的分类名（bills.category 是分类名而不是 id）。 */
    val category: String? = null,
    /** 预算额度（分）。 */
    val totalBudget: Long,
    /** 用掉多少比例时提醒（0.01 ~ 1.0，默认 80%）。 */
    val reminderThreshold: Double = 0.8,
    /** 这条预算是否发通知（页内进度始终显示，不受它影响）。 */
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 演示隔离：NONE 真实 / SEED 播种示例 / USER 演示期自建（与 bills/assets/wallets 同口径）。 */
    val demoOrigin: DemoOrigin = DemoOrigin.NONE
) {
    val periodEnum: BudgetPeriod get() = BudgetPeriod.from(period)
    val scopeEnum: BudgetScope get() = BudgetScope.from(scope)

    companion object {
        /** 全账本某周期的总预算（最常用的一种，可读性比直接传枚举更清楚）。 */
        fun total(period: BudgetPeriod, totalBudget: Long, demoOrigin: DemoOrigin = DemoOrigin.NONE) =
            Budget(
                period = period.name,
                scope = BudgetScope.TOTAL.name,
                totalBudget = totalBudget,
                demoOrigin = demoOrigin
            )
    }
}
