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

/** 分类详情页：按真实 category（计划/时间/记录）展示该分类下模板与条目。 */
@Serializable
data class LifeCategoryDetail(
    val category: String
)

/** 模板管理页（设计稿 ed_10 / ed_11，§4.8）。 */
@Serializable
data object LifeTemplateManage

/** 模板编辑器（设计稿 ed_2～ed_6 / ed_9）：`templateId` = 0 表示新建模板。 */
@Serializable
data class LifeTemplateEdit(
    val templateId: Long = 0L
)

/** 记录填写页：按模板字段渲染表单，创建新记录。 */
@Serializable
data class LifeCreateRecord(
    val templateIconKey: String
)
