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
        views.removeAllViews(R.id.widget_subscription_list)
        // 两列色片网格（参考图那张「物品分布」的排布）：每行两个色片，条目数为奇数时补一个等宽占位
        renewals.chunked(2).forEachIndexed { pairIndex, pair ->
            val pairRow = RemoteViews(context.packageName, R.layout.widget_subscription_pair)
            pair.forEachIndexed { i, renewal ->
                pairRow.addView(
                    R.id.widget_subscription_pair_row,
                    rowViews(context, renewal, pairIndex * 2 + i)
                )
            }
            if (pair.size == 1) {
                pairRow.addView(
                    R.id.widget_subscription_pair_row,
                    RemoteViews(context.packageName, R.layout.widget_subscription_spacer)
                )
            }
            views.addView(R.id.widget_subscription_list, pairRow)
        }
        views.setViewVisibility(R.id.widget_subscription_empty, if (renewals.isEmpty()) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_subscription_list, if (renewals.isEmpty()) View.GONE else View.VISIBLE)
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

    /** 一行：名称 + 价格，右侧「还有几天」（当天显示「今天」）。 */
    private fun rowViews(context: Context, renewal: Renewal, index: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_subscription_item)
        // 每条一个色族：色片底淡彩、色点同族饱和（参考主页色片清单的用法）
        val family = WidgetData.colorFamily(context, index)
        row.setInt(R.id.widget_subscription_item_chip, "setColorFilter", family.tint)
        row.setInt(R.id.widget_subscription_item_dot, "setColorFilter", family.hue)
        row.setTextViewText(R.id.widget_subscription_item_name, renewal.name)
        row.setTextViewText(R.id.widget_subscription_item_price, renewal.price)
        row.setTextViewText(
            R.id.widget_subscription_item_days,
            if (renewal.daysLeft == 0L) {
                context.getString(R.string.widget_today)
            } else {
                context.resources.getQuantityString(
                    R.plurals.widget_days_remaining_format,
                    renewal.daysLeft.toInt(),
                    renewal.daysLeft
                )
            }
        )
        return row
    }

    private companion object {
        /** 3×2 放得下 3 行。 */
        const val MAX_ROWS = 3
    }
}
