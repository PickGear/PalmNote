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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.app.R
import com.palmnote.ui.components.*
import com.palmnote.ui.components.SectionHeader
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
    var showAdvancePicker by remember { mutableStateOf(false) }
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
            // ── 什么时候提醒 ──
            // 全部提醒共用一次每日检查：所以「时间」只有一行（原来"每日/记账"两行同名，其中记账那行
            // 22:00 根本没有任何代码读过，是死设置）；「提前几天」也只有一行（原先生日/纪念日/保质期
            // 三行几乎一模一样，用户分不清谁是谁）。
            item { SectionHeader(stringResource(R.string.settings_reminder_time_section), Icons.Outlined.Schedule, DopamineAmber) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingRow(clickable = { pickerTarget = TimePickerTarget(state.dailyReminderHour, state.dailyReminderMinute) { h, m -> viewModel.setDailyReminderTime(h, m) } }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_reminder_time),
                            subtitle = stringResource(R.string.settings_reminder_time_subtitle),
                            value = String.format(java.util.Locale.US, "%02d:%02d", state.dailyReminderHour, state.dailyReminderMinute),
                            showChevron = true
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = { showAdvancePicker = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_reminder_advance),
                            subtitle = stringResource(R.string.settings_reminder_advance_subtitle),
                            value = if (state.reminderAdvanceDays == 0) {
                                stringResource(R.string.settings_advance_same_day)
                            } else {
                                pluralStringResource(
                                    R.plurals.settings_advance_days,
                                    state.reminderAdvanceDays,
                                    state.reminderAdvanceDays
                                )
                            },
                            showChevron = true
                        )
                    }
                }
            }

            // ── 提醒什么 ──
            // 只列真正有开关的三条。生日/纪念日不在这里给开关：它们的开关在生活模块每条条目自己的
            // 编辑页里（模板级），在这里再加一个就是两处控制同一件事。
            item {
                SectionHeader(
                    stringResource(R.string.settings_reminder_content_section),
                    Icons.Outlined.NotificationsActive,
                    DopamineSky
                )
            }
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
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.settings_asset_expiry_reminder),
                            subtitle = stringResource(R.string.settings_asset_expiry_reminder_subtitle)
                        )
                        CapsuleSwitch(
                            checked = state.assetExpiryReminderEnabled,
                            onCheckedChange = { viewModel.setAssetExpiryReminderEnabled(it) },
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.settings_birthday_anniversary_reminder),
                            subtitle = stringResource(R.string.settings_birthday_anniversary_reminder_subtitle)
                        )
                    }
                }
            }

            // ── 通知渠道 ──
            item { SectionHeader(stringResource(R.string.settings_reminder_channel_section), Icons.Outlined.Tune, DopamineSky) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingRow(clickable = { openSystemNotificationSettings(context) }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_system_notification),
                            subtitle = stringResource(R.string.settings_system_notification_subtitle),
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

    // 提前天数：直接填数字（原来是 1/2/3/5/7/14 的固定档，用户要能填任意天数）
    if (showAdvancePicker) {
        AdvanceDaysDialog(
            currentDays = state.reminderAdvanceDays,
            onConfirm = { viewModel.setReminderAdvanceDays(it); showAdvancePicker = false },
            onDismiss = { showAdvancePicker = false }
        )
    }

    NotificationPermissionDialogs(
        showRequestDialog = showNotificationPermissionDialog,
        showDeniedDialog = showNotificationDeniedDialog,
        onDismissRequest = { showNotificationPermissionDialog = false },
        onAllow = {
            showNotificationPermissionDialog = false
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onDismissDenied = { showNotificationDeniedDialog = false }
    )
}

/**
 * 通知权限的两个对话框（申请 / 被拒后去系统设置）。单独提出来：它自带 LocalContext，
 * 也不让这两个分支把 [ReminderSettingsScreen] 的圈复杂度顶到阈值上。
 */
@Composable
private fun NotificationPermissionDialogs(
    showRequestDialog: Boolean,
    showDeniedDialog: Boolean,
    onDismissRequest: () -> Unit,
    onAllow: () -> Unit,
    onDismissDenied: () -> Unit
) {
    if (showRequestDialog) {
        AppDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(R.string.settings_notification_permission_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_notification_permission_text)) },
            confirmButton = {
                TextButton(onClick = onAllow) {
                    Text(stringResource(R.string.settings_notification_permission_allow), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showDeniedDialog) {
        val context = LocalContext.current
        AppDialog(
            onDismissRequest = onDismissDenied,
            title = { Text(stringResource(R.string.settings_notification_denied_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_notification_denied_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDismissDenied()
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    })
                }) {
                    Text(stringResource(R.string.settings_notification_denied_go_settings), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDenied) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) }
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

/** 提前提醒天数的允许范围：够用，又不至于填出荒唐值。0 = 只在当天提醒。 */
private val ADVANCE_DAYS_RANGE = 0..365

/**
 * 提前提醒天数：**直接填数字**。
 *
 * 原来是 1/2/3/5/7/14 的固定档（`ChoiceDialog`），但 10 天、30 天这种真实需求都被逼着往最近一档凑。
 * 只接受数字、上限 3 位；越界时确认键禁用、下面给出范围提示。
 * 0 是有效值（只在当天提醒），别当成"没填"。
 *
 * 打开就聚焦并**全选**已有的数字：否则用户接着打字是追加（3 → 312），越界后确认键变灰，
 * 看上去像"填不进去"。全选之后直接输入就是替换。
 */
@Composable
private fun AdvanceDaysDialog(
    currentDays: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = currentDays.toString()
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val days = text.text.toIntOrNull()
    val valid = days != null && days in ADVANCE_DAYS_RANGE

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_advance_title), fontWeight = FontWeight.Bold) },
        text = {
            // 放在弹窗内容里：此时输入框已经挂上，requestFocus 才不会抛"FocusRequester 未初始化"
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    if (input.text.length <= 3 && input.text.all { it.isDigit() }) text = input
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text(stringResource(R.string.settings_reminder_advance_field)) },
                suffix = { Text(stringResource(R.string.settings_reminder_advance_unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = !valid,
                supportingText = { Text(stringResource(R.string.settings_reminder_advance_range)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { days?.let { onConfirm(it) } }, enabled = valid) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}

/**
 * 跳到系统里**本应用的通知设置页**（按 channel 一行一个开关那一页）。
 * 「开不开」本来就该归系统管，App 这边不再放一份可能与系统状态不一致的开关。
 * 这页之所以能用，是因为 channel 已经**按功能拆开**（见 `NotificationHelper.Channel`）：
 * 每一行的名字都能对上 App 里的一个功能，想只关掉物品到期提醒就真的能做到。
 */
private fun openSystemNotificationSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
    }
    context.startActivity(intent)
}
