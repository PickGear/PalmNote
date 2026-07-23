package com.palmnote

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.worker.LifeDailyCheckWorker
import com.palmnote.data.worker.AutoBackupWorker
import com.palmnote.di.AppContainer
import com.palmnote.ui.notification.NotificationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PalmNoteApp : Application() {

    companion object {
        lateinit var instance: PalmNoteApp
            private set
        lateinit var container: AppContainer
            private set
        var cachedStartPage: String = "dashboard"
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        cachedStartPage = runBlocking {
            container.preferencesManager.defaultStartPage.first()
        }
        applySavedLanguage()
        NotificationHelper.createChannels(this)
        applicationScope.launch {
            container.database.openHelper.writableDatabase
            scheduleDailyCheck()
            scheduleAutoBackup()
            container.lifeDataSeeder.seedIfEmpty()
        }
    }

    private fun scheduleDailyCheck() {
        applicationScope.launch {
            val hour = container.preferencesManager.dailyReminderHour.first()
            val minute = container.preferencesManager.dailyReminderMinute.first()
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

    private fun applySavedLanguage() {
        val savedLanguage = runBlocking { container.preferencesManager.language.first() }
        com.palmnote.ui.settings.LanguageHelper.applyLanguage(savedLanguage)
    }
}
