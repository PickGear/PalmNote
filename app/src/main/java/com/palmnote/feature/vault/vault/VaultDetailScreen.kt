package com.palmnote.feature.vault.vault

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import com.palmnote.app.R
import com.palmnote.feature.vault.VaultEntry
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.vaultTint
import kotlinx.coroutines.launch

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
    val autoLockMode by viewModel.autoLockMode.collectAsStateWithLifecycle()
    val autoLockTimeoutMinutes by viewModel.autoLockTimeoutMinutes.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    var showForgotPinConfirm by remember { mutableStateOf(false) }

    // 锁定/自动锁定后隐藏明文，避免重新解锁后残留显示
    LaunchedEffect(state.lockState) {
        if (state.lockState != LockState.UNLOCKED) {
            showPassword = false
        }
    }

    VaultLockOnBackground(
        lock = viewModel::lock,
        requireAuth = { state.requireAuth },
        autoLockMode = autoLockMode,
        autoLockTimeoutMinutes = autoLockTimeoutMinutes
    )

    if (state.lockState != LockState.UNLOCKED) {
        VaultLockGate(
            lockState = state.lockState,
            error = state.gateError,
            lockoutRemainingMs = state.lockoutRemainingMs,
            biometricEnabled = state.biometricEnabled,
            onBiometricUnlock = viewModel::unlockBiometric,
            onSetup = viewModel::setupPin,
            onUnlock = viewModel::unlock,
            onForgotPin = { showForgotPinConfirm = true },
            onPinTyped = viewModel::clearGateError
        )
        if (showForgotPinConfirm) {
            VaultForgotPinDialog(
                onConfirm = {
                    showForgotPinConfirm = false
                    viewModel.resetForVaultLockout()
                },
                onDismiss = { showForgotPinConfirm = false }
            )
        }
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardClearSeconds by viewModel.clipboardClearSeconds.collectAsStateWithLifecycle()
    val showCopied: () -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar(
                if (clipboardClearSeconds > 0) context.getString(R.string.vault_copied_autoclear, clipboardClearSeconds)
                else context.getString(R.string.vault_copied)
            )
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = entry.title,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
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
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══════════════════════════════════════
            // 登录头部（图标 + 标题 + 分类徽章）
            // ═══════════════════════════════════════
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(vaultTint().copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.avatarPath.isNotBlank()) {
                            AsyncImage(
                                model = File(entry.avatarPath),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = entryIcon(entry.url),
                                contentDescription = null,
                                tint = vaultTint(),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.category.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = vaultTint().copy(alpha = 0.15f),
                            contentColor = vaultTint(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = entry.category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // 登录信息
            // ═══════════════════════════════════════
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                SectionHeader(null, stringResource(R.string.vault_section_login))
                Spacer(Modifier.height(4.dp))
                if (entry.username.isNotEmpty()) {
                    DetailRow(
                        icon = Icons.Outlined.Person,
                        label = stringResource(R.string.vault_field_username),
                        value = entry.username,
                        onCopy = {
                            if (viewModel.copyUsername(entry)) showCopied()
                        }
                    )
                }
                if (entry.email.isNotEmpty()) {
                    DetailRow(
                        icon = Icons.Outlined.Mail,
                        label = stringResource(R.string.vault_field_email),
                        value = entry.email,
                        onCopy = {
                            if (viewModel.copyEmail(entry)) showCopied()
                        }
                    )
                }
                DetailRow(
                    icon = Icons.Outlined.Key,
                    label = stringResource(R.string.vault_field_password),
                    value = if (showPassword) displayPassword else maskedPassword(displayPassword),
                    isMasked = true,
                    onToggleVisibility = { showPassword = !showPassword },
                    onCopy = {
                        if (viewModel.copyPassword(entry)) showCopied()
                    }
                )
            }

            // ═══════════════════════════════════════
            // 站点信息
            // ═══════════════════════════════════════
            if (entry.url.isNotEmpty()) {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    SectionHeader(null, stringResource(R.string.vault_section_site))
                    Spacer(Modifier.height(4.dp))
                    DetailRow(
                        icon = Icons.Outlined.Link,
                        label = stringResource(R.string.vault_field_url),
                        value = entry.url,
                        modifier = Modifier.clickable {
                            try {
                                val url = entry.url.let {
                                    if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {}
                        },
                        onCopy = {
                            if (viewModel.copyUrl(entry)) showCopied()
                        }
                    )
                }
            }

            // ═══════════════════════════════════════
            // 备注
            // ═══════════════════════════════════════
            if (entry.notes.isNotEmpty()) {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    SectionHeader(null, stringResource(R.string.vault_section_notes))
                    Spacer(Modifier.height(4.dp))
                    DetailRow(
                        icon = Icons.AutoMirrored.Outlined.Notes,
                        label = stringResource(R.string.vault_field_notes),
                        value = entry.notes,
                        multiline = true,
                        onCopy = {
                            if (viewModel.copyNotes(entry)) showCopied()
                        }
                    )
                }
            }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    onToggleVisibility: (() -> Unit)? = null,
    isMasked: Boolean = false,
    multiline: Boolean = false
) {
    Column(modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = vaultTint(),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 26.dp),
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onToggleVisibility != null) {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (isMasked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.vault_toggle_visibility)
                    )
                }
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.vault_copy),
                        tint = vaultTint()
                    )
                }
            }
        }
    }
}

/** 分组标题：图标 + 标题。 */
@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = vaultTint()
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
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

/** 按真实密码长度生成遮罩，避免固定长度与内容不符。 */
private fun maskedPassword(plain: String): String =
    "•".repeat(plain.length.coerceAtLeast(MASKED_PASSWORD.length))
