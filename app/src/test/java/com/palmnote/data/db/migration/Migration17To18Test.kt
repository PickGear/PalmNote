package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.domain.util.BuiltinGoalTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v17 → v18 迁移测试：目标并入生活模板体系（`goals` / `goal_check_ins` 退役）。
 *
 * 验收口径：
 * - **用户自己的非习惯目标**逐条搬成目标模板下的生活记录：目标值 / 当前值 / 起始值 / 单位进
 *   fieldsData，截止日期进**字段**而不是执行列（目标不是待办）；
 * - **习惯型与演示行不搬**（习惯语义已由「打卡」模板承担，演示由 LifeDemoData 播种）；
 * - 两张旧表被删除，`moments.linkedGoalId` 清空（无读方的悬空引用）；
 * - 空库也能迁移，且**不会**凭空建出目标模板（模板由 LifeDataSeeder 全新播种）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration17To18Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** 全部 NOT NULL 列都要给值：v17 的 goals 表除了 icon / isDemo 没有任何 SQL 默认值。 */
    @Suppress("LongParameterList")
    private fun insertGoal(
        db: SupportSQLiteDatabase,
        title: String,
        goalType: String = "CUMULATIVE",
        totalCount: Int = 100,
        currentCount: Int = 0,
        unit: String = "次",
        direction: String = "INCREASE",
        initialValue: Int = 0,
        deadline: Long? = null,
        isDemo: Int = 0
    ) {
        db.execSQL(
            "INSERT INTO goals (title, description, category, goalType, totalCount, currentCount, unit, " +
                "frequency, targetPerPeriod, currentPeriodCount, periodStartDate, deadline, startDate, " +
                "priority, color, icon, streak, longestStreak, lastCheckInDate, totalCheckInDays, " +
                "currentPeriodStart, currentPeriodEnd, direction, initialValue, reminderEnabled, reminderTime, " +
                "linkedAssetId, isPublic, isDemo, notes, createdAt, updatedAt) " +
                "VALUES (?, '描述', 'READING', ?, ?, ?, ?, '', 0, 0, NULL, ?, 1000, 'MEDIUM', '', 'Flag', " +
                "0, 0, NULL, 0, 0, 0, ?, ?, 0, '', NULL, 0, ?, '', 2000, 3000)",
            arrayOf(title, goalType, totalCount, currentCount, unit, deadline, direction, initialValue, isDemo)
        )
    }

    /** v17 老库样本：3 条要搬的目标（含递减型）+ 习惯/演示两条不搬 + 打卡行 + 带旧关联的瞬时。 */
    private fun seedLegacyData(db: SupportSQLiteDatabase) {
        insertGoal(db, title = "今年读完 12 本书", totalCount = 12, currentCount = 7, unit = "本", deadline = 5_000L)
        // 递减型：戒烟 20 → 0，当前读数 15（进度 5/20 = 25%）
        insertGoal(
            db, title = "戒烟", goalType = "TARGET", totalCount = 0, currentCount = 15,
            unit = "支", direction = "DECREASE", initialValue = 20
        )
        insertGoal(db, title = "背完单词", totalCount = 30, currentCount = 30)
        // 下面两条不搬
        insertGoal(db, title = "晨跑 30 分钟", goalType = "HABIT", totalCount = 12, currentCount = 12)
        insertGoal(db, title = "演示目标", totalCount = 5, currentCount = 1, isDemo = 1)
        db.execSQL(
            "INSERT INTO goal_check_ins (goalId, date, count, note, mood, duration, isDemo, createdAt) " +
                "VALUES (1, 1000, 1, '打卡', 'GOOD', 0, 0, 1000)"
        )
        db.execSQL(
            "INSERT INTO moments (title, content, timestamp, images, videoPath, audioPath, mood, weather, " +
                "temperature, location, tags, category, privacy, isPinned, isFavorite, wordCount, linkedGoalId, " +
                "createdAt, updatedAt) VALUES ('瞬时', '正文', 1, '', '', '', '', '', 0, '', '', '', 'PRIVATE', " +
                "0, 0, 0, 1, 1, 1)"
        )
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int = db.query(sql).use { c ->
        c.moveToFirst()
        c.getInt(0)
    }

    private fun fieldsOf(db: SupportSQLiteDatabase, title: String): Map<String, String?> =
        db.query("SELECT fieldsData FROM life_items WHERE title = ?", arrayOf(title)).use { c ->
            assertTrue("迁移后应有「$title」", c.moveToFirst())
            json.parseToJsonElement(c.getString(0)).jsonObject
                .mapValues { (_, v) -> v.jsonPrimitive.contentOrNull }
        }

    @Test
    fun migrate17To18_convertsUserGoalsAndDropsLegacyTables() {
        val seed = helper.createDatabase("migration-17-to-18", 17)
        seedLegacyData(seed)
        seed.close()

        val migrated = helper.runMigrationsAndValidate("migration-17-to-18", 18, true, MIGRATION_17_18)

        // 目标模板：按 icon 认身份，语义身份 kind = GOAL，字段配置与种子共用同一个常量
        migrated.query(
            "SELECT name, category, kind, fieldsConfig, isBuiltin FROM life_templates WHERE icon = ?",
            arrayOf(BuiltinGoalTemplate.ICON)
        ).use { c ->
            assertTrue("迁移应建出目标模板", c.moveToFirst())
            assertEquals(BuiltinGoalTemplate.NAME, c.getString(0))
            assertEquals(BuiltinGoalTemplate.CATEGORY, c.getString(1))
            assertEquals("GOAL", c.getString(2))
            assertEquals(BuiltinGoalTemplate.FIELDS_CONFIG, c.getString(3))
            assertEquals(1, c.getInt(4))
        }

        // 只搬用户自己的非习惯目标：3 条（习惯型与演示行留在门外）
        assertEquals(3, count(migrated, "SELECT COUNT(*) FROM life_items"))

        val cumulative = fieldsOf(migrated, "今年读完 12 本书")
        assertEquals("12", cumulative["target"])
        assertEquals("7", cumulative["current"])
        assertEquals("本", cumulative["unitText"])
        assertEquals("5000", cumulative["deadline"])
        assertNull("递增型不该写起始值", cumulative["initial"])

        val decreasing = fieldsOf(migrated, "戒烟")
        assertEquals("0", decreasing["target"])
        assertEquals("15", decreasing["current"])
        assertEquals("递减型的起始读数要保留", "20", decreasing["initial"])
        assertEquals("支", decreasing["unitText"])

        migrated.query("SELECT status, note, dueDate, createdAt, updatedAt FROM life_items WHERE title = '背完单词'")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("当前值达到目标值即已完成", "COMPLETED", c.getString(0))
                assertEquals("描述进备注", "描述", c.getString(1))
                assertTrue("截止日期只进字段，不映射执行列", c.isNull(2))
                assertEquals(2000L, c.getLong(3))
                assertEquals(3000L, c.getLong(4))
            }

        // 旧表已删、旧关联已清
        assertEquals(0, count(migrated, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'goals'"))
        assertEquals(0, count(migrated, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'goal_check_ins'"))
        migrated.query("SELECT linkedGoalId FROM moments").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("指向旧 goals 的关联要清空", c.isNull(0))
        }
        migrated.close()
    }

    @Test
    fun migrate17To18_emptyDatabaseStaysEmpty() {
        helper.createDatabase("migration-17-to-18-empty", 17).close()

        val migrated = helper.runMigrationsAndValidate("migration-17-to-18-empty", 18, true, MIGRATION_17_18)

        assertEquals(0, count(migrated, "SELECT COUNT(*) FROM life_templates"))
        assertEquals(0, count(migrated, "SELECT COUNT(*) FROM life_items"))
        migrated.close()
    }
}
