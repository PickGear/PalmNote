package com.palmnote.feature.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 密码生成器测试：长度、字符集约束、空字符集、熵估算。
 */
class VaultPasswordGeneratorTest {

    @Test
    fun generate_respectsLength() {
        listOf(8, 12, 16, 20, 24).forEach { length ->
            val password = VaultPasswordGenerator.generate(length)
            assertEquals(length, password.length)
        }
    }

    @Test
    fun generate_onlyUppercaseAndDigits_containsOnlyThoseChars() {
        val password = VaultPasswordGenerator.generate(length = 100, useLowercase = false, useSymbols = false)
        assertTrue(password.all { it.isUpperCase() || it.isDigit() })
    }

    @Test
    fun generate_onlyDigits_containsOnlyDigits() {
        val password = VaultPasswordGenerator.generate(
            length = 50,
            useUppercase = false,
            useLowercase = false,
            useSymbols = false
        )
        assertTrue(password.all { it.isDigit() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_emptyCharset_throws() {
        VaultPasswordGenerator.generate(
            length = 10,
            useUppercase = false,
            useLowercase = false,
            useDigits = false,
            useSymbols = false
        )
    }

    @Test
    fun generate_zeroLength_throws() {
        try {
            VaultPasswordGenerator.generate(length = 0)
        } catch (_: Exception) {
            return
        }
        throw AssertionError("零长度应抛出异常")
    }

    @Test
    fun estimateEntropy_empty_returnsZero() {
        assertEquals(0.0, VaultPasswordGenerator.estimateEntropy(""), 0.001)
    }

    @Test
    fun estimateEntropy_digitsOnly_matchesLog2Ten() {
        val entropy = VaultPasswordGenerator.estimateEntropy("123456")
        assertEquals(6 * kotlin.math.log2(10.0), entropy, 0.001)
    }

    @Test
    fun strengthMapping_isCorrect() {
        assertEquals(PasswordStrength.WEAK, VaultPasswordGenerator.strengthOf(20.0))
        assertEquals(PasswordStrength.MEDIUM, VaultPasswordGenerator.strengthOf(50.0))
        assertEquals(PasswordStrength.STRONG, VaultPasswordGenerator.strengthOf(70.0))
        assertEquals(PasswordStrength.VERY_STRONG, VaultPasswordGenerator.strengthOf(90.0))
    }

    @Test
    fun generate_usesFullSymbolSet() {
        val password = VaultPasswordGenerator.generate(length = 200)
        assertTrue(password.any { "!@#\$%^&*()_+-=[]{}|;:,.<>?".contains(it) })
    }
}
