package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DashboardWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): com.palmnote.data.db.dao.BillDao
        fun budgetDao(): com.palmnote.data.db.dao.BudgetDao
        fun goalDao(): com.palmnote.data.db.dao.GoalDao
        fun lifeItemDao(): com.palmnote.data.db.dao.LifeItemDao
        fun lifeTemplateDao(): com.palmnote.data.db.dao.LifeTemplateDao
        fun anniversaryDao(): com.palmnote.data.db.dao.AnniversaryDao
        fun preferencesManager(): com.palmnote.data.datastore.PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val snapshot = fetchSnapshot(context, entryPoint)
        val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, opacity ->
            // 版式不再随尺寸分档（大档那一行已并进「待办 + 纪念日」），直接一份 RemoteViews
            bindDashboard(context, appWidgetId, snapshot, accent)
        }
    }

    /** 构建概览：大号弧形仪表（预算用量）+ 三行微统计。 */
    private fun bindDashboard(
        context: Context,
        appWidgetId: Int,
        snapshot: DashboardSnapshot,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        bindSnapshot(context, views, snapshot)
        // 三层同心环（苹果活动环做法）：外=预算、中=目标、内=待办完成度。
        // 半径由控件尺寸定，粗细在 drawable 里；进度走 setImageLevel，颜色走 setColorFilter，
        // 轨道用同色相低透明度（不是中性灰）
        val families = (0..3).map { WidgetData.colorFamily(context, it) }
        views.setInt(R.id.widget_dashboard_icon_bg, "setColorFilter", accent.accent)
        // 四张色片：淡底 + 同族色点 + 同族数值
        val chips = listOf(
            Triple(R.id.widget_dashboard_budget_tint, R.id.widget_dashboard_budget_dot, R.id.widget_dashboard_budget),
            Triple(R.id.widget_dashboard_goal_tint, R.id.widget_dashboard_goal_dot, R.id.widget_dashboard_goal_pct),
            Triple(R.id.widget_dashboard_todo_tint, R.id.widget_dashboard_todo_dot, R.id.widget_dashboard_todo),
            Triple(R.id.widget_dashboard_anniversary_tint, R.id.widget_dashboard_anniversary_dot, R.id.widget_dashboard_days)
        )
        chips.forEachIndexed { index, (tintId, dotId, valueId) ->
            views.setInt(tintId, "setColorFilter", families[index].tint)
            views.setInt(dotId, "setColorFilter", families[index].hue)
            views.setTextColor(valueId, families[index].hue)
        }
        // 三层环：环色与对应色片同族
        bindRing(
            views, R.id.widget_dashboard_budget_ring_track, R.id.widget_dashboard_budget_ring,
            families[0].hue, snapshot.budgetPercent
        )
        bindRing(
            views, R.id.widget_dashboard_goal_ring_track, R.id.widget_dashboard_goal_ring,
            families[1].hue, snapshot.goalPercent
        )
        bindRing(
            views, R.id.widget_dashboard_todo_ring_track, R.id.widget_dashboard_todo_ring,
            families[2].hue, todoPercent(snapshot)
        )
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_DASHBOARD + appWidgetId, WidgetDeepLink.TAB_DASHBOARD)
        )
        bindCardClicks(context, views, appWidgetId)
        return views
    }

    /**
     * 分区热区：四张卡各自深链，子卡点击不再整块落回首页。
     * 预算卡 → 预算页；待办卡 → 生活页的今日清单；目标/纪念卡 → 生活页。
     */
    internal fun bindCardClicks(context: Context, views: RemoteViews, appWidgetId: Int) {
        for (target in cardTargets()) {
            val requestCode = target.segment + appWidgetId
            val pendingIntent = if (target.listMode != null) {
                WidgetHelper.createLifeListPendingIntent(context, requestCode, target.listMode)
            } else {
                WidgetHelper.createPendingIntent(context, requestCode, target.tab)
            }
            views.setOnClickPendingIntent(target.viewId, pendingIntent)
        }
    }

    /**
     * 卡片 viewId → 深链号段 + 目标页（可选生活清单模式）。
     * 号段必须互不相同，否则 FLAG_UPDATE_CURRENT 会让点击互相覆盖。
     */
    internal data class CardTarget(
        val viewId: Int,
        val segment: Int,
        val tab: String,
        val listMode: String? = null
    )

    internal fun cardTargets(): List<CardTarget> = listOf(
        CardTarget(R.id.widget_dashboard_budget_card, WidgetDeepLink.SEG_DASHBOARD_BUDGET, WidgetDeepLink.TAB_BUDGET),
        CardTarget(R.id.widget_dashboard_goal_card, WidgetDeepLink.SEG_DASHBOARD_GOAL, WidgetDeepLink.TAB_LIFE),
        // 待办卡看的是「今日还有几项」→ 直接落到今日清单（LifeFullListMode.AGENDA），不落首页
        CardTarget(
            R.id.widget_dashboard_todo_card,
            WidgetDeepLink.SEG_DASHBOARD_TODO,
            WidgetDeepLink.TAB_LIFE,
            listMode = "AGENDA"
        ),
        // 纪念日与待办并成同一行显示，热区也共用待办那一格（都落生活页）
        CardTarget(R.id.widget_dashboard_todo_card, WidgetDeepLink.SEG_DASHBOARD_ANNIVERSARY, WidgetDeepLink.TAB_LIFE)
    )

    internal data class DashboardSnapshot(
        val dateText: String,
        val budgetCardAmount: String,
        val budgetCardSub: String,
        val goalPct: String,
        val goalName: String,
        val todoRemaining: Int,
        val anniversaryTitle: String?,
        val anniversaryDays: Long,
        /** 预算用量百分比；无预算为 null（环隐藏）。 */
        val budgetPercent: Int? = null,
        /** 目标完成度百分比；无目标为 null（环隐藏，大号百分比文案继续显示）。 */
        val goalPercent: Int? = null
    )

    private suspend fun fetchSnapshot(context: Context, entryPoint: WidgetEntryPoint): DashboardSnapshot {
        val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
        val monthlyExpense = entryPoint.billDao().getMonthlyExpense(yearMonth).first() ?: 0L
        val monthlyIncome = entryPoint.billDao().getMonthlyIncome(yearMonth).first() ?: 0L
        val budget = entryPoint.budgetDao().getBudgetByMonth(yearMonth)
            ?: entryPoint.budgetDao().getLatestBudget().first()

        // 预算卡：有预算显示剩余，无预算显示结余
        val budgetCard = if (budget != null && budget.totalBudget > 0) {
            WidgetData.formatMoneyCompact(context, budget.totalBudget - monthlyExpense) to R.string.widget_budget_remaining
        } else {
            WidgetData.formatMoneyCompact(context, monthlyIncome - monthlyExpense) to R.string.widget_net_income
        }
        // 预算用量环：只在真有预算时给值（无预算时卡片显示的是结余，没有用量可表）
        val budgetPercent = if (budget != null && budget.totalBudget > 0) {
            (monthlyExpense * 100 / budget.totalBudget).toInt().coerceIn(0, 100)
        } else {
            null
        }

        val goals = fetchGoalProgress(context, entryPoint)

        val todos = WidgetData.fetchTodayTodos(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = entryPoint.preferencesManager().lifeDemoMode.first(),
            demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        )

        val nextAnniversary = entryPoint.anniversaryDao().getAllAnniversaries().first()
            .mapNotNull { ann ->
                WidgetData.nextOccurrenceDaysIn(ann.solarDate, ann.isYearly)?.let { days -> ann.title to days }
            }
            .minByOrNull { it.second }

        return DashboardSnapshot(
            dateText = LocalDate.now().format(DateTimeFormatter.ofPattern(context.getString(R.string.widget_date_format_cn))),
            budgetCardAmount = budgetCard.first,
            budgetCardSub = context.getString(budgetCard.second),
            goalPct = goals.pctText,
            goalName = goals.name,
            todoRemaining = todos.count { it.status != "COMPLETED" },
            anniversaryTitle = nextAnniversary?.first,
            anniversaryDays = nextAnniversary?.second ?: -1L,
            budgetPercent = budgetPercent,
            goalPercent = goals.percent
        )
    }

    /** 目标进度：文案、名称与环形进度（无目标时百分比为 null，环隐藏）。 */
    private suspend fun fetchGoalProgress(context: Context, entryPoint: WidgetEntryPoint): GoalProgress {
        val allGoals = entryPoint.goalDao().getAllGoals().first()
        val activeGoal = allGoals.firstOrNull { it.currentCount < it.totalCount }
        return when {
            activeGoal != null -> {
                val pct = if (activeGoal.totalCount > 0) {
                    activeGoal.currentCount * 100 / activeGoal.totalCount
                } else {
                    0
                }
                GoalProgress("$pct%", activeGoal.title, pct.coerceIn(0, 100))
            }
            allGoals.isNotEmpty() -> GoalProgress(
                "100%",
                context.getString(R.string.widget_goal_all_done),
                100
            )
            else -> GoalProgress("--", context.getString(R.string.widget_no_goals), null)
        }
    }

    private data class GoalProgress(val pctText: String, val name: String, val percent: Int?)


    /**
     * 一层环：轨道用同色相淡化（不是中性灰，苹果活动环的做法）、进度弧用同色相实色，
     * 进度走 setImageLevel（满分 10000）。id 直接传，避免运行时查资源名。
     */
    private fun bindRing(views: RemoteViews, trackId: Int, arcId: Int, hue: Int, percent: Int?) {
        val faint = (hue and 0x00FFFFFF) or (ringTrackAlpha shl 24)
        views.setInt(trackId, "setColorFilter", faint)
        views.setInt(arcId, "setColorFilter", hue)
        views.setInt(arcId, "setImageLevel", ((percent ?: 0) * 100).coerceIn(0, 10_000))
    }

    /** 轨道透明度：同色相 26% 左右，深浅底上都像"同色但暗一档"。 */
    private val ringTrackAlpha = 0x42

    /** 今日待办完成度：没有待办时给 0。 */
    private fun todoPercent(snapshot: DashboardSnapshot): Int =
        if (snapshot.todoRemaining <= 0) 100 else 0

    internal fun bindSnapshot(context: Context, views: RemoteViews, snapshot: DashboardSnapshot) {
        views.setTextViewText(R.id.widget_dashboard_date, snapshot.dateText)
        // 环中心的数字取第一个有数据的百分比（预算 → 目标 → 待办），三个都没有才显示 --
        val ringPercent = snapshot.budgetPercent ?: snapshot.goalPercent ?: todoPercent(snapshot)
        views.setTextViewText(
            R.id.widget_dashboard_ring_text,
            ringPercent?.let { "$it%" } ?: "--"
        )
        views.setTextViewText(R.id.widget_dashboard_budget, snapshot.budgetCardAmount)
        views.setTextViewText(R.id.widget_dashboard_budget_sub, snapshot.budgetCardSub)
        views.setTextViewText(R.id.widget_dashboard_goal_pct, snapshot.goalPct)
        views.setTextViewText(R.id.widget_dashboard_goal_name, snapshot.goalName)

        views.setTextViewText(R.id.widget_dashboard_todo, "${snapshot.todoRemaining}")

        // 纪念日砖：数值带上单位（砖里只有标签 + 数值两行，不再单开一行说明）
        if (snapshot.anniversaryTitle != null) {
            views.setTextViewText(R.id.widget_dashboard_days_title, snapshot.anniversaryTitle)
            views.setTextViewText(
                R.id.widget_dashboard_days,
                "${snapshot.anniversaryDays} ${formatAnniversarySub(context, snapshot.anniversaryDays)}"
            )
        } else {
            views.setTextViewText(R.id.widget_dashboard_days_title, context.getString(R.string.widget_no_anniversary))
            views.setTextViewText(R.id.widget_dashboard_days, "--")
        }

        // 预算用量百分比并进「预算剩余」那块砖的标签
        snapshot.budgetPercent?.let { percent ->
            views.setTextViewText(
                R.id.widget_dashboard_budget_sub,
                context.getString(R.string.widget_budget_remaining) + " · " + percent + "%"
            )
        }
    }

    /** 砖里的单位词：数字在主角位置，这里只给「天后 / days」这种量词。 */
    private fun formatAnniversarySub(context: Context, days: Long): String = if (days == 0L) {
        context.getString(R.string.widget_today)
    } else {
        // 复数走 getQuantityString（en 要区分 day/days）
        context.resources.getQuantityString(R.plurals.widget_days_unit, days.toInt(), days)
    }

}
