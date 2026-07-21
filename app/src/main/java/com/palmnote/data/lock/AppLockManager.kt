package com.palmnote.data.lock

import android.content.Context
import android.content.SharedPreferences
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.lock.AppLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

class AppLockManager @Inject constructor(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val _lockState = MutableStateFlow<AppLockState>(AppLockState.Unlocked)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private var cachedIsLockEnabled: Boolean = false
    private var cachedHasPin: Boolean = false

    init {
        cachedIsLockEnabled = preferencesManager.isAppLockEnabled()
        cachedHasPin = preferencesManager.getEncryptedPin().isNotEmpty()
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
    }

    fun isLockEnabled(): Boolean = cachedIsLockEnabled

    fun hasPin(): Boolean = cachedHasPin

    fun verifyPin(pin: String): Boolean {
        val storedPin = preferencesManager.getEncryptedPin()
        if (storedPin.isEmpty()) return false
        
        // PBKDF2 (new format)
        if (storedPin.startsWith(PBKDF2_PREFIX)) {
            return verifyPbkdf2Pin(pin, storedPin)
        }
        
        // Legacy SHA-256 verification
        val isValid = hashPinLegacy(pin) == storedPin
        if (isValid) {
            val newHash = hashPin(pin)
            runBlocking { preferencesManager.setEncryptedPin(newHash) }
        }
        return isValid
    }

    fun setPin(pin: String) {
        runBlocking { preferencesManager.setEncryptedPin(hashPin(pin)) }
        cachedHasPin = true
    }

    fun clearPin() {
        runBlocking { preferencesManager.setEncryptedPin("") }
        cachedHasPin = false
    }

    fun lock() {
        if (cachedIsLockEnabled && cachedHasPin) {
            _lockState.value = AppLockState.Locked
        }
    }

    fun unlock() {
        _lockState.value = AppLockState.Unlocked
    }

    fun setEnabled(enabled: Boolean) {
        preferencesManager.setAppLockEnabledSync(enabled)
        cachedIsLockEnabled = enabled
        if (enabled && !cachedHasPin) {
            _lockState.value = AppLockState.NeedSetup
        } else {
            _lockState.value = AppLockState.Unlocked
        }
    }

    fun shouldShowLockScreen(): Boolean {
        return cachedIsLockEnabled && cachedHasPin && _lockState.value is AppLockState.Locked
    }

    /** PBKDF2 hash: pbkdf2:<iterations>:<base64(salt)>:<base64(hash)> */
    private fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return "${PBKDF2_PREFIX}${PBKDF2_ITERATIONS}:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(hash)}"
    }

    private fun verifyPbkdf2Pin(pin: String, stored: String): Boolean {
        return try {
            val parts = stored.removePrefix(PBKDF2_PREFIX).split(":")
            if (parts.size != 3) false
            else {
                val iterations = parts[0].toInt()
                val salt = Base64.getDecoder().decode(parts[1])
                val expected = Base64.getDecoder().decode(parts[2])
                val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expected.size * 8)
                val actual = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
                MessageDigest.isEqual(actual, expected)
            }
        } catch (_: Exception) { false }
    }

    /** Legacy SHA-256 (for migration only) */
    private fun hashPinLegacy(pin: String): String {
        val salt = getOrCreateSalt()
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest((salt + pin).toByteArray()))
    }

    private fun getOrCreateSalt(): String {
        var salt = prefs.getString("pin_salt", null)
        if (salt == null) {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            salt = Base64.getEncoder().encodeToString(bytes)
            prefs.edit().putString("pin_salt", salt).apply()
        }
        return salt
    }

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100000
        private const val SALT_SIZE = 16
        private const val KEY_LENGTH = 256
        private const val PBKDF2_PREFIX = "pbkdf2:"
    }
}
