package com.palmnote.data.db.converter

import androidx.room.TypeConverter
import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.AutoLockMode
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import com.palmnote.domain.model.PaymentMethod
import com.palmnote.domain.model.RecurringFrequency
import com.palmnote.domain.model.TimeOfDay
import com.palmnote.ui.theme.AppIcon
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ---- 已有枚举 ----

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

    // ---- 新增枚举 ----

    @TypeConverter
    fun fromBillType(v: BillType): String = v.value

    @TypeConverter
    fun toBillType(s: String): BillType = BillType.from(s)

    @TypeConverter
    fun fromAssetStatus(v: AssetStatus): String = v.value

    @TypeConverter
    fun toAssetStatus(s: String): AssetStatus = AssetStatus.from(s)

    @TypeConverter
    fun fromPaymentMethod(v: PaymentMethod): String = v.value

    @TypeConverter
    fun toPaymentMethod(s: String): PaymentMethod = PaymentMethod.from(s)

    @TypeConverter
    fun fromRecurringFrequency(v: RecurringFrequency): String = v.value

    @TypeConverter
    fun toRecurringFrequency(s: String): RecurringFrequency = RecurringFrequency.from(s)

    @TypeConverter
    fun fromAutoLockMode(v: AutoLockMode): String = v.value

    @TypeConverter
    fun toAutoLockMode(s: String): AutoLockMode = AutoLockMode.from(s)

    @TypeConverter
    fun fromTimeOfDay(v: TimeOfDay): String = v.value

    @TypeConverter
    fun toTimeOfDay(s: String): TimeOfDay = TimeOfDay.from(s)

    // ---- 集合 ----

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
