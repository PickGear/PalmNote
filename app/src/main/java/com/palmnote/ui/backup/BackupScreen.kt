package com.palmnote.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.backup.BackupKind
import com.palmnote.data.backup.BackupState
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.BlockingProgressDialog
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.SettingsCard
import com.palmnote.ui.components.ChoiceDialog
import com.palmnote.ui.components.SectionHeader
import com.palmnote.ui.components.SettingsMenuItem
import com.palmnote.ui.components.SettingsSectionFooter
import com.palmnote.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 健康度判断用：仅比较「多久没有新备份」，不涉及精确日历运算。 */
private const val DAY_MS = 24L * 60 * 60 * 1000

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = BYTES_PER_KB * 1024
private const val BYTES_PER_GB = BYTES_PER_MB * 1024

/**
 * 备份与恢复：数据安全的唯一页面（备份与恢复收在一处，不再散到别处）。
 *
 * 只有**一种备份**：「立即备份」（本机）与「备份到文件夹」（同一份备份另存一处），
 * 且自动/手动共用。加密与否由「备份密码」一行统一决定：设了密码 → 全部备份加密并
 * 带便携密钥（可换机恢复）；没设 → 全部明文（仅本机可恢复）。
 * CSV 导入导出是给其他软件的数据交换，另一件事，见「导出与导入」页。
 *
 * 版面照「导入导出」页同一套：摘要卡（备份健康度）→「备份」「恢复」两个分区，
 * 动作都是带图标衬底的行；解释性文字挂卡片外面的脚注，卡里只放可点的行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel()
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val autoBackupSettings by viewModel.autoBackupSettings.collectAsStateWithLifecycle()
    val backupPasswordSet by viewModel.backupPasswordSet.collectAsStateWithLifecycle()
    val backupPasswordEverSet by viewModel.backupPasswordEverSet.collectAsStateWithLifecycle(initialValue = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 「从文件导入」确认恢复时置位：恢复成功后必须重启
    var pendingRestore by remember { mutableStateOf(false) }

    // 备份目录列表刷新信号（备份成功后 +1 触发重查）
    var backupListRefreshKey by remember { mutableIntStateOf(0) }

    // 本机内部备份与所选文件夹内的包合并为一个列表：对用户而言都是"可以拿来恢复的备份"。
    // 提到 Screen 顶层是因为「备份」卡的状态行要基于同一份数据给结论，避免两处各算一遍导致对不上。
    var candidates by remember { mutableStateOf<List<RestoreCandidate>>(emptyList()) }
    var listExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(backupListRefreshKey) {
        val local = viewModel.listLocalBackups()
            .map { RestoreCandidate(it.date, it.size, RestoreSource.FromLocal(it.filePath), it.kind) }
        val inFolder = viewModel.listBackupsInDir()
            .map { RestoreCandidate(it.date, it.size, RestoreSource.FromUri(it.uri), it.kind) }
        candidates = (local + inFolder).sortedByDescending { it.date }
    }

    // 待删除的备份：删除是破坏性且不可撤销，先确认再执行
    var pendingDelete by remember { mutableStateOf<RestoreCandidate?>(null) }

    // 对话框开关：备份密码、频率与份数选择器
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showKeepPicker by remember { mutableStateOf(false) }

    // 「备份频率」选项与当前值：0 表示不自动备份（选项里含「关」就不必再单设开关）
    val autoIntervalOptions = remember { listOf(0) + PreferencesManager.AUTO_BACKUP_INTERVAL_OPTIONS }
    val selectedInterval = if (autoBackupSettings.enabled) autoBackupSettings.intervalDays else 0

    // 首次备份位置询问：第一次点「立即备份」时先问存哪里，之后跟随所选位置
    var showLocationDialog by remember { mutableStateOf(false) }
    var pendingFirstBackup by remember { mutableStateOf(false) }
    val backupLocation by viewModel.backupLocation.collectAsStateWithLifecycle()
    // 文件夹位置显示目录名（SAF 读名是 IO 操作）
    var locationLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(backupLocation) {
        locationLabel = backupLocation.folderUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name
                }.getOrNull()
            }
        }
    }

    // 待恢复来源：SAF 文件（用户选择或备份文件夹）／本机内部存储备份
    var isRestoring by remember { mutableStateOf(false) }
    // 备份进行中禁止发起恢复：恢复会关库/替换文件，与正在写的备份文件互相损坏
    val backupBusy = backupState is BackupState.Progress
    var restoreSource by remember { mutableStateOf<RestoreSource?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { fileUri ->
        fileUri?.let { uri ->
            scope.launch {
                // 与导入导出页共用同一套文件识别：备份包恢复、数据包/账单文件明确指路，
                // 不再只认 PNB 头（旧版明文 ZIP 备份也能恢复）
                when (com.palmnote.data.export.ImportFileDetector.detect(context, uri)) {
                    com.palmnote.data.export.ImportFileDetector.FileType.BACKUP_PACKAGE ->
                        restoreSource = RestoreSource.FromUri(uri)
                    com.palmnote.data.export.ImportFileDetector.FileType.FULL_DATA ->
                        snackbarHostState.showSnackbar(context.getString(R.string.backup_file_is_data))
                    else ->
                        snackbarHostState.showSnackbar(context.getString(R.string.backup_file_not_package))
                }
            }
        }
    }

    // 备份位置选择（SAF）：首次备份询问与后续修改共用
    val portableBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) {
            // 用户在 SAF 选择器里取消：清除待备份标记，避免之后从「备份位置」行
            // 随手改个位置却意外触发一次立即备份
            pendingFirstBackup = false
            return@rememberLauncherForActivityResult
        }
        treeUri.let { uri ->
            // 部分 DocumentsProvider（第三方网盘等）不支持持久化授权并抛 SecurityException；
            // 本次备份依然能写入，只是重启后目录授权会失效，不应因此崩溃
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.saveBackupLocation(uri.toString())
            if (pendingFirstBackup) {
                pendingFirstBackup = false
                viewModel.createBackupNow()
            }
        }
    }

    // Handle results
    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupState.Success -> {
                if (isRestoring || pendingRestore) {
                    // 整包恢复替换了数据库，用 AlarmManager 可靠重启，避免 startActivity+exit(0) 竞态
                    isRestoring = false
                    pendingRestore = false
                    com.palmnote.util.AppRestarter.restartApp(context)
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.backup_operation_success))
                    viewModel.resetState()
                    backupListRefreshKey++
                }
            }
            is BackupState.Error -> {
                pendingRestore = false
                isRestoring = false
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.backup_title),
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
            // ── 页面摘要：备份健康度。打开这一页先回答「备份是不是真的在跑」──
            item {
                BackupHero(
                    settings = autoBackupSettings,
                    candidates = candidates,
                    onDismissError = { viewModel.dismissAutoBackupError() }
                )
            }

            // ── 备份：主动作在前，其余全是「标签 —— 当前值 ▸」──
            item {
                SectionHeader(stringResource(R.string.backup_section_backup), Icons.Outlined.Backup, DopamineSky)
            }
            item {
                SettingsCard(modifier = Modifier.fillMaxWidth()) {
                    // 唯一的主动作保留实心按钮：点了立刻执行、不进任何下一层，
                    // 与下面「标签 —— 当前值 ▸」的设置行是两类东西，不共用行样式
                    // 主动作不用实心 Button：这一页的视觉词汇是「淡主色」（摘要卡 10%、
                    // 图标衬底 12~14%），一块高饱和色会跳出整页。tonal 底 12% 与衬底同源，
                    // 主色文字保住「这是主动作」的分量。
                    FilledTonalButton(
                        onClick = {
                            if (backupLocation.chosen) viewModel.createBackupNow()
                            else {
                                pendingFirstBackup = true
                                showLocationDialog = true
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 12.dp),
                        enabled = backupState !is BackupState.Progress
                    ) {
                        Icon(
                            Icons.Outlined.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_now))
                    }
                    if (backupState is BackupState.Progress && !isRestoring) {
                        BackupProgressBar(
                            percent = (backupState as BackupState.Progress).percent,
                            label = stringResource(R.string.backup_backing_up)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 备份位置：决定「立即备份」与自动备份写到哪里，首次备份时询问，之后可改
                    SettingsMenuItem(
                        icon = Icons.Outlined.FolderOpen,
                        title = stringResource(R.string.backup_location),
                        subtitle = stringResource(R.string.backup_location_desc),
                        tint = DopamineSky,
                        onClick = { showLocationDialog = true },
                        trailing = {
                            val locationValue = if (backupLocation.folderUri == null) {
                                stringResource(R.string.backup_location_internal)
                            } else {
                                locationLabel ?: stringResource(R.string.backup_location_folder_set)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = locationValue,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 自动备份只留一行「备份频率」，选项里含「不自动备份」= 关，
                    // 不再单设开关——少一个概念、少一行；「备份计划」这类造词主流产品不用。
                    SettingsMenuItem(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.backup_auto_interval),
                        subtitle = stringResource(
                            if (autoBackupSettings.enabled) R.string.backup_auto_interval_desc_on
                            else R.string.backup_auto_interval_desc_off
                        ),
                        tint = DopamineSky,
                        onClick = { showIntervalPicker = true },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = autoIntervalLabel(selectedInterval),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    if (autoBackupSettings.enabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsMenuItem(
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.backup_auto_keep),
                            subtitle = stringResource(R.string.backup_auto_keep_desc),
                            tint = DopamineSky,
                            onClick = { showKeepPicker = true },
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.backup_auto_keep_option,
                                            autoBackupSettings.keepCount,
                                            autoBackupSettings.keepCount
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // 备份密码：一个开关决定所有备份加不加密（自动 / 手动 / 文件夹共用）
                    SettingsMenuItem(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.backup_password_label),
                        // 副标题三态：已设置讲功能；未设置且曾用过要点破「已退回明文」；
                        // 从未用过讲「无密码=明文」。后两态都把风险在导出前告诉用户
                        subtitle = stringResource(
                            when {
                                backupPasswordSet -> R.string.backup_password_desc
                                backupPasswordEverSet -> R.string.backup_password_used_before_hint
                                else -> R.string.backup_password_unset_hint
                            }
                        ),
                        tint = DopamineSky,
                        onClick = { showPasswordDialog = true },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        if (backupPasswordSet) R.string.backup_password_set else R.string.backup_password_unset
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            // ── 恢复：已有备份池在前（自名分组），从文件恢复收尾 ──
            item {
                SectionHeader(stringResource(R.string.backup_section_restore), Icons.Outlined.FileDownload, DopamineSky)
            }
            item {
                SettingsCard(modifier = Modifier.fillMaxWidth()) {
                    // 列表提到 Screen 顶层：Hero 与这里共用同一份 candidates，避免两处各算一遍
                    val localCount = candidates.count { it.source is RestoreSource.FromLocal }
                    SettingsMenuItem(
                        icon = Icons.Outlined.Archive,
                        title = stringResource(R.string.backup_existing),
                        subtitle = stringResource(R.string.backup_existing_desc),
                        tint = DopamineSky,
                        onClick = { listExpanded = !listExpanded },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.backup_existing_count,
                                        localCount,
                                        candidates.size - localCount
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    if (listExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    if (listExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                            candidates.forEach { candidate ->
                                RestoreCandidateRow(
                                    date = candidate.date,
                                    size = candidate.size,
                                    kind = candidate.kind,
                                    fromLocal = candidate.source is RestoreSource.FromLocal,
                                    onClick = { if (!backupBusy) restoreSource = candidate.source },
                                    onDelete = { if (!backupBusy) pendingDelete = candidate }
                                )
                            }
                            // 池逻辑说明：本机名额 / 快照独立 / 导出包归用户，只在展开看列表时出现
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.backup_pool_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsMenuItem(
                        icon = Icons.Outlined.FileDownload,
                        title = stringResource(R.string.backup_restore_from_file),
                        subtitle = stringResource(R.string.backup_restore_from_file_desc),
                        tint = DopamineSky,
                        onClick = {
                            if (backupBusy) return@SettingsMenuItem
                            // .palmnote/.zip 都没有注册的 MIME 类型，部分文件管理器会把它判为不可选而灰掉；
                            // 放宽到任意类型，选错文件由文件识别拦下
                            restoreLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
            item {
                // 「本机 / 文件夹」的区别是这一页最容易困惑的点：两种备份内容相同，
                // 差别只在文件放在哪、卸载后还在不在。规则常驻卡外脚注，不藏在展开列表里
                SettingsSectionFooter(listOf(stringResource(R.string.backup_restore_footer)))
            }
        }
    }

    if (showLocationDialog) {
        val firstRun = !backupLocation.chosen
        AppDialog(
            onDismissRequest = {
                showLocationDialog = false
                pendingFirstBackup = false
            },
            title = { Text(stringResource(R.string.backup_location), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(
                        if (firstRun) R.string.backup_location_first_body else R.string.backup_location_change_body
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocationDialog = false
                    viewModel.saveBackupLocation(null)
                    if (pendingFirstBackup) {
                        pendingFirstBackup = false
                        viewModel.createBackupNow()
                    }
                }) { Text(stringResource(R.string.backup_location_use_internal), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationDialog = false
                    portableBackupLauncher.launch(null)
                }) { Text(stringResource(R.string.backup_location_pick_folder)) }
            }
        )
    }

    if (showIntervalPicker) {
        ChoiceDialog(
            title = stringResource(R.string.backup_auto_interval),
            options = autoIntervalOptions,
            selected = selectedInterval,
            optionLabel = { autoIntervalLabel(it) },
            optionIcon = { Icons.Outlined.Schedule },
            optionTint = { InfoBlue },
            onSelect = {
                if (it == 0) {
                    viewModel.setAutoBackupEnabled(false)
                } else {
                    viewModel.setAutoBackupEnabled(true)
                    viewModel.setAutoBackupIntervalDays(it)
                }
            },
            onDismiss = { showIntervalPicker = false }
        )
    }

    if (showKeepPicker) {
        ChoiceDialog(
            title = stringResource(R.string.backup_auto_keep),
            options = PreferencesManager.AUTO_BACKUP_KEEP_OPTIONS,
            selected = autoBackupSettings.keepCount,
            optionLabel = { pluralStringResource(R.plurals.backup_auto_keep_option, it, it) },
            optionIcon = { Icons.Outlined.Inventory2 },
            optionTint = { InfoBlue },
            onSelect = { viewModel.setAutoBackupKeepCount(it) },
            onDismiss = { showKeepPicker = false }
        )
    }

    // 备份密码对话框：设了 → 所有备份加密（含便携密钥，可换机恢复）；留空确认 → 清除，全部明文。
    // 预填当前已保存的密码，改动即保存（密码经 Keystore 包裹落盘，自动备份也用得上）。
    if (showPasswordDialog) {
        var passwordVisible by remember { mutableStateOf(false) }
        var draft by remember { mutableStateOf(viewModel.loadBackupPassword().orEmpty()) }
        val tooShort = draft.isNotEmpty() && draft.length < BackupViewModel.MIN_PASSWORD_LENGTH
        AppDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(stringResource(R.string.backup_password_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.backup_hide_password else R.string.backup_show_password
                                    )
                                )
                            }
                        },
                        singleLine = true,
                        isError = tooShort
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.backup_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tooShort) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setBackupPassword(draft.ifBlank { null })
                        showPasswordDialog = false
                    },
                    enabled = !tooShort
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Restore password dialog
    restoreSource?.let { source ->
        var restorePassword by remember(source) { mutableStateOf(viewModel.loadBackupPassword().orEmpty()) }
        var restorePasswordVisible by remember { mutableStateOf(false) }
        // 备份是否加密（读文件头 MAGIC）：明文备份无需密码，隐藏密码框避免误解
        var encrypted by remember(source) { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(source) { encrypted = isEncryptedBackupFile(context, source) }
        AppDialog(
            onDismissRequest = { restoreSource = null },
            title = { Text(stringResource(R.string.backup_restore_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_restore_confirm))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (encrypted != false) {
                        OutlinedTextField(
                            value = restorePassword,
                            onValueChange = { restorePassword = it },
                            label = { Text(stringResource(R.string.backup_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (restorePasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { restorePasswordVisible = !restorePasswordVisible }) {
                                    Icon(
                                        if (restorePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(
                                            if (restorePasswordVisible) R.string.backup_hide_password else R.string.backup_show_password
                                        )
                                    )
                                }
                            },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    isRestoring = true
                    val pwd = restorePassword.ifBlank { null }
                    when (source) {
                        is RestoreSource.FromUri -> viewModel.restoreFromUri(source.uri, pwd)
                        is RestoreSource.FromLocal -> viewModel.restoreFromLocalFile(source.filePath, pwd)
                    }
                    restoreSource = null
                }) {
                    Text(stringResource(R.string.backup_restore), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreSource = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Delete confirm dialog：删除不可撤销，且不同类型的备份后果不同，必须分别说清
    pendingDelete?.let { candidate ->
        AppDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.backup_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(deleteConsequence(candidate)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val deleted = when (val source = candidate.source) {
                            is RestoreSource.FromLocal -> viewModel.deleteLocalBackup(source.filePath)
                            is RestoreSource.FromUri -> viewModel.deleteFolderBackup(source.uri)
                        }
                        snackbarHostState.showSnackbar(
                            context.getString(
                                if (deleted) R.string.backup_delete_done else R.string.backup_delete_failed
                            )
                        )
                        if (deleted) backupListRefreshKey++
                    }
                }) {
                    Text(stringResource(R.string.backup_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** 删除确认里的后果说明：快照的风险与普通备份完全不同，不能共用一句"删除后无法恢复"。 */
