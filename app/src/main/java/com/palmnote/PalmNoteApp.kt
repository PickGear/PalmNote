package com.palmnote

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.LifeDemoSeeder
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.AppIconManager
import com.palmnote.data.backup.BackupManager
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.worker.LifeDailyCheckWorker
import com.palmnote.data.backup.AutoBackupScheduler
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
    @Inject lateinit var lifeDemoSeeder: LifeDemoSeeder
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var autoBackupScheduler: AutoBackupScheduler
    @Inject @JvmSuppressWildcards lateinit var cachedCategoryConfigs: StateFlow<List<CategoryConfig>>
    @Inject @JvmSuppressWildcards lateinit var cachedWallets: StateFlow<List<Wallet>>
    @Inject @JvmSuppressWildcards lateinit var cachedAccountBooks: StateFlow<List<AccountBook>>

    companion object {
        lateinit var instance: PalmNoteApp
            private set
        var cachedStartPage: String = "dashboard"
        var pendingNavigation: String? = null

        // 记一笔的来源账本：BillScreen FAB 设置，AddBill 的 resetForm 消费（跨 VM 实例传递，
        // 因为 BillScreen 与 AddBillScreen 的 BillViewModel 分属不同 backStackEntry）
        var pendingAddBillBookId: Long? = null
        private const val MAX_CRASH_LOG_CHARS = 100_000
        // 崩溃日志最多保留份数，超出按文件名时间戳删除最旧，避免 cacheDir 只增不减
        private const val MAX_CRASH_LOG_FILES = 10
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
            // 自动备份按用户偏好重建（开关/频率/约束都存在 DataStore，改设置后由设置侧再调一次 sync）
            autoBackupScheduler.sync()
            // 旧版本把备份写在应用专属外部存储（可被文件管理器读取），迁移到内部存储后旧文件不再外露
            BackupManager.migrateLegacyExternalBackups(this@PalmNoteApp)
            lifeDataSeeder.seedIfEmpty()
            // 演示数据同样在**启动时**保证最新：模板播完后调用；改过示例内容（SEED_VERSION +1）
            // 即自动重播种，不必等用户进生活页、也不必手动开关演示模式。
            lifeDemoSeeder.ensureSeeded(preferencesManager)
            // 恢复备份成功后进程重启：恢复窗口内的 Widget 广播被抑制过，
            // 这里补一次全量刷新，让桌面小组件立即显示恢复后的数据
            val justRestored = getSharedPreferences("restore_flags", MODE_PRIVATE)
                .getBoolean("just_restored", false)
            if (justRestored) {
                getSharedPreferences("restore_flags", MODE_PRIVATE).edit().clear().apply()
                com.palmnote.ui.widget.WidgetUpdateHelper.refreshAllWidgets()
            }
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
                pruneCrashLogs()
                android.util.Log.e("PalmNote", "Uncaught exception", throwable)
            } catch (_: Exception) {
            }
            defaultCrashHandler?.uncaughtException(thread, throwable)
        }
    }

    /** 崩溃日志只保留最近 MAX_CRASH_LOG_FILES 份，超出按时间（文件名时间戳）删除最旧的，避免只增不减。 */
    private fun pruneCrashLogs() {
        val logs = cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".log")
        } ?: return
        if (logs.size <= MAX_CRASH_LOG_FILES) return
        logs.sortedByDescending { it.name }.drop(MAX_CRASH_LOG_FILES).forEach { it.delete() }
    }

    private val defaultCrashHandler = Thread.getDefaultUncaughtExceptionHandler()

    /** 每日检查任务重建入口：恢复失败等不重启进程的场景需要立即恢复被取消的任务。 */
    fun scheduleDailyCheck() {
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
                LifeDailyCheckWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request
            )
        }
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
