package com.palmnote.feature.vault.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.feature.vault.VaultLockManager
import com.palmnote.feature.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultSettingsUiState(
    val clipboardSeconds: Int = 30,
    val requireAuth: Boolean = true,
    val entryCount: Int = 0,
    val initialized: Boolean = false
)

@HiltViewModel
class VaultSettingsViewModel @Inject constructor(
    private val lockManager: VaultLockManager,
    private val repository: VaultRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val entryCountState = MutableStateFlow(0)

    val uiState: StateFlow<VaultSettingsUiState> = combineState()

    init {
        lockManager.initialize()
        viewModelScope.launch {
            entryCountState.value = repository.countEntries()
        }
    }

    private fun combineState(): StateFlow<VaultSettingsUiState> {
        val clipboard = preferencesManager.vaultClipboardClearSeconds
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 30)
        val requireAuth = preferencesManager.vaultRequireAuth
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)
        val initialized = lockManager.state
            .map { it != VaultLockManager.LockState.NEED_SETUP }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
        return kotlinx.coroutines.flow.combine(
            clipboard,
            requireAuth,
            entryCountState,
            initialized
        ) { clip, auth, count, init ->
            VaultSettingsUiState(clip, auth, count, init)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaultSettingsUiState())
    }

    fun setClipboardSeconds(seconds: Int) {
        viewModelScope.launch { preferencesManager.setVaultClipboardClearSeconds(seconds) }
    }

    fun setRequireAuth(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVaultRequireAuth(enabled) }
    }

    /** 修改主密码：先验证旧 PIN，再用新 PIN 重新包裹数据密钥。 */
    fun changePin(oldPin: String, newPin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (lockManager.unlock(oldPin)) {
                if (lockManager.changePin(newPin)) {
                    onResult(true, "")
                } else {
                    onResult(false, "lock")
                }
            } else if (lockManager.isLockedOut()) {
                onResult(false, "locked_out")
            } else {
                onResult(false, "wrong")
            }
        }
    }

    /** 重置密码本：清除全部条目与密钥，回到首次设置状态。 */
    fun reset(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearAll()
            lockManager.reset()
            entryCountState.value = 0
            onDone()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
