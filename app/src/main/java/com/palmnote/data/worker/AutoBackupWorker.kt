package com.palmnote.data.worker

import android.content.Context
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.backup.BackupState
import com.palmnote.domain.util.AppLogger
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result {
        return try {
            AppLogger.d(TAG, "Starting auto backup")

            // createBackup() 把异常捕获后发出 Error 状态而非抛出（见 BackupRepository.createBackup），
            // 因此直接 collect 永远不会触发异常 → 重试/失败判断形同虚设。这里改为追踪最终状态，
            // 命中 Error 即抛出让 doWork 进入重试分支。
            var failedMessage: String? = null
            backupRepository.createBackup().collect { state ->
                when (state) {
                    is BackupState.Error -> {
                        AppLogger.d(TAG, "Backup state: $state")
                        failedMessage = state.message
                    }
                    is BackupState.Success -> {
                        AppLogger.d(TAG, "Backup state: $state")
                        backupRepository.cleanupOldBackups()
                    }
                    else -> AppLogger.d(TAG, "Backup state: $state")
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
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }
}
