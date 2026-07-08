package com.palmnote.ui.lock

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.theme.*

@Composable
fun AppLockScreen(
    appLockManager: AppLockManager,
    onUnlocked: () -> Unit,
    isSetupMode: Boolean = false
) {
    val lockState by appLockManager.lockState.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pinSuccessText = stringResource(R.string.app_lock_pin_success)
    val pinMismatchText = stringResource(R.string.app_lock_pin_mismatch)
    val pinWrongText = stringResource(R.string.app_lock_pin_wrong)

    LaunchedEffect(lockState) {
        if (lockState is AppLockState.Unlocked) {
            onUnlocked()
        }
    }

    val actualIsSetupMode = isSetupMode || lockState is AppLockState.NeedSetup

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (actualIsSetupMode) {
                if (isConfirming) stringResource(R.string.app_lock_confirm_pin) else stringResource(R.string.app_lock_setup_pin)
            } else {
                stringResource(R.string.app_lock_enter_pin)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (error.isNotEmpty()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        PinDotsDisplay(pin.length)

        Spacer(modifier = Modifier.height(32.dp))

        PinKeyboard(
            onDigitClick = { digit ->
                if (pin.length < 6) {
                    pin += digit
                    error = ""
                    if (pin.length == 6) {
                        if (actualIsSetupMode) {
                            if (isConfirming) {
                                if (pin == confirmPin) {
                                    appLockManager.setPin(pin)
                                    appLockManager.setEnabled(true)
                                    appLockManager.unlock()
                                    Toast.makeText(context, pinSuccessText, Toast.LENGTH_SHORT).show()
                                } else {
                                    error = pinMismatchText
                                    pin = ""
                                    isConfirming = false
                                    confirmPin = ""
                                }
                            } else {
                                confirmPin = pin
                                isConfirming = true
                                pin = ""
                            }
                        } else {
                            if (appLockManager.verifyPin(pin)) {
                                appLockManager.unlock()
                            } else {
                                error = pinWrongText
                                pin = ""
                            }
                        }
                    }
                }
            },
            onDeleteClick = {
                if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                    error = ""
                }
            },
            onBiometricClick = {
                showBiometricPrompt(context) { success ->
                    if (success) {
                        appLockManager.unlock()
                    }
                }
            },
            showBiometric = !actualIsSetupMode && isBiometricAvailable(context)
        )

    }
}
