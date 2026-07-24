package com.palmnote.data.repository

import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.entity.Asset
import com.palmnote.domain.repository.AssetRepository
class AssetRepository(
    private val assetDao: AssetDao
) : AssetRepository {
    override fun getAllAssets() = assetDao.getAllAssets()

    override suspend fun getAssetById(id: Long): Asset? = assetDao.getAssetById(id)

    override fun getAssetByIdFlow(id: Long) = assetDao.getAssetByIdFlow(id)

    override fun getAssetsByCategory(category: String) = assetDao.getAssetsByCategory(category)

    override fun getAssetsByStatus(status: String) = assetDao.getAssetsByStatus(status)

    override fun getAssetsByAcquisitionType(type: String) = assetDao.getAssetsByAcquisitionType(type)

    override fun searchAssets(query: String) = assetDao.searchAssets(query)

    override fun getTotalAssetCount() = assetDao.getTotalAssetCount()

    override fun getAssetCountByStatus(status: String) = assetDao.getAssetCountByStatus(status)

    override fun getTotalAssetValue() = assetDao.getTotalAssetValue()

    override fun getHeldAssetValue() = assetDao.getHeldAssetValue()

    override fun getTotalSoldValue() = assetDao.getTotalSoldValue()

    override fun getCategoryDistribution() = assetDao.getCategoryDistribution()

    override fun getDeletedAssets() = assetDao.getDeletedAssets()

    override fun getAssetsWithValidWarranty() = assetDao.getAssetsWithValidWarranty()

    override fun getAssetsWithExpiredWarranty() = assetDao.getAssetsWithExpiredWarranty()

    override fun getAllAssetsSortedByStatus() = assetDao.getAllAssetsSortedByStatus()

    override suspend fun insertAsset(asset: Asset): Long = assetDao.insertAsset(asset)

    override suspend fun updateAsset(asset: Asset) = assetDao.updateAsset(asset)

    override suspend fun softDeleteAsset(id: Long) = assetDao.softDeleteAsset(id)

    override suspend fun restoreAsset(id: Long) = assetDao.restoreAsset(id)

    override suspend fun hardDeleteAsset(id: Long) = assetDao.hardDeleteAsset(id)

    override suspend fun incrementUseCount(assetId: Long) = assetDao.incrementUseCount(assetId)

    override suspend fun awayAsset(id: Long, tags: String, reason: String) =
        assetDao.awayAsset(id, System.currentTimeMillis(), tags, reason)

    override suspend fun retireAsset(id: Long, reason: String) =
        assetDao.retireAsset(id, System.currentTimeMillis(), reason)

    override suspend fun markAssetLost(id: Long, reason: String) =
        assetDao.markAssetLost(id, System.currentTimeMillis(), reason)

    override suspend fun sellAsset(id: Long, soldPrice: Double, soldChannel: String, soldToWhom: String) =
        assetDao.sellAsset(id, System.currentTimeMillis(), soldPrice, soldChannel, soldToWhom)

    override suspend fun reactivateAsset(id: Long) = assetDao.reactivateAsset(id)

    override suspend fun completeMaintenance(id: Long, date: Long, nextDate: Long?, notes: String) =
        assetDao.completeMaintenance(id, date, nextDate, notes)

    override suspend fun linkBill(id: Long, billId: Long) = assetDao.linkBill(id, billId)

    override suspend fun search(query: String): List<Asset> = assetDao.search(query)

    override suspend fun updateCategoryNameInAssets(oldName: String, newName: String) =
        assetDao.updateCategoryName(oldName, newName)

    override suspend fun countByCategory(category: String): Int =
        assetDao.countByCategory(category)

    override suspend fun softDeleteByCategory(category: String) =
        assetDao.softDeleteByCategory(category)
}
