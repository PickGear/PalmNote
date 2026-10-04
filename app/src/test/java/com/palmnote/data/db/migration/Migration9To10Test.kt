package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.domain.model.ReminderSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v9 → v10 迁移测试：新增 `life_templates.reminderConfig` 列并按图标回填存量内置模板。
 *
 * 验收口径：
 * - 五类提醒模板（trending_up/timer_off/cake/celebration/subscriptions）得到正确的显式配置，
 *   此后改图标不再静默弄丢提醒；
 * - 非提醒类内置模板（如 savings）与自定义模板保持 NULL（Worker 按图标兜底推断，行为不变）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration9To10Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-9-to-10"
    }

    /** 在 v9 库里插一行最简模板，返回其 id。 */
    private fun seedV9Template(db: androidx.sqlite.db.SupportSQLiteDatabase, icon: String): Long {
        val stmt = db.compileStatement(
            "INSERT INTO life_templates " +
                "(name, category, icon, color, description, fieldsConfig, layoutType, availableLayouts, " +
                "statusFlowConfig, linkConfig, isBuiltin, isHidden, isSpecial, sortOrder, createdAt, updatedAt) " +
                "VALUES (?, '时间', ?, '#000000', '', '[]', 'card', '[]', '', '', 1, 0, 0, 0, 1, 1)"
        )
        stmt.bindString(1, "tpl-$icon")
        stmt.bindString(2, icon)
        val id = stmt.executeInsert()
        return id
    }

    private fun reminderConfigOf(db: androidx.sqlite.db.SupportSQLiteDatabase, icon: String): String? {
        db.query(
            "SELECT reminderConfig FROM life_templates WHERE icon = '$icon'"
        ).use { c ->
            assertTrue("期望存在 icon=$icon 的模板", c.moveToFirst())
            return if (c.isNull(0)) null else c.getString(0)
        }
    }

    @Test
    fun migrate9To10_backfillsReminderConfigByIcon() {
        val seed = helper.createDatabase(DB, 9)
        listOf("trending_up", "timer_off", "cake", "celebration", "subscriptions", "savings")
            .forEach { seedV9Template(seed, it) }
        seed.close()

        val migrated = helper.runMigrationsAndValidate(DB, 10, true, MIGRATION_9_10)
        assertEquals(
            """{"kind":"MILESTONE","dateKey":"start_date","enabled":true}""",
            reminderConfigOf(migrated, "trending_up")
        )
        assertEquals(
            """{"kind":"COUNTDOWN","dateKey":"targetDate","enabled":true}""",
            reminderConfigOf(migrated, "timer_off")
        )
        assertEquals(
            """{"kind":"BIRTHDAY","dateKey":"date","enabled":true}""",
            reminderConfigOf(migrated, "cake")
        )
        assertEquals(
            """{"kind":"ANNIVERSARY","dateKey":"date","enabled":true}""",
            reminderConfigOf(migrated, "celebration")
        )
        assertEquals(
            """{"kind":"SUBSCRIPTION","enabled":true}""",
            reminderConfigOf(migrated, "subscriptions")
        )
        // 非提醒类模板不回填：Worker 的图标兜底路径继续负责
        assertNull(reminderConfigOf(migrated, "savings"))
        // 回填 JSON 必须能被 ReminderSpec 解析（迁移 SQL 与领域模型两处字面量不得漂移）
        listOf("trending_up", "timer_off", "cake", "celebration", "subscriptions").forEach { icon ->
            val spec = ReminderSpec.fromJson(reminderConfigOf(migrated, icon))
            assertTrue("回填 JSON 应可解析为 ReminderSpec: icon=$icon", spec != null && spec.enabled)
        }
        migrated.close()
    }
}
