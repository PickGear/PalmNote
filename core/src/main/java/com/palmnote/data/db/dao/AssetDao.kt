package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.domain.model.AssetStatus
import com.palmnote.data.db.entity.Asset
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY isFavorite DESC, sortOrder ASC, updatedAt DESC")
    fun getAllAssets(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: Long): Asset?

    @Query("SELECT * FROM assets WHERE id = :id")
    fun getAssetByIdFlow(id: Long): Flow<Asset?>

    @Query("SELECT * FROM assets WHERE category = :category ORDER BY isFavorite DESC, sortOrder ASC")
    fun getAssetsByCategory(category: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE status = :status ORDER BY updatedAt DESC")
    fun getAssetsByStatus(status: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE acquisitionType = :type ORDER BY updatedAt DESC")
    fun getAssetsByAcquisitionType(type: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR model LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')")
    fun searchAssets(query: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteAssets(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE nextMaintenanceDate IS NOT NULL AND nextMaintenanceDate <= :now AND status = 'HELD'")
    fun getAssetsNeedingMaintenance(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE warrantyExpireDate IS NOT NULL AND warrantyExpireDate > :now AND ((warrantyExpireDate - :now) / 86400000) <= 30 AND status = 'HELD'")
    fun getAssetsNeedingWarrantyAlert(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE insuranceExpireDate IS NOT NULL AND insuranceExpireDate > :now AND ((insuranceExpireDate - :now) / 86400000) <= 30 AND status = 'HELD'")
    fun getAssetsNeedingInsuranceAlert(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE room = :room ORDER BY name ASC")
    fun getAssetsByRoom(room: String): Flow<List<Asset>>

    @Query("SELECT DISTINCT room FROM assets WHERE room != '' ORDER BY room ASC")
    fun getAllRooms(): Flow<List<String>>

    @Query("SELECT DISTINCT brand FROM assets WHERE brand != '' ORDER BY brand ASC")
    fun getAllBrands(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM assets WHERE 1=1")
    fun getTotalAssetCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM assets WHERE status = :status")
    fun getAssetCountByStatus(status: AssetStatus): Flow<Int>

    @Query("SELECT SUM(purchasePrice) FROM assets WHERE status != 'REMOVED'")
    fun getTotalAssetValue(): Flow<Long?>

    @Query("SELECT SUM(purchasePrice) FROM assets WHERE status = 'HELD'")
    fun getHeldAssetValue(): Flow<Long?>

    @Query("SELECT SUM(soldPrice) FROM assets WHERE status = 'REMOVED' AND soldPrice IS NOT NULL")
    fun getTotalSoldValue(): Flow<Long?>

    @Query("SELECT SUM(currentValue) FROM assets WHERE status = 'HELD' AND currentValue > 0")
    fun getTotalCurrentValue(): Flow<Long?>

    @Query("SELECT category, COUNT(*) as count, SUM(purchasePrice) as totalValue FROM assets GROUP BY category ORDER BY count DESC")
    fun getCategoryDistribution(): Flow<List<CategoryCount>>

    @Query("SELECT brand, COUNT(*) as count, SUM(purchasePrice) as totalValue FROM assets WHERE brand != '' GROUP BY brand ORDER BY count DESC LIMIT 10")
    fun getBrandDistribution(): Flow<List<BrandCount>>


    @Query("SELECT * FROM assets WHERE warrantyExpireDate IS NOT NULL AND warrantyExpireDate > :now ORDER BY warrantyExpireDate ASC")
    fun getAssetsWithValidWarranty(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE warrantyExpireDate IS NOT NULL AND warrantyExpireDate <= :now")
    fun getAssetsWithExpiredWarranty(now: Long = System.currentTimeMillis()): Flow<List<Asset>>

    @Query("""
        SELECT * FROM assets
       
        ORDER BY
            CASE WHEN status = 'HELD' THEN 0
                 WHEN status = 'AWAY' THEN 1
                 WHEN status = 'REMOVED' THEN 2
                 ELSE 3 END,
            isFavorite DESC,
            updatedAt DESC
    """)
    fun getAllAssetsSortedByStatus(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE condition = :condition")
    fun getAssetsByCondition(condition: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR model LIKE '%' || :query || '%' OR serialNumber LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<Asset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: Asset): Long

    @Update
    suspend fun updateAsset(asset: Asset)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAsset(id: Long)

    @Query("UPDATE assets SET useCount = useCount + 1, updatedAt = :now WHERE id = :assetId")
    suspend fun incrementUseCount(assetId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET totalUsageHours = totalUsageHours + :hours, updatedAt = :now WHERE id = :assetId")
    suspend fun addUsageHours(assetId: Long, hours: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET isFavorite = :isFavorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET currentValue = :value, updatedAt = :now WHERE id = :id")
    suspend fun updateCurrentValue(id: Long, value: Long, now: Long = System.currentTimeMillis())

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
    suspend fun sellAsset(id: Long, soldDate: Long, soldPrice: Long, soldChannel: String, soldToWhom: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET status = 'HELD', retireDate = null, retireReason = '', lostDate = null, lostReason = '', soldDate = null, soldPrice = null, soldChannel = null, soldToWhom = null, updatedAt = :now WHERE id = :id")
    suspend fun reactivateAsset(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE assets SET linkedBillId = :billId, updatedAt = :now WHERE id = :id")
    suspend fun linkBill(id: Long, billId: Long, now: Long = System.currentTimeMillis())


    @Query("DELETE FROM assets WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("DELETE FROM assets")
    suspend fun deleteAll()

    @Query("UPDATE assets SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM assets WHERE category = :category")
    suspend fun countByCategory(category: String): Int

}

data class CategoryCount(
    val category: String,
    val count: Int,
    val totalValue: Long = 0
)

data class BrandCount(
    val brand: String,
    val count: Int,
    val totalValue: Long = 0
)
