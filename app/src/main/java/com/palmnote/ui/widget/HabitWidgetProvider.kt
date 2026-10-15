package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
 * 今日打卡组件（定稿）：左栏给连击最长的那个习惯的连续天数，右侧是「习惯 × 近 7 天」点阵。
 * 点阵行的整行点击＝给该习惯打今天的卡。
 *
 * 与生活页详情**同一份数据**（LifeItem + LifeTemplate）：已打卡 = 今天该模板有一条非
 * ARCHIVED 的行；连击用 [StreakEngine] 按 [LifeItemDao.getDistinctCheckInDays] 的天数算，
 * 与详情页/统计页口径一致。
 */
// open：尺寸变体（HabitMini/HabitWide/HabitNarrow）薄子类继承，复用同一套取数与切档逻辑
open class HabitWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeTemplateDao(): LifeTemplateDao
        fun lifeItemDao(): LifeItemDao
        fun preferencesManager(): PreferencesManager
    }

    /** 一行打卡：模板 + 今天是否已打卡 + 当前连击 + 近 7 天 + 周历统计。 */
    internal data class HabitRow(
        val templateId: Long,
        val name: String,
        val checked: Boolean,
        val streak: Int,
        /** 近 7 天（从 6 天前到今天）是否打卡 —— 方柱矩阵的一行 */
        val recent: List<Boolean> = emptyList(),
        /** 本月已打卡天数（hero 副标用） */
        val monthCount: Int = 0,
        /** 历史最长连击（底部指标用） */
        val longest: Int = 0,
        /** 本周（周一到今天）已打卡天数（行尾 x/7 与完成率用） */
        val weekHits: Int = 0
    )

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val preferences = entryPoint.preferencesManager()
        val rows = fetchHabitRows(
            context = context,
            templateDao = entryPoint.lifeTemplateDao(),
            itemDao = entryPoint.lifeItemDao(),
            includeDemo = preferences.lifeDemoMode.first()
        )

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            val today = LocalDate.now()
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                when {
                    width < NARROW_WIDTH_DP -> bindNarrowViews(context, appWidgetId, rows)
                    height < STRIP_HEIGHT_DP && width >= WIDE_WIDTH_DP ->
                        bindWideViews(context, appWidgetId, rows)
                    height < STRIP_HEIGHT_DP -> bindMinimalViews(context, appWidgetId, rows)
                    else -> bindViews(context, appWidgetId, rows, today)
                }
            }
        }
    }

    /**
     * 打卡模板 → 每行状态。连击要按模板取一次「打卡天集合」，所以这里逐模板查；
     * 模板数量级在十位以内，且整轮刷新只跑一次（不是每个组件实例各跑一遍）。
     */
    private suspend fun fetchHabitRows(
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
        today: LocalDate = LocalDate.now()
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_habit_unified)
        // 组件自身的徽章与完成度用第一个色族（装饰色不占全局强调色）
        val widgetFamily = WidgetData.colorFamily(context, 0)
        views.setInt(R.id.widget_habit_badge, "setColorFilter", widgetFamily.hue)
        views.setTextColor(R.id.widget_habit_count, widgetFamily.hue)
        views.setTextViewText(R.id.widget_habit_count, "${rows.count { it.checked }}/${rows.size}")
        views.setViewVisibility(R.id.widget_habit_count, if (rows.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(
            R.id.widget_habit_count_label,
            if (rows.isEmpty()) View.GONE else View.VISIBLE
        )
        views.setViewVisibility(R.id.widget_habit_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_habit_weekhead, if (rows.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_habit_grid, if (rows.isEmpty()) View.GONE else View.VISIBLE)

        bindHero(context, views, rows)

        // 列头：近 7 天的「日」数字；今天那格是列头顶 —— 上圆角淡底（.tct）+ 色族色数字
        // （TextView 没有 setColorFilter，淡底落在独立 ImageView 上）
        bindWeekHead(views, today, widgetFamily)

        // 方柱矩阵：一行一个习惯，行本身是打卡热区
        views.removeAllViews(R.id.widget_habit_grid)
        val shown = rows.take(MAX_ROWS)
        shown.forEachIndexed { index, row ->
            views.addView(R.id.widget_habit_grid, dotsRow(context, row, index, isLast = index == shown.lastIndex))
        }

        // 底部三指标：最长连击 / 本周完成率 / 今日待打卡
        bindFooterStats(context, views, shown, rows)

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_HABIT + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        return views
    }

    /**
     * 左栏 Hero：连击最长的那个习惯（数字着族色，名字跟稿子走主文字色并补「· 连续打卡」后缀），
     * 副标给「距离最长记录还差 X 天」（当前连击本身就是记录时改说「已是最长记录」），
     * 最右侧挂同一习惯的本月打卡天数；一个习惯都没有时收起本月块，Hero 交给布局里的空态文案。
     */
    private fun bindHero(context: Context, views: RemoteViews, rows: List<HabitRow>) {
        val topIndex = rows.indices.maxByOrNull { rows[it].streak }
        val top = topIndex?.let { rows[it] }
        if (top == null) {
            views.setViewVisibility(R.id.widget_habit_month_box, View.GONE)
            return
        }
        val topFamily = WidgetData.colorFamily(context, topIndex)
        views.setTextViewText(R.id.widget_habit_streak, "${top.streak}")
        views.setTextColor(R.id.widget_habit_streak, topFamily.hue)
        // 习惯只有一两条时卡片会空掉一大半：连击数字按行数放大，把 Hero 撑起来
        views.setTextViewTextSize(
            R.id.widget_habit_streak,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            when {
                rows.size <= 2 -> STREAK_SP_FEW
                rows.size <= 4 -> STREAK_SP_SOME
                else -> STREAK_SP_MANY
            }
        )
        views.setTextViewText(
            R.id.widget_habit_streak_name,
            "${top.name} · ${context.getString(R.string.widget_habit_streak_label)}"
        )
        val gap = (top.longest - top.streak).coerceAtLeast(0)
        views.setTextViewText(
            R.id.widget_habit_streak_sub,
            if (gap == 0) {
                context.getString(R.string.widget_habit_hero_max)
            } else {
                context.getString(R.string.widget_habit_hero_sub, gap)
            }
        )
        views.setTextViewText(
            R.id.widget_habit_month_days,
            context.getString(R.string.widget_habit_month_days, top.monthCount)
        )
        views.setViewVisibility(R.id.widget_habit_month_box, View.VISIBLE)
    }

    /**
     * 新形态档位共用的汇总值：今日完成 x/y、本周完成率、最长连击、连击最长的习惯。
     * 档位小部件（2×1/4×1/1×3）都只消费这一组数。
     */
    private data class HabitSummary(
        val todayDone: Int,
        val total: Int,
        val weekRate: Int,
        val longest: Int,
        val top: HabitRow?
    )

    private fun summarize(rows: List<HabitRow>): HabitSummary {
        val weekHits = rows.sumOf { it.weekHits }
        val weekTotal = rows.size * RECENT_DAYS
        return HabitSummary(
            todayDone = rows.count { it.checked },
            total = rows.size,
            weekRate = if (weekTotal == 0) 0 else weekHits * 100 / weekTotal,
            longest = rows.maxOfOrNull { it.longest } ?: 0,
            top = rows.maxByOrNull { it.streak }
        )
    }

    /** 2×1 极简档：「连续打卡 + 今日 1/4 / 12 天 / 本周完成率」。 */
    /** 2×1 极简档：左连击大数 + 今日「2 / 4」，右本周完成率。internal = 预览图生成器复用。 */
    internal fun bindMinimalViews(context: Context, appWidgetId: Int, rows: List<HabitRow>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_habit_minimal)
        val summary = summarize(rows)
        val family = WidgetData.colorFamily(context, 0)
        if (summary.top == null) {
            views.setViewVisibility(R.id.widget_habit_min_streak, View.GONE)
            views.setViewVisibility(R.id.widget_habit_min_today, View.GONE)
            views.setTextViewText(R.id.widget_habit_min_week, context.getString(R.string.widget_habit_empty))
            return linkViews(context, views, appWidgetId)
        }
        views.setViewVisibility(R.id.widget_habit_min_streak, View.VISIBLE)
        views.setViewVisibility(R.id.widget_habit_min_today, View.VISIBLE)
        views.setTextColor(R.id.widget_habit_min_today, family.hue)
        views.setTextViewText(
            R.id.widget_habit_min_today,
            context.getString(R.string.widget_habit_today_fmt, summary.todayDone, summary.total)
        )
        views.setTextColor(R.id.widget_habit_min_streak, family.hue)
        views.setTextViewText(
            R.id.widget_habit_min_streak,
            "${summary.top.streak} ${context.getString(R.string.widget_days_label)}"
        )
        views.setTextViewText(R.id.widget_habit_min_week, context.getString(R.string.widget_habit_stat_week, summary.weekRate))
        return linkViews(context, views, appWidgetId)
    }

    /** 4×1 横条：左连击 / 中今日与完成率 + 完成度条 / 右最长记录。 */
    /** 4×1 横条：左连击 / 中今日与完成率 + 完成度条 / 右最长记录。internal = 预览图生成器复用。 */
    internal fun bindWideViews(context: Context, appWidgetId: Int, rows: List<HabitRow>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_habit_wide)
        val summary = summarize(rows)
        val family = WidgetData.colorFamily(context, 0)
        views.setTextColor(R.id.widget_habit_wide_streak, family.hue)
        views.setTextViewText(
            R.id.widget_habit_wide_streak,
            if (summary.top == null) {
                context.getString(R.string.widget_habit_empty)
            } else {
                "${summary.top.streak} ${context.getString(R.string.widget_days_label)}"
            }
        )
        val hue = family.hue
        val primary = context.getColor(R.color.widget_v2_text_primary)
        views.setTextViewText(
            R.id.widget_habit_wide_today,
            statSpanned(
                context.getString(R.string.widget_habit_today_fmt, summary.todayDone, summary.total),
                summary.todayDone,
                hue
            )
        )
        views.setTextViewText(
            R.id.widget_habit_wide_week,
            statSpanned(context.getString(R.string.widget_habit_stat_week, summary.weekRate), summary.weekRate, primary)
        )
        val percent = if (summary.total == 0) 0 else summary.todayDone * 100 / summary.total
        views.setInt(R.id.widget_habit_wide_bar, "setColorFilter", family.hue)
        views.setInt(R.id.widget_habit_wide_bar, "setImageLevel", percent * 100)
        views.setTextViewText(
            R.id.widget_habit_wide_longest,
            "${summary.longest} ${context.getString(R.string.widget_days_label)}"
        )
        return linkViews(context, views, appWidgetId)
    }

    /** 1×3 竖条：「N 项习惯 + 1/4」→ 每行名称 + 近 7 天方柱 → 底部本周完成率。 */
    internal fun bindNarrowViews(context: Context, appWidgetId: Int, rows: List<HabitRow>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_habit_narrow)
        val summary = summarize(rows)
        val family = WidgetData.colorFamily(context, 0)
        views.setTextViewText(
            R.id.widget_habit_narrow_title,
            if (rows.isEmpty()) context.getString(R.string.widget_habit_empty)
            else context.getString(R.string.widget_habit_count_label, rows.size)
        )
        views.setTextColor(R.id.widget_habit_narrow_count, family.hue)
        // 头部计数是「今日 x/y」的纯比值（设计稿窄卡头部只有 1/4，不带「今日」前缀），
        // 口径仍是今日，别用 /7 的周口径
        views.setTextViewText(
            R.id.widget_habit_narrow_count,
            context.getString(R.string.widget_habit_today_ratio, summary.todayDone, summary.total)
        )
        views.setTextViewText(
            R.id.widget_habit_narrow_week,
            statSpanned(
                context.getString(R.string.widget_habit_week_brief, summary.weekRate),
                summary.weekRate,
                context.getColor(R.color.widget_v2_text_primary)
            )
        )
        views.removeAllViews(R.id.widget_habit_narrow_rows)
        rows.take(MAX_ROWS).forEachIndexed { index, row ->
            views.addView(R.id.widget_habit_narrow_rows, narrowRow(context, row, index))
        }
        return linkViews(context, views, appWidgetId)
    }

    /** 1×3 竖条的行：名称 + 7 格小方柱，整行是打卡热区（与 4×3 点阵同一动作）。 */
    private fun narrowRow(context: Context, row: HabitRow, index: Int): RemoteViews {
        val rowView = RemoteViews(context.packageName, R.layout.widget_habit_narrow_row)
        val family = WidgetData.colorFamily(context, index)
        val emptyDot = context.getColor(R.color.widget_v2_ring)
        rowView.setTextViewText(R.id.widget_habit_narrow_name, row.name)
        narrowDotIds.forEachIndexed { dotIndex, dotId ->
            val checked = row.recent.getOrElse(dotIndex) { false }
            val isToday = dotIndex == narrowDotIds.lastIndex
            // 稿子窄条：今天未打卡是色族色描边空心（border 1.5px solid var(--h1)），
            // 不是轨道灰实心 —— 与 4×3 的三级色虚线环同一「空心＝没打」语言、不同色
            if (isToday && !checked) {
                rowView.setImageViewResource(dotId, R.drawable.widget_sq_hue_ring)
                rowView.setInt(dotId, "setColorFilter", family.hue)
            } else {
                rowView.setImageViewResource(dotId, R.drawable.widget_sq_dot)
                rowView.setInt(dotId, "setColorFilter", if (checked) family.hue else emptyDot)
            }
        }
        rowView.setOnClickPendingIntent(
            R.id.widget_habit_narrow_row,
            HabitCheckInReceiver.checkInPendingIntent(context, row.templateId)
        )
        return rowView
    }

    /** 新档位共用出口：点卡片进生活页。 */
    private fun linkViews(context: Context, views: RemoteViews, appWidgetId: Int): RemoteViews {
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_HABIT + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        return views
    }

    private val narrowDotIds = listOf(
        R.id.widget_habit_narrow_dot_0,
        R.id.widget_habit_narrow_dot_1,
        R.id.widget_habit_narrow_dot_2,
        R.id.widget_habit_narrow_dot_3,
        R.id.widget_habit_narrow_dot_4,
        R.id.widget_habit_narrow_dot_5,
        R.id.widget_habit_narrow_dot_6
    )

    /** 近 7 天列头：日期数字逐格落位；今天那格是列头顶 —— 上圆角淡底（.tc/.tct）+ 色族色数字（.wkh.on）。 */
    private fun bindWeekHead(views: RemoteViews, today: LocalDate, widgetFamily: WidgetData.ColorFamily) {
        habitHeadIds.forEachIndexed { index, headId ->
            val date = today.minusDays((RECENT_DAYS - 1 - index).toLong())
            views.setTextViewText(headId, "${date.dayOfMonth}")
        }
        views.setInt(R.id.widget_habit_head_6_bg, "setColorFilter", widgetFamily.tint)
        views.setTextColor(R.id.widget_habit_head_6, widgetFamily.hue)
    }

    /** 底部三指标：全量最长连击 / 本周完成率 / 今日待打卡数。数值部分加粗着色（稿 .micro + b）。 */
    private fun bindFooterStats(
        context: Context,
        views: RemoteViews,
        shown: List<HabitRow>,
        rows: List<HabitRow>
    ) {
        val longest = rows.maxOfOrNull { it.longest } ?: 0
        val weekHits = shown.sumOf { it.weekHits }
        val weekTotal = shown.size * RECENT_DAYS
        val weekRate = if (weekTotal == 0) 0 else weekHits * 100 / weekTotal
        val primary = context.getColor(R.color.widget_v2_text_primary)
        val hue = WidgetData.colorFamily(context, 0).hue
        views.setTextViewText(
            R.id.widget_habit_stat_longest,
            statSpanned(context.getString(R.string.widget_habit_stat_longest, longest), longest, primary)
        )
        views.setTextViewText(
            R.id.widget_habit_stat_week,
            statSpanned(context.getString(R.string.widget_habit_stat_week, weekRate), weekRate, primary)
        )
        views.setTextViewText(
            R.id.widget_habit_stat_todo,
            statSpanned(
                context.getString(R.string.widget_habit_stat_todo, rows.count { !it.checked }),
                rows.count { !it.checked },
                hue
            )
        )
    }

    /**
     * 指标值 span：从格式化好的串里找到数值起点，把「数值 + 单位」整段加粗着色
     * （标签留在 TextView 默认的次级色）。找不到数值时原样返回。
     */
    private fun statSpanned(full: String, value: Int, valueColor: Int): CharSequence {
        val start = full.indexOf(value.toString())
        if (start < 0) return full
        return android.text.SpannableString(full).apply {
            setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start,
                full.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                android.text.style.ForegroundColorSpan(valueColor),
                start,
                full.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** 方柱矩阵的一行：左习惯名 + 近 7 天方柱 + 行尾本周 x/7，今天列带贯穿淡底。 */
    private fun dotsRow(context: Context, row: HabitRow, index: Int, isLast: Boolean): RemoteViews {
        val rowView = RemoteViews(context.packageName, R.layout.widget_habit_dots)
        val family = WidgetData.colorFamily(context, index)
        val emptyDot = context.getColor(R.color.widget_v2_ring)
        rowView.setTextViewText(R.id.widget_habit_row_name, row.name)
        rowView.setTextViewText(
            R.id.widget_habit_row_week,
            context.getString(R.string.widget_habit_row_week, row.weekHits)
        )
        // 行尾 x/7：一周没打过是三级色（稿 0/7 用 --txt3），有记录用二级色
        rowView.setTextColor(
            R.id.widget_habit_row_week,
            context.getColor(
                if (row.weekHits == 0) R.color.widget_v2_text_tertiary else R.color.widget_v2_text_secondary
            )
        )
        habitDotIds.forEachIndexed { dotIndex, dotId ->
            val done = row.recent.getOrElse(dotIndex) { false }
            rowView.setInt(dotId, "setColorFilter", if (done) family.hue else emptyDot)
        }
        bindTodayColumn(context, rowView, row, isLast)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val state = if (row.checked) context.getString(R.string.widget_completed) else ""
            rowView.setContentDescription(R.id.widget_habit_dots_row, "${row.name} $state".trim())
        }
        rowView.setOnClickPendingIntent(
            R.id.widget_habit_dots_row,
            HabitCheckInReceiver.checkInPendingIntent(context, row.templateId)
        )
        return rowView
    }

    /**
     * 今天列（稿子「今天列贯穿淡底」）：满行高淡底 .tc（中段直角，最后一行下圆角 .tcb），
     * 颜色取第一色族的淡底（稿子 .tc 恒为 var(--t1)，不随行的色族变）；
     * 打了卡露出色族色方柱，没打卡方柱让位给同位虚线环（border:1.5px dashed var(--txt3)）。
     */
    private fun bindTodayColumn(context: Context, rowView: RemoteViews, row: HabitRow, isLast: Boolean) {
        val done = row.recent.getOrElse(RECENT_DAYS - 1) { false }
        rowView.setInt(R.id.widget_habit_dot_today_bg, "setColorFilter", WidgetData.colorFamily(context, 0).tint)
        if (isLast) rowView.setImageViewResource(R.id.widget_habit_dot_today_bg, R.drawable.widget_col_bottom)
        rowView.setViewVisibility(R.id.widget_habit_dot_6, if (done) View.VISIBLE else View.GONE)
        rowView.setViewVisibility(R.id.widget_habit_dot_today_ring, if (done) View.GONE else View.VISIBLE)
        if (!done) {
            rowView.setInt(
                R.id.widget_habit_dot_today_ring,
                "setColorFilter",
                context.getColor(R.color.widget_v2_text_tertiary)
            )
        }
    }

    private val habitDotIds = listOf(
        R.id.widget_habit_dot_0,
        R.id.widget_habit_dot_1,
        R.id.widget_habit_dot_2,
        R.id.widget_habit_dot_3,
        R.id.widget_habit_dot_4,
        R.id.widget_habit_dot_5,
        R.id.widget_habit_dot_6
    )

    private val habitHeadIds = listOf(
        R.id.widget_habit_head_0,
        R.id.widget_habit_head_1,
        R.id.widget_habit_head_2,
        R.id.widget_habit_head_3,
        R.id.widget_habit_head_4,
        R.id.widget_habit_head_5,
        R.id.widget_habit_head_6
    )

    companion object {
        /** 组件里最多列几行（再多交给生活页；4×3 周历一行约 26dp，4 行正好铺满）。 */
        const val MAX_ROWS = 4

        /** 组件声明的默认尺寸（4×3），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 宽度 <150dp 切 1×3 竖条；高度 <100dp 切横条/极简（横条要求宽 ≥200dp）。 */
        const val NARROW_WIDTH_DP = 150
        const val STRIP_HEIGHT_DP = 100
        const val WIDE_WIDTH_DP = 200

        /** 连击数字字号：习惯越少放得越大（1-2 / 3-4 / 5+ 行）。 */
        private const val STREAK_SP_FEW = 64f
        private const val STREAK_SP_SOME = 48f
        private const val STREAK_SP_MANY = 40f

        /** 点阵一行 7 格，从 6 天前排到今天。 */
        private const val RECENT_DAYS = 7

        /**
         * 习惯一族全量刷新：基类 + 3 个尺寸形态（HabitMini/HabitWide/HabitNarrow）。
         * 从目录条目派生（isAssignableFrom），每个类只广播自己实例的 id ——
         * 框架不校验 id 归属，混发会把一种形态覆盖成另一种的界面。
         */
        fun requestUpdateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            WidgetPin.entries.map { it.providerClass }
                .filter { HabitWidgetProvider::class.java.isAssignableFrom(it) }
                .forEach { provider ->
                    val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                    if (ids.isEmpty()) return@forEach
                    context.sendBroadcast(
                        android.content.Intent(context, provider)
                            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    )
                }
        }
    }
}
