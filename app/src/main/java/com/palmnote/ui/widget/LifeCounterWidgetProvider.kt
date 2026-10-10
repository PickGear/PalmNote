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
        val events = collectCounterEvents(context)
        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            // 按尺寸分档：宽到 400dp 以上显示多事件列表，否则只显示最近一个焦点事件
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                counterViews(
                    context,
                    appWidgetId,
                    events,
                    isLarge = width >= LARGE_WIDTH_DP,
                    compactHeight = height < COMPACT_HEIGHT_DP,
                    heightDp = height
                )
            }
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

    internal fun counterViews(
        context: Context,
        appWidgetId: Int,
        events: List<CounterEvent>,
        isLarge: Boolean,
        compactHeight: Boolean = false,
        heightDp: Int = DEFAULT_HEIGHT_DP
    ): RemoteViews {
        // 摆得高时把数字与日历条一起放大，否则卡片上下会空出三分之一
        val roomy = heightDp >= ROOMY_HEIGHT_DP
        val views = RemoteViews(context.packageName, R.layout.widget_counter_unified)
        // 装饰色走色族（不占全局强调色）：倒计时用粉（稿子如此），徽章里的时钟字形着白
        val family = WidgetData.colorFamily(context, 3)
        views.setInt(R.id.widget_counter_badge, "setColorFilter", family.hue)
        views.setInt(R.id.widget_counter_badge_icon, "setColorFilter", android.graphics.Color.WHITE)
        views.setTextViewText(R.id.widget_counter_count, "${events.size}")
        val firstEvent = events.firstOrNull()
        // 大档：整列多事件（首事件并入列表）；小/中档：只留单个焦点事件，窄格子里才不至于挤成一列
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
            views.setTextColor(R.id.widget_days_count, family.hue)
            views.setTextViewTextSize(
                R.id.widget_days_count,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                if (roomy) DAYS_SP_ROOMY else DAYS_SP_NORMAL
            )
            val datePattern = context.getString(R.string.widget_date_format_cn)
            views.setTextViewText(
                R.id.widget_event_date,
                firstEvent.targetDate.format(DateTimeFormatter.ofPattern(datePattern))
            )
            // 高度不足时收起日期行（次要信息），天数与日历条保留
            views.setViewVisibility(R.id.widget_event_date, if (compactHeight) View.GONE else View.VISIBLE)
            bindDateStrip(context, views, firstEvent.targetDate, family.hue, roomy)
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
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_COUNTER + appWidgetId, "life")
        )
        return views
    }

    /**
     * 横向日历条：目标日前后各几天，目标日那格上色族色、文字转白并放大一号。
     * 格子等宽（布局里写死的 weight），只换颜色与字号 —— RemoteViews 改不了格子宽度。
     */
    private fun bindDateStrip(
        context: Context,
        views: RemoteViews,
        target: LocalDate,
        highlight: Int,
        roomy: Boolean
    ) {
        val secondary = context.getColor(R.color.widget_v2_text_secondary)
        val normalSp = if (roomy) DATE_SP_ROOMY else DATE_SP_NORMAL
        val highlightSp = normalSp + 2f
        dateCellIds.forEachIndexed { index, (glowId, bgId, textId) ->
            val date = target.plusDays((index - STRIP_LEAD).toLong())
            views.setTextViewText(textId, "${date.dayOfMonth}")
            val isTarget = date == target
            views.setViewVisibility(bgId, if (isTarget) View.VISIBLE else View.GONE)
            views.setViewVisibility(glowId, if (isTarget) View.VISIBLE else View.GONE)
            if (isTarget) {
                // 外发光 = 同色相压到约 20% 透明度（RemoteViews 改不了 alpha，只能换色值）
                views.setInt(glowId, "setColorFilter", (highlight and 0x00FFFFFF) or (0x33 shl 24))
                views.setInt(bgId, "setColorFilter", highlight)
                views.setTextColor(textId, android.graphics.Color.WHITE)
                views.setTextViewTextSize(textId, android.util.TypedValue.COMPLEX_UNIT_SP, highlightSp)
            } else {
                views.setTextColor(textId, secondary)
                views.setTextViewTextSize(textId, android.util.TypedValue.COMPLEX_UNIT_SP, normalSp)
            }
        }
    }

    /** 每个日期格：(发光层, 高亮块, 数字)。 */
    private val dateCellIds = listOf(
        Triple(R.id.widget_counter_date_glow_0, R.id.widget_counter_date_bg_0, R.id.widget_counter_date_0),
        Triple(R.id.widget_counter_date_glow_1, R.id.widget_counter_date_bg_1, R.id.widget_counter_date_1),
        Triple(R.id.widget_counter_date_glow_2, R.id.widget_counter_date_bg_2, R.id.widget_counter_date_2),
        Triple(R.id.widget_counter_date_glow_3, R.id.widget_counter_date_bg_3, R.id.widget_counter_date_3),
        Triple(R.id.widget_counter_date_glow_4, R.id.widget_counter_date_bg_4, R.id.widget_counter_date_4),
        Triple(R.id.widget_counter_date_glow_5, R.id.widget_counter_date_bg_5, R.id.widget_counter_date_5),
        Triple(R.id.widget_counter_date_glow_6, R.id.widget_counter_date_bg_6, R.id.widget_counter_date_6)
    )

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
            targetDate = targetDate
        )
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
        val targetDate: LocalDate
    )

    private companion object {
        /** 组件声明的默认尺寸（4×3），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 宽度到 4 格以上才显示多事件列表。 */
        const val LARGE_WIDTH_DP = 400

        /** 高度不足 2 格（160dp）时收起次要元素。 */
        const val COMPACT_HEIGHT_DP = 160

        /** 日历条 7 格：目标日在前 5 天后 1 天的位置上。 */
        const val STRIP_LEAD = 5

        /** 摆到 3 格高以上（约 240dp）就算「摆得高」，数字与日历条放大一档。 */
        const val ROOMY_HEIGHT_DP = 240

        private const val DAYS_SP_NORMAL = 38f
        private const val DAYS_SP_ROOMY = 46f
        private const val DATE_SP_NORMAL = 13f
        private const val DATE_SP_ROOMY = 15f
    }
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