@Composable
private fun deleteConsequence(candidate: RestoreCandidate): String = when {
    candidate.isSnapshot -> stringResource(R.string.backup_delete_desc_snapshot)
    candidate.source is RestoreSource.FromLocal -> stringResource(R.string.backup_delete_desc_local)
    else -> stringResource(R.string.backup_delete_desc_folder)
}

/** 待恢复的备份来源：SAF 文件（用户选择/备份文件夹）或本机内部存储备份。 */
private sealed interface RestoreSource {
    data class FromUri(val uri: Uri) : RestoreSource
    data class FromLocal(val filePath: String) : RestoreSource
}

/** 一条可恢复的备份：本机备份或备份文件夹内的包，统一按时间倒序展示。 */
private data class RestoreCandidate(
    val date: Long,
    val size: Long,
    val source: RestoreSource,
    /** 备份身份（由文件名解析）；决定列表里如何标识、以及删除时的后果说明。 */
    val kind: BackupKind = BackupKind.LEGACY
) {
    /** 快照是恢复失败后的唯一退路：不参与轮转、也不能和普通备份共用后果说明。 */
    val isSnapshot: Boolean get() = kind == BackupKind.SNAPSHOT
}

/**
 * 读 URI 文件头判断是不是备份包（PNB*），并区分是否加密。
 * 「是不是备份包」供本页与「导出与导入」页分流；「是否加密」决定恢复时要不要密码。
 */
