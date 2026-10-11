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
 * v16 → v17 迁移测试：新建 `life_recycle_bin` 快照表。
 *
 * 验收口径：
 * - 表结构与 Room 实体期望一致（runMigrationsAndValidate 的 schema 校验兜底）；
 * - 存量数据零改动——此前生活条目删除就是物理删，迁移只建空表，不做任何回填；
 * - 空库也要能迁移。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration16To17Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate16To17_createsEmptyBinTable() {
        helper.createDatabase("migration-16-to-17", 16).close()

        val migrated = helper.runMigrationsAndValidate("migration-16-to-17", 17, true, MIGRATION_16_17)

        // 空表 + 可写入：列结构与 NOT NULL 约束由 schema 校验保证，这里验证最小写入路径
        migrated.execSQL(
            "INSERT INTO life_recycle_bin " +
                "(originalId, templateId, templateName, title, fieldsData, status, note, " +
                "sortOrder, isFavorite, createdAt, updatedAt, deletedAt) " +
                "VALUES (1, 1, '模板', '标题', '{}', 'ACTIVE', '', 0, 0, 1, 1, 1)"
        )
        migrated.query("SELECT originalId, templateName, title, isSeedSample FROM life_recycle_bin").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("模板", c.getString(1))
            assertEquals("标题", c.getString(2))
            assertEquals("isSeedSample 走 SQL 默认值 0", 0, c.getInt(3))
        }
        migrated.close()
    }

    @Test
    fun migrate16To17_keepsExistingLifeRows() {
        val seed = helper.createDatabase("migration-16-to-17-rows", 16)
        seed.execSQL(
            "INSERT INTO life_items (id, templateId, title, fieldsData, status, note, sortOrder, " +
                "isFavorite, createdAt, updatedAt, isSeedSample) " +
                "VALUES (9, 1, '存量记录', '{}', 'ACTIVE', '', 0, 0, 1, 1, 0)"
        )
        seed.close()

        val migrated = helper.runMigrationsAndValidate("migration-16-to-17-rows", 17, true, MIGRATION_16_17)

        migrated.query("SELECT COUNT(*) FROM life_items WHERE id = 9").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("存量条目不能被迁移改动", 1, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM life_recycle_bin").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("迁移不回填回收站", 0, c.getInt(0))
        }
        migrated.close()
    }
}
