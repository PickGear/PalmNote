package com.palmnote.feature.vault.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.feature.vault.VaultEntry
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar

/**
 * 密码本详情页：密码默认遮罩，点击 👁 切换明文；各字段可复制。
 */
@Composable
fun VaultDetailScreen(
    viewModel: VaultDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    VaultLockOnBackground(viewModel::lock) { state.requireAuth }

    if (state.lockState != LockState.UNLOCKED) {
        VaultLockGate(
            lockState = state.lockState,
            error = null,
            lockoutRemainingMs = 0L,
            onSetup = viewModel::setupPin,
            onUnlock = viewModel::unlock
        )
        return
    }

    val entry = state.entry

    if (state.deleted) {
        VaultDetailDeleted(onNavigateBack)
        return
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (entry == null) {
        VaultDetailDeleted(onNavigateBack)
        return
    }

    val displayPassword = remember(entry) {
        viewModel.passwordForDisplay(entry) ?: ""
    }

    val context = LocalContext.current
    val copiedText = stringResource(R.string.vault_copied)
    val showCopied: () -> Unit = {
        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = entry.title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.settings_more)
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vault_edit)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToEdit(entry.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vault_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                confirmDelete = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (entry.username.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.vault_field_username),
                    value = entry.username,
                    onCopy = {
                        if (viewModel.copyUsername(entry)) {
                            showCopied()
                        }
                    }
                )
            }
            DetailRow(
                label = stringResource(R.string.vault_field_password),
                value = if (showPassword) displayPassword else MASKED_PASSWORD,
                isMasked = true,
                onToggleVisibility = { showPassword = !showPassword },
                onCopy = {
                    if (viewModel.copyPassword(entry)) {
                        showCopied()
                    }
                }
            )
            if (entry.url.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.vault_field_url),
                    value = entry.url,
                    onCopy = {
                        if (viewModel.copyUrl(entry)) {
                            showCopied()
                        }
                    }
                )
            }
            if (entry.notes.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.vault_field_notes),
                    value = entry.notes,
                    multiline = true
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.vault_field_category, entry.category),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmDelete) {
        AppDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vault_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.vault_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.vault_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
    onToggleVisibility: (() -> Unit)? = null,
    isMasked: Boolean = false,
    multiline: Boolean = false
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onToggleVisibility != null) {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (isMasked) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(R.string.vault_toggle_visibility)
                    )
                }
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.vault_copy)
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultDetailDeleted(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.vault_deleted),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_deleted_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_navigate_back),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(onClick = onNavigateBack)
        )
    }
}

private const val MASKED_PASSWORD = "••••••••"
