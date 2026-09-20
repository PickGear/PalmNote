package com.palmnote.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream

/**
 * SQLCipher 接入：自定义 [SupportSQLiteOpenHelper.Factory]，在打开数据库前
 * 将历史明文数据库原地迁移为加密数据库，再委托给 [SupportOpenHelperFactory]。
 */
class EncryptedOpenHelperFactory(
    private val context: Context,
    private val keyStore: DbKeyStore
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        ensureLibraryLoaded()
        val key = keyStore.getOrCreateKey()
        configuration.name?.let { migrateIfPlaintext(context, it, key) }
        return SupportOpenHelperFactory(key).create(configuration)
    }

    companion object {
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        @Volatile
        private var libraryLoaded = false

        fun ensureLibraryLoaded() {
            if (!libraryLoaded) {
                synchronized(this) {
                    if (!libraryLoaded) {
                        System.loadLibrary("sqlcipher")
                        libraryLoaded = true
                    }
                }
            }
        }

        /**
         * 将历史明文 Room 数据库原地迁移为 SQLCipher 加密数据库。
         * 已加密或不存在时直接跳过；幂等，可安全重复调用。
         */
        @Synchronized
        fun migrateIfPlaintext(context: Context, dbName: String, key: ByteArray) {
            val dbFile = context.getDatabasePath(dbName)
            // 进程死亡恢复：改名备份已就位但加密库未替换完成时，dbFile 不存在而备份在。
            // 不恢复的话 SupportOpenHelperFactory 会静默建一个全新空库，用户数据滞留在备份里。
            // 恢复后不能 return：恢复出来的是明文库，必须继续落入下方流程，
            // 在本次调用内完成加密迁移——否则下一步拿 SQLCipher 密钥开明文库必然报错。
            val backup = File(dbFile.path + ".plaintext-bak")
            if (!dbFile.exists() && backup.exists()) {
                backup.renameTo(dbFile)
            }
            if (!dbFile.exists()) return
            if (!isPlaintext(dbFile)) return

            ensureLibraryLoaded()
            val temp = File(context.cacheDir, "palmnote_encrypt_${System.currentTimeMillis()}.db")
            try {
                // 1. 打开明文库（空密码），触发 WAL 恢复并读取版本号
                val version = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, "", null,
                    SQLiteDatabase.OPEN_READWRITE, null
                ).use { plain ->
                    plain.version
                }

                // 2. 创建加密临时库，ATTACH 明文库并导出
                SQLiteDatabase.openOrCreateDatabase(
                    temp.absolutePath, key, null, null
                ).use { encrypted ->
                    val stmt = encrypted.compileStatement("ATTACH DATABASE ? AS plaintext KEY ''")
                    stmt.bindString(1, dbFile.absolutePath)
                    stmt.execute()
                    encrypted.execSQL("SELECT sqlcipher_export('main', 'plaintext')")
                    encrypted.execSQL("DETACH DATABASE plaintext")
                    encrypted.version = version
                    stmt.close()
                }

                // 3. 原子替换：先把明文库改名留作备份，再改名加密库；仅当加密库就位后才删除备份。
                //    直接 delete 明文库再 rename，中途任何一步失败都会永久丢库。
                val backup = File(dbFile.path + ".plaintext-bak")
                backup.delete()
                if (!dbFile.renameTo(backup)) {
                    throw IllegalStateException("明文库重命名失败，中止加密迁移: $dbFile")
                }
                try {
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()
                    if (!temp.renameTo(dbFile)) {
                        throw IllegalStateException("加密库改名失败，恢复原库: $dbFile")
                    }
                } catch (e: Throwable) {
                    // 加密库未就位：把明文备份改回原名，保住数据
                    backup.renameTo(dbFile)
                    throw e
                }
                backup.delete()
            } finally {
                temp.delete()
            }
        }

        /** 明文 SQLite 库首字节为固定头；SQLCipher 加密库为随机盐值，据此区分。 */
        private fun isPlaintext(dbFile: File): Boolean {
            return try {
                FileInputStream(dbFile).use { fis ->
                    val header = ByteArray(16)
                    val read = fis.read(header)
                    read == 16 && header.contentEquals(SQLITE_HEADER)
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
