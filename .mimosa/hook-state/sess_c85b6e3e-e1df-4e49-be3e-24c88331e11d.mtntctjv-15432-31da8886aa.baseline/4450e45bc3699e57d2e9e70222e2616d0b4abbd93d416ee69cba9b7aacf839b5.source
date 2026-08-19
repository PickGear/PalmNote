package com.palmnote.domain.util

import com.palmnote.data.db.entity.LifeTemplate

enum class LifeTemplateRouteType {
    GENERIC,
    HABIT,
    MOOD,
    JOURNAL,
    FOCUS,
    COUNTUP,
    COUNTDOWN,
    BIRTHDAY,
    ANNIVERSARY,
    TODO
}

fun LifeTemplate.getRouteType(): LifeTemplateRouteType {
    if (!isBuiltin && !isSpecial) return LifeTemplateRouteType.GENERIC
    return when (icon) {
        "calendar_month" -> LifeTemplateRouteType.HABIT
        "mood" -> LifeTemplateRouteType.MOOD
        "book" -> LifeTemplateRouteType.JOURNAL
        "timer" -> LifeTemplateRouteType.FOCUS
        "trending_up", "today" -> LifeTemplateRouteType.COUNTUP
        "timer_off" -> LifeTemplateRouteType.COUNTDOWN
        "cake" -> LifeTemplateRouteType.BIRTHDAY
        "celebration", "favorite" -> LifeTemplateRouteType.ANNIVERSARY
        "checklist" -> LifeTemplateRouteType.TODO
        else -> LifeTemplateRouteType.GENERIC
    }
}