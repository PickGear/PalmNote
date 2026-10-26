package com.palmnote.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.palmnote.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter


object WidgetHelper {

    /**
     * 各组件的「点整卡 → 进对应页签」出口。[requestCode] = `WidgetDeepLink.SEG_*` 号段 + appWidgetId，
     * [tab] 决定落到哪个根页签。之前每个 provider 自带一份 `linkViews` 私有实现，只有号段不同 —— 收敛到这里。
     */
    fun linkViews(context: Context, views: RemoteViews, requestCode: Int, tab: String): RemoteViews {
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            createPendingIntent(context, requestCode, tab)
        )
        return views
    }


    fun formatMoney(amount: Long): String {
        val yuan = amount / 100.0
        return String.format("¥%.2f", yuan)
    }

    fun formatMoneyDouble(amount: Double): String {
        return String.format("¥%.2f", amount)
    }

    /**
     * 条目价格原文 → 带货币符号的展示文案：原文已带 ¥/$ 等符号就原样，否则补 ¥。
     * 设计稿金额一律带符号（订阅行「¥25」），而条目 price 字段是裸数字串。
     */
    fun moneyText(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return if (text.startsWith("¥") || text.startsWith("$") || text.startsWith("€")) {
            text
        } else {
            "¥$text"
        }
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

    /**
     * 待办议程行（v4 设计稿 .tdrow2）：方框 + 名称 + 时间。
     * 时间文案由 [TodoWidgetService] 按段算好传入（今天 HH:mm、其余 M/d、逾期可给「昨天」），
     * 逾期行的时间转警示红；整行点击走 fill-in intent——集合里的行不能各自持有 PendingIntent。
     */
    fun todoAgendaRowViews(
        context: Context,
        item: com.palmnote.data.db.entity.LifeItem,
        timeText: String,
        isOverdue: Boolean
    ): RemoteViews {
        val views = widgetViews(context, R.layout.widget_todo_agenda_row)
        views.setTextViewText(R.id.widget_todo_agenda_name, item.title)
        views.setTextViewText(R.id.widget_todo_agenda_time, timeText)
        if (isOverdue) {
            views.setTextColor(R.id.widget_todo_agenda_time, context.getColor(R.color.widget_risk_red))
        }
        // 无障碍：行内容描述（API 30+ RemoteViews 支持，低版本自动忽略）
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            views.setContentDescription(R.id.widget_todo_agenda_row, item.title)
        }
        // 行内点击：只带条目 id，action 与组件由集合的 template 补全
        views.setOnClickFillInIntent(
            R.id.widget_todo_agenda_row,
            Intent().putExtra(TodoToggleReceiver.EXTRA_ITEM_ID, item.id)
        )
        return views
    }

    fun addCounterItemView(context: Context, views: RemoteViews, containerId: Int, title: String, dateMillis: Long) {
        val itemView = widgetViews(context, R.layout.widget_counter_item)
        itemView.setTextViewText(R.id.widget_item_name, title)
        val days = daysUntil(dateMillis)
        itemView.setTextViewText(R.id.widget_item_days, formatDaysLeft(days))
        itemView.setTextViewText(R.id.widget_item_unit, getDaysLabel(context, days))
        views.addView(containerId, itemView)
    }
}
