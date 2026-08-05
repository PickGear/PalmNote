package com.palmnote.feature.vault.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VaultEditUiState(
    val lockState: LockState = LockState.LOCKED,
    val requireAuth: Boolean = true,
    val biometricEnabled: Boolean = false,
    val gateError: String? = null,
    val lockoutRemainingMs: Long = 0L,
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val entry: VaultEntry? = null
)

@HiltViewModel
class VaultEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lockManager: VaultLockManager,
    private val repository: VaultRepository,
    preferencesManager: PreferencesManager
) : ViewModel() {

    val entryId: Long? = savedStateHandle["entryId"]
    private val entryState = MutableStateFlow<VaultEntry?>(null)
    private val loadingState = MutableStateFlow(true)
    private val categoriesState = MutableStateFlow<List<String>>(emptyList())
    private val gateErrorState = MutableStateFlow(GateError())
    private var countdownJob: Job? = null

    /** 已有分类（供编辑页下拉复用，避免自由输入产生不一致分类）。 */
    val categories: StateFlow<List<String>> = categoriesState.asStateFlow()

    val uiState: StateFlow<VaultEditUiState> = combine(
        combine(lockManager.state, preferencesManager.vaultRequireAuth) { lock, requireAuth -> lock to requireAuth },
        entryState,
        loadingState,
        gateErrorState
    ) { base, entry, loading, gateError ->
        VaultEditUiState(
            lockState = base.first,
            requireAuth = base.second,
            biometricEnabled = lockManager.biometricEnabled(),
            gateError = gateError.message,
            lockoutRemainingMs = gateError.remainingMs,
            isEdit = entryId != null,
            loading = loading,
            entry = entry
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VaultEditUiState(lockState = lockManager.state.value))

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
        viewModelScope.launch {
            repository.observeCategories().collect { categoriesState.value = it }
        }
        if (entryId != null) {
            viewModelScope.launch {
                loadingState.value = true
                entryState.value = repository.getEntry(entryId)
                loadingState.value = false
            }
        } else {
            loadingState.value = false
        }
    }

    /** 详情/编辑页明文展示用。 */
    fun passwordForDisplay(entry: VaultEntry): String? = repository.decryptPassword(entry)

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

    /** 忘记主密码自救：清空全部条目与密钥，回到首次设置状态。 */
    fun resetForVaultLockout() {
        viewModelScope.launch {
            repository.clearAll()
            lockManager.reset()
        }
    }

    fun save(
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        category: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val ok = if (entryId == null) {
                repository.create(title, username, password, url, notes, category) != null
            } else {
                repository.update(entryId, title, username, password, url, notes, category)
            }
            onResult(ok)
        }
    }

    private companion object {
        const val COUNTDOWN_INTERVAL_MS = 1000L
    }
}
