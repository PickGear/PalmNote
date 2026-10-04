package com.palmnote.domain.util

object BuiltinTemplates {
    const val SUBSCRIPTION_KEYWORD = "订阅"
    const val TODO_KEYWORD = "待办"

    /** 模板 `category` 字段的取值（全仓唯一真源）：计划 / 时间 / 记录。 */
    const val PLAN_CATEGORY = "计划"
    const val TIME_CATEGORY = "时间"

    /**
     * 「记录」分类键（模板 `category` 字段的三个取值之一：计划 / 记录 / 时间）。
     *
     * 语义：**记录类＝「某天发生过什么」**（打卡 / 心情 / 日记 / 专注 / 身体记录 / 订阅），
     * 其日期是「记录发生日」而非「截止日」——所以：
     * - 写入时若无日期字段，以 `createdAt` 兜底 `dueDate`（见 `LifeItemRepositoryImpl.recordFallbackDueDate`）；
     * - **不参与逾期**（逾期是「计划 / 时间」才有的概念）。
     */
    const val RECORD_CATEGORY = "记录"

    /**
     * 三个分类的**完整取值**，顺序即生活页三张分类卡的顺序（计划 / 时间 / 记录）。
     *
     * 用途：模板编辑器把它当**封闭选项**（此前分类是自由输入框，用户能造出「理财」这类
     * 分类，而生活页只认这三张卡 —— 造出来的模板在首页没有任何入口，真机反馈
     * 「新建自定义模板不生效」即此）；保存前也用它做归一，把历史自造分类收敛回来。
     */
    val ALL_CATEGORIES = listOf(PLAN_CATEGORY, TIME_CATEGORY, RECORD_CATEGORY)
}
