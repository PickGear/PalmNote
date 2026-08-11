package com.palmnote.domain.event

import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.BillType

/**
 * 领域事件：所有业务事件的统一定义。
 * 通过 EventBus 发布，由 EventConsumer 订阅处理。
 */
sealed interface DomainEvent {
    // 账单事件
    data class BillCreated(val billId: Long, val type: BillType, val amount: Long) : DomainEvent
    data class BillUpdated(val billId: Long) : DomainEvent
    data class BillDeleted(val billId: Long) : DomainEvent
    data class BillRestored(val billId: Long) : DomainEvent

    // 物品事件
    data class AssetCreated(val assetId: Long) : DomainEvent
    data class AssetStatusChanged(val assetId: Long, val old: AssetStatus, val new: AssetStatus) : DomainEvent

    // 生活事件
    data class LifeItemCreated(val itemId: Long) : DomainEvent
    data class SavingDeposit(val itemId: Long, val amount: Long, val total: Long) : DomainEvent
    data class HabitCheckedIn(val itemId: Long, val streak: Int) : DomainEvent

    // 钱包事件
    data class WalletBalanceChanged(val walletId: Long, val newBalance: Long) : DomainEvent

    // 数据事件
    data class DataImported(val count: Int, val source: String) : DomainEvent
}
