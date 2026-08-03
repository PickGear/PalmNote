package com.palmnote.domain.event

import kotlinx.coroutines.flow.Flow

/**
 * 领域事件总线：发布-订阅模式。
 * Repository/Service 发布事件，EventConsumer 订阅处理。
 */
interface EventBus {
    val events: Flow<DomainEvent>
    suspend fun publish(event: DomainEvent)
    suspend fun publishAll(events: List<DomainEvent>)
}
