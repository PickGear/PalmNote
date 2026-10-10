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

    /**
     * 有到期日（质保或保质期）且仍在持有的物品。
     *
     * 两种到期日都取：界面和提醒都按「更紧迫的那个」说话（`Asset.nearestExpiry`），
     * 只查保质期会让质保更紧迫的物品在界面上橙着却永远收不到提醒。
     * 「还差几天」交给上层按**自然日**判，不在这里用时间戳除法——那样算出来的天数跟界面上
     * （同样按自然日）会差一天，同一个物品在列表里显示的和提醒说的就对不上了。
     */
    @Query("SELECT * FROM assets WHERE (warrantyExpireDate IS NOT NULL OR shelfLifeExpireDate IS NOT NULL) AND status = 'HELD'")
    fun getHeldAssetsWithExpiry(): Flow<List<Asset>>

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

    /** 在用物品按分类的件数分布（物品组件用）。只数 HELD，与组件头部「共 N 件」同口径。 */
    @Query("SELECT category, COUNT(*) as count FROM assets WHERE status = 'HELD' GROUP BY category ORDER BY count DESC, category ASC")
    fun getHeldCategoryCounts(): Flow<List<HeldCategoryCount>>

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


    @Query("SELECT * FROM assets WHERE category = :category")
    suspend fun getByCategoryOnce(category: String): List<Asset>

    @Query("DELETE FROM assets WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("DELETE FROM assets")
    suspend fun deleteAll()

    @Query("UPDATE assets SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM assets WHERE category = :category")
    suspend fun countByCategory(category: String): Int


    /** 演示数据清理（v14 isDemo 列）：演示模式关闭时物理删除示例行。 */
    @Query("DELETE FROM assets WHERE isDemo = 1")
    suspend fun clearDemoAssets()

    /** 当前示例行数（关演示反馈 / 幂等判断用）。 */
    @Query("SELECT COUNT(*) FROM assets WHERE isDemo = 1")
    suspend fun countDemoAssets(): Int
}

data class CategoryCount(
    val category: String,
    val count: Int,
    val totalValue: Long = 0
)

/** 只要分类与件数的投影（物品组件占比条与色片网格用）。 */
data class HeldCategoryCount(
    val category: String,
    val count: Int
)

data class BrandCount(
    val brand: String,
    val count: Int,
    val totalValue: Long = 0
)
