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
import com.palmnote.app.R
import com.palmnote.ui.components.*
import com.palmnote.ui.components.SectionHeader
import com.palmnote.ui.components.ChoiceDialog
import com.palmnote.ui.components.SettingRowContent
import com.palmnote.ui.components.SettingRow
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showBirthdayAdvancePicker by remember { mutableStateOf(false) }
    var showAnniversaryAdvancePicker by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showNotificationDeniedDialog by remember { mutableStateOf(false) }
    // 两个提醒时间选择共用同一个参数化对话框
    var pickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }
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
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.settings_daily_reminder),
                            subtitle = stringResource(R.string.settings_daily_reminder_subtitle)
                        )
                        CapsuleSwitch(
                            checked = state.dailyReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 33) {
                                    showNotificationPermissionDialog = true
                                } else {
                                    viewModel.setDailyReminderEnabled(enabled)
                                }
                            },
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.dailyReminderEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(clickable = { pickerTarget = TimePickerTarget(state.dailyReminderHour, state.dailyReminderMinute) { h, m -> viewModel.setDailyReminderTime(h, m) } }) {
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
                        CapsuleSwitch(
                            checked = state.billReminderEnabled,
                            onCheckedChange = { viewModel.setBillReminderEnabled(it) },
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.billReminderEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(clickable = { pickerTarget = TimePickerTarget(state.billReminderHour, state.billReminderMinute) { h, m -> viewModel.setBillReminderTime(h, m) } }) {
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

    pickerTarget?.let { target ->
        val timePickerState = rememberTimePickerState(
            initialHour = target.initialHour,
            initialMinute = target.initialMinute,
            is24Hour = true
        )
        AppDialog(
            onDismissRequest = { pickerTarget = null },
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
                    target.onConfirm(timePickerState.hour, timePickerState.minute)
                    pickerTarget = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    // 提前天数选择：两个对话框结构相同，仅色与图标不同，用 ChoiceDialog 统一
    val advanceDaysOptions = listOf(1, 2, 3, 5, 7, 14)
    if (showBirthdayAdvancePicker) {
        ChoiceDialog(
            title = stringResource(R.string.settings_birthday_advance_title),
            options = advanceDaysOptions,
            selected = state.birthdayReminderAdvanceDays,
            optionLabel = { stringResource(R.string.settings_days, it) },
            optionIcon = { Icons.Outlined.Cake },
            optionTint = { AccentOrange },
            onSelect = { viewModel.setBirthdayReminderAdvanceDays(it) },
            onDismiss = { showBirthdayAdvancePicker = false }
        )
    }

    if (showAnniversaryAdvancePicker) {
        ChoiceDialog(
            title = stringResource(R.string.settings_anniversary_advance_title),
            options = advanceDaysOptions,
            selected = state.anniversaryReminderAdvanceDays,
            optionLabel = { stringResource(R.string.settings_days, it) },
            optionIcon = { Icons.Outlined.FavoriteBorder },
            optionTint = { ErrorLight },
            onSelect = { viewModel.setAnniversaryReminderAdvanceDays(it) },
            onDismiss = { showAnniversaryAdvancePicker = false }
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

/** 时间选择对话框的目标：初始值 + 确认回调（每日提醒/记账提醒共用一个对话框）。 */
private data class TimePickerTarget(
    val initialHour: Int,
    val initialMinute: Int,
    val onConfirm: (Int, Int) -> Unit
)
