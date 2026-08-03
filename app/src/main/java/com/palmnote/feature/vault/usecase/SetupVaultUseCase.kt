package com.palmnote.feature.vault.usecase

import com.palmnote.feature.vault.VaultKeyManager
import javax.inject.Inject

/**
 * 密码本初始化：生成 salt + 数据密钥 + PIN 包裹。
 */
class SetupVaultUseCase @Inject constructor(
    private val vaultKeyManager: VaultKeyManager
) {
    suspend operator fun invoke(pin: String) {
        vaultKeyManager.setup(pin)
    }
}
