package com.palmnote.domain.util

object BuiltinTemplates {
    const val SUBSCRIPTION_KEYWORD = "订阅"
    const val TODO_KEYWORD = "待办"

    /**
     * 「记录」分类键（模板 `category` 字段的三个取值之一：计划 / 记录 / 时间）。
     *
     * 语义：**记录类＝「某天发生过什么」**（打卡 / 心情 / 日记 / 专注 / 身体记录 / 订阅），
     * 其日期是「记录发生日」而非「截止日」——所以：
     * - 写入时若无日期字段，以 `createdAt` 兜底 `dueDate`（见 `LifeItemRepositoryImpl.recordFallbackDueDate`）；
     * - **不参与逾期**（逾期是「计划 / 时间」才有的概念）。
     */
    const val RECORD_CATEGORY = "记录"
}
