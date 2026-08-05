package com.palmnote.feature.vault.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.lock.PinDotsDisplay
import com.palmnote.ui.lock.PinKeyboard
import com.palmnote.ui.lock.PinVerifyDialog
import com.palmnote.ui.lock.showBiometricPrompt
import com.palmnote.ui.lock.DEFAULT_PIN_LENGTH
import com.palmnote.ui.settings.SectionHeader
import com.palmnote.ui.settings.SettingRow
import com.palmnote.ui.settings.SettingRowContent
import com.palmnote.ui.theme.LocalSwitchColor
import com.palmnote.ui.theme.ModuleSettings
import com.palmnote.ui.theme.vaultTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val clipboardOptions = listOf(0, 10, 30, 60)

/**
 * 密码本设置页：剪贴板清除、进入需验证、条目统计、修改主密码、重置。
 */
@Composable
fun VaultSettingsScreen(
    viewModel: VaultSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showClipboardDialog by remember { mutableStateOf(false) }
    var showSetupPin by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showResetPinVerify by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetDone by remember { mutableStateOf(false) }
    var showBioVerifyPin by remember { mutableStateOf(false) }
    var showBioUpgradePin by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val bioScope = rememberCoroutineScope()

    val pinChangedText = stringResource(R.string.vault_pin_changed)
    val pinSetupSuccessText = stringResource(R.string.vault_pin_setup_success)
    val lockInterruptedText = stringResource(R.string.vault_lock_interrupted)
    val lockedOutText = stringResource(R.string.vault_locked_out_short)
    val wrongPinText = stringResource(R.string.vault_old_pin_wrong)
    val bioEnableFailed = stringResource(R.string.vault_bio_enable_failed)

    /** 开启生物识别：纯在场认证（不传 CryptoObject）。认证成功后 30s 窗口内包裹 DK 落盘。 */
    fun enableBiometric() {
        bioScope.launch {
            showBiometricPrompt(
                context = context,
                title = context.getString(R.string.vault_bio_setup_title),
                subtitle = context.getString(R.string.vault_bio_setup_subtitle),
                cancelText = context.getString(R.string.vault_biometric_cancel)
            ) { success ->
                if (success) {
                    viewModel.setupBiometric { ok -> if (!ok) feedback = bioEnableFailed }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_vault),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                }
    )
}

    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader(stringResource(R.string.vault_settings_section_security), Icons.Outlined.Fingerprint, vaultTint()) }
            item {
                ModuleCard(modifier = Modifier.fillMaxWidth()) {
                    if (state.initialized) {
                        SettingRow(clickable = { showChangePin = true }) {
                            SettingRowContent(
                                title = stringResource(if (state.isNoLockMode) R.string.vault_settings_set_pin else R.string.vault_settings_change_pin),
                                subtitle = stringResource(if (state.isNoLockMode) R.string.vault_settings_set_pin_hint else R.string.vault_settings_change_pin_hint),
                                showChevron = true
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        SettingRow(clickable = { showSetupPin = true }) {
                            SettingRowContent(
                                title = stringResource(R.string.vault_settings_set_pin),
                                subtitle = stringResource(R.string.vault_settings_set_pin_hint),
                                showChevron = true
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.vault_settings_biometric),
                            subtitle = stringResource(
                                if (state.isNoLockMode) R.string.vault_settings_biometric_no_lock_hint
                                else R.string.vault_settings_biometric_hint
                            )
                        )
                        CapsuleSwitch(
                            checked = state.biometricEnabled,
                            enabled = state.initialized && state.biometricAvailable,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    when {
                                        state.isNoLockMode -> showBioUpgradePin = true
                                        state.unlocked -> enableBiometric()
                                        else -> showBioVerifyPin = true
                                    }
                                } else {
                                    viewModel.disableBiometric()
                                }
                            },
                            checkedTrackColor = LocalSwitchColor.current
                        )
                    }
                    if (!state.isNoLockMode && state.initialized) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow {
                            SettingRowContent(
                                title = stringResource(R.string.vault_settings_require_auth),
                                subtitle = stringResource(R.string.vault_settings_require_auth_hint)
                            )
                            CapsuleSwitch(
                                checked = state.requireAuth,
                                onCheckedChange = viewModel::setRequireAuth,
                                checkedTrackColor = LocalSwitchColor.current
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = {
                        // 无锁模式或尚未初始化时无需验证（没有可验证的 PIN）
                        if (state.isNoLockMode || !state.initialized) {
                            showResetConfirm = true
                        } else {
                            showResetPinVerify = true
                        }
                    }) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vault_settings_reset),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.vault_settings_reset_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.vault_settings_section_general), Icons.Outlined.Settings, ModuleSettings) }
            item {
                ModuleCard(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(clickable = { showClipboardDialog = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.vault_settings_clipboard),
                            subtitle = stringResource(R.string.vault_settings_clipboard_value, state.clipboardSeconds),
                            showChevron = true
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.vault_settings_count),
                            subtitle = stringResource(R.string.vault_settings_count_value, state.entryCount)
                        )
                    }
                }
            }

            if (feedback != null) {
                item {
                    Text(
                        text = feedback ?: "",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.vault_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showClipboardDialog) {
        ClipboardSecondsDialog(
            current = state.clipboardSeconds,
            onSelect = { seconds ->
                viewModel.setClipboardSeconds(seconds)
                showClipboardDialog = false
            },
            onDismiss = { showClipboardDialog = false }
        )
    }

    if (showSetupPin) {
        ChangeVaultPinDialog(
            onVerifyOld = viewModel::verifyPin,
            onConfirm = { newPin ->
                viewModel.setupPin(newPin) { ok ->
                    showSetupPin = false
                    feedback = if (ok) pinSetupSuccessText else wrongPinText
                }
            },
            onDismiss = { showSetupPin = false },
            skipVerify = true,
            onLockedOut = viewModel::getLockoutRemainingMs
        )
    }

    if (showChangePin) {
        ChangeVaultPinDialog(
            onVerifyOld = viewModel::verifyPin,
            onConfirm = { newPin ->
                // 防御：若对话框打开期间 vault 被自动锁定（切后台等），dataKey 已清空，
                // changePin/upgradeToPin 会因 dataKey==null 失败并显示误导性"旧密码错误"。
                // 此处提前检测并给出明确提示。
                if (!state.unlocked) {
                    showChangePin = false
                    feedback = lockInterruptedText
                    return@ChangeVaultPinDialog
                }
                viewModel.changePin(newPin) { ok ->
                    showChangePin = false
                    feedback = if (ok) pinChangedText else wrongPinText
                }
            },
            // 取消时重新上锁，避免 PIN 验证后把密码本留在已解锁状态（与重置对话框一致）
            onDismiss = {
                showChangePin = false
                viewModel.lock()
            },
            skipVerify = state.isNoLockMode,
            onLockedOut = viewModel::getLockoutRemainingMs
        )
    }

    if (showResetPinVerify) {
        PinVerifyDialog(
            title = stringResource(R.string.vault_old_pin),
            onVerify = viewModel::verifyPin,
            onLockedOut = viewModel::getLockoutRemainingMs,
            onSuccess = {
                showResetPinVerify = false
                showResetConfirm = true
            },
            onDismiss = { showResetPinVerify = false }
        )
    }

    // 密码本锁定态下开启生物识别：先验证主密码解锁，再开启
    if (showBioVerifyPin) {
        PinVerifyDialog(
            title = stringResource(R.string.vault_bio_verify_title),
            onVerify = viewModel::verifyPin,
            onLockedOut = viewModel::getLockoutRemainingMs,
            onSuccess = {
                showBioVerifyPin = false
                enableBiometric()
            },
            onDismiss = { showBioVerifyPin = false }
        )
    }

    // 无锁模式下开启生物识别：先设置主密码（升级为锁定模式），再开启
    if (showBioUpgradePin) {
        ChangeVaultPinDialog(
            onVerifyOld = viewModel::verifyPin,
            onConfirm = { newPin ->
                showBioUpgradePin = false
                viewModel.changePin(newPin) { ok ->
                    if (ok) {
                        enableBiometric()
                    } else {
                        feedback = wrongPinText
                    }
                }
            },
            onDismiss = { showBioUpgradePin = false },
            skipVerify = true,
            onLockedOut = viewModel::getLockoutRemainingMs
        )
    }

    if (showResetConfirm) {
        AppDialog(
            // 取消重置时重新上锁，避免 PIN 验证后把密码本留在已解锁状态
            onDismissRequest = {
                showResetConfirm = false
                viewModel.lock()
            },
            title = { Text(stringResource(R.string.vault_reset_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.vault_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.reset { showResetDone = true }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.vault_reset_action),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    viewModel.lock()
                }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showResetDone) {
        AppDialog(
            onDismissRequest = { showResetDone = false },
            title = { Text(stringResource(R.string.vault_reset_done_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.vault_reset_done_hint)) },
            confirmButton = {
                TextButton(onClick = { showResetDone = false }) {
                    Text(stringResource(R.string.settings_confirm))
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
private fun ClipboardSecondsDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_settings_clipboard), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                clipboardOptions.forEach { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.vault_settings_clipboard_option, seconds),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (seconds == current) {
                            Text(
                                text = stringResource(R.string.vault_settings_clipboard_selected),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
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

@Composable
private fun ChangeVaultPinDialog(
    onVerifyOld: suspend (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    skipVerify: Boolean = false,
    onLockedOut: () -> Long = { 0L }
) {
    var step by remember { mutableStateOf(if (skipVerify) 2 else 1) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pinMismatch = stringResource(R.string.vault_pin_mismatch)
    val oldPinWrong = stringResource(R.string.vault_old_pin_wrong)
    val lockedOutText = stringResource(R.string.vault_locked_out_short)

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    1 -> stringResource(R.string.vault_old_pin)
                    else -> stringResource(R.string.vault_new_pin)
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (step) {
                        1 -> stringResource(R.string.vault_old_pin_hint)
                        2 -> stringResource(R.string.vault_new_pin_hint)
                        else -> stringResource(R.string.vault_new_pin_confirm_hint)
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                PinKeyboard(
                    onDigitClick = { digit ->
                        if (!verifying && currentPin.length < DEFAULT_PIN_LENGTH) {
                            when (step) {
                                1 -> oldPin += digit
                                2 -> newPin += digit
                                else -> confirmNewPin += digit
                            }
                            error = ""
                            if (currentPin.length + 1 == DEFAULT_PIN_LENGTH) {
                                when (step) {
                                    1 -> {
                                        // 第 1 步输满即校验旧 PIN，避免到最后一步才发现错误要重来全部步骤
                                        verifying = true
                                        val input = oldPin
                                        scope.launch {
                                            delay(150) // 短暂延迟，让用户看到第 6 位圆圈
                                            oldPin = ""
                                            if (onVerifyOld(input)) {
                                                verifying = false
                                                step = 2
                                            } else {
                                                verifying = false
                                                error = if (onLockedOut() > 0) lockedOutText else oldPinWrong
                                            }
                                        }
                                    }
                                    2 -> scope.launch { delay(150); step = 3 }
                                    else -> {
                                        if (newPin == confirmNewPin) {
                                            scope.launch { delay(150); onConfirm(newPin) }
                                        } else {
                                            error = pinMismatch
                                            scope.launch {
                                                delay(150)
                                                step = 2
                                                newPin = ""
                                                confirmNewPin = ""
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        when (step) {
                            1 -> { if (oldPin.isNotEmpty()) oldPin = oldPin.dropLast(1) }
                            2 -> { if (newPin.isNotEmpty()) newPin = newPin.dropLast(1) }
                            else -> { if (confirmNewPin.isNotEmpty()) confirmNewPin = confirmNewPin.dropLast(1) }
                        }
                        error = ""
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