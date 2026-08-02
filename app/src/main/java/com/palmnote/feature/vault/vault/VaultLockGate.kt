package com.palmnote.feature.vault.vault

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.ui.lock.PinDotsDisplay
import com.palmnote.ui.lock.PinKeyboard
import com.palmnote.ui.lock.showBiometricPrompt
import com.palmnote.ui.theme.vaultTint

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
    createBioDecryptCipher: () -> javax.crypto.Cipher?,
    onBiometricUnlock: (javax.crypto.Cipher) -> Unit,
    onSetup: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onSkip: (() -> Unit)? = null
) {
    var step by remember { mutableIntStateOf(1) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

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

    val displayError = localError ?: error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSetup) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = vaultTint(),
            modifier = Modifier.size(56.dp)
        )
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

        if (displayError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (displayError) {
                    "locked_out" -> stringResource(R.string.vault_locked_out, lockoutSeconds)
                    else -> stringResource(R.string.vault_pin_wrong)
                },
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        PinDotsDisplay((if (step == 1) pin else confirmPin).length)

        Spacer(Modifier.height(32.dp))

        PinKeyboard(
            onDigitClick = { digit ->
                if (lockedOut) return@PinKeyboard
                if (step == 1) {
                    if (pin.length < PIN_LENGTH) {
                        pin += digit
                        if (pin.length == PIN_LENGTH) {
                            if (isSetup) {
                                step = 2
                                confirmPin = ""
                            } else {
                                onUnlock(pin)
                                pin = ""
                            }
                        }
                    }
                } else {
                    if (confirmPin.length < PIN_LENGTH) {
                        confirmPin += digit
                        if (confirmPin.length == PIN_LENGTH) {
                            if (pin == confirmPin) {
                                onSetup(pin)
                                pin = ""
                                confirmPin = ""
                            } else {
                                localError = "wrong"
                                step = 1
                                pin = ""
                                confirmPin = ""
                            }
                        }
                    }
                }
            },
            onDeleteClick = {
                if (step == 1) {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                } else {
                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                }
                localError = null
            },
            onBiometricClick = {
                if (lockedOut || !biometricEnabled) return@PinKeyboard
                val cipher = createBioDecryptCipher() ?: return@PinKeyboard
                showBiometricPrompt(
                    context = context,
                    title = context.getString(R.string.vault_biometric_title),
                    subtitle = context.getString(R.string.vault_biometric_subtitle),
                    cancelText = context.getString(R.string.vault_biometric_cancel),
                    cipher = cipher
                ) { success ->
                    if (success) onBiometricUnlock(cipher)
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
    }
}

private const val PIN_LENGTH = 6
