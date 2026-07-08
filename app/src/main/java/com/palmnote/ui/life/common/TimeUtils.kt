package com.palmnote.ui.life.common

import java.time.Instant
import java.time.temporal.ChronoUnit

fun formatRelativeTime(millis: Long): String {
    val now = Instant.now()
    val instant = Instant.ofEpochMilli(millis)
    val days = ChronoUnit.DAYS.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val minutes = ChronoUnit.MINUTES.between(instant, now)

    return when {
        minutes < 1 -> "\u521A\u521A"
        minutes < 60 -> "${minutes}\u5206\u949F\u524D"
        hours < 24 -> "${hours}\u5C0F\u65F6\u524D"
        days < 7 -> "${days}\u5929\u524D"
        days < 30 -> "${days / 7}\u5468\u524D"
        days < 365 -> "${days / 30}\u4E2A\u6708\u524D"
        else -> "${days / 365}\u5E74\u524D"
    }
}