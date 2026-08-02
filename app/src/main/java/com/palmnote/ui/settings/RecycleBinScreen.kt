package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.domain.model.toYuanString
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.recycle_tab_assets), stringResource(R.string.recycle_tab_bills), stringResource(R.string.recycle_tab_goals), stringResource(R.string.recycle_tab_anniversaries), stringResource(R.string.recycle_tab_moments))
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AppDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.recycle_bin_clear_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.recycle_bin_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text(stringResource(R.string.recycle_bin_clear), color = ErrorLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.recycle_bin_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { showClearDialog = true }) {
                        Text(stringResource(R.string.recycle_bin_clear), color = ErrorLight)
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
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val items = when (selectedTab) {
                0 -> state.deletedAssets.map { "${it.name} - ${it.category}" }
                1 -> state.deletedBills.map { "${it.category} ${it.amount.toYuanString()}" }
                2 -> state.deletedGoals.map { it.title }
                3 -> state.deletedAnniversaries.map { it.displayTitle }
                4 -> state.deletedMoments.map { it.title.ifEmpty { it.content.take(20) } }
                else -> emptyList()
            }

            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.recycle_bin_empty),
                    subtitle = stringResource(R.string.recycle_bin_empty_hint)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items.size, key = { index ->
                        val id = when (selectedTab) {
                            0 -> state.deletedAssets.getOrNull(index)?.id ?: index.toLong()
                            1 -> state.deletedBills.getOrNull(index)?.id ?: index.toLong()
                            2 -> state.deletedGoals.getOrNull(index)?.id ?: index.toLong()
                            3 -> state.deletedAnniversaries.getOrNull(index)?.id ?: index.toLong()
                            4 -> state.deletedMoments.getOrNull(index)?.id ?: index.toLong()
                            else -> index.toLong()
                        }
                        "recycle_$id"
                    }) { index ->
                        val item = items[index]
                        ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    TextButton(onClick = {
                                        when (selectedTab) {
                                            0 -> viewModel.restoreAsset(state.deletedAssets[index].id)
                                            1 -> viewModel.restoreBill(state.deletedBills[index].id)
                                            2 -> viewModel.restoreGoal(state.deletedGoals[index].id)
                                            3 -> viewModel.restoreAnniversary(state.deletedAnniversaries[index].id)
                                            4 -> viewModel.restoreMoment(state.deletedMoments[index].id)
                                        }
                                    }) {
                                        Text(stringResource(R.string.recycle_bin_restore), color = MaterialTheme.colorScheme.primary)
                                    }
                                    TextButton(onClick = {
                                        when (selectedTab) {
                                            0 -> viewModel.hardDeleteAsset(state.deletedAssets[index].id)
                                            1 -> viewModel.hardDeleteBill(state.deletedBills[index].id)
                                            2 -> viewModel.hardDeleteGoal(state.deletedGoals[index].id)
                                            3 -> viewModel.hardDeleteAnniversary(state.deletedAnniversaries[index].id)
                                            4 -> viewModel.hardDeleteMoment(state.deletedMoments[index].id)
                                        }
                                    }) {
                                        Text(stringResource(R.string.delete), color = ErrorLight)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
