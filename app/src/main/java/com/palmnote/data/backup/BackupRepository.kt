package com.palmnote.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.RoomDatabase
import com.palmnote.app.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import com.palmnote.feature.vault.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    dbKeyStore: DbKeyStore,
    private val vaultDb: VaultDatabase
) {
    private val backupManager = BackupManager(dbKeyStore)

    // 创建备份（支持可选密码）；[kind] 决定文件名身份与是否参与轮转
    fun createBackup(password: String? = null, kind: BackupKind = BackupKind.MANUAL): Flow<BackupState> = flow {
        emit(BackupState.Progress(0))
        try {
            // 打包前先对密码本库做 WAL checkpoint（TRUNCATE），保证快照一致地并入主文件
            checkpointVaultWal()
            val file = backupManager.createBackup(context, db, password, kind)
            emit(BackupState.Progress(100))
            emit(BackupState.Success(file.absolutePath))
        } catch (e: Exception) {
            emit(BackupState.Error(e.message ?: context.getString(R.string.backup_error_create_failed)))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 创建备份并写入用户所选的 SAF 文件夹（备份位置 = 文件夹时，手动/自动共用此路径）。
     * [kind] 为 AUTO 时按 [keep] 轮转该文件夹内的自动备份，避免无限堆积。
     * 目标目录不可用、建文件失败、写入 0 字节都视为失败——静默成功会让用户
     * 以为备份躺在文件夹里，真正需要时才发现是空的。
     */
    suspend fun createBackupToFolder(
        folderUri: Uri,
        password: String?,
        kind: BackupKind,
        keep: Int? = null
    ): BackupState = withContext(Dispatchers.IO) {
        // 缓存中间产物必须清理：备份位置=文件夹时内部目录没有轮转兜底，会无限堆积
        var created: java.io.File? = null
        try {
            checkpointVaultWal()
            val file = backupManager.createBackup(context, db, password, kind)
            created = file
            val docDir = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw IllegalStateException(context.getString(R.string.backup_error_export_failed))
            val newFile = docDir.createFile("application/octet-stream", file.name)
                ?: throw IllegalStateException(context.getString(R.string.backup_error_export_failed))
            try {
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException(context.getString(R.string.backup_error_export_failed))
            } catch (e: Exception) {
                // 半途失败会留下不完整的包，删掉避免下次被当成有效备份
                runCatching { newFile.delete() }
                throw e
            }
            if (kind == BackupKind.AUTO && keep != null) rotateFolderAutoBackups(docDir, keep, newFile.uri)
            BackupState.Success(newFile.uri.toString())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupState.Error(e.message ?: context.getString(R.string.backup_error_export_failed))
        } finally {
            created?.delete()
        }
    }

    /** 文件夹内自动备份轮转：仅删除本应用命名的 AUTO 备份，超出份数删最旧，用户自放的文件不动。 */
    private fun rotateFolderAutoBackups(docDir: DocumentFile, keep: Int, justCreated: Uri) {
        // 按**文件名内嵌时间戳**排序：部分 DocumentsProvider 的 lastModified 返回 0，
        // 只按它排会连新带旧乱删；刚创建的这份永不删除
        docDir.listFiles()
            .filter { it.isFile && it.name?.startsWith("palmnote_auto_") == true && it.name?.endsWith(".palmnote") == true }
            .sortedByDescending {
                BackupKind.timestampFromFileName(it.name ?: "").takeIf { t -> t > 0 } ?: it.lastModified()
            }
            .drop(keep.coerceAtLeast(0))
            .filter { it.uri != justCreated }
            .forEach { runCatching { it.delete() } }
    }

    // 列出所有备份（IO）
    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        backupManager.listBackups(context)
    }

    // 清理旧备份（保留最近 keep 份）
    suspend fun cleanupOldBackups(keep: Int = DEFAULT_KEEP_BACKUPS) {
        backupManager.cleanupOldBackups(context, keep)
    }

    // 删除备份
    fun deleteBackup(file: File) = backupManager.deleteBackup(file)

    private fun checkpointVaultWal() {
        try {
            vaultDb.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { }
        } catch (_: Exception) {}
    }

    private companion object {
        const val DEFAULT_KEEP_BACKUPS = 7
    }
}
