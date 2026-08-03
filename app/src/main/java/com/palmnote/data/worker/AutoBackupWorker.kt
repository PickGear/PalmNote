package com.palmnote.data.worker

import android.content.Context
import com.palmnote.domain.util.AppLogger
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.palmnote.data.backup.BackupRepository
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
    }

    override suspend fun doWork(): Result {
        return try {
            AppLogger.d(TAG, "Starting auto backup")

            backupRepository.createBackup().collect { state ->
                AppLogger.d(TAG, "Backup state: $state")
            }

            AppLogger.d(TAG, "Auto backup completed successfully")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Auto backup failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
