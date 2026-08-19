package com.palmnote.data.backup

// 备份/恢复操作的状态
sealed class BackupState {
    data object Idle : BackupState()
    data class Progress(val percent: Int) : BackupState()
    data class Success(val filePath: String) : BackupState()
    data class Error(val message: String) : BackupState()
}

// 备份文件信息
data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val date: Long,
    val size: Long,
    val md5: String
)
