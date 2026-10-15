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
// open：尺寸变体（SubscriptionMini/SubscriptionNarrow）薄子类继承，复用同一套取数与切档逻辑
open class SubscriptionWidgetProvider : ScopedWidgetProvider() {

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
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                when {
                    width < NARROW_WIDTH_DP -> narrowViews(context, appWidgetId, renewals)
                    height < STRIP_HEIGHT_DP -> minimalViews(context, appWidgetId, renewals)
                    else -> bindViews(context, appWidgetId, renewals)
                }
            }
        }
    }

    /** 一条待扣费：名称、价格原文、还有几天。 */
    internal data class Renewal(val name: String, val price: String, val daysLeft: Long)

    internal suspend fun fetchRenewals(
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
        val monthRenewals = renewals.filter { isSameMonth(today, today.plusDays(it.daysLeft)) }
        views.setTextViewText(
            R.id.widget_subscription_month_count,
            context.resources.getQuantityString(
                R.plurals.widget_subscription_month_count, monthRenewals.size, monthRenewals.size
            )
        )
        // Hero：本月合计金额。价格是条目字段里的**原文**（字符串），解析不出就整块收起，
        // 宁可少一个 Hero 也不能摆一个错数。
        val monthSum = monthSumOf(renewals, today)
        if (monthSum == null) {
            views.setViewVisibility(R.id.widget_subscription_month_sum, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_subscription_month_sum, View.VISIBLE)
            views.setTextViewText(R.id.widget_subscription_month_sum, formatSum(monthSum))
        }

        // chip 行：一行一次待扣费，行底色/色点/金额同族上色
        views.removeAllViews(R.id.widget_subscription_list)
        renewals.forEachIndexed { index, renewal ->
            views.addView(R.id.widget_subscription_list, rowViews(context, renewal, index, today))
        }

        val empty = renewals.isEmpty()
        views.setViewVisibility(R.id.widget_subscription_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_subscription_list, if (empty) View.GONE else View.VISIBLE)
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

    /** 本月合计：整元不带小数（¥54），带小数保留两位（¥54.50）。 */
    private fun formatSum(sum: Double): String {
        val rounded = Math.round(sum * 100) / 100.0
        return if (rounded == Math.floor(rounded)) {
            "¥${rounded.toInt()}"
        } else {
            String.format(java.util.Locale.CHINA, "¥%.2f", rounded)
        }
    }

    /**
     * 本月扣费合计（Hero 用）：过滤出本月到期的条目后把价格原文解析求和；
     * 条目为空或任一价格解析失败都返回 null —— 宁可少一个 Hero 也不能摆一个错数。
     */
    internal fun monthSumOf(renewals: List<Renewal>, today: LocalDate): Double? {
        val monthRenewals = renewals.filter { isSameMonth(today, today.plusDays(it.daysLeft)) }
        if (monthRenewals.isEmpty()) return null
        var sum = 0.0
        monthRenewals.forEach { renewal ->
            val value = renewal.price.removePrefix("¥").toDoubleOrNull() ?: return null
            sum += value
        }
        return sum
    }

    /** 2×1 极简档：最近一笔「金额 / 名称 · 日期」，3 天内附「即将扣费」。internal = 预览图生成器复用。 */
    internal fun minimalViews(context: Context, appWidgetId: Int, renewals: List<Renewal>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_subscription_minimal)
        val today = LocalDate.now()
        val first = renewals.firstOrNull()
        if (first == null) {
            views.setViewVisibility(R.id.widget_subscription_min_amount, View.GONE)
            views.setTextViewText(R.id.widget_subscription_min_meta, context.getString(R.string.widget_subscription_empty))
        } else {
            views.setViewVisibility(R.id.widget_subscription_min_amount, View.VISIBLE)
            views.setTextViewText(R.id.widget_subscription_min_amount, first.price)
            views.setTextViewText(
                R.id.widget_subscription_min_meta,
                "${first.name} · ${today.plusDays(first.daysLeft).format(DateTimeFormatter.ofPattern("M/d"))}"
            )
            val upcoming = first.daysLeft <= UPCOMING_DAYS
            views.setViewVisibility(R.id.widget_subscription_min_upcoming, if (upcoming) View.VISIBLE else View.GONE)
            if (upcoming) {
                views.setTextColor(
                    R.id.widget_subscription_min_upcoming,
                    WidgetData.colorFamily(context, ROW_FAMILIES.first()).hue
                )
            }
        }
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

    /** 1×2 竖条：徽章 / 本月合计 / 本月笔数 / 最近一笔「日期 · 金额」。internal = 预览图生成器复用。 */
    internal fun narrowViews(context: Context, appWidgetId: Int, renewals: List<Renewal>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_subscription_narrow)
        val family = WidgetData.colorFamily(context, 4)
        views.setInt(R.id.widget_subscription_narrow_badge, "setColorFilter", family.tint)
        views.setInt(R.id.widget_subscription_narrow_badge_icon, "setColorFilter", android.graphics.Color.WHITE)
        val today = LocalDate.now()
        val empty = renewals.isEmpty()
        views.setViewVisibility(R.id.widget_subscription_narrow_content, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_subscription_narrow_empty, if (empty) View.VISIBLE else View.GONE)
        if (!empty) {
            val monthSum = monthSumOf(renewals, today)
            views.setViewVisibility(R.id.widget_subscription_narrow_sum, if (monthSum == null) View.GONE else View.VISIBLE)
            if (monthSum != null) views.setTextViewText(R.id.widget_subscription_narrow_sum, formatSum(monthSum))
            val monthCount = renewals.count { isSameMonth(today, today.plusDays(it.daysLeft)) }
            views.setTextViewText(
                R.id.widget_subscription_narrow_month,
                context.getString(R.string.widget_subscription_month_brief, monthCount)
            )
            val first = renewals.first()
            views.setTextViewText(R.id.widget_subscription_narrow_name, first.name)
            views.setTextViewText(
                R.id.widget_subscription_narrow_next,
                "${today.plusDays(first.daysLeft).format(DateTimeFormatter.ofPattern("M/d"))} · ${first.price}"
            )
        }
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

    /**
     * chip 行：日期列 + 色底 chip（色点 + 名称 + 即将扣费 + 金额）。
     * 族色按行取 [ROW_FAMILIES]（稿子里第四行跳过粉族用紫）；daysLeft ≤ 3 的行整行提色高亮。
     */
    private fun rowViews(context: Context, renewal: Renewal, index: Int, today: LocalDate): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_subscription_item)
        val family = WidgetData.colorFamily(context, ROW_FAMILIES[index % ROW_FAMILIES.size])
        val upcoming = renewal.daysLeft <= UPCOMING_DAYS
        val secondary = context.getColor(R.color.widget_v2_text_secondary)
        row.setInt(R.id.widget_subscription_item_bg, "setColorFilter", family.tint)
        row.setInt(R.id.widget_subscription_item_dot, "setColorFilter", family.hue)
        row.setTextColor(R.id.widget_subscription_item_price, family.hue)
        row.setTextViewText(
            R.id.widget_subscription_item_date,
            today.plusDays(renewal.daysLeft).format(DateTimeFormatter.ofPattern("M/d"))
        )
        row.setTextColor(R.id.widget_subscription_item_date, if (upcoming) family.hue else secondary)
        row.setTextViewText(R.id.widget_subscription_item_name, renewal.name)
        row.setTextViewText(R.id.widget_subscription_item_price, renewal.price)
        row.setViewVisibility(R.id.widget_subscription_item_upcoming, if (upcoming) View.VISIBLE else View.GONE)
        if (upcoming) row.setTextColor(R.id.widget_subscription_item_upcoming, family.hue)
        return row
    }

    private companion object {
        /** 真机 3×2 高 190dp，放得下 4 行（稿子也是 4 条）。 */
        const val MAX_ROWS = 4

        /** 组件声明的默认尺寸（3×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 180
        const val DEFAULT_HEIGHT_DP = 110

        /** 宽度 <150dp 切 1×2 竖条；高度 <100dp 切 2×1 极简。 */
        const val NARROW_WIDTH_DP = 150
        const val STRIP_HEIGHT_DP = 100

        /** 「即将扣费」高亮窗口：3 天内（与「接下来」组件的订阅窗口一致）。 */
        const val UPCOMING_DAYS = 3L

        /** 行→色族：稿子里 4 行依次是橙/蓝/绿/紫（跳过粉族）。 */
        val ROW_FAMILIES = intArrayOf(0, 1, 2, 4)
    }
}
