package com.palmnote.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.data.backup.BackupInfo
import com.palmnote.data.backup.BackupState
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import com.palmnote.domain.util.DateUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel()
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreDialog by remember { mutableStateOf<BackupInfo?>(null) }
    val context = LocalContext.current

    // 处理操作结果
    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupState.Success -> {
                snackbarHostState.showSnackbar(context.getString(R.string.backup_operation_success))
                viewModel.resetState()
            }
            is BackupState.Error -> {
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 立即备份按钮
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_create), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_create_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (backupState is BackupState.Progress) {
                        LinearProgressIndicator(
                            progress = { (backupState as BackupState.Progress).percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.backup_backing_up), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button(
                            onClick = { viewModel.createBackup() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = backupState !is BackupState.Progress
                        ) {
                            Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_now))
                        }
                    }
                }
            }

            // 备份列表
            item {
                Text(stringResource(R.string.backup_existing), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (backups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.backup_empty),
                        subtitle = stringResource(R.string.backup_empty_hint)
                    )
                }
            } else {
                items(backups, key = { it.filePath }) { backup ->
                    BackupItem(
                        backup = backup,
                        onRestore = { showRestoreDialog = backup },
                        onDelete = { viewModel.deleteBackup(File(backup.filePath)) }
                    )
                }
            }
        }
    }

    // 恢复确认对话框
    showRestoreDialog?.let { backup ->
        AppDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text(stringResource(R.string.backup_restore_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.backup_restore_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreBackup(File(backup.filePath))
                    showRestoreDialog = null
                }) {
                    Text(stringResource(R.string.backup_restore), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun BackupItem(
    backup: BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    // DateUtils.formatDateTime already provides "yyyy-MM-dd HH:mm" format
    val sizeText = remember(backup.size) {
        when {
            backup.size < 1024 -> "${backup.size} B"
            backup.size < 1024 * 1024 -> "${backup.size / 1024} KB"
            else -> "${backup.size / (1024 * 1024)} MB"
        }
    }

    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(backup.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${DateUtils.formatDateTime(backup.date)} · $sizeText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.backup_restore)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
