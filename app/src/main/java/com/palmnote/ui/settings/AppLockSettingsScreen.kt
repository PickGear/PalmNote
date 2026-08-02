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
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appLockManager = viewModel.appLockManager
    val context = LocalContext.current

    var showChangePin by remember { mutableStateOf(false) }
    var showSetupPin by remember { mutableStateOf(false) }
    var showForgotPin by remember { mutableStateOf(false) }

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
                                    appLockManager.setEnabled(false)
                                    updateLockEnabled()
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
                                    onCheckedChange = { viewModel.setBiometricEnabled(it) },
                                    checkedTrackColor = LocalSwitchColor.current
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(clickable = { showForgotPin = true }) {
                            SettingRowContent(
                                title = stringResource(R.string.app_lock_forgot_pin_title_settings),
                                subtitle = stringResource(R.string.app_lock_forgot_pin_subtitle)
                            )
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

    if (showForgotPin) {
        AppDialog(
            onDismissRequest = { showForgotPin = false },
            title = { Text(stringResource(R.string.app_lock_forgot_pin), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.app_lock_forgot_pin_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    appLockManager.clearPin()
                    appLockManager.setEnabled(false)
                    showForgotPin = false
                    updateLockEnabled()
                }) { Text(stringResource(R.string.app_lock_forgot_pin_action), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPin = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) }
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
                        if (currentPin.length < 6) {
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
                                            appLockManager.setPin(pin)
                                            appLockManager.setEnabled(true)
                                            onSuccess()
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
