package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.palmnote.data.db.FieldValueExtractor

/**
 * v8 → v9：新增统计信源表 `field_values`（总纲 §7.1 双写）并回填存量 `fieldsData`。
 *
 * - 建表 SQL 必须与 Room 依据 @Entity 生成的 schema 逐字一致（含 NOT NULL / AUTOINCREMENT），
 *   且索引要在这里手动补建（迁移路径 Room 不自动建索引，参照 MIGRATION_7_8 教训）。
 * - 回填：逐条读取 life_items（id / templateId / fieldsData）与 life_templates（fieldsConfig），
 *   用 FieldValueExtractor 在 Kotlin 侧解析写入；解析失败（脏数据）跳过该条目，不阻断迁移。
 * - 验收口径：回填行与 FieldValueExtractor 对原值的日常双写完全同一实现。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `field_values` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`itemId` INTEGER NOT NULL, `fieldKey` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`num` REAL, `text` TEXT, `dateMs` INTEGER, `json` TEXT, `idx` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_values_fieldKey_num` ON `field_values` (`fieldKey`, `num`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_values_itemId` ON `field_values` (`itemId`)")

        // 模板 fieldsConfig 快照
        val configs = HashMap<Long, String>()
        db.query("SELECT id, fieldsConfig FROM life_templates").use { c ->
            while (c.moveToNext()) {
                configs[c.getLong(0)] = c.getString(1) ?: ""
            }
        }

        val insert = db.compileStatement(
            "INSERT INTO field_values (itemId, fieldKey, type, num, text, dateMs, json, idx) VALUES (?,?,?,?,?,?,?,?)"
        )
        db.query(
            "SELECT id, templateId, fieldsData FROM life_items WHERE fieldsData IS NOT NULL AND fieldsData != '' AND fieldsData != '{}'"
        ).use { c ->
            while (c.moveToNext()) {
                val itemId = c.getLong(0)
                val templateId = c.getLong(1)
                val fieldsData = c.getString(2) ?: continue
                val cfg = configs[templateId] ?: continue
                val rows = try {
                    FieldValueExtractor.extract(cfg, fieldsData)
                } catch (_: Exception) {
                    continue
                }
                for (row in rows) {
                    insert.bindLong(1, itemId)
                    insert.bindString(2, row.fieldKey)
                    insert.bindString(3, row.type)
                    if (row.num != null) insert.bindDouble(4, row.num) else insert.bindNull(4)
                    if (row.text != null) insert.bindString(5, row.text) else insert.bindNull(5)
                    if (row.dateMs != null) insert.bindLong(6, row.dateMs) else insert.bindNull(6)
                    if (row.json != null) insert.bindString(7, row.json) else insert.bindNull(7)
                    insert.bindLong(8, row.idx.toLong())
                    insert.executeInsert()
                }
            }
        }
    }
}
