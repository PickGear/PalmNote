package com.palmnote.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.model.BillType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter


object WidgetHelper {

    /**
     * 组件透明度落到**卡片底色**上：RemoteViews 不允许调用 `View.setAlpha`
     * （`can't use method with RemoteViews: setAlpha(float)`，框架只放行
     * `@RemotableViewMethod`），所以只能换成对应档位的半透明圆角底图。
     * 档位固定 4 档，滑块也按这 4 档吸附，所见即所得。
     */
    fun cardBackgroundFor(opacity: Float): Int = when {
        opacity >= 0.9f -> R.drawable.widget_card_round
        opacity >= 0.7f -> R.drawable.widget_card_round_80
        opacity >= 0.5f -> R.drawable.widget_card_round_60
        else -> R.drawable.widget_card_round_40
    }

    /** 整卡底色。不透明档不写入，省掉每次刷新的一次 RemoteViews 动作。 */
    fun applyWidgetOpacity(views: RemoteViews, opacity: Float) {
        if (opacity < 0.9f) {
            views.setInt(R.id.widget_layout, "setBackgroundResource", cardBackgroundFor(opacity))
        }
    }

    /**
     * 卡片内部的大块实底（概览的预算/目标卡、账单横幅、倒计时焦点卡、快捷入口的记一笔格）。
     * 底色变透但内层实底不动的话，只有卡片留白透出壁纸、看起来像没生效；
     * 这几块用 `setImageAlpha`（ImageView 的 @RemotableViewMethod）跟着一起变透。
     */
    fun applySurfaceAlpha(views: RemoteViews, viewId: Int, opacity: Float) {
        if (opacity < 0.9f) {
            views.setInt(viewId, "setImageAlpha", (opacity * 255f).toInt().coerceIn(0, 255))
        }
    }

    fun formatMoney(amount: Long): String {
        val yuan = amount / 100.0
        return String.format("¥%.2f", yuan)
    }

    fun formatMoneyDouble(amount: Double): String {
        return String.format("¥%.2f", amount)
    }

    fun daysUntil(dateMillis: Long): Long {
        val now = LocalDate.now()
        val target = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return target.toEpochDay() - now.toEpochDay()
    }

    fun formatDaysLeft(days: Long): String {
        return when {
            days < 0 -> "${-days}"
            days == 0L -> "0"
            else -> "$days"
        }
    }

    fun getDaysLabel(context: Context, days: Long): String {
        return when {
            days < 0 -> context.getString(R.string.widget_days_ago)
            days == 0L -> context.getString(R.string.widget_today)
            else -> context.getString(R.string.widget_days_unit)
        }
    }

    fun formatDate(context: Context, dateMillis: Long): String {
        val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val pattern = context.getString(R.string.widget_date_format_cn)
        return date.format(DateTimeFormatter.ofPattern(pattern))
    }

