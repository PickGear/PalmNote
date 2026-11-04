package com.palmnote.ui.bills

import android.content.Context
import com.palmnote.app.R
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.model.BudgetScope

/**
 * 预算的展示名。**普通函数而不是 @Composable**：同样的措辞要在两个地方用——
 * 界面（Compose）与通知（Worker，没有 Compose 上下文），各写一份必然会漂。
 */

/** 周期的短标签（本周 / 本月 / 本年）。 */
fun budgetPeriodLabel(context: Context, period: BudgetPeriod): String = context.getString(
    when (period) {
        BudgetPeriod.WEEK -> R.string.budget_period_week
        BudgetPeriod.MONTH -> R.string.budget_period_month
        BudgetPeriod.YEAR -> R.string.budget_period_year
    }
)

/**
 * 分类名的显示值：内置分类走既有本地化映射（`category_food` 等），
 * 自定义分类没有映射就原样显示。存库始终是中文 key（与 bills.category 对齐）。
 */
fun budgetCategoryDisplayName(context: Context, category: String): String =
    getLocalizedCategoryName(category)?.let { context.getString(it) } ?: category

/**
 * 一条预算的名字：「本月总预算」「本月「餐饮」预算」「本月「旅行账本」预算」。
 *
 * 账本名从 [bookNames] 取；账本已删（预算残留指向不存在的账本）时退化成「某账本」，
 * 不把 id 直接印给用户看。
 */
fun budgetLabel(context: Context, progress: BudgetProgress, bookNames: Map<Long, String>): String {
    val period = budgetPeriodLabel(context, progress.budget.periodEnum)
    return when (progress.budget.scopeEnum) {
        BudgetScope.TOTAL -> context.getString(R.string.budget_label_total, period)
        BudgetScope.CATEGORY -> context.getString(
            R.string.budget_label_category,
            period,
            budgetCategoryDisplayName(context, progress.budget.category.orEmpty())
        )
        BudgetScope.BOOK -> context.getString(
            R.string.budget_label_book,
            period,
            bookNames[progress.budget.bookId] ?: context.getString(R.string.budget_label_book_missing)
        )
    }
}
