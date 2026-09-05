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
                }

                // 数据导入
                is DomainEvent.DataImported -> {
                    WidgetUpdateHelper.refreshAllWidgets()
                }

                // 钱包事件（影响 DashboardWidget）
                is DomainEvent.WalletBalanceChanged -> {
                    WidgetUpdateHelper.refreshDashboardWidgets()
                }

                else -> {}
            }
        }.launchIn(scope)
    }
}
