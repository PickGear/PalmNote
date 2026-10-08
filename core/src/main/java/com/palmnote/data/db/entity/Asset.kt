package com.palmnote.data.db.entity

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.palmnote.R
import androidx.compose.runtime.Immutable
import com.palmnote.domain.util.DateUtils
import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.ExpiryKind

@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["warrantyExpireDate"]),
        Index(value = ["shelfLifeExpireDate"]),
        Index(value = ["nextMaintenanceDate"]),
        Index(value = ["insuranceExpireDate"]),
        Index(value = ["isFavorite"])
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
    val status: AssetStatus = AssetStatus.HELD, // HELD, AWAY, REMOVED
    val costMode: String = "DAILY", // DAILY, PER_USE, DEPRECIATION
    val quantity: Int = 1,
    val useCount: Int = 0,
    val totalUsageHours: Double = 0.0, // 累计使用时长(小时)
    val location: String = "",
    val room: String = "", // 房间: 卧室/客厅/书房/厨房/卫生间
    val purchaseChannel: String = "",
    val warrantyExpireDate: Long? = null,
    val shelfLifeExpireDate: Long? = null, // 保质期到期日
    val shelfLifeProducedDate: Long? = null, // 生产日期（按「保质期限」录法时记，直接填到期日时为 null）
    val shelfLifeDurationValue: Int? = null, // 保质期时长数值
    val shelfLifeDurationUnit: String? = null, // 保质期时长单位（ShelfLifeUnit.value）
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
    /**
     * 演示数据标记（v14）：示例账本 / 示例钱包 / 示例账单 / 示例物品。
     * 演示模式关闭时按此列整批物理删除；CSV 导出排除（示例不进「我的数据」）。
     */
    @ColumnInfo(defaultValue = "0")
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 质保 / 保险是否还在有效期内。
     *
     * ⚠️ 口径必须与界面上那个「剩余 N 天」chip 一致，也就是**自然日**（`DateUtils.getDaysUntil`）。
     * 这两个日期列存的是当天 00:00，直接拿时间戳比大小会在**到期当天**就判成"已过期"，
     * 于是同一张卡上出现「红色过期图标 + 绿色 剩余 0 天」这种自相矛盾的组合。
     * 到期日当天算有效——那天是最后一天。
     */
    val isWarrantyValid: Boolean
        get() = warrantyExpireDate?.let { DateUtils.getDaysUntil(it) >= 0 } ?: false

    val isInsuranceValid: Boolean
        get() = insuranceExpireDate?.let { DateUtils.getDaysUntil(it) >= 0 } ?: false

    val effectiveDate: Long
        get() = acquisitionDate ?: createdAt

    val daysOwned: Long
        get() = ((System.currentTimeMillis() - effectiveDate) / DateUtils.MILLIS_PER_DAY).coerceAtLeast(1)

    val displayPrice: Long
        get() = when {
            status == AssetStatus.REMOVED && soldPrice != null -> soldPrice
            currentValue > 0 -> currentValue
            else -> purchasePrice
        }

    val dailyCost: Double
        get() = if (daysOwned > 0) purchasePrice.toDouble() / 100.0 / daysOwned else 0.0

    val isMaintenanceDue: Boolean
        get() = nextMaintenanceDate != null && nextMaintenanceDate <= System.currentTimeMillis()
}

fun Asset.getWarrantyStatusText(context: Context): String {
    val expire = warrantyExpireDate ?: return ""
    val days = DateUtils.getDaysUntil(expire)
    return when {
        days < 0 -> context.getString(R.string.asset_warranty_expired)
        days <= 30 -> context.getString(R.string.asset_warranty_days, days)
        else -> context.getString(R.string.asset_warranty_active)
    }
}

fun Asset.getInsuranceStatusText(context: Context): String {
    return when {
        insuranceExpireDate == null -> ""
        isInsuranceValid -> context.getString(R.string.asset_insurance_active)
        else -> context.getString(R.string.asset_insurance_expired)
    }
}

fun Asset.getShelfLifeStatusText(context: Context): String {
    val expire = shelfLifeExpireDate ?: return ""
    val days = DateUtils.getDaysUntil(expire)
    return when {
        days < 0 -> context.getString(R.string.asset_shelf_life_expired)
        days <= 30 -> context.getString(R.string.asset_shelf_life_days, days)
        else -> context.getString(R.string.asset_shelf_life_active)
    }
}

/**
 * 质保 / 保质期里更早的那个到期日，以及它属于哪一种；都没有则 null。
 *
 * 界面（列表卡、网格卡、详情页头部）与提醒**共用这一个判定**——否则会出现
 * 「卡片上橙色标着质保 1 天、却因为提醒只看保质期而一条通知都不发」这种自相矛盾。
 */
val Asset.nearestExpiry: Pair<ExpiryKind, Long>?
    get() {
        val warranty = warrantyExpireDate
        val shelfLife = shelfLifeExpireDate
        return when {
            warranty == null -> shelfLife?.let { ExpiryKind.SHELF_LIFE to it }
            shelfLife == null -> ExpiryKind.WARRANTY to warranty
            shelfLife <= warranty -> ExpiryKind.SHELF_LIFE to shelfLife
            else -> ExpiryKind.WARRANTY to warranty
        }
    }

val Asset.nearestExpiryDate: Long?
    get() = nearestExpiry?.second

val Asset.nearestExpiryKind: ExpiryKind?
    get() = nearestExpiry?.first

/** [nearestExpiry] 对应的文案，按更早的那种选措辞（质保 / 保质期）。 */
fun Asset.getNearestExpiryText(context: Context): String = when (nearestExpiryKind) {
    ExpiryKind.SHELF_LIFE -> getShelfLifeStatusText(context)
    ExpiryKind.WARRANTY -> getWarrantyStatusText(context)
    null -> ""
}
