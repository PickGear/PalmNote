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
 *
 * ## ⚠️ 目前**整条链路是休眠的**（2026-10-01 审计）
 *
 * 它依赖的 [DomainEvent.LifeItemCreated] / [DomainEvent.SavingDeposit] **没有发布方**
 * （全仓 `eventBus.publish` 只有账单与资产创建四处）。因此：
 * - `ITEM_CREATED` 规则（自动建关联）从未评估；
 * - `DEPOSIT_MADE` 规则（**存钱达标 → 标记完成 + 提醒 + 建关联**）从未评估 ——
 *   这条是本文件里**唯一有明显用户价值**的规则。
 *
 * 单测 `TriggerEngineTest` 直接调 `engine.evaluate(...)`，所以**测试绿不代表生产在跑**。
 * 复活前请先确认规则动作合理：`ITEM_CREATED` 那条建的关联 `targetId = item.id` 像自关联。
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
