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
 * 瀵嗙爜鏈攣瀹氱鐞嗭紙鍗曚緥锛夈€?
 *
 * - 鐙珛浜庡簲鐢ㄩ攣锛氬嵆浣垮簲鐢ㄩ攣鏈秴鏃讹紝鍒囧悗鍙?閲嶆柊杩涘叆瀵嗙爜鏈粛闇€閲嶆柊楠岃瘉銆?
 * - 澶辫触璁℃暟涓庨攣瀹氭椂闀挎寔涔呭寲锛堣繘绋嬭鏉€涓嶉噸缃級锛岄槻鏆村姏鐮磋В銆?
 * - 閿佸畾鍗虫竻闄ゅ唴瀛樻暟鎹瘑閽ワ紙[VaultKeyManager.lock]锛夈€?
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
    // 闃叉毚鍔涚牬瑙ｈ拷韪紙澶辫触娆℃暟/閿佸畾鏈熸寔涔呭寲锛岃繘绋嬭鏉€涓嶄涪锛?
    private val lockoutTracker = LockoutTracker(
        context = context,
        prefsName = PREFS_NAME,
        keyFailedAttempts = KEY_FAILED_ATTEMPTS,
        keyLockoutUntil = KEY_LOCKOUT_UNTIL,
    )
    // 涓茶鍖栬В閿侊紝閬垮厤骞跺彂瑙﹀彂澶辫触璁℃暟闈炲師瀛愰€掑锛堥槻鏆村姏绐楀彛鎵╁ぇ锛?
    private val unlockMutex = Mutex()

    fun initialize() {
        hasKey = keyManager.isInitialized()
        // 鑻ュ凡瑙ｉ攣锛堝唴瀛樹腑 DK 浠嶅湪锛屽浠庡垪琛ㄩ〉瀵艰埅鍒拌鎯呴〉锛夊垯淇濇寔 UNLOCKED锛岄伩鍏嶆瘡娆″鑸噸鏂拌緭 PIN
        if (keyManager.isUnlocked) {
            _state.value = LockState.UNLOCKED
        } else {
            _state.value = if (hasKey) LockState.LOCKED else LockState.NEED_SETUP
        }
    }

    val isUnlocked: Boolean get() = _state.value == LockState.UNLOCKED

    fun isLockedOut(): Boolean = lockoutTracker.isLockedOut()

    /** 鏄惁宸插惎鐢ㄧ敓鐗╄瘑鍒В閿侊紙瀛樺湪 Keystore 鍖呰９锛夈€?*/
    fun biometricEnabled(): Boolean = keyManager.isBiometricEnabled()

    /** 鐢熺墿璇嗗埆璁よ瘉閫氳繃鍚庤В瀵?DK 骞惰В閿併€傝繑鍥炴槸鍚︽垚鍔熴€?*/
    suspend fun unlockWithBiometric(cipher: javax.crypto.Cipher): Boolean = withContext(Dispatchers.IO) {
        val ok = keyManager.decryptWithBiometric(cipher)
        if (ok) {
            lockoutTracker.reset()
            _state.value = LockState.UNLOCKED
        }
        ok
    }

    /** 鍦?BiometricPrompt 鍓嶅垵濮嬪寲瑙ｅ瘑 Cipher銆傝繑鍥?null 琛ㄧず鏃犵敓鐗╄瘑鍒瘑閽ャ€?*/
    fun createBioDecryptCipher(): javax.crypto.Cipher? = keyManager.createBioDecryptCipher()

    /** 璁剧疆鐢熺墿璇嗗埆瑙ｉ攣锛堥渶宸茶В閿侊級銆?*/
    suspend fun setupBiometric(): Boolean = keyManager.setupBiometric()

    /** 鍏抽棴鐢熺墿璇嗗埆瑙ｉ攣銆?*/
    suspend fun disableBiometric() = keyManager.disableBiometric()

    fun getLockoutRemainingMs(): Long = lockoutTracker.getLockoutRemainingMs()

    /** 棣栨璁剧疆涓诲瘑鐮併€?*/
    suspend fun setup(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (hasKey) {
            return@withContext false
        }
        keyManager.setup(pin)
        hasKey = true
        lockoutTracker.reset()
        _state.value = LockState.UNLOCKED
        true
    }

    /** 鏃犻攣妯″紡锛氶娆′娇鐢ㄤ笉璁惧瘑鐮侊紝鎵撳紑鍗崇敤銆?*/
    suspend fun setupNoLock(): Boolean = withContext(Dispatchers.IO) {
        if (hasKey) {
            return@withContext false
        }
        val ok = keyManager.setupNoLock()
        if (ok) {
            hasKey = true
            lockoutTracker.reset()
            _state.value = LockState.UNLOCKED
            // 鏃犻攣妯″紡鏃犻渶楠岃瘉锛屽叧闂洖閿侊紝閬垮厤鍒囧悗鍙板悗姘镐箙鍗″湪 PIN 闂?
            preferencesManager.setVaultRequireAuth(false)
        }
        ok
    }

    /** 鏄惁鏃犻攣妯″紡銆?*/
    fun isNoLockMode(): Boolean = keyManager.isNoLockMode()

    /** 鏃犻攣妯″紡瑙ｉ攣锛堟棤闇€楠岃瘉锛夈€?*/
    suspend fun unlockNoLock(): Boolean = withContext(Dispatchers.IO) {
        val ok = keyManager.unlockNoLock()
        if (ok) {
            lockoutTracker.reset()
            _state.value = LockState.UNLOCKED
            // 鏃犻攣妯″紡鍏抽棴鍥為攣锛岄伩鍏嶅垏鍚庡彴鍚庨攣姝?
            preferencesManager.setVaultRequireAuth(false)
        }
        ok
    }

    /** 浠庢棤閿佹ā寮忓崌绾т负 PIN 閿併€?*/
    suspend fun upgradeToPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val ok = keyManager.upgradeToPin(pin)
        if (ok) {
            _state.value = LockState.UNLOCKED
            // 鍗囩骇涓?PIN 閿佸悗鎭㈠鍥為攣
            preferencesManager.setVaultRequireAuth(true)
        }
        ok
    }

    /** 楠岃瘉 PIN 瑙ｉ攣銆傚け璐ラ€掑璁℃暟骞跺湪瓒呰繃涓婇檺鍚庨攣瀹氫竴娈垫椂闂淬€?*/
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

    /** 宸茶В閿佺姸鎬佷笅淇敼涓诲瘑鐮併€?*/
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

