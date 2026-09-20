package com.palmnote.data.worker

import android.content.Context
import android.net.Uri
import com.palmnote.data.backup.BackupKind
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.backup.BackupState
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.util.AppLogger
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val preferencesManager: PreferencesManager,
    private val backupPasswordStore: com.palmnote.data.backup.BackupPasswordStore,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result {
        // 用户关掉自动备份后，此前入队的任务仍可能被系统唤醒。这里再确认一次，
        // 否则"关掉了却还在偷偷备份"会让开关形同虚设。
        if (!preferencesManager.autoBackupEnabled.first()) {
            AppLogger.d(TAG, "Auto backup disabled by user, skipping")
            return Result.success()
        }

        return try {
            AppLogger.d(TAG, "Starting auto backup")

            // 与手动备份共用同一份备份密码：设了就加密（含便携密钥，可换机恢复），没设就明文。
            // 密码经 Keystore 包裹落盘，无人值守时也能加密。
            // 仓库层方法把异常捕获后发出 Error 状态而非抛出，
            // 因此直接 collect 永远不会触发异常 → 重试/失败判断形同虚设。这里改为追踪最终状态，
            // 命中 Error 即抛出让 doWork 进入重试分支。
            val keepCount = preferencesManager.autoBackupKeepCount.first()
            var failedMessage: String? = null
            // 备份位置 = 用户所选文件夹时，自动备份跟随写入该文件夹（含 AUTO 轮转）
            val folderUri = applicationContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                .getString("backup_location_uri", null)
            if (folderUri != null) {
                val finalState = backupRepository.createBackupToFolder(
                    Uri.parse(folderUri), backupPasswordStore.load(), BackupKind.AUTO, keepCount
                )
                when (finalState) {
                    is BackupState.Error -> failedMessage = finalState.message
                    is BackupState.Success -> preferencesManager.clearAutoBackupError()
                    else -> {}
                }
            } else {
                backupRepository.createBackup(
                    password = backupPasswordStore.load(),
                    kind = BackupKind.AUTO
                ).collect { state ->
                    when (state) {
                        is BackupState.Error -> {
                            AppLogger.d(TAG, "Backup state: $state")
                            failedMessage = state.message
                        }
                        is BackupState.Success -> {
                            AppLogger.d(TAG, "Backup state: $state")
                            backupRepository.cleanupOldBackups(keep = keepCount)
                            preferencesManager.clearAutoBackupError()
                        }
                        else -> {}
                    }
                }
            }
            val error = failedMessage
            if (error != null) {
                throw java.io.IOException("Auto backup failed: $error")
            }

            AppLogger.d(TAG, "Auto backup completed successfully")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Auto backup failed", e)
            // 系统级 Auto Backup 已关闭，App 内备份是唯一退路：失败必须留痕并在界面如实告知，
            // 否则用户要到真正需要恢复的那一刻才发现从来没有备上。
            preferencesManager.recordAutoBackupError(e.message ?: e::class.java.simpleName)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }
}
