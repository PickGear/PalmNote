package com.palmnote.data.event

import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventConsumer
import com.palmnote.ui.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget 刷新事件消费者：订阅领域事件，主动触发 Widget 更新。
 * 覆盖所有通过 EventBus 发布的数据变更事件。
 *
 * ## 现状（2026-10-26 复核）
 *
 * - **已接**：资产状态变更（AssetRepositoryImpl）、钱包余额（WalletRepositoryImpl）——
 *   这两个分支现在真的会执行；
 * - **未接**：新增 / 编辑资产、账单增删改 —— `CreateAssetUseCase` / `CreateBillUseCase`
 *   仍无调用方（界面层直接调仓库），这些改动最坏等 `updatePeriodMillis` 的 30 分钟周期才上桌面。
 *   生活模块则一直由 VM / 接收器直接调 `WidgetUpdateHelper`，无延迟。
 *
 * 所以「账单/资产改动要等半小时」仍是已知小瑕疵；要彻底解决得把发布点接到界面层保存成功处
 * （或在 VM 里直接调 `WidgetUpdateHelper`），与本次的小组件渲染修复分开做。
 */
@Singleton
class WidgetRefreshConsumer @Inject constructor() : EventConsumer {

    override fun startConsuming(events: Flow<DomainEvent>, scope: CoroutineScope) {
        events.onEach { event ->
            when (event) {
                // 账单事件
                is DomainEvent.BillCreated,
                is DomainEvent.BillUpdated,
                is DomainEvent.BillDeleted,
                is DomainEvent.BillRestored -> {
                    WidgetUpdateHelper.refreshBillWidgets()
                    // 记账回滚余额走的是 walletDao.adjustBalance（绕过 WalletRepository，
                    // 发不出 WalletBalanceChanged），净值合计含钱包余额，这里补上
                    WidgetUpdateHelper.refreshNetWorthWidgets()
                }

                // 资产事件
                is DomainEvent.AssetCreated,
                is DomainEvent.AssetStatusChanged -> {
                    WidgetUpdateHelper.refreshAssetWidgets()
                }

                // 生活事件
                is DomainEvent.LifeItemCreated,
                is DomainEvent.SavingDeposit,
                is DomainEvent.HabitCheckedIn -> {
                    WidgetUpdateHelper.refreshTodoWidgets()
                    WidgetUpdateHelper.refreshCounterWidgets()
                    // 焦点卡吃待办/订阅/倒数三路数据，生活条目一变就跟着切
                    WidgetUpdateHelper.refreshNextUpWidgets()
                }

                // 数据导入
                is DomainEvent.DataImported -> {
                    WidgetUpdateHelper.refreshAllWidgets()
                }

                // 钱包事件（余额进概览 hero，也进净资产组件的合计）
                is DomainEvent.WalletBalanceChanged -> {
                    WidgetUpdateHelper.refreshDashboardWidgets()
                    WidgetUpdateHelper.refreshNetWorthWidgets()
                }

                else -> {}
            }
        }.launchIn(scope)
    }
}
