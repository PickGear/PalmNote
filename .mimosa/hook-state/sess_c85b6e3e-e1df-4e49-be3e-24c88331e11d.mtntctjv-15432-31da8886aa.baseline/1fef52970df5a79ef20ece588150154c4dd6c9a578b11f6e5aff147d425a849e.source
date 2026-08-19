package com.palmnote.feature.asset.usecase

import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.entity.Asset
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import javax.inject.Inject

/**
 * 创建物品：插入 + 发布事件。
 */
class CreateAssetUseCase @Inject constructor(
    private val assetDao: AssetDao,
    private val eventBus: EventBus
) {
    suspend operator fun invoke(asset: Asset): Long {
        val id = assetDao.insertAsset(asset)
        eventBus.publish(DomainEvent.AssetCreated(id))
        return id
    }
}
