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
import kotlinx.coroutines.flow.first

/**
 * 「接下来」焦点组件：一张卡只回答「现在最该管哪一件事」。
 *
 * 候选按可行动性与紧迫度排序：
 * 1. 今天到期、还没完成的待办（可行动，直接在卡上勾掉）；
 * 2. 3 天内要扣费的订阅（错过就是真金白银）；
 * 3. 最近的倒数事件（生日/纪念日/倒计时）。
 *
 * 「完成后接下一件」不需要本组件做任何事：底层一变，数据变更刷新链路
 * （WidgetRefreshConsumer）会带着新的第一名重绘。
 */
class NextUpWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
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
        val counters = LifeCounterWidgetProvider().collectCounterEvents(context)

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, pickFocus(todos, renewals, counters))
        }
    }

    /** 焦点条目：kind 决定标签与配色族，itemId 只有待办有（驱动卡上的勾选动作）。 */
    internal data class NextUpFocus(
        val kind: Kind,
        val title: String,
        /** 次要说明行原始值：订阅是「M/d」，倒数是目标日 ISO 文本；待办为空（bindFocus 落「今天到期」）。 */
        val meta: String?,
        /** 大字数值：待办是「今天」，订阅是金额，倒数是天数。 */
        val bigValue: String,
        val itemId: Long? = null
    )

    internal enum class Kind { TODO, RENEWAL, COUNTDOWN }

    internal fun pickFocus(
        todos: List<com.palmnote.data.db.entity.LifeItem>,
        renewals: List<SubscriptionWidgetProvider.Renewal>,
        counters: List<LifeCounterWidgetProvider.CounterEvent>
    ): NextUpFocus? {
        todos.firstOrNull()?.let { todo ->
            return NextUpFocus(Kind.TODO, todo.title, null, bigValue = "", itemId = todo.id)
        }
        renewals.filter { it.daysLeft <= RENEWAL_WINDOW_DAYS }.minByOrNull { it.daysLeft }?.let { renewal ->
            return NextUpFocus(
                Kind.RENEWAL, renewal.name,
                java.time.LocalDate.now().plusDays(renewal.daysLeft)
                    .format(java.time.format.DateTimeFormatter.ofPattern("M/d")),
                WidgetHelper.moneyText(renewal.price).ifBlank { "—" }
            )
        }
        counters.minByOrNull { it.daysLeft }?.let { counter ->
            return NextUpFocus(
                Kind.COUNTDOWN, counter.name,
                // 目标日按 ISO 落进 meta，渲染时再按 locale 格式化（pickFocus 无 Context）
                meta = counter.targetDate.toString(),
                bigValue = "${counter.daysLeft}"
            )
        }
        return null
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        focus: NextUpFocus?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_next_up_unified)
        if (focus == null) {
            views.setViewVisibility(R.id.widget_next_up_content, View.GONE)
            views.setViewVisibility(R.id.widget_next_up_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_next_up_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_next_up_empty, View.GONE)
            bindFocus(context, views, focus)
        }
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_NEXT_UP + appWidgetId,
                WidgetDeepLink.TAB_LIFE
            )
        )
        return views
    }

    /**
     * 焦点内容（v4）：整卡按 kind 取一套色族 —— 徽章/图标/标签/大字数值同族上色，
     * 主体是一张色族淡底的大 chip；时效说明按 kind 落「今天到期 / M/d 扣费 / 目标日期」。
     */
    private fun bindFocus(context: Context, views: RemoteViews, focus: NextUpFocus) {
        val family = WidgetData.colorFamily(context, familyIndexOf(focus.kind))
        // 徽章图标随 kind 换：待办=勾、订阅=刷新、倒数=日历（稿子如此）
        views.setInt(R.id.widget_next_up_badge, "setColorFilter", family.hue)
        views.setImageViewResource(R.id.widget_next_up_badge_icon, badgeIconOf(focus.kind))
        // 主体大 chip：色族淡底 + 同族色点
        views.setInt(R.id.widget_next_up_chip_bg, "setColorFilter", family.tint)
        views.setInt(R.id.widget_next_up_chip_dot, "setColorFilter", family.hue)
        views.setTextViewText(R.id.widget_next_up_kind, context.getString(kindLabelOf(focus.kind)))
        // 类目文字是稿子的 .lbl 三级灰（色点已经标了族色，文字不再上色）
        views.setTextViewText(R.id.widget_next_up_title, focus.title)
        // 待办的大字是「今天」，倒数带单位（pickFocus 无 Context，文案在这里落）
        views.setTextViewText(R.id.widget_next_up_value, resolveValue(context, focus))
        // 主角数值用类型族色（稿子如此，同族才成体系）
        views.setTextColor(R.id.widget_next_up_value, family.hue)
        bindMeta(context, views, focus)
        if (focus.kind == Kind.TODO && focus.itemId != null) {
            views.setViewVisibility(R.id.widget_next_up_action, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_next_up_action,
                TodoToggleReceiver.togglePendingIntent(context, focus.itemId)
            )
        } else {
            views.setViewVisibility(R.id.widget_next_up_action, View.GONE)
        }
    }

    /** 时效说明行：待办=今天到期；订阅=M/d 扣费；倒数=目标日期（解析不出就原样展示 ISO 文本）。 */
    private fun bindMeta(context: Context, views: RemoteViews, focus: NextUpFocus) {
        val metaText = when (focus.kind) {
            Kind.TODO -> context.getString(R.string.widget_next_up_today_due)
            Kind.RENEWAL -> context.getString(R.string.widget_next_up_renewal_date, focus.meta.orEmpty())
            Kind.COUNTDOWN -> focus.meta?.let { raw ->
                runCatching { java.time.LocalDate.parse(raw) }.getOrNull()
                    ?.format(java.time.format.DateTimeFormatter.ofPattern(context.getString(R.string.widget_date_format_cn)))
                    ?: raw
            }.orEmpty()
        }
        views.setTextViewText(R.id.widget_next_up_meta, metaText)
        views.setViewVisibility(R.id.widget_next_up_meta, if (metaText.isBlank()) View.GONE else View.VISIBLE)
    }

    private fun familyIndexOf(kind: Kind): Int = when (kind) {
        Kind.TODO -> 1
        Kind.RENEWAL -> 4
        Kind.COUNTDOWN -> 0
    }

    private fun badgeIconOf(kind: Kind): Int = when (kind) {
        Kind.TODO -> R.drawable.widget_ic_check_white
        Kind.RENEWAL -> R.drawable.widget_ic_refresh_white
        Kind.COUNTDOWN -> R.drawable.widget_ic_calendar_white
    }

    private fun kindLabelOf(kind: Kind): Int = when (kind) {
        Kind.TODO -> R.string.widget_next_up_kind_todo
        Kind.RENEWAL -> R.string.widget_next_up_kind_renewal
        Kind.COUNTDOWN -> R.string.widget_next_up_kind_countdown
    }

    /** 大字数值文案：待办=今天；倒数=N 天；订阅=金额原文。 */
    private fun resolveValue(context: Context, focus: NextUpFocus): String = when (focus.kind) {
        Kind.TODO -> context.getString(R.string.widget_next_up_due_today)
        Kind.COUNTDOWN -> "${focus.bigValue} ${context.getString(R.string.widget_next_up_days_unit)}"
        Kind.RENEWAL -> focus.bigValue
    }

    internal companion object {
        /** 订阅进入「接下来」的窗口：3 天内要扣费才算紧迫。 */
        const val RENEWAL_WINDOW_DAYS = 3L
    }
}
