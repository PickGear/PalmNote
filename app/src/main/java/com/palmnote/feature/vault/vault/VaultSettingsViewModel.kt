package com.palmnote.feature.vault.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.feature.vault.VaultLockManager
import com.palmnote.feature.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val initialized: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val isNoLockMode: Boolean = false
)

@HiltViewModel
class VaultSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val lockManager: VaultLockManager,
    private val repository: VaultRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val entryCountState = MutableStateFlow(0)

    val uiState: StateFlow<VaultSettingsUiState> = combineState()

    init {
        lockManager.initialize()
        // 无锁模式自动解锁，使设置页可直接操作（生物识别/升级 PIN 均需 dataKey 在内存）
        viewModelScope.launch {
            if (lockManager.isNoLockMode() && lockManager.state.value == VaultLockManager.LockState.LOCKED) {
                lockManager.unlockNoLock()
            }
        }
        viewModelScope.launch {
            entryCountState.value = repository.countEntries()
        }
    }

    private fun combineState(): StateFlow<VaultSettingsUiState> {
        val clipboard = preferencesManager.vaultClipboardClearSeconds
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 30)
        val requireAuth = preferencesManager.vaultRequireAuth
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)
        val bioEnabled = preferencesManager.vaultBiometricEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
        val initialized = lockManager.state
            .map { it != VaultLockManager.LockState.NEED_SETUP }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
        return kotlinx.coroutines.flow.combine(
            clipboard,
            requireAuth,
            entryCountState,
            initialized,
            bioEnabled
        ) { clip, auth, count, init, bio ->
            VaultSettingsUiState(
                clipboardSeconds = clip,
                requireAuth = auth,
                entryCount = count,
                initialized = init,
                biometricEnabled = bio,
                biometricAvailable = com.palmnote.ui.lock.isBiometricAvailable(context),
                isNoLockMode = lockManager.isNoLockMode()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaultSettingsUiState())
    }

    fun setClipboardSeconds(seconds: Int) {
        viewModelScope.launch { preferencesManager.setVaultClipboardClearSeconds(seconds) }
    }

    fun setRequireAuth(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVaultRequireAuth(enabled) }
    }

    /** 启用/关闭生物识别解锁。 */
    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 若未解锁（如 PIN 锁定），先尝试无锁自动解锁；仍失败则忽略（UI 上开关应禁用）
                if (lockManager.state.value != VaultLockManager.LockState.UNLOCKED &&
                    lockManager.isNoLockMode()
                ) {
                    lockManager.unlockNoLock()
                }
                if (lockManager.state.value == VaultLockManager.LockState.UNLOCKED) {
                    lockManager.setupBiometric()
                }
            } else {
                lockManager.disableBiometric()
            }
        }
    }

    /** 修改主密码：先验证旧 PIN，再用新 PIN 重新包裹数据密钥。 */
    fun changePin(oldPin: String, newPin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (lockManager.isNoLockMode()) {
                // 无锁模式：直接设置 PIN 即升级为锁定模式
                onResult(lockManager.upgradeToPin(newPin), "")
                return@launch
            }
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
