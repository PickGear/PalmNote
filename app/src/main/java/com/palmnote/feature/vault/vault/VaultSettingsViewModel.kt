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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultSettingsUiState(
    val requireAuth: Boolean = true,
    val initialized: Boolean = false,
    val unlocked: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val isNoLockMode: Boolean = false,
    val cardIdentity: String = "email_first"
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

    /** 无锁模式：需要显式发信号，否则关闭/开启密码后界面不会刷新（combine 只在依赖流发射时重算）。 */
    private val noLockModeState = MutableStateFlow(lockManager.isNoLockMode())

    val uiState: StateFlow<VaultSettingsUiState> = combineState()

    init {
        lockManager.initialize()
        // 冷启动 DataStore 未就绪时上面那次读取可能是旧值，初始化后同步一次
        noLockModeState.value = lockManager.isNoLockMode()
        // 冷启动竞态修正
        viewModelScope.launch {
            lockManager.reinitializeWhenReady()
            // 就绪后再校准一次，否则无锁模式会被当成 PIN 模式渲染
            noLockModeState.value = lockManager.isNoLockMode()
        }
        // 无锁模式自动解锁，使设置页可直接操作（生物识别/升级 PIN 均需 dataKey 在内存）
        viewModelScope.launch {
            if (lockManager.isNoLockMode() && lockManager.state.value == VaultLockManager.LockState.LOCKED) {
                lockManager.unlockNoLock()
            }
        }
    }

    /**
     * 密码本回锁跟随全局「应用锁」的锁定时机（跟随系统锁屏 / 立即 / 超时）。
     * 设置页只展示当前值并指路，不在这里改——改这里等于改全局，藏在密码本里会让人意外。
     */
    val autoLockMode: StateFlow<String> = preferencesManager.autoLockMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PreferencesManager.AUTO_LOCK_MODE_SYSTEM)
    val autoLockTimeoutMinutes: StateFlow<Int> = preferencesManager.autoLockTimeoutMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PreferencesManager.DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES)

    private fun combineState(): StateFlow<VaultSettingsUiState> {
        val requireAuth = preferencesManager.vaultRequireAuth
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)
        val bioEnabled = preferencesManager.vaultBiometricEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
        val cardIdentity = preferencesManager.vaultCardIdentity
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "email_first")
        val lockStatus = lockManager.state
            .map {
                VaultLockStatus(
                    initialized = it != VaultLockManager.LockState.NEED_SETUP,
                    unlocked = it == VaultLockManager.LockState.UNLOCKED
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaultLockStatus())
        val baseState = kotlinx.coroutines.flow.combine(
            requireAuth,
            lockStatus,
            bioEnabled,
            cardIdentity,
            noLockModeState
        ) { auth, status, bio, identity, noLock ->
            VaultSettingsUiState(
                requireAuth = auth,
                initialized = status.initialized,
                unlocked = status.unlocked,
                biometricEnabled = bio,
                cardIdentity = identity,
                biometricAvailable = com.palmnote.ui.lock.isBiometricAvailable(context),
                isNoLockMode = noLock
            )
        }
        return baseState.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            VaultSettingsUiState()
        )
    }

    fun setRequireAuth(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVaultRequireAuth(enabled) }
    }

    fun setCardIdentity(identity: String) {
        viewModelScope.launch { preferencesManager.setVaultCardIdentity(identity) }
    }

    /** 关闭生物识别解锁。 */
    fun disableBiometric() {
        viewModelScope.launch { lockManager.disableBiometric() }
    }

    /** 开启生物识别（纯在场弹窗认证通过后包裹 DK 落盘）。 */
    fun setupBiometric(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(lockManager.setupBiometric()) }
    }

    /** 验证密码（重置/改密前使用）。失败会递增防暴力计数，超出后锁定。 */
    suspend fun verifyPin(pin: String): Boolean = lockManager.unlock(pin)

    fun getLockoutRemainingMs(): Long = lockManager.getLockoutRemainingMs()

    fun lock() {
        lockManager.lock()
    }

    /** 首次设置密码（未初始化状态，可在设置页直接设置）。 */
    fun setupPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(lockManager.setup(pin)) }
    }

    /** 已解锁状态下修改密码（旧密码已在对话框第 1 步验证过）。 */
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
                val ok = lockManager.upgradeToPin(newPin)
                if (ok) noLockModeState.value = false
                onResult(ok)
            } else {
                onResult(lockManager.changePin(newPin))
            }
        }
    }

    /**
     * 关闭密码（改为无锁进入）。密码已由对话框校验过，这里只做密钥重包裹。
     *
     * 条目全部保留——与 [reset] 完全不同，后者是清空数据回到首次设置。
     */
    fun disablePin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // 密码本可能已被自动回锁：先用刚校验过的 PIN 解锁，恢复内存里的 DK 再降级
            if (!lockManager.isUnlocked && !lockManager.isNoLockMode()) {
                if (!lockManager.unlock(pin)) {
                    onResult(false)
                    return@launch
                }
            }
            val ok = lockManager.disablePin()
            if (ok) noLockModeState.value = true
            onResult(ok)
        }
    }

    /** 重置密码本：清除全部条目与密钥，回到首次设置状态。 */
    fun reset(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearAll()
            lockManager.reset()
            onDone()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
