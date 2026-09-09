package com.palmnote.ui.settings

import android.Manifest
import android.widget.Toast
import com.palmnote.domain.model.BillType
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.wallpaper.WallpaperPresets
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import com.palmnote.ui.components.InlineColorPicker
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

private val iconVisuals = linkedMapOf(
    PreferencesManager.APP_ICON_GREEN_WHITE to Pair(Color(0xFF2D4A3E), Color.White),
    PreferencesManager.APP_ICON_BLACK_WHITE to Pair(Color.Black, Color.White),
    PreferencesManager.APP_ICON_CYAN_WHITE to Pair(Color(0xFF0891B2), Color.White),
    PreferencesManager.APP_ICON_WHITE_BLACK to Pair(Color.White, Color.Black),
)

private fun wallpaperLabelRes(id: String): Int = when (id) {
    "ocean" -> R.string.wallpaper_ocean
    "sunset" -> R.string.wallpaper_sunset
    "forest" -> R.string.wallpaper_forest
    "lavender" -> R.string.wallpaper_lavender
    "midnight" -> R.string.wallpaper_midnight
    "peach" -> R.string.wallpaper_peach
    "color" -> R.string.wallpaper_color
    "custom" -> R.string.wallpaper_custom
    else -> R.string.wallpaper_none
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    var showThemePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showThemeColorPicker by remember { mutableStateOf(false) }
    var showWallpaperPicker by remember { mutableStateOf(false) }
    var showStartPagePicker by remember { mutableStateOf(false) }
    var showBillTypePicker by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var pendingIconStyle by remember { mutableStateOf<String?>(null) }
    var showCalendarPermissionDialog by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

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
                    val currentThemeColor = ThemePackages.getById(state.themeColor)
                    val themeColorPreview = if (isDarkTheme) currentThemeColor.darkPrimary else currentThemeColor.lightPrimary
                    SettingRow(clickable = { showThemeColorPicker = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_theme_color),
                            subtitle = stringResource(R.string.settings_theme_color_subtitle)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(themeColorPreview)
                                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = { showWallpaperPicker = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_wallpaper),
                            subtitle = stringResource(R.string.settings_wallpaper_subtitle)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(wallpaperLabelRes(state.wallpaperStyle)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow(clickable = { showIconPicker = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.settings_app_icon),
                            subtitle = stringResource(R.string.settings_app_icon_subtitle)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (bgColor, fgColor) = iconVisuals[state.appIconStyle]
                                ?: iconVisuals[PreferencesManager.DEFAULT_APP_ICON_STYLE]!!
                            Box(
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(bgColor)
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
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
                        CapsuleSwitch(
                            checked = state.budgetReminderEnabled,
                            onCheckedChange = { viewModel.setBudgetReminderEnabled(it) },
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingRow {
                        SettingRowContent(title = stringResource(R.string.settings_calendar_sync), subtitle = stringResource(R.string.settings_calendar_sync_subtitle))
                        CapsuleSwitch(checked = state.calendarSyncEnabled, onCheckedChange = { enabled ->
                            if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                                showCalendarPermissionDialog = true
                            } else { viewModel.setCalendarSyncEnabled(enabled) }
                        }, checkedTrackColor = MaterialTheme.colorScheme.primary)
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
                    RadioButton(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode); showThemePicker = false },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
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
                    RadioButton(
                        selected = state.language == lang,
                        onClick = { viewModel.setLanguage(lang); showLanguagePicker = false },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                }
                if (lang != langOptions.last().first) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
            } } },
            confirmButton = { TextButton(onClick = { showLanguagePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showThemeColorPicker) {
        val themeColors = ThemePackages.packages.map { pkg ->
            pkg.id to (if (isDarkTheme) pkg.darkPrimary else pkg.lightPrimary)
        }
        AppDialog(
            onDismissRequest = { showThemeColorPicker = false },
            title = { Text(stringResource(R.string.settings_select_theme_color), fontWeight = FontWeight.Bold) },
            text = {
                InlineColorPicker(
                    presetColors = themeColors,
                    selectedColor = state.themeColor,
                    onColorSelected = { color ->
                        if (color != null) {
                            viewModel.setThemeColor(color)
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showThemeColorPicker = false }) {
                    Text(stringResource(R.string.done), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showWallpaperPicker) {
        val context = LocalContext.current
        val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    val file = java.io.File(context.filesDir, "wallpaper_custom.jpg")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    viewModel.setWallpaperCustomUri(file.absolutePath)
                    viewModel.setWallpaperStyle("custom")
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.wallpaper_load_failed, Toast.LENGTH_SHORT).show()
                }
                showWallpaperPicker = false
            }
        }
        var customWallpaperColor by remember(showWallpaperPicker) {
            mutableStateOf(if (state.wallpaperCustomColor.startsWith("#")) state.wallpaperCustomColor.removePrefix("#") else "")
        }
        AppDialog(
            onDismissRequest = { showWallpaperPicker = false },
            title = { Text(stringResource(R.string.settings_select_wallpaper), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // ── 预设色块网格 ──
                    Text(
                        stringResource(R.string.wallpaper_preset),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    val gridItems = buildList {
                        add(Triple("none", stringResource(R.string.wallpaper_none), MaterialTheme.colorScheme.surface))
                        WallpaperPresets.presets.forEach { preset ->
                            add(Triple(preset.id, stringResource(wallpaperLabelRes(preset.id)), preset.lightColor))
                        }
                        add(
                            Triple(
                                "color",
                                stringResource(R.string.wallpaper_color),
                                state.wallpaperCustomColor.toComposeColor(MaterialTheme.colorScheme.primary)
                            )
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (row in gridItems.chunked(4)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                row.forEach { (id, label, color) ->
                                    val selected = state.wallpaperStyle == id
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .then(
                                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                    else Modifier.border(
                                                        1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape
                                                    )
                                                )
                                                .clickable {
                                                    viewModel.setWallpaperStyle(id)
                                                    if (id != "color" && id != "custom") showWallpaperPicker = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 自定义颜色输入（选中"自定义"时展开）──
                    if (state.wallpaperStyle == "color") {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.wallpaper_custom_color),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customWallpaperColor,
                                onValueChange = { v ->
                                    customWallpaperColor = v.filter { it.isLetterOrDigit() }.take(6)
                                    if (customWallpaperColor.length == 6) {
                                        viewModel.setWallpaperCustomColor("#$customWallpaperColor")
                                    }
                                },
                                label = { Text("HEX") },
                                prefix = { Text("#", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small
                            )
                            val previewColor = "#$customWallpaperColor".toComposeColor(Color.Gray)
                            val isValid = customWallpaperColor.length == 6
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(previewColor)
                                    .then(
                                        if (isValid) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isValid) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // ── 自定义图片入口 ──
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Outlined.Image, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.wallpaper_from_gallery))
                    }

                    // ── 透明度/模糊度调节（非"默认"时显示）──
                    if (state.wallpaperStyle != "none") {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        var blurValue by remember(state.wallpaperBlur) { mutableFloatStateOf(state.wallpaperBlur) }
                        var opacityValue by remember(state.wallpaperOpacity) { mutableFloatStateOf(state.wallpaperOpacity) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.wallpaper_opacity),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(48.dp)
                            )
                            Slider(
                                value = opacityValue,
                                onValueChange = { opacityValue = it },
                                onValueChangeFinished = { viewModel.setWallpaperOpacity(opacityValue) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(opacityValue * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.wallpaper_blur),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(48.dp)
                            )
                            Slider(
                                value = blurValue,
                                onValueChange = { blurValue = it },
                                onValueChangeFinished = { viewModel.setWallpaperBlur(blurValue) },
                                valueRange = 0f..30f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${blurValue.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperPicker = false }) {
                    Text(stringResource(R.string.wallpaper_done), fontWeight = FontWeight.Bold)
                }
            }
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
                    RadioButton(
                        selected = state.defaultStartPage == route,
                        onClick = { viewModel.setDefaultStartPage(route); showStartPagePicker = false },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
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
                    RadioButton(
                        selected = state.defaultBillType.value == type,
                        onClick = { viewModel.setDefaultBillType(type); showBillTypePicker = false },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                }
                if (type != billTypeOptions.last().first) HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
            } } },
            confirmButton = { TextButton(onClick = { showBillTypePicker = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) } }
        )
    }

    if (showIconPicker) {
        val iconLabels = linkedMapOf(
            PreferencesManager.APP_ICON_GREEN_WHITE to stringResource(R.string.settings_icon_green_white),
            PreferencesManager.APP_ICON_BLACK_WHITE to stringResource(R.string.settings_icon_black_white),
            PreferencesManager.APP_ICON_CYAN_WHITE to stringResource(R.string.settings_icon_cyan_white),
            PreferencesManager.APP_ICON_WHITE_BLACK to stringResource(R.string.settings_icon_white_black),
        )
        AppDialog(
            onDismissRequest = { showIconPicker = false },
            title = { Text(stringResource(R.string.settings_select_app_icon), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_app_icon_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                        .then(
                                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                            else Modifier.border(1.5.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                        ),
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
