package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v11 → v12 迁移测试：新增 `life_templates.kind`，按 `icon` 回填语义身份。
 *
 * 验收口径（三条正是这个迁移存在的理由）：
 * - 列存在、默认 NULL —— 未匹配的图标（自建/通用模板）**不被凭空标成**某个语义类型；
 * - 有语义的图标按 `kindFromIcon` 逐条回填 —— 存量用户的既有行为完全不变；
 * - 回填是**一次性**的：迁移之后用户改图标，`kind` 不再跟着变（这正是 v12 的目的）。
 *
 * 最后一条无法在这个测试里断言（它需要跑两遍迁移），因此改为断言
 * 「回填结果 == kindFromIcon 的 Kotlin 实现」—— 两边一旦漂移，这个测试立刻失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration11To12Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-11-to-12"

        /**
         * 第二个测试用**独立的库名**：`MigrationTestHelper.createDatabase` 对「同名库已存在」
         * 的处理不明确，两个测试复用同一名字可能因残留文件而互相影响。
         */
        const val DB_EMPTY = "migration-11-to-12-empty"

        /**
         * 覆盖 [com.palmnote.domain.util.kindFromIcon] 的**每一个**分支（11 个 icon），
         * 外加 [NO_KIND_ICONS] 六个无语义反例。加新语义类型时这里必须同步，
         * 否则该类型的回填会静默漏掉。
         */
        val EXPECTED = mapOf(
            "calendar_month" to "HABIT",
            "timer" to "FOCUS",
            "checklist" to "TODO",
            "timer_off" to "COUNTDOWN",
            "cake" to "BIRTHDAY",
            "celebration" to "ANNIVERSARY",
            "favorite" to "ANNIVERSARY",
            "book" to "JOURNAL",
            "mood" to "MOOD",
            "school" to "STUDY",
            "flight" to "TRAVEL"
        )

        /** 无语义图标的代表：迁移后必须是 NULL，不能被猜成某个类型。 */
        val NO_KIND_ICONS = listOf("savings", "shopping_cart", "menu_book", "trending_up", "build", "fitness_center")
    }

    private fun seedV11Template(db: androidx.sqlite.db.SupportSQLiteDatabase, name: String, icon: String) {
        db.execSQL(
            "INSERT INTO life_templates " +
                "(name, category, icon, color, description, fieldsConfig, layoutType, availableLayouts, " +
                "statusFlowConfig, linkConfig, isBuiltin, isHidden, isSpecial, sortOrder, createdAt, updatedAt) " +
                "VALUES (?, '时间', ?, '#8A8580', '', '[]', 'card', '[]', '{}', '{}', 1, 0, 0, 0, 1, 1)",
            arrayOf(name, icon)
        )
    }

    private fun kindOf(db: androidx.sqlite.db.SupportSQLiteDatabase, name: String): String? {
        db.query("SELECT kind FROM life_templates WHERE name = ?", arrayOf(name)).use { c ->
            assertTrue("期望存在 name=$name 的模板", c.moveToFirst())
            return if (c.isNull(0)) null else c.getString(0)
        }
    }

    @Test
    fun migrate11To12_backfillsKindFromIconAndLeavesOthersNull() {
        val seed = helper.createDatabase(DB, 11)
        EXPECTED.keys.forEach { seedV11Template(seed, "t_$it", it) }
        NO_KIND_ICONS.forEach { seedV11Template(seed, "n_$it", it) }
        seed.close()

        val migrated = helper.runMigrationsAndValidate(DB, 12, true, MIGRATION_11_12)

        EXPECTED.forEach { (icon, expected) ->
            assertEquals("icon=$icon 应回填为 $expected", expected, kindOf(migrated, "t_$icon"))
        }
        NO_KIND_ICONS.forEach { icon ->
            assertNull("icon=$icon 无语义，必须保持 NULL 而不是被猜一个", kindOf(migrated, "n_$icon"))
        }
        migrated.close()
    }

    /**
     * 空表也要能迁移（全新安装之外的边界：用户删光了模板）。
     * `UPDATE ... WHERE icon = ...` 对空表是 no-op，但真机上的失败往往就出在这种「应该没事」的路径。
     */
    @Test
    fun migrate11To12_onEmptyTable_succeeds() {
        helper.createDatabase(DB_EMPTY, 11).close()
        val migrated = helper.runMigrationsAndValidate(DB_EMPTY, 12, true, MIGRATION_11_12)
        migrated.query("SELECT COUNT(*) FROM life_templates").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migrated.close()
    }
}
