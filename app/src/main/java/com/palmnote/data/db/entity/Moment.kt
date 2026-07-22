package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import com.palmnote.ui.theme.AppIcon


@Entity(tableName = "moments")
@Immutable
data class Moment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val images: String = "", // JSON array of image paths
    val videoPath: String = "", // 视频路径
    val audioPath: String = "", // 录音路径
    val mood: String = "", // GREAT, GOOD, OK, BAD, TERRIBLE
    val weather: String = "", // SUNNY, CLOUDY, RAINY, SNOWY, WINDY, FOGGY
    val temperature: Int = Int.MIN_VALUE, // 温度
    val location: String = "", // 位置名称
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tags: String = "", // JSON array of tags
    val category: String = "", // TRAVEL, FOOD, FAMILY, WORK, FRIEND, HOBBY, NATURE, PET, CUSTOM
    val privacy: String = "PRIVATE", // PRIVATE, FRIENDS, PUBLIC
    val isPinned: Boolean = false, // 置顶
    val isFavorite: Boolean = false, // 收藏
    val wordCount: Int = 0, // 字数统计
    val linkedAssetId: Long? = null,
    val linkedAnniversaryId: Long? = null,
    val linkedGoalId: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val moodIcon: AppIcon
        get() = when (mood) {
            "GREAT" -> AppIcon.SentimentVerySatisfied
            "GOOD" -> AppIcon.SentimentSatisfied
            "OK" -> AppIcon.SentimentNeutral
            "BAD" -> AppIcon.SentimentDissatisfied
            "TERRIBLE" -> AppIcon.SentimentVeryDissatisfied
            else -> AppIcon.SentimentNeutral
        }

    val moodText: String
        get() = when (mood) {
            "GREAT" -> "开心"
            "GOOD" -> "不错"
            "OK" -> "一般"
            "BAD" -> "难过"
            "TERRIBLE" -> "糟糕"
            else -> ""
        }

    val weatherIcon: AppIcon
        get() = when (weather) {
            "SUNNY" -> AppIcon.WbSunny
            "CLOUDY" -> AppIcon.Cloud
            "RAINY" -> AppIcon.WaterDrop
            "SNOWY" -> AppIcon.AcUnit
            "WINDY" -> AppIcon.Air
            "FOGGY" -> AppIcon.Cloud
            else -> AppIcon.Cloud
        }

    val weatherText: String
        get() = when (weather) {
            "SUNNY" -> "晴"
            "CLOUDY" -> "多云"
            "RAINY" -> "雨"
            "SNOWY" -> "雪"
            "WINDY" -> "大风"
            "FOGGY" -> "雾"
            else -> ""
        }

    val categoryText: String
        get() = when (category) {
            "TRAVEL" -> "旅行"
            "FOOD" -> "美食"
            "FAMILY" -> "家庭"
            "WORK" -> "工作"
            "FRIEND" -> "朋友"
            "HOBBY" -> "爱好"
            "NATURE" -> "自然"
            "PET" -> "宠物"
            "CUSTOM" -> "其他"
            else -> ""
        }

    val categoryIcon: AppIcon
        get() = when (category) {
            "TRAVEL" -> AppIcon.Flight
            "FOOD" -> AppIcon.Restaurant
            "FAMILY" -> AppIcon.Groups
            "WORK" -> AppIcon.Work
            "FRIEND" -> AppIcon.Favorite
            "HOBBY" -> AppIcon.Palette
            "NATURE" -> AppIcon.Eco
            "PET" -> AppIcon.Pets
            else -> AppIcon.Note
        }

    val hasMedia: Boolean
        get() = images.isNotEmpty() || videoPath.isNotEmpty() || audioPath.isNotEmpty()

    val displayDate: String
        get() {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = timestamp
            val nowCal = java.util.Calendar.getInstance()
            return when {
                cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR) -> "今天"
                cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR) - 1 -> "昨天"
                else -> "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
            }
        }
}
