package com.palmnote.ui.backup
import androidx.hilt.navigation.compose.hiltViewModel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.backup.BackupInfo
import com.palmnote.data.backup.BackupState
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel()
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 备份目录列表刷新信号（备份成功后 +1 触发重查）
    var backupListRefreshKey by remember { mutableIntStateOf(0) }

    // SAF: backup to user-chosen folder
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { uri ->
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Backup ──
            ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_create), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.backup_create_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                // 导出的备份会经网盘/U 盘等外部渠道流转，必须加密后才允许导出
                if ((password?.length ?: 0) < BackupViewModel.MIN_PASSWORD_LENGTH) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backup_plaintext_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

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
            }

            // ── Restore ──
            ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.backup_restore_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // 备份文件夹内的备份直接列出（无需再导航文件选择器）；备份成功后刷新列表
                val dirBackups = remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
                LaunchedEffect(backupListRefreshKey) { dirBackups.value = viewModel.listBackupsInDir() }

                if (dirBackups.value.isNotEmpty()) {
                    Text(
                        stringResource(R.string.backup_in_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    dirBackups.value.forEach { (name, uri) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { restoreSource = RestoreSource.FromUri(uri) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Restore, contentDescription = null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 本机备份（自动备份 / 恢复前快照）：落在应用私有目录，其他应用与文件管理器都进不去，
                // 若此处不提供入口，这些备份即等同于不可恢复。
                val localBackups = remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
                LaunchedEffect(backupListRefreshKey) { localBackups.value = viewModel.listLocalBackups() }
                if (localBackups.value.isNotEmpty()) {
                    Text(
                        stringResource(R.string.backup_local_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val localDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                    localBackups.value.forEach { info ->
                        LocalBackupRow(
                            info = info,
                            dateText = localDateFormat.format(Date(info.date)),
                            onClick = { restoreSource = RestoreSource.FromLocal(info.filePath) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                        onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backupState !is BackupState.Progress
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_restore_from_file))
                    }
                }
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

/** 本机备份列表项：文件名 + 创建时间 + 体积。 */
@Composable
private fun LocalBackupRow(info: BackupInfo, dateText: String, onClick: () -> Unit) {
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
            Icon(
                Icons.Outlined.Restore, contentDescription = null,
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    info.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.backup_local_subtitle, dateText, (info.size / 1024 / 1024).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
