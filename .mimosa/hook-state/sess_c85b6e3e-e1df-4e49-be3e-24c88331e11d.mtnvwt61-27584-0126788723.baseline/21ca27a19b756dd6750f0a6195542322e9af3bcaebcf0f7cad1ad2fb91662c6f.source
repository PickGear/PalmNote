package com.palmnote.data.event

import com.palmnote.di.ApplicationScope
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.event.EventConsumer
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动所有事件消费者。
 * 使用 @Inject 构造函数，Hilt 自动创建并在 init 块中启动消费者。
 * 替代原来的 @Provides 方法（@Provides 不允许返回 void/Unit）。
 */
@Singleton
class EventStarter @Inject constructor(
    eventBus: EventBus,
    consumers: Set<@JvmSuppressWildcards EventConsumer>,
    @ApplicationScope scope: CoroutineScope
) {
    init {
        consumers.forEach { it.startConsuming(eventBus.events, scope) }
    }
}
