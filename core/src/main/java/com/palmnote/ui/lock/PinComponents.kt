package com.palmnote.ui.lock

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.palmnote.R
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.components.AppDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PinDotsDisplay(length: Int) {
    // 始终显示固定的 DEFAULT_PIN_LENGTH 个槽位，按已输入位数填充，
    // 让用户明确知道还需要输几位
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(DEFAULT_PIN_LENGTH) { index ->
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
    val deleteDesc = stringResource(R.string.app_lock_delete)
    val bioDesc = stringResource(R.string.app_lock_biometric)
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
                    val interactionSource = remember(key) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val keyScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.93f else 1f,
                        animationSpec = tween(90),
                        label = "pinKeyScale"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .graphicsLayer { scaleX = keyScale; scaleY = keyScale }
                            .then(
                                if (key.isEmpty()) Modifier
                                else Modifier.semantics {
                                    role = Role.Button
                                    contentDescription = when (key) {
                                        "del" -> deleteDesc
                                        "bio" -> bioDesc
                                        else -> key
                                    }
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
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
                        if (!saving && currentPin.length < DEFAULT_PIN_LENGTH) {
                            when (step) {
                                1 -> oldPin += digit
                                2 -> newPin += digit
                                else -> confirmNewPin += digit
                            }
                            error = ""
                            if (currentPin.length + 1 == DEFAULT_PIN_LENGTH) {
                                when (step) {
                                    1 -> {
                                        scope.launch {
                                            val input = oldPin
                                            delay(150)
                                            if (appLockManager.verifyPin(input)) {
                                                step = 2
                                            } else {
                                                error = if (appLockManager.getLockoutRemainingMs() > 0) lockedOutText else oldPinWrongText
                                                oldPin = ""
                                            }
                                        }
                                    }
                                    2 -> {
                                        scope.launch { delay(150); step = 3 }
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
                                            scope.launch {
                                                delay(150)
                                                confirmNewPin = ""
                                                step = 2
                                                newPin = ""
                                            }
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

/** 输入错误时左右抖动一次并触发拒绝震动，提升错误反馈的感知度。 */
@Composable
fun PinShake(
    trigger: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            offsetX.snapTo(0f)
            repeat(3) {
                offsetX.animateTo(14f, tween(55))
                offsetX.animateTo(-14f, tween(55))
            }
            offsetX.animateTo(0f, tween(55))
        }
    }
    Box(modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
        content()
    }
}

/**
 * 通用 PIN 验证对话框：输入 6 位 PIN 后异步校验，成功回调 [onSuccess]。
 * 供关闭应用锁、启用生物识别、重置密码本等需要先验证的场景复用。
 */
@Composable
fun PinVerifyDialog(
    title: String,
    onVerify: suspend (String) -> Boolean,
    onLockedOut: () -> Long = { 0L },
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val pinWrongText = stringResource(R.string.app_lock_pin_wrong)
    val lockedOutText = stringResource(R.string.app_lock_locked_out)

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
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
                PinShake(if (shakeTrigger > 0) shakeTrigger else null) {
                    PinDotsDisplay(pin.length)
                }
                Spacer(modifier = Modifier.height(8.dp))
                PinKeyboard(
                    onDigitClick = { digit ->
                        if (!verifying && pin.length < DEFAULT_PIN_LENGTH) {
                            pin += digit
                            error = ""
                            if (pin.length == DEFAULT_PIN_LENGTH) {
                                verifying = true
                                val input = pin
                                scope.launch {
                                    delay(150)
                                    pin = ""
                                    if (onVerify(input)) {
                                        onSuccess()
                                    } else {
                                        verifying = false
                                        error = if (onLockedOut() > 0) lockedOutText else pinWrongText
                                        shakeTrigger++
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        if (!verifying && pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                            error = ""
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
    showBiometricPrompt(
        context = context,
        title = context.getString(R.string.app_lock_biometric_title),
        subtitle = context.getString(R.string.app_lock_biometric_subtitle),
        cancelText = context.getString(R.string.app_lock_biometric_cancel),
        onResult = onResult
    )
}

/** 通用生物识别弹窗（纯在场认证，不传 CryptoObject）。
 *  带认证有效期的 Keystore 密钥与 CryptoObject 互斥，须用此在场弹窗。 */
fun showBiometricPrompt(
    context: Context,
    title: String,
    subtitle: String,
    cancelText: String,
    onResult: (Boolean) -> Unit
) {
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
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(cancelText)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .build()

    try {
        biometricPrompt.authenticate(promptInfo)
    } catch (e: Throwable) {
        // 某些设备上 Keystore 状态异常会直接抛错，兜底不崩溃
        android.util.Log.w("PinComponents", "biometric authenticate failed", e)
        onResult(false)
    }
}

const val DEFAULT_PIN_LENGTH = 6
