package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.theme.AppIcon
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 距下一次纪念日还有几天（**自然日**）。
     *
     * 必须按**日期**比大小，不能拿时间戳比：`solarDate` 存的是当天 00:00，而"现在"永远大于它，
     * 于是**纪念日当天**会被判成"今年已过"、跳到明年，显示成「还有 365 天」——那天恰恰是最该
     * 显示「今天」的一天（界面上 `daysUntil == 0 -> 今天` 那条分支因此从来没生效过）。
     * 同时也不能用毫秒除法（`(target-now)/86400000` 截断），否则平时也会少一天。
     */
    val daysUntil: Int
        get() {
            val today = LocalDate.now()
            // withYear 对 2/29 会自动收敛到该年最后一天（平年 → 2/28）
            val thisYear = DateUtils.millisToLocalDate(solarDate).withYear(today.year)
            val next = if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
            return ChronoUnit.DAYS.between(today, next).toInt()
        }

    /** 距原始日期已过去多少天（自然日；纪念日当天是 0）。 */
    val daysSince: Int
        get() = DateUtils.getDaysSince(solarDate)

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
