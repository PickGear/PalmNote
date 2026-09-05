package com.palmnote

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.AppIconManager
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.worker.LifeDailyCheckWorker
import com.palmnote.data.worker.AutoBackupWorker
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.WalletRepository
import com.palmnote.ui.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class PalmNoteApp : Application(), Configuration.Provider {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var accountBookRepository: AccountBookRepository
    @Inject lateinit var lifeDataSeeder: LifeDataSeeder
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject @JvmSuppressWildcards lateinit var cachedCategoryConfigs: StateFlow<List<CategoryConfig>>
    @Inject @JvmSuppressWildcards lateinit var cachedWallets: StateFlow<List<Wallet>>
    @Inject @JvmSuppressWildcards lateinit var cachedAccountBooks: StateFlow<List<AccountBook>>

    companion object {
        lateinit var instance: PalmNoteApp
            private set
        var cachedStartPage: String = "dashboard"
        var pendingNavigation: String? = null
        private const val MAX_CRASH_LOG_CHARS = 100_000
        private const val REDACT_MARKER = "[REDACTED]"
        private val SENSITIVE_KEYWORDS = listOf(
            "password", "passwordEncrypted", "secret", "token", "credential",
            "pin", "ciphertext", "wrappedkey", "db_key", "vaultkey"
        )
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.palmnote.ui.widget.WidgetUpdateHelper.init(this)
        installCrashHandler()
        try {
            com.palmnote.data.db.EncryptedOpenHelperFactory.ensureLibraryLoaded()
        } catch (_: UnsatisfiedLinkError) {
            android.util.Log.e("PalmNote", "sqlcipher native library load failed")
        }
        applySavedLanguage()
        restoreAppIconStyle()
        NotificationHelper.createChannels(this)
        applicationScope.launch {
            // 异步读取启动页配置，避免在 Application.onCreate 主线程同步阻塞 DataStore
            cachedStartPage = preferencesManager.defaultStartPage.first()
            database.openHelper.writableDatabase
            walletRepository.initDefaultWallets()
            accountBookRepository.initDefaultBooks()
            scheduleDailyCheck()
            scheduleAutoBackup()
            lifeDataSeeder.seedIfEmpty()
            preferencesManager.categoryColorOverrides.first().let {
                com.palmnote.ui.theme.ColorResolver.loadOverrides(it)
            }
            preferencesManager.presetCategoryOverrides.first().let {
                com.palmnote.ui.theme.ColorResolver.loadPresetColorOverrides(it)
            }
        }
    }

    /** 对崩溃堆栈做脱敏：过滤敏感关键字匹配的行内容并限制长度，避免用户导出日志时泄露密钥/口令类信息 */
    private fun sanitizeStackTrace(throwable: Throwable): String {
        val raw = android.util.Log.getStackTraceString(throwable)
        val lines = raw.lineSequence().map { line ->
            if (SENSITIVE_KEYWORDS.any { line.contains(it, ignoreCase = true) }) REDACT_MARKER else line
        }.toList()
        return lines.joinToString("\n").take(MAX_CRASH_LOG_CHARS)
    }

    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 记录本地崩溃日志，便于用户导出反馈（内容已脱敏）
                val logFile = File(cacheDir, "crash_${System.currentTimeMillis()}.log")
                logFile.writeText(
                    "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n" +
                        "Thread: ${thread.name}\n" +
                        sanitizeStackTrace(throwable)
                )
                android.util.Log.e("PalmNote", "Uncaught exception", throwable)
            } catch (_: Exception) {
            }
            defaultCrashHandler?.uncaughtException(thread, throwable)
        }
    }

    private val defaultCrashHandler = Thread.getDefaultUncaughtExceptionHandler()

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

    private fun applySavedLanguage() {
        val savedLanguage = preferencesManager.getLanguage()
        com.palmnote.ui.settings.LanguageHelper.applyLanguage(savedLanguage)
    }

    private fun restoreAppIconStyle() {
        applicationScope.launch {
            val style = preferencesManager.appIconStyle.first()
            if (!AppIconManager.apply(this@PalmNoteApp, style)) {
                android.util.Log.w("PalmNote", "Failed to restore icon style: $style")
            }
        }
    }
}
