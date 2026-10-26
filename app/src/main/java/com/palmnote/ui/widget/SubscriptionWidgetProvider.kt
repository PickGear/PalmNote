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
 * 订阅提醒（v22/v37）：列出最近几次扣费（名称 + 价格 + 还有几天），最多 3 条。
 * 到期口径与提醒链路共用 [nextBillingDate]；点条目进生活页。
 *
 * 四档构图按 [WidgetSizeClass] 切档：
 * - WIDE 4×2：hero 本月合计 + 三行待扣（日期在外 + 胶囊在内）；
 * - SQUARE 2×2：hero + 两行待扣；
 * - BAR 4×1：三列读数（本月 / 下个 / 金额）+ 分段条；
 * - TALL 4×4（v37 §2-8）：hero 合计 + 三 KPI（本月/年度/下个）+ 未来 6 个月扣费柱图
 *   （按周期投影，见 [projectMonthlyCharges]）+ 最近两笔明细。
 */
// open：尺寸由 WidgetSizeClass 在 bindViews 内归档（1×2 竖条已随「目录只做五档」移除）
open class SubscriptionWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeTemplateDao(): LifeTemplateDao
        fun lifeItemDao(): LifeItemDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            WidgetEntryPoint::class.java
        )
        val includeDemo = entryPoint.preferencesManager().lifeDemoMode.first()
        val template = entryPoint.lifeTemplateDao().getAllVisibleTemplates().first()
            .firstOrNull { it.name.contains(BuiltinTemplates.SUBSCRIPTION_KEYWORD) }
        val entries = template?.let { fetchSubEntries(entryPoint.lifeItemDao(), it.id, includeDemo) }.orEmpty()
        val today = LocalDate.now()
        val renewals = entries
            .filter { it.amount != null }
            .mapNotNull { entry ->
                val next = entry.nextDate(today) ?: return@mapNotNull null
                Renewal(entry.name, entry.priceText, ChronoUnit.DAYS.between(today, next))
            }
            .sortedBy { it.daysLeft }
            .take(MAX_ROWS)

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                bindViews(context, appWidgetId, renewals, width, height, entries)
            }
        }
    }

    /** 一条待扣费：名称、价格原文、还有几天。 */
    internal data class Renewal(val name: String, val price: String, val daysLeft: Long)

    /**
     * 一条订阅的全量投影要素（4×4 用）：[amount] 解析不出（价格非数字）为 null，
     * 该条不进任何合计与柱图（宁可少算，不摆错数）。
     */
    internal data class SubEntry(
        val name: String,
        val priceText: String,
        val amount: Double?,
        val billingDay: Int,
        val cycle: String,
        val lastBilled: LocalDate?
    ) {
        /** 下次扣费日（今天或之后）；billingDay 非法时 null。 */
        fun nextDate(today: LocalDate): LocalDate? =
            nextBillingDate(billingDay, cycle, lastBilled, today)
    }

    internal suspend fun fetchRenewals(
        lifeItemDao: LifeItemDao,
        templateId: Long,
        includeDemo: Boolean
    ): List<Renewal> {
        val today = LocalDate.now()
        return fetchSubEntries(lifeItemDao, templateId, includeDemo)
            .filter { it.amount != null }
            .mapNotNull { entry ->
                val next = entry.nextDate(today) ?: return@mapNotNull null
                Renewal(
                    name = entry.name,
                    price = entry.priceText,
                    daysLeft = ChronoUnit.DAYS.between(today, next)
                )
            }
            .sortedBy { it.daysLeft }
            .take(MAX_ROWS)
    }

    /**
     * 全部在用订阅的投影要素（4×4 用）。价格解析不出（[SubEntry.amount] = null）的条目
     * 也返回——明细行照样显示名字与日期，只是不进合计。
     */
    internal suspend fun fetchSubEntries(
        lifeItemDao: LifeItemDao,
        templateId: Long,
        includeDemo: Boolean
    ): List<SubEntry> {
        val today = LocalDate.now()
        return lifeItemDao.getWidgetItemsByTemplate(templateId, includeDemo, com.palmnote.data.db.dao.LIFE_DEMO_META)
            .first()
            .filter { it.status == "ACTIVE" }
            .mapNotNull { item -> item.toSubEntry() }
            .sortedBy { entry -> entry.nextDate(today) }
    }

    private fun com.palmnote.data.db.entity.LifeItem.toSubEntry(): SubEntry? {
        val obj = runCatching { Json.parseToJsonElement(fieldsData) as? JsonObject }.getOrNull() ?: return null
        val billingDay = (obj["billingDay"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: (obj["billing_day"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: return null
        val cycle = (obj["billingCycle"] as? JsonPrimitive)?.content ?: "monthly"
        val lastBilled = (obj["lastBilledDate"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        val priceText = (obj["price"] as? JsonPrimitive)?.content.orEmpty()
        return SubEntry(
            name = title,
            priceText = WidgetHelper.moneyText(priceText),
            amount = priceText.removePrefix("¥").toDoubleOrNull(),
            billingDay = billingDay,
            cycle = cycle,
            lastBilled = lastBilled
        )
    }

    /**
     * 未来 [months] 个月（含本月）的扣费合计（分，逐月）。按各订阅的周期从下次扣费日
     * 逐次投影（[nextBillingDate] 以「上次扣费 + 一个周期」为下限迭代，不会重复计同一周期）。
     * 价格解析不出的条目不进任何一个月。
     */
    internal fun projectMonthlyCharges(
        entries: List<SubEntry>,
        today: LocalDate,
        months: Int = 6
    ): List<Long> {
        val totals = LongArray(months)
        val horizon = today.withDayOfMonth(1).plusMonths(months.toLong())
        entries.forEach { entry ->
            val amount = entry.amount ?: return@forEach
            var charge = entry.nextDate(today) ?: return@forEach
            val step = when (entry.cycle) {
                "yearly" -> 12L
                "quarterly" -> 3L
                else -> 1L
            }
            while (charge.isBefore(horizon)) {
                val idx = ChronoUnit.MONTHS.between(
                    today.withDayOfMonth(1), charge.withDayOfMonth(1)
                ).toInt()
                if (idx in 0 until months) {
                    totals[idx] += Math.round(amount * 100)
                }
                // plusMonths 自动钳短月（31 号在 2 月落 28/29），与 billingDayIn 同规
                charge = charge.plusMonths(step)
            }
        }
        return totals.toList()
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        renewals: List<Renewal>,
        widthDp: Int = DEFAULT_WIDTH_DP,
        heightDp: Int = DEFAULT_HEIGHT_DP,
        entries: List<SubEntry> = emptyList()
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_sub_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_sub_v22_square
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> R.layout.widget_sub_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        if (size != WidgetSizeClass.TALL) {
            // 徽章用族色 c4 紫（稿订阅提醒的标识色）；tall 布局的徽章/标题由 bindTallHeader 上
            views.setInt(R.id.widget_subscription_badge, "setColorFilter", familyD(context, 3))
            views.setTextViewText(
                R.id.widget_subscription_title,
                context.getString(
                    if (size == WidgetSizeClass.CHIP) R.string.widget_sub_title_next
                    else if (size == WidgetSizeClass.SQUARE) R.string.widget_subscription_title_short
                    else R.string.widget_subscription_title
                )
            )
        }
        // 整卡出口四档统一挂（行点击挂行的 id,与 widget_layout 不冲突）
        WidgetHelper.linkViews(context, views, WidgetDeepLink.SEG_SUBSCRIPTION + appWidgetId, WidgetDeepLink.TAB_LIFE)
        when (size) {
            WidgetSizeClass.TALL -> bindTall4x4(context, views, entries)
            WidgetSizeClass.WIDE ->
                bindWide(context, views, renewals)
            WidgetSizeClass.SQUARE -> bindSquare(context, views, renewals)
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> bindBar(context, views, renewals)
        }
        return views
    }

    /**
     * 4×4（v37 §2-8）：hero 合计 + 三 KPI（本月/年度/下个）+ 未来 6 个月扣费柱图 +
     * 最近两笔明细。柱图按各订阅周期投影（[projectMonthlyCharges]）；一条订阅都没有时空白态。
     */
    private fun bindTall4x4(context: Context, views: RemoteViews, entries: List<SubEntry>) {
        val today = LocalDate.now()
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_refresh_white,
            title = context.getString(R.string.widget_subscription_title),
            meta = null,
            familyIdx = 3
        )
        if (entries.isEmpty()) {
            WidgetV22.bindTallHero(context, views, context.getString(R.string.widget_asset_dash), null)
            WidgetV22.bindEmpty(
                context, views,
                iconRes = R.drawable.widget_ic_refresh_white,
                title = context.getString(R.string.widget_subscription_empty),
                hint = context.getString(R.string.widget_dash_empty_hint),
                familyIdx = 3
            )
            WidgetV22.tallSections(context, views, "empty")
            return
        }
        // hero：全部在用订阅的合计（与 4×2 同口径：列表全部条目之和，不分本月下月）
        val sumAll = entries.mapNotNull { it.amount }.sum()
        WidgetV22.bindTallHero(
            context, views,
            if (entries.any { it.amount == null }) context.getString(R.string.widget_asset_dash)
            else formatSum(sumAll),
            null
        )
        bindTallKpis(context, views, entries, today)
        val showChart = bindTallChart(context, views, entries, today)
        // 最近两笔明细（日期 + 名 + 金额）
        val rows = entries.take(2).mapIndexed { i, entry ->
            val date = entry.nextDate(today)?.format(DateTimeFormatter.ofPattern("M/d")) ?: "—"
            WidgetV22.DualItem(date, entry.name, entry.priceText, ROW_FAMILIES[i % ROW_FAMILIES.size])
        }
        WidgetV22.bindRows(context, views, R.id.widget_tall_rows, rows)
        WidgetV22.tallSections(
            context, views,
            "kpis",
            if (showChart) "chart" else "",
            "rows"
        )
    }

    /** 4×4 KPI：本月（投影首月）/ 年度（12 个月投影和）/ 下个（最近一笔日期）。 */
    private fun bindTallKpis(context: Context, views: RemoteViews, entries: List<SubEntry>, today: LocalDate) {
        val monthly = projectMonthlyCharges(entries, today, months = 12)
        val nextEntry = entries.firstOrNull()
        val nextDate = nextEntry?.nextDate(today)
        WidgetV22.bindKpis(
            context, views,
            listOf(
                WidgetV22.Kpi(
                    // 4×4 的第一格是「本月投影」，不能复用 4×2 那颗「合计」（那是全量之和）
                    context.getString(R.string.widget_sub_label_this_month),
                    formatSum(monthly.first() / 100.0),
                    null,
                    3
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_sub_label_year),
                    formatSum(monthly.sum() / 100.0),
                    null,
                    5
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_sub_label_next),
                    nextDate?.format(DateTimeFormatter.ofPattern("M/d"))
                        ?: context.getString(R.string.widget_asset_dash),
                    nextEntry?.name,
                    4
                )
            )
        )
    }

    /**
     * 4×4 柱图：未来 6 个月扣费（含本月；峰月粉 + 金额标）。
     * @return 是否真的摆了柱图（全 0 —— 价格都解析不出 —— 时整块收起）
     */
    private fun bindTallChart(context: Context, views: RemoteViews, entries: List<SubEntry>, today: LocalDate): Boolean {
        val charges = projectMonthlyCharges(entries, today, months = 6)
        val maxCharge = charges.maxOrNull() ?: 0L
        if (maxCharge <= 0L) return false
        // 峰月只认一根（并列时取最早那个），否则同额月份全着粉、全带标
        val peak = charges.indexOf(maxCharge)
        val bars = charges.mapIndexed { i, v ->
            val month = today.withDayOfMonth(1).plusMonths(i.toLong())
            WidgetV22.ChartBar(
                percent = (v * 100 / maxCharge).toInt(),
                familyIdx = if (i == peak) 5 else 3,
                label = if (i == peak) WidgetData.formatChartValue(v) else null,
                axis = month.format(DateTimeFormatter.ofPattern("M")),
                // 本图是「未来 6 个月」，首列就是本月
                today = i == 0
            )
        }
        WidgetV22.bindChart(context, views, context.getString(R.string.widget_sub_chart_6m), null, bars)
        return true
    }

    /** 四档共用的出口：点卡片进生活页。 */

    /** 4×2：hero 本月合计 + 三行待扣（日期在外 + 胶囊在内）。 */
    private fun bindWide(context: Context, views: RemoteViews, renewals: List<Renewal>) {
        bindMonthSum(views, renewals)
        views.setTextViewText(R.id.widget_subscription_title, context.getString(R.string.widget_subscription_title))
        val rows = renewals.take(MAX_ROWS)
        views.removeAllViews(R.id.widget_subscription_list)
        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_subscription_list, View.GONE)
            views.setViewVisibility(R.id.widget_subscription_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_subscription_list, View.VISIBLE)
            views.setViewVisibility(R.id.widget_subscription_empty, View.GONE)
            rows.forEachIndexed { index, renewal ->
                views.addView(
                    R.id.widget_subscription_list,
                    rowViews(context, renewal, index, rows.lastIndex)
                )
            }
        }
    }

    /** 2×2：hero 本月合计 + 两行待扣。 */
    private fun bindSquare(context: Context, views: RemoteViews, renewals: List<Renewal>) {
        bindMonthSum(views, renewals)
        views.setTextViewText(R.id.widget_subscription_title, context.getString(R.string.widget_subscription_title_short))
        val rows = renewals.take(2)
        views.removeAllViews(R.id.widget_subscription_list)
        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_subscription_list, View.GONE)
            views.setViewVisibility(R.id.widget_subscription_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_subscription_list, View.VISIBLE)
            views.setViewVisibility(R.id.widget_subscription_empty, View.GONE)
            rows.forEachIndexed { index, renewal ->
                views.addView(
                    R.id.widget_subscription_list,
                    rowViews(context, renewal, index, rows.lastIndex)
                )
            }
        }
    }

    /** 4×1：三列读数（本月 / 下个 / 金额）+ 分段条（本月待扣按金额占比）。 */
    private fun bindBar(context: Context, views: RemoteViews, renewals: List<Renewal>) {
        views.setTextViewText(R.id.widget_subscription_title, context.getString(R.string.widget_subscription_title_short))
        val today = java.time.LocalDate.now()
        val monthRenewals = renewals
        val monthSum = monthSumOf(renewals)
        views.setTextViewText(
            R.id.widget_subscription_bar_month,
            if (monthSum == null) context.getString(R.string.widget_asset_dash) else formatSum(monthSum)
        )
        val first = renewals.firstOrNull()
        if (first == null) {
            views.setTextViewText(R.id.widget_subscription_bar_next, context.getString(R.string.widget_asset_dash))
            views.setTextViewText(R.id.widget_subscription_bar_amount, context.getString(R.string.widget_asset_dash))
            views.setViewVisibility(R.id.widget_subscription_seg_host, View.GONE)
        } else {
            views.setTextViewText(
                R.id.widget_subscription_bar_next,
                today.plusDays(first.daysLeft).format(DateTimeFormatter.ofPattern("M/d"))
            )
            views.setTextViewText(R.id.widget_subscription_bar_amount, WidgetHelper.moneyText(first.price))
            // 分段条：一格 = 本月一笔待扣（金额占比）；价格解析不出整条收起
            val amounts = monthRenewals.mapNotNull { it.price.removePrefix("¥").toDoubleOrNull() }
            if (amounts.size == monthRenewals.size && amounts.isNotEmpty() && amounts.sum() > 0) {
                val seg = widgetViews(context, R.layout.widget_v22_seg)
                WidgetV22.bindSeg(
                    context, seg,
                    counts = amounts.map { Math.round(it * 100).toInt() },
                    colors = monthRenewals.indices.map { familyD(context, ROW_FAMILIES[it % ROW_FAMILIES.size]) }
                )
                views.removeAllViews(R.id.widget_subscription_seg_host)
                views.addView(R.id.widget_subscription_seg_host, seg)
                views.setViewVisibility(R.id.widget_subscription_seg_host, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_subscription_seg_host, View.GONE)
            }
        }
    }

    /** 2×1：徽章 + 「N 天」+ 三根微柱。 */


    /** hero 本月合计：解析不出整块收起（宁可少一个 Hero 也不能摆一个错数）。 */
    private fun bindMonthSum(views: RemoteViews, renewals: List<Renewal>) {
        val monthSum = monthSumOf(renewals)
        if (monthSum == null) {
            views.setViewVisibility(R.id.widget_subscription_month_sum, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_subscription_month_sum, View.VISIBLE)
            views.setTextViewText(R.id.widget_subscription_month_sum, formatSum(monthSum))
        }
    }

    /** 一行待扣：日期列 + 胶囊（色点 + 名称 + 金额）。3 天内整行提色高亮。 */
    private fun rowViews(context: Context, renewal: Renewal, index: Int, lastIndex: Int): RemoteViews {
        val row = widgetViews(
            context,
            if (index == lastIndex) R.layout.widget_sub_v22_row_end else R.layout.widget_sub_v22_row
        )
        val familyIdx = ROW_FAMILIES[index % ROW_FAMILIES.size]
        val today = java.time.LocalDate.now()
        val upcoming = renewal.daysLeft <= UPCOMING_DAYS
        row.setTextViewText(
            R.id.widget_sub_row_date,
            today.plusDays(renewal.daysLeft).format(DateTimeFormatter.ofPattern("M/d"))
        )
        row.setTextColor(
            R.id.widget_sub_row_date,
            if (upcoming) familyInk(context, familyIdx) else context.getColor(R.color.widget_v22_text_2)
        )
        val chip = widgetViews(context, R.layout.widget_v22_chip)
        WidgetV22.bindChip(
            context, chip,
            name = renewal.name,
            sub = null,
            value = WidgetHelper.moneyText(renewal.price),
            familyIdx = familyIdx,
            showDot = true
        )
        row.addView(R.id.widget_sub_row_chip_host, chip)
        return row
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
     * 待扣合计（稿口径）：**列表全部条目之和**（稿卡「¥48」= 25+15+8,不分本月下月）;
     * 任一价格解析失败返回 null —— 宁可少一个 Hero 也不能摆一个错数。
     */
    internal fun monthSumOf(renewals: List<Renewal>): Double? {
        if (renewals.isEmpty()) return null
        var sum = 0.0
        renewals.forEach { renewal ->
            val value = renewal.price.removePrefix("¥").toDoubleOrNull() ?: return null
            sum += value
        }
        return sum
    }

    private fun familyD(context: Context, index: Int) = WidgetV22.family(context, index).d

    private fun familyInk(context: Context, index: Int) = WidgetV22.family(context, index).ink

    private companion object {
        /** 4×2 最多列几行待扣（稿口径三行）。 */
        const val MAX_ROWS = 3

        /** 组件声明的默认尺寸（4×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 「即将扣费」高亮窗口：3 天内（与「接下来」组件的订阅窗口一致）。 */
        const val UPCOMING_DAYS = 3L

        /** 行→族色（稿内取色）：橙 / 蓝 / 绿。 */
        val ROW_FAMILIES = intArrayOf(4, 0, 1)
    }
}
