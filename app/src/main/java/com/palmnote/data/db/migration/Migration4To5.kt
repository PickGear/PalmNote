package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5：新增密码本表 vault_entries。
 * 密码本功能（feature/vault）独立字段加密，本迁移仅建表。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS vault_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                username TEXT NOT NULL DEFAULT '',
                passwordEncrypted BLOB NOT NULL,
                url TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '其他',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_entries_updatedAt ON vault_entries(updatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_entries_category ON vault_entries(category)")
    }
}
