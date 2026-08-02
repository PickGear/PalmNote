package com.palmnote.feature.vault.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.feature.vault.VaultEntry
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.ui.components.CompactTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 密码本列表页：锁定门 → 搜索/分类筛选 → 条目列表。
 */
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    VaultLockOnBackground(viewModel::lock) { state.requireAuth }

    if (state.lockState != LockState.UNLOCKED) {
        VaultLockGate(
            lockState = state.lockState,
            error = state.pinError,
            lockoutRemainingMs = state.lockoutRemainingMs,
            onSetup = viewModel::setupPin,
            onUnlock = viewModel::unlock
        )
        return
    }

    VaultListContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelect = viewModel::onCategorySelect,
        onEntryClick = onNavigateToDetail,
        onCopy = viewModel::copyPassword,
        onAdd = onNavigateToEdit,
        onBack = onNavigateBack
    )
}
/**
 * 切到后台立即锁定（清除内存密钥与剪贴板）。requireAuth 关闭时跳过（安全降级）。
 */
@Composable
fun VaultLockOnBackground(lock: () -> Unit, requireAuth: () -> Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestLock by rememberUpdatedState(lock)
    val latestRequireAuth by rememberUpdatedState(requireAuth)
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (latestRequireAuth()) {
                    latestLock()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun VaultListContent(
    state: VaultUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onEntryClick: (Long) -> Unit,
    onCopy: (VaultEntry) -> Boolean,
    onAdd: () -> Unit,
    onBack: () -> Unit
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copiedText = stringResource(R.string.vault_copied)

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.vault_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.vault_search)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = stringResource(R.string.vault_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CategoryFilterRow(
                categories = state.categories,
                selected = state.category,
                onSelect = onCategorySelect
            )
            if (searchExpanded) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.vault_search_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.entries.isEmpty()) {
                VaultEmptyState(query = state.query)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        VaultEntryCard(
                            entry = entry,
                            onClick = { onEntryClick(entry.id) },
                            onCopy = {
                                if (onCopy(entry)) {
                                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (selected == null) {
                        stringResource(R.string.vault_all_categories)
                    } else {
                        selected
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_all_categories)) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onSelect(category)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultEntryCard(
    entry: VaultEntry,
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entryIcon(entry.url),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.username.isNotEmpty()) {
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(R.string.vault_updated_at, formatDate(entry.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.vault_copy_password),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun VaultEmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (query.isBlank()) R.string.vault_empty else R.string.vault_no_result),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (query.isBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vault_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun entryIcon(url: String): ImageVector {
    val normalized = url.lowercase(Locale.ROOT)
    return when {
        normalized.contains("github") -> Icons.Outlined.Code
        normalized.contains("bank") || normalized.contains("alipay") || normalized.contains("pay") -> Icons.Outlined.AccountBalance
        else -> Icons.Outlined.Key
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
