package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * v12 → v13 迁移测试：新增 `life_templates.repeatYearly`。
 *
 * 验收口径（三条正是这个迁移存在的理由）：
 * - 列存在且**存量模板一律为 0**（不重复）—— 迁移不得凭空改变已有模板的表现；
 * - 存量行**一字不动**（name / kind / isHidden 这些原有列全部保留）；
 * - 空表也要能迁移（用户删光模板的边界；真机上的失败常出在这种"应该没事"的路径）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration12To13Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-12-to-13"

        /** 第二个测试用独立库名：同名库残留文件可能让两个测试互相影响（同 11→12 的教训）。 */
        const val DB_EMPTY = "migration-12-to-13-empty"
    }

    private fun seedV12Template(db: SupportSQLiteDatabase, name: String, icon: String, kind: String?, hidden: Int) {
        db.execSQL(
            "INSERT INTO life_templates " +
                "(name, category, icon, color, description, fieldsConfig, layoutType, availableLayouts, " +
                "statusFlowConfig, linkConfig, reminderConfig, isBuiltin, isHidden, isSpecial, sortOrder, " +
                "kind, createdAt, updatedAt) " +
                "VALUES (?, '时间', ?, '#8A8580', '', '[]', 'card', '[]', '{}', '{}', NULL, 1, ?, 0, 0, ?, 1, 1)",
            arrayOf<Any?>(name, icon, hidden, kind)
        )
    }

    private fun rowOf(db: SupportSQLiteDatabase, name: String): Triple<String, Int, Int> {
        // (icon, isHidden, repeatYearly)
        db.query(
            "SELECT icon, isHidden, repeatYearly FROM life_templates WHERE name = ?",
            arrayOf(name)
        ).use { c ->
            assertTrue("期望存在 name=$name 的模板", c.moveToFirst())
            return Triple(c.getString(0), c.getInt(1), c.getInt(2))
        }
    }

    @Test
    fun migrate12To13_addsColumnDefaultingToNotRepeating() {
        val seed = helper.createDatabase(DB, 12)
        seedV12Template(seed, "生日", "cake", "BIRTHDAY", hidden = 0)
        seedV12Template(seed, "已关闭的自建", "build", null, hidden = 1)
        seed.close()

        val migrated = helper.runMigrationsAndValidate(DB, 13, true, MIGRATION_12_13)

        val birthday = rowOf(migrated, "生日")
        assertEquals("cake", birthday.first)
        assertEquals("存量模板不得被自动打开重复", 0, birthday.third)
        val closed = rowOf(migrated, "已关闭的自建")
        assertEquals("已关闭状态要保留", 1, closed.second)
        assertEquals("自建模板同样不得被自动打开重复", 0, closed.third)

        migrated.query("SELECT COUNT(*) FROM life_templates").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("存量行不能被迁移丢掉", 2, c.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate12To13_onEmptyTable_succeeds() {
        helper.createDatabase(DB_EMPTY, 12).close()
        val migrated = helper.runMigrationsAndValidate(DB_EMPTY, 13, true, MIGRATION_12_13)
        migrated.query("SELECT COUNT(*) FROM life_templates").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migrated.close()
    }
}
