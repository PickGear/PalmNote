package com.palmnote.data.db

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * SQLCipher 数据库密钥存储。
 *
 * 密钥以 Base64 形式存于 SharedPreferences（随备份导出/导入），保证跨设备恢复可用；
 * 安全性依赖备份文件自身的 PNB3 加密密码。32 字节随机密钥首次访问时生成。
 */
class DbKeyStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreateKey(): ByteArray {
        prefs.getString(KEY_NAME, null)?.let { encoded ->
            return Base64.decode(encoded, Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_NAME, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    companion object {
        const val PREFS_NAME = "db_key_prefs"
        private const val KEY_NAME = "db_key"
    }
}
