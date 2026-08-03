package com.palmnote.data.db.entity

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import com.palmnote.R

@Entity(tableName = "life_templates", indices = [
    Index(value = ["category"], name = "idx_template_category"),
    Index(value = ["isHidden"], name = "idx_template_visible")
])
@Immutable
data class LifeTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String,
    val icon: String,
    val color: String,
    val description: String = "",
    val fieldsConfig: String,
    val layoutType: String,
    val availableLayouts: String,
    val statusFlowConfig: String,
    val linkConfig: String,
    val isBuiltin: Boolean = false,
    val isHidden: Boolean = false,
    val isSpecial: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun LifeTemplate.getDisplayName(context: Context): String {
    if (!isBuiltin) return name
    return when (icon) {
        "savings" -> context.getString(R.string.template_savings_name)
        "shopping_cart" -> context.getString(R.string.template_shopping_name)
        "checklist" -> context.getString(R.string.template_todo_name)
        "flight" -> context.getString(R.string.template_travel_name)
        "menu_book" -> context.getString(R.string.template_reading_name)
        "school" -> context.getString(R.string.template_study_name)
        "timer_off" -> context.getString(R.string.template_countdown_name)
        "trending_up" -> context.getString(R.string.template_countup_name)
        "cake" -> context.getString(R.string.template_birthday_name)
        "celebration" -> context.getString(R.string.template_anniversary_name)
        "calendar_month" -> context.getString(R.string.template_checkin_name)
        "mood" -> context.getString(R.string.template_mood_name)
        "book" -> context.getString(R.string.template_diary_name)
        "subscriptions" -> context.getString(R.string.template_subscription_name)
        "BarChart" -> context.getString(R.string.template_report_name)
        else -> name
    }
}

fun LifeTemplate.getDisplayDescription(context: Context): String {
    if (!isBuiltin) return description
    return when (icon) {
        "savings" -> context.getString(R.string.template_savings_desc)
        "shopping_cart" -> context.getString(R.string.template_shopping_desc)
        "checklist" -> context.getString(R.string.template_todo_desc)
        "flight" -> context.getString(R.string.template_travel_desc)
        "menu_book" -> context.getString(R.string.template_reading_desc)
        "school" -> context.getString(R.string.template_study_desc)
        "timer_off" -> context.getString(R.string.template_countdown_desc)
        "trending_up" -> context.getString(R.string.template_countup_desc)
        "cake" -> context.getString(R.string.template_birthday_desc)
        "celebration" -> context.getString(R.string.template_anniversary_desc)
        "calendar_month" -> context.getString(R.string.template_checkin_desc)
        "mood" -> context.getString(R.string.template_mood_desc)
        "book" -> context.getString(R.string.template_diary_desc)
        "subscriptions" -> context.getString(R.string.template_subscription_desc)
        "BarChart" -> context.getString(R.string.template_report_desc)
        else -> description
    }
}
