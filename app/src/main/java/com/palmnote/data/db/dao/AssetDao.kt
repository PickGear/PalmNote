package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Asset
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE isDeleted = 0 ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getAllAssets(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE id = :id AND isDeleted = 0")
    suspend fun getAssetById(id: Long): Asset?

    @Query("SELECT * FROM assets WHERE id = :id AND isDeleted = 0")
    fun getAssetByIdFlow(id: Long): Flow<Asset?>

    @Query("SELECT * FROM assets WHERE category = :category AND isDeleted = 0 ORDER BY isFavorite DESC, sortOrder ASC")
    fun getAssetsByCategory(category: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE status = :status AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAssetsByStatus(status: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE acquisitionType = :type AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAssetsByAcquisitionType(type: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR model LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') AND isDeleted = 0")
    fun searchAssets(query: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getFavoriteAssets(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE nextMaintenanceDate IS NOT NULL AND nextMaintenanceDate <= :now AND status = 'HELD' AND isDeleted = 0")
    fun getAssetsNeedingMaintenance(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE warrantyExpireDate IS NOT NULL AND warrantyExpireDate > :now AND ((warrantyExpireDate - :now) / 86400000) <= 30 AND status = 'HELD' AND isDeleted = 0")
    fun getAssetsNeedingWarrantyAlert(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE insuranceExpireDate IS NOT NULL AND insuranceExpireDate > :now AND ((insuranceExpireDate - :now) / 86400000) <= 30 AND status = 'HELD' AND isDeleted = 0")
    fun getAssetsNeedingInsuranceAlert(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE room = :room AND isDeleted = 0 ORDER BY name ASC")
    fun getAssetsByRoom(room: String): Flow<List<Asset>>

    @Query("SELECT DISTINCT room FROM assets WHERE room != '' AND isDeleted = 0 ORDER BY room ASC")
    fun getAllRooms(): Flow<List<String>>

    @Query("SELECT DISTINCT brand FROM assets WHERE brand != '' AND isDeleted = 0 ORDER BY brand ASC")
    fun getAllBrands(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM assets WHERE isDeleted = 0")
    fun getTotalAssetCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM assets WHERE status = :status AND isDeleted = 0")
    fun getAssetCountByStatus(status: String): Flow<Int>

    @Query("SELECT SUM(purchasePrice) FROM assets WHERE isDeleted = 0 AND status != 'REMOVED'")
    fun getTotalAssetValue(): Flow<Double?>

    @Query("SELECT SUM(purchasePrice) FROM assets WHERE isDeleted = 0 AND status = 'HELD'")
    fun getHeldAssetValue(): Flow<Double?>

    @Query("SELECT SUM(soldPrice) FROM assets WHERE status = 'REMOVED' AND isDeleted = 0 AND soldPrice IS NOT NULL")
    fun getTotalSoldValue(): Flow<Double?>

    @Query("SELECT SUM(currentValue) FROM assets WHERE status = 'HELD' AND isDeleted = 0 AND currentValue > 0")
    fun getTotalCurrentValue(): Flow<Double?>

    @Query("SELECT category, COUNT(*) as count, SUM(purchasePrice) as totalValue FROM assets WHERE isDeleted = 0 GROUP BY category ORDER BY count DESC")
    fun getCategoryDistribution(): Flow<List<CategoryCount>>

    @Query("SELECT brand, COUNT(*) as count, SUM(purchasePrice) as totalValue FROM assets WHERE brand != '' AND isDeleted = 0 GROUP BY brand ORDER BY count DESC LIMIT 10")
    fun getBrandDistribution(): Flow<List<BrandCount>>

    @Query("SELECT * FROM assets WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedAssets(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE warrantyExpireDate IS NOT NULL AND warrantyExpireDate > :now AND isDeleted = 0 ORDER BY warrantyExpireDate ASC")
    fun getAssetsWithValidWarranty(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE warrantyExpireDate IS NOT NULL AND warrantyExpireDate <= :now AND isDeleted = 0")
    fun getAssetsWithExpiredWarranty(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("""
        SELECT * FROM assets
        WHERE isDeleted = 0
        ORDER BY
            CASE WHEN status = 'HELD' THEN 0
                 WHEN status = 'AWAY' THEN 1
                 WHEN status = 'REMOVED' THEN 2
                 ELSE 3 END,
            isFavorite DESC,
            updatedAt DESC
    """)
    fun getAllAssetsSortedByStatus(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE condition = :condition AND isDeleted = 0")
    fun getAssetsByCondition(condition: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE isDeleted = 0 AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR model LIKE '%' || :query || '%' OR serialNumber LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<Asset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: Asset): Long

    @Update
    suspend fun updateAsset(asset: Asset)

    @Query("UPDATE assets SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteAsset(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET isDeleted = 0, deletedAt = null, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restoreAsset(id: Long, restoredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun hardDeleteAsset(id: Long)

    @Query("UPDATE assets SET useCount = useCount + 1, updatedAt = :now WHERE id = :assetId")
    suspend fun incrementUseCount(assetId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET totalUsageHours = totalUsageHours + :hours, updatedAt = :now WHERE id = :assetId")
    suspend fun addUsageHours(assetId: Long, hours: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET isFavorite = :isFavorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET currentValue = :value, updatedAt = :now WHERE id = :id")
    suspend fun updateCurrentValue(id: Long, value: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET condition = :condition, updatedAt = :now WHERE id = :id")
    suspend fun updateCondition(id: Long, condition: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'AWAY', lastMaintenanceDate = :date, updatedAt = :now WHERE id = :id")
    suspend fun startMaintenance(id: Long, date: Long = System.currentTimeMillis(), now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'HELD', lastMaintenanceDate = :date, nextMaintenanceDate = :nextDate, maintenanceNotes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun completeMaintenance(id: Long, date: Long, nextDate: Long?, notes: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'AWAY', tags = :tags, retireDate = :date, retireReason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun awayAsset(id: Long, date: Long, tags: String, reason: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'REMOVED', retireDate = :retireDate, retireReason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun retireAsset(id: Long, retireDate: Long, reason: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'REMOVED', lostDate = :lostDate, lostReason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun markAssetLost(id: Long, lostDate: Long, reason: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'REMOVED', soldDate = :soldDate, soldPrice = :soldPrice, soldChannel = :soldChannel, soldToWhom = :soldToWhom, updatedAt = :now WHERE id = :id")
    suspend fun sellAsset(id: Long, soldDate: Long, soldPrice: Double, soldChannel: String, soldToWhom: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'HELD', retireDate = null, retireReason = '', lostDate = null, lostReason = '', soldDate = null, soldPrice = null, soldChannel = null, soldToWhom = null, updatedAt = :now WHERE id = :id")
    suspend fun reactivateAsset(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET linkedBillId = :billId, updatedAt = :now WHERE id = :id")
    suspend fun linkBill(id: Long, billId: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM assets")
    suspend fun deleteAll()

    @Query("UPDATE assets SET category = :newName WHERE category = :oldName AND isDeleted = 0")
    suspend fun updateCategoryName(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM assets WHERE category = :category AND isDeleted = 0")
    suspend fun countByCategory(category: String): Int

    @Query("UPDATE assets SET isDeleted = 1, deletedAt = :now WHERE category = :category AND isDeleted = 0")
    suspend fun softDeleteByCategory(category: String, now: Long = System.currentTimeMillis())
}

data class CategoryCount(
    val category: String,
    val count: Int,
    val totalValue: Double = 0.0
)

data class BrandCount(
    val brand: String,
    val count: Int,
    val totalValue: Double = 0.0
)
