package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.model.BillType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * 概览组件（v22）：「今天」一屏 —— 左 hero 今日支出，右三枚胶囊（待办 / 打卡 / 订阅）。
 *
 * 与 v4 的「四模块 tile（预算/目标/待办/纪念日）」是**两代设计**：v22 按稿改成
 * 「今日快照」，预算/目标/纪念日的明细各自由自己的组件与页面承载（点胶囊进对应页）。
 *
 * 四档构图按 [WidgetSizeClass] 切档（稿 c-dash）：
 * - WIDE 4×2：左 hero（今日支出 + 笔数 + 收入）+ 右三枚胶囊；
 * - SQUARE 2×2：hero + 笔数 + 两枚胶囊（待办 / 打卡）；
 * - BAR 4×1：四列读数（支出 / 待办 / 打卡 / 订阅），打卡列带 5 点；
 * - CHIP 2×1：支出徽章 + 笔数 / 收入。
 * TALL（4×4）稿未设计，复用 WIDE 构图。
 *
 * 点阵口径同打卡组件：只有打卡列画 5 点（分母 = 习惯数，≤5 才画）；
 * 支出笔数、待办项数无上限，只给数字（用户 2026-10-23 定）。
 */
// open：DashboardSquare / DashboardBar / DashboardTall 尺寸形态薄子类继承（v37 四档）
open class DashboardWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): BillDao
        fun budgetDao(): com.palmnote.data.db.dao.BudgetDao
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    /** 概览的一屏数据（全部当天口径）。 */
    internal data class DashboardSnapshot(
        /** 今日支出合计（分）。 */
        val todayExpense: Long,
        /** 今日收入合计（分）。 */
        val todayIncome: Long,
        /** 今日支出笔数。 */
        val todayExpenseCount: Int,
        /** 星期简称（周四 / Wed）—— 头部「今日 · 周四」用。 */
        val weekday: String,
        /** 今日待办未完成数（胶囊数值）。 */
        val todoRemaining: Int,
        /** 今日待办总数（胶囊副信息「共 N 项」）。 */
        val todoTotal: Int,
        /** 今日打卡完成数 / 习惯总数（打卡胶囊 2/5 与五点）。 */
        val habitDone: Int,
        val habitTotal: Int,
        /** 下次扣费日期文案（M/d）；没有订阅为 null（胶囊不摆）。 */
        val subDateText: String?,
        /** 距下次扣费天数；没有订阅为 null。 */
        val subDaysLeft: Long?,
        /** 近 7 天每日支出（分，含今天，旧→新）；4×4 柱图用。 */
        val dailyExpense: List<Long> = emptyList(),
        /** 本月预算已用百分比；无预算为 null（4×4 KPI）。 */
        val budgetUsedPercent: Int? = null,
        /** 预算剩余文案（「剩 ¥1.8k」）；无预算为 null。 */
        val budgetRemainText: String? = null
    )

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            WidgetEntryPoint::class.java
        )
        val snapshot = fetchSnapshot(appContext, entryPoint)
        val prefs = entryPoint.preferencesManager()

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            // 强调色：逐实例配色覆盖优先，否则概览族默认色（与徽章原先的 familyD(0) 一致）
            val accent = WidgetData.accentColorOrDefault(context, prefs, appWidgetId, familyD(context, 0))
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                bindViews(context, appWidgetId, snapshot, width, height, accent)
            }
        }
    }

    private suspend fun fetchSnapshot(context: Context, entryPoint: WidgetEntryPoint): DashboardSnapshot {
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val includeDemo = entryPoint.preferencesManager().lifeDemoMode.first()

        // 今日账单：支出合计 + 笔数、收入合计（TRANSFER 不计入任何一端）
        val bills = entryPoint.billDao().getBillsByDate(todayStart).first()
        val expense = bills.filter { it.type == BillType.EXPENSE }
        val income = bills.filter { it.type == BillType.INCOME }

        // 待办：与待办组件同源（今日到期口径）
        val board = WidgetData.fetchTodoBoard(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = includeDemo,
            demoMeta = LIFE_DEMO_META
        )
        // 打卡：与打卡组件同源
        val habitRows = HabitWidgetProvider().fetchHabitRows(
            context = context,
            templateDao = entryPoint.lifeTemplateDao(),
            itemDao = entryPoint.lifeItemDao(),
            includeDemo = includeDemo
        )
        // 订阅：最近一笔待扣费（与订阅组件同源）
        val subTemplate = entryPoint.lifeTemplateDao().getAllVisibleTemplates().first()
            .firstOrNull { it.name.contains(BuiltinTemplates.SUBSCRIPTION_KEYWORD) }
        val nextRenewal = subTemplate
            ?.let { SubscriptionWidgetProvider().fetchRenewals(entryPoint.lifeItemDao(), it.id, includeDemo) }
            ?.orEmpty()
            ?.firstOrNull()

        // 近 7 天每日支出（4×4 柱图）；月预算（4×4 KPI）
        val weekStart = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekBills = entryPoint.billDao().getBillsByDate(weekStart).first()
            .filter { it.type == BillType.EXPENSE }
        val dailyExpense = sumByDay(weekBills, today, 7)
        val budget = entryPoint.budgetDao().getBudgetByMonth(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").format(today)
        ) ?: entryPoint.budgetDao().getLatestBudget().first()

        return DashboardSnapshot(
            todayExpense = expense.sumOf { it.amount },
            todayIncome = income.sumOf { it.amount },
            todayExpenseCount = expense.size,
            weekday = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            todoRemaining = (board.todayTotal - board.todayDone).coerceAtLeast(0),
            todoTotal = board.todayTotal,
            habitDone = habitRows.count { it.checked },
            habitTotal = habitRows.size,
            subDateText = nextRenewal?.let {
                today.plusDays(it.daysLeft).format(DateTimeFormatter.ofPattern("M/d"))
            },
            subDaysLeft = nextRenewal?.daysLeft,
            dailyExpense = dailyExpense,
            budgetUsedPercent = budget?.takeIf { b -> b.totalBudget > 0 && expense.isNotEmpty() }?.let { b ->
                ((expense.sumOf { it.amount }.toDouble() / b.totalBudget) * 100).toInt().coerceIn(0, 100)
            },
            budgetRemainText = budget?.takeIf { b -> b.totalBudget > 0 }?.let { b ->
                context.getString(
                    R.string.widget_budget_remain_fmt,
                    WidgetData.formatMoneyCompact(context, (b.totalBudget - expense.sumOf { it.amount }).coerceAtLeast(0))
                )
            }
        )
    }

    /** 按自然日聚合账单金额（分），旧→新共 [days] 天。 */
    private fun sumByDay(bills: List<com.palmnote.data.db.entity.Bill>, today: LocalDate, days: Long): List<Long> =
        (0 until days).map { offset ->
            val day = today.minusDays(days - 1 - offset)
            bills.filter {
                java.time.Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() == day
            }.sumOf { it.amount }
        }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        snapshot: DashboardSnapshot,
        widthDp: Int = DEFAULT_WIDTH_DP,
        heightDp: Int = DEFAULT_HEIGHT_DP,
        accent: Int? = null
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_dash_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_dash_v22_square
            WidgetSizeClass.BAR -> R.layout.widget_dash_v22_bar
            WidgetSizeClass.CHIP -> R.layout.widget_dash_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        if (size == WidgetSizeClass.TALL) {
            bindTall4x4(context, views, snapshot)
            views.setOnClickPendingIntent(
                R.id.widget_layout,
                WidgetHelper.createPendingIntent(
                    context,
                    WidgetDeepLink.SEG_DASHBOARD + appWidgetId,
                    WidgetDeepLink.TAB_DASHBOARD
                )
            )
            return views
        }
        views.setInt(R.id.widget_dash_badge, "setColorFilter", accent ?: familyD(context, 0))
        views.setTextViewText(
            R.id.widget_dash_title,
            context.getString(R.string.widget_dash_today_fmt, snapshot.weekday)
        )
        views.setTextViewText(
            R.id.widget_dash_hero,
            WidgetData.formatMoneyCompact(context, snapshot.todayExpense)
        )
        val billsText = context.getString(R.string.widget_dash_bills_fmt, snapshot.todayExpenseCount)
        val incomeText = context.getString(
            R.string.widget_dash_income_fmt,
            WidgetData.formatMoneyCompact(context, snapshot.todayIncome)
        )
        when (size) {
            WidgetSizeClass.WIDE ->
                bindWide(context, views, snapshot, billsText, incomeText)
            WidgetSizeClass.SQUARE -> bindSquare(context, views, snapshot, billsText)
            WidgetSizeClass.BAR -> bindBar(context, views, snapshot, billsText)
            WidgetSizeClass.CHIP -> bindBar(context, views, snapshot, billsText)
            else -> {}
        }
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_DASHBOARD + appWidgetId,
                WidgetDeepLink.TAB_DASHBOARD
            )
        )
        return views
    }

    /** 4×2：三枚胶囊（待办 / 打卡 / 订阅 —— 缺订阅不补位）。 */
    /**
     * 4×4 仪表盘（v37）：主数字 + 四 KPI + 近 7 天支出柱图（带轴与今天柱数值标）。
     * 空数据（今天 0 笔）不摆柱图与 0 值 KPI，给空白引导（稿 §11）。
     */
    internal fun bindTall4x4(context: Context, views: RemoteViews, snapshot: DashboardSnapshot) {
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_grid_white,
            title = context.getString(R.string.widget_dash_today_fmt, snapshot.weekday),
            meta = DateTimeFormatter.ofPattern("M/d").format(LocalDate.now()),
            familyIdx = 0
        )
        val hasExpense = snapshot.todayExpense > 0
        val avg = snapshot.todayExpenseCount.takeIf { it > 0 && hasExpense }
            ?.let { WidgetData.formatMoneyCompact(context, snapshot.todayExpense / it) }
        val heroLabel = when {
            hasExpense && avg != null -> context.getString(R.string.widget_dash_bills_fmt, snapshot.todayExpenseCount) +
                " · " + context.getString(R.string.widget_dash_avg_fmt, avg)
            else -> null
        }
        WidgetV22.bindTallHero(
            context, views,
            WidgetData.formatMoneyCompact(context, snapshot.todayExpense),
            heroLabel
        )

        // KPI：待办 / 打卡 / 订阅 / 预算（预算缺失则只摆三块）
        WidgetV22.bindKpis(context, views, dashKpis(context, snapshot))

        // 近 7 天柱图：空数据（全 0）整块 GONE，改空白引导。
        // 稿 §02：数值标在**今天**柱（128）、峰值只在头部注名（峰值 ¥420）、峰柱着橙
        val maxDaily = snapshot.dailyExpense.maxOrNull() ?: 0L
        if (maxDaily > 0L) {
            bindTallChart(context, views, snapshot, maxDaily)
        } else {
            WidgetV22.tallSection(context, views, "chart", false)
            WidgetV22.tallSection(context, views, "empty", true)
            WidgetV22.bindEmpty(
                context, views,
                iconRes = R.drawable.widget_ic_grid_white,
                title = context.getString(R.string.widget_dash_empty_title),
                hint = context.getString(R.string.widget_dash_empty_hint),
                familyIdx = 0
            )
        }
        // minichips / rows / footer / strip / grid 本族不用
        WidgetV22.tallSections(context, views, "kpis", if (maxDaily > 0L) "chart" else "empty")
    }

    /** 4×4 柱图：近 7 天支出（今天柱带数值标、峰柱着橙、头部注名峰值）。 */
    private fun bindTallChart(
        context: Context,
        views: RemoteViews,
        snapshot: DashboardSnapshot,
        maxDaily: Long
    ) {
        WidgetV22.tallSection(context, views, "chart", true)
        WidgetV22.tallSection(context, views, "empty", false)
        val peakText = WidgetData.formatMoneyCompact(context, maxDaily)
        val axis = WidgetV22.last7Axis()
        // 并列峰值只给最靠今天的一根着橙（稿每图一处峰值色）
        val peak = snapshot.dailyExpense.lastIndexOf(maxDaily)
        val bars = snapshot.dailyExpense.mapIndexed { i, v ->
            WidgetV22.ChartBar(
                percent = ((v.toDouble() / maxDaily) * 100).toInt(),
                familyIdx = if (i == peak) 4 else 0,
                label = if (i == snapshot.dailyExpense.lastIndex) {
                    WidgetData.formatChartValue(v)
                } else {
                    null
                },
                // 轴末格 = 今天：族色深墨加粗（稿 .caxis .today）
                today = i == snapshot.dailyExpense.lastIndex,
                axis = axis[i]
            )
        }
        WidgetV22.bindChart(
            context, views,
            context.getString(R.string.widget_chart_week),
            context.getString(R.string.widget_chart_peak_fmt, peakText),
            bars
        )
    }

    /** 4×4 KPI 行：待办 / 打卡 / 订阅 / 预算（预算缺失只摆三块）。 */
    private fun dashKpis(context: Context, snapshot: DashboardSnapshot): List<WidgetV22.Kpi> = buildList {
        add(
            WidgetV22.Kpi(
                context.getString(R.string.widget_todo_title_short),
                "${snapshot.todoRemaining}/${snapshot.todoTotal}",
                context.getString(R.string.widget_dash_undone),
                0
            )
        )
        add(
            WidgetV22.Kpi(
                context.getString(R.string.widget_dash_habit_label),
                "${snapshot.habitDone}/${snapshot.habitTotal}",
                null,
                1
            )
        )
        snapshot.subDaysLeft?.let {
            add(
                WidgetV22.Kpi(
                    context.getString(R.string.widget_dash_sub_label),
                    "$it",
                    context.getString(R.string.widget_dash_left_days_fmt, it),
                    3
                )
            )
        }
        snapshot.budgetUsedPercent?.let {
            add(
                WidgetV22.Kpi(
                    context.getString(R.string.widget_dash_budget_label),
                    "$it%",
                    snapshot.budgetRemainText,
                    4
                )
            )
        }
    }

    private fun bindWide(
        context: Context,
        views: RemoteViews,
        snapshot: DashboardSnapshot,
        billsText: String,
        incomeText: String
    ) {
        views.setTextViewText(R.id.widget_dash_hero_sub, "$billsText · $incomeText")
        bindChipsColumn(context, views, chipsOf(context, snapshot))
    }

    /**
     * 纵排胶囊：chip 根是横向 weight 语义（0dp+weight），直接塞进纵向 host 宽度会变 0 ——
     * 每颗先包进一行（[R.layout.widget_v22_chiprow]），行内 weight 才能把宽度撑满。
     * 末行走 [R.layout.widget_v22_chiprow_last]（无 6dp 底距）：4×2 三行正好填满中间行，
     * 尾距会把整列撑到 96dp，第三颗底部 6dp 被容器裁掉（预览图里就是切平的）。
     */
    private fun bindChipsColumn(context: Context, views: RemoteViews, chips: List<RemoteViews>) {
        views.removeAllViews(R.id.widget_dash_chips_host)
        chips.forEachIndexed { index, chip ->
            val rowLayout = if (index == chips.lastIndex) {
                R.layout.widget_v22_chiprow_last
            } else {
                R.layout.widget_v22_chiprow
            }
            val row = widgetViews(context, rowLayout)
            row.addView(R.id.widget_v22_chiprow, chip)
            views.addView(R.id.widget_dash_chips_host, row)
        }
    }

    /** 2×2：两枚胶囊（待办 / 打卡）。 */
    private fun bindSquare(context: Context, views: RemoteViews, snapshot: DashboardSnapshot, billsText: String) {
        views.setTextViewText(R.id.widget_dash_hero_sub, billsText)
        bindChipsColumn(context, views, chipsOf(context, snapshot).take(2))
    }

    /** 4×1：四列读数。 */
    private fun bindBar(
        context: Context,
        views: RemoteViews,
        snapshot: DashboardSnapshot,
        billsText: String
    ) {
        views.setTextViewText(R.id.widget_dash_bar_expense_lbl, context.getString(R.string.widget_dash_expense_short))
        views.setTextViewText(
            R.id.widget_dash_bar_expense,
            WidgetData.formatMoneyCompact(context, snapshot.todayExpense)
        )
        views.setTextViewText(R.id.widget_dash_bar_expense_sub, billsText)

        views.setTextViewText(R.id.widget_dash_bar_todo_lbl, context.getString(R.string.widget_todo_title_short))
        views.setTextViewText(R.id.widget_dash_bar_todo, "${snapshot.todoRemaining}")
        views.setTextViewText(
            R.id.widget_dash_bar_todo_sub,
            context.getString(R.string.widget_dash_todo_total_fmt, snapshot.todoTotal)
        )

        views.setTextViewText(R.id.widget_dash_bar_habit_lbl, context.getString(R.string.widget_habit_title_short))
        views.setTextViewText(
            R.id.widget_dash_bar_habit,
            context.getString(R.string.widget_habit_today_ratio, snapshot.habitDone, snapshot.habitTotal)
        )
        // 打卡列没有副信息行（5 点即可视化），副信息 TextView 留空
        // 打卡列五个点：分母 = 习惯数，≤5 才画
        views.removeAllViews(R.id.widget_dash_bar_habit_dots)
        if (snapshot.habitTotal in 1..HABIT_DOTS_MAX) {
            val dots = widgetViews(context, R.layout.widget_v22_dpd)
            WidgetV22.bindDpd(context, dots, on = snapshot.habitDone, familyIdx = 1)
            views.addView(R.id.widget_dash_bar_habit_dots, dots)
            views.setViewVisibility(R.id.widget_dash_bar_habit_dots, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_dash_bar_habit_dots, View.GONE)
        }

        views.setTextViewText(R.id.widget_dash_bar_sub_lbl, context.getString(R.string.widget_dash_sub_label))
        if (snapshot.subDaysLeft != null && snapshot.subDateText != null) {
            views.setTextViewText(
                R.id.widget_dash_bar_sub,
                "${snapshot.subDaysLeft} ${context.getString(R.string.widget_next_up_days_unit)}"
            )
            views.setTextViewText(R.id.widget_dash_bar_sub_sub, snapshot.subDateText)
        } else {
            views.setTextViewText(R.id.widget_dash_bar_sub, context.getString(R.string.widget_asset_dash))
            views.setTextViewText(R.id.widget_dash_bar_sub_sub, "")
        }
    }

    /** 2×1：hero + 右侧两行（笔数 / 收入）。 */


    /**
     * 三枚胶囊（4×2 全量 / 2×2 取前两枚）：
     * - 待办：名字 + 「共 N 项」+ 未完成数（c1）；
     * - 打卡：名字 + 5 点（≤5 习惯才画）+ 「done/total」（c2）；
     * - 订阅：名字 + 下次扣费日 + 「N 天」（c4）；没有订阅整枚不摆。
     */
    private fun chipsOf(context: Context, snapshot: DashboardSnapshot): List<RemoteViews> {
        val chips = mutableListOf<RemoteViews>()
        val todoChip = widgetViews(context, R.layout.widget_v22_chip)
        WidgetV22.bindChip(
            context, todoChip,
            name = context.getString(R.string.widget_todo_title_short),
            sub = context.getString(R.string.widget_dash_todo_total_fmt, snapshot.todoTotal),
            value = "${snapshot.todoRemaining}",
            familyIdx = 0,
            showDot = true
        )
        chips += todoChip

        val habitChip = widgetViews(context, R.layout.widget_v22_chip)
        WidgetV22.bindChip(
            context, habitChip,
            name = context.getString(R.string.widget_habit_title_short),
            sub = null,
            value = context.getString(
                R.string.widget_habit_today_ratio,
                snapshot.habitDone,
                snapshot.habitTotal
            ),
            familyIdx = 1,
            showDot = true
        )
        chips += habitChip

        if (snapshot.subDateText != null && snapshot.subDaysLeft != null) {
            val subChip = widgetViews(context, R.layout.widget_v22_chip)
            WidgetV22.bindChip(
                context, subChip,
                name = context.getString(R.string.widget_dash_sub_label),
                sub = snapshot.subDateText,
                value = "${snapshot.subDaysLeft} ${context.getString(R.string.widget_next_up_days_unit)}",
                familyIdx = 3,
                showDot = true
            )
            chips += subChip
        }
        return chips
    }

    private fun familyD(context: Context, index: Int) = WidgetV22.family(context, index).d

    private companion object {
        /** 组件声明的默认尺寸（4×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 打卡列 5 点的分母上限（习惯数 ≤5 才画）。 */
        const val HABIT_DOTS_MAX = 5
    }
}
