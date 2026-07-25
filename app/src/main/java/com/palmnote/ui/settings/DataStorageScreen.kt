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
import com.palmnote.R
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataStorageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToDataClear: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToImportBill: () -> Unit,
    viewModel: SettingsViewModel
) {
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appName = stringResource(R.string.app_name)
    val exportSuffix = stringResource(R.string.settings_export_file_suffix)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportData(it) }
    }

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
            item { SectionHeader(stringResource(R.string.settings_data), Icons.Outlined.Storage, InfoBlue) }
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(icon = Icons.Outlined.DeleteOutline, title = stringResource(R.string.settings_recycle_bin), subtitle = stringResource(R.string.settings_recycle_bin_subtitle), tint = StatusActive, onClick = onNavigateToRecycleBin)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.FileDownload, title = stringResource(R.string.settings_export_data), subtitle = stringResource(R.string.settings_export_data_subtitle), tint = InfoBlue, onClick = { exportLauncher.launch(appName + exportSuffix) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.FileUpload, title = stringResource(R.string.settings_import_data), subtitle = stringResource(R.string.settings_import_data_subtitle), tint = AccentOrange, onClick = onNavigateToImportBill)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.Backup, title = stringResource(R.string.settings_data_backup), subtitle = stringResource(R.string.settings_data_backup_subtitle), tint = ModuleLife, onClick = onNavigateToBackup)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.DeleteForever, title = stringResource(R.string.settings_clear_data), subtitle = stringResource(R.string.settings_clear_data_subtitle), tint = ErrorLight, onClick = onNavigateToDataClear)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(icon = Icons.Outlined.CleaningServices, title = stringResource(R.string.settings_clear_cache), subtitle = stringResource(R.string.settings_clear_cache_subtitle), tint = Amber, onClick = { showClearCacheDialog = true })
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
}
