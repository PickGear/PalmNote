package com.palmnote.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.lock.ChangePinDialog
import com.palmnote.ui.lock.DEFAULT_PIN_LENGTH
import com.palmnote.ui.lock.PinDotsDisplay
import com.palmnote.ui.lock.PinKeyboard
import com.palmnote.ui.lock.PinVerifyDialog
import com.palmnote.ui.lock.isBiometricAvailable
import com.palmnote.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class LockDialogState {
    var showChangePin by mutableStateOf(false)
    var showSetupPin by mutableStateOf(false)
    var showVerifyPin by mutableStateOf(false)
    var showVerifyPinForBio by mutableStateOf(false)
    var showAutoLockDialog by mutableStateOf(false)
    var showAutoLockTimeoutDialog by mutableStateOf(false)
}

private data class AppLockSettingsUiState(
    val isLockEnabled: Boolean,
    val bioAvailable: Boolean,
    val biometricEnabled: Boolean,
    val autoLockMode: String,
    val autoLockTimeoutMinutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val dialog = remember { LockDialogState() }
    val appLockManager = viewModel.appLockManager
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bioAvailable = remember { isBiometricAvailable(context) }
    // 响应式读取，避免 remember 缓存与 DataStore 实际状态不一致
    val isLockEnabled by appLockManager.appLockEnabledFlow().collectAsStateWithLifecycle(initialValue = appLockManager.isLockEnabled())
    val hasPin by appLockManager.hasPinFlow().collectAsStateWithLifecycle(initialValue = appLockManager.hasPin())

    AppLockSettingsList(
        uiState = AppLockSettingsUiState(
            isLockEnabled = isLockEnabled,
            bioAvailable = bioAvailable,
            biometricEnabled = state.biometricEnabled,
            autoLockMode = state.autoLockMode,
            autoLockTimeoutMinutes = state.autoLockTimeoutMinutes
        ),
        onNavigateBack = onNavigateBack,
        onToggleLock = { enabled ->
            if (enabled) {
                if (!hasPin) dialog.showSetupPin = true else appLockManager.setEnabled(true)
            } else {
                dialog.showVerifyPin = true
            }
        },
        onToggleBiometric = { enabled ->
            if (enabled) dialog.showVerifyPinForBio = true else viewModel.setBiometricEnabled(false)
        },
        onChangePin = { dialog.showChangePin = true },
        onAutoLock = { dialog.showAutoLockDialog = true },
        onAutoLockTimeout = { dialog.showAutoLockTimeoutDialog = true }
    )

    LockChangePinDialogs(dialog, appLockManager, hasPin)
    LockVerifyDialogs(dialog, appLockManager, viewModel)
    LockAutoLockDialogs(dialog, state.autoLockMode, state.autoLockTimeoutMinutes, viewModel)
}

