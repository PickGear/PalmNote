package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class LifeCounterWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): com.palmnote.data.db.dao.LifeItemDao
        fun lifeTemplateDao(): com.palmnote.data.db.dao.LifeTemplateDao
        fun preferencesManager(): com.palmnote.data.datastore.PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())
        val events = collectCounterEvents(context)
        for (appWidgetId in appWidgetIds) {
            // 按实际宽度分桶：大档（≥400dp）显示多事件列表，小/中档只显示最近一个事件
            val minWidth = appWidgetManager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val views = counterViews(context, appWidgetId, events, isLarge = minWidth >= 400)
            // 焦点卡底运行时取主题色（此前硬编码青色）
            views.setInt(R.id.widget_counter_focus_bg, "setColorFilter", accent.accent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private suspend fun collectCounterEvents(context: Context): List<CounterEvent> {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val lifeItemDao = entryPoint.lifeItemDao()
        val lifeTemplateDao = entryPoint.lifeTemplateDao()
        val prefs = entryPoint.preferencesManager()
        val demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        val includeDemo = prefs.lifeDemoMode.first()
        // 本小组件是**倒计时**口径（按 daysLeft 排序）：取语义身份为 COUNTDOWN 的模板，
        // 不是 FOCUS。此前按 "timer"（专注）过滤，于是这个倒计时小组件挂的是专注模板，
        // 而真正该显示的倒计时条目一条都不出现 —— 与 strings.xml 里
        // widget_life_counter_description（倒计时/生日/纪念日）自相矛盾。
        // 现走统一入口：用户改图标不会再让这个小组件认错模板。
        //
        // 生日 / 纪念日也纳入（原来只取 COUNTDOWN，与组件说明不符）：它们的日期是**过去**的，
        // 所以要按**下一次周年**滚 —— 复用详情页那套 `nextYearlyOccurrence`（农历也会反算），
        // 这也是原先那条 TODO 卡住的点（"需要先确认日期字段口径"）。
        val counterTemplates = lifeTemplateDao.getAllVisibleTemplates().first()
            .filter { it.getKind() in COUNTER_KINDS }
        val today = LocalDate.now()
        return counterTemplates
            // 定案 31：小组件出口过滤下沉查询端（demo 互斥 + 排除归档，软撤销不再计数）
            .flatMap { template ->
                lifeItemDao.getWidgetItemsByTemplate(template.id, includeDemo, demoMeta).first()
                    .map { item -> item to template.getKind() }
            }
            .mapNotNull { (item, kind) -> item.toCounterEvent(today, kind) }
            .sortedBy { it.daysLeft }
    }

    internal fun counterViews(context: Context, appWidgetId: Int, events: List<CounterEvent>, isLarge: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_counter_unified)
        views.setTextViewText(R.id.widget_counter_count, "${events.size}")
        val firstEvent = events.firstOrNull()
        // 大档：整列多事件（首事件并入列表）；小/中档：单个焦点事件（Apple 式：S 档是单一焦点）
        if (isLarge && events.size > 1) {
            views.setViewVisibility(R.id.widget_first_event, View.GONE)
            views.setViewVisibility(R.id.widget_more_events, View.VISIBLE)
            views.removeAllViews(R.id.widget_more_events)
            events.take(4).forEach { event ->
                val item = RemoteViews(context.packageName, R.layout.widget_counter_item)
                item.setTextViewText(R.id.widget_item_name, event.name)
                item.setTextViewText(R.id.widget_item_days, "${event.daysLeft}")
                // 点事件 → 直达该记录详情页（深链）
                item.setOnClickPendingIntent(
                    R.id.widget_counter_item_root,
                    WidgetHelper.createLifeEventPendingIntent(context, event.itemId)
                )
                views.addView(R.id.widget_more_events, item)
            }
        } else if (firstEvent != null) {
            views.setViewVisibility(R.id.widget_first_event, View.VISIBLE)
            views.setViewVisibility(R.id.widget_more_events, View.GONE)
            views.removeAllViews(R.id.widget_more_events)
            views.setTextViewText(R.id.widget_event_name, firstEvent.name)
            views.setTextViewText(R.id.widget_days_count, "${firstEvent.daysLeft}")
            val datePattern = context.getString(R.string.widget_date_format_cn)
            views.setTextViewText(
                R.id.widget_event_date,
                firstEvent.targetDate.format(DateTimeFormatter.ofPattern(datePattern))
            )
            // 周年滚动进度环：生日/纪念日才有周期，一次性倒计时隐藏
            val rollPercent = firstEvent.progressPercent
            views.setViewVisibility(R.id.widget_counter_ring, if (rollPercent != null) View.VISIBLE else View.GONE)
            if (rollPercent != null) {
                views.setProgressBar(R.id.widget_counter_ring, 100, rollPercent, false)
            }
            // 点焦点事件 → 直达该记录详情页（深链）
            views.setOnClickPendingIntent(
                R.id.widget_first_event,
                WidgetHelper.createLifeEventPendingIntent(context, firstEvent.itemId)
            )
        } else {
            views.setViewVisibility(R.id.widget_first_event, View.GONE)
            views.setViewVisibility(R.id.widget_more_events, View.GONE)
        }
        views.setViewVisibility(R.id.widget_empty_state, if (firstEvent == null) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, 300_000 + appWidgetId, "life")
        )
        return views
    }

    private fun com.palmnote.data.db.entity.LifeItem.toCounterEvent(
        today: LocalDate,
        kind: LifeTemplateKind
    ): CounterEvent? {
        val anchorMs = dueDate ?: return null
        val anchor = java.time.Instant.ofEpochMilli(anchorMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val yearly = kind == LifeTemplateKind.BIRTHDAY || kind == LifeTemplateKind.ANNIVERSARY
        val lunar = lunarFlagOf(fieldsData)
        val targetDate = counterTargetDate(anchor, today, yearly, lunar = lunar)
        val daysLeft = ChronoUnit.DAYS.between(today, targetDate)
        if (daysLeft < 0) return null
        return CounterEvent(
            itemId = id,
            name = title,
            daysLeft = daysLeft,
            targetDate = targetDate,
            progressPercent = yearlyRollPercent(anchor, today, targetDate, yearly, lunar)
        )
    }

    /**
     * 周年滚动进度：本期（上一次周年 → 下一次周年）已过比例 0..100。
     * 上一次周年由「一年前的今天」反推，农历也按农历反算，周期因此是真实间隔而非固定 365。
     * 一次性倒计时没有周期，返回 null（环隐藏）。
     */
    internal fun yearlyRollPercent(
        anchor: LocalDate,
        today: LocalDate,
        targetDate: LocalDate,
        yearly: Boolean,
        lunar: Boolean
    ): Int? {
        if (!yearly) return null
        val previous = counterTargetDate(anchor, today.minusYears(1), yearly = true, lunar = lunar)
        val cycleDays = ChronoUnit.DAYS.between(previous, targetDate)
        if (cycleDays <= 0) return null
        return (ChronoUnit.DAYS.between(previous, today) * 100 / cycleDays).toInt().coerceIn(0, 100)
    }

    /** 条目字段里的 `lunar` 开关（生日模板才有）；解析不出按公历。 */
    private fun lunarFlagOf(fieldsData: String): Boolean = runCatching {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(fieldsData)
            .let { it as? kotlinx.serialization.json.JsonObject }
        val raw = (obj?.get("lunar") as? kotlinx.serialization.json.JsonPrimitive)?.content
        com.palmnote.ui.life.parseLunarFlag(raw)
    }.getOrDefault(false)

    internal data class CounterEvent(
        val itemId: Long,
        val name: String,
        val daysLeft: Long,
        val targetDate: LocalDate,
        /** 周年滚动进度 0..100；一次性倒计时为 null。 */
        val progressPercent: Int? = null
    )
}

/** 倒计时组件纳入的模板身份：显式倒计时 + 生日 + 纪念日（与组件说明文案一致）。 */
private val COUNTER_KINDS = setOf(
    LifeTemplateKind.COUNTDOWN,
    LifeTemplateKind.BIRTHDAY,
    LifeTemplateKind.ANNIVERSARY
)

/**
 * 倒计时组件的**目标日**（纯函数，可单测）：
 * - 显式倒计时：就是锚点那天（已过则由调用方按 `daysLeft >= 0` 过滤掉）；
 * - 生日 / 纪念日：滚到**下一次周年**（`nextYearlyOccurrence`，农历按农历反算）。
 *
 * 抽出来是因为这里正是原先那条 TODO 卡住的点：生日条目的日期是**过去**的，
 * 直接算 `today → 锚点` 永远是负数、条目一条都不显示。
 */
internal fun counterTargetDate(
    anchor: LocalDate,
    today: LocalDate,
    yearly: Boolean,
    lunar: Boolean
): LocalDate = if (yearly) com.palmnote.ui.life.nextYearlyOccurrence(anchor, today, lunar) else anchor
