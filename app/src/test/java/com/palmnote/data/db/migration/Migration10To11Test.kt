package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v10 → v11 迁移测试：新增 `life_items.isSeedSample`，并把存量演示行回填为示例行。
 *
 * 验收口径（三条都正是这个迁移存在的理由）：
 * - 列存在且默认 0 —— 老数据不会因为迁移被误标成「可丢弃的示例」；
 * - 存量 `meta = {"demo":true}` 的行回填为 1 —— 与迁移前的删除行为一致，
 *   不改变任何既有用户的既有表现；
 * - 用户行（`meta` 为 NULL 或其他值）一律为 0 —— 从此不会再被「关闭演示模式」删掉。
 *
 * `runMigrationsAndValidate` 的第三参 `true` 是关键：它会拿 `schemas/11.json`
 * 逐列核对实际表结构。没给 `@ColumnInfo(defaultValue = "0")` 的话，
 * 期望默认值(null) 与实际默认值(0) 不一致，这个测试会直接失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration10To11Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-10-to-11"
        const val DEMO_META = """{"demo":true}"""
    }

    /** 在 v10 库里插一行最简记录，返回其 id。 */
    private fun seedV10Item(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        title: String,
        meta: String?
    ): Long {
        val stmt = db.compileStatement(
            "INSERT INTO life_items " +
                "(templateId, title, fieldsData, status, note, sortOrder, isFavorite, createdAt, updatedAt, meta) " +
                "VALUES (1, ?, '{}', 'ACTIVE', '', 0, 0, 1, 1, ?)"
        )
        stmt.bindString(1, title)
        if (meta == null) stmt.bindNull(2) else stmt.bindString(2, meta)
        return stmt.executeInsert()
    }

    private fun isSeedSampleOf(db: androidx.sqlite.db.SupportSQLiteDatabase, title: String): Int {
        db.query("SELECT isSeedSample FROM life_items WHERE title = ?", arrayOf(title)).use { c ->
            assertTrue("期望存在 title=$title 的记录", c.moveToFirst())
            return c.getInt(0)
        }
    }

    @Test
    fun migrate10To11_backfillsSeedFlagOnlyForDemoMetaRows() {
        val seed = helper.createDatabase(DB, 10)
        seedV10Item(seed, "示例条目", DEMO_META)
        seedV10Item(seed, "我的记录", null)
        seedV10Item(seed, "其他标记", """{"demo":false}""")
        seed.close()

        val migrated = helper.runMigrationsAndValidate(DB, 11, true, MIGRATION_10_11)
        assertEquals("演示行应回填为示例", 1, isSeedSampleOf(migrated, "示例条目"))
        assertEquals("用户行不得被标成示例", 0, isSeedSampleOf(migrated, "我的记录"))
        assertEquals("其他 meta 值不得被标成示例", 0, isSeedSampleOf(migrated, "其他标记"))
        migrated.close()
    }
}