@Composable
private fun LockChangePinDialogs(
    dialog: LockDialogState,
    appLockManager: AppLockManager,
    hasPin: Boolean
) {
    val context = LocalContext.current

    if (dialog.showChangePin) {
        ChangePinDialog(
            appLockManager = appLockManager,
            onDismiss = { dialog.showChangePin = false },
            onSuccess = {
                dialog.showChangePin = false
                Toast.makeText(context, R.string.app_lock_pin_changed, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (dialog.showSetupPin) {
        SetupPinDialog(
            appLockManager = appLockManager,
            onDismiss = {
                dialog.showSetupPin = false
                if (!hasPin) appLockManager.setEnabled(false)
            },
            onSuccess = {
                dialog.showSetupPin = false
                Toast.makeText(context, R.string.app_lock_pin_success, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun LockVerifyDialogs(
    dialog: LockDialogState,
    appLockManager: AppLockManager,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (dialog.showVerifyPin) {
        PinVerifyDialog(
            title = stringResource(R.string.app_lock_enter_old_pin),
            onVerify = appLockManager::verifyPin,
            onLockedOut = appLockManager::getLockoutRemainingMs,
            onDismiss = { dialog.showVerifyPin = false },
            onSuccess = {
                dialog.showVerifyPin = false
                scope.launch {
                    appLockManager.disableLock()
                    viewModel.setBiometricEnabled(false)
                }
                Toast.makeText(context, R.string.app_lock_disabled_toast, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (dialog.showVerifyPinForBio) {
        PinVerifyDialog(
            title = stringResource(R.string.app_lock_biometric_enable_verify_title),
            onVerify = appLockManager::verifyPin,
            onLockedOut = appLockManager::getLockoutRemainingMs,
            onDismiss = { dialog.showVerifyPinForBio = false },
            onSuccess = {
                dialog.showVerifyPinForBio = false
                viewModel.setBiometricEnabled(true)
                Toast.makeText(context, R.string.app_lock_biometric_enabled_toast, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun LockAutoLockDialogs(
    dialog: LockDialogState,
    autoLockMode: String,
    autoLockTimeoutMinutes: Int,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current

    if (dialog.showAutoLockDialog) {
        AutoLockModeDialog(
            currentMode = autoLockMode,
            onSelect = { mode ->
                dialog.showAutoLockDialog = false
                viewModel.setAutoLockMode(mode)
                Toast.makeText(context, R.string.app_lock_auto_lock_changed, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { dialog.showAutoLockDialog = false }
        )
    }

    if (dialog.showAutoLockTimeoutDialog) {
        AutoLockTimeoutDialog(
            currentMinutes = autoLockTimeoutMinutes,
            onSelect = { minutes ->
                dialog.showAutoLockTimeoutDialog = false
                viewModel.setAutoLockTimeoutMinutes(minutes)
                Toast.makeText(context, R.string.app_lock_auto_lock_changed, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { dialog.showAutoLockTimeoutDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLockSettingsList(
    uiState: AppLockSettingsUiState,
    onNavigateBack: () -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onAutoLock: () -> Unit,
    onAutoLockTimeout: () -> Unit
) {
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.app_lock_settings_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader(stringResource(R.string.app_lock_settings_section_lock), Icons.Outlined.Lock, ModuleSettings) }
            item {
                LockSettingsCard(
                    uiState = uiState,
                    onToggleLock = onToggleLock,
                    onToggleBiometric = onToggleBiometric,
                    onChangePin = onChangePin
                )
            }

            item { SectionHeader(stringResource(R.string.app_lock_settings_section_autolock), Icons.Outlined.Timer, AccentOrange) }
            item {
                AutoLockSettingsCard(
                    uiState = uiState,
                    onAutoLock = onAutoLock,
                    onAutoLockTimeout = onAutoLockTimeout
                )
            }

            item {
                Text(
                    text = stringResource(R.string.app_lock_security_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun LockSettingsCard(
    uiState: AppLockSettingsUiState,
    onToggleLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onChangePin: () -> Unit
) {
    ModuleCard(modifier = Modifier.fillMaxWidth()) {
        SettingRow {
            SettingRowContent(
                title = stringResource(R.string.settings_app_lock),
                subtitle = stringResource(R.string.settings_app_lock_subtitle)
            )
            CapsuleSwitch(
                checked = uiState.isLockEnabled,
                onCheckedChange = onToggleLock,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        }
        if (uiState.isLockEnabled) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingRow(clickable = onChangePin) {
                SettingRowContent(
                    title = stringResource(R.string.app_lock_change_pin_title),
                    subtitle = stringResource(R.string.app_lock_change_pin_subtitle),
                    showChevron = true
                )
            }
            if (uiState.bioAvailable) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow {
                    SettingRowContent(
                        title = stringResource(R.string.app_lock_biometric_enable),
                        subtitle = stringResource(R.string.app_lock_biometric_enable_subtitle)
                    )
                    CapsuleSwitch(
                        checked = uiState.biometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoLockSettingsCard(
    uiState: AppLockSettingsUiState,
    onAutoLock: () -> Unit,
    onAutoLockTimeout: () -> Unit
) {
    val context = LocalContext.current

    ModuleCard(modifier = Modifier.fillMaxWidth()) {
        SettingRow(clickable = onAutoLock) {
            SettingRowContent(
                title = stringResource(R.string.app_lock_auto_lock),
                subtitle = stringResource(
                    R.string.app_lock_auto_lock_value,
                    if (uiState.autoLockMode == PreferencesManager.AUTO_LOCK_MODE_TIMEOUT) {
                        context.getString(R.string.app_lock_auto_lock_timeout_value, uiState.autoLockTimeoutMinutes)
                    } else {
                        autoLockModeLabel(uiState.autoLockMode, context)
                    }
                ),
                showChevron = true
            )
        }
        if (uiState.autoLockMode == PreferencesManager.AUTO_LOCK_MODE_TIMEOUT) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingRow(clickable = onAutoLockTimeout) {
                SettingRowContent(
                    title = stringResource(R.string.app_lock_auto_lock_timeout_duration),
                    subtitle = stringResource(R.string.app_lock_auto_lock_minutes, uiState.autoLockTimeoutMinutes),
                    showChevron = true
                )
            }
        }
    }
}

@Composable
private fun AutoLockModeDialog(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf(
        Triple(
            PreferencesManager.AUTO_LOCK_MODE_SYSTEM,
            R.string.app_lock_auto_lock_system,
            R.string.app_lock_auto_lock_system_hint
        ),
        Triple(
            PreferencesManager.AUTO_LOCK_MODE_IMMEDIATE,
            R.string.app_lock_auto_lock_immediate,
            R.string.app_lock_auto_lock_immediate_hint
        ),
        Triple(
            PreferencesManager.AUTO_LOCK_MODE_TIMEOUT,
            R.string.app_lock_auto_lock_timeout,
            R.string.app_lock_auto_lock_timeout_hint
        )
    )
    AppDialog(
        title = { Text(stringResource(R.string.app_lock_auto_lock)) },
        text = {
            Column(Modifier.padding(vertical = 8.dp)) {
                modes.forEach { (mode, title, hint) ->
                    AutoLockModeOption(currentMode, mode, title, hint) {
                        onSelect(mode)
                    }
                }
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
private fun AutoLockTimeoutDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = { Text(stringResource(R.string.app_lock_auto_lock_timeout_duration), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.padding(vertical = 8.dp)) {
                listOf(1, 5, 15, 30).forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_lock_auto_lock_minutes, minutes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        RadioButton(selected = currentMinutes == minutes, onClick = null)
                    }
                }
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
private fun SetupPinDialog(
    appLockManager: AppLockManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pinMismatchText = stringResource(R.string.app_lock_pin_mismatch)

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    1 -> stringResource(R.string.app_lock_setup_pin)
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
                    1 -> pin
                    else -> confirmPin
                }
                PinDotsDisplay(currentPin.length)
                Spacer(modifier = Modifier.height(8.dp))
                PinKeyboard(
                    onDigitClick = { digit ->
                        if (!saving && currentPin.length < DEFAULT_PIN_LENGTH) {
                            when (step) {
                                1 -> pin += digit
                                else -> confirmPin += digit
                            }
                            error = ""
                            if (currentPin.length + 1 == DEFAULT_PIN_LENGTH) {
                                when (step) {
                                    1 -> scope.launch { delay(150); step = 2 }
                                    else -> {
                                        if (pin == confirmPin) {
                                            saving = true
                                            scope.launch {
                                                appLockManager.setPin(pin, enable = true)
                                                onSuccess()
                                            }
                                        } else {
                                            error = pinMismatchText
                                            scope.launch { delay(150); confirmPin = ""; step = 2; pin = "" }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        when (step) {
                            1 -> { if (pin.isNotEmpty()) { pin = pin.dropLast(1); error = "" } }
                            else -> { if (confirmPin.isNotEmpty()) { confirmPin = confirmPin.dropLast(1); error = "" } }
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

@Composable
private fun AutoLockModeOption(
    currentMode: String,
    mode: String,
    titleRes: Int,
    hintRes: Int,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(hintRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = currentMode == mode, onClick = { onSelect(mode) })
    }
}

private fun autoLockModeLabel(mode: String, context: android.content.Context): String = when (mode) {
    PreferencesManager.AUTO_LOCK_MODE_IMMEDIATE -> context.getString(R.string.app_lock_auto_lock_immediate)
    PreferencesManager.AUTO_LOCK_MODE_TIMEOUT -> context.getString(R.string.app_lock_auto_lock_timeout)
    else -> context.getString(R.string.app_lock_auto_lock_system)
}
