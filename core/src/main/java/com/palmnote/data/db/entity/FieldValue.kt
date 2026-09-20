package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 统计信源表（总纲 §7.1，v9 新增）。展示仍读 `life_items.fieldsData`，唯一切换点在 Repository；
 * 本表是「双写」模式的统计副本：数值 → SUM/AVG，选项 → COUNT/分布，日期 → 时间轴，复合字段按行展开。
 *
 * 写入路径与 `fieldsData` 同一事务（LifeItemRepositoryImpl）；只读 `fieldsData` 不会丢失统计能力。
 */
@Entity(
    tableName = "field_values",
    indices = [Index(value = ["fieldKey", "num"]), Index(value = ["itemId"])]
)
data class FieldValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val fieldKey: String,
    val type: String,
    /** 数值 / 金额 / 百分比 / 时长（TIME 存分钟数，BOOLEAN 存 0/1）→ SUM/AVG。 */
    val num: Double? = null,
    /** 选项 / 标签 → COUNT / DISTRIBUTION。 */
    val text: String? = null,
    /** 日期毫秒 → 时间轴 / 按天分组。 */
    val dateMs: Long? = null,
    /** 复合字段（TABLE/RANGE/PERSON/MAP 等）的结构化载荷。 */
    val json: String? = null,
    /** 复合字段的行序号（CHECKLIST/TABLE/MULTI_SELECT 展开）。 */
    val idx: Int = 0
)
