package com.palmnote.feature.vault.usecase

import com.palmnote.feature.vault.VaultKeyManager
import javax.inject.Inject

/**
 * 密码本解锁：PIN 验证 → 解包数据密钥 → 内存驻留。
 */
class UnlockVaultUseCase @Inject constructor(
    private val vaultKeyManager: VaultKeyManager
) {
    /**
     * @return true 解锁成功，false PIN 错误
     */
    suspend operator fun invoke(pin: String): Boolean {
        return vaultKeyManager.unlock(pin)
    }
}
