package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v15 → v16：把「报销」从半成品补成可用的报销管理。
 *
 * 此前 `isReimbursable` / `isReimbursed` / `reimbursedDate` 三列早已存在（Migration2To3 起），
 * 但没有任何页面消费它们：既没有待报销汇总，也没有入口能把 `isReimbursed` 置为 true。
 * 这一版补上两列，让「记一笔垫付 → 报销回来 → 支出上体现净额」这条链闭合：
 *
 * - `reimbursedAmount`：累计已报销金额（分）。支持部分报销（差旅餐费只报一部分很常见），
 *   0 = 未报，>= amount = 报完。`isReimbursed` 由同一处写入派生，两者不会各自漂移。
 * - `reimbursedByBillId`：这笔支出关联的报销收入账单 id。方向选「支出指向收入」是因为
 *   一笔报销收入经常覆盖多条支出，反过来存列表会引入冗余；反查一条收入覆盖了谁，
 *   `WHERE reimbursedByBillId = ?` 即可。
 *
 * 存量行 `reimbursedByBillId` 一律 NULL（那笔关联收入账单此时并不存在，无从回填）。
 *
 * `reimbursedAmount` 默认 0，但**必须再按 `isReimbursed = 1` 回填**：`isReimbursed` 早于本迁移
 * 就存在，且可由 CSV 导入写入（见 `CsvDataExporter` 的「已报销」列）。若只置 0 而保留 `isReimbursed`，
 * 这些行的状态会自相矛盾 —— 列表页按 `isReimbursed` 把它们算进「已报销」，详情页却按
 * `reimbursedAmount <= 0` 显示「待报销」，且只给「报销」不给「撤销」。回填成「已报满」
 * （= amount）后两处口径一致，也与迁移前 `isReimbursed = false` 的行保持各自的原有语义。
 *
 * 回收站同步加列，删除/恢复之间带住报销进度。
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("bills", "bills_recycle_bin").forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `reimbursedAmount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `reimbursedByBillId` INTEGER")
            // 存量已标记报销的行回填为「已报满」，避免 reimbursedAmount 与 isReimbursed 互相打架
            db.execSQL("UPDATE `$table` SET `reimbursedAmount` = `amount` WHERE `isReimbursed` = 1")
        }
        // 删除报销收入账单时按此列反查并解绑，避免留下悬空引用
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_reimbursedByBillId` ON `bills` (`reimbursedByBillId`)")
    }
}
