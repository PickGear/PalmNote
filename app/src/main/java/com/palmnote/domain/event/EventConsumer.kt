package com.palmnote.domain.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * 事件消费者接口。
 * 每个消费者订阅 EventBus 的事件流，处理自己关心的事件类型。
 */
interface EventConsumer {
    fun startConsuming(events: Flow<DomainEvent>, scope: CoroutineScope)
}
