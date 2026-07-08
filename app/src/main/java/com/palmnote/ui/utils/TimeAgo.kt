package com.palmnote.ui.utils

import android.content.Context
import com.palmnote.R
import java.text.SimpleDateFormat
import java.util.*

fun formatTimeAgo(context: Context, timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis

    if (diff < 0) return formatAbsolute(context, timestampMillis)

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    return when {
        seconds < 60 -> context.getString(R.string.time_just_now)
        minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> context.getString(R.string.time_today, timeFmt.format(Date(timestampMillis)))
        days < 2 -> context.getString(R.string.time_yesterday, timeFmt.format(Date(timestampMillis)))
        isSameYear(timestampMillis, now) -> SimpleDateFormat(context.getString(R.string.date_format_display), Locale.getDefault()).format(Date(timestampMillis))
        else -> SimpleDateFormat(context.getString(R.string.date_format_display_year), Locale.getDefault()).format(Date(timestampMillis))
    }
}

private fun formatAbsolute(context: Context, timestampMillis: Long): String {
    return if (isSameYear(timestampMillis, System.currentTimeMillis())) {
        SimpleDateFormat("${context.getString(R.string.date_format_display)} HH:mm", Locale.getDefault()).format(Date(timestampMillis))
    } else {
        SimpleDateFormat(context.getString(R.string.date_format_display_year), Locale.getDefault()).format(Date(timestampMillis))
    }
}

private fun isSameYear(a: Long, b: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = a
    val yearA = cal.get(Calendar.YEAR)
    cal.timeInMillis = b
    val yearB = cal.get(Calendar.YEAR)
    return yearA == yearB
}
