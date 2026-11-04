package com.palmnote.domain.model

/**
 * 预算的**周期**。预算是一条「长期有效模板」：设一次周预算，之后每周都按它算，
 * 不是每月单独存一行（v21 起）。
 *
 * 周期窗口由 `DateUtils` 的既有口径决定：周 = 周一起始的自然周，月 = 自然月，年 = 自然年。
 */
enum class BudgetPeriod {
    WEEK,
    MONTH,
    YEAR;

    companion object {
        fun from(value: String): BudgetPeriod =
            entries.firstOrNull { it.name == value } ?: MONTH
    }
}

/**
 * 预算的**作用范围**。
 *
 * - [TOTAL]：全账本合计（最常见）。
 * - [BOOK]：某个账本自己的预算，取该账本支出对比（多账本分账时用）。
 * - [CATEGORY]：某个支出分类的预算，跨账本合计（「餐饮别超 1500」这类）。
 *
 * 同一周期下每种范围各一条（分类/账本范围内再按 [com.palmnote.data.db.entity.Budget.category] /
 * `bookId` 区分）；重复设置走仓库层「先查再改」，不依赖数据库唯一索引
 * （SQLite 的唯一索引对 NULL 不去重，`bookId`/`category` 可空，靠索引挡不住）。
 */
enum class BudgetScope {
    TOTAL,
    BOOK,
    CATEGORY;

    companion object {
        fun from(value: String): BudgetScope =
            entries.firstOrNull { it.name == value } ?: TOTAL
    }
}
