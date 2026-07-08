package com.palmnote.ui.utils

import java.text.SimpleDateFormat
import java.util.*

fun formatTimeAgo(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis

    if (diff < 0) return formatAbsolute(timestampMillis)

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "\u521A\u521A"
        minutes < 60 -> "${minutes} \u5206\u949F\u524D"
        hours < 24 -> "\u4ECA\u5929 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))}"
        days < 2 -> "\u6628\u5929 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))}"
        isSameYear(timestampMillis, now) -> SimpleDateFormat("M\u6708d\u65E5", Locale.getDefault()).format(Date(timestampMillis))
        else -> SimpleDateFormat("yyyy\u5E74M\u6708d\u65E5", Locale.getDefault()).format(Date(timestampMillis))
    }
}

private fun formatAbsolute(timestampMillis: Long): String {
    return if (isSameYear(timestampMillis, System.currentTimeMillis())) {
        SimpleDateFormat("M\u6708d\u65E5 HH:mm", Locale.getDefault()).format(Date(timestampMillis))
    } else {
        SimpleDateFormat("yyyy\u5E74M\u6708d\u65E5", Locale.getDefault()).format(Date(timestampMillis))
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
