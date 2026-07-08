package com.palmnote.data.db.converter

import androidx.room.TypeConverter
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import com.palmnote.ui.theme.AppIcon
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @TypeConverter
    fun fromEntityType(type: EntityType): String = type.name

    @TypeConverter
    fun toEntityType(name: String): EntityType = try { EntityType.valueOf(name) } catch (_: Exception) { EntityType.ITEM }

    @TypeConverter
    fun fromLinkType(type: LinkType): String = type.name

    @TypeConverter
    fun toLinkType(name: String): LinkType = try { LinkType.valueOf(name) } catch (_: Exception) { LinkType.PART_OF }

    @TypeConverter
    fun fromAppIcon(icon: AppIcon): String = icon.name

    @TypeConverter
    fun toAppIcon(name: String): AppIcon = AppIcon.fromName(name)

    @TypeConverter
    fun fromStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (_: Exception) {
            listOf(value)
        }
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return if (list.isEmpty()) "" else json.encodeToString(list)
    }

    @TypeConverter
    fun fromLongList(value: String): List<Long> {
        if (value.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<Long>>(value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun toLongList(list: List<Long>): String {
        return if (list.isEmpty()) "" else json.encodeToString(list)
    }
}
