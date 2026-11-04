package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v20 → v21：`budgets` 从「一个月一行」改为「长期有效的预算模板」。
 *
 * ## 为什么改
 *
 * v20 之前预算只有「当月总额」一个维度：`yearMonth` 既是周期键也是唯一维度，
 * 于是没法表达「每周预算」「年度预算」「餐饮分类预算」「某个账本自己的预算」。
 * v21 把表重建为模板表：
 *
 * | 列 | 含义 |
 * |---|---|
 * | `period` | WEEK / MONTH / YEAR |
 * | `scope` | TOTAL / BOOK / CATEGORY |
 * | `bookId` | scope=BOOK 的账本 |
 * | `category` | scope=CATEGORY 的分类名 |
 * | `reminderEnabled` | 这条预算是否发通知 |
 *
 * ## 存量数据
 *
 * 旧表里每个月一行，语义上是「那个月的总额」。新语义是长期模板，**只保留最近一个月的
 * 额度**，否则迁移会造出一堆同维度重复行（周/月/年 × 总额各一条的期望被打破）。
 * 真实与演示两个口径各保留自己最近的一条——演示预算不能盖掉用户自己的预算。
 *
 * 旧的 `reminderAtThreshold` / `reminderOverBudget` 两列合并为 `reminderEnabled`
 * （任一为真即认为这条预算要提醒），两列此前 UI 从未读写，按默认值保留行为不变。
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `period` TEXT NOT NULL,
                `scope` TEXT NOT NULL,
                `bookId` INTEGER,
                `category` TEXT,
                `totalBudget` INTEGER NOT NULL,
                `reminderThreshold` REAL NOT NULL,
                `reminderEnabled` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `demoOrigin` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `budgets_new`
                (`id`, `period`, `scope`, `bookId`, `category`, `totalBudget`,
                 `reminderThreshold`, `reminderEnabled`, `createdAt`, `updatedAt`, `demoOrigin`)
            SELECT `id`, 'MONTH', 'TOTAL', NULL, NULL, `totalBudget`,
                   `reminderThreshold`,
                   CASE WHEN `reminderAtThreshold` = 1 OR `reminderOverBudget` = 1 THEN 1 ELSE 0 END,
                   `createdAt`, `updatedAt`, `demoOrigin`
            FROM `budgets` AS b
            WHERE b.`id` = (
                SELECT b2.`id` FROM `budgets` AS b2
                WHERE (b2.`demoOrigin` != 'NONE') = (b.`demoOrigin` != 'NONE')
                ORDER BY b2.`yearMonth` DESC, b2.`id` DESC
                LIMIT 1
            )
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `budgets`")
        db.execSQL("ALTER TABLE `budgets_new` RENAME TO `budgets`")
    }
}
