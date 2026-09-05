package com.palmnote.feature.vault

import java.security.SecureRandom
import kotlin.math.log2

enum class PasswordStrength { WEAK, MEDIUM, STRONG, VERY_STRONG }

/**
 * 密码生成器：SecureRandom 阻断预测，支持长度与字符集配置。
 */
object VaultPasswordGenerator {
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    private val random = SecureRandom()

    fun generate(
        length: Int = DEFAULT_LENGTH,
        useUppercase: Boolean = true,
        useLowercase: Boolean = true,
        useDigits: Boolean = true,
        useSymbols: Boolean = true
    ): String {
        val charset = buildString {
            if (useUppercase) append(UPPERCASE)
            if (useLowercase) append(LOWERCASE)
            if (useDigits) append(DIGITS)
            if (useSymbols) append(SYMBOLS)
        }
        require(charset.isNotEmpty()) { "至少选择一种字符类型" }
        require(length > 0) { "长度必须大于 0" }
        return buildString {
            repeat(length) { append(charset[random.nextInt(charset.length)]) }
        }
    }

    /** 估算熵值（bit），用于强度指示。 */
    fun estimateEntropy(password: String): Double {
        if (password.isEmpty()) return 0.0
        val charsetSize = when {
            password.any { SYMBOLS.contains(it) } -> 94
            password.any { it.isLetter() } && password.any { it.isDigit() } -> 62
            password.any { it.isLetter() } -> 52
            password.any { it.isDigit() } -> 10
            else -> 1
        }
        return password.length * log2(charsetSize.toDouble())
    }

    fun strengthOf(entropy: Double): PasswordStrength = when {
        entropy < 40.0 -> PasswordStrength.WEAK
        entropy < 60.0 -> PasswordStrength.MEDIUM
        entropy < 80.0 -> PasswordStrength.STRONG
        else -> PasswordStrength.VERY_STRONG
    }

    private const val DEFAULT_LENGTH = 16
}
