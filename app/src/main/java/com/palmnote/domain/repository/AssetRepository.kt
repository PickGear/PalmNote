package com.palmnote.domain.repository

import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.data.db.entity.Asset
import kotlinx.coroutines.flow.Flow

interface AssetRepository {
    fun getAllAssets(): Flow<List<Asset>>
    suspend fun getAssetById(id: Long): Asset?
    fun getAssetByIdFlow(id: Long): Flow<Asset?>
    fun getAssetsByCategory(category: String): Flow<List<Asset>>
    fun getAssetsByStatus(status: String): Flow<List<Asset>>
    fun getAssetsByAcquisitionType(type: String): Flow<List<Asset>>
    fun searchAssets(query: String): Flow<List<Asset>>
    fun getTotalAssetCount(): Flow<Int>
    fun getAssetCountByStatus(status: String): Flow<Int>
    fun getTotalAssetValue(): Flow<Double?>
    fun getHeldAssetValue(): Flow<Double?>
    fun getTotalSoldValue(): Flow<Double?>
    fun getCategoryDistribution(): Flow<List<CategoryCount>>
    fun getDeletedAssets(): Flow<List<Asset>>
    fun getAssetsWithValidWarranty(): Flow<List<Asset>>
    fun getAssetsWithExpiredWarranty(): Flow<List<Asset>>
    fun getAllAssetsSortedByStatus(): Flow<List<Asset>>
    suspend fun insertAsset(asset: Asset): Long
    suspend fun updateAsset(asset: Asset)
    suspend fun softDeleteAsset(id: Long)
    suspend fun restoreAsset(id: Long)
    suspend fun hardDeleteAsset(id: Long)
    suspend fun incrementUseCount(assetId: Long)
    suspend fun awayAsset(id: Long, tags: String, reason: String)
    suspend fun retireAsset(id: Long, reason: String)
    suspend fun markAssetLost(id: Long, reason: String)
    suspend fun sellAsset(id: Long, soldPrice: Double, soldChannel: String, soldToWhom: String)
    suspend fun reactivateAsset(id: Long)
    suspend fun completeMaintenance(id: Long, date: Long, nextDate: Long?, notes: String)
    suspend fun linkBill(id: Long, billId: Long)
    suspend fun search(query: String): List<Asset>
    suspend fun updateCategoryNameInAssets(oldName: String, newName: String)
    suspend fun countByCategory(category: String): Int
    suspend fun softDeleteByCategory(category: String)
}
