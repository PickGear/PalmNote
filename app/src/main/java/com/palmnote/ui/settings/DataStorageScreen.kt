package com.palmnote.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.app.R
import com.palmnote.ui.components.*
import com.palmnote.ui.components.SettingsMenuItem
import com.palmnote.ui.components.SectionHeader
import com.palmnote.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 数据与备份（Hub）。
 *
 * 按用户的任务心智分四组，不按技术格式分：
 * **备份与迁移**（备份恢复 / 导入导出，数据的进出与保全）与
 * **存储与清理**（回收站 / 清除 / 缓存 / 日志）。
 * 两组同构：分区标题 + 单卡多行，页面节奏统一。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataStorageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToDataClear: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToDataExchange: () -> Unit,
    viewModel: SettingsViewModel
) {
    var showClearCacheDialog by remember { mutableStateOf(false) }
    // 崩溃日志（cacheDir/crash_*.log，已脱敏）：展示份数与体积，提供导出/清空
    var showCrashLogDialog by remember { mutableStateOf(false) }
    var crashLogCount by remember { mutableIntStateOf(0) }
    var crashLogBytes by remember { mutableLongStateOf(0L) }
    val crashLogScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val crashLogExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            crashLogScope.launch {
                val ok = withContext(Dispatchers.IO) { CrashLogStore.export(context, uri) }
                val messageRes = if (ok) R.string.settings_crash_log_exported else R.string.settings_crash_log_export_failed
                snackbarHostState.showSnackbar(context.getString(messageRes))
                crashLogCount = CrashLogStore.count(context)
                crashLogBytes = CrashLogStore.totalBytes(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        crashLogCount = CrashLogStore.count(context)
        crashLogBytes = CrashLogStore.totalBytes(context)
    }

    val crashLogKb = (crashLogBytes + 512) / 1024

    LaunchedEffect(state.resultMessage) {
        state.resultMessage?.let {
            snackbarHostState.showSnackbar(it)
            delay(100)
            viewModel.clearResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_data),
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
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── 备份与迁移：标题与「存储与清理」对仗，两组结构一致页面才有节奏 ──
            item { SectionHeader(stringResource(R.string.settings_data_transfer), Icons.Outlined.Backup, ModuleLife) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.settings_data_backup),
                        subtitle = stringResource(R.string.settings_data_backup_subtitle),
                        tint = ModuleLife,
                        onClick = onNavigateToBackup
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(
                        icon = Icons.Outlined.ImportExport,
                        title = stringResource(R.string.settings_data_exchange),
                        subtitle = stringResource(R.string.settings_data_exchange_subtitle),
                        tint = InfoBlue,
                        onClick = onNavigateToDataExchange
                    )
                }
            }

            // ── 存储与清理 ──
            item { SectionHeader(stringResource(R.string.settings_data_cleanup), Icons.Outlined.CleaningServices, Amber) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(icon = Icons.Outlined.DeleteOutline, title = stringResource(R.string.settings_recycle_bin), subtitle = stringResource(R.string.settings_recycle_bin_subtitle), tint = StatusActive, onClick = onNavigateToRecycleBin)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.DeleteForever, title = stringResource(R.string.settings_clear_data), subtitle = stringResource(R.string.settings_clear_data_subtitle), tint = ErrorLight, onClick = onNavigateToDataClear)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.CleaningServices, title = stringResource(R.string.settings_clear_cache), subtitle = stringResource(R.string.settings_clear_cache_subtitle), tint = Amber, onClick = { showClearCacheDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.settings_crash_log),
                        subtitle = stringResource(R.string.settings_crash_log_subtitle, crashLogCount, crashLogKb),
                        tint = ErrorLight,
                        onClick = {
                            crashLogCount = CrashLogStore.count(context)
                            crashLogBytes = CrashLogStore.totalBytes(context)
                            showCrashLogDialog = true
                        }
                    )
                }
            }

            // ── 示例数据：演示模式开关（载入/移除一套示例记录；关闭即物理移除，不再进页面与备份。当前为生活页示例，后续按此扩展到记账/资产）──
            item { SectionHeader(stringResource(R.string.settings_demo_section), Icons.Outlined.AutoAwesome, ModuleLife) }
            item {
                // 单行卡：去掉上下内边距，整卡高度与多行卡里的单行一致（否则 12dp×2 只包这一行，会比别行显高）。
                ModuleCard(
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    SettingsMenuItem(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(R.string.settings_demo_mode),
                        subtitle = stringResource(R.string.settings_demo_mode_subtitle),
                        tint = ModuleLife,
                        // 不传 onClick：整行不可点，只有右侧开关响应（避免整卡出现按下态）。
                        trailing = {
                            CapsuleSwitch(
                                checked = state.demoModeEnabled,
                                onCheckedChange = { viewModel.setDemoModeEnabled(it) },
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AppDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm), fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = { showClearCacheDialog = false; viewModel.clearCache(context) }) {
                    Text(stringResource(R.string.settings_clear_cache_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showCrashLogDialog) {
        AppDialog(
            onDismissRequest = { showCrashLogDialog = false },
            title = { Text(stringResource(R.string.settings_crash_log), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_crash_log_subtitle, crashLogCount, crashLogKb))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_crash_log_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCrashLogDialog = false
                    if (crashLogCount == 0) {
                        crashLogScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_crash_log_empty)) }
                    } else {
                        crashLogExportLauncher.launch("palmnote_crash_logs.txt")
                    }
                }) { Text(stringResource(R.string.settings_crash_log_export)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLogStore.clear(context)
                    crashLogCount = 0
                    crashLogBytes = 0L
                    showCrashLogDialog = false
                    crashLogScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_crash_log_cleared)) }
                }) { Text(stringResource(R.string.settings_crash_log_clear)) }
            }
        )
    }
}
