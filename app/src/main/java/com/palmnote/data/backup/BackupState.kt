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
    val checksum: String,
    /** 由文件名解析出的身份；列表据此区分「自动备份 / 手动备份 / 恢复前快照 / 旧版遗留」。 */
    val kind: BackupKind = BackupKind.LEGACY
) {
    /** 快照是恢复失败后的唯一退路，界面上必须独立标识、且不能被批量操作误删。 */
    val isSnapshot: Boolean get() = kind == BackupKind.SNAPSHOT
}
