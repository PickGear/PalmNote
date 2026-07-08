package com.palmnote.domain.service

import com.palmnote.data.db.entity.LifeItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TriggerEventBus @Inject constructor(
    private val engine: TriggerEngine
) {
    fun postCreated(item: LifeItem) { engine.evaluate(TriggerEvent.ITEM_CREATED, item) }
    fun postStatusChanged(item: LifeItem) { engine.evaluate(TriggerEvent.ITEM_STATUS_CHANGED, item) }
    fun postDepositMade(item: LifeItem) { engine.evaluate(TriggerEvent.DEPOSIT_MADE, item) }
}
