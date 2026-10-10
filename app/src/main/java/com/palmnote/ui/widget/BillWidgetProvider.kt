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
        val prefs = entryPoint.preferencesManager()
        val accent = WidgetData.readAccentTheme(context, prefs)

        // 账本是**按组件实例**选的：每个实例各自取一次数，不能几个实例共用一个快照
        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            val snapshot = fetchSnapshot(entryPoint, prefs.widgetBook(appWidgetId).first())
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                COMPACT_MIN_WIDTH_DP,
                COMPACT_MIN_HEIGHT_DP
            ) { width, height ->
                if (isCompactSize(width, height)) {
                    bindMiniViews(context, appWidgetId, snapshot, accent)
                } else {
                    bindViews(context, appWidgetId, snapshot)
                }
            }
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
        val topCategory: String?,
        /** 近 7 天（从 6 天前到今天）的每日支出，柱状图用。 */
        val dailyExpense: List<Long> = emptyList()
    )

    private suspend fun fetchSnapshot(entryPoint: WidgetEntryPoint, bookId: Long?): BillSnapshot {
        val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
        // 预算表没有账本维度：选中某个账本时不能再拿全局预算去算「已用百分比」，
        // 那是把 A 账本的支出除以全账本的预算。此时 budget 置空，横幅自动退回净收入卡（本书口径）。
        val budget = if (bookId == null) {
            entryPoint.budgetDao().getBudgetByMonth(yearMonth)
                ?: entryPoint.budgetDao().getLatestBudget().first()
        } else {
            null
        }
        // 有消费记录时横幅带上最高消费分类（对应设计稿「餐饮预算已使用 82%」）
        val topCategory = if (budget != null && budget.totalBudget > 0) {
            entryPoint.billDao().getTopExpenseCategory(yearMonth)
                ?.takeIf { it.total > 0 }
                ?.category
        } else {
            null
        }
        val expense = if (bookId == null) {
            entryPoint.billDao().getMonthlyExpense(yearMonth).first() ?: 0L
        } else {
            entryPoint.billDao().getMonthlyExpenseByBook(bookId, yearMonth).first() ?: 0L
        }
        val income = if (bookId == null) {
            entryPoint.billDao().getMonthlyIncome(yearMonth).first() ?: 0L
        } else {
            entryPoint.billDao().getMonthlyIncomeByBook(bookId, yearMonth).first() ?: 0L
        }
        return BillSnapshot(
            monthlyExpense = expense,
            monthlyIncome = income,
            budget = budget,
            topCategory = topCategory,
            dailyExpense = fetchDailyExpense(entryPoint, bookId)
        )
    }

    /**
     * 近 7 天柱状图：柱高 = 当天支出 ÷ 七天里最大的一天。
     * RemoteViews 不能动态改控件高度，所以柱高走竖向 clip 的 level（0..10000）。
     * 全为 0 或某天为 0 时留一条矮基线（着轨道色），免得整行看起来是空的；
     * 最高的一天换个颜色点出来。
     */
    private fun bindDailyBars(
        views: RemoteViews,
        daily: List<Long>,
        normalColor: Int,
        highlightColor: Int,
        trackColor: Int
    ) {
        val max = daily.maxOrNull() ?: 0L
        barIds.forEachIndexed { index, id ->
            val value = daily.getOrElse(index) { 0L }
            val isEmpty = max <= 0L || value <= 0L
            val level = if (isEmpty) {
                MIN_BAR_LEVEL
            } else {
                (value * FULL_LEVEL / max).toInt().coerceIn(MIN_BAR_LEVEL, FULL_LEVEL)
            }
            val color = when {
                isEmpty -> trackColor
                value >= max -> highlightColor
                else -> normalColor
            }
            views.setInt(id, "setColorFilter", color)
            views.setInt(id, "setImageLevel", level)
        }
    }

    /**
     * 近 7 天每日支出，按「6 天前 → 今天」对齐（那天没有账单就是 0）。
     * 用当天 00:00 作为起点，走本地时区，避免跨时区把日期算错一天。
     */
    private suspend fun fetchDailyExpense(entryPoint: WidgetEntryPoint, bookId: Long?): List<Long> {
        val today = LocalDate.now()
        val from = today.minusDays((BAR_DAYS - 1).toLong()).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val totals = entryPoint.billDao().getDailyExpenseSince(from).first()
            .groupBy { dayKey(it.date) }
            .mapValues { (_, rows) -> rows.sumOf { it.expense } }
        // 选了某个账本时按账本口径过滤：查询本身没有账本维度，这里用账本账单集合求交
        val allowed = if (bookId == null) null else {
            entryPoint.billDao().getBillsByBookAndMonth(bookId, DateTimeFormatter.ofPattern("yyyy-MM").format(today))
                .first().map { dayKey(it.date) }.toSet()
        }
        return (BAR_DAYS - 1 downTo 0).map { back ->
            val key = dayKey(today.minusDays(back.toLong()).atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli())
            val value = totals[key] ?: 0L
            if (allowed == null || allowed.contains(key)) value else 0L
        }
    }

    /** 把毫秒时间戳归到本地日期（同一天的多笔合并成一根柱子）。 */
    private fun dayKey(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        snapshot: BillSnapshot
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bill_unified)

        // 装饰色走色族（不占全局强调色）：徽章与用量条用橙，柱子用蓝、最高的一天换回橙
        val primary = WidgetData.colorFamily(context, 0)
        val secondary = WidgetData.colorFamily(context, 1)

        // 大数字走不带分的紧凑格式：左栏只有 96dp，`¥5290.50` 这种带分的会被省略号截断
        // （真机实测截成 `¥529…`）；稿子里也是 `¥8200` 这种整数
        views.setTextViewText(
            R.id.widget_expense_amount,
            "¥${WidgetData.formatAmountCompact(context, snapshot.monthlyExpense)}"
        )
        views.setInt(R.id.widget_bill_badge, "setColorFilter", primary.hue)
        views.setTextColor(R.id.widget_add_btn, primary.hue)
        bindDailyBars(
            views,
            snapshot.dailyExpense,
            normalColor = secondary.hue,
            highlightColor = primary.hue,
            trackColor = context.getColor(R.color.widget_v2_ring)
        )

        // 用量条与图下说明：有预算给「预算已用 N%」，没预算就给本月净收入（与迷你档同口径）
        val budget = snapshot.budget?.takeIf { it.totalBudget > 0 }
        if (budget != null) {
            bindBudgetUsage(context, views, snapshot, budget, primary.hue)
        } else {
            bindBalanceCard(context, views, snapshot)
        }

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

    /** 有预算：用量条按「支出 ÷ 预算」扫过，下面写「预算已用 N%」。 */
    private fun bindBudgetUsage(
        context: Context,
        views: RemoteViews,
        snapshot: BillSnapshot,
        budget: Budget,
        fillColor: Int
    ) {
        // 超支按 100% 封顶，免得进度条扫过头
        val usedPercent = (snapshot.monthlyExpense * 100 / budget.totalBudget).toInt().coerceIn(0, 100)
        views.setViewVisibility(R.id.widget_bill_budget_bar, View.VISIBLE)
        views.setInt(R.id.widget_bill_budget_fill, "setColorFilter", fillColor)
        // RemoteViews 没有 setImageLevel 专用 API，反射调 ImageView.setImageLevel
        views.setInt(R.id.widget_bill_budget_fill, "setImageLevel", usedPercent * 100)
        views.setTextViewText(R.id.widget_budget_card_label, context.getString(R.string.widget_budget_used))
        views.setTextViewText(R.id.widget_budget_amount, "$usedPercent%")
    }

    private fun bindBalanceCard(context: Context, views: RemoteViews, snapshot: BillSnapshot) {
        val balance = snapshot.monthlyIncome - snapshot.monthlyExpense
        // 没有预算就没有用量可表，收起条只留一行结余
        views.setViewVisibility(R.id.widget_bill_budget_bar, View.GONE)
        views.setTextViewText(R.id.widget_budget_card_label, context.getString(R.string.widget_net_income))
        // 净收入可正可负，标签不表方向，所以负值要带符号（格式函数返回绝对值）
        val balanceSign = if (balance < 0) "-" else ""
        views.setTextViewText(
            R.id.widget_budget_amount,
            "$balanceSign¥${WidgetData.formatAmountCompact(context, balance)}"
        )
    }

    private val barIds = listOf(
        R.id.widget_bill_bar_0,
        R.id.widget_bill_bar_1,
        R.id.widget_bill_bar_2,
        R.id.widget_bill_bar_3,
        R.id.widget_bill_bar_4,
        R.id.widget_bill_bar_5,
        R.id.widget_bill_bar_6
    )

    private companion object {
        /** 3 格宽（180dp）；摆放宽度低于此值即迷你档。 */
        const val COMPACT_MIN_WIDTH_DP = 180

        /** 2 格高（110dp）；摆放高度低于此值即迷你档（3×1 这类扁档也走迷你）。 */
        const val COMPACT_MIN_HEIGHT_DP = 110

        /** 柱状图天数：近 7 天。 */
        const val BAR_DAYS = 7

        /** clip 的 level 满分与空柱基线。 */
        private const val FULL_LEVEL = 10_000
        private const val MIN_BAR_LEVEL = 220
    }
}
