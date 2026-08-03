package com.palmnote.feature.life.usecase

import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import javax.inject.Inject

/**
 * 创建生活条目：插入 + 发布事件。
 */
class CreateLifeItemUseCase @Inject constructor(
    private val lifeItemDao: LifeItemDao,
    private val eventBus: EventBus
) {
    suspend operator fun invoke(item: LifeItem): Long {
        val id = lifeItemDao.insertItem(item)
        eventBus.publish(DomainEvent.LifeItemCreated(id))
        return id
    }
}
