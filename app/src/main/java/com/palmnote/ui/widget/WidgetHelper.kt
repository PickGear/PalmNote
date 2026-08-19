package com.palmnote.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.model.BillType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter


object WidgetHelper {

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
    fun createPendingIntent(context: Context, requestCode: Int, tab: String): PendingIntent {
        val intent = Intent(context, com.palmnote.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, requestCode)
            putExtra("WIDGET_TAB", tab)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun addTodoItemView(
        context: Context,
        views: RemoteViews,
        containerId: Int,
        item: com.palmnote.data.db.entity.LifeItem,
        accentCircleRes: Int = R.drawable.widget_circle_accent,
        togglePendingIntent: android.app.PendingIntent? = null
    ) {
        val itemView = RemoteViews(context.packageName, R.layout.widget_todo_item)
        val completed = item.status == "COMPLETED"
        itemView.setInt(
            R.id.widget_item_check, "setBackgroundResource",
            if (completed) accentCircleRes else R.drawable.widget_ring_gray
        )
        // 整行可点：桌面直接勾选/取消（传入 PendingIntent 时）
        togglePendingIntent?.let { itemView.setOnClickPendingIntent(R.id.widget_todo_row, it) }
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
                else -> context.getString(R.string.widget_days_later, days)
            }
        } ?: ""
        itemView.setTextViewText(R.id.widget_item_due, dueText)
        views.addView(containerId, itemView)
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
