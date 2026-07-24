package com.palmnote.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.palmnote.PalmNoteApp
import kotlinx.coroutines.flow.collect

class AutoBackupWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
    }

    override suspend fun doWork(): Result {
        val backupRepository = PalmNoteApp.container.backupRepository
        return try {
            Log.d(TAG, "Starting auto backup")
            
            backupRepository.createBackup().collect { state ->
                Log.d(TAG, "Backup state: $state")
            }
            
            Log.d(TAG, "Auto backup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto backup failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
