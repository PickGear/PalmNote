package com.palmnote.ui.settings

import android.Manifest
import com.palmnote.domain.model.BillType
import com.palmnote.data.datastore.PreferencesManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.toComposeColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.app.Activity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.app.R
import com.palmnote.ui.theme.*

private val iconVisuals = mapOf(
    PreferencesManager.APP_ICON_GREEN_WHITE to Pair(Color(0xFF2D4A3E), Color.White),
    PreferencesManager.APP_ICON_BLACK_WHITE to Pair(Color(0xFF1A1A1A), Color.White),
    PreferencesManager.APP_ICON_WHITE_BLACK to Pair(Color.White, Color(0xFF1A1A1A)),
    PreferencesManager.APP_ICON_WHITE_GREEN to Pair(Color.White, Color(0xFF2D4A3E)),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showThemePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showStartPagePicker by remember { mutableStateOf(false) }
    var showBillTypePicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var pendingIconStyle by remember { mutableStateOf<String?>(null) }
    var showCalendarPermissionDialog by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    var customColor by remember(showColorPicker) { mutableStateOf(state.switchColor.removePrefix("#")) }
    var customColorError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.resultMessage) {
        state.resultMessage?.let {
            snackbarHostState.showSnackbar(it)
            kotlinx.coroutines.delay(100)
            viewModel.clearResult()
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) viewModel.setCalendarSyncEnabled(true)
        else {
            (context as? Activity)?.let { ctx ->
                if (!ActivityCompat.shouldShowRequestPermissionRationale(ctx, Manifest.permission.WRITE_CALENDAR)) {
                    permissionPermanentlyDenied = true
                } else {
                    showCalendarPermissionDialog = true
                }
            }
        }
    }

    val themes = mapOf("SYSTEM" to stringResource(R.string.settings_follow_system), "LIGHT" to stringResource(R.string.settings_theme_light), "DARK" to stringResource(R.string.settings_theme_dark))
    val languageLabels = mapOf("SYSTEM" to stringResource(R.string.settings_follow_system), "zh" to stringResource(R.string.settings_language_chinese), "en" to stringResource(R.string.settings_language_english))
    val startPages = mapOf("dashboard" to stringResource(R.string.settings_home), "asset" to stringResource(R.string.settings_items), "bill" to stringResource(R.string.bill_title), "life" to stringResource(R.string.life_title))
    val billTypes = mapOf(BillType.EXPENSE.value to stringResource(R.string.settings_bill_expense), BillType.INCOME.value to stringResource(R.string.settings_bill_income))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_appearance),
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
            item { SectionHeader(stringResource(R.string.settings_appearance), Icons.Default.Palette, LifePlan) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingRow(clickable = { showThemePicker = true }) {
                        SettingRowContent(title = stringResource(R.string.settings_dark_mode), subtitle = stringResource(R.string.settings_dark_mode_subtitle), value = themes[state.themeMode], showChevron = true)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = { showLanguagePicker = true }) {
                        SettingRowContent(title = stringResource(R.string.settings_language), subtitle = stringResource(R.string.settings_language_subtitle), value = languageLabels[state.language], showChevron = true)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val switchColor = state.switchColor.toComposeColor(PrimaryGreen)
                    SettingRow(clickable = { showColorPicker = true }) {
                        SettingRowContent(title = stringResource(R.string.settings_switch_color), subtitle = stringResource(R.string.settings_switch_color_subtitle))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(switchColor).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = { showIconPicker = true }) {
                        SettingRowContent(title = stringResource(R.string.settings_app_icon), subtitle = stringResource(R.string.settings_app_icon_subtitle))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (bgColor, fgColor) = iconVisuals[state.appIconStyle] ?: iconVisuals[PreferencesManager.DEFAULT_APP_ICON_STYLE]!!
                            Box(
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(bgColor).border(0.5.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground_bw),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(fgColor),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_general), Icons.Default.Tune, LifeRecord) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingRow(clickable = { showStartPagePicker = true }) {
                        SettingRowContent(title = stringResource(R.string.settings_default_start_page), value = startPages[state.defaultStartPage], showChevron = true)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = { showBillTypePicker = true }) {
                        SettingRowContent(title = stringResource(R.string.settings_default_bill_type), value = billTypes[state.defaultBillType.value], showChevron = true)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow {
                        SettingRowContent(title = stringResource(R.string.settings_budget_reminder))
                        CapsuleSwitch(checked = state.budgetReminderEnabled, onCheckedChange = { viewModel.setBudgetReminderEnabled(it) }, checkedTrackColor = LocalSwitchColor.current)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow {
                        SettingRowContent(title = stringResource(R.string.settings_calendar_sync), subtitle = stringResource(R.string.settings_calendar_sync_subtitle))
                        CapsuleSwitch(checked = state.calendarSyncEnabled, onCheckedChange = { enabled ->
                            if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                                showCalendarPermissionDialog = true
                            } else { viewModel.setCalendarSyncEnabled(enabled) }
                        }, checkedTrackColor = LocalSwitchColor.current)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showThemePicker) {
        val themeOptions = listOf(Triple("SYSTEM", stringResource(R.string.settings_follow_system), Icons.Outlined.BrightnessAuto), Triple("LIGHT", stringResource(R.string.settings_theme_light), Icons.Outlined.WbSunny), Triple("DARK", stringResource(R.string.settings_theme_dark), Icons.Outlined.Brightness3))
        val themeColors = mapOf("SYSTEM" to InfoBlue, "LIGHT" to AccentOrange, "DARK" to LifePlan)
        AppDialog(
            onDismissRequest = { showThemePicker = false }, title = { Text(stringResource(R.string.settings_select_dark_mode), fontWeight = FontWeight.Bold) },
            text = { Column { themeOptions.forEach { (mode, label, icon) ->
                Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { viewModel.setThemeMode(mode); showThemePicker = false }.padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val tint = themeColors[mode] ?: MaterialTheme.colorScheme.primary
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected = state.themeMode == mode, onClick = { viewModel.setThemeMode(mode); showThemePicker = false }, colors = RadioButtonDefaults.colors(selectedColor = LocalSwitchColor.current))
                }
                if (mode != themeOptions.last().first) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
            } } },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showLanguagePicker) {
        val langOptions = listOf(Triple("SYSTEM", stringResource(R.string.settings_follow_system), Icons.Outlined.BrightnessAuto), Triple("zh", stringResource(R.string.settings_language_chinese), Icons.Outlined.Translate), Triple("en", "English", Icons.Outlined.Translate))
        val langColors = mapOf("SYSTEM" to InfoBlue, "zh" to AccentOrange, "en" to StatusActive)
        AppDialog(
            onDismissRequest = { showLanguagePicker = false }, title = { Text(stringResource(R.string.settings_select_language), fontWeight = FontWeight.Bold) },
            text = { Column { langOptions.forEach { (lang, label, icon) ->
                Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { viewModel.setLanguage(lang); showLanguagePicker = false }.padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val tint = langColors[lang] ?: MaterialTheme.colorScheme.primary
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected = state.language == lang, onClick = { viewModel.setLanguage(lang); showLanguagePicker = false }, colors = RadioButtonDefaults.colors(selectedColor = LocalSwitchColor.current))
                }
                if (lang != langOptions.last().first) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
            } } },
            confirmButton = { TextButton(onClick = { showLanguagePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showColorPicker) {
        val presetColors = listOf("#2D4A3E", "#34A853", "#4285F4", "#EA4335", "#FBBC04", "#FF6D01", "#AB47BC", "#1E88E5", "#00BCD4", "#66BB6A")
        AppDialog(
            onDismissRequest = { showColorPicker = false }, title = { Text(stringResource(R.string.settings_color_pick_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_preset_color), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presetColors.forEach { color ->
                            val c = color.toComposeColor(Color.Gray)
                            val isSelected = state.switchColor == color
                            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(c).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.setSwitchColor(color) }.then(if (isSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                                if (isSelected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.settings_custom_color), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = customColor, onValueChange = { v -> customColor = v.filter { it.isLetterOrDigit() }.take(6); customColorError = false }, label = { Text(stringResource(R.string.settings_hex_color), fontWeight = FontWeight.Bold) }, prefix = { Text("#") }, isError = customColorError, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
                        val previewColor = "#$customColor".toComposeColor(Color.Gray)
                        val isCustomSelected = state.switchColor == "#$customColor" && customColor.length == 6
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(previewColor).then(if (isCustomSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (customColor.length == 6) viewModel.setSwitchColor("#$customColor"); else customColorError = true }, contentAlignment = Alignment.Center) {
                            if (isCustomSelected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColorPicker = false }) { Text(stringResource(R.string.settings_color_done), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showStartPagePicker) {
        val startPageColors = mapOf("dashboard" to ModuleHome, "asset" to ModuleItem, "bill" to ModuleBill, "life" to ModuleLife)
        val startPageOptions = listOf(Triple("dashboard", stringResource(R.string.settings_home), Icons.Outlined.Home), Triple("asset", stringResource(R.string.settings_items), Icons.Outlined.Inventory2), Triple("bill", stringResource(R.string.bill_title), Icons.Outlined.AccountBalanceWallet), Triple("life", stringResource(R.string.life_title), Icons.Outlined.FavoriteBorder))
        AppDialog(
            onDismissRequest = { showStartPagePicker = false }, title = { Text(stringResource(R.string.settings_select_start_page), fontWeight = FontWeight.Bold) },
            text = { Column { startPageOptions.forEach { (route, label, icon) ->
                val tint = startPageColors[route] ?: MaterialTheme.colorScheme.primary
                Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { viewModel.setDefaultStartPage(route); showStartPagePicker = false }.padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected = state.defaultStartPage == route, onClick = { viewModel.setDefaultStartPage(route); showStartPagePicker = false }, colors = RadioButtonDefaults.colors(selectedColor = LocalSwitchColor.current))
                }
                if (route != startPageOptions.last().first) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
            } } },
            confirmButton = { TextButton(onClick = { showStartPagePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showBillTypePicker) {
        val billTypeExpense = stringResource(R.string.settings_bill_expense)
        val billTypeIncome = stringResource(R.string.settings_bill_income)
        val billTypeColors = mapOf(BillType.EXPENSE.value to ExpenseRed, BillType.INCOME.value to IncomeGreen)
        val billTypeOptions = listOf(Triple(BillType.EXPENSE.value, billTypeExpense, Icons.AutoMirrored.Outlined.TrendingDown), Triple(BillType.INCOME.value, billTypeIncome, Icons.AutoMirrored.Outlined.TrendingUp))
        AppDialog(
            onDismissRequest = { showBillTypePicker = false }, title = { Text(stringResource(R.string.settings_select_bill_type), fontWeight = FontWeight.Bold) },
            text = { Column { billTypeOptions.forEach { (type, label, icon) ->
                val tint = billTypeColors[type] ?: AccentOrange
                Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { viewModel.setDefaultBillType(type); showBillTypePicker = false }.padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected = state.defaultBillType.value == type, onClick = { viewModel.setDefaultBillType(type); showBillTypePicker = false }, colors = RadioButtonDefaults.colors(selectedColor = LocalSwitchColor.current))
                }
                if (type != billTypeOptions.last().first) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
            } } },
            confirmButton = { TextButton(onClick = { showBillTypePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showIconPicker) {
        val iconLabels = mapOf(
            PreferencesManager.APP_ICON_GREEN_WHITE to stringResource(R.string.settings_icon_green_white),
            PreferencesManager.APP_ICON_BLACK_WHITE to stringResource(R.string.settings_icon_black_white),
            PreferencesManager.APP_ICON_WHITE_BLACK to stringResource(R.string.settings_icon_white_black),
            PreferencesManager.APP_ICON_WHITE_GREEN to stringResource(R.string.settings_icon_white_green),
        )
        AppDialog(
            onDismissRequest = { showIconPicker = false },
            title = { Text(stringResource(R.string.settings_select_app_icon), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_app_icon_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        iconVisuals.forEach { (key, visual) ->
                            val isSelected = state.appIconStyle == key
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(visual.first)
                                        .clickable {
                                            if (key != state.appIconStyle) {
                                                showIconPicker = false
                                                pendingIconStyle = key
                                            }
                                        }
                                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier.border(1.5.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(14.dp))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground_bw),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(visual.second),
                                        modifier = Modifier.size(60.dp)
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(iconLabels[key] ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconPicker = false }) {
                    Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    pendingIconStyle?.let { style ->
        AppDialog(
            onDismissRequest = { pendingIconStyle = null },
            title = { Text(stringResource(R.string.settings_icon_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_icon_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val activityContext = context as? Activity
                    viewModel.setAppIconStyle(style, activityContext)
                    showIconPicker = false
                    pendingIconStyle = null
                }) { Text(stringResource(R.string.settings_confirm), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingIconStyle = null }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showCalendarPermissionDialog) {
        AppDialog(
            onDismissRequest = { showCalendarPermissionDialog = false },
            title = { Text(stringResource(R.string.settings_calendar_permission_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_calendar_permission_text), fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = { showCalendarPermissionDialog = false; calendarPermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR)) }) { Text(stringResource(R.string.settings_calendar_permission_action), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCalendarPermissionDialog = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (permissionPermanentlyDenied) {
        AppDialog(
            onDismissRequest = { permissionPermanentlyDenied = false },
            title = { Text(stringResource(R.string.settings_calendar_permission_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_calendar_permission_denied), fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    permissionPermanentlyDenied = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }) { Text(stringResource(R.string.settings_calendar_permission_action), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { permissionPermanentlyDenied = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}
