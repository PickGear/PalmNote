package com.palmnote.data.backup

/**
 * 备份包的身份，由文件名中的 tag 段承载。
 *
 * 命名约定：`palmnote_<tag>_<epochMillis>.palmnote`。
 *
 * 为什么把身份写进文件名，而不是只写在包里：文件名是用户在文件管理器、网盘、分享列表里
 * 唯一能直接看到的信息。真正需要它的时刻（换机、误删找回）用户手里只有一个文件列表，
 * 而"这份是什么、能不能换机"必须在那一刻就能判断，不能要求先打开 App。
 */
enum class BackupKind(val tag: String) {
    /** 自动备份：落本机私有目录，加密与否跟随全局备份密码，受保留份数约束。 */
    AUTO("auto"),

    /** 用户手动创建的本机备份：落本机私有目录，与自动备份共用密码设置与保留份数。 */
    MANUAL("manual"),

    /** 恢复前快照：恢复失败时的唯一退路，独立计量，**不参与自动轮转**。 */
    SNAPSHOT("snapshot"),

    /** 导出到用户所选文件夹的包（加密与否跟随全局备份密码）；导出后即离开本机目录，不在列表常驻。 */
    PORTABLE("portable"),

    /** 旧版本遗留命名（`palmnote_backup_*`）或无法识别的名称；按可轮转处理，避免旧包堆积。 */
    LEGACY("backup");

    companion object {
        const val PREFIX = "palmnote_"

        private val NAME_REGEX = Regex("""^palmnote_([a-z]+)_(\d+)\.palmnote$""")

        /** 由文件名解析身份；不匹配命名约定的一律归 [LEGACY]，不抛错。 */
        fun fromFileName(fileName: String): BackupKind {
            val tag = NAME_REGEX.find(fileName)?.groupValues?.get(1) ?: return LEGACY
            return entries.firstOrNull { it.tag == tag } ?: LEGACY
        }

        /** 从文件名取回创建时间（epochMillis）；解析不到返回 0，调用方回退到文件系统时间。 */
        fun timestampFromFileName(fileName: String): Long =
            NAME_REGEX.find(fileName)?.groupValues?.get(2)?.toLongOrNull() ?: 0L

        fun fileNameOf(kind: BackupKind, timestamp: Long): String =
            "$PREFIX${kind.tag}_$timestamp.palmnote"
    }
}
