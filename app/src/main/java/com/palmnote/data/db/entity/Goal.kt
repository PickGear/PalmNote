package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.palmnote.domain.util.DateUtils
import androidx.compose.runtime.Immutable
import com.palmnote.ui.theme.AppIcon

@Entity(tableName = "goals", indices = [
    Index(value = ["goalType", "isDeleted"], name = "idx_goal_type_deleted"),
    Index(value = ["category"], name = "idx_goal_category"),
    Index(value = ["isDeleted"], name = "idx_goal_deleted"),
    Index(value = ["deadline"], name = "idx_goal_deadline")
])
@Immutable
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "", // FITNESS, READING, SKILL, HABIT, PROJECT, SAVING, CUSTOM
    val goalType: String = "CUMULATIVE", // CUMULATIVE(累计), HABIT(习惯打卡), TARGET(目标值)
    val totalCount: Int = 100,
    val currentCount: Int = 0,
    val unit: String = "次", // 次, 页, 公里, 小时, 元, 天
    val frequency: String = "", // DAILY, WEEKLY, MONTHLY (习惯频率)
    val targetPerPeriod: Int = 0, // 每周期目标次数
    val currentPeriodCount: Int = 0, // 当前周期完成次数
    val periodStartDate: Long? = null, // 当前周期开始日期
    val deadline: Long? = null,
    val startDate: Long = System.currentTimeMillis(),
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val color: String = "", // 自定义颜色
    @ColumnInfo(defaultValue = "Flag")
    val icon: AppIcon = AppIcon.Flag, // 自定义图标
    val streak: Int = 0, // 连续完成天数
    val longestStreak: Int = 0, // 最长连续天数
    val lastCheckInDate: Long? = null, // 上次打卡日期
    val totalCheckInDays: Int = 0, // 总打卡天数
    val currentPeriodStart: Long = 0, // 当前周期开始时间
    val currentPeriodEnd: Long = 0, // 当前周期结束时间
    val direction: String = "INCREASE", // INCREASE, DECREASE (目标值型)
    val initialValue: Long = 0, // 初始值 (DECREASE 类型用)
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "", // "08:00" 格式
    val linkedAssetId: Long? = null, // 关联资产(如跑步→跑鞋)
    val isPublic: Boolean = false, // 是否公开展示
    val notes: String = "", // 进度笔记
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalCount > 0) currentCount.toFloat() / totalCount else 0f

    val isCompleted: Boolean
        get() = currentCount >= totalCount

    val isOverdue: Boolean
        get() = deadline != null && deadline < System.currentTimeMillis() && !isCompleted

    val isHabit: Boolean
        get() = goalType == "HABIT"

    val daysUntilDeadline: Int
        get() = if (deadline != null) {
            ((deadline - System.currentTimeMillis()) / DateUtils.MILLIS_PER_DAY).toInt()
        } else Int.MAX_VALUE

    val priorityText: String
        get() = when (priority) {
            "HIGH" -> "高优先"
            "MEDIUM" -> "中优先"
            "LOW" -> "低优先"
            else -> "中优先"
        }

    val categoryText: String
        get() = when (category) {
            "FITNESS" -> "运动健身"
            "READING" -> "阅读学习"
            "SKILL" -> "技能提升"
            "HABIT" -> "日常习惯"
            "PROJECT" -> "项目计划"
            "SAVING" -> "储蓄理财"
            "CUSTOM" -> "自定义"
            else -> category.ifEmpty { "未分类" }
        }
}

@Entity(tableName = "goal_check_ins", indices = [
    Index(value = ["goalId"], name = "idx_checkin_goal"),
    Index(value = ["goalId", "date"], name = "idx_checkin_goal_date")
])
@Immutable
data class GoalCheckIn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalId: Long,
    val date: Long, // 打卡日期(天)
    val count: Int = 1, // 本次完成数量
    val note: String = "",
    val mood: String = "", // GREAT, GOOD, OK, BAD
    val duration: Int = 0, // 用时(分钟)
    val createdAt: Long = System.currentTimeMillis()
)
