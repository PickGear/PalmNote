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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultSettingsUiState(
    val clipboardSeconds: Int = 30,
    val requireAuth: Boolean = true,
    val entryCount: Int = 0,
    val initialized: Boolean = false,
    val unlocked: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val isNoLockMode: Boolean = false
)

/** 密码本锁定状态派生值（避免 combine 超过 5 个 flow 的类型化重载限制）。 */
private data class VaultLockStatus(
    val initialized: Boolean = false,
    val unlocked: Boolean = false
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
        // 冷启动竞态修正
        viewModelScope.launch { lockManager.reinitializeWhenReady() }
        // 无锁模式自动解锁，使设置页可直接操作（生物识别/升级 PIN 均需 dataKey 在内存）
        viewModelScope.launch {
            if (lockManager.isNoLockMode() && lockManager.state.value == VaultLockManager.LockState.LOCKED) {
                lockManager.unlockNoLock()
            }
        }
        viewModelScope.launch {
            repository.observeCount().collect { entryCountState.value = it }
        }
    }

    private fun combineState(): StateFlow<VaultSettingsUiState> {
        val clipboard = preferencesManager.vaultClipboardClearSeconds
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 30)
        val requireAuth = preferencesManager.vaultRequireAuth
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)
        val bioEnabled = preferencesManager.vaultBiometricEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
        val lockStatus = lockManager.state
            .map {
                VaultLockStatus(
                    initialized = it != VaultLockManager.LockState.NEED_SETUP,
                    unlocked = it == VaultLockManager.LockState.UNLOCKED
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaultLockStatus())
        return kotlinx.coroutines.flow.combine(
            clipboard,
            requireAuth,
            entryCountState,
            lockStatus,
            bioEnabled
        ) { clip, auth, count, status, bio ->
            VaultSettingsUiState(
                clipboardSeconds = clip,
                requireAuth = auth,
                entryCount = count,
                initialized = status.initialized,
                unlocked = status.unlocked,
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

    /** 关闭生物识别解锁。 */
    fun disableBiometric() {
        viewModelScope.launch { lockManager.disableBiometric() }
    }

    /** 开启生物识别（纯在场弹窗认证通过后包裹 DK 落盘）。 */
    fun setupBiometric(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(lockManager.setupBiometric()) }
    }

    /** 验证主密码（重置/改密前使用）。失败会递增防暴力计数，超出后锁定。 */
    suspend fun verifyPin(pin: String): Boolean = lockManager.unlock(pin)

    fun getLockoutRemainingMs(): Long = lockManager.getLockoutRemainingMs()

    fun lock() {
        lockManager.lock()
    }

    /** 首次设置主密码（未初始化状态，可在设置页直接设置）。 */
    fun setupPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(lockManager.setup(pin)) }
    }

    /** 已解锁状态下修改主密码（旧 PIN 已在对话框第 1 步验证过）。 */
    fun changePin(newPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (lockManager.isNoLockMode()) {
                // 无锁模式：直接设置 PIN 即升级为锁定模式
                // 若 vault 被自动锁定（切后台等），先无密码解锁恢复 dataKey，否则 upgradeToPin 会因 dataKey==null 失败
                if (!lockManager.isUnlocked) {
                    val unlocked = lockManager.unlockNoLock()
                    if (!unlocked) {
                        onResult(false)
                        return@launch
                    }
                }
                onResult(lockManager.upgradeToPin(newPin))
            } else {
                onResult(lockManager.changePin(newPin))
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
