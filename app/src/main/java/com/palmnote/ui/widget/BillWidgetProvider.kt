package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.entity.Budget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BillWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): BillDao
        fun budgetDao(): BudgetDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val snapshot = fetchSnapshot(entryPoint)
        val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, COMPACT_MIN_WIDTH_DP)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, COMPACT_MIN_HEIGHT_DP)
            val views = if (isCompactSize(width, height)) {
                bindMiniViews(context, appWidgetId, snapshot, accent)
            } else {
                bindViews(context, appWidgetId, snapshot, accent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /** 摆放尺寸是否已小到该用迷你档：宽度不足 3 格或高度不足 2 格，任一不足即降级。 */
    internal fun isCompactSize(widthDp: Int, heightDp: Int): Boolean =
        widthDp < COMPACT_MIN_WIDTH_DP || heightDp < COMPACT_MIN_HEIGHT_DP

    internal fun bindMiniViews(
        context: Context,
        appWidgetId: Int,
        snapshot: BillSnapshot,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bill_mini)
        views.setTextViewText(
            R.id.widget_bill_mini_expense,
            WidgetData.formatMoneyCompact(context, snapshot.monthlyExpense)
        )
        // 有预算显示剩余 + 进度条，无预算显示当月结余（无进度可表）
        val budget = snapshot.budget?.takeIf { it.totalBudget > 0 }
        val budgetValue = budget?.let { it.totalBudget - snapshot.monthlyExpense }
            ?: (snapshot.monthlyIncome - snapshot.monthlyExpense)
        val budgetLabel = context.getString(
            if (budget != null) R.string.widget_budget_remaining else R.string.widget_net_income
        )
        views.setTextViewText(
            R.id.widget_bill_mini_budget,
            "$budgetLabel ${WidgetData.formatMoneyCompact(context, budgetValue)}"
        )
        views.setTextColor(R.id.widget_bill_mini_budget, accent.accent)
        views.setViewVisibility(R.id.widget_bill_mini_bar, if (budget != null) View.VISIBLE else View.GONE)
        if (budget != null) {
            // 用量口径与全档横幅一致：超支按 100% 封顶；填充是白底 clip，宽度由 level 定
            val usedPercent = (snapshot.monthlyExpense * 100 / budget.totalBudget).toInt().coerceIn(0, 100)
            views.setInt(R.id.widget_bill_mini_bar_fill, "setColorFilter", accent.accent)
            // RemoteViews 没有 setImageLevel 专用 API，反射调 ImageView.setImageLevel
            views.setInt(R.id.widget_bill_mini_bar_fill, "setImageLevel", usedPercent * 100)
        }
        views.setOnClickPendingIntent(
            R.id.widget_bill_mini_add,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_BILL_ADD + appWidgetId, WidgetDeepLink.TAB_ADD_BILL)
        )
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_BILL + appWidgetId, WidgetDeepLink.TAB_BILL)
        )
        return views
    }

    internal data class BillSnapshot(
        val monthlyExpense: Long,
        val monthlyIncome: Long,
        val budget: Budget?,
        val topCategory: String?
    )

    private suspend fun fetchSnapshot(entryPoint: WidgetEntryPoint): BillSnapshot {
        val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
        val budget = entryPoint.budgetDao().getBudgetByMonth(yearMonth)
            ?: entryPoint.budgetDao().getLatestBudget().first()
        // 有消费记录时横幅带上最高消费分类（对应设计稿「餐饮预算已使用 82%」）
        val topCategory = if (budget != null && budget.totalBudget > 0) {
            entryPoint.billDao().getTopExpenseCategory(yearMonth)
                ?.takeIf { it.total > 0 }
                ?.category
        } else {
            null
        }
        return BillSnapshot(
            monthlyExpense = entryPoint.billDao().getMonthlyExpense(yearMonth).first() ?: 0L,
            monthlyIncome = entryPoint.billDao().getMonthlyIncome(yearMonth).first() ?: 0L,
            budget = budget,
            topCategory = topCategory
        )
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        snapshot: BillSnapshot,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bill_unified)

        views.setTextViewText(
            R.id.widget_income_amount,
            "+${WidgetData.formatMoneyCompact(context, snapshot.monthlyIncome)}"
        )
        views.setTextViewText(
            R.id.widget_expense_amount,
            "-${WidgetData.formatMoneyCompact(context, snapshot.monthlyExpense)}"
        )
        views.setTextColor(R.id.widget_add_btn, accent.accent)

        val bannerText = if (snapshot.budget != null && snapshot.budget.totalBudget > 0) {
            bindBudgetCard(context, views, snapshot)
        } else {
            bindBalanceCard(context, views, snapshot)
        }
        views.setTextViewText(R.id.widget_budget_banner_text, bannerText)
        // 横幅底运行时着主题色：漏了这一步时白底卡上是白底白字，整条横幅（文字+箭头+进度条）不可见
        views.setInt(R.id.widget_budget_banner_bg, "setColorFilter", accent.accent)
        // 横幅可点 → 报表页（此前 chevron 是视觉骗点击，期 1 假按钮清零）
        views.setOnClickPendingIntent(
            R.id.widget_budget_banner,
            WidgetHelper.createReportPendingIntent(context, appWidgetId)
        )

        val hasData = snapshot.monthlyExpense > 0 || snapshot.monthlyIncome > 0
        views.setViewVisibility(R.id.widget_content_with_data, if (hasData) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_empty_state, if (hasData) View.GONE else View.VISIBLE)

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_BILL + appWidgetId, WidgetDeepLink.TAB_BILL)
        )
        views.setOnClickPendingIntent(
            R.id.widget_add_btn,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_BILL_ADD + appWidgetId, WidgetDeepLink.TAB_ADD_BILL)
        )
        return views
    }

    private fun bindBudgetCard(context: Context, views: RemoteViews, snapshot: BillSnapshot): String {
        val budget = snapshot.budget ?: return ""
        views.setTextViewText(R.id.widget_budget_card_label, context.getString(R.string.widget_budget_remaining))
        views.setTextViewText(
            R.id.widget_budget_amount,
            WidgetData.formatMoneyCompact(context, budget.totalBudget - snapshot.monthlyExpense)
        )
        val usedPercent = (snapshot.monthlyExpense * 100 / budget.totalBudget).toInt()
        val bannerText = snapshot.topCategory?.let {
            context.getString(R.string.widget_budget_top_format, it, usedPercent)
        } ?: context.getString(R.string.widget_budget_used_format, usedPercent)
        // 横幅内嵌用量进度条（超支按 100% 封顶显示）
        views.setViewVisibility(R.id.widget_budget_progress, View.VISIBLE)
        views.setInt(R.id.widget_budget_progress, "setMax", 100)
        views.setInt(R.id.widget_budget_progress, "setProgress", usedPercent.coerceIn(0, 100))
        return bannerText
    }

    private fun bindBalanceCard(context: Context, views: RemoteViews, snapshot: BillSnapshot): String {
        val balance = snapshot.monthlyIncome - snapshot.monthlyExpense
        views.setTextViewText(R.id.widget_budget_card_label, context.getString(R.string.widget_net_income))
        views.setTextViewText(
            R.id.widget_budget_amount,
            WidgetData.formatMoneyCompact(context, balance)
        )
        val sign = if (balance >= 0) "+" else ""
        views.setViewVisibility(R.id.widget_budget_progress, View.GONE)
        return context.getString(
            R.string.widget_month_balance_format,
            "$sign${WidgetData.formatMoneyCompact(context, balance)}"
        )
    }

    private companion object {
        /** 3 格宽（180dp）；摆放宽度低于此值即迷你档。 */
        const val COMPACT_MIN_WIDTH_DP = 180

        /** 2 格高（110dp）；摆放高度低于此值即迷你档（3×1 这类扁档也走迷你）。 */
        const val COMPACT_MIN_HEIGHT_DP = 110
    }
}
