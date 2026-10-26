package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.StreakEngine
import com.palmnote.domain.util.getKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 今日打卡组件（v22）：hero「今日 x/y」+ 每习惯的近 7 天打盘点阵。
 * 点阵行的整行点击＝给该习惯打今天的卡（[HabitCheckInReceiver]）。
 *
 * 与生活页详情**同一份数据**（LifeItem + LifeTemplate）：已打卡 = 今天该模板有一条非
 * ARCHIVED 的行；连击用 [StreakEngine] 按 [LifeItemDao.getDistinctCheckInDays] 的天数算，
 * 与详情页/统计页口径一致。
 *
 * 四档构图按 [WidgetSizeClass] 切档：
 * - WIDE 4×2：三行习惯胶囊（名字 + 行内七天点，一格一天）；
 * - SQUARE 2×2：hero + 五个大点（一格一习惯）+ 完成项名 + 连续天数；
 * - BAR 4×1：三列读数（今日 / 连续 / 焦点习惯本周），两列带刻度；
 * - TALL 4×4（v37 §2-6）：hero「今日 x/y」+ 四 KPI（今日/本周/最佳/连续）+
 *   近 7 天完成柱图（每日合计，峰值橙）+ footer 最佳习惯。
 *
 * 点阵口径（用户 2026-10-23 定：分母固定才画点）：七天点恒画（一周 7 天是固定分母）；
 * 「今日 x/y」的点/刻度分母是**用户的习惯数**，≤5 才画（2×2/2×1 的五点）、≤7 才画
 * （4×1 的刻度）—— 超了就整行收起，数字照样给，不虚构分母。
 */
