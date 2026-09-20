package com.palmnote.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.worker.AutoBackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动备份的唯一调度入口。
 *
 * 之所以要单独一层：自动备份的开关/频率/约束都来自用户偏好，而偏好随时会变。
 * 若把它写死在 Application.onCreate 里，用户改了设置就必须重启 App 才生效。
 * 所有改动偏好的入口都应调用 [sync] 重建任务。
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        const val UNIQUE_WORK_NAME = "auto_backup"
        /** 开启自动备份时的首份备份：不能因为首份要等一整个周期而留下无备份的空窗。 */
        const val INITIAL_WORK_NAME = "auto_backup_initial"
    }

    /** 按当前偏好重建任务：关闭则取消，开启则按频率/约束重新入队。幂等，可重复调用。 */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val workManager = WorkManager.getInstance(context)
        if (!preferencesManager.autoBackupEnabled.first()) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            workManager.cancelUniqueWork(INITIAL_WORK_NAME)
            return@withContext
        }

        val intervalDays = preferencesManager.autoBackupIntervalDays.first()
            .coerceIn(1, PreferencesManager.MAX_AUTO_BACKUP_INTERVAL_DAYS)
        val constraints = buildConstraints()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalDays.toLong(), TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        // UPDATE：只替换周期与约束，不重置已累计的周期进度，避免反复进设置页把备份一次次推后
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)

        if (!hasAutoBackup()) {
            val immediate = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(INITIAL_WORK_NAME, ExistingWorkPolicy.KEEP, immediate)
        }
    }

    /**
     * 备份是「加密读全库 + 写大文件」的重 IO：至少要求电量不低，避免在低电量时雪上加霜。
     */
    private fun buildConstraints(): Constraints =
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

    /** 本机/所选文件夹是否已存在自动备份；用于决定是否需要补一份首备。 */
    private fun hasAutoBackup(): Boolean {
        val dir = File(context.filesDir, BackupManager.BACKUP_DIR_NAME)
        if (dir.listFiles()?.any {
                it.isFile && BackupKind.fromFileName(it.name) == BackupKind.AUTO
            } == true) return true
        // 备份位置是用户所选文件夹时，首备写在那里；不探测的话每次 sync()
        // （启动/改设置）都会误判"从未备份"并再写一份
        val folderUri = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
            .getString("backup_location_uri", null) ?: return false
        return runCatching {
            val docDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                context, android.net.Uri.parse(folderUri)
            ) ?: return@runCatching true  // 目录读不到时宁可不补首备，也别反复写
            docDir.listFiles().any {
                it.isFile && it.name?.startsWith("palmnote_auto_") == true
            }
        }.getOrDefault(true)
    }
}
