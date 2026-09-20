package com.palmnote.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.export.ExportScope
import com.palmnote.data.export.ImportFileDetector
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.BlockingProgressDialog
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.components.SettingRowContent
import com.palmnote.ui.components.SettingRow
import com.palmnote.ui.components.ChoiceDialog
import com.palmnote.ui.theme.InfoBlue
import com.palmnote.ui.theme.ModuleBill
import com.palmnote.ui.theme.ModuleItem
import com.palmnote.ui.theme.ModuleLife
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导入导出：**应用数据**的进出都在这一页，导入侧自动识别文件类型——
 * - 完整备份包（PNB）→ 指回「备份与恢复」页（恢复职能不在这里）
 * - 全量数据包（本应用导出的 ZIP）→ 就地合并导入（判重跳过，不覆盖已有数据）
 * - 账单文件 → 明确指路「账单」页（账单导入/OCR 与本页完全分开）
 * 导出侧把明文 CSV 交给表格软件 / 其他 App（依赖闭包自动带上钱包/分类）。
 * 整份可恢复的数据走「备份与恢复」页——各走各的入口，互不掺杂。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExchangeScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    importViewModel: ImportViewModel = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 识别出备份包 / 账单文件 / 无法识别的兜底提示
    var showBackupRedirect by remember { mutableStateOf(false) }
    var showBillFile by remember { mutableStateOf(false) }
    var showUnsupported by remember { mutableStateOf(false) }

    var showScopePicker by remember { mutableStateOf(false) }
    var csvScope by remember { mutableStateOf(ExportScope.ALL) }
    val appName = stringResource(R.string.app_name)
    val exportSuffix = stringResource(R.string.settings_export_file_suffix)
    val csvScopeLabel = exportScopeLabel(csvScope)

    // 导入：选择一个文件，自动识别类型并分流
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val type = withContext(Dispatchers.IO) { ImportFileDetector.detect(context, uri) }
            when (type) {
                ImportFileDetector.FileType.BACKUP_PACKAGE -> showBackupRedirect = true
                ImportFileDetector.FileType.FULL_DATA -> importViewModel.importFullData(uri)
                // 账单文件与 OCR 在「账单」页，本页只管应用数据；指路而不是跳转
                ImportFileDetector.FileType.BILL_FILE -> showBillFile = true
                ImportFileDetector.FileType.UNKNOWN -> showUnsupported = true
            }
        }
    }

    // 导出：范围在点击时已确定，随回调一并送进 ViewModel
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportData(it, csvScope) } }

    // 导出/导入的反馈走各自的单通道，展示后清空避免旋转屏幕后重复弹出
    LaunchedEffect(state.resultMessage) {
        state.resultMessage?.let {
            snackbarHostState.showSnackbar(it)
            delay(100)
            viewModel.clearResult()
        }
    }
    LaunchedEffect(importState.error) {
        importState.error?.let {
            snackbarHostState.showSnackbar(it)
            importViewModel.consumeResult()
        }
    }

    // 全量导入结果对话框
    importState.report?.let { report ->
        AppDialog(
            onDismissRequest = { importViewModel.consumeResult() },
            title = { Text(stringResource(R.string.import_full_done_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.import_full_done_body, report.inserted, report.skipped))
                    if (report.walletsRecalculated > 0) {
                        Text(
                            stringResource(R.string.import_full_done_wallets, report.walletsRecalculated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importViewModel.consumeResult() }) {
                    Text(stringResource(R.string.settings_confirm))
                }
            }
        )
    }

    if (importState.importing) {
        BlockingProgressDialog(
            message = stringResource(R.string.import_full_running),
            detail = stringResource(R.string.import_full_running_detail)
        )
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_data_exchange),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── 导入：选择一个文件，应用自动识别类型并分流 ──
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingRow {
                        Button(
                            onClick = {
                                // 导出包没有注册的 MIME 类型，部分文件管理器会灰掉；
                                // 放宽到任意类型，识别不出的文件由兜底提示拦下
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.import_pick_file))
                        }
                    }
                    Text(
                        text = stringResource(R.string.import_pick_file_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    )
                }
            }

            // ── 导出 CSV：范围行 + 按钮 + 脚注，卡内自名无需另立标题 ──
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    // 范围只对 CSV 有意义（备份恒为全量）
                    SettingRow(clickable = { showScopePicker = true }) {
                        SettingRowContent(
                            title = stringResource(R.string.data_exchange_export_scope),
                            // 「含什么」必须写在点击之前：依赖闭包会自动带上钱包/分类，用户需要能预判
                            subtitle = exportScopeSummary(csvScope),
                            value = csvScopeLabel,
                            showChevron = true
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingRow {
                        Button(
                            onClick = {
                                // 文件名承载范围：用户事后在网盘里只看得见文件名
                                val fileName = if (csvScope.isComplete) appName + exportSuffix
                                               else "${appName}_$csvScopeLabel$exportSuffix"
                                csvExportLauncher.launch(fileName)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.data_exchange_export_action))
                        }
                    }

                    Text(
                        text = stringResource(R.string.data_exchange_export_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    )
                }
            }

            // ── 关于导入：让用户在选文件之前就能预判能不能导 ──
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingRow {
                        SettingRowContent(
                            title = stringResource(R.string.import_help_title),
                            subtitle = stringResource(R.string.import_help_body)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showScopePicker) {
        ChoiceDialog(
            title = stringResource(R.string.data_exchange_export_scope),
            options = ExportScope.entries,
            selected = csvScope,
            optionLabel = { exportScopeLabel(it) },
            optionIcon = { exportScopeIcon(it) },
            optionTint = { exportScopeTint(it) },
            onSelect = { csvScope = it },
            onDismiss = { showScopePicker = false }
        )
    }

    if (showBackupRedirect) {
        AppDialog(
            onDismissRequest = { showBackupRedirect = false },
            title = { Text(stringResource(R.string.import_is_package_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.import_is_package_body)) },
            confirmButton = {
                TextButton(onClick = { showBackupRedirect = false; onNavigateToBackup() }) {
                    Text(stringResource(R.string.import_is_package_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupRedirect = false }) {
                    Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBillFile) {
        AppDialog(
            onDismissRequest = { showBillFile = false },
            title = { Text(stringResource(R.string.import_bill_file_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.import_bill_file_body)) },
            confirmButton = {
                TextButton(onClick = { showBillFile = false }) {
                    Text(stringResource(R.string.settings_confirm))
                }
            }
        )
    }

    if (showUnsupported) {
        AppDialog(
            onDismissRequest = { showUnsupported = false },
            title = { Text(stringResource(R.string.import_unknown_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.import_unknown_body)) },
            confirmButton = {
                TextButton(onClick = { showUnsupported = false }) {
                    Text(stringResource(R.string.settings_confirm))
                }
            }
        )
    }
}

@Composable
private fun exportScopeLabel(scope: ExportScope): String = stringResource(
    when (scope) {
        ExportScope.ALL -> R.string.data_exchange_scope_all
        ExportScope.BILL -> R.string.data_exchange_scope_bill
        ExportScope.ASSET -> R.string.data_exchange_scope_asset
        ExportScope.LIFE -> R.string.data_exchange_scope_life
    }
)

/** 把「依赖闭包自动带上什么」摆到用户眼前——否则他无法预判勾了记账会拿到几个文件。 */
@Composable
private fun exportScopeSummary(scope: ExportScope): String = stringResource(
    when (scope) {
        ExportScope.ALL -> R.string.data_exchange_scope_all_desc
        ExportScope.BILL -> R.string.data_exchange_scope_bill_desc
        ExportScope.ASSET -> R.string.data_exchange_scope_asset_desc
        ExportScope.LIFE -> R.string.data_exchange_scope_life_desc
    }
)

/**
 * 选择器里每个选项的图标与配色。
 *
 * 三个单模块范围直接用各自的**模块色**（`ModuleBill` / `ModuleItem` / `ModuleLife`）——
 * 用户在整个 App 里已经把这些颜色和「记账 / 物品 / 生活」绑定了，这里不必再教一遍。
 * 「全部」不指向任何单个模块，用中性蓝。
 */
private fun exportScopeIcon(scope: ExportScope): ImageVector = when (scope) {
    ExportScope.ALL -> Icons.Outlined.SelectAll
    ExportScope.BILL -> Icons.Outlined.AccountBalanceWallet
    ExportScope.ASSET -> Icons.Outlined.Inventory2
    ExportScope.LIFE -> Icons.Outlined.FavoriteBorder
}

private fun exportScopeTint(scope: ExportScope): Color = when (scope) {
    ExportScope.ALL -> InfoBlue
    ExportScope.BILL -> ModuleBill
    ExportScope.ASSET -> ModuleItem
    ExportScope.LIFE -> ModuleLife
}
