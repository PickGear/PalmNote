package com.palmnote.feature.vault.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.feature.vault.PasswordStrength
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.feature.vault.VaultPasswordGenerator
import com.palmnote.ui.components.SecondaryTopAppBar

/**
 * 密码本新增/编辑页：表单 + 密码生成器入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultEditScreen(
    viewModel: VaultEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showForgotPinConfirm by remember { mutableStateOf(false) }

    VaultLockOnBackground(viewModel::lock) { state.requireAuth }

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

    // 编辑模式：加载现有条目填充表单
    LaunchedEffect(state.entry?.id) {
        val entry = state.entry ?: return@LaunchedEffect
        title = entry.title
        username = entry.username
        password = viewModel.passwordForDisplay(entry) ?: ""
        url = entry.url
        notes = entry.notes
        category = entry.category
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val titleRequired = stringResource(R.string.vault_error_title_required)
    val passwordRequired = stringResource(R.string.vault_error_password_required)
    val saveFailed = stringResource(R.string.vault_error_save_failed)

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = stringResource(
                    if (state.isEdit) R.string.vault_edit_title else R.string.vault_add_title
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
                    IconButton(
                        onClick = {
                            if (saving) return@IconButton
                            error = null
                            if (title.isBlank()) {
                                error = titleRequired
                                return@IconButton
                            }
                            if (password.isBlank()) {
                                error = passwordRequired
                                return@IconButton
                            }
                            saving = true
                            viewModel.save(
                                title = title,
                                username = username,
                                password = password,
                                url = url,
                                notes = notes,
                                category = category,
                                onResult = { ok ->
                                    saving = false
                                    if (ok) {
                                        onNavigateBack()
                                    } else {
                                        error = saveFailed
                                    }
                                }
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.common_save)
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                label = { Text(stringResource(R.string.vault_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.vault_field_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text(stringResource(R.string.vault_field_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(R.string.vault_toggle_visibility)
                            )
                        }
                        IconButton(onClick = { showGenerator = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Shuffle,
                                contentDescription = stringResource(R.string.vault_generator_title)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (password.isNotEmpty()) {
                val (label, color, fraction) = passwordStrength(password)
                Text(
                    text = stringResource(R.string.vault_edit_password_strength, label),
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.15f)
                )
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.vault_field_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.vault_field_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it; error = null },
                    label = { Text(stringResource(R.string.vault_field_category_input)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showGenerator) {
        VaultPasswordGeneratorSheet(
            onUse = { generated ->
                password = generated
                error = null
            },
            onDismiss = { showGenerator = false }
        )
    }
}

/** 密码强度提示（空文本不显示）。返回 标签 / 颜色 / 进度分数。 */
@Composable
private fun passwordStrength(text: String): Triple<String, Color, Float> {
    if (text.isEmpty()) return Triple("", MaterialTheme.colorScheme.outline, 0f)
    return when (VaultPasswordGenerator.strengthOf(VaultPasswordGenerator.estimateEntropy(text))) {
        PasswordStrength.WEAK -> Triple(
            stringResource(R.string.vault_strength_weak),
            MaterialTheme.colorScheme.error,
            0.25f
        )
        PasswordStrength.MEDIUM -> Triple(
            stringResource(R.string.vault_strength_medium),
            MaterialTheme.colorScheme.tertiary,
            0.5f
        )
        PasswordStrength.STRONG -> Triple(
            stringResource(R.string.vault_strength_strong),
            MaterialTheme.colorScheme.primary,
            0.75f
        )
        PasswordStrength.VERY_STRONG -> Triple(
            stringResource(R.string.vault_strength_very_strong),
            MaterialTheme.colorScheme.primary,
            1f
        )
    }
}
