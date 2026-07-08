package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.theme.AppIcon

@Entity(tableName = "anniversaries")
data class Anniversary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val solarDate: Long, // timestamp
    val isLunar: Boolean = false,
    val lunarYear: Int? = null,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarLeapMonth: Boolean = false,
    val type: String = "OTHER", // BIRTHDAY, WEDDING, MEETING, GRADUATION, JOB, TRAVEL, BABY, PET, CUSTOM
    val personName: String = "", // 相关人物
    val personRelation: String = "", // 关系: 家人/朋友/同事/恋人
    val isYearly: Boolean = true, // 每年提醒
    val displayMode: String = "COUNT_UP", // COUNT_UP(正计时), COUNT_DOWN(倒计时)
    val multiRemindJson: String = "", // JSON array of remind days
    val reminderTime: String = "09:00", // 提醒时间
    val notificationEnabled: Boolean = true,
    val color: String = "", // 自定义颜色
    @ColumnInfo(defaultValue = "Favorite")
    val icon: AppIcon = AppIcon.Favorite, // 自定义图标
    @ColumnInfo(defaultValue = "")
    val emoji: String = "", // 遗留字段，已迁移至 icon
    val linkedMomentId: Long? = null,
    val isPinned: Boolean = false, // 置顶
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val daysUntil: Int
        get() {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = solarDate
            val nowCal = java.util.Calendar.getInstance()

            // 计算今年的这个日期
            cal.set(java.util.Calendar.YEAR, nowCal.get(java.util.Calendar.YEAR))
            var target = cal.timeInMillis

            // 如果今年的日期已过，算明年
            if (target < now) {
                cal.add(java.util.Calendar.YEAR, 1)
                target = cal.timeInMillis
            }

            return ((target - now) / DateUtils.MILLIS_PER_DAY).toInt()
        }

    val daysSince: Int
        get() = ((System.currentTimeMillis() - solarDate) / DateUtils.MILLIS_PER_DAY).toInt()

    val typeText: String
        get() = when (type) {
            "BIRTHDAY" -> "生日"
            "WEDDING" -> "结婚纪念"
            "MEETING" -> "相识纪念"
            "GRADUATION" -> "毕业纪念"
            "JOB" -> "工作纪念"
            "TRAVEL" -> "旅行纪念"
            "BABY" -> "宝宝纪念"
            "PET" -> "宠物纪念"
            "CUSTOM" -> "自定义"
            else -> "其他"
        }

    val typeIcon: AppIcon
        get() = when (type) {
            "BIRTHDAY" -> AppIcon.Celebration
            "WEDDING" -> AppIcon.Favorite
            "MEETING" -> AppIcon.Group
            "GRADUATION" -> AppIcon.School
            "JOB" -> AppIcon.Work
            "TRAVEL" -> AppIcon.Flight
            "BABY" -> AppIcon.ChildCare
            "PET" -> AppIcon.Pets
            else -> AppIcon.Today
        }

    val displayTitle: String
        get() = if (personName.isNotEmpty()) "${personName}的$title" else title
}
