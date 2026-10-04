package com.palmnote.data.event

import com.palmnote.di.ApplicationScope
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.event.EventConsumer
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动所有事件消费者（在 `init` 块里订阅）。
 *
 * ## ⚠️ 它**必须被请求**才会生效 —— 而它曾经谁也没请求
 *
 * 旧注释写着「使用 @Inject 构造函数，Hilt 自动创建并在 init 块中启动消费者」——
 * **这是错的**：Hilt 只在**有人请求**（注入）时才创建实例，一个从没被注入过的
 * `@Singleton` 永远不会被构造，`init` 也就永远不会跑。
 *
 * 后果（2026-10-01 审查发现）：`TriggerEventConsumer` / `WidgetRefreshConsumer`
 * **从未启动** —— 于是"存钱达标 → 完成 + 提醒"即使有了事件源也照样空转，
 * 是一条**发布方与订阅方都在、中间却是断的**的链路。
 *
 * 现在由 `PalmNoteApp` 显式注入（只为触发构造）。**若将来它被移出 App，
 * 请确保仍有地方请求它**，否则整条事件链会静默失效。
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
