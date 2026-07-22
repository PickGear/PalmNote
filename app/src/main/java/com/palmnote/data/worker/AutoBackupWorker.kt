package com.palmnote.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.palmnote.PalmNoteApp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

class AutoBackupWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
    }

    override fun doWork(): Result = runBlocking {
        val backupRepository = PalmNoteApp.container.backupRepository
        try {
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
