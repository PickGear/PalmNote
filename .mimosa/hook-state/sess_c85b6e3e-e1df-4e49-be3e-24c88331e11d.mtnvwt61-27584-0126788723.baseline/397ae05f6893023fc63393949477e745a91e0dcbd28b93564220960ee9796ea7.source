package com.palmnote.ui.life.common

import android.content.Context
import com.palmnote.app.R
import java.time.Instant
import java.time.temporal.ChronoUnit

fun formatRelativeTime(context: Context, millis: Long): String {
    val now = Instant.now()
    val instant = Instant.ofEpochMilli(millis)
    val days = ChronoUnit.DAYS.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val minutes = ChronoUnit.MINUTES.between(instant, now)

    return when {
        minutes < 1 -> context.getString(R.string.time_just_now)
        minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> context.getString(R.string.time_hours_ago, hours.toInt())
        days < 7 -> context.getString(R.string.time_days_ago, days.toInt())
        days < 30 -> context.getString(R.string.time_weeks_ago, (days / 7).toInt())
        days < 365 -> context.getString(R.string.time_months_ago, (days / 30).toInt())
        else -> context.getString(R.string.time_years_ago, (days / 365).toInt())
    }
}
