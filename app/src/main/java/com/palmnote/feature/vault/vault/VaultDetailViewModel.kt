package com.palmnote.feature.vault.vault

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 锁定门错误状态：消息 + 锁定剩余时间（倒计时更新）。同一包内 Edit/Detail 共用。 */
internal data class GateError(val message: String? = null, val remainingMs: Long = 0L)

data class VaultDetailUiState(
    val lockState: LockState = LockState.LOCKED,
    val requireAuth: Boolean = true,
    val biometricEnabled: Boolean = false,
    val gateError: String? = null,
    val lockoutRemainingMs: Long = 0L,
    val entry: VaultEntry? = null,
    val isLoading: Boolean = true,
    val deleted: Boolean = false
)

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lockManager: VaultLockManager,
    private val repository: VaultRepository,
    private val clipboardManager: VaultClipboardManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val entryId: Long = savedStateHandle["entryId"] ?: INVALID_ID
    private val entryState = MutableStateFlow<VaultEntry?>(null)
    private val loadingState = MutableStateFlow(true)
    private val deletedState = MutableStateFlow(false)
    private val gateErrorState = MutableStateFlow(GateError())
    private var countdownJob: Job? = null

    /** 剪贴板自动清除秒数（复制成功 snackbar 提示用）。 */
    val clipboardClearSeconds: StateFlow<Int> = preferencesManager.vaultClipboardClearSeconds.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000L),
        DEFAULT_CLIPBOARD_CLEAR_SECONDS
    )

    val autoLockMode: StateFlow<String> = preferencesManager.autoLockMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000L),
        PreferencesManager.AUTO_LOCK_MODE_SYSTEM
    )
    val autoLockTimeoutMinutes: StateFlow<Int> = preferencesManager.autoLockTimeoutMinutes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000L),
        PreferencesManager.DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES
    )

    val uiState: StateFlow<VaultDetailUiState> = combine(
        combine(lockManager.state, preferencesManager.vaultRequireAuth) { lock, requireAuth -> lock to requireAuth },
        entryState,
        loadingState,
        deletedState,
        gateErrorState
    ) { base, entry, loading, deleted, gateError ->
        VaultDetailUiState(
            lockState = base.first,
            requireAuth = base.second,
            biometricEnabled = lockManager.biometricEnabled(),
            gateError = gateError.message,
            lockoutRemainingMs = gateError.remainingMs,
            entry = entry,
            isLoading = loading,
            deleted = deleted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VaultDetailUiState(lockState = lockManager.state.value))

    init {
        lockManager.initialize()
        // 冷启动若已处于锁定期：立即展示倒计时（L-5），而非等首次输入后才出现
        if (lockManager.isLockedOut()) {
            gateErrorState.value = GateError("locked_out", lockManager.getLockoutRemainingMs())
            startLockoutCountdown()
        }
        // 锁定（含 MainActivity 全局自动锁）时清除残留门错误提示
        viewModelScope.launch {
            lockManager.state.filter { it == LockState.LOCKED }.collect { gateErrorState.value = GateError() }
        }
        if (lockManager.isNoLockMode()) {
            viewModelScope.launch {
                if (lockManager.state.value == VaultLockManager.LockState.LOCKED) {
                    lockManager.unlockNoLock()
                }
            }
        }
        // 冷启动竞态修正
        viewModelScope.launch { lockManager.reinitializeWhenReady() }
        reload()
    }

    fun reload() {
        if (entryId == INVALID_ID) {
            loadingState.value = false
            return
        }
        viewModelScope.launch {
            loadingState.value = true
            entryState.value = repository.getEntry(entryId)
            loadingState.value = false
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch { lockManager.setup(pin) }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            gateErrorState.value = GateError()
            if (!lockManager.unlock(pin)) {
                if (lockManager.isLockedOut()) {
                    gateErrorState.value = GateError("locked_out", lockManager.getLockoutRemainingMs())
                    startLockoutCountdown()
                } else {
                    gateErrorState.value = GateError("wrong")
                }
            }
        }
    }

    fun biometricEnabled(): Boolean = lockManager.biometricEnabled()

    fun unlockBiometric() {
        viewModelScope.launch {
            gateErrorState.value = GateError()
            if (!lockManager.unlockBiometric()) {
                gateErrorState.value = GateError("bio_failed")
            }
        }
    }

    /** 重新输入时清除门错误提示。 */
    fun clearGateError() {
        gateErrorState.value = GateError()
    }

    fun lock() {
        countdownJob?.cancel()
        gateErrorState.value = GateError()
        lockManager.lock()
    }

    private fun startLockoutCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive && gateErrorState.value.remainingMs > 0L) {
                delay(COUNTDOWN_INTERVAL_MS)
                gateErrorState.value = gateErrorState.value.copy(remainingMs = lockManager.getLockoutRemainingMs())
            }
            if (gateErrorState.value.remainingMs <= 0L) {
                gateErrorState.value = GateError()
            }
        }
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

    /** 详情页明文展示用（同步解密，需已解锁）。 */
    fun passwordForDisplay(entry: VaultEntry): String? = repository.decryptPassword(entry)

    fun copyUrl(entry: VaultEntry): Boolean {
        if (entry.url.isEmpty()) return false
        clipboardManager.copy(entry.title, entry.url)
        return true
    }

    fun copyNotes(entry: VaultEntry): Boolean {
        if (entry.notes.isEmpty()) return false
        clipboardManager.copy(entry.title, entry.notes)
        return true
    }

    fun delete() {
        viewModelScope.launch {
            entryState.value?.let { repository.delete(it) }
            deletedState.value = true
        }
    }

    /** 忘记主密码自救：清空全部条目与密钥，回到首次设置状态。 */
    fun resetForVaultLockout() {
        viewModelScope.launch {
            repository.clearAll()
            lockManager.reset()
            deletedState.value = true
        }
    }

    private companion object {
        const val INVALID_ID = -1L
        const val COUNTDOWN_INTERVAL_MS = 1000L
        const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 30
    }
}
