package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.db.entity.Budget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * 本月记账 · **数据版**（v22）：左栏「本月支出 hero + 预算条」，右栏近 7 天柱（今天恒全不透明）。
 * 柱状版 / 预算版见 [BillTrendWidgetProvider] / [BillBudgetWidgetProvider]，三样式共用取数与零件。
 *
 * 数据口径（无额外字段）：
 * - 支出/收入按月取，选了账本走 `*ByBook`（预算表没有账本维度，选账本时预算置空）；
 * - 近 7 天柱 = 「6 天前 → 今天」的每日支出，柱高 = 当天 ÷ 峰值；
 * - 预算用量 = 支出 ÷ 预算，超支封顶 100%；没设预算整块收起，不拿 0% 假装有预算。
 *
 * 四档按 [WidgetSizeClass] 动态切构图：WIDE hero+条+柱 / SQUARE hero+迷你柱 / BAR 三列读数 / CHIP 数字+五微柱。
 */
// open：BillSquare/BillBar/BillMini 尺寸形态薄子类继承（v22 四档）
open class BillWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): com.palmnote.data.db.dao.BillDao
        fun budgetDao(): com.palmnote.data.db.dao.BudgetDao
        fun preferencesManager(): com.palmnote.data.datastore.PreferencesManager
    }

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()

        // 账本是**按组件实例**选的：每个实例各自取一次数，不能几个实例共用一个快照。
        // 强调色同样逐实例：配置了配色覆盖（强制主题包主色）就用它，否则按稿 V22 的账单族色。
        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            val accent = accentOf(context, prefs, appWidgetId)
            val snapshot = fetchSnapshot(entryPoint, prefs.widgetBook(appWidgetId).first())
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                WidgetSizeClass.WIDE.designWidthDp,
                WidgetSizeClass.WIDE.designHeightDp
            ) { width, height ->
                val size = WidgetSizeClass.of(width, height)
        val views = when (size) {
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP ->
                bindBarViews(context, appWidgetId, snapshot, accent)
            WidgetSizeClass.SQUARE ->
                bindSquareViews(context, appWidgetId, snapshot, accent)
            WidgetSizeClass.TALL ->
                bindTall4x4(context, appWidgetId, snapshot)
            else -> bindViews(context, appWidgetId, snapshot, accent)
        }
        WidgetV22.bindCardBg(views, size)
        views
            }
        }
    }

    /** 4×4（v37）：主数字 + 三 KPI + 近 12 个月柱图 + 分类 top4 小胶囊。 */
    internal fun bindTall4x4(
        context: Context,
        appWidgetId: Int,
        snapshot: BillSnapshot
    ): RemoteViews {
        val views = widgetViews(context, R.layout.widget_v22_tall)
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_BILL + appWidgetId,
                WidgetDeepLink.TAB_BILL
            )
        )
        // tall 布局的头部由 bindTallHeader 统一上（徽章族色 c5 橙 = 账单族色；
        // widget_bill_badge 只存在于 unified 布局，不能在这里设）
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_wallet_white,
            title = context.getString(R.string.widget_bill_title),
            meta = DateTimeFormatter.ofPattern("M 月").format(LocalDate.now()),
            familyIdx = 4
        )
        WidgetV22.bindTallHero(
            context, views,
            WidgetData.formatMoneyCompact(context, snapshot.monthlyExpense),
            context.getString(
                R.string.widget_bill_budget_fmt,
                WidgetData.formatMoneyCompact(context, snapshot.budget?.totalBudget ?: 0L)
            )
        )
        bindTallKpis(context, views, snapshot)
        bindTallChart(context, views, snapshot)
        // 分类小胶囊（稿 4×4 底部四枚）：没有分类数据时整条收起，不摆空胶囊
        WidgetV22.bindMiniChips(
            context, views,
            snapshot.topCategories.mapIndexed { i, c ->
                WidgetV22.MiniChip(c.category, WidgetData.formatMoneyCompact(context, c.total), i)
            }
        )
        WidgetV22.tallSection(context, views, "minichips", snapshot.topCategories.isNotEmpty())
        WidgetV22.tallSection(context, views, "rows", false)
        WidgetV22.tallSection(context, views, "footer", false)
        WidgetV22.tallSection(context, views, "strip_host", false)
        WidgetV22.tallSection(context, views, "grid", false)
        WidgetV22.tallSection(context, views, "empty", false)
        return views
    }

    /** 4×4 KPI：收入 / 结余 / 日均（按本月天数折算）。 */
    private fun bindTallKpis(context: Context, views: RemoteViews, snapshot: BillSnapshot) {
        val balance = snapshot.monthlyIncome - snapshot.monthlyExpense
        val daily = snapshot.monthlyExpense / LocalDate.now().dayOfMonth.coerceAtLeast(1)
        WidgetV22.bindKpis(
            context, views,
            listOf(
                WidgetV22.Kpi(
                    context.getString(R.string.widget_net_label_income_alt),
                    WidgetData.formatMoneyCompact(context, snapshot.monthlyIncome),
                    null,
                    1
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_bill_balance),
                    WidgetData.formatMoneyCompact(context, balance),
                    null,
                    0
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_bill_daily_avg),
                    WidgetData.formatMoneyCompact(context, daily),
                    null,
                    4
                )
            )
        )
    }

    /** 4×4 柱图：近 12 个月支出（旧→新，末月 = 本月，橙色峰值 + 金额标）。 */
    private fun bindTallChart(context: Context, views: RemoteViews, snapshot: BillSnapshot) {
        val max = (snapshot.monthlySeries.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val today = LocalDate.now()
        val axisFmt = DateTimeFormatter.ofPattern("M")
        val bars = snapshot.monthlySeries.mapIndexed { i, v ->
            val month = today.minusMonths((snapshot.monthlySeries.size - 1 - i).toLong())
            WidgetV22.ChartBar(
                percent = ((v.toDouble() / max) * 100).toInt(),
                familyIdx = if (i == snapshot.monthlySeries.lastIndex) 4 else 0,
                label = if (i == snapshot.monthlySeries.lastIndex) WidgetData.formatChartValue(v) else null,
                axis = month.format(axisFmt),
                // 轴末格 = 本月
                today = i == snapshot.monthlySeries.lastIndex
            )
        }
        val avgSeries = WidgetData.formatMoneyCompact(
            context,
            snapshot.monthlySeries.sum() / snapshot.monthlySeries.size.coerceAtLeast(1)
        )
        WidgetV22.bindChart(
            context, views,
            context.getString(R.string.widget_chart_months),
            context.getString(R.string.widget_chart_avg_fmt, avgSeries),
            bars
        )
    }

    /** 4×2（以及被拉成 4×4 时）：左 hero + 预算条，右近 7 天柱。 */
    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        snapshot: BillSnapshot,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = widgetViews(context, R.layout.widget_bill_unified)
        bindHeader(views, accent)
        bindAddButton(context, views, accent)
        views.setTextViewText(
            R.id.widget_expense_amount,
            "¥${WidgetData.formatAmountCompact(context, snapshot.monthlyExpense)}"
        )

        // 预算条：没设预算整块收起（条 + 说明），不拿 0% 假装有预算
        val budget = snapshot.budget?.takeIf { it.totalBudget > 0 }
        if (budget != null) {
            bindBudgetUsage(context, views, snapshot, budget, accent.accent)
        } else {
            views.setViewVisibility(R.id.widget_bill_budget_bar, View.GONE)
            views.setViewVisibility(R.id.widget_budget_card_label, View.GONE)
            views.setViewVisibility(R.id.widget_budget_amount, View.GONE)
        }

        // 近 7 天柱（v22 新增）：柱色跟用户配色，今天（末柱）恒全不透明
        val bars = widgetViews(context, R.layout.widget_v22_bars7)
        WidgetV22.bindBars7(context, bars, snapshot.dailyExpense, accent.accent)
        views.addView(R.id.widget_bill_bars_host, bars)

        bindDataVisibility(views, hasData(snapshot))
        bindClicks(context, views, appWidgetId, R.id.widget_add_btn)
        return views
    }

    /** 2×2：标题行 + hero + 七根迷你柱压在卡底（稿「宁减条数不缩字号」）。 */
    internal fun bindSquareViews(
        context: Context,
        appWidgetId: Int,
        snapshot: BillSnapshot,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = widgetViews(context, R.layout.widget_bill_square)
        val hasData = hasData(snapshot)
        views.setInt(R.id.widget_bill_badge, "setColorFilter", accent.accent)
        views.setTextViewText(
            R.id.widget_expense_amount,
            if (hasData) "¥${WidgetData.formatAmountCompact(context, snapshot.monthlyExpense)}" else "—"
        )
        val bars = widgetViews(context, R.layout.widget_v22_bars7)
        WidgetV22.bindBars7(context, bars, snapshot.dailyExpense, accent.accent)
        views.addView(R.id.widget_bill_bars_host, bars)
        bindClicks(context, views, appWidgetId, null)
        return views
    }

    /** 4×1：三列读数 支出 / 收入 / 结余（稿「读数条：支出/收入/结余三列」）。 */
    internal fun bindBarViews(
        context: Context,
        appWidgetId: Int,
        snapshot: BillSnapshot,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = widgetViews(context, R.layout.widget_bill_v22_bar)
        views.setInt(R.id.widget_bill_badge, "setColorFilter", accent.accent)
        views.setTextViewText(
            R.id.widget_expense_amount,
            "¥${WidgetData.formatAmountCompact(context, snapshot.monthlyExpense)}"
        )
        views.setTextViewText(
            R.id.widget_bill_income,
            "¥${WidgetData.formatAmountCompact(context, snapshot.monthlyIncome)}"
        )
        views.setTextViewText(R.id.widget_bill_balance, balanceText(context, snapshot))
        bindClicks(context, views, appWidgetId, null)
        return views
    }

    internal data class BillSnapshot(
        val monthlyExpense: Long,
        val monthlyIncome: Long,
        val budget: Budget?,
        val topCategory: String?,
        /** 近 7 天（从 6 天前到今天）的每日支出，柱状图用。 */
        val dailyExpense: List<Long> = emptyList(),
        /** 上月总支出：趋势样式 2×2 的「上月 → 本月」对比用。 */
        val lastMonthExpense: Long = 0L,
        /** 近 12 个月支出（旧→新，含本月）；4×4 柱图用。 */
        val monthlySeries: List<Long> = emptyList(),
        /** 本月支出分类 top4（名 + 分）；4×4 分类小胶囊用。 */
        val topCategories: List<com.palmnote.data.db.dao.CategoryTotal> = emptyList()
    )

    internal companion object {
        // ── 三个样式共用的绑定零件（可见性、进度条、点击、取数） ──

        /** 卡片头部的色族徽章：柱状版 / 预算版同用。 */
        internal fun bindHeader(views: RemoteViews, accent: WidgetData.AccentTheme) {
            views.setInt(R.id.widget_bill_badge, "setColorFilter", accent.accent)
        }

        /**
         * 「新增」胶囊：底是色族 tint（语义色，不进 [WidgetToneSweep] 的中性扫色），字是强调色。
         * 自选深色背景时按近黑卡调的深底胶囊落在彩卡上就是一块黑（2026-10-20 反馈）——
         * 卡片相对 + 深色卡翻成浅色胶囊；其余情况写回布局原样（视图树复用会残留上一轮的样式）。
         */
        internal fun bindAddButton(context: Context, views: RemoteViews, accent: WidgetData.AccentTheme) {
            val chipCard = (context as? WidgetToneContext)?.tone == WidgetTone.DARK
            views.setInt(
                R.id.widget_add_btn,
                "setBackgroundResource",
                if (chipCard) R.drawable.widget_pill_chip else R.drawable.widget_pill_tint1
            )
            views.setTextColor(R.id.widget_add_btn, accent.accent)
        }

        /**
         * 预算用量条：track 铺底，fill 是白底 clip，宽度由 level 定（82% = setImageLevel 8200）。
         * 超支按 100% 封顶，免得进度条扫过头。
         */
        internal fun bindBar(views: RemoteViews, percent: Int, fillColor: Int) {
            views.setInt(R.id.widget_bill_budget_fill, "setColorFilter", fillColor)
            // RemoteViews 没有 setImageLevel 专用 API，反射调 ImageView.setImageLevel
            views.setInt(R.id.widget_bill_budget_fill, "setImageLevel", percent * 100)
        }

        /** 整卡点击进账单页；[addViewId] 非空时再挂「记一笔」热区（部分档没有那颗胶囊）。 */
        internal fun bindClicks(context: Context, views: RemoteViews, appWidgetId: Int, addViewId: Int?) {
            views.setOnClickPendingIntent(
                R.id.widget_layout,
                WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_BILL + appWidgetId, WidgetDeepLink.TAB_BILL)
            )
            if (addViewId != null) {
                views.setOnClickPendingIntent(
                    addViewId,
                    WidgetHelper.createPendingIntent(
                        context,
                        WidgetDeepLink.SEG_BILL_ADD + appWidgetId,
                        WidgetDeepLink.TAB_ADD_BILL
                    )
                )
            }
        }

        internal fun bindDataVisibility(views: RemoteViews, hasData: Boolean) {
            views.setViewVisibility(R.id.widget_content_with_data, if (hasData) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_empty_state, if (hasData) View.GONE else View.VISIBLE)
        }

        internal fun hasData(snapshot: BillSnapshot): Boolean =
            snapshot.monthlyExpense > 0 || snapshot.monthlyIncome > 0

        /** 用量百分比（0..100，超支封顶）；没有预算返回 null = 「无进度可表」。 */
        internal fun usedPercent(snapshot: BillSnapshot, budget: Budget?): Int? {
            val total = budget?.totalBudget ?: return null
            if (total <= 0) return null
            return (snapshot.monthlyExpense * 100 / total).toInt().coerceIn(0, 100)
        }

        /** 净收入可正可负，标签不表方向，所以负值要带符号（格式函数返回绝对值）。 */
        internal fun balanceText(context: Context, snapshot: BillSnapshot): String {
            val balance = snapshot.monthlyIncome - snapshot.monthlyExpense
            val sign = if (balance < 0) "-" else ""
            return "$sign¥${WidgetData.formatAmountCompact(context, balance)}"
        }

        /** 有预算：用量条按「支出 ÷ 预算」扫过，下面写「预算已用 N%」。 */
        internal fun bindBudgetUsage(
            context: Context,
            views: RemoteViews,
            snapshot: BillSnapshot,
            budget: Budget,
            fillColor: Int
        ) {
            val used = usedPercent(snapshot, budget) ?: 0
            views.setViewVisibility(R.id.widget_bill_budget_bar, View.VISIBLE)
            bindBar(views, used, fillColor)
            views.setTextViewText(R.id.widget_budget_card_label, context.getString(R.string.widget_budget_used))
            views.setTextViewText(R.id.widget_budget_amount, "$used%")
        }

        /**
         * 强调色逐实例：配置了配色覆盖（强制主题包主色）就用它；否则按设计稿 V22 的账单族色
         * c5 橙（稿 §2「颜色跟模块走」，不再默认跟随应用主题包 —— 2026-10-24 对稿定版）。
         */
        internal suspend fun accentOf(
            context: Context,
            prefs: com.palmnote.data.datastore.PreferencesManager,
            appWidgetId: Int
        ): WidgetData.AccentTheme {
            val override = prefs.widgetAccentThemeFor(appWidgetId).first()
            if (override == null) {
                return WidgetData.AccentTheme(
                    accent = WidgetV22.family(context, 4).d,
                    onAccent = WidgetData.AccentTheme.ON_ACCENT
                )
            }
            return WidgetData.readAccentTheme(context, prefs, override)
        }

        internal suspend fun fetchSnapshot(
            entryPoint: WidgetEntryPoint,
            bookId: Long?
        ): BillSnapshot {
            val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
            // 预算表没有账本维度：选中某个账本时不能再拿全局预算去算「已用百分比」，
            // 那是把 A 账本的支出除以全账本的预算。此时 budget 置空。
            val budget = if (bookId == null) {
                entryPoint.budgetDao().getBudgetByMonth(yearMonth)
                    ?: entryPoint.budgetDao().getLatestBudget().first()
            } else {
                null
            }
            // 有消费记录时带上最高消费分类（对应设计稿「餐饮预算已使用 82%」）
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
            val lastMonth = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val lastMonthExpense = if (bookId == null) {
                entryPoint.billDao().getMonthlyExpense(lastMonth).first() ?: 0L
            } else {
                entryPoint.billDao().getMonthlyExpenseByBook(bookId, lastMonth).first() ?: 0L
            }
            return BillSnapshot(
                monthlyExpense = expense,
                monthlyIncome = income,
                budget = budget,
                topCategory = topCategory,
                dailyExpense = fetchDailyExpense(entryPoint, bookId),
                lastMonthExpense = lastMonthExpense,
                monthlySeries = fetchMonthlySeries(entryPoint, bookId),
                topCategories = entryPoint.billDao()
                    .getTopExpenseCategories(yearMonth, 4)
                    .filter { it.total > 0 }
            )
        }

        /** 近 12 个月支出（旧→新，含本月）；4×4 柱图序列。 */
        private suspend fun fetchMonthlySeries(
            entryPoint: WidgetEntryPoint,
            bookId: Long?
        ): List<Long> = (11 downTo 0).map { offset ->
            val month = LocalDate.now().minusMonths(offset.toLong())
                .format(DateTimeFormatter.ofPattern("yyyy-MM"))
            if (bookId == null) {
                entryPoint.billDao().getMonthlyExpense(month).first() ?: 0L
            } else {
                entryPoint.billDao().getMonthlyExpenseByBook(bookId, month).first() ?: 0L
            }
        }

        /**
         * 近 7 天每日支出，按「6 天前 → 今天」对齐（那天没有账单就是 0）。
         * 用当天 00:00 作为起点，走本地时区，避免跨时区把日期算错一天。
         */
        internal suspend fun fetchDailyExpense(entryPoint: WidgetEntryPoint, bookId: Long?): List<Long> {
            val today = LocalDate.now()
            val from = today.minusDays((BAR_DAYS - 1).toLong()).atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val totals = entryPoint.billDao().getDailyExpenseSince(from).first()
                .groupBy { dayKey(it.date) }
                .mapValues { (_, rows) -> rows.sumOf { it.expense } }
            // 选了某个账本时按账本口径过滤：查询本身没有账本维度，这里用账本账单集合求交
            val allowed = if (bookId == null) {
                null
            } else {
                entryPoint.billDao()
                    .getBillsByBookAndMonth(bookId, DateTimeFormatter.ofPattern("yyyy-MM").format(today))
                    .first().map { dayKey(it.date) }.toSet()
            }
            return (BAR_DAYS - 1 downTo 0).map { back ->
                val key = dayKey(
                    today.minusDays(back.toLong()).atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                )
                val value = totals[key] ?: 0L
                if (allowed == null || allowed.contains(key)) value else 0L
            }
        }

        /** 把毫秒时间戳归到本地日期（同一天的多笔合并成一根柱子）。 */
        internal fun dayKey(millis: Long): String =
            java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().toString()

        /** 近 7 天柱状图的天数（口径与 fetchDailyExpense 的查询窗口一致）。 */
        internal const val BAR_DAYS = 7
    }
}
