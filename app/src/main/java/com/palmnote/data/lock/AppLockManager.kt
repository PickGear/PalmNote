package com.palmnote.data.lock

import android.content.Context
import android.content.SharedPreferences
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.lock.AppLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
class AppLockManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val _lockState = MutableStateFlow<AppLockState>(AppLockState.Unlocked)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private var cachedIsLockEnabled: Boolean = false
    private var cachedHasPin: Boolean = false
    private var failedAttempts = 0
    private var lockoutUntilMs = 0L

    init {
        cachedIsLockEnabled = preferencesManager.isAppLockEnabled()
        cachedHasPin = preferencesManager.getEncryptedPin().isNotEmpty()
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
    }

    fun isLockEnabled(): Boolean = cachedIsLockEnabled

    fun hasPin(): Boolean = cachedHasPin

    fun biometricEnabledFlow(): Flow<Boolean> = preferencesManager.biometricEnabled

    fun isBiometricEnabled(): Boolean = runBlocking(Dispatchers.IO) { preferencesManager.biometricEnabled.first() }

    fun verifyPin(pin: String): Boolean {
        if (isLockedOut()) return false
        val storedPin = preferencesManager.getEncryptedPin()
        if (storedPin.isEmpty()) return false

        val isValid = if (storedPin.startsWith(PBKDF2_PREFIX)) {
            verifyPbkdf2Pin(pin, storedPin)
        } else {
            val legacyValid = hashPinLegacy(pin) == storedPin
            if (legacyValid) {
                val newHash = hashPin(pin)
                runBlocking(Dispatchers.IO) { preferencesManager.setEncryptedPin(newHash) }
            }
            legacyValid
        }

        if (isValid) {
            failedAttempts = 0
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockoutUntilMs = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            }
        }
        return isValid
    }

    fun getFailedAttempts(): Int = failedAttempts

    fun getLockoutRemainingMs(): Long {
        val remaining = lockoutUntilMs - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntilMs

    fun resetFailedAttempts() {
        failedAttempts = 0
        lockoutUntilMs = 0
    }

    fun setPin(pin: String) {
        runBlocking(Dispatchers.IO) { preferencesManager.setEncryptedPin(hashPin(pin)) }
        cachedHasPin = true
    }

    fun clearPin() {
        runBlocking(Dispatchers.IO) { preferencesManager.setEncryptedPin("") }
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
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 3000L
    }
}
