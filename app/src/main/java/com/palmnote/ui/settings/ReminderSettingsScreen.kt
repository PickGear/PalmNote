package com.palmnote.ui.settings

import androidx.compose.foundation.layout.*
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import android.app.Activity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.R
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDailyTimePicker by remember { mutableStateOf(false) }
    var showBillTimePicker by remember { mutableStateOf(false) }
    var showBirthdayAdvancePicker by remember { mutableStateOf(false) }
    var showAnniversaryAdvancePicker by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showNotificationDeniedDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setDailyReminderEnabled(true)
        else {
            (context as? android.app.Activity)?.let { ctx ->
                if (!ActivityCompat.shouldShowRequestPermissionRationale(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationDeniedDialog = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_reminder),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader(stringResource(R.string.settings_reminder_daily_section), Icons.Outlined.Notifications, AccentOrange) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.settings_daily_reminder),
                            subtitle = stringResource(R.string.settings_daily_reminder_subtitle)
                        )
                        XiaomiSwitch(checked = state.dailyReminderEnabled, onCheckedChange = { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33) { showNotificationPermissionDialog = true } else { viewModel.setDailyReminderEnabled(enabled) } }, checkedTrackColor = LocalSwitchColor.current)
                    }
                    if (state.dailyReminderEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(clickable = { showDailyTimePicker = true }) {
                            SettingRowContent(
                                title = stringResource(R.string.settings_reminder_time),
                                subtitle = stringResource(R.string.settings_reminder_time_subtitle),
                                value = String.format("%02d:%02d", state.dailyReminderHour, state.dailyReminderMinute),
                                showChevron = true
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.settings_bill_reminder),
                            subtitle = stringResource(R.string.settings_bill_reminder_subtitle)
                        )
                        XiaomiSwitch(checked = state.billReminderEnabled, onCheckedChange = { viewModel.setBillReminderEnabled(it) }, checkedTrackColor = LocalSwitchColor.current)
                    }
                    if (state.billReminderEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(clickable = { showBillTimePicker = true }) {
                            SettingRowContent(
                                title = stringResource(R.string.settings_reminder_time),
                                subtitle = stringResource(R.string.settings_reminder_time_bill_subtitle),
                                value = String.format("%02d:%02d", state.billReminderHour, state.billReminderMinute),
                                showChevron = true
                            )
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_reminder_advance_section), Icons.Outlined.Event, LifePlan) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingRow(clickable = { showBirthdayAdvancePicker = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_birthday_advance),
                            subtitle = stringResource(R.string.settings_birthday_advance_subtitle),
                            value = stringResource(R.string.settings_days, state.birthdayReminderAdvanceDays),
                            showChevron = true
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = { showAnniversaryAdvancePicker = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_anniversary_advance),
                            subtitle = stringResource(R.string.settings_anniversary_advance_subtitle),
                            value = stringResource(R.string.settings_days, state.anniversaryReminderAdvanceDays),
                            showChevron = true
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showDailyTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.dailyReminderHour,
            initialMinute = state.dailyReminderMinute,
            is24Hour = true
        )
        AppDialog(
            onDismissRequest = { showDailyTimePicker = false },
            title = { Text(stringResource(R.string.settings_select_reminder_time), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDailyReminderTime(timePickerState.hour, timePickerState.minute)
                    showDailyTimePicker = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDailyTimePicker = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showBillTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.billReminderHour,
            initialMinute = state.billReminderMinute,
            is24Hour = true
        )
        AppDialog(
            onDismissRequest = { showBillTimePicker = false },
            title = { Text(stringResource(R.string.settings_select_reminder_time), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBillReminderTime(timePickerState.hour, timePickerState.minute)
                    showBillTimePicker = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBillTimePicker = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showBirthdayAdvancePicker) {
        val options = listOf(1, 2, 3, 5, 7, 14)
        AppDialog(
            onDismissRequest = { showBirthdayAdvancePicker = false },
            title = { Text(stringResource(R.string.settings_birthday_advance_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { days ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { viewModel.setBirthdayReminderAdvanceDays(days); showBirthdayAdvancePicker = false }.padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentOrange.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Cake, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_days, days), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            RadioButton(selected = state.birthdayReminderAdvanceDays == days, onClick = { viewModel.setBirthdayReminderAdvanceDays(days); showBirthdayAdvancePicker = false }, colors = RadioButtonDefaults.colors(selectedColor = LocalSwitchColor.current))
                        }
                        if (days != options.last()) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBirthdayAdvancePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showAnniversaryAdvancePicker) {
        val options = listOf(1, 2, 3, 5, 7, 14)
        AppDialog(
            onDismissRequest = { showAnniversaryAdvancePicker = false },
            title = { Text(stringResource(R.string.settings_anniversary_advance_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { days ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { viewModel.setAnniversaryReminderAdvanceDays(days); showAnniversaryAdvancePicker = false }.padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(ErrorLight.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = ErrorLight, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_days, days), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            RadioButton(selected = state.anniversaryReminderAdvanceDays == days, onClick = { viewModel.setAnniversaryReminderAdvanceDays(days); showAnniversaryAdvancePicker = false }, colors = RadioButtonDefaults.colors(selectedColor = LocalSwitchColor.current))
                        }
                        if (days != options.last()) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAnniversaryAdvancePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showNotificationPermissionDialog) {
        AppDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            title = { Text(stringResource(R.string.settings_notification_permission_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_notification_permission_text)) },
            confirmButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false; notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.settings_notification_permission_allow), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showNotificationDeniedDialog) {
        AppDialog(
            onDismissRequest = { showNotificationDeniedDialog = false },
            title = { Text(stringResource(R.string.settings_notification_denied_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_notification_denied_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationDeniedDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    })
                }) {
                    Text(stringResource(R.string.settings_notification_denied_go_settings), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDeniedDialog = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }
}
