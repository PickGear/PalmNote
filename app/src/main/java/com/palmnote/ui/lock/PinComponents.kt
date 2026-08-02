package com.palmnote.ui.lock

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.palmnote.R
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.components.AppDialog
import kotlinx.coroutines.launch

@Composable
fun PinDotsDisplay(length: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) { index ->
            val filled = index < length
            val fillColor by animateColorAsState(
                targetValue = if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(140),
                label = "pinDotFill"
            )
            val borderColor by animateColorAsState(
                targetValue = if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = tween(140),
                label = "pinDotBorder"
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(
                        width = 2.dp,
                        color = borderColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun PinKeyboard(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: () -> Unit,
    showBiometric: Boolean
) {
    val view = LocalView.current
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (showBiometric) "bio" else "", "0", "del")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                when (key) {
                                    "del" -> onDeleteClick()
                                    "bio" -> onBiometricClick()
                                    "" -> {}
                                    else -> onDigitClick(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "del" -> Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                contentDescription = stringResource(R.string.app_lock_delete),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            "bio" -> Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = stringResource(R.string.app_lock_biometric),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            "" -> {}
                            else -> Text(
                                text = key,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangePinDialog(
    appLockManager: AppLockManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val oldPinWrongText = stringResource(R.string.app_lock_old_pin_wrong)
    val pinMismatchText = stringResource(R.string.app_lock_pin_mismatch)
    val lockedOutText = stringResource(R.string.app_lock_locked_out)

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    1 -> stringResource(R.string.app_lock_old_pin)
                    2 -> stringResource(R.string.app_lock_new_pin)
                    else -> stringResource(R.string.app_lock_confirm_pin)
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (error.isNotEmpty()) {
                    Text(text = error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }

                val currentPin = when (step) {
                    1 -> oldPin
                    2 -> newPin
                    else -> confirmNewPin
                }
                PinDotsDisplay(currentPin.length)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (step) {
                        1 -> stringResource(R.string.app_lock_enter_old_pin)
                        2 -> stringResource(R.string.app_lock_enter_new_pin)
                        else -> stringResource(R.string.app_lock_confirm_new_pin)
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                PinKeyboard(
                    onDigitClick = { digit ->
                        if (!saving && currentPin.length < 6) {
                            when (step) {
                                1 -> oldPin += digit
                                2 -> newPin += digit
                                else -> confirmNewPin += digit
                            }
                            error = ""
                            if (currentPin.length + 1 == 6) {
                                when (step) {
                                    1 -> {
                                        scope.launch {
                                            if (appLockManager.verifyPin(oldPin)) {
                                                step = 2
                                            } else {
                                                error = if (appLockManager.getLockoutRemainingMs() > 0) lockedOutText else oldPinWrongText
                                                oldPin = ""
                                            }
                                        }
                                    }
                                    2 -> {
                                        step = 3
                                    }
                                    3 -> {
                                        if (newPin == confirmNewPin) {
                                            saving = true
                                            scope.launch {
                                                appLockManager.setPin(newPin)
                                                onSuccess()
                                            }
                                        } else {
                                            error = pinMismatchText
                                            confirmNewPin = ""
                                            step = 2
                                            newPin = ""
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        when (step) {
                            1 -> { if (oldPin.isNotEmpty()) { oldPin = oldPin.dropLast(1); error = "" } }
                            2 -> { if (newPin.isNotEmpty()) { newPin = newPin.dropLast(1); error = "" } }
                            3 -> { if (confirmNewPin.isNotEmpty()) { confirmNewPin = confirmNewPin.dropLast(1); error = "" } }
                        }
                    },
                    onBiometricClick = {},
                    showBiometric = false
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}

fun isBiometricAvailable(context: Context): Boolean {
    val biometricManager = BiometricManager.from(context)
    return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
        BiometricManager.BIOMETRIC_SUCCESS -> true
        else -> false
    }
}

fun showBiometricPrompt(context: Context, onResult: (Boolean) -> Unit) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onResult(true)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            onResult(false)
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
        }
    }

    val biometricPrompt = BiometricPrompt(activity, executor, callback)

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.app_lock_biometric_title))
        .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
        .setNegativeButtonText(context.getString(R.string.app_lock_biometric_cancel))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .build()

    biometricPrompt.authenticate(promptInfo)
}
