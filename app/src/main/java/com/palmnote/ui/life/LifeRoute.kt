package com.palmnote.ui.life

import kotlinx.serialization.Serializable

/** 生活页内部（嵌套）路由——页面内导航跳转与路由切换。 */
@Serializable
data object LifeHome

/** 详情页路由：**只传 itemId**，页面自己查库装配（§14.2 四段骨架）。 */
@Serializable
data class LifeDetail(
    val itemId: Long
)

@Serializable
data class LifeDayRead(
    val dateKey: String
)

@Serializable
data object LifeStats

/**
 * 月度回顾页：回顾**上一个完整月份**——总记录数、活跃天数、最常记录、心情分布。
 * 叙事式只读页；今日视角由今日看板/统计页承担。
 */
@Serializable
data object LifeMonthlyReview

/** 分类详情页：按真实 category（计划/时间/记录）展示该分类下模板与条目。 */
@Serializable
data class LifeCategoryDetail(
    val category: String
)

/**
 * 首页清单的完整列表页：逾期 / 今日安排 / 待安排共用同一页。
 *
 * [mode] 使用 [LifeFullListMode] 的稳定 DB 值（OVERDUE / AGENDA / UNSCHEDULED），
 * 避免把三套相似页面拆成三个目的地。
 */
@Serializable
data class LifeFullList(
    val mode: String,
    val dateEpochDay: Long = 0L
)

/** 模板管理页（设计稿 ed_10 / ed_11，§4.8）。 */
@Serializable
data object LifeTemplateManage

/** 模板编辑器（设计稿 ed_2～ed_6 / ed_9）：`templateId` = 0 表示新建模板。 */
@Serializable
data class LifeTemplateEdit(
    val templateId: Long = 0L
)

/**
 * 字段库全屏页（§4.7(2)）：34 种字段按 A–F 分组 + 搜索。
 * 从模板编辑器「+ 添加字段」进入，选中即返回（带结果回传）。
 */
@Serializable
data object LifeFieldLibrary

/**
 * 记录填写页：按模板字段渲染表单。
 * [templateId] 定模板（新建）；[itemId] > 0 时为编辑已有记录（模板由记录反查，templateId 忽略）。
 *
 * **为什么按 id 而不是按图标认模板**：图标是**显示**属性——用户可以改，新建模板时也只能从
 * 内置那套图标里挑，所以「自定义模板」和某个内置模板撞图标是常态。而
 * `getTemplateByIcon` 是 `WHERE icon = :icon LIMIT 1`，撞图标时它会返回**另一个**模板，
 * 于是点自定义模板打开的是内置模板的表单（真机反馈「新建自定义模板不生效」即此）。
 * id 才是身份，图标只是长相。
 */
@Serializable
data class LifeCreateRecord(
    val templateId: Long = 0L,
    val itemId: Long = 0L
)
