package com.palmnote.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 创建 bills 表的触发器：yearMonth 自动生成。
 * onCreate（全新库）与 Migration6To7（重建表后）共用，避免 SQL 重复。
 *
 * 注：bills_fts 全文索引已清退（无 MATCH 查询、纯写入开销，且迁移路径缺表曾导致
 * 老用户无法记账——详见 MIGRATION_7_8 注释），此处不再创建 FTS 触发器。
 */
fun createBillTriggers(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS auto_yearmonth_insert
        AFTER INSERT ON bills
        BEGIN
            UPDATE bills SET yearMonth = strftime('%Y-%m', datetime(NEW.date / 1000, 'unixepoch', 'localtime'))
            WHERE id = NEW.id;
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS auto_yearmonth_update
        AFTER UPDATE OF date ON bills
        BEGIN
            UPDATE bills SET yearMonth = strftime('%Y-%m', datetime(NEW.date / 1000, 'unixepoch', 'localtime'))
            WHERE id = NEW.id;
        END
        """.trimIndent()
    )
}
