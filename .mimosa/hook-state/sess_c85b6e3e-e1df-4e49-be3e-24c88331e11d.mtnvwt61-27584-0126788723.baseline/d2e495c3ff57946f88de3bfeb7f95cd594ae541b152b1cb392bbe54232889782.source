package com.palmnote.data.event

import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventConsumer
import com.palmnote.domain.service.TriggerEngine
import com.palmnote.domain.service.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 触发器事件消费者：订阅领域事件，驱动 TriggerEngine 评估规则。
 * 替代原有的 TriggerEventBus 硬编码调用。
 */
@Singleton
class TriggerEventConsumer @Inject constructor(
    private val triggerEngine: TriggerEngine
) : EventConsumer {

    override fun startConsuming(events: Flow<DomainEvent>, scope: CoroutineScope) {
        events.onEach { event ->
            when (event) {
                is DomainEvent.LifeItemCreated -> {
                    triggerEngine.evaluate(TriggerEvent.ITEM_CREATED, event.itemId)
                }
                is DomainEvent.SavingDeposit -> {
                    triggerEngine.evaluate(TriggerEvent.DEPOSIT_MADE, event.itemId)
                }
                is DomainEvent.HabitCheckedIn -> {
                    // 习惯打卡可触发成就评估
                }
                else -> { /* 不处理其他事件 */ }
            }
        }.launchIn(scope)
    }
}
