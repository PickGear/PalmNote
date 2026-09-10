package com.palmnote.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份密码的本地记忆（默认不启用）。
 *
 * 只有用户在备份页显式打开"记住密码"后才写入。密码经 Android Keystore 中的
 * AES-256-GCM 密钥包裹后存于 SharedPreferences —— **明文不落盘**，因此不会削弱
 * "导出包必须加密"这条底线：拿到 prefs 文件也解不开包。
 *
 * 包裹绑定当前设备的 Keystore：换机或卸载重装后自动失效（解不开即视为未记住），
 * 此时需要重新输入密码。这与备份自身的可移植性无关 —— 备份包内的密钥由
 * 用户在导出时设置的密码保护，不依赖本机。
 */
@Singleton
class BackupPasswordStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keystoreKey: SecretKey by lazy { getOrCreateKeystoreKey() }

    /**
     * 读取已记住的密码；未记住或本机已解不开（换机/重装）时返回 null。
     *
     * 解不开说明这条记录对本机已经永久失效（Keystore 密钥随设备/重装一起换了），
     * 顺手清掉，否则残留密文会让"是否记住过"长期为真，开关每次进来都错判。
     */
    fun load(): String? {
        val wrapped = prefs.getString(KEY_NAME, null) ?: return null
        return try {
            String(decrypt(Base64.decode(wrapped, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    /** 记住密码；传入空白等同于清除。 */
    fun save(password: String?) {
        if (password.isNullOrBlank()) {
            clear()
            return
        }
        runCatching {
            val wrapped = encrypt(password.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(KEY_NAME, Base64.encodeToString(wrapped, Base64.NO_WRAP)).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_NAME).apply()
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null)?.let { return it as SecretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build()
        )
        return kg.generateKey()
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
        return cipher.iv + cipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = data.copyOfRange(GCM_IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        /** prefs 文件名与条目名对模块内可见：测试需要直接写入一条"本机已解不开"的残留记录。 */
        internal const val PREFS_NAME = "backup_password_prefs"
        internal const val KEY_NAME = "backup_password"

        private const val ALIAS = "palmnote_backup_password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
    }
}
