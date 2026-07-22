package com.palmnote.domain.service

import com.palmnote.data.db.entity.LifeItem

class TriggerEventBus(
    private val engine: TriggerEngine
) {
    fun postCreated(item: LifeItem) { engine.evaluate(TriggerEvent.ITEM_CREATED, item) }
    fun postStatusChanged(item: LifeItem) { engine.evaluate(TriggerEvent.ITEM_STATUS_CHANGED, item) }
    fun postDepositMade(item: LifeItem) { engine.evaluate(TriggerEvent.DEPOSIT_MADE, item) }
}
