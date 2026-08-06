package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataClearScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DataClearViewModel = hiltViewModel()
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var clearTarget by remember { mutableStateOf("") }
    var clearLabelResId by remember { mutableIntStateOf(R.string.data_clear_asset) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.data_clear_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.data_clear_select), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.data_clear_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))

            ClearCard(
                icon = Icons.Outlined.Inventory2,
                title = stringResource(R.string.data_clear_asset),
                subtitle = stringResource(R.string.data_clear_asset_desc),
                color = InfoBlue
            ) {
                clearTarget = "asset"; clearLabelResId = R.string.data_clear_asset; showConfirmDialog = true
            }

            ClearCard(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = stringResource(R.string.data_clear_bill),
                subtitle = stringResource(R.string.data_clear_bill_desc),
                color = AccentOrange
            ) {
                clearTarget = "bill"; clearLabelResId = R.string.data_clear_bill; showConfirmDialog = true
            }

            ClearCard(
                icon = Icons.Outlined.Favorite,
                title = stringResource(R.string.data_clear_life),
                subtitle = stringResource(R.string.data_clear_life_desc),
                color = ErrorLight
            ) {
                clearTarget = "life"; clearLabelResId = R.string.data_clear_life; showConfirmDialog = true
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ClearCard(
                icon = Icons.Outlined.DeleteForever,
                title = stringResource(R.string.data_clear_all),
                subtitle = stringResource(R.string.data_clear_all_desc),
                color = ErrorLight
            ) {
                clearTarget = "all"; clearLabelResId = R.string.data_clear_all; showConfirmDialog = true
            }
        }
    }

    if (showConfirmDialog) {
        AppDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.data_clear_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.data_clear_confirm_message, stringResource(clearLabelResId))) },
            confirmButton = {
                TextButton(onClick = {
                    when (clearTarget) {
                        "asset" -> viewModel.clearAssets()
                        "bill" -> viewModel.clearBills()
                        "life" -> viewModel.clearLife()
                        "all" -> viewModel.clearAll()
                    }
                    showConfirmDialog = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.data_clear_action), color = ErrorLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
private fun ClearCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
