package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
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
import com.palmnote.R
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.lock.ChangePinDialog
import com.palmnote.ui.lock.DEFAULT_PIN_LENGTH
import com.palmnote.ui.lock.PinDotsDisplay
import com.palmnote.ui.lock.PinKeyboard
import com.palmnote.ui.lock.PinVerifyDialog
import com.palmnote.ui.lock.isBiometricAvailable
import com.palmnote.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVault: () -> Unit = {},
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appLockManager = viewModel.appLockManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChangePin by remember { mutableStateOf(false) }
    var showSetupPin by remember { mutableStateOf(false) }
    var showVerifyPin by remember { mutableStateOf(false) }
    var showVerifyPinForBio by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showAutoLockTimeoutDialog by remember { mutableStateOf(false) }

    val bioAvailable = remember { isBiometricAvailable(context) }
    // 响应式读取，避免 remember 缓存与 DataStore 实际状态不一致
    val isLockEnabled by appLockManager.appLockEnabledFlow().collectAsStateWithLifecycle(initialValue = appLockManager.isLockEnabled())
    val hasPin by appLockManager.hasPinFlow().collectAsStateWithLifecycle(initialValue = appLockManager.hasPin())

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
                ModuleCard(modifier = Modifier.fillMaxWidth()) {
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.settings_app_lock),
                            subtitle = stringResource(R.string.settings_app_lock_subtitle)
                        )
                        CapsuleSwitch(
                            checked = isLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (!hasPin) {
                                        showSetupPin = true
                                    } else {
                                        appLockManager.setEnabled(true)
                                    }
                                } else {
                                    showVerifyPin = true
                                }
                            },
                            checkedTrackColor = LocalSwitchColor.current
                        )
                    }
                    if (isLockEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(clickable = { showChangePin = true }) {
                            SettingRowContent(
                                title = stringResource(R.string.app_lock_change_pin_title),
                                subtitle = stringResource(R.string.app_lock_change_pin_subtitle),
                                showChevron = true
                            )
                        }
                        if (bioAvailable) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingRow {
                                SettingRowContent(
                                    title = stringResource(R.string.app_lock_biometric_enable),
                                    subtitle = stringResource(R.string.app_lock_biometric_enable_subtitle)
                                )
                                CapsuleSwitch(
                                    checked = state.biometricEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            // 开启生物识别前先验证 PIN，防止设备已解锁时被他人顺手开启
                                            showVerifyPinForBio = true
                                        } else {
                                            viewModel.setBiometricEnabled(false)
                                        }
                                    },
                                    checkedTrackColor = LocalSwitchColor.current
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.app_lock_settings_section_autolock), Icons.Outlined.Timer, AccentOrange) }
            item {
                ModuleCard(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(clickable = { showAutoLockDialog = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.app_lock_auto_lock),
                            subtitle = stringResource(
                                R.string.app_lock_auto_lock_value,
                                if (state.autoLockMode == com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_TIMEOUT) {
                                    context.getString(R.string.app_lock_auto_lock_timeout_value, state.autoLockTimeoutMinutes)
                                } else {
                                    autoLockModeLabel(state.autoLockMode, context)
                                }
                            ),
                            showChevron = true
                        )
                    }
                    if (state.autoLockMode == com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_TIMEOUT) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(clickable = { showAutoLockTimeoutDialog = true }) {
                            SettingRowContent(
                                title = stringResource(R.string.app_lock_auto_lock_timeout_duration),
                                subtitle = stringResource(R.string.app_lock_auto_lock_minutes, state.autoLockTimeoutMinutes),
                                showChevron = true
                            )
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.app_lock_settings_section_vault), Icons.Outlined.LockOpen, vaultTint()) }
            item {
                ModuleCard(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(clickable = onNavigateToVault) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_vault),
                            subtitle = stringResource(R.string.settings_vault_subtitle),
                            showChevron = true
                        )
                    }
                }
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

    if (showChangePin) {
        ChangePinDialog(
            appLockManager = appLockManager,
            onDismiss = { showChangePin = false },
            onSuccess = {
                showChangePin = false
                android.widget.Toast.makeText(context, R.string.app_lock_pin_changed, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAutoLockDialog) {
        AppDialog(
            title = { Text(stringResource(R.string.app_lock_auto_lock)) },
            text = {
                val modes = listOf(
                    Triple(
                        com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_SYSTEM,
                        R.string.app_lock_auto_lock_system,
                        R.string.app_lock_auto_lock_system_hint
                    ),
                    Triple(
                        com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_IMMEDIATE,
                        R.string.app_lock_auto_lock_immediate,
                        R.string.app_lock_auto_lock_immediate_hint
                    ),
                    Triple(
                        com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_TIMEOUT,
                        R.string.app_lock_auto_lock_timeout,
                        R.string.app_lock_auto_lock_timeout_hint
                    )
                )
                Column(Modifier.padding(vertical = 8.dp)) {
                    modes.forEach { (mode, title, hint) ->
                        AutoLockModeOption(state.autoLockMode, mode, title, hint) {
                            viewModel.setAutoLockMode(it)
                            showAutoLockDialog = false
                            Toast.makeText(context, R.string.app_lock_auto_lock_changed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismissRequest = { showAutoLockDialog = false }
        )
    }

    if (showAutoLockTimeoutDialog) {
        AppDialog(
            title = { Text(stringResource(R.string.app_lock_auto_lock_timeout_duration), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.padding(vertical = 8.dp)) {
                    listOf(1, 5, 15, 30).forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAutoLockTimeoutDialog = false
                                    viewModel.setAutoLockTimeoutMinutes(minutes)
                                    Toast.makeText(context, R.string.app_lock_auto_lock_changed, Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.app_lock_auto_lock_minutes, minutes),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            RadioButton(selected = state.autoLockTimeoutMinutes == minutes, onClick = null)
                        }
                    }
                }
            },
            onDismissRequest = { showAutoLockTimeoutDialog = false }
        )
    }

    if (showSetupPin) {
        SetupPinDialog(
            appLockManager = appLockManager,
            onDismiss = {
                showSetupPin = false
                if (!hasPin) appLockManager.setEnabled(false)
            },
            onSuccess = {
                showSetupPin = false
                android.widget.Toast.makeText(context, R.string.app_lock_pin_success, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showVerifyPin) {
        PinVerifyDialog(
            title = stringResource(R.string.app_lock_enter_old_pin),
            onVerify = appLockManager::verifyPin,
            onLockedOut = appLockManager::getLockoutRemainingMs,
            onDismiss = { showVerifyPin = false },
            onSuccess = {
                showVerifyPin = false
                scope.launch {
                    appLockManager.disableLock()
                    viewModel.setBiometricEnabled(false)
                }
                Toast.makeText(context, R.string.app_lock_disabled_toast, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showVerifyPinForBio) {
        PinVerifyDialog(
            title = stringResource(R.string.app_lock_biometric_enable_verify_title),
            onVerify = appLockManager::verifyPin,
            onLockedOut = appLockManager::getLockoutRemainingMs,
            onDismiss = { showVerifyPinForBio = false },
            onSuccess = {
                showVerifyPinForBio = false
                viewModel.setBiometricEnabled(true)
                Toast.makeText(context, R.string.app_lock_biometric_enabled_toast, Toast.LENGTH_SHORT).show()
            }
        )
    }
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
    com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_IMMEDIATE -> context.getString(R.string.app_lock_auto_lock_immediate)
    com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_TIMEOUT -> context.getString(R.string.app_lock_auto_lock_timeout)
    else -> context.getString(R.string.app_lock_auto_lock_system)
}
