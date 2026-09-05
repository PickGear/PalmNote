package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.domain.model.AssetStatus

/**
 * 物品回收站：删除的物品暂存于此，可恢复。
 */
@Entity(tableName = "assets_recycle_bin")
data class AssetRecycleBin(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalId: Long,
    val name: String,
    val category: String,
    val subCategory: String = "",
    val brand: String = "",
    val model: String = "",
    val purchasePrice: Long = 0,
    val acquisitionType: String = "PURCHASE",
    val acquisitionDate: Long? = null,
    val status: AssetStatus = AssetStatus.HELD,
    val costMode: String = "DAILY",
    val quantity: Int = 1,
    val useCount: Int = 0,
    val totalUsageHours: Double = 0.0,
    val location: String = "",
    val room: String = "",
    val purchaseChannel: String = "",
    val warrantyExpireDate: Long? = null,
    val insuranceExpireDate: Long? = null,
    val insuranceCompany: String = "",
    val insurancePolicyNo: String = "",
    val images: String = "",
    val description: String = "",
    val condition: String = "GOOD",
    val serialNumber: String = "",
    val receiptPath: String = "",
    val depreciationRate: Double = 0.0,
    val currentValue: Long = 0,
    val maintenanceIntervalDays: Int = 0,
    val lastMaintenanceDate: Long? = null,
    val nextMaintenanceDate: Long? = null,
    val maintenanceNotes: String = "",
    val isFavorite: Boolean = false,
    val tags: String = "",
    val linkedBillId: Long? = null,
    val linkedMomentId: Long? = null,
    val retireDate: Long? = null,
    val retireReason: String = "",
    val lostDate: Long? = null,
    val lostReason: String = "",
    val soldDate: Long? = null,
    val soldPrice: Long? = null,
    val soldChannel: String? = null,
    val soldToWhom: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = System.currentTimeMillis()
)

fun Asset.toRecycleBin() = AssetRecycleBin(
    originalId = id, name = name, category = category, subCategory = subCategory,
    brand = brand, model = model, purchasePrice = purchasePrice,
    acquisitionType = acquisitionType, acquisitionDate = acquisitionDate,
    status = status, costMode = costMode, quantity = quantity,
    useCount = useCount, totalUsageHours = totalUsageHours,
    location = location, room = room, purchaseChannel = purchaseChannel,
    warrantyExpireDate = warrantyExpireDate, insuranceExpireDate = insuranceExpireDate,
    insuranceCompany = insuranceCompany, insurancePolicyNo = insurancePolicyNo,
    images = images, description = description, condition = condition,
    serialNumber = serialNumber, receiptPath = receiptPath,
    depreciationRate = depreciationRate, currentValue = currentValue,
    maintenanceIntervalDays = maintenanceIntervalDays,
    lastMaintenanceDate = lastMaintenanceDate, nextMaintenanceDate = nextMaintenanceDate,
    maintenanceNotes = maintenanceNotes, isFavorite = isFavorite, tags = tags,
    linkedBillId = linkedBillId, linkedMomentId = linkedMomentId,
    retireDate = retireDate, retireReason = retireReason,
    lostDate = lostDate, lostReason = lostReason,
    soldDate = soldDate, soldPrice = soldPrice, soldChannel = soldChannel,
    soldToWhom = soldToWhom, sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = System.currentTimeMillis()
)

fun AssetRecycleBin.toAsset() = Asset(
    id = originalId, name = name, category = category, subCategory = subCategory,
    brand = brand, model = model, purchasePrice = purchasePrice,
    acquisitionType = acquisitionType, acquisitionDate = acquisitionDate,
    status = status, costMode = costMode, quantity = quantity,
    useCount = useCount, totalUsageHours = totalUsageHours,
    location = location, room = room, purchaseChannel = purchaseChannel,
    warrantyExpireDate = warrantyExpireDate, insuranceExpireDate = insuranceExpireDate,
    insuranceCompany = insuranceCompany, insurancePolicyNo = insurancePolicyNo,
    images = images, description = description, condition = condition,
    serialNumber = serialNumber, receiptPath = receiptPath,
    depreciationRate = depreciationRate, currentValue = currentValue,
    maintenanceIntervalDays = maintenanceIntervalDays,
    lastMaintenanceDate = lastMaintenanceDate, nextMaintenanceDate = nextMaintenanceDate,
    maintenanceNotes = maintenanceNotes, isFavorite = isFavorite, tags = tags,
    linkedBillId = linkedBillId, linkedMomentId = linkedMomentId,
    retireDate = retireDate, retireReason = retireReason,
    lostDate = lostDate, lostReason = lostReason,
    soldDate = soldDate, soldPrice = soldPrice, soldChannel = soldChannel,
    soldToWhom = soldToWhom, sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt
)
