package com.palmnote.data.event

import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.event.EventConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 事件链的**接线守门**（2026-10-01 审查发现的真实故障）。
 *
 * 故障形态：`EventStarter` 在 `init` 里订阅所有消费者，但**没有任何代码请求它** ——
 * 而 Hilt 只在有人注入时才构造 `@Singleton`，所以它从未被创建、`init` 从未执行
 * → `TriggerEventConsumer` / `WidgetRefreshConsumer` **从未启动**。
 * 表现是"发布方在、订阅方也在，中间是断的"：连"存钱达标 → 完成 + 提醒"都是空转。
 *
 * 两条断言分别守住两半：
 * 1. 构造 [EventStarter] 确实会启动全部消费者（守 `init`）；
 * 2. `PalmNoteApp` 确实注入了它（守"有人请求"这一半 —— 只修 `init` 是不够的）。
 */
class EventStarterWiringTest {

    private class RecordingConsumer : EventConsumer {
        var started = 0
        override fun startConsuming(events: Flow<DomainEvent>, scope: CoroutineScope) {
            started++
        }
    }

    private val bus = object : EventBus {
        override val events: Flow<DomainEvent> = emptyFlow()
        override suspend fun publish(event: DomainEvent) = Unit
        override suspend fun publishAll(events: List<DomainEvent>) = Unit
    }

    @Test
    fun `构造时启动全部消费者`() {
        val first = RecordingConsumer()
        val second = RecordingConsumer()
        EventStarter(bus, setOf(first, second), CoroutineScope(UnconfinedTestDispatcher()))
        assertEquals(1, first.started)
        assertEquals(1, second.started)
    }

    @Test
    fun `App 必须请求 EventStarter，否则整条事件链静默失效`() {
        val injected = com.palmnote.PalmNoteApp::class.java.declaredFields.map { it.type }
        assertTrue(
            "PalmNoteApp 必须注入 EventStarter（只为触发构造）：Hilt 不会凭空创建 @Singleton",
            injected.contains(EventStarter::class.java)
        )
    }
}
