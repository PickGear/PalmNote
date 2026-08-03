package com.palmnote.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLCipher 数据库密钥存储。
 *
 * 密钥以 AES-256-GCM 加密后存于 SharedPreferences；
 * 加密密钥由 Android Keystore 管理（TEE 中运算，不可导出）。
 * 32 字节随机密钥首次访问时生成。
 */
@Singleton
class DbKeyStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keystoreKey: SecretKey by lazy { getOrCreateKeystoreKey() }

    @Synchronized
    fun getOrCreateKey(): ByteArray {
        prefs.getString(KEY_NAME, null)?.let { wrapped ->
            return decrypt(Base64.decode(wrapped, Base64.NO_WRAP))
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_NAME, Base64.encodeToString(encrypt(key), Base64.NO_WRAP))
            .apply()
        return key
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null)?.let { return it as SecretKey }
        val kg = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
        return cipher.iv + cipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        const val PREFS_NAME = "db_key_prefs"
        private const val KEY_NAME = "db_key"
        private const val ALIAS = "palmnote_db_key"
    }
}
