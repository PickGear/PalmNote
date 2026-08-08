package com.palmnote.feature.vault

import android.content.Context
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.lock.LockoutTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码本锁定管理（单例）。
 *
 * - 独立于应用锁：即使应用锁未超时，切后台重新进入密码本仍需重新验证。
 * - 失败计数与锁定时长持久化（进程被杀不重置），防暴力破解。
 * - 锁定即清除内存数据密钥（[VaultKeyManager.lock]）。
 */
@Singleton
class VaultLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: VaultKeyManager,
    private val clipboardManager: VaultClipboardManager,
    private val preferencesManager: PreferencesManager
) {
    enum class LockState { NEED_SETUP, LOCKED, UNLOCKED }

    private val _state = MutableStateFlow<LockState>(LockState.LOCKED)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var hasKey = false
    // 防暴力破解追踪（失败次数/锁定期持久化，进程被杀不丢）
    private val lockoutTracker = LockoutTracker(
        context = context,
        prefsName = PREFS_NAME,
        keyFailedAttempts = KEY_FAILED_ATTEMPTS,
        keyLockoutUntil = KEY_LOCKOUT_UNTIL,
    )
    // 串行化解锁，避免并发触发失败计数非原子递增（防暴力窗口扩大）
    private val unlockMutex = Mutex()

    /** 计算并返回当前锁状态（同步）。init 时调用一次以消除首帧展示错误锁门。 */
    fun initialize(): LockState {
        hasKey = keyManager.isInitialized()
        // 若已处于解锁状态（内存中 DK 仍在，如从列表页导航到详情页）则保持 UNLOCKED，避免因导航重新输 PIN
        _state.value = when {
            keyManager.isUnlocked -> LockState.UNLOCKED
            hasKey -> LockState.LOCKED
            else -> LockState.NEED_SETUP
        }
        return _state.value
    }

    /** 冷启动竞态修正：若因 DataStore 未加载被误判为 NEED_SETUP，等 vault_salt 首次发射后重新计算状态。
     *  无锁模式在 init 时可能因快照未就绪漏判，此处一并补自动解锁。 */
    suspend fun reinitializeWhenReady() {
        if (_state.value != LockState.NEED_SETUP) return
        preferencesManager.vaultSalt.first()
        initialize()
        // 无锁模式：DataStore 就绪后补一次自动解锁，否则会卡在 PIN 门（unlock 对无锁恒 false）
        if (keyManager.isNoLockMode() && _state.value == LockState.LOCKED) {
            unlockNoLock()
        }
    }

    val isUnlocked: Boolean get() = _state.value == LockState.UNLOCKED

    fun isLockedOut(): Boolean = lockoutTracker.isLockedOut()

    /** 是否已启用生物识别解锁（存在 Keystore 包裹）。*/
    fun biometricEnabled(): Boolean = keyManager.isBiometricEnabled()

    /** 开启生物识别（纯在场弹窗认证通过后包裹 DK 落盘）。*/
    suspend fun setupBiometric(): Boolean = keyManager.setupBiometric()

    /** 生物识别认证通过后解开 DK 并解锁（纯在场弹窗 + 有效期窗口）。 */
    suspend fun unlockBiometric(): Boolean = withContext(Dispatchers.IO) {
        unlockMutex.withLock {
            val ok = keyManager.unlockBiometric()
            if (ok) {
                lockoutTracker.reset()
                _state.value = LockState.UNLOCKED
            }
            ok
        }
    }

    /** 关闭生物识别解锁。*/
    suspend fun disableBiometric() = keyManager.disableBiometric()

    fun getLockoutRemainingMs(): Long = lockoutTracker.getLockoutRemainingMs()

    /** 首次设置主密码。守卫用实时初始化状态，防止冷启动竞态下误判未初始化而覆盖真实凭据。 */
    suspend fun setup(pin: String): Boolean = withContext(Dispatchers.IO) {
        // 先等 DataStore 就绪：冷启动初期 prefsState 为空快照，isInitialized() 会误报未初始化，
        // 若此时放行会用新盐/DK 覆盖磁盘真实凭据（S-2）。等首次发射后再校验。
        preferencesManager.vaultSalt.first()
        if (keyManager.isInitialized()) {
            return@withContext false
        }
        val ok = keyManager.setup(pin)
        if (ok) {
            hasKey = true
            lockoutTracker.reset()
            _state.value = LockState.UNLOCKED
        }
        ok
    }

    /** 无锁模式：首次使用不设密码，打开即用。 */
    suspend fun setupNoLock(): Boolean = withContext(Dispatchers.IO) {
        // 与 setup() 同理：等 DataStore 就绪再校验，防止冷启动窗口覆盖真实凭据
        preferencesManager.vaultSalt.first()
        if (keyManager.isInitialized()) {
            return@withContext false
        }
        val ok = keyManager.setupNoLock()
        if (ok) {
            hasKey = true
            lockoutTracker.reset()
            _state.value = LockState.UNLOCKED
            // 无锁模式无需验证，关闭回锁，避免切后台后永久卡在 PIN 门
            preferencesManager.setVaultRequireAuth(false)
        }
        ok
    }

    /** 是否无锁模式。*/
    fun isNoLockMode(): Boolean = keyManager.isNoLockMode()

    /** 无锁模式解锁（无需验证）。 */
    suspend fun unlockNoLock(): Boolean = withContext(Dispatchers.IO) {
        unlockMutex.withLock {
            val ok = keyManager.unlockNoLock()
            if (ok) {
                lockoutTracker.reset()
                _state.value = LockState.UNLOCKED
                // 无锁模式关闭回锁，避免切后台后锁死
                preferencesManager.setVaultRequireAuth(false)
            }
            ok
        }
    }

    /** 从无锁模式升级为 PIN 锁。*/
    suspend fun upgradeToPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val ok = keyManager.upgradeToPin(pin)
        if (ok) {
            _state.value = LockState.UNLOCKED
            // 升级为 PIN 锁后恢复回锁
            preferencesManager.setVaultRequireAuth(true)
        }
        ok
    }

    /** 验证 PIN 解锁。失败递增计数并在超过上限后锁定一段时间。*/
    suspend fun unlock(pin: String): Boolean = withContext(Dispatchers.IO) {
        unlockMutex.withLock {
            if (isLockedOut()) {
                return@withLock false
            }
            val ok = keyManager.unlock(pin)
            if (ok) {
                lockoutTracker.onSuccess()
                _state.value = LockState.UNLOCKED
            } else {
                lockoutTracker.onFailedAttempt()
            }
            ok
        }
    }

    /** 已解锁状态下修改主密码。*/
    suspend fun changePin(newPin: String): Boolean {
        val ok = keyManager.changePin(newPin)
        if (!ok) {
            lock()
        }
        return ok
    }

    fun lock() {
        keyManager.lock()
        clipboardManager.clearIfOwned()
        _state.value = if (hasKey) LockState.LOCKED else LockState.NEED_SETUP
    }

    suspend fun requireAuth(): Boolean = preferencesManager.vaultRequireAuth.first()

    suspend fun reset() = withContext(Dispatchers.IO) {
        clipboardManager.clearIfOwned()
        keyManager.reset()
        lockoutTracker.reset()
        hasKey = false
        _state.value = LockState.NEED_SETUP
        // 重置后恢复默认回锁（新设 PIN 的密码本应默认需验证）
        preferencesManager.setVaultRequireAuth(true)
    }


    private companion object {
        const val PREFS_NAME = "vault_prefs"
        const val KEY_FAILED_ATTEMPTS = "vault_failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "vault_lockout_until"
    }
}
