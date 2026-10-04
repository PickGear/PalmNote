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
 * ## ⚠️ 本类**所有分支都不会执行**（2026-10-01 审计）
 *
 * 事件的 4 个发布点都在 UseCase 里，而那些 UseCase **自己没有任何调用方**
 * （界面层直接调仓库）→ **没有任何事件会被发出**，这里的 `when` 恒为 `else`。
 *
 * ## 但"小组件没刷新"的影响有限
 *
 * 所有小组件都配了 `updatePeriodMillis = 30 分钟`，Android 会周期性刷新它们：
 * - **生活模块**：改动后**立即刷新**（VM / 小组件接收器直接调 `WidgetUpdateHelper`）✓ 无延迟；
 * - **账单 / 资产 / 密码本**：只能等周期刷新 → 应用内改动后**最多 30 分钟**才反映到桌面。
 *   这是小瑕疵（不是"永远不更新"），所以本次**没有为它改 4 个模块的调用点**。
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
