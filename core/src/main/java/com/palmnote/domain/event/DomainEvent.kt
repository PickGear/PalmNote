package com.palmnote.domain.event

import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.BillType

/**
 * 领域事件：所有业务事件的统一定义。
 * 通过 EventBus 发布，由 EventConsumer 订阅处理。
 *
 * ## ⚠️ 大部分事件没有发布方（2026-10-01 全仓审计，含两处自我更正）
 *
 * 早先的注释写成"只有 4 个事件真的会发出"，那是**错的** —— 那 4 个发布点所在的 UseCase
 * **自己没有任何调用方**（`UpdateBillUseCase` 连引用都没有），界面层直接调仓库。
 * 后续又发现 `ITEM_CREATED` 那条规则**本身是空转**（见下）。两处都已更正。
 *
 * 未复活的分支因此全部**不会执行**：
 * - `WidgetRefreshConsumer`：账单 / 资产 / 密码本小组件的刷新分支从未执行
 *   （这些小组件另有直接刷新，或靠 30 分钟周期刷新兜底，用户看不到问题）；
 * - `TriggerEventConsumer`：`ITEM_CREATED` 空转（即使接线也无效果）。
 *
 * ## 小组件"没刷新"的实际影响有限
 *
 * 所有小组件都配了 `updatePeriodMillis = 30 分钟`，Android 会周期性刷新它们，
 * 所以应用内改动后的可见延迟**最多 30 分钟**，而不是"永远不更新"。
 * 生活模块额外做了**立即刷新**（直接调 `WidgetUpdateHelper`），所以那边没有延迟。
 *
 * ## 现状：只有 [SavingDeposit] 会真正发出（其余仍无发布方）
 *
 * [SavingDeposit] 现在由 `LifeItemRepositoryImpl.updateFieldsData` 在**进度上涨**时发布
 * （那是"改一个字段值"的唯一咽喉，详情页的 ± / 点数值就地编辑 / 快捷编辑都经过它），
 * 于是 `DEPOSIT_MADE` 规则（存钱达标 → 标记完成 + 提醒）**真的会跑**。
 *
 * 其余事件仍然没有发布方：
 * - 账单 / 资产的 4 个 UseCase 虽然会发布，但**它们自己没有任何调用方**（界面直接调仓库）；
 * - `ITEM_CREATED` / `HabitCheckedIn` / `WalletBalanceChanged` / `AssetStatusChanged` /
 *   `DataImported` 连发布点都没有。
 *
 * ## 复活前必须先看规则本身
 *
 * - `DEPOSIT_MADE`：**已复活**。规则里那条 `CreateAutoLink(ASSET, targetId = item.id)`
 *   （拿条目 id 当资产 id → 指向不存在资产的幽灵关联）**已删除**；引擎的自关联守卫
 *   只拦 `EntityType.ITEM`，拦不住它。
 * - `ITEM_CREATED`：**空转规则已删除**（2026-10-01）—— 它建的关联是 `ITEM/RELATED_TO`
 *   且目标就是自己，而 `TriggerEngine.executeAction` 对「ITEM 自关联」有显式守卫（跳过），
 *   所以即使接线也什么都不做。要接它，得先给它一个**真实的**关联目标。
 * - `ITEM_STATUS_CHANGED`：**不可达**（无发布方、消费者也无分支）→ 规则保留并标注，
 *   接它之前先想清楚"每次完成都弹通知"是否可取。
 */
sealed interface DomainEvent {
    // 账单事件
    data class BillCreated(val billId: Long, val type: BillType, val amount: Long) : DomainEvent
    data class BillUpdated(val billId: Long) : DomainEvent
    data class BillDeleted(val billId: Long) : DomainEvent
    data class BillRestored(val billId: Long) : DomainEvent

    // 物品事件
    data class AssetCreated(val assetId: Long) : DomainEvent

    /** ⚠️ **无发布方**（见文件头）：资产状态变更后，资产小组件的刷新分支不会执行。 */
    data class AssetStatusChanged(val assetId: Long, val old: AssetStatus, val new: AssetStatus) : DomainEvent

    // 生活事件

    /** ⚠️ **无发布方**：`TriggerEngine` 的 `ITEM_CREATED` 规则因此从未评估。 */
    data class LifeItemCreated(val itemId: Long) : DomainEvent

    /** ⚠️ **无发布方**：**「存钱达标 → 自动完成 + 提醒」这条规则从未触发**（功能价值最高的一条）。 */
    data class SavingDeposit(val itemId: Long, val amount: Long, val total: Long) : DomainEvent

    /** ⚠️ **无发布方**：消费者里本就是空分支（成就评估待做）。 */
    data class HabitCheckedIn(val itemId: Long, val streak: Int) : DomainEvent

    // 钱包事件

    /** ⚠️ **无发布方**：Dashboard 小组件的刷新分支不会执行。 */
    data class WalletBalanceChanged(val walletId: Long, val newBalance: Long) : DomainEvent

    // 数据事件

    /** ⚠️ **无发布方**：`refreshAllWidgets()` 分支不会执行（导入后另有直接刷新）。 */
    data class DataImported(val count: Int, val source: String) : DomainEvent
}
