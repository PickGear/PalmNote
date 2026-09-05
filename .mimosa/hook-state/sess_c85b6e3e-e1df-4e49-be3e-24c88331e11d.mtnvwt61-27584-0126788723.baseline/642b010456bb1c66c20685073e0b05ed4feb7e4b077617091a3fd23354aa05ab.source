package com.palmnote.ui.lock

import androidx.activity.compose.BackHandler
import android.widget.Toast
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.theme.*
import kotlinx.coroutines.coroutineScope
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
    var shakeTrigger by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun triggerShake() { shakeTrigger++ }

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

    // 锁屏图标入场动画：轻微缩放 + 淡入，更精致
    val iconScale = remember { androidx.compose.animation.core.Animatable(0.7f) }
    val iconAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { iconScale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 220f)) }
            launch { iconAlpha.animateTo(1f, androidx.compose.animation.core.tween(220)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.graphicsLayer {
            scaleX = iconScale.value
            scaleY = iconScale.value
            alpha = iconAlpha.value
        }) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

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

        PinShake(if (shakeTrigger > 0) shakeTrigger else null) {
            PinDotsDisplay(pin.length)
        }

        Spacer(modifier = Modifier.height(32.dp))

        PinKeyboard(
            onDigitClick = { digit ->
                if (lockoutRemaining == 0L && pin.length < DEFAULT_PIN_LENGTH) {
                    pin += digit
                    error = ""
                if (pin.length == DEFAULT_PIN_LENGTH) {
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
                                triggerShake()
                                scope.launch { delay(150); pin = ""; isConfirming = false; confirmPin = "" }
                            }
                        } else {
                            val input = pin
                            scope.launch { delay(150); confirmPin = input; isConfirming = true; pin = "" }
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
                                    triggerShake()
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

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.app_version),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
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
                        com.palmnote.util.AppRestarter.restartApp(context)
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
