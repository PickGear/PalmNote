package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "life_items",
    indices = [
        Index(value = ["templateId", "status"], name = "idx_items_template_status"),
        Index(value = ["templateId"], name = "idx_items_template"),
        Index(value = ["createdAt"], name = "idx_items_created"),
        Index(value = ["status"], name = "idx_items_status"),
        Index(value = ["dueDate"], name = "idx_items_due"),
        Index(value = ["parentId"], name = "idx_items_parent")
    ]
)
@Immutable
data class LifeItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val title: String,
    val fieldsData: String = "{}",
    val status: String = "ACTIVE",
    val note: String = "",
    val sortOrder: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // ---- 执行列（v8 新增，查询索引，非展示信源；fieldsData 仍是详情/卡片唯一信源）----
    val dueDate: Long? = null,
    val dueTime: Int? = null,
    /**
     * ⚠️ 条目级「重复」四列：**目前无消费方**（2026-09-30 全仓审计：仅 repository 透传 + CSV 往返）。
     *
     * 真正需要的重复已由**模板级** [LifeTemplate.repeatYearly] 承担（提醒路径一直是按模板算的），
     * 因此这四列是历史遗留。**别再往它们写值，也别据此判断"是否重复"**。
     *
     * 决定（2026-10-01）：**不做删列迁移** —— 零用户收益，却要重建本项目最大的表，
     * 而且会叠在尚未经过真机验证的 v12→v13 之上（一旦升级出问题，无法判断是哪次迁移的锅）。
     */
    val recurring: String? = null,
    val recurringEndType: String? = null,
    val recurringEndCount: Int? = null,
    val recurringEndDate: Long? = null,
    val parentId: Long? = null,
    val remindAt: Int? = null,
    val meta: String? = null,
    /**
     * 是否由 [com.palmnote.data.LifeDemoSeeder] 播种的示例行。
     *
     * 这是「可以丢弃」的**唯一**判据：关闭演示模式时按它物理删除、导出时按它排除。
     * 不要再用 [meta] 做这件事——[meta] 是**可见性**口径，演示模式下用户自己新建的
     * 记录也会带上它，拿它当删除依据就会删掉真实数据（v10 及以前的实际行为）。
     */
    @ColumnInfo(defaultValue = "0")
    val isSeedSample: Boolean = false
)
