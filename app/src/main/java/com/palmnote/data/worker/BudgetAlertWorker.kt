package com.palmnote.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.palmnote.data.notify.BudgetAlertNotifier
import com.palmnote.domain.util.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * 每日预算检查：把「达阈值 / 超支」的预算推到通知栏。
 *
 * 与「记账提醒」（[LifeDailyCheckWorker]）**分开一条 worker**：那个受记账提醒时间与
 * 开关控制，预算通知不该跟着它被关掉或推迟；预算这条只受「预算通知」开关控制，
 * 每天跑一次，加上保存账单时的即时检查（[BudgetAlertNotifier.checkAndNotify]）。
 */
@HiltWorker
class BudgetAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: BudgetAlertNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        notifier.checkAndNotify()
        Result.success()
    } catch (e: CancellationException) {
        // 协程取消必须继续传播，吞进 retry 会破坏 WorkManager 的取消/停止语义
        throw e
    } catch (e: Exception) {
        AppLogger.e("BudgetAlertWorker", "budget alert check failed", e)
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "budget_alert_daily"
    }
}
