package com.palmnote.ui.life

import kotlinx.serialization.Serializable

/** 生活页内部（嵌套）路由——页面内导航跳转与路由切换。 */
@Serializable
data object LifeHome

@Serializable
data class LifeDetail(
    val title: String,
    val iconKey: String = "",
    val accentHex: String = "",
    val heroLabel: String = "",
    val heroValue: String = ""
)

@Serializable
data class LifeList(
    val title: String,
    val subtitle: String = ""
)

@Serializable
data class LifeDayRead(
    val dayLabel: String
)

@Serializable
data object LifeStats