internal fun isBackupPackage(context: Context, uri: Uri): Pair<Boolean, Boolean> = try {
    val magic = ByteArray(4)
    val read = context.contentResolver.openInputStream(uri)?.use { it.read(magic) }
    val header = if (read == 4) String(magic) else ""
    Pair(header.startsWith("PNB"), header == "PNBK" || header == "PNB2")
} catch (_: Exception) {
    Pair(false, false)
}

/** 读备份文件头 MAGIC，判断是否为加密备份（仅加密包恢复时才需要密码）。 */
private fun isEncryptedBackupFile(context: Context, source: RestoreSource): Boolean = when (source) {
    is RestoreSource.FromUri -> isBackupPackage(context, source.uri).second
    is RestoreSource.FromLocal -> try {
        val magic = ByteArray(4)
        val read = java.io.FileInputStream(source.filePath).use { it.read(magic) }
        read == 4 && (String(magic) == "PNBK" || String(magic) == "PNB2")
    } catch (_: Exception) {
        false
    }
}

/**
 * 备份 / 恢复共用的进度块。
 *
 * 原本两处各写一遍，且 `Spacer` 高度还不一致（6dp / 8dp）——同一个进度条在同一页出现两种间距。
 * 提成组件后间距唯一。
 */
@Composable
private fun BackupProgressBar(percent: Int, label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 页面摘要卡：备份健康度。给结论，不给一串时间戳。
 *
 * 系统级 Auto Backup 已关闭（`AndroidManifest: allowBackup="false"`，因为包内含密码本密钥，
 * 不允许外流到云端），因此 App 内备份是用户唯一的数据退路。既然没有第二条命，
 * 「备份是不是真的在跑」就必须在打开这一页时一眼可见，而不是等到需要恢复那天才发现从来没备上。
 *
 * 形态与「导出与打包」页的摘要卡一致：淡主色底、不可点。健康信号由图标衬底的颜色承载
 * （正常 = primary / 出错或过期 = error），标题与副标题保持默认色——整卡染红会喧宾夺主，
 * 也让「摘要卡」看起来像一张错误卡。
 */
@Composable
private fun BackupHero(
    settings: BackupViewModel.AutoBackupSettings,
    candidates: List<RestoreCandidate>,
    onDismissError: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    // 只把自动/手动备份算作「最近一次保护」：快照是恢复前临时生成的，把它算进来会让
    // 从未正常备份过的用户看到一条乐观的时间 —— 那恰好掩盖了唯一要暴露的问题。
    val protectedBackups = remember(candidates) { candidates.filter { !it.isSnapshot } }
    val lastBackupAt = remember(protectedBackups) { protectedBackups.maxOfOrNull { it.date } ?: 0L }
    // 备份体积是「我一共占了多少空间」的唯一答案，与「最近一次」互补，不是重复。
    val totalBytes = remember(protectedBackups) { protectedBackups.sumOf { it.size } }

    val staleAfterDays = settings.intervalDays * 2
    val isStale = settings.enabled && lastBackupAt > 0L &&
        System.currentTimeMillis() - lastBackupAt > staleAfterDays * DAY_MS
    val hasError = settings.lastErrorAt > 0L

    val statusColor = when {
        hasError || isStale -> scheme.error
        lastBackupAt > 0L -> scheme.primary
        else -> scheme.onSurfaceVariant
    }

    // 先合成到不透明面色上：壁纸模式下 background 可能是透明的，直接铺会压在照片上
    val heroContainer = scheme.primary.copy(alpha = 0.10f).compositeOver(scheme.surface)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(heroContainer)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column {
            BackupHealthSummaryRow(
                lastBackupAt = lastBackupAt,
                hasError = hasError,
                statusColor = statusColor,
                protectedCount = protectedBackups.size,
                totalBytes = totalBytes
            )

            if (isStale) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.backup_health_stale, staleAfterDays, staleAfterDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error
                )
            }

            if (hasError) {
                Spacer(modifier = Modifier.height(10.dp))
                BackupErrorBlock(lastErrorMsg = settings.lastErrorMsg, onDismissError = onDismissError)
            }
        }
    }
}