// open：尺寸变体（HabitMini/HabitWide）薄子类继承，复用同一套取数与切档逻辑；其余尺寸由 WidgetSizeClass 在 bindViews 内归档
open class HabitWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeTemplateDao(): LifeTemplateDao
        fun lifeItemDao(): LifeItemDao
        fun preferencesManager(): PreferencesManager
    }

    /**
     * 4×4（v37 §2-6）：hero「今日 x/y」+ 四 KPI + 近 7 天完成柱图（按天合计，峰值橙）+
     * footer 最佳习惯（本周 x/7）。0 习惯走空白态。
     */
    private fun bindTall4x4(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        val done = rows.count { it.checked }
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_calendar_white,
            title = context.getString(R.string.widget_habit_title),
            meta = null,
            familyIdx = 1
        )
        if (rows.isEmpty()) {
            WidgetV22.bindTallHero(context, views, "0/0", null)
            WidgetV22.bindEmpty(
                context, views,
                iconRes = R.drawable.widget_ic_calendar_white,
                title = context.getString(R.string.widget_habit_empty),
                hint = context.getString(R.string.widget_dash_empty_hint),
                familyIdx = 1
            )
            WidgetV22.tallSections(context, views, "empty")
            return
        }
        WidgetV22.bindTallHero(
            context, views,
            context.getString(R.string.widget_habit_today_ratio, done, rows.size),
            null
        )
        bindTallKpis(context, views, rows)
        bindTallChart(context, views, rows)
        bindTallFooter(context, views, rows)
    }


    /** 4×4 KPI：今日 x/y / 本周合计 / 最佳 x/7 / 连续天数。 */
    private fun bindTallKpis(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        val done = rows.count { it.checked }
        val weekSum = rows.sumOf { it.weekHits }
        val best = rows.maxByOrNull { it.weekHits }
        val topStreak = rows.maxByOrNull { it.streak }
        WidgetV22.bindKpis(
            context, views,
            listOf(
                WidgetV22.Kpi(
                    context.getString(R.string.widget_habit_label_today),
                    context.getString(R.string.widget_habit_today_ratio, done, rows.size),
                    null,
                    1
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_habit_label_week),
                    "$weekSum",
                    null,
                    0
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_habit_label_best),
                    best?.let { context.getString(R.string.widget_habit_row_week, it.weekHits) } ?: "—",
                    best?.name,
                    4
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_habit_label_streak),
                    "${topStreak?.streak ?: 0}",
                    null,
                    3
                )
            )
        )
    }

    /** 4×4 柱图：近 7 天完成（每日合计 = 当天打了卡的习惯数；峰值橙 + 数值标；全 0 收起）。 */
    private fun bindTallChart(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        val daily = (0 until RECENT_DAYS).map { day -> rows.count { it.recent.getOrElse(day) { false } } }
        val max = daily.maxOrNull() ?: 0
        // 并列峰值只认**最靠今天**的那一根：否则同高的柱全着橙、全带标（稿只给一根）
        val peak = daily.lastIndexOf(max)
        if (max > 0) {
            val bars = daily.mapIndexed { i, v ->
                WidgetV22.ChartBar(
                    percent = v * 100 / max,
                    familyIdx = if (i == peak) 4 else 1,
                    label = if (i == peak) "$v" else null,
                    axis = WidgetV22.last7Axis()[i],
                    // 轴末格 = 今天
                    today = i == RECENT_DAYS - 1
                )
            }
            WidgetV22.bindChart(
                context, views,
                context.getString(R.string.widget_habit_chart_recent),
                null,
                bars
            )
        }
    }

    /** 4×4 footer：最佳习惯 + 次佳（稿「早起 7/7 · 阅读 5/7」）。 */
    private fun bindTallFooter(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        val ranked = rows.sortedByDescending { it.weekHits }
        val first = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        if (first != null && first.weekHits > 0) {
            WidgetV22.bindFooter(
                context, views,
                left = context.getString(R.string.widget_habit_label_best),
                value = context.getString(R.string.widget_habit_row_week, first.weekHits)
                    .let { "${first.name} $it" },
                right = second?.takeIf { it.weekHits > 0 }
                    ?.let { "${it.name} ${context.getString(R.string.widget_habit_row_week, it.weekHits)}" }
            )
        }
        WidgetV22.tallSections(
            context, views,
            "kpis",
            "chart",
            if (first != null && first.weekHits > 0) "footer" else ""
        )
    }

    /** 一行打卡：模板 + 今天是否已打卡 + 当前连击 + 近 7 天 + 周历统计。 */
    internal data class HabitRow(
        val templateId: Long,
        val name: String,
        val checked: Boolean,
        val streak: Int,
        /** 近 7 天（从 6 天前到今天）是否打卡 —— 行内点阵的一行 */
        val recent: List<Boolean> = emptyList(),
        /** 本月已打卡天数（hero 副标用） */
        val monthCount: Int = 0,
        /** 历史最长连击（底部指标用） */
        val longest: Int = 0,
        /** 本周（周一到今天）已打卡天数（行尾 x/7 与完成率用） */
        val weekHits: Int = 0
    )

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext, WidgetEntryPoint::class.java
        )
        val preferences = entryPoint.preferencesManager()
        val rows = fetchHabitRows(
            context = appContext,
            templateDao = entryPoint.lifeTemplateDao(),
            itemDao = entryPoint.lifeItemDao(),
            includeDemo = preferences.lifeDemoMode.first()
        )

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                bindViews(context, appWidgetId, rows, width, height)
            }
        }
    }

    /**
     * 打卡模板 → 每行状态。连击要按模板取一次「打卡天集合」，所以这里逐模板查；
     * 模板数量级在十位以内，且整轮刷新只跑一次（不是每个组件实例各跑一遍）。
     */
    internal suspend fun fetchHabitRows(
        context: Context,
        templateDao: LifeTemplateDao,
        itemDao: LifeItemDao,
        includeDemo: Boolean,
        today: LocalDate = LocalDate.now()
    ): List<HabitRow> = habitTemplates(templateDao).map { template ->
        val days = itemDao.getDistinctCheckInDays(template.id, includeDemo, LIFE_DEMO_META).first()
        // 周口径：周一到今天（yyyy-MM-dd 字符串可直接比较）
        val weekStart = today.with(java.time.DayOfWeek.MONDAY).toString()
        val streakResult = StreakEngine.compute(days, today)
        HabitRow(
            templateId = template.id,
            name = template.getDisplayName(context),
            checked = days.contains(today.toString()),
            streak = streakResult.current,
            // 从 6 天前排到今天，最后一格就是今天
            recent = (RECENT_DAYS - 1 downTo 0).map { days.contains(today.minusDays(it.toLong()).toString()) },
            monthCount = days.count { it.startsWith(today.toString().substring(0, 7)) },
            longest = streakResult.longest,
            weekHits = days.count { it >= weekStart && it <= today.toString() }
        )
    }

    private suspend fun habitTemplates(dao: LifeTemplateDao): List<LifeTemplate> =
        dao.getAllVisibleTemplates().first().filter { it.getKind() == LifeTemplateKind.HABIT }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        rows: List<HabitRow>,
        widthDp: Int = DEFAULT_WIDTH_DP,
        heightDp: Int = DEFAULT_HEIGHT_DP
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_habit_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_habit_v22_square
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> R.layout.widget_habit_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        // 整卡出口四档统一挂（行点击挂在行自己的 id 上，与 widget_layout 不冲突）
        WidgetHelper.linkViews(context, views, WidgetDeepLink.SEG_HABIT + appWidgetId, WidgetDeepLink.TAB_LIFE)
        if (size == WidgetSizeClass.TALL) {
            bindTall4x4(context, views, rows)
            return views
        }
        bindChrome(context, views, rows)
        when (size) {
            WidgetSizeClass.WIDE -> bindWide(context, views, rows)
            WidgetSizeClass.SQUARE -> bindSquare(context, views, rows)
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> bindBar(context, views, rows)
            WidgetSizeClass.TALL -> {} // 上面已提前 return
        }
        return views
    }


    /** 四档共用的头部：徽章（族色 c5 橙，稿「今日打卡」标识色）+ 标题 + hero「今日 x/y」。 */
    private fun bindChrome(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        views.setInt(R.id.widget_habit_badge, "setColorFilter", familyD(context, 4))
        views.setTextViewText(
            R.id.widget_habit_hero,
            context.getString(R.string.widget_habit_today_ratio, rows.count { it.checked }, rows.size)
        )
        views.setViewVisibility(R.id.widget_habit_hero, if (rows.isEmpty()) View.GONE else View.VISIBLE)
    }

    /** 4×2：三行习惯胶囊（名字 + 行内七天点），整行是打卡热区。 */
    private fun bindWide(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        views.setTextViewText(R.id.widget_habit_title, context.getString(R.string.widget_habit_title))
        val shown = rows.take(MAX_ROWS)
        views.removeAllViews(R.id.widget_habit_rows_host)
        if (shown.isEmpty()) {
            views.setViewVisibility(R.id.widget_habit_rows_host, View.GONE)
            views.setViewVisibility(R.id.widget_habit_empty, View.VISIBLE)
            return
        }
        views.setViewVisibility(R.id.widget_habit_rows_host, View.VISIBLE)
        views.setViewVisibility(R.id.widget_habit_empty, View.GONE)
        shown.forEachIndexed { index, row ->
            val rowView = widgetViews(context, R.layout.widget_habit_v22_row)
            val familyIdx = ROW_FAMILIES[index % ROW_FAMILIES.size]
            rowView.setInt(R.id.widget_habit_row_bg, "setColorFilter", familyTint(context, familyIdx))
            rowView.setTextViewText(R.id.widget_habit_row_name, row.name)
            val dots = widgetViews(context, R.layout.widget_v22_dotr7)
            bindWeekDots(context, dots, row.recent, familyIdx)
            rowView.addView(R.id.widget_habit_row_dots, dots)
            rowView.setOnClickPendingIntent(
                R.id.widget_habit_row,
                HabitCheckInReceiver.checkInPendingIntent(context, row.templateId)
            )
            views.addView(R.id.widget_habit_rows_host, rowView)
        }
    }

    /** 2×2：hero + 五个大点 + 完成项名 + 连续天数。 */
    private fun bindSquare(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        views.setTextViewText(R.id.widget_habit_title, context.getString(R.string.widget_habit_title_short))
        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_habit_dots_host, View.GONE)
            views.setViewVisibility(R.id.widget_habit_done_names, View.GONE)
            views.setViewVisibility(R.id.widget_habit_streak, View.GONE)
            views.setViewVisibility(R.id.widget_habit_empty, View.VISIBLE)
            return
        }
        views.setViewVisibility(R.id.widget_habit_empty, View.GONE)
        // 五个大点：一格一习惯，习惯数 >5 收起（分母不虚构）
        if (rows.size <= DOTS_SLOTS) {
            val dots = widgetViews(context, R.layout.widget_v22_dotslg)
            bindDotslg(context, dots, rows.size, rows.count { it.checked })
            views.removeAllViews(R.id.widget_habit_dots_host)
            views.addView(R.id.widget_habit_dots_host, dots)
            views.setViewVisibility(R.id.widget_habit_dots_host, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_habit_dots_host, View.GONE)
        }
        // 完成项名：今天打了卡的习惯，中点相连；一个都没有就收起整行
        val doneNames = rows.filter { it.checked }.map { it.name }
        if (doneNames.isEmpty()) {
            views.setViewVisibility(R.id.widget_habit_done_names, View.GONE)
        } else {
            views.setTextViewText(
                R.id.widget_habit_done_names,
                context.getString(R.string.widget_habit_done_names, doneNames.joinToString(DOT_SEP))
            )
            views.setViewVisibility(R.id.widget_habit_done_names, View.VISIBLE)
        }
        // 连续天数：连击最长的习惯的当前连击（着卡族 ink）
        val top = rows.maxByOrNull { it.streak }
        if (top == null || top.streak <= 0) {
            views.setViewVisibility(R.id.widget_habit_streak, View.GONE)
        } else {
            views.setTextViewText(
                R.id.widget_habit_streak,
                context.getString(R.string.widget_habit_streak_days, top.streak)
            )
            views.setTextColor(R.id.widget_habit_streak, familyInk(context, 4))
            views.setViewVisibility(R.id.widget_habit_streak, View.VISIBLE)
        }
    }

    /** 4×1：三列读数（今日 / 连续 / 焦点习惯本周），今日列与焦点列带刻度。 */
    private fun bindBar(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        views.setTextViewText(R.id.widget_habit_title, context.getString(R.string.widget_habit_title))
        val todayDone = rows.count { it.checked }
        views.setTextViewText(
            R.id.widget_habit_bar_today,
            context.getString(R.string.widget_habit_today_ratio, todayDone, rows.size)
        )
        // 今日刻度：一格 = 一个习惯（分母 = 习惯数，>7 收起）
        views.removeAllViews(R.id.widget_habit_bar_today_ticks)
        if (rows.size in 1..TICK_SLOTS) {
            val ticks = widgetViews(context, R.layout.widget_v22_ticks)
            WidgetV22.bindTicks(context, ticks, total = rows.size, on = todayDone, familyIdx = 0)
            views.addView(R.id.widget_habit_bar_today_ticks, ticks)
            views.setViewVisibility(R.id.widget_habit_bar_today_ticks, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_habit_bar_today_ticks, View.GONE)
        }
        // 连续：连击最长的习惯的当前连击
        val top = rows.maxByOrNull { it.streak }
        views.setTextViewText(R.id.widget_habit_bar_streak, "${top?.streak ?: 0}")
        // 焦点习惯列：本周 x/7 + 7 格刻度（一周是固定分母，恒画）
        if (top == null) {
            views.setTextViewText(R.id.widget_habit_bar_top_lbl, context.getString(R.string.widget_asset_dash))
            views.setTextViewText(R.id.widget_habit_bar_top, context.getString(R.string.widget_asset_dash))
            views.setViewVisibility(R.id.widget_habit_bar_top_ticks, View.GONE)
        } else {
            views.setTextViewText(R.id.widget_habit_bar_top_lbl, top.name)
            views.setTextViewText(
                R.id.widget_habit_bar_top,
                context.getString(R.string.widget_habit_row_week, top.weekHits)
            )
            val ticks = widgetViews(context, R.layout.widget_v22_ticks)
            WidgetV22.bindTicks(context, ticks, total = RECENT_DAYS, on = top.weekHits, familyIdx = 0)
            views.removeAllViews(R.id.widget_habit_bar_top_ticks)
            views.addView(R.id.widget_habit_bar_top_ticks, ticks)
            views.setViewVisibility(R.id.widget_habit_bar_top_ticks, View.VISIBLE)
        }
    }

    /** 2×1：分数徽章 + 五个点（一格一习惯，≤5 才画）。 */


    /** 行内七天点：亮点 = 那天打了卡（着族色），暗点着 outline 灰。 */
    private fun bindWeekDots(context: Context, dots: RemoteViews, recent: List<Boolean>, familyIdx: Int) {
        val on = familyD(context, familyIdx)
        val off = emptySlot(context)
        DOT7_IDS.forEachIndexed { index, id ->
            val checked = recent.getOrElse(index) { false }
            dots.setInt(id, "setColorFilter", if (checked) on else off)
        }
    }

    /** 五个大点：前 [on] 个亮点（今天已打卡数），其余暗点。 */
    private fun bindDotslg(context: Context, dots: RemoteViews, total: Int, on: Int) {
        val hue = familyD(context, 0)
        val off = emptySlot(context)
        DOTS5_IDS.forEachIndexed { index, id ->
            dots.setInt(id, "setColorFilter", if (index < on) hue else off)
            dots.setViewVisibility(id, if (index < total) View.VISIBLE else View.GONE)
        }
    }

    /** 点阵暗点：outline 灰（与 WidgetV22.bindDpd 同源，行内自用版）。 */
    private fun emptySlot(context: Context) = context.getColor(R.color.widget_v22_outline)

    private fun familyD(context: Context, index: Int) = WidgetV22.family(context, index).d

    private fun familyTint(context: Context, index: Int) = WidgetV22.family(context, index).tint

    private fun familyInk(context: Context, index: Int) = WidgetV22.family(context, index).ink


    companion object {
        /** 近 7 天（行内点阵的格数、周口径分母）。 */
        const val RECENT_DAYS = 7

        /** 4×2 最多列几个习惯（稿口径三行）。 */
        const val MAX_ROWS = 3

        /** 2×2/2×1 的大点格数；4×1 今日刻度的格数上限。 */
        const val DOTS_SLOTS = 5
        const val TICK_SLOTS = 7

        /** 组件声明的默认尺寸（4×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 完成项名之间的分隔（稿「早起 · 阅读」）。 */
        const val DOT_SEP = " · "

        /** 胶囊行色（稿内取色）：橙 / 蓝 / 青 —— 与徽章橙同族轮转。 */
        val ROW_FAMILIES = intArrayOf(4, 0, 6)

        val DOT7_IDS = listOf(
            R.id.widget_v22_dotr7_1, R.id.widget_v22_dotr7_2, R.id.widget_v22_dotr7_3,
            R.id.widget_v22_dotr7_4, R.id.widget_v22_dotr7_5, R.id.widget_v22_dotr7_6,
            R.id.widget_v22_dotr7_7
        )

        val DOTS5_IDS = listOf(
            R.id.widget_v22_dotslg_1, R.id.widget_v22_dotslg_2, R.id.widget_v22_dotslg_3,
            R.id.widget_v22_dotslg_4, R.id.widget_v22_dotslg_5
        )
    }
}