    // requestCode 必须由调用方保证全局唯一：Intent 仅 extras 不同（不参与 filterEquals 匹配），
    // 若复用同一 requestCode，FLAG_UPDATE_CURRENT 会互相覆盖，导致点击所有组件跳到同一个页面
    /**
     * 计数/倒数事件的深链：点事件 → 打开 App 并直达该记录详情页。
     * extras 不参与 PendingIntent 相等性判断，requestCode 必须逐 item 唯一。
     */
    fun createLifeEventPendingIntent(context: Context, itemId: Long): PendingIntent {
        val intent = Intent(context, com.palmnote.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WidgetDeepLink.KEY_TAB, WidgetDeepLink.TAB_LIFE)
            putExtra(WidgetDeepLink.KEY_ITEM_ID, itemId.toString())
        }
        return PendingIntent.getActivity(
            context, (WidgetDeepLink.SEG_EVENT_DETAIL.toLong() + itemId).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 页脚深链：到生活完整清单的指定模式（AGENDA / OVERDUE / UNSCHEDULED）。 */
    fun createLifeListPendingIntent(context: Context, requestCode: Int, mode: String): PendingIntent {
        val intent = Intent(context, com.palmnote.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WidgetDeepLink.KEY_TAB, WidgetDeepLink.TAB_LIFE)
            putExtra(WidgetDeepLink.KEY_LIST_MODE, mode)
        }
        return PendingIntent.getActivity(
            context, (WidgetDeepLink.SEG_LIFE_LIST + requestCode).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 账单横幅深链：到报表页（MainActivity 把 TAB_REPORT 转 pendingNavigation）。 */
    fun createReportPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, com.palmnote.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WidgetDeepLink.KEY_TAB, WidgetDeepLink.TAB_REPORT)
        }
        return PendingIntent.getActivity(
            context, WidgetDeepLink.SEG_BILL_REPORT + widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createPendingIntent(context: Context, requestCode: Int, tab: String): PendingIntent {
        val intent = Intent(context, com.palmnote.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 不写 EXTRA_APPWIDGET_ID：这里没有组件 id，消费端（handleWidgetIntent）也不读它，
            // 塞个号段进去只会让人误以为那是组件 id
            putExtra(WidgetDeepLink.KEY_TAB, tab)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 待办行：整行点击走 fill-in intent —— 集合里的行不能各自持有 PendingIntent，由模板补全。 */
    fun todoRowViews(
        context: Context,
        item: com.palmnote.data.db.entity.LifeItem,
        accentColor: Int
    ): RemoteViews {
        val itemView = RemoteViews(context.packageName, R.layout.widget_todo_item)
        val completed = item.status == "COMPLETED"
        // 左侧色条按条目 id 取调色板色：同一个条目每次刷新都是同一个颜色
        val palette = WidgetData.widgetPalette(context)
        itemView.setInt(R.id.widget_todo_bar, "setColorFilter", palette[(item.id % palette.size).toInt()])
        // 圆圈三层结构（灰环/可着色圆底/对勾）：主题色经 setColorFilter 运行时着色，全主题生效
        itemView.setViewVisibility(R.id.widget_item_check_ring, if (completed) View.GONE else View.VISIBLE)
        itemView.setViewVisibility(R.id.widget_item_check_fill, if (completed) View.VISIBLE else View.GONE)
        if (completed) {
            itemView.setInt(R.id.widget_item_check_fill, "setColorFilter", accentColor)
        }
        itemView.setViewVisibility(R.id.widget_item_check, if (completed) View.VISIBLE else View.GONE)
        // 无障碍：行内容描述（API 30+ RemoteViews 支持，低版本自动忽略）
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            itemView.setContentDescription(R.id.widget_todo_row, item.title)
        }
        itemView.setTextViewText(R.id.widget_item_text, item.title)
        itemView.setTextColor(
            R.id.widget_item_text,
            if (completed) context.getColor(R.color.widget_v2_text_tertiary) else context.getColor(R.color.widget_v2_text_primary)
        )
        val dueText = item.dueDate?.let { due ->
            val days = daysUntil(due)
            when {
                days < 0 -> context.getString(R.string.widget_overdue)
                days == 0L -> context.getString(R.string.widget_today)
                days == 1L -> context.getString(R.string.widget_tomorrow)
                // 复数走 getQuantityString（quantity 要 Int，格式化参数仍用 Long）
                else -> context.resources.getQuantityString(
                    R.plurals.widget_days_later, days.toInt(), days
                )
            }
        } ?: ""
        itemView.setTextViewText(R.id.widget_item_due, dueText)
        // 行内点击：只带条目 id，action 与组件由集合的 template 补全
        itemView.setOnClickFillInIntent(
            R.id.widget_todo_row,
            android.content.Intent().putExtra(TodoToggleReceiver.EXTRA_ITEM_ID, item.id)
        )
        return itemView
    }

    fun addCounterItemView(context: Context, views: RemoteViews, containerId: Int, title: String, dateMillis: Long) {
        val itemView = RemoteViews(context.packageName, R.layout.widget_counter_item)
        itemView.setTextViewText(R.id.widget_item_name, title)
        val days = daysUntil(dateMillis)
        itemView.setTextViewText(R.id.widget_item_days, formatDaysLeft(days))
        itemView.setTextViewText(R.id.widget_item_unit, getDaysLabel(context, days))
        views.addView(containerId, itemView)
    }

    fun addBillItemView(context: Context, views: RemoteViews, containerId: Int, bill: com.palmnote.data.db.entity.Bill) {
        val itemView = RemoteViews(context.packageName, R.layout.widget_bill_item)
        itemView.setTextViewText(R.id.widget_item_category, bill.category)
        val prefix = if (bill.type == BillType.EXPENSE) "-" else "+"
        itemView.setTextViewText(R.id.widget_item_amount, "$prefix${formatMoney(bill.amount)}")
        views.addView(containerId, itemView)
    }

    fun addAssetCategoryView(context: Context, views: RemoteViews, containerId: Int, category: String, count: Int) {
        val itemView = RemoteViews(context.packageName, R.layout.widget_asset_category_item)
        itemView.setTextViewText(R.id.widget_item_category, category)
        itemView.setTextViewText(R.id.widget_item_count, "$count")
        views.addView(containerId, itemView)
    }

    fun addVaultItemView(context: Context, views: RemoteViews, containerId: Int, name: String, type: String) {
        val itemView = RemoteViews(context.packageName, R.layout.widget_vault_item)
        itemView.setTextViewText(R.id.widget_item_name, name)
        itemView.setTextViewText(R.id.widget_item_type, type)
        views.addView(containerId, itemView)
    }
}
