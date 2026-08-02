package com.palmnote.data.db.entity

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.palmnote.R
import androidx.compose.runtime.Immutable
import com.palmnote.domain.util.DateUtils

@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["status", "isDeleted"]),
        Index(value = ["category", "isDeleted"]),
        Index(value = ["warrantyExpireDate", "isDeleted"]),
        Index(value = ["nextMaintenanceDate", "isDeleted"]),
        Index(value = ["insuranceExpireDate", "isDeleted"]),
        Index(value = ["isFavorite", "isDeleted"])
    ]
)
@Immutable
data class Asset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String,
    val subCategory: String = "", // 子分类，如"手机"下的"iPhone"
    val brand: String = "", // 品牌
    val model: String = "", // 型号
    val purchasePrice: Long = 0, // 购买价（分）
    val acquisitionType: String = "PURCHASE", // PURCHASE, GIFT, LOTTERY, PRIZE, INHERITANCE, OTHER
    val acquisitionDate: Long? = null,
    val status: String = "HELD", // HELD, AWAY, REMOVED
    val costMode: String = "DAILY", // DAILY, PER_USE, DEPRECIATION
    val quantity: Int = 1,
    val useCount: Int = 0,
    val totalUsageHours: Double = 0.0, // 累计使用时长(小时)
    val location: String = "",
    val room: String = "", // 房间: 卧室/客厅/书房/厨房/卫生间
    val purchaseChannel: String = "",
    val warrantyExpireDate: Long? = null,
    val insuranceExpireDate: Long? = null, // 保险到期日
    val insuranceCompany: String = "", // 保险公司
    val insurancePolicyNo: String = "", // 保单号
    val images: String = "", // JSON array of image paths
    val description: String = "",
    val condition: String = "GOOD", // NEW, GOOD, FAIR, POOR
    val serialNumber: String = "", // 序列号
    val receiptPath: String = "", // 电子发票路径
    val depreciationRate: Double = 0.0, // 年折旧率(%), 0表示不折旧
    val currentValue: Long = 0, // 当前估值（分）
    val maintenanceIntervalDays: Int = 0, // 维护提醒间隔(天), 0=不提醒
    val lastMaintenanceDate: Long? = null, // 上次维护日期
    val nextMaintenanceDate: Long? = null, // 下次维护日期
    val maintenanceNotes: String = "", // 维护记录备注
    val isFavorite: Boolean = false, // 收藏/常用
    val tags: String = "", // JSON array of tags
    val linkedBillId: Long? = null,
    val linkedMomentId: Long? = null,
    val retireDate: Long? = null,
    val retireReason: String = "",
    val lostDate: Long? = null,
    val lostReason: String = "",
    val soldDate: Long? = null,
    val soldPrice: Long? = null, // 售出价（分）
    val soldChannel: String? = null,
    val soldToWhom: String? = null, // 售出给谁
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isWarrantyValid: Boolean
        get() = warrantyExpireDate != null && warrantyExpireDate > System.currentTimeMillis()

    val isInsuranceValid: Boolean
        get() = insuranceExpireDate != null && insuranceExpireDate > System.currentTimeMillis()

    val warrantyStatusText: String
        get() = when {
            warrantyExpireDate == null -> ""
            isWarrantyValid -> {
                val days = ((warrantyExpireDate - System.currentTimeMillis()) / DateUtils.MILLIS_PER_DAY).toInt()
                if (days <= 30) "质保${days}天" else "质保中"
            }
            else -> "已过保"
        }

    val insuranceStatusText: String
        get() = when {
            insuranceExpireDate == null -> ""
            isInsuranceValid -> "保险有效"
            else -> "保险过期"
        }

    val effectiveDate: Long
        get() = acquisitionDate ?: createdAt

    val daysOwned: Long
        get() = ((System.currentTimeMillis() - effectiveDate) / DateUtils.MILLIS_PER_DAY).coerceAtLeast(1)

    val displayPrice: Long
        get() = when {
            status == "REMOVED" && soldPrice != null -> soldPrice
            currentValue > 0 -> currentValue
            else -> purchasePrice
        }

    val dailyCost: Double
        get() = if (daysOwned > 0) purchasePrice.toDouble() / 100.0 / daysOwned else 0.0

    val isMaintenanceDue: Boolean
        get() = nextMaintenanceDate != null && nextMaintenanceDate <= System.currentTimeMillis()
}

fun Asset.getWarrantyStatusText(context: Context): String {
    return when {
        warrantyExpireDate == null -> ""
        isWarrantyValid -> {
            val days = ((warrantyExpireDate - System.currentTimeMillis()) / DateUtils.MILLIS_PER_DAY).toInt()
            if (days <= 30) context.getString(R.string.asset_warranty_days, days) else context.getString(R.string.asset_warranty_active)
        }
        else -> context.getString(R.string.asset_warranty_expired)
    }
}

fun Asset.getInsuranceStatusText(context: Context): String {
    return when {
        insuranceExpireDate == null -> ""
        isInsuranceValid -> context.getString(R.string.asset_insurance_active)
        else -> context.getString(R.string.asset_insurance_expired)
    }
}
