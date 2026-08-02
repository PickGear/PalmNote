package com.palmnote.data.db.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 → v2 迁移测试：仅创建索引。校验迁移后索引齐备且 schema 与 2.json 一致。
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-1-to-2"
    }

    @Test
    fun migrate1To2_createsAllExpectedIndexes() {
        helper.createDatabase(DB, 1).close()

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        val expected = listOf(
            "idx_goal_type_deleted", "idx_goal_category", "idx_goal_deleted", "idx_goal_deadline",
            "idx_checkin_goal", "idx_checkin_goal_date",
            "idx_template_category", "idx_template_deleted", "idx_template_visible",
            "idx_todo_status", "idx_todo_due", "idx_todo_plan", "idx_todo_life_item",
            "idx_moment_date", "idx_moment_deleted", "idx_moment_life_item",
            "idx_mood_date", "idx_mood_life_item"
        )
        assertIndexesExist(db, expected)
        db.close()
    }

    private fun assertIndexesExist(db: androidx.sqlite.db.SupportSQLiteDatabase, expected: List<String>) {
        val placeholders = expected.joinToString(",") { "?" }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN ($placeholders)",
            expected.toTypedArray()
        ).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(0))
            assertEquals(expected.size, names.size)
            assertEquals(expected.sorted(), names.sorted())
        }
    }
}
