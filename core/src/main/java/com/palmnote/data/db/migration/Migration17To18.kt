package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.palmnote.domain.util.BuiltinGoalTemplate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * v17 → v18：目标并入生活模板体系，`goals` / `goal_check_ins` 两表退役。
 *
 * ## 背景
 *
 * 「目标」原是一套与生活模板并行的独立子系统（`goals` 表 + 独立列表/编辑页 + 顶栏 Flag 入口）。
 * 累计型 / 目标值型目标与模板的进度字段（`showAsProgress`）语义重叠，同类需求两处建。
 * v1.4.2 起目标成为**内置模板**（[BuiltinGoalTemplate]，kind = GOAL），每条目标 = 一条生活条目。
 *
 * ## 迁移内容
 *
 * 1. 建「目标」模板行（**仅当确有需要搬运的目标**；否则由 `LifeDataSeeder` 全新播种）；
 * 2. 把 `goals` 里**用户自己的、非习惯打卡**的行（`isDemo = 0 AND goalType <> 'HABIT'`）
 *    逐条转成 life_items：标题 / 描述（→ note）/ 目标值 / 当前值 / 起始值 / 单位 / 截止日期。
 *    - **HABIT 型不搬**：习惯语义早已由生活模板体系的「打卡」模板承担（`goals` 里的习惯行
 *      只有演示播种在写，且从不进任何列表）；
 *    - **演示行不搬**：演示目标改由 `LifeDemoData` 按新结构播种；
 *    - **递减型（DECREASE）**：`initialValue` 写进 `initial` 字段，进度引擎据此按
 *      `(起始 − 当前) / (起始 − 目标)` 算（见 `FieldConfig.progressStartKey`）；
 *    - `deadline` 写进 fieldsData 的 `deadline` 字段；迁移直接写行不碰执行列
     *      `dueDate`（此后一经仓库保存即按通用日期镜像同步，与 UI 新建的目标同口径）。
 * 3. 清掉 `moments.linkedGoalId`：目标行 id 从此无效，留着是悬空引用（该列本就无读方）；
 * 4. `DROP TABLE goals` / `DROP TABLE goal_check_ins`。
 *
 * 物化到模板行的 `fieldsConfig` 与种子共用 [BuiltinGoalTemplate] 常量 —— 两处若漂移，
 * 迁移过来的数据会因字段 key 对不上而**静默没有进度条**。
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val goals = readMigratableGoals(db)
        if (goals.isNotEmpty()) {
            val templateId = insertGoalTemplate(db)
            goals.forEach { goal ->
                db.execSQL(
                    "INSERT INTO `life_items` (" +
                        "`templateId`, `title`, `fieldsData`, `status`, `note`, `sortOrder`, " +
                        "`isFavorite`, `createdAt`, `updatedAt`, `meta`, `isSeedSample`" +
                        ") VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, NULL, 0)",
                    arrayOf(
                        templateId,
                        goal.title,
                        goal.fieldsData(),
                        if (goal.completed) "COMPLETED" else "ACTIVE",
                        goal.description,
                        goal.createdAt,
                        goal.updatedAt
                    )
                )
            }
        }
        // 目标行 id 即将消失：瞬间表里指向它们的关联没有落点（Moment.linkedGoalId 无读方）
        db.execSQL("UPDATE `moments` SET `linkedGoalId` = NULL WHERE `linkedGoalId` IS NOT NULL")
        db.execSQL("DROP TABLE IF EXISTS `goal_check_ins`")
        db.execSQL("DROP TABLE IF EXISTS `goals`")
    }

    private fun readMigratableGoals(db: SupportSQLiteDatabase): List<LegacyGoal> {
        val sql = "SELECT `title`, `description`, `totalCount`, `currentCount`, `unit`, " +
            "`deadline`, `direction`, `initialValue`, `createdAt`, `updatedAt` " +
            "FROM `goals` WHERE `isDemo` = 0 AND `goalType` <> 'HABIT' ORDER BY `id`"
        return db.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LegacyGoal(
                            title = c.getString(0),
                            description = c.getString(1) ?: "",
                            totalCount = c.getInt(2),
                            currentCount = c.getInt(3),
                            unit = c.getString(4) ?: "",
                            deadline = if (c.isNull(5)) null else c.getLong(5),
                            direction = c.getString(6) ?: "INCREASE",
                            initialValue = c.getLong(7),
                            createdAt = c.getLong(8),
                            updatedAt = c.getLong(9)
                        )
                    )
                }
            }
        }
    }

    private fun insertGoalTemplate(db: SupportSQLiteDatabase): Long {
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO `life_templates` (" +
                "`name`, `category`, `icon`, `color`, `description`, `fieldsConfig`, `layoutType`, " +
                "`availableLayouts`, `statusFlowConfig`, `linkConfig`, `isBuiltin`, `isHidden`, " +
                "`isSpecial`, `sortOrder`, `kind`, `repeatYearly`, `createdAt`, `updatedAt`" +
                ") VALUES (?, ?, ?, ?, ?, ?, 'card', ?, ?, ?, 1, 0, 0, ?, ?, 0, ?, ?)",
            arrayOf(
                BuiltinGoalTemplate.NAME,
                BuiltinGoalTemplate.CATEGORY,
                BuiltinGoalTemplate.ICON,
                BuiltinGoalTemplate.COLOR,
                BuiltinGoalTemplate.DESCRIPTION,
                BuiltinGoalTemplate.FIELDS_CONFIG,
                BuiltinGoalTemplate.AVAILABLE_LAYOUTS,
                BuiltinGoalTemplate.STATUS_FLOW,
                BuiltinGoalTemplate.LINK_CONFIG,
                BuiltinGoalTemplate.SORT_ORDER,
                "GOAL",
                now,
                now
            )
        )
        return db.query("SELECT last_insert_rowid()").use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }
}

/** 迁移期间从 `goals` 读出的行（Goal 实体已退役，这里保留最小投影）。 */
private data class LegacyGoal(
    val title: String,
    val description: String,
    val totalCount: Int,
    val currentCount: Int,
    val unit: String,
    val deadline: Long?,
    val direction: String,
    val initialValue: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 递减型判据与进度引擎一致：DECREASE 且起始值大于目标值。 */
    private val decreasing = direction == "DECREASE" && initialValue > totalCount

    val completed: Boolean
        get() = if (decreasing) currentCount <= totalCount else currentCount >= totalCount

    fun fieldsData(): String {
        val entries = buildMap<String, JsonElement> {
            put("target", JsonPrimitive(totalCount))
            put("current", JsonPrimitive(currentCount))
            if (decreasing) put("initial", JsonPrimitive(initialValue))
            if (unit.isNotBlank()) put("unitText", JsonPrimitive(unit))
            deadline?.let { put("deadline", JsonPrimitive(it)) }
        }
        return JsonObject(entries).toString()
    }
}
