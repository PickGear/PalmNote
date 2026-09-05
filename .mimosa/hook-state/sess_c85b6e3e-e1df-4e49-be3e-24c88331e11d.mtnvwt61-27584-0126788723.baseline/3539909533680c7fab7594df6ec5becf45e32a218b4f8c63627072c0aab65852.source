package com.palmnote.ui.life.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeTemplate

private fun nameRes(tpl: LifeTemplate): Int? = when (tpl.icon) {
    "savings" -> R.string.life_type_saving
    "shopping_cart" -> R.string.life_type_shopping
    "checklist" -> R.string.life_type_todo
    "flight" -> R.string.life_type_travel
    "menu_book" -> R.string.life_type_reading
    "school" -> R.string.life_type_study
    "timer_off" -> R.string.life_type_countdown
    "trending_up" -> R.string.life_type_countup
    "cake" -> R.string.life_type_birthday
    "celebration" -> R.string.life_type_anniversary
    "calendar_month" -> R.string.life_type_habit
    "mood" -> R.string.life_type_mood
    "book" -> R.string.life_type_journal
    "subscriptions" -> R.string.life_type_subscription
    "BarChart" -> R.string.life_type_report
    else -> null
}

@Composable
fun LifeTemplate.displayName(): String {
    val res = nameRes(this)
    return if (res != null) stringResource(res) else name
}
