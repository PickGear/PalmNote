package com.palmnote.data.notify

import android.content.Context
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BudgetRepository
import com.palmnote.ui.bills.budgetLabel
import com.palmnote.ui.notification.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 预算通知：把「达到阈值 / 已超支」的预算推到通知栏。
 *
 * 去重口径是**每条预算每一期每一级只提醒一次**：
 * 键 = `预算id:本期开始时间`，值 = 已提醒到的严重度（1 达阈值 / 2 超支）。
 * 因此同一期内从「达阈值」升级到「超支」会再提醒一次，而重复保存账单不会反复弹。
 * 进入新一期（周/月/年窗口变化）时键自然不同，会重新提醒。
 *
 * 只发通知、不改数据；开关是设置里的「预算通知」，与首页预警卡（预算提醒）分开。
 */
@Singleton
class BudgetAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
    private val accountBookRepository: AccountBookRepository,
    private val preferencesManager: PreferencesManager
) {
    private val mutex = Mutex()

    suspend fun checkAndNotify() {
        // 每日 Worker 与「保存账单后即时检查」可能并发进入；去重账本是读-改-写，
        // 不加锁会各自读到同一份旧 map、都判定「没提醒过」，重复弹通知。
        mutex.withLock { checkAndNotifyLocked() }
    }

    private suspend fun checkAndNotifyLocked() {
        // 示例预算是演示内容，不该用系统通知打断用户：新装默认就在演示模式，
        // 演示种子还特意做成超支，不拦会必现一条假的「本月总预算已超支」。
        // 演示模式下的进度仍由首页预警卡呈现。
        if (preferencesManager.lifeDemoMode.first()) return
        if (!preferencesManager.budgetNotifyEnabled.first()) return
        val progresses = budgetRepository.getProgresses().first()
        val targets = progresses.filter { it.budget.reminderEnabled && it.severity > 0 }
        val notified = preferencesManager.budgetAlertNotified().toMutableMap()
        var changed = false

        if (targets.isNotEmpty()) {
            val bookNames = accountBookRepository.getAllBooks().first()
                .associate { it.id to it.name }
            targets.forEach { progress ->
                val key = keyOf(progress)
                if (progress.severity > (notified[key] ?: 0)) {
                    showNotification(progress, bookNames)
                    notified[key] = progress.severity
                    changed = true
                }
            }
        }

        // 旧周期要淘汰，但不能只保留「当前达标」的键：退款/删账单让严重度回落到 0
        // 时键被清掉，再花回去会重复提醒。「每期每级只提醒一次」要求回落也留痕。
        // 键里含 windowStart，换期自然淘汰，记录数上限 = 预算条数。
        val currentKeys = progresses.map(::keyOf).toSet()
        if (notified.keys.retainAll(currentKeys)) changed = true

        if (changed) preferencesManager.setBudgetAlertNotified(notified)
    }

    private fun keyOf(progress: BudgetProgress): String =
        "${progress.budget.id}:${progress.windowStart}"

    private fun showNotification(progress: BudgetProgress, bookNames: Map<Long, String>) {
        val label = budgetLabel(context, progress, bookNames)
        val message = if (progress.isOver) {
            context.getString(
                R.string.notification_budget_over,
                label,
                (-progress.remaining) / 100.0
            )
        } else {
            context.getString(
                R.string.notification_budget_threshold,
                label,
                progress.usedPercent,
                progress.spent / 100.0,
                progress.limit / 100.0
            )
        }
        NotificationHelper.show(
            context = context,
            channel = NotificationHelper.Channel.BUDGET,
            title = context.getString(R.string.notification_budget_title),
            message = message,
            id = notificationIdOf(progress.budget.id)
        )
    }

    /** 每条预算一个固定 id：同一预算同一期就地更新，不堆一摞。取模放大避免互相覆盖。 */
    private fun notificationIdOf(budgetId: Long): Int =
        NOTIFICATION_ID_BASE + (budgetId % 100_000).toInt()

    /** 删除预算后撤掉还挂在通知栏的那条预警，别留一条指向已不存在规则的告警。 */
    fun cancelForBudget(budgetId: Long) {
        try {
            androidx.core.app.NotificationManagerCompat.from(context)
                .cancel(notificationIdOf(budgetId))
        } catch (_: SecurityException) {
            // 未授予通知权限时取消也可能被拒；此时通知本就不存在，忽略即可
        }
    }

    private companion object {
        /** 与生活模块的通知 id 空间错开（那边从 1000 起自增）。 */
        const val NOTIFICATION_ID_BASE = 2000
    }
}
