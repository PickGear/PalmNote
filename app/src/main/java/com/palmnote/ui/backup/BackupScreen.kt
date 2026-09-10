package com.palmnote.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.backup.BackupState
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 「记住密码」开关的显示缩放：Material3 默认尺寸与一行说明文字并排时偏大。 */
private const val REMEMBER_SWITCH_SCALE = 0.8f

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = BYTES_PER_KB * 1024
private const val BYTES_PER_GB = BYTES_PER_MB * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel()
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val rememberPassword by viewModel.rememberPassword.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 备份目录列表刷新信号（备份成功后 +1 触发重查）
    var backupListRefreshKey by remember { mutableIntStateOf(0) }

    // SAF: backup to user-chosen folder
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { uri ->
            // 部分 DocumentsProvider（第三方网盘等）不支持持久化授权并抛 SecurityException；
            // 本次导出依然能写入，只是重启后目录列表会失效，不应因此崩溃
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.saveBackupDir(uri)
            viewModel.createBackupToFolder(uri)
        }
    }

    // 待恢复来源：SAF 文件（用户选择或备份文件夹）／本机内部存储备份
    var isRestoring by remember { mutableStateOf(false) }
    var restoreSource by remember { mutableStateOf<RestoreSource?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { fileUri ->
        fileUri?.let { uri ->
            restoreSource = RestoreSource.FromUri(uri)
        }
    }

    // Handle results
    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupState.Success -> {
                if (isRestoring) {
                    isRestoring = false
                    // 用 AlarmManager 可靠重启，避免 startActivity+exit(0) 竞态
                    com.palmnote.util.AppRestarter.restartApp(context)
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.backup_operation_success))
                    viewModel.resetState()
                    backupListRefreshKey++
                }
            }
            is BackupState.Error -> {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Backup ──
            ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_create), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = password ?: "",
                    onValueChange = { viewModel.setPassword(it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))

                // 可选：把密码交给本机 Keystore 包裹后记住，下次进入自动预填（默认关闭，不改变导出仍需密码的要求）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rememberPassword,
                        onCheckedChange = { viewModel.setRememberPassword(it) },
                        modifier = Modifier.scale(REMEMBER_SWITCH_SCALE)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.backup_remember_password),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (backupState is BackupState.Progress && !isRestoring) {
                    val backupPercent = (backupState as BackupState.Progress).percent
                    LinearProgressIndicator(
                        progress = { (backupPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_backing_up), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Button(
                        onClick = { backupLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        // 导出必须加密：包内含图片、设置等明文条目，无密码（或密码过短）导出等于明文外流
                        enabled = (password?.length ?: 0) >= BackupViewModel.MIN_PASSWORD_LENGTH &&
                            backupState !is BackupState.Progress
                    ) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_now))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val typedLength = password?.length ?: 0
                Text(
                    text = stringResource(R.string.backup_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (typedLength in 1 until BackupViewModel.MIN_PASSWORD_LENGTH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // ── Restore ──
            ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                // 本机自动备份与备份文件夹内的包合并为一个列表：对用户而言都是"可以拿来恢复的备份"，
                // 分成两组只会让人多读一段说明。默认折叠，展开后按时间倒序。
                var candidates by remember { mutableStateOf<List<RestoreCandidate>>(emptyList()) }
                var listExpanded by remember { mutableStateOf(false) }
                LaunchedEffect(backupListRefreshKey) {
                    val local = viewModel.listLocalBackups()
                        .map { RestoreCandidate(it.date, it.size, RestoreSource.FromLocal(it.filePath)) }
                    val inFolder = viewModel.listBackupsInDir()
                        .map { RestoreCandidate(it.date, it.size, RestoreSource.FromUri(it.uri)) }
                    candidates = (local + inFolder).sortedByDescending { it.date }
                }

                if (candidates.isNotEmpty()) {
                    // 两类分开计数：本机自动备份只能本机恢复；文件夹里的导出包才可换机。
                    // 混成一个总数会让用户误以为"备份很多=很安全"。
                    val localCount = candidates.count { it.source is RestoreSource.FromLocal }
                    RestoreSummaryRow(
                        localCount = localCount,
                        portableCount = candidates.size - localCount,
                        newestDate = candidates.first().date,
                        expanded = listExpanded,
                        onClick = { listExpanded = !listExpanded }
                    )
                    if (listExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        candidates.forEach { candidate ->
                            RestoreCandidateRow(
                                date = candidate.date,
                                size = candidate.size,
                                fromLocal = candidate.source is RestoreSource.FromLocal,
                                onClick = { restoreSource = candidate.source }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isRestoring && backupState is BackupState.Progress) {
                    val progressPercent = (backupState as BackupState.Progress).percent
                    LinearProgressIndicator(
                        progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_restoring), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedButton(
                        // .palmnote 没有注册的 MIME 类型，部分文件管理器会把它判为不可选而灰掉；
                        // 放宽到任意类型，选错文件由文件头 MAGIC 校验拦下
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backupState !is BackupState.Progress
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_restore_from_file))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.backup_restore_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Restore password dialog
    restoreSource?.let { source ->
        var restorePassword by remember { mutableStateOf("") }
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
}

/** 待恢复的备份来源：SAF 文件（用户选择/备份文件夹）或本机内部存储备份。 */
private sealed interface RestoreSource {
    data class FromUri(val uri: Uri) : RestoreSource
    data class FromLocal(val filePath: String) : RestoreSource
}

/** 一条可恢复的备份：本机自动备份或备份文件夹内的包，两者统一按时间倒序展示。 */
private data class RestoreCandidate(val date: Long, val size: Long, val source: RestoreSource)

/** 读备份文件头 MAGIC，判断是否为加密备份（仅加密包恢复时才需要密码）。 */
private fun isEncryptedBackupFile(context: Context, source: RestoreSource): Boolean = try {
    val magic = ByteArray(4)
    val read = when (source) {
        is RestoreSource.FromUri -> context.contentResolver.openInputStream(source.uri)?.use { it.read(magic) }
        is RestoreSource.FromLocal -> java.io.FileInputStream(source.filePath).use { it.read(magic) }
    }
    read == 4 && (String(magic) == "PNBK" || String(magic) == "PNB2")
} catch (_: Exception) {
    false
}

/** 折叠入口：一行给出两类备份的数量与最新一份的时间，展开后才列出明细。 */
@Composable
private fun RestoreSummaryRow(
    localCount: Int,
    portableCount: Int,
    newestDate: Long,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Restore, contentDescription = null,
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.backup_existing_count, localCount, portableCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = backupTimeText(newestDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单条备份明细：友好时间 + 来源与体积。长串文件名对用户没有信息量，不再展示。 */
@Composable
private fun RestoreCandidateRow(date: Long, size: Long, fromLocal: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = backupTimeText(date),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = backupMetaText(size, fromLocal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

/** 来源 + 体积，形如「本机 · 350 KB」。 */
@Composable
private fun backupMetaText(size: Long, fromLocal: Boolean): String {
    val source = stringResource(if (fromLocal) R.string.backup_source_local else R.string.backup_source_folder)
    val sizeText = formatBackupSize(size)
    return if (sizeText.isEmpty()) source else "$source · $sizeText"
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
