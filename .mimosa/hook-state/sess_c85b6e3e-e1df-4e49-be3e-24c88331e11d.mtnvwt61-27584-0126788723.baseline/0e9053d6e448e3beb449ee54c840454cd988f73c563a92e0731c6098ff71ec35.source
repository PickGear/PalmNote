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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VaultUiState(
    val lockState: LockState = LockState.LOCKED,
    /** 锁状态是否已从 DataStore 确认。冷启动时快照未就绪，先不渲染锁门/输入门，避免闪现错误页面。 */
    val lockSettled: Boolean = false,
    val requireAuth: Boolean = true,
    val biometricEnabled: Boolean = false,
    val isNoLockMode: Boolean = false,
    val noLockBannerDismissed: Boolean = false,
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
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    private val categoryState = MutableStateFlow<String?>(null)
    private val pinErrorState = MutableStateFlow<String?>(null)
    private val lockoutState = MutableStateFlow(0L)
    /** 锁状态是否已从 DataStore 确认（冷启动快照就绪后置 true）。 */
    private val lockSettledState = MutableStateFlow(false)
    /** 剪贴板自动清除秒数（复制成功 snackbar 提示用）。 */
    val clipboardClearSeconds: StateFlow<Int> = preferencesManager.vaultClipboardClearSeconds.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        DEFAULT_CLIPBOARD_CLEAR_SECONDS
    )
    val autoLockMode: StateFlow<String> = preferencesManager.autoLockMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        PreferencesManager.AUTO_LOCK_MODE_SYSTEM
    )
    val autoLockTimeoutMinutes: StateFlow<Int> = preferencesManager.autoLockTimeoutMinutes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        PreferencesManager.DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES
    )
    val cardIdentity: StateFlow<String> = preferencesManager.vaultCardIdentity.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "email_first"
    )
    /** 无锁模式引导横幅是否已被用户关闭（持久化，跨导航保持隐藏）。
     *  该值已并入 [VaultUiState.noLockBannerDismissed]，此处仅提供关闭动作。 */
    fun dismissNoLockBanner() {
        viewModelScope.launch { preferencesManager.setVaultNoLockBannerDismissed(true) }
    }

    private var countdownJob: Job? = null

    private val filters = combine(queryState.debounce(SEARCH_DEBOUNCE_MS), categoryState) { q, c -> q to c }
        .distinctUntilChanged()

    /** 安全相关偏好打捆，避免 combine 参数过多破坏解构类型推断。 */
    private val bundleSecurityPrefs = combine(
        preferencesManager.vaultRequireAuth,
        preferencesManager.vaultNoLockBannerDismissed
    ) { requireAuth, dismissed -> requireAuth to dismissed }

    /** 输入态 + 锁状态确认打捆（同样规避 combine 参数上限）。 */
    private val bundleIndicators = combine(
        pinErrorState,
        lockoutState,
        lockSettledState
    ) { pinError, lockout, settled -> Triple(pinError, lockout, settled) }

    val uiState: StateFlow<VaultUiState> = combine(
        lockManager.state,
        bundleSecurityPrefs,
        filters,
        bundleIndicators
    ) { lock, (requireAuth, bannerDismissed), (query, category), (pinError, lockout, settled) ->
        VaultUiState(
            lockState = lock,
            lockSettled = settled,
            requireAuth = requireAuth,
            biometricEnabled = lockManager.biometricEnabled(),
            isNoLockMode = lockManager.isNoLockMode(),
            noLockBannerDismissed = bannerDismissed,
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
                // 分类筛选器：若选中分类不再存在于数据库，自动重置为全部
                val selectedCategory = base.category
                if (selectedCategory != null && selectedCategory !in categories) {
                    categoryState.value = null
                }
                base.copy(entries = entries, categories = categories, isLoading = false)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        // 初始锁状态由 initialize() 同步计算出正确值，避免冷启动首帧闪现错误的锁门/输入门
        VaultUiState(lockState = lockManager.initialize())
    )

    init {
        lockManager.initialize()
        // 冷启动若已处于锁定期：立即展示倒计时（L-5），而非等首次输入后才出现
        if (lockManager.isLockedOut()) {
            lockoutState.value = lockManager.getLockoutRemainingMs()
            pinErrorState.value = "locked_out"
            startLockoutCountdown()
        }
        // 锁定（含 MainActivity 全局自动锁）时清除残留 PIN 错误提示
        viewModelScope.launch {
            lockManager.state.filter { it == LockState.LOCKED }.collect { pinErrorState.value = null }
        }
        // 冷启动竞态修正 + 无锁模式自动解锁，全部完成后才确认锁状态：
        // 否则 UI 会先按未确认的锁状态渲染（闪现错误页面/无锁模式下闪现 PIN 门）。
        viewModelScope.launch {
            lockManager.reinitializeWhenReady()
            if (lockManager.isNoLockMode() && lockManager.state.value == LockState.LOCKED) {
                lockManager.unlockNoLock()
            }
            lockSettledState.value = true
        }
    }

    /** 无锁模式：跳过设置，直接进入。 */
    fun setupNoLock() {
        viewModelScope.launch {
            lockManager.setupNoLock()
        }
    }

    /** 忘记主密码自救：清空全部条目与密钥，回到首次设置状态。 */
    fun resetForVaultLockout() {
        viewModelScope.launch {
            repository.clearAll()
            lockManager.reset()
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

    /** 生物识别认证通过后解开 DK 并解锁；失败提示指纹已失效。 */
    fun unlockBiometric() {
        viewModelScope.launch {
            pinErrorState.value = null
            if (lockManager.unlockBiometric()) {
                countdownJob?.cancel()
                lockoutState.value = 0L
            } else {
                pinErrorState.value = "bio_failed"
            }
        }
    }

    /** 重新输入时清除 PIN 错误提示。 */
    fun clearPinError() {
        pinErrorState.value = null
    }

    fun lock() {
        countdownJob?.cancel()
        pinErrorState.value = null
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

    fun copyEmail(entry: VaultEntry): Boolean {
        if (entry.email.isEmpty()) return false
        clipboardManager.copy(entry.title, entry.email)
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
        const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 30
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
