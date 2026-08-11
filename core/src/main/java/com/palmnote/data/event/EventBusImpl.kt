package com.palmnote.data.event

import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EventBus 实现：基于 SharedFlow 的发布-订阅。
 * - replay = 0：新订阅者不接收历史事件
 * - extraBufferCapacity = 64：缓冲区，防止发布方挂起
 * - DROP_OLDEST：缓冲区满时丢弃最旧事件
 */
@Singleton
class EventBusImpl @Inject constructor() : EventBus {

    private val _events = MutableSharedFlow<DomainEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val events: Flow<DomainEvent> = _events.asSharedFlow()

    override suspend fun publish(event: DomainEvent) {
        _events.emit(event)
    }

    override suspend fun publishAll(events: List<DomainEvent>) {
        events.forEach { _events.emit(it) }
    }
}
