package com.palmnote.ui.life

import kotlinx.serialization.Serializable

/** Life 模块内嵌 NavHost 的类型安全路由（后缀 Route 避免与主题色/实体类撞名） */
@Serializable
data object LifeHomeRoute

@Serializable
data class LifeTemplateRoute(val templateId: Long)

@Serializable
data class LifeItemRoute(val itemId: Long)

@Serializable
data class LifeCreateRoute(val templateId: Long)

@Serializable
data class LifeEditRoute(val itemId: Long)

@Serializable
data object LifeFocusRoute

@Serializable
data object LifeHabitRoute

@Serializable
data object LifeMoodRoute

@Serializable
data object LifeJournalRoute

@Serializable
data object LifeTodoRoute

@Serializable
data object LifeReportRoute

@Serializable
data object LifeAchievementRoute

@Serializable
data object LifeTemplateManageRoute

@Serializable
data object LifeTemplateCreateRoute

@Serializable
data object LifeStatsRoute

@Serializable
data class LifeCategoryDetailRoute(val category: String)
