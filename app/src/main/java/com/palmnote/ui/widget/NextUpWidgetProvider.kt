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
import com.palmnote.domain.util.BuiltinTemplates
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * 「接下来」焦点组件（v22）：一张卡只回答「现在最该管哪一件事」。
 *
 * 候选按可行动性与紧迫度排序（[pickFocus]）：
 * 1. 今天到期、还没完成的待办（可行动，直接在卡上勾掉）；
 * 2. 3 天内要扣费的订阅（错过就是真金白银）；
 * 3. 最近的倒数事件（生日/纪念日/倒计时）。
 *
 * 四档构图按 [WidgetSizeClass] 切档（稿 c-next）：
 * - WIDE 4×2：hero「N 天」+ 倒计时进度条 + 事件说明行；
 * - SQUARE 2×2：hero + 进度条 + 事件行 / 提醒行；
 * - BAR 4×1：三列读数（还有 / 事件 / 日期）+ 日历条（一格 = 还剩一天，末格 = 事件日）；
 * - TALL 4×4（v37 §2-10）：hero + 三 KPI（最近/本月/提醒）+ 日历条（今天描边、事件格实底）+
 *   进度条 + 底部四行事件明细；压缩态（高 250~299dp）砍日历条与进度条、明细降 2 行。
 *
 * 进度条口径：**从建项到目标日已过去多少**（[NextUpFocus.progress]）——
 * 建项当天或算不出时整条收起；待办/订阅没有可比的跨度，同样不画（不编比例）。
 * 日历条格数 = 还剩几天（≤10 才画，格位就 10 个）。
 */
// open：NextUpWide/Bar/Mini 尺寸形态薄子类继承（v22 四档）
open class NextUpWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    /** 焦点条目：kind 决定文案与配色族，itemId 只有待办有（驱动卡上的勾选动作）。 */
    internal data class NextUpFocus(
        val kind: Kind,
        val title: String,
        /** 次要说明行原始值：订阅是「M/d」，倒数是目标日 ISO 文本；待办为空（bindFocus 落「今天到期」）。 */
        val meta: String?,
        /** 大字数值：待办是「今天」，订阅是金额，倒数是天数。 */
        val bigValue: String,
        val itemId: Long? = null,
        /** 倒计时进度 0..1（建项→目标日已过去多少）；null = 不画进度条。 */
        val progress: Float? = null,
        /** 事件日自带的时刻「HH:mm」（生日设了时间才有）；null = 提醒行收起。 */
        val reminder: String? = null,
        /** 倒数天数（日历条格数用；非倒数为 null）。 */
        val daysLeft: Long? = null,
        /** 事件目标日（日历条末格与日期文案用；非倒数为 null）。 */
        val targetDate: LocalDate? = null
    )

    internal enum class Kind { TODO, RENEWAL, COUNTDOWN }

    /**
     * 4×4 底部的一行事件明细：日期 + 名称 + 时刻（没有时刻的按「全天」）。
     * [familyIdx] 按行轮换（稿内四行取色 c1/c4/c2/c5）。
     */
    internal data class UpcomingEvent(
        val date: LocalDate,
        val name: String,
        val timeText: String?,
        val familyIdx: Int
    )

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()
        val includeDemo = prefs.lifeDemoMode.first()
        val todos = WidgetData.fetchTodayTodos(
            entryPoint.lifeItemDao(), entryPoint.lifeTemplateDao(), includeDemo, LIFE_DEMO_META
        ).filter { it.status != "COMPLETED" }
        val subscriptionTemplate = entryPoint.lifeTemplateDao().getAllVisibleTemplates().first()
            .firstOrNull { it.name.contains(BuiltinTemplates.SUBSCRIPTION_KEYWORD) }
        val renewals = subscriptionTemplate
            ?.let { SubscriptionWidgetProvider().fetchRenewals(entryPoint.lifeItemDao(), it.id, includeDemo) }
            .orEmpty()
        val counters = LifeCounterWidgetProvider().collectCounterEvents(appContext)
        val events = buildUpcomingEvents(todos, renewals, counters, LocalDate.now())

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            // 强调色：逐实例配色覆盖优先，否则「接下来」族默认色（与徽章原先的 familyD(4) 一致）
            val accent = WidgetData.accentColorOrDefault(context, prefs, appWidgetId, familyD(context, 4))
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                bindViews(
                    context, appWidgetId, pickFocus(todos, renewals, counters), width, height,
                    events = events, accent = accent
                )
            }
        }
    }

    /**
     * 4×4 底部明细行：今天到期的待办 + 3 天内扣费的订阅 + 未来的倒数事件，按日期/时刻排序，
     * 取前 4 条（压缩态由渲染层再截到 2 条）。行色按位次轮换（稿 c1/c4/c2/c5）。
     */
    internal fun buildUpcomingEvents(
        todos: List<com.palmnote.data.db.entity.LifeItem>,
        renewals: List<SubscriptionWidgetProvider.Renewal>,
        counters: List<CounterEvent>,
        today: LocalDate
    ): List<UpcomingEvent> {
        val rows = buildList {
            todos.forEach { todo ->
                val due = todo.dueDate?.let {
                    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                } ?: return@forEach
                add(
                    UpcomingEvent(
                        date = due.toLocalDate(),
                        name = todo.title,
                        timeText = due.format(DateTimeFormatter.ofPattern("HH:mm"))
                            .takeIf { it != "00:00" },
                        familyIdx = 0
                    )
                )
            }
            renewals.filter { it.daysLeft <= RENEWAL_WINDOW_DAYS }.forEach { renewal ->
                add(
                    UpcomingEvent(
                        date = today.plusDays(renewal.daysLeft),
                        name = renewal.name,
                        timeText = null,
                        familyIdx = 4
                    )
                )
            }
            counters.forEach { counter ->
                add(
                    UpcomingEvent(
                        date = counter.targetDate,
                        name = counter.name,
                        timeText = counter.reminderTime,
                        familyIdx = 3
                    )
                )
            }
        }
        return rows
            .sortedWith(compareBy({ it.date }, { it.timeText ?: "99" }))
            .take(MAX_EVENT_ROWS)
            .mapIndexed { index, event -> event.copy(familyIdx = ROW_FAMILIES[index % ROW_FAMILIES.size]) }
    }

    internal fun pickFocus(
        todos: List<com.palmnote.data.db.entity.LifeItem>,
        renewals: List<SubscriptionWidgetProvider.Renewal>,
        counters: List<CounterEvent>
    ): NextUpFocus? {
        todos.firstOrNull()?.let { todo ->
            return NextUpFocus(Kind.TODO, todo.title, null, bigValue = "", itemId = todo.id)
        }
        renewals.filter { it.daysLeft <= RENEWAL_WINDOW_DAYS }.minByOrNull { it.daysLeft }?.let { renewal ->
            return NextUpFocus(
                Kind.RENEWAL, renewal.name,
                LocalDate.now().plusDays(renewal.daysLeft)
                    .format(DateTimeFormatter.ofPattern("M/d")),
                WidgetHelper.moneyText(renewal.price).ifBlank { "—" }
            )
        }
        counters.minByOrNull { it.daysLeft }?.let { counter ->
            val today = LocalDate.now()
            return NextUpFocus(
                Kind.COUNTDOWN, counter.name,
                // 目标日按 ISO 落进 meta，渲染时再按 locale 格式化（pickFocus 无 Context）
                meta = counter.targetDate.toString(),
                bigValue = "${counter.daysLeft}",
                progress = countdownProgress(counter, today),
                reminder = counter.reminderTime,
                daysLeft = counter.daysLeft,
                targetDate = counter.targetDate
            )
        }
        return null
    }

    /**
     * 倒计时进度：从建项到目标日已过去多少。建项日期取不到、或跨度 ≤0（当天建当天到期）
     * 都返回 null —— 调用方收起进度条，不摆一条假比例。
     */
    internal fun countdownProgress(counter: CounterEvent, today: LocalDate): Float? {
        val created = counter.createdAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        } ?: return null
        val total = ChronoUnit.DAYS.between(created, counter.targetDate)
        if (total <= 0) return null
        val elapsed = ChronoUnit.DAYS.between(created, today)
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        focus: NextUpFocus?,
        widthDp: Int = DEFAULT_WIDTH_DP,
        heightDp: Int = DEFAULT_HEIGHT_DP,
        events: List<UpcomingEvent> = emptyList(),
        accent: Int? = null
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_next_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_next_v22_square
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> R.layout.widget_next_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        // 徽章用族色 c5 橙（稿「接下来」的标识色）；实例配色覆盖优先；tall 布局的徽章由 bindTallHeader 上
        applyBadge(views, context, size, accent)
        if (focus == null) {
            views.setViewVisibility(R.id.widget_next_hero, View.GONE)
            views.setViewVisibility(R.id.widget_next_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_next_empty, View.GONE)
            when (size) {
                WidgetSizeClass.TALL ->
                    bindTall4x4(context, views, focus, events, compact = heightDp < TALL_FULL_DP)
                WidgetSizeClass.WIDE ->
                    bindWide(context, views, focus)
                WidgetSizeClass.SQUARE -> bindSquare(context, views, focus)
                WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> bindBar(context, views, focus)
            }
        }
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_NEXT_UP + appWidgetId,
                WidgetDeepLink.TAB_LIFE
            )
        )
        // 待办焦点：卡上直接勾掉（与待办组件同一接收器）
        if (focus != null && focus.kind == Kind.TODO && focus.itemId != null) {
            views.setOnClickPendingIntent(
                R.id.widget_next_hero,
                TodoToggleReceiver.togglePendingIntent(context, focus.itemId)
            )
        }
        return views
    }

    /**
     * 4×4（v37 §2-10）：hero「N 天 · 事件名」+ 三 KPI（最近/本月/提醒）+ 日历条
     * （今天描边、事件格实底）+ 建项至今进度条 + 底部四行事件明细。
     * 压缩态（高 250~299dp）：砍日历条与进度条、明细降 2 行（稿 §04 压缩优先级）。
     * 待办/订阅焦点没有可比跨度，日历条与进度条整块收起（不编比例）。
     */
    private fun bindTall4x4(
        context: Context,
        views: RemoteViews,
        focus: NextUpFocus,
        events: List<UpcomingEvent>,
        compact: Boolean
    ) {
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_clock_white,
            title = context.getString(R.string.widget_next_up_title),
            meta = context.getString(R.string.widget_next_up_meta_window),
            familyIdx = 4
        )
        WidgetV22.bindTallHero(context, views, heroText(context, focus), focus.title)
        bindTallKpis(context, views, focus, events)
        val showStrip = !compact &&
            focus.kind == Kind.COUNTDOWN && focus.daysLeft != null && focus.targetDate != null &&
            focus.daysLeft > 0 && focus.daysLeft <= STRIP_SLOTS
        if (showStrip) {
            bindTallStrip(context, views, focus)
        }
        bindTallRows(context, views, events, compact)
        WidgetV22.tallSections(context, views, "kpis", if (showStrip) "strip_host" else "", "rows")
    }

    /** 4×4 KPI：最近（首行事件日期 + 名称）/ 本月（事件数）/ 提醒（焦点事件时刻）。 */
    private fun bindTallKpis(
        context: Context,
        views: RemoteViews,
        focus: NextUpFocus,
        events: List<UpcomingEvent>
    ) {
        val today = LocalDate.now()
        val monthCount = events.count { it.date.year == today.year && it.date.month == today.month }
        WidgetV22.bindKpis(
            context, views,
            buildList {
                events.firstOrNull()?.let { first ->
                    add(
                        WidgetV22.Kpi(
                            context.getString(R.string.widget_next_up_label_recent),
                            first.date.format(DateTimeFormatter.ofPattern("M/d")),
                            first.name,
                            4
                        )
                    )
                }
                add(
                    WidgetV22.Kpi(
                        context.getString(R.string.widget_next_up_label_month),
                        "$monthCount",
                        // 副文只给单位：KPI 大数字已经是笔数，再拼一遍就成了「3 / 3 个事件」
                        context.getString(R.string.widget_next_up_events_unit),
                        0
                    )
                )
                focus.reminder?.let {
                    add(
                        WidgetV22.Kpi(
                            context.getString(R.string.widget_next_up_label_remind),
                            it,
                            null,
                            3
                        )
                    )
                }
            }
        )
    }

    /**
     * 4×4 日历条 + 进度条：10 格 ending at 事件日（事件日 − 9 天起，今天落在格内时给描边，
     * 稿 .dcell.today）。日历条是 tall 布局里的既有 include，直接按 id 绑（不用 addView）。
     */
    private fun bindTallStrip(context: Context, views: RemoteViews, focus: NextUpFocus) {
        val target = focus.targetDate ?: return
        val today = LocalDate.now()
        val start = target.minusDays((STRIP_SLOTS - 1))
        val labels = (0 until STRIP_SLOTS.toInt()).map { offset ->
            val day = start.plusDays(offset.toLong())
            if (day.month == target.month && day.year == target.year && day != target) {
                "${day.dayOfMonth}"
            } else {
                day.format(DateTimeFormatter.ofPattern("M/d"))
            }
        }
        // 今天格下标 = 今天距起始格的天数（不在 10 格窗口内就不描边）
        val todayIdx = ChronoUnit.DAYS.between(start, today).toInt().takeIf { it in labels.indices }
        WidgetV22.bindDstrip(
            context, views,
            labels = labels,
            eventIdx = labels.lastIndex,
            familyIdx = 4,
            todayIdx = todayIdx
        )
        focus.progress?.let {
            WidgetV22.bindTallProgress(context, views, Math.round(it * 100), 4)
        }
    }

    /** 4×4 底部事件明细：4 行（压缩态 2 行）；时刻缺失按「全天」。 */
    private fun bindTallRows(
        context: Context,
        views: RemoteViews,
        events: List<UpcomingEvent>,
        compact: Boolean
    ) {
        val shown = if (compact) events.take(2) else events.take(MAX_EVENT_ROWS)
        WidgetV22.bindRows(
            context, views,
            R.id.widget_tall_rows,
            shown.map { event ->
                WidgetV22.DualItem(
                    date = event.date.format(DateTimeFormatter.ofPattern("M/d")),
                    name = event.name,
                    value = event.timeText ?: context.getString(R.string.widget_next_up_all_day),
                    familyIdx = event.familyIdx
                )
            }
        )
    }

    /** 4×2：hero + 进度条 + 一行说明（事件 · 日期 · [提醒] 星期）。 */
    private fun bindWide(context: Context, views: RemoteViews, focus: NextUpFocus) {
        views.setTextViewText(R.id.widget_next_hero, heroText(context, focus))
        views.setViewVisibility(R.id.widget_next_hero, View.VISIBLE)
        bindProg(context, views, focus)
        val meta = metaText(context, focus, twoLines = false)
        views.setTextViewText(R.id.widget_next_meta, meta)
        views.setViewVisibility(R.id.widget_next_meta, if (meta.isBlank()) View.GONE else View.VISIBLE)
    }

    /** 2×2：hero + 进度条 + 事件行 / 提醒行。 */
    private fun bindSquare(context: Context, views: RemoteViews, focus: NextUpFocus) {
        views.setTextViewText(R.id.widget_next_hero, heroText(context, focus))
        views.setViewVisibility(R.id.widget_next_hero, View.VISIBLE)
        bindProg(context, views, focus)
        val meta = metaText(context, focus, twoLines = true)
        views.setTextViewText(R.id.widget_next_meta, meta)
        views.setViewVisibility(R.id.widget_next_meta, if (meta.isBlank()) View.GONE else View.VISIBLE)
        val meta2 = meta2Text(context, focus)
        views.setTextViewText(R.id.widget_next_meta2, meta2)
        views.setViewVisibility(R.id.widget_next_meta2, if (meta2.isBlank()) View.GONE else View.VISIBLE)
    }

    /** 4×1：三列读数 + 日历条（≤10 天才画）。 */
    private fun bindBar(context: Context, views: RemoteViews, focus: NextUpFocus) {
        views.setTextViewText(R.id.widget_next_bar_left, heroText(context, focus))
        views.setTextViewText(R.id.widget_next_bar_mid, focus.title)
        val dateText = focus.targetDate
            ?.format(DateTimeFormatter.ofPattern("M/d"))
            ?: focus.meta.orEmpty()
        views.setTextViewText(
            R.id.widget_next_bar_right,
            if (focus.targetDate != null) "$dateText ${weekdayOf(focus.targetDate)}" else dateText
        )
        bindStrip(context, views, focus)
    }

    /** 2×1：天数徽章 + 三根微柱。 */


    /** 进度条：fraction 为 null（待办/订阅/当天建项）时整条收起。 */
    private fun bindProg(context: Context, views: RemoteViews, focus: NextUpFocus) {
        val fraction = focus.progress
        if (fraction == null) {
            views.setViewVisibility(R.id.widget_next_prog, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_next_prog, View.VISIBLE)
        views.setInt(R.id.widget_next_prog_fill, "setColorFilter", familyD(context, 4))
        views.setInt(R.id.widget_next_prog_fill, "setImageLevel", Math.round(fraction * 100) * 100)
    }

    /**
     * 日历条：一格 = 还剩一天（从明天数到事件日），末格 = 事件日（族色底白字）。
     * 天数 >10 没有足够格位，整条收起（数字照样给，不把 30 天塞进 10 格）。
     */
    private fun bindStrip(context: Context, views: RemoteViews, focus: NextUpFocus) {
        val days = focus.daysLeft
        val target = focus.targetDate
        val notCountdown = focus.kind != Kind.COUNTDOWN || days == null || target == null
        if (notCountdown || days <= 0 || days > STRIP_SLOTS) {
            views.setViewVisibility(R.id.widget_next_strip_host, View.GONE)
            return
        }
        val labels = (1..days).map { offset ->
            val day = target.minusDays(days - offset)
            // 同月用日数，跨月那格用 M/d（与稿一致：23…31、11/1）
            if (day.month == target.month && day.year == target.year && offset < days) {
                "${day.dayOfMonth}"
            } else {
                day.format(DateTimeFormatter.ofPattern("M/d"))
            }
        }
        val strip = widgetViews(context, R.layout.widget_v22_dstrip)
        WidgetV22.bindDstrip(context, strip, labels, eventIdx = labels.lastIndex, familyIdx = 4)
        views.removeAllViews(R.id.widget_next_strip_host)
        views.addView(R.id.widget_next_strip_host, strip)
        views.setViewVisibility(R.id.widget_next_strip_host, View.VISIBLE)
    }

    /** hero：待办=今天；订阅=金额；倒数=「N 天」。 */
    private fun heroText(context: Context, focus: NextUpFocus): String = when (focus.kind) {
        Kind.TODO -> context.getString(R.string.widget_next_up_due_today)
        Kind.COUNTDOWN -> "${focus.bigValue} ${context.getString(R.string.widget_next_up_days_unit)}"
        Kind.RENEWAL -> focus.bigValue
    }

    /**
     * 说明行：`事件 · 日期 [· 提醒 HH:mm] 星期`（twoLines = 2×2 的两行式，
     * 第一行只到「日期 星期」，提醒单独一行）。
     */
    private fun metaText(context: Context, focus: NextUpFocus, twoLines: Boolean): String {
        val date = focus.targetDate?.format(DateTimeFormatter.ofPattern("M/d")) ?: focus.meta.orEmpty()
        val weekday = focus.targetDate?.let { weekdayOf(it) }.orEmpty()
        return when (focus.kind) {
            // 待办:meta 行放标题(标题不放就永远看不到是哪件事);hero 已有「今天」
            Kind.TODO -> focus.title
            Kind.RENEWAL -> context.getString(R.string.widget_next_up_renewal_date, date)
            Kind.COUNTDOWN -> buildString {
                append(focus.title)
                if (date.isNotEmpty()) append(" · $date")
                if (!twoLines && focus.reminder != null) append(" · ${reminderLabel(context, focus.reminder)}")
                if (weekday.isNotEmpty()) append(" $weekday")
            }
        }
    }

    /** 2×2 的第二行：只有「提醒 HH:mm」（没有时刻就不编）。 */
    private fun meta2Text(context: Context, focus: NextUpFocus): String =
        if (focus.kind == Kind.COUNTDOWN && focus.reminder != null) {
            reminderLabel(context, focus.reminder)
        } else {
            ""
        }

    private fun reminderLabel(context: Context, time: String): String =
        context.getString(R.string.widget_next_up_reminder, time)

    private fun weekdayOf(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /** 徽章色：实例配色覆盖优先，否则「接下来」族色；tall 布局的徽章由 bindTallHeader 上色。 */
    private fun applyBadge(views: RemoteViews, context: Context, size: WidgetSizeClass, accent: Int?) {
        if (size == WidgetSizeClass.TALL) return
        views.setInt(R.id.widget_next_badge, "setColorFilter", accent ?: familyD(context, 4))
    }

    private fun familyD(context: Context, index: Int) = WidgetV22.family(context, index).d

    companion object {
        /** 组件声明的默认尺寸（4×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 订阅进入「接下来」的窗口：3 天内要扣费才算紧迫。 */
        const val RENEWAL_WINDOW_DAYS = 3L

        /** 日历条格位上限（widget_v22_dstrip 就 10 格）。 */
        const val STRIP_SLOTS = 10L

        /** 4×4 底部事件明细行数上限（稿 §2-10 四行）。 */
        const val MAX_EVENT_ROWS = 4

        /** 4×4 明细行色（稿内四行取色）：c1 蓝 / c4 紫 / c2 绿 / c5 橙。 */
        val ROW_FAMILIES = intArrayOf(0, 3, 1, 4)

        /** 4×4 完整排版高度；低于它（250~299dp 的压缩态）砍日历条与进度条、明细降 2 行。 */
        const val TALL_FULL_DP = 300
    }
}