/**
 * 摘要卡的标题行：48dp 健康徽标 + 「上次备份：<时间>」+「共 N 份 · 体积」。
 * 从 [BackupHero] 拆出来让摘要卡只保留「算状态 → 摆区块」的骨架；
 * 状态逻辑（哪些备份算保护、过期怎么判）留在 Hero 里，这里只管画。
 */
@Composable
private fun BackupHealthSummaryRow(
    lastBackupAt: Long,
    hasError: Boolean,
    statusColor: Color,
    protectedCount: Int,
    totalBytes: Long
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (lastBackupAt > 0L && !hasError) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (lastBackupAt > 0L) {
                    stringResource(R.string.backup_health_last_success, backupTimeText(lastBackupAt))
                } else {
                    stringResource(R.string.backup_health_never)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // 份数只给总数：快照是恢复失败的退路，明细在下方列表里，不必在这里再拆一遍。
            // 一份都没有时不显示「共 0 份」——那只是把标题那句话又说了一遍。
            if (protectedCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (totalBytes > 0L) {
                        stringResource(
                            R.string.backup_health_counts_size,
                            protectedCount,
                            formatBackupSize(totalBytes)
                        )
                    } else {
                        stringResource(R.string.backup_health_counts, protectedCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * 自动备份失败块：errorContainer 底 + 报错文案 + 「知道了」。
 * 从 [BackupHero] 拆出来纯粹是因为 detekt：摘要卡本体刚好压在 LongMethod 阈值上，
 * 错误块自成一体后语义也更清楚——它是「一次失败的详情」，不是摘要卡的一部分。
 */
@Composable
private fun BackupErrorBlock(lastErrorMsg: String, onDismissError: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = scheme.errorContainer) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (lastErrorMsg.isBlank()) {
                    stringResource(R.string.backup_health_failed_unknown)
                } else {
                    stringResource(R.string.backup_health_failed, lastErrorMsg)
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismissError, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.backup_health_dismiss))
            }
        }
    }
}

