package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5：新增密码本表 vault_entries。
 * 密码本功能（feature/vault）独立字段加密，本迁移仅建表。
 * 注意：表结构与 v5.json 导出的 schema 严格一致（无 DEFAULT、无额外索引），
 * 否则 runMigrationsAndValidate 的 schema 校验会失败。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS vault_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                username TEXT NOT NULL,
                passwordEncrypted BLOB NOT NULL,
                url TEXT NOT NULL,
                notes TEXT NOT NULL,
                category TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
    }
}
