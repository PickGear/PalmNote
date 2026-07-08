package com.palmnote.data.backup

import android.content.Context
import com.palmnote.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {
    private val backupManager = BackupManager()

    // 创建备份
    fun createBackup(): Flow<BackupState> = flow {
        emit(BackupState.Progress(0))
        try {
            val file = backupManager.createBackup(context)
            emit(BackupState.Progress(100))
            emit(BackupState.Success(file.absolutePath))
        } catch (e: Exception) {
            emit(BackupState.Error(e.message ?: "备份失败"))
        }
    }.flowOn(Dispatchers.IO)

    // 恢复备份
    fun restoreBackup(file: File): Flow<BackupState> = flow {
        emit(BackupState.Progress(0))
        try {
            db.close()
            backupManager.restoreBackup(context, file)
            db.openHelper.writableDatabase
            emit(BackupState.Progress(100))
            emit(BackupState.Success(file.absolutePath))
        } catch (e: Exception) {
            emit(BackupState.Error(e.message ?: "恢复失败"))
        }
    }.flowOn(Dispatchers.IO)

    // 列出所有备份
    fun listBackups(): List<BackupInfo> = backupManager.listBackups(context)

    // 删除备份
    fun deleteBackup(file: File) = backupManager.deleteBackup(file)
}