/** 单条备份明细：友好时间 + 身份/来源/体积 + 删除。长串文件名对用户没有信息量，不再展示。 */
@Composable
private fun RestoreCandidateRow(
    date: Long,
    size: Long,
    kind: BackupKind,
    fromLocal: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backupTimeText(date),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = backupMetaText(size, fromLocal, kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 备份一直"只能建不能删"是明确缺陷：用户既清不掉旧文件，也无法在存储告急时腾空间
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.backup_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 备份时间：今天/昨天用相对说法，其余按日期。 */
@Composable
private fun backupTimeText(date: Long): String {
    if (date <= 0L) return ""
    val now = remember(date) { Calendar.getInstance() }
    val then = remember(date) { Calendar.getInstance().apply { timeInMillis = date } }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayGap = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    val time = remember(date) { formatWithSkeleton(date, "Hm") }
    val day = remember(date) { formatWithSkeleton(date, if (sameYear) "MMMd" else "yMMMd") }
    return when {
        sameYear && dayGap == 0 -> stringResource(R.string.backup_time_today, time)
        sameYear && dayGap == 1 -> stringResource(R.string.backup_time_yesterday, time)
        else -> day
    }
}

/** 身份 + 来源 + 体积，形如「自动 · 本机 · 350 KB」。 */
@Composable
private fun backupMetaText(size: Long, fromLocal: Boolean, kind: BackupKind): String {
    val kindText = stringResource(
        when (kind) {
            BackupKind.AUTO -> R.string.backup_kind_auto
            BackupKind.MANUAL -> R.string.backup_kind_manual
            BackupKind.SNAPSHOT -> R.string.backup_kind_snapshot
            BackupKind.PORTABLE -> R.string.backup_kind_portable
            BackupKind.LEGACY -> R.string.backup_kind_legacy
        }
    )
    val source = stringResource(if (fromLocal) R.string.backup_source_local else R.string.backup_source_folder)
    val sizeText = formatBackupSize(size)
    return if (sizeText.isEmpty()) "$kindText · $source" else "$kindText · $source · $sizeText"
}

/** 体积自适应单位：350 KB 不再被取整成 0 MB。 */
private fun formatBackupSize(bytes: Long): String = when {
    bytes >= BYTES_PER_GB -> String.format(Locale.getDefault(), "%.1f GB", bytes.toDouble() / BYTES_PER_GB)
    bytes >= BYTES_PER_MB -> String.format(Locale.getDefault(), "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
    bytes >= BYTES_PER_KB -> "${bytes / BYTES_PER_KB} KB"
    bytes > 0L -> "$bytes B"
    else -> ""
}

/** 按 locale 取最佳日期格式（zh 得到「9月10日」，en 得到「Sep 10」）。 */
private fun formatWithSkeleton(date: Long, skeleton: String): String = SimpleDateFormat(
    android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton),
    Locale.getDefault()
).format(Date(date))

/** 频率选项文案：0 = 不自动；1 = 每天；其余「每 N 天」。 */
@Composable
private fun autoIntervalLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.backup_auto_off)
    1 -> stringResource(R.string.backup_auto_daily)
    else -> pluralStringResource(R.plurals.backup_auto_interval_option, days, days)
}
