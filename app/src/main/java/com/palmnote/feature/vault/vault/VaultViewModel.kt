package com.palmnote.feature.vault.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.feature.vault.VaultClipboardManager
import com.palmnote.feature.vault.VaultEntry
import com.palmnote.feature.vault.VaultLockManager
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.feature.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VaultUiState(
    val lockState: LockState = LockState.LOCKED,
    val requireAuth: Boolean = true,
    val biometricEnabled: Boolean = false,
    val entries: List<VaultEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val isLoading: Boolean = true,
    val pinError: String? = null,
    val lockoutRemainingMs: Long = 0L
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val lockManager: VaultLockManager,
    private val repository: VaultRepository,
    private val clipboardManager: VaultClipboardManager,
    preferencesManager: PreferencesManager
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    private val categoryState = MutableStateFlow<String?>(null)
    private val pinErrorState = MutableStateFlow<String?>(null)
    private val lockoutState = MutableStateFlow(0L)
    private var countdownJob: Job? = null

    private val filters = combine(queryState, categoryState) { q, c -> q to c }

    val uiState: StateFlow<VaultUiState> = combine(
        lockManager.state,
        preferencesManager.vaultRequireAuth,
        filters,
        pinErrorState,
        lockoutState
    ) { lock, requireAuth, (query, category), pinError, lockout ->
        VaultUiState(
            lockState = lock,
            requireAuth = requireAuth,
            biometricEnabled = lockManager.biometricEnabled(),
            query = query,
            category = category,
            pinError = pinError,
            lockoutRemainingMs = lockout
        )
    }.flatMapLatest { base ->
        if (base.lockState != LockState.UNLOCKED) {
            flowOf(base)
        } else {
            combine(
                repository.observeEntries(base.query, base.category),
                repository.observeCategories()
            ) { entries, categories ->
                base.copy(entries = entries, categories = categories, isLoading = false)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        VaultUiState()
    )

    init {
        lockManager.initialize()
        // 无锁模式自动解锁（无需验证）
        viewModelScope.launch {
            if (lockManager.isNoLockMode() && lockManager.state.value == LockState.LOCKED) {
                lockManager.unlockNoLock()
            }
        }
    }

    /** 无锁模式：跳过设置，直接进入。 */
    fun setupNoLock() {
        viewModelScope.launch {
            lockManager.setupNoLock()
        }
    }

    fun onQueryChange(query: String) {
        queryState.value = query
    }

    fun onCategorySelect(category: String?) {
        categoryState.value = category
    }

    fun setupPin(pin: String) {
        viewModelScope.launch {
            lockManager.setup(pin)
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            pinErrorState.value = null
            if (lockManager.unlock(pin)) {
                countdownJob?.cancel()
                lockoutState.value = 0L
            } else if (lockManager.isLockedOut()) {
                pinErrorState.value = "locked_out"
                lockoutState.value = lockManager.getLockoutRemainingMs()
                startLockoutCountdown()
            } else {
                pinErrorState.value = "wrong"
            }
        }
    }

    /** 生物识别认证通过后解密 DK 解锁。 */
    fun unlockWithBiometric(cipher: javax.crypto.Cipher) {
        viewModelScope.launch {
            pinErrorState.value = null
            if (lockManager.unlockWithBiometric(cipher)) {
                countdownJob?.cancel()
                lockoutState.value = 0L
            }
        }
    }

    /** 在 BiometricPrompt 前初始化解密 Cipher。返回 null 表示无生物识别密钥。 */
    fun createBioDecryptCipher(): javax.crypto.Cipher? = lockManager.createBioDecryptCipher()

    fun lock() {
        countdownJob?.cancel()
        lockManager.lock()
    }

    fun copyUsername(entry: VaultEntry): Boolean {
        if (entry.username.isEmpty()) return false
        clipboardManager.copy(entry.title, entry.username)
        return true
    }

    fun copyPassword(entry: VaultEntry): Boolean {
        val password = repository.decryptPassword(entry) ?: return false
        if (password.isEmpty()) return false
        clipboardManager.copy(entry.title, password)
        return true
    }

    fun copyUrl(entry: VaultEntry): Boolean {
        if (entry.url.isEmpty()) return false
        clipboardManager.copy(entry.title, entry.url)
        return true
    }

    fun deleteEntry(entry: VaultEntry) {
        viewModelScope.launch {
            repository.delete(entry)
        }
    }

    private fun startLockoutCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive && lockoutState.value > 0L) {
                delay(COUNTDOWN_INTERVAL_MS)
                lockoutState.value = lockManager.getLockoutRemainingMs()
            }
            if (lockoutState.value <= 0L) {
                pinErrorState.value = null
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val COUNTDOWN_INTERVAL_MS = 1000L
    }
}
