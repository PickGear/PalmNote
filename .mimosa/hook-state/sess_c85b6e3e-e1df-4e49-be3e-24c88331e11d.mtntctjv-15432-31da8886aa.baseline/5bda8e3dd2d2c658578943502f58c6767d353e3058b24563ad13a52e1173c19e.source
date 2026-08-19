package com.palmnote.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 创建 bills 表的触发器：yearMonth 自动生成 + FTS 全文索引同步。
 * onCreate（全新库）与 Migration6To7（重建表后）共用，避免 SQL 重复。
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
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS bills_fts_ai
        AFTER INSERT ON bills
        BEGIN
            INSERT INTO bills_fts(rowid, note, merchant, tags)
            VALUES (new.id, new.note, new.merchant, new.tags);
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS bills_fts_ad
        AFTER DELETE ON bills
        BEGIN
            INSERT INTO bills_fts(bills_fts, rowid, note, merchant, tags)
            VALUES ('delete', old.id, old.note, old.merchant, old.tags);
        END
        """.trimIndent()
    )
    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS bills_fts_au
        AFTER UPDATE ON bills
        BEGIN
            INSERT INTO bills_fts(bills_fts, rowid, note, merchant, tags)
            VALUES ('delete', old.id, old.note, old.merchant, old.tags);
            INSERT INTO bills_fts(rowid, note, merchant, tags)
            VALUES (new.id, new.note, new.merchant, new.tags);
        END
        """.trimIndent()
    )
}
