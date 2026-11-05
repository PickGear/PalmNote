package com.palmnote.data.repository
import com.palmnote.ui.components.imagePaths
import com.palmnote.ui.components.toImageList
import java.io.File
import javax.inject.Inject
import com.palmnote.domain.model.AssetStatus

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.AssetRecycleBinDao
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.toRecycleBin
import com.palmnote.data.db.entity.toAsset
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.repository.AssetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
/**
 * 演示模式下**只看示例行、关闭演示只看真实行**（「全 App 口径唯一」）。
 *
 * 物品没有账本维度，只能在取数处按 `isDemo` 分流：列表在内存过滤，
 * 聚合值把开关透传给 DAO 的同一条 SQL（`includeDemo`），保证列表与汇总永远同口径。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssetRepositoryImpl @Inject constructor(
    private val assetDao: AssetDao,
    private val assetRecycleBinDao: AssetRecycleBinDao,
    private val appDatabase: AppDatabase,
    private val eventBus: com.palmnote.domain.event.EventBus,
    private val preferencesManager: com.palmnote.data.datastore.PreferencesManager
) : AssetRepository {

    /** 列出口径：示例模式只留 `isDemo`，否则只留真实行。 */
    private fun scopedList(source: kotlinx.coroutines.flow.Flow<List<Asset>>) =
        preferencesManager.lifeDemoMode.flatMapLatest { demo ->
            source.map { list -> list.filter { it.isDemo == demo } }
        }

    /** 标量口径：把开关透传给 DAO，SQL 里用 `includeDemo` 选行。 */
    private fun <T> scoped(block: (Boolean) -> kotlinx.coroutines.flow.Flow<T>) =
        preferencesManager.lifeDemoMode.flatMapLatest { demo -> block(demo) }
    override fun getAllAssets() = scopedList(assetDao.getAllAssets())

    override suspend fun getAssetById(id: Long): Asset? = assetDao.getAssetById(id)

    override fun getAssetByIdFlow(id: Long) = assetDao.getAssetByIdFlow(id)

    override fun getAssetsByCategory(category: String) = scopedList(assetDao.getAssetsByCategory(category))

    override fun getAssetsByStatus(status: String) = scopedList(assetDao.getAssetsByStatus(status))

    override fun getAssetsByAcquisitionType(type: String) =
        scopedList(assetDao.getAssetsByAcquisitionType(type))

    override fun searchAssets(query: String) = scopedList(assetDao.searchAssets(query))

    override fun getTotalAssetCount() = scoped { assetDao.getTotalAssetCount(it) }

    override fun getAssetCountByStatus(status: AssetStatus) =
        scoped { assetDao.getAssetCountByStatus(status, it) }

    override fun getTotalAssetValue() = scoped { assetDao.getTotalAssetValue(it) }

    override fun getHeldAssetValue() = scoped { assetDao.getHeldAssetValue(it) }

    override fun getTotalSoldValue() = scoped { assetDao.getTotalSoldValue(it) }

    override fun getCategoryDistribution() = scoped { assetDao.getCategoryDistribution(it) }


    override fun getAssetsWithValidWarranty() = scopedList(assetDao.getAssetsWithValidWarranty())

    override fun getAssetsWithExpiredWarranty() =
        scopedList(assetDao.getAssetsWithExpiredWarranty())

    override fun getHeldAssetsWithExpiry() = scopedList(assetDao.getHeldAssetsWithExpiry())

    override fun getAllAssetsSortedByStatus() =
        scopedList(assetDao.getAllAssetsSortedByStatus())

    /** 演示模式下新建的物品同样标 `USER`（可见可改，关闭时迁回真实数据）。 */
    override suspend fun insertAsset(asset: Asset): Long {
        val demo = preferencesManager.lifeDemoMode.first()
        return assetDao.insertAsset(
            if (demo) asset.copy(isDemo = true, demoOrigin = com.palmnote.domain.model.DemoOrigin.USER) else asset
        )
    }

    /**
     * 编辑口径与插入一致（全 App 口径唯一）：演示模式下编辑示例行必须**原地保持示例身份**。
     * 表单重建的实体不带 isDemo/demoOrigin（默认真实行）——原样写库会让该行静默「毕业」：
     * 演示列表瞬间丢行、真实数据凭空多行（2026-11-02 用户实测「演示模式加的物品时隐时现」）。
     * 演示关闭时库里不存在示例行（关闭即整批清理），原样写即可。
     */
    override suspend fun updateAsset(asset: Asset) {
        val aligned = if (preferencesManager.lifeDemoMode.first()) {
            val existing = assetDao.getAssetById(asset.id)
            if (existing?.isDemo == true) {
                asset.copy(isDemo = true, demoOrigin = existing.demoOrigin)
            } else asset
        } else asset
        assetDao.updateAsset(aligned)
    }

    override suspend fun deleteAsset(id: Long) = appDatabase.withTransaction {
        val asset = assetDao.getAssetById(id) ?: return@withTransaction
        assetRecycleBinDao.insert(asset.toRecycleBin())
        assetDao.deleteAsset(id)
    }

    override suspend fun restoreAsset(id: Long) = appDatabase.withTransaction {
        val item = assetRecycleBinDao.getById(id) ?: return@withTransaction
        assetDao.insertAsset(item.toAsset())
        assetRecycleBinDao.deleteById(id)
    }

    override suspend fun hardDeleteAsset(id: Long) = appDatabase.withTransaction {
        val item = assetRecycleBinDao.getById(id) ?: return@withTransaction
        // 与 BillRepositoryImpl.hardDeleteBill 同一口径：彻底删掉条目时，它独占的图片
        // 也没人再引用了（图片文件名带 uuid，条目之间不共享），不清就是永久孤儿。
        item.images.toImageList().imagePaths().forEach { path ->
            runCatching { File(path).delete() }
        }
        assetRecycleBinDao.deleteById(id)
    }


    override suspend fun setUseCount(assetId: Long, count: Int) = assetDao.setUseCount(assetId, count)

    override suspend fun awayAsset(id: Long, tags: String, reason: String) =
        changeAssetStatus(id) { assetDao.awayAsset(id, System.currentTimeMillis(), tags, reason) }

    override suspend fun retireAsset(id: Long, reason: String) =
        changeAssetStatus(id) { assetDao.retireAsset(id, System.currentTimeMillis(), reason) }

    override suspend fun markAssetLost(id: Long, reason: String) =
        changeAssetStatus(id) { assetDao.markAssetLost(id, System.currentTimeMillis(), reason) }

    override suspend fun sellAsset(id: Long, soldPrice: Long, soldChannel: String, soldToWhom: String) =
        changeAssetStatus(id) {
            assetDao.sellAsset(id, System.currentTimeMillis(), soldPrice, soldChannel, soldToWhom)
        }

    override suspend fun reactivateAsset(id: Long) =
        changeAssetStatus(id) { assetDao.reactivateAsset(id) }

    /**
     * 状态变更统一包装：变更前后各读一次状态，成功后发布 [DomainEvent.AssetStatusChanged]
     * （WidgetRefreshConsumer 据此刷物品组件；发布失败不影响主流程，桌面靠 30 分钟周期兜底）。
     * 状态读不到（条目已被删）就只执行动作不发事件 —— 别拿编造的状态撒谎。
     */
    private suspend fun changeAssetStatus(id: Long, mutation: suspend () -> Unit) {
        val old = assetDao.getAssetById(id)?.status
        mutation()
        val new = assetDao.getAssetById(id)?.status
        if (old == null || new == null || old == new) return
        runCatching { eventBus.publish(DomainEvent.AssetStatusChanged(id, old, new)) }
    }

    override suspend fun completeMaintenance(id: Long, date: Long, nextDate: Long?, notes: String) =
        assetDao.completeMaintenance(id, date, nextDate, notes)

    override suspend fun linkBill(id: Long, billId: Long) = assetDao.linkBill(id, billId)

    override suspend fun search(query: String): List<Asset> = assetDao.search(query)

    override suspend fun updateCategoryNameInAssets(oldName: String, newName: String) =
        assetDao.updateCategoryName(oldName, newName)

    override suspend fun countByCategory(category: String): Int =
        assetDao.countByCategory(category)

    /**
     * 删除某分类下全部物品：与单条 [deleteAsset] 保持一致——进回收站、事务内先查后删。
     * 此前这里直调 `assetDao.deleteByCategory` 硬删，分类管理里删分类时物品全没、无法恢复
     * （2026-10-11 回收站覆盖度审计发现；账单侧的 deleteByCategory 早就有包装，物品侧漏了）。
     *
     * 演示口径：分类名在演示与真实之间共享，演示期删分类只删演示物品，不波及真实物品。
     */
    override suspend fun deleteByCategory(category: String) {
        val demo = preferencesManager.lifeDemoMode.first()
        appDatabase.withTransaction {
            // 查询必须在事务内：先查后删的两段式会让"查询之后、删除之前"新写入的
            // 目标物品被硬删（无回收站行）——与账单批量删除 deleteBillsToRecycleBin 同一保证
            val assets = assetDao.getByCategoryOnce(category, demo)
            assets.forEach { assetRecycleBinDao.insert(it.toRecycleBin()) }
            assetDao.deleteByCategory(category, demo)
        }
    }
}
