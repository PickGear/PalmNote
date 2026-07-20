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
        return hashPin(pin) == storedPin
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

    private fun hashPin(pin: String): String {
        val salt = getOrCreateSalt()
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedPin = salt + pin
        val hash = digest.digest(saltedPin.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
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
}
