package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.palmnote.ui.lock.PinDotsDisplay
import com.palmnote.ui.lock.PinKeyboard
import com.palmnote.ui.lock.isBiometricAvailable
import com.palmnote.ui.lock.showBiometricPrompt
import com.palmnote.ui.theme.*
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appLockManager = viewModel.appLockManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChangePin by remember { mutableStateOf(false) }
    var showSetupPin by remember { mutableStateOf(false) }
    var showVerifyPin by remember { mutableStateOf(false) }

    val bioAvailable = remember { isBiometricAvailable(context) }
    var isLockEnabled by remember { mutableStateOf(appLockManager.isLockEnabled()) }
    var hasPin by remember { mutableStateOf(appLockManager.hasPin()) }

    fun updateLockEnabled() {
        isLockEnabled = appLockManager.isLockEnabled()
        hasPin = appLockManager.hasPin()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text(stringResource(R.string.app_lock_settings_title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ModuleSettings) },
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
                                        updateLockEnabled()
                                    }
                                } else {
                                    showVerifyPin = true
                                }
                            },
                            checkedTrackColor = LocalSwitchColor.current
                        )
                    }
                }
            }

            if (isLockEnabled) {
                item {
                    ModuleCard(modifier = Modifier.fillMaxWidth()) {
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
                                            showBiometricPrompt(context) { success ->
                                                if (success) viewModel.setBiometricEnabled(true)
                                                else Toast.makeText(context, R.string.app_lock_biometric_verify_fail, Toast.LENGTH_SHORT).show()
                                            }
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

    if (showSetupPin) {
        SetupPinDialog(
            appLockManager = appLockManager,
            onDismiss = {
                showSetupPin = false
                if (!hasPin) appLockManager.setEnabled(false)
                updateLockEnabled()
            },
            onSuccess = {
                showSetupPin = false
                updateLockEnabled()
                android.widget.Toast.makeText(context, R.string.app_lock_pin_success, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showVerifyPin) {
        VerifyPinDialog(
            appLockManager = appLockManager,
            onDismiss = { showVerifyPin = false },
            onSuccess = {
                showVerifyPin = false
                scope.launch {
                    appLockManager.disableLock()
                    viewModel.setBiometricEnabled(false)
                    updateLockEnabled()
                }
                Toast.makeText(context, R.string.app_lock_disabled_toast, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun VerifyPinDialog(
    appLockManager: AppLockManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pinWrongText = stringResource(R.string.app_lock_pin_wrong)
    val lockedOutText = stringResource(R.string.app_lock_locked_out)

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.app_lock_enter_old_pin),
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
                PinDotsDisplay(pin.length)
                Spacer(modifier = Modifier.height(8.dp))
                PinKeyboard(
                    onDigitClick = { digit ->
                        if (!verifying && pin.length < 6) {
                            pin += digit
                            error = ""
                            if (pin.length == 6) {
                                verifying = true
                                val input = pin
                                pin = ""
                                scope.launch {
                                    if (appLockManager.verifyPin(input)) {
                                        onSuccess()
                                    } else {
                                        verifying = false
                                        error = if (appLockManager.getLockoutRemainingMs() > 0) lockedOutText else pinWrongText
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
                        if (!saving && currentPin.length < 6) {
                            when (step) {
                                1 -> pin += digit
                                else -> confirmPin += digit
                            }
                            error = ""
                            if (currentPin.length + 1 == 6) {
                                when (step) {
                                    1 -> step = 2
                                    else -> {
                                        if (pin == confirmPin) {
                                            saving = true
                                            scope.launch {
                                                appLockManager.setPin(pin, enable = true)
                                                onSuccess()
                                            }
                                        } else {
                                            error = pinMismatchText
                                            confirmPin = ""
                                            step = 2
                                            pin = ""
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
