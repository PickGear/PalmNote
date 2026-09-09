package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8：为 `life_items` 增加执行列（dueDate/dueTime/recurring/parentId 等）。
 *
 * 全部为可空 ADD COLUMN，一列一条，无数据回填（存量条目的日期仍在 fieldsData JSON 内，
 * 升级后老条目不进入今日看板，重新编辑保存一次即落列）。
 *
 * 注意：Room 只在 onCreate（全新建库）时依据 @Entity(indices=...) 建索引，迁移路径不会，
 * 因此这里必须手动补建 idx_items_due / idx_items_parent，否则迁移后 schema 校验失败、库打不开。
 *
 * 同时移除 bills_fts 全文索引（FTS 清退）：
 * - 全项目无任何 MATCH 查询（账单搜索实际走 LIKE），该表纯属写入开销；
 * - 历史缺陷：v5→v6 迁移只建了引用 bills_fts 的触发器而从不建表（表仅由 onCreate 建），
 *   导致 v5 及更早安装、升级上来的用户每次 INSERT/UPDATE/DELETE bills 都抛
 *   "no such table: bills_fts"，无法记账。此处一并清除存量触发器与虚拟表。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE life_items ADD COLUMN dueDate INTEGER")
        db.execSQL("ALTER TABLE life_items ADD COLUMN dueTime INTEGER")
        db.execSQL("ALTER TABLE life_items ADD COLUMN recurring TEXT")
        db.execSQL("ALTER TABLE life_items ADD COLUMN recurringEndType TEXT")
        db.execSQL("ALTER TABLE life_items ADD COLUMN recurringEndCount INTEGER")
        db.execSQL("ALTER TABLE life_items ADD COLUMN recurringEndDate INTEGER")
        db.execSQL("ALTER TABLE life_items ADD COLUMN parentId INTEGER")
        db.execSQL("ALTER TABLE life_items ADD COLUMN remindAt INTEGER")
        db.execSQL("ALTER TABLE life_items ADD COLUMN meta TEXT")

        // 补建 v8 schema 声明的索引（迁移路径 Room 不自动建）
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_due ON life_items(dueDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_parent ON life_items(parentId)")

        // FTS 清退：删触发器再删表（IF EXISTS 兼容"有触发器无表"的受损库与全新库两种状态）
        db.execSQL("DROP TRIGGER IF EXISTS bills_fts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS bills_fts_ad")
        db.execSQL("DROP TRIGGER IF EXISTS bills_fts_au")
        db.execSQL("DROP TABLE IF EXISTS bills_fts")
    }
}
