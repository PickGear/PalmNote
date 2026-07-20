package com.palmnote.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 100000
    private const val KEY_LENGTH = 256
    
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        return salt
    }
    
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
    
    fun encrypt(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
        
        val encryptedData = cipher.doFinal(data)
        
        return iv + encryptedData
    }
    
    fun decrypt(encryptedData: ByteArray, key: SecretKey): ByteArray {
        val iv = encryptedData.copyOfRange(0, IV_SIZE)
        val data = encryptedData.copyOfRange(IV_SIZE, encryptedData.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
        
        return cipher.doFinal(data)
    }
    
    fun encryptStream(input: java.io.InputStream, output: java.io.OutputStream, key: SecretKey) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
        output.write(iv)
        val cos = javax.crypto.CipherOutputStream(output, cipher)
        input.copyTo(cos)
        cos.flush()
        cos.close()
    }

    fun decryptStream(input: java.io.InputStream, output: java.io.OutputStream, key: SecretKey) {
        val iv = ByteArray(IV_SIZE)
        if (input.read(iv) != IV_SIZE) throw java.io.IOException("Failed to read IV")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
        val cis = javax.crypto.CipherInputStream(input, cipher)
        cis.copyTo(output)
        cis.close()
    }

    fun isEncryptedBackup(file: java.io.File): Boolean {
        return try {
            java.io.FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) != 4) false
                else String(magic) == "PNBK"
            }
        } catch (e: Exception) {
            false
        }
    }
}
