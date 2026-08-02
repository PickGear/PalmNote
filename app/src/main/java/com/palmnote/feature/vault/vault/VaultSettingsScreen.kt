package com.palmnote.feature.vault.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.lock.PinDotsDisplay
import com.palmnote.ui.lock.PinKeyboard
import com.palmnote.ui.theme.vaultTint

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
    var showChangePin by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetDone by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val pinChangedText = stringResource(R.string.vault_pin_changed)
    val lockedOutText = stringResource(R.string.vault_locked_out_short)
    val wrongPinText = stringResource(R.string.vault_old_pin_wrong)

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            VaultSettingRow(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.vault_settings_clipboard),
                subtitle = stringResource(R.string.vault_settings_clipboard_value, state.clipboardSeconds),
                onClick = { showClipboardDialog = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (!state.isNoLockMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.VisibilityOff, null, tint = vaultTint(), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.vault_settings_require_auth),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.vault_settings_require_auth_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.requireAuth, onCheckedChange = viewModel::setRequireAuth)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.VisibilityOff, null, tint = vaultTint(), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.vault_settings_no_lock_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.vault_settings_no_lock_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Fingerprint, null, tint = vaultTint(), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vault_settings_biometric),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.vault_settings_biometric_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.biometricEnabled,
                    enabled = state.initialized && state.biometricAvailable,
                    onCheckedChange = viewModel::setBiometric
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Key, null, tint = vaultTint(), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vault_settings_count),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.vault_settings_count_value, state.entryCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (state.initialized) {
                VaultSettingRow(
                    icon = Icons.Outlined.Password,
                    title = stringResource(if (state.isNoLockMode) R.string.vault_settings_set_pin else R.string.vault_settings_change_pin),
                    subtitle = stringResource(if (state.isNoLockMode) R.string.vault_settings_set_pin_hint else R.string.vault_settings_change_pin_hint),
                    onClick = { showChangePin = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            VaultSettingRow(
                icon = Icons.Outlined.LockReset,
                title = stringResource(R.string.vault_settings_reset),
                subtitle = stringResource(R.string.vault_settings_reset_hint),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showResetConfirm = true }
            )

            if (feedback != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = feedback ?: "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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

    if (showChangePin) {
        ChangeVaultPinDialog(
            onConfirm = { oldPin, newPin ->
                viewModel.changePin(oldPin, newPin) { ok, error ->
                    showChangePin = false
                    feedback = if (ok) {
                        pinChangedText
                    } else {
                        when (error) {
                            "locked_out" -> lockedOutText
                            else -> wrongPinText
                        }
                    }
                }
            },
            onDismiss = { showChangePin = false },
            skipVerify = state.isNoLockMode
        )
    }

    if (showResetConfirm) {
        AppDialog(
            onDismissRequest = { showResetConfirm = false },
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
                TextButton(onClick = { showResetConfirm = false }) {
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
private fun VaultSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = vaultTint(), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    skipVerify: Boolean = false
) {
    var step by remember { mutableStateOf(if (skipVerify) 2 else 1) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val pinMismatch = stringResource(R.string.vault_pin_mismatch)

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
                        else -> stringResource(R.string.vault_new_pin_hint)
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                PinKeyboard(
                    onDigitClick = { digit ->
                        if (currentPin.length < 6) {
                            when (step) {
                                1 -> oldPin += digit
                                2 -> newPin += digit
                                else -> confirmNewPin += digit
                            }
                            error = ""
                            if (currentPin.length + 1 == 6) {
                                when (step) {
                                    1 -> step = 2
                                    2 -> step = 3
                                    else -> {
                                        if (newPin == confirmNewPin) {
                                            onConfirm(if (skipVerify) "" else oldPin, newPin)
                                        } else {
                                            error = pinMismatch
                                            step = 2
                                            newPin = ""
                                            confirmNewPin = ""
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
