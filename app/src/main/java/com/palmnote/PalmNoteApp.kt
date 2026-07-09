package com.palmnote

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.worker.LifeDailyCheckWorker
import com.palmnote.data.worker.AutoBackupWorker
import com.palmnote.ui.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class PalmNoteApp : Application(), Configuration.Provider {

    companion object {
        lateinit var instance: PalmNoteApp
            private set
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var preferencesManager: PreferencesManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        applySavedLanguage()
        NotificationHelper.createChannels(this)
        scheduleDailyCheck()
        scheduleAutoBackup()
        runStartupCheck()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private fun scheduleDailyCheck() {
        applicationScope.launch {
            val hour = preferencesManager.dailyReminderHour.first()
            val minute = preferencesManager.dailyReminderMinute.first()
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            val initialDelay = target.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<LifeDailyCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(this@PalmNoteApp).enqueueUniquePeriodicWork(
                "life_daily_check", ExistingPeriodicWorkPolicy.REPLACE, request
            )
        }
    }
    
    private fun scheduleAutoBackup() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "auto_backup", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    private fun runStartupCheck() {
        val request = OneTimeWorkRequestBuilder<LifeDailyCheckWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
    }

    private fun applySavedLanguage() {
        val savedLanguage = runBlocking { preferencesManager.language.first() }
        com.palmnote.ui.settings.LanguageHelper.applyLanguage(savedLanguage)
    }
}
