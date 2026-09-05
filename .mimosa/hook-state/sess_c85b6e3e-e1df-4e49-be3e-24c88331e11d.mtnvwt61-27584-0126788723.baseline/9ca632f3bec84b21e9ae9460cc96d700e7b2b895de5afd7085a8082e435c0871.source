package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8：为 `life_items` 增加执行列（dueDate/dueTime/recurring/parentId 等）。
 *
 * 全部为可空 ADD COLUMN，一列一条，无数据回填（存量条目的日期仍在 fieldsData JSON 内，
 * 升级后老条目不进入今日看板，重新编辑保存一次即落列）。
 * 索引 `idx_items_due` / `idx_items_parent` 由 Room 依据 @Entity(indices=...) 自动创建。
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
    }
}
