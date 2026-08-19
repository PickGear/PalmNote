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

                // 3. 原子替换：删除明文库及附属文件，改名加密库
                dbFile.delete()
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                temp.renameTo(dbFile)
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
