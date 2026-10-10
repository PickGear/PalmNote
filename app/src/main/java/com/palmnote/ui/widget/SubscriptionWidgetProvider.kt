package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.util.nextBillingDate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 订阅提醒 3×2：列出最近几次扣费（名称 + 价格 + 还有几天），最多 3 条。
 * 到期口径与提醒链路共用 [nextBillingDate]；点条目进生活页。
 */
class SubscriptionWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeTemplateDao(): LifeTemplateDao
        fun lifeItemDao(): LifeItemDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val includeDemo = entryPoint.preferencesManager().lifeDemoMode.first()
        val template = entryPoint.lifeTemplateDao().getAllVisibleTemplates().first()
            .firstOrNull { it.name.contains(BuiltinTemplates.SUBSCRIPTION_KEYWORD) }
        val renewals = template?.let { fetchRenewals(entryPoint.lifeItemDao(), it.id, includeDemo) }.orEmpty()

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, renewals)
        }
    }

    /** 一条待扣费：名称、价格原文、还有几天。 */
    internal data class Renewal(val name: String, val price: String, val daysLeft: Long)

    private suspend fun fetchRenewals(
        lifeItemDao: LifeItemDao,
        templateId: Long,
        includeDemo: Boolean
    ): List<Renewal> {
        val today = LocalDate.now()
        return lifeItemDao.getWidgetItemsByTemplate(templateId, includeDemo, com.palmnote.data.db.dao.LIFE_DEMO_META)
            .first()
            .filter { it.status == "ACTIVE" }
            .mapNotNull { item -> item.toRenewal(today) }
            .sortedBy { it.daysLeft }
            .take(MAX_ROWS)
    }

    private fun com.palmnote.data.db.entity.LifeItem.toRenewal(today: LocalDate): Renewal? {
        val obj = runCatching { Json.parseToJsonElement(fieldsData) as? JsonObject }.getOrNull() ?: return null
        val billingDay = (obj["billingDay"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: (obj["billing_day"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: return null
        val cycle = (obj["billingCycle"] as? JsonPrimitive)?.content ?: "monthly"
        val lastBilled = (obj["lastBilledDate"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        val next = nextBillingDate(billingDay, cycle, lastBilled, today) ?: return null
        return Renewal(
            name = title,
            price = (obj["price"] as? JsonPrimitive)?.content.orEmpty(),
            daysLeft = ChronoUnit.DAYS.between(today, next)
        )
    }

    internal fun bindViews(context: Context, appWidgetId: Int, renewals: List<Renewal>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_subscription_unified)
        // 装饰色走色族（不占全局强调色）：徽章用紫、徽章里的时钟字形着白
        views.setInt(R.id.widget_subscription_badge, "setColorFilter", WidgetData.colorFamily(context, 4).hue)
        views.setInt(R.id.widget_subscription_badge_icon, "setColorFilter", android.graphics.Color.WHITE)

        val today = LocalDate.now()
        val monthCount = renewals.count { isSameMonth(today, today.plusDays(it.daysLeft)) }
        views.setTextViewText(
            R.id.widget_subscription_month_count,
            context.resources.getQuantityString(
                R.plurals.widget_subscription_month_count, monthCount, monthCount
            )
        )

        // 时间轴：一行一次待扣费，色点落在竖线上
        views.removeAllViews(R.id.widget_subscription_list)
        renewals.forEachIndexed { index, renewal ->
            views.addView(R.id.widget_subscription_list, rowViews(context, renewal, index, today))
        }

        val empty = renewals.isEmpty()
        views.setViewVisibility(R.id.widget_subscription_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_subscription_list, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_subscription_line, if (empty) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_SUBSCRIPTION + appWidgetId,
                WidgetDeepLink.TAB_LIFE
            )
        )
        return views
    }

    /** 下一次扣费日是否落在与今天同一个月（「本月 N 笔」的口径）。 */
    private fun isSameMonth(today: LocalDate, date: LocalDate): Boolean =
        date.year == today.year && date.month == today.month

    /** 时间轴一行：色点 + 日期 + 名称 + 金额（金额与色点同族上色）。 */
    private fun rowViews(context: Context, renewal: Renewal, index: Int, today: LocalDate): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_subscription_item)
        val family = WidgetData.colorFamily(context, index)
        row.setInt(R.id.widget_subscription_item_dot, "setColorFilter", family.hue)
        row.setTextColor(R.id.widget_subscription_item_price, family.hue)
        row.setTextViewText(
            R.id.widget_subscription_item_date,
            today.plusDays(renewal.daysLeft).format(DateTimeFormatter.ofPattern("M/d"))
        )
        row.setTextViewText(R.id.widget_subscription_item_name, renewal.name)
        row.setTextViewText(R.id.widget_subscription_item_price, renewal.price)
        return row
    }

    private companion object {
        /** 3×2 放得下 3 行。 */
        const val MAX_ROWS = 3
    }
}
