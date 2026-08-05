package com.palmnote.feature.vault.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.lock.DEFAULT_PIN_LENGTH
import com.palmnote.ui.lock.PinDotsDisplay
import com.palmnote.ui.lock.PinKeyboard
import com.palmnote.ui.lock.PinShake
import com.palmnote.ui.lock.showBiometricPrompt
import com.palmnote.ui.theme.vaultTint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 密码本锁定门：首次设置主密码（两步确认）或输入 PIN 解锁。
 * 三种 PIN 输入态由 [LockState] 驱动。
 */
@Composable
fun VaultLockGate(
    lockState: LockState,
    error: String?,
    lockoutRemainingMs: Long,
    biometricEnabled: Boolean,
    onBiometricUnlock: () -> Unit,
    onSetup: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onSkip: (() -> Unit)? = null,
    onForgotPin: (() -> Unit)? = null,
    onPinTyped: () -> Unit = {}
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val isSetup = lockState == LockState.NEED_SETUP
    val context = LocalContext.current

    LaunchedEffect(lockState) {
        step = 1
        pin = ""
        confirmPin = ""
        localError = null
    }

    val lockedOut = lockoutRemainingMs > 0L
    val lockoutSeconds = (lockoutRemainingMs + 999L) / 1000L

    // 图标入场动画：轻微缩放 + 淡入
    val iconScale = remember { androidx.compose.animation.core.Animatable(0.7f) }
    val iconAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { iconScale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 220f)) }
            launch { iconAlpha.animateTo(1f, androidx.compose.animation.core.tween(220)) }
        }
    }

    val displayError = localError ?: error
    // 错误出现时抖动一次；locked_out / bio_failed 不算"输入错误"，不抖
    val shakeTrigger = if (displayError != null && displayError != "locked_out" && displayError != "bio_failed") 1 else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.graphicsLayer {
            scaleX = iconScale.value
            scaleY = iconScale.value
            alpha = iconAlpha.value
        }) {
            Icon(
                imageVector = if (isSetup) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = vaultTint(),
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(if (isSetup) R.string.vault_setup_title else R.string.vault_unlock_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when {
                    isSetup && step == 1 -> R.string.vault_setup_hint_1
                    isSetup && step == 2 -> R.string.vault_setup_hint_2
                    lockedOut -> R.string.vault_locked_out_hint
                    else -> R.string.vault_unlock_hint
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // 固定高度错误区，避免错误出现/消失导致圆点跳动
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            if (displayError != null) {
                Text(
                text = when (displayError) {
                    "locked_out" -> stringResource(R.string.vault_locked_out, lockoutSeconds)
                    "bio_failed" -> stringResource(R.string.vault_bio_failed)
                    else -> stringResource(R.string.vault_pin_wrong)
                },
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        PinShake(shakeTrigger) {
            PinDotsDisplay((if (step == 1) pin else confirmPin).length)
        }

        Spacer(Modifier.height(32.dp))

        PinKeyboard(
            onDigitClick = { digit ->
                if (lockedOut) return@PinKeyboard
                onPinTyped()
                if (step == 1) {
                    if (pin.length < DEFAULT_PIN_LENGTH) {
                        pin += digit
                        if (pin.length == DEFAULT_PIN_LENGTH) {
                            if (isSetup) {
                                scope.launch { delay(150); step = 2; confirmPin = "" }
                            } else {
                                val input = pin
                                scope.launch { delay(150); pin = ""; onUnlock(input) }
                            }
                        }
                    }
                } else {
                    if (confirmPin.length < DEFAULT_PIN_LENGTH) {
                        confirmPin += digit
                        if (confirmPin.length == DEFAULT_PIN_LENGTH) {
                            if (pin == confirmPin) {
                                val input = pin
                                scope.launch { delay(150); pin = ""; confirmPin = ""; onSetup(input) }
                            } else {
                                localError = "wrong"
                                scope.launch { delay(150); step = 1; pin = ""; confirmPin = "" }
                            }
                        }
                    }
                }
            },
            onDeleteClick = {
                onPinTyped()
                if (step == 1) {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                } else {
                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                }
                localError = null
            },
            onBiometricClick = {
                if (lockedOut || !biometricEnabled) return@PinKeyboard
                // 纯在场认证（与应用锁一致，不传 CryptoObject）：
                // 官方规定带认证有效期的密钥与 CryptoObject 互斥，必须走在场弹窗；
                // 认证成功后 30s 窗口内 init(DECRYPT)+doFinal 解开 DK
                showBiometricPrompt(
                    context = context,
                    title = context.getString(R.string.vault_biometric_title),
                    subtitle = context.getString(R.string.vault_biometric_subtitle),
                    cancelText = context.getString(R.string.vault_biometric_cancel)
                ) { success ->
                    if (success) onBiometricUnlock()
                }
            },
            showBiometric = biometricEnabled && !isSetup
        )

        if (isSetup && onSkip != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.vault_skip_setup), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!isSetup && onForgotPin != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onForgotPin) {
                Text(
                    text = stringResource(R.string.vault_forgot_pin),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** 忘记主密码 → 重置密码本的确认对话框（明示数据清空）。 */
@Composable
fun VaultForgotPinDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_forgot_pin_confirm_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.vault_forgot_pin_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.vault_forgot_pin_reset),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
