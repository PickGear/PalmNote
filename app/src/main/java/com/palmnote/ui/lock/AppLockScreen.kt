package com.palmnote.ui.lock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

@Composable
fun AppLockScreen(
    appLockManager: AppLockManager,
    onUnlocked: () -> Unit,
    isSetupMode: Boolean = false
) {
    val lockState by appLockManager.lockState.collectAsStateWithLifecycle()
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var isConfirming by rememberSaveable { mutableStateOf(false) }
    var lockoutRemaining by rememberSaveable { mutableLongStateOf(appLockManager.getLockoutRemainingMs()) }
    var showForgotConfirm by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pinSuccessText = stringResource(R.string.app_lock_pin_success)
    val pinMismatchText = stringResource(R.string.app_lock_pin_mismatch)
    val pinWrongText = stringResource(R.string.app_lock_pin_wrong)
    val tooManyAttemptsText = stringResource(R.string.app_lock_too_many_attempts)

    val actualIsSetupMode = isSetupMode || lockState is AppLockState.NeedSetup

    // 锁屏拦截系统返回：防止误触退出（退出后重进仍锁，但体验上应阻止）
    BackHandler { }

    LaunchedEffect(lockState) {
        if (lockState is AppLockState.Unlocked) onUnlocked()
    }

    val bioEnabled by appLockManager.biometricEnabledFlow().collectAsStateWithLifecycle(false)

    var bioAutoFired by remember { mutableStateOf(false) }

    LaunchedEffect(lockState, bioEnabled, bioAutoFired) {
        if (actualIsSetupMode || !bioEnabled || !isBiometricAvailable(context)) return@LaunchedEffect
        if (lockState is AppLockState.Locked && !bioAutoFired) {
            bioAutoFired = true
            showBiometricPrompt(context) { success ->
                if (success) {
                    scope.launch { appLockManager.resetFailedAttempts() }
                    appLockManager.unlock()
                }
            }
        }
    }
    LaunchedEffect(lockoutRemaining) {
        if (lockoutRemaining > 0) {
            // 进程恢复后 lockoutRemaining 可能是陈旧 saveable 值，先对齐真实剩余（可能已过期）
            var remaining = appLockManager.getLockoutRemainingMs()
            lockoutRemaining = remaining
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                lockoutRemaining = maxOf(remaining, 0L)
            }
            appLockManager.resetFailedAttempts()
        }
    }

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

        Box(
            modifier = Modifier.height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (error.isNotEmpty() || lockoutRemaining > 0) {
                Text(
                    text = if (lockoutRemaining > 0)
                        tooManyAttemptsText.format(ceil(lockoutRemaining / 1000.0).toInt())
                    else error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PinDotsDisplay(pin.length)

        Spacer(modifier = Modifier.height(32.dp))

        PinKeyboard(
            onDigitClick = { digit ->
                if (lockoutRemaining == 0L && pin.length < 6) {
                    pin += digit
                    error = ""
                if (pin.length == 6) {
                    if (actualIsSetupMode) {
                        if (isConfirming) {
                            if (pin == confirmPin) {
                                scope.launch {
                                    appLockManager.setPin(pin, enable = true)
                                    appLockManager.unlock()
                                    Toast.makeText(context, pinSuccessText, Toast.LENGTH_SHORT).show()
                                }
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
                        scope.launch {
                            if (appLockManager.verifyPin(pin)) {
                                appLockManager.unlock()
                            } else {
                                val remaining = appLockManager.getLockoutRemainingMs()
                                if (remaining > 0) {
                                    lockoutRemaining = remaining
                                } else {
                                    error = pinWrongText
                                }
                                pin = ""
                            }
                        }
                    }
                }
                }
            },
            onDeleteClick = {
                if (lockoutRemaining == 0L && pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                    error = ""
                }
            },
            onBiometricClick = {
                showBiometricPrompt(context) { success ->
                    if (success) {
                        scope.launch { appLockManager.resetFailedAttempts() }
                        appLockManager.unlock()
                    }
                }
            },
            showBiometric = !actualIsSetupMode && bioEnabled && isBiometricAvailable(context)
        )

        if (!actualIsSetupMode) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { showForgotConfirm = true }) {
                Text(
                    text = stringResource(R.string.app_lock_forgot_pin),
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (showForgotConfirm) {
        AlertDialog(
            onDismissRequest = { showForgotConfirm = false },
            title = { Text(stringResource(R.string.app_lock_forgot_pin), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.app_lock_forgot_pin_confirm_lock)) },
            confirmButton = {
                TextButton(onClick = {
                    showForgotConfirm = false
                    scope.launch {
                        appLockManager.resetLockAndData(context)
                        restartApp(context)
                    }
                }) { Text(stringResource(R.string.app_lock_forgot_pin_action_lock), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showForgotConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    val pendingIntent = PendingIntent.getActivity(
        context, 1001, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAt = System.currentTimeMillis() + 300
    val exactAlarmAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    if (exactAlarmAllowed) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent)
    } else {
        alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent)
    }
    Runtime.getRuntime().exit(0)
}
