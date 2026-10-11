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
            // 大字数值吃逐实例强调色（配置页「配色」覆盖，null 跟随应用主题包）
            val accent = WidgetData.readAccentTheme(context, prefs, prefs.widgetAccentThemeFor(appWidgetId).first())
            bindViews(context, appWidgetId, pickFocus(todos, renewals, counters), accent)
        }
    }

    /** 焦点条目：kind 决定标签与配色族，itemId 只有待办有（驱动卡上的勾选动作）。 */
    internal data class NextUpFocus(
        val kind: Kind,
        val title: String,
        /** 次要说明行（扣费日 / 事件日期），可为空。 */
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
                renewal.price.ifBlank { "—" }
            )
        }
        counters.minByOrNull { it.daysLeft }?.let { counter ->
            return NextUpFocus(Kind.COUNTDOWN, counter.name, null, "${counter.daysLeft}")
        }
        return null
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        focus: NextUpFocus?,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_next_up_unified)
        if (focus == null) {
            views.setViewVisibility(R.id.widget_next_up_content, View.GONE)
            views.setViewVisibility(R.id.widget_next_up_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_next_up_content, View.VISIBLE)
            views.setViewVisibility(R.id.widget_next_up_empty, View.GONE)
            bindFocus(context, views, focus, accent)
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

    /** 焦点内容：徽标/类型标签按 kind 取色族，勾选动作只对待办有意义。 */
    private fun bindFocus(context: Context, views: RemoteViews, focus: NextUpFocus, accent: WidgetData.AccentTheme) {
        val familyIndex = when (focus.kind) {
            Kind.TODO -> 2
            Kind.RENEWAL -> 4
            Kind.COUNTDOWN -> 0
        }
        val family = WidgetData.colorFamily(context, familyIndex)
        views.setInt(R.id.widget_next_up_badge, "setColorFilter", family.hue)
        views.setInt(R.id.widget_next_up_badge_icon, "setColorFilter", android.graphics.Color.WHITE)
        views.setTextViewText(
            R.id.widget_next_up_kind,
            context.getString(
                when (focus.kind) {
                    Kind.TODO -> R.string.widget_next_up_kind_todo
                    Kind.RENEWAL -> R.string.widget_next_up_kind_renewal
                    Kind.COUNTDOWN -> R.string.widget_next_up_kind_countdown
                }
            )
        )
        views.setTextColor(R.id.widget_next_up_kind, family.hue)
        views.setTextViewText(R.id.widget_next_up_title, focus.title)
        views.setTextViewText(R.id.widget_next_up_meta, focus.meta.orEmpty())
        views.setViewVisibility(
            R.id.widget_next_up_meta,
            if (focus.meta.isNullOrBlank()) View.GONE else View.VISIBLE
        )
        // 待办的大字是「今天」，倒数带单位（pickFocus 无 Context，文案在这里落）
        views.setTextViewText(
            R.id.widget_next_up_value,
            when (focus.kind) {
                Kind.TODO -> context.getString(R.string.widget_next_up_due_today)
                Kind.COUNTDOWN -> "${focus.bigValue} ${context.getString(R.string.widget_next_up_days_unit)}"
                Kind.RENEWAL -> focus.bigValue
            }
        )
        // 主角数值用强调色（逐实例可覆盖）
        views.setTextColor(R.id.widget_next_up_value, accent.accent)
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

    internal companion object {
        /** 订阅进入「接下来」的窗口：3 天内要扣费才算紧迫。 */
        const val RENEWAL_WINDOW_DAYS = 3L
    }
}
