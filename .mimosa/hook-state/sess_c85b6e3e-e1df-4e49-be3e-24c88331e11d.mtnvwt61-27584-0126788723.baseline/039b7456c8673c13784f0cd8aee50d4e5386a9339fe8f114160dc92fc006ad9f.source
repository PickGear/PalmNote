package com.palmnote.feature.vault.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.feature.vault.PasswordStrength
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.feature.vault.VaultPasswordGenerator
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.saveImageToVaultStorage
import com.palmnote.ui.theme.vaultTint
import java.io.File
import kotlinx.coroutines.launch

/**
 * 密码本新增/编辑页：登录图标头部 + 分组卡片表单 + 密码生成器入口。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultEditScreen(
    viewModel: VaultEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val autoLockMode by viewModel.autoLockMode.collectAsStateWithLifecycle()
    val autoLockTimeoutMinutes by viewModel.autoLockTimeoutMinutes.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf("") }
    // 进入编辑时的原头像路径：保存成功后才清理被替换/移除的旧文件
    var originalAvatar by remember { mutableStateOf("") }
    var avatarSaved by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showForgotPinConfirm by remember { mutableStateOf(false) }

    // 锁定/自动锁定后隐藏明文，避免重新解锁后残留显示
    LaunchedEffect(state.lockState) {
        if (state.lockState != com.palmnote.feature.vault.VaultLockManager.LockState.UNLOCKED) {
            passwordVisible = false
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

    // 编辑模式：加载现有条目填充表单
    LaunchedEffect(state.entry?.id) {
        val entry = state.entry ?: return@LaunchedEffect
        title = entry.title
        username = entry.username
        email = entry.email
        phone = entry.phone
        password = viewModel.passwordForDisplay(entry) ?: ""
        url = entry.url
        notes = entry.notes
        category = entry.category
        avatarPath = entry.avatarPath
        originalAvatar = entry.avatarPath
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                saveImageToVaultStorage(context, uri)?.let { path ->
                    avatarPath = path
                }
            }
        }
    }
    fun removeAvatar() {
        avatarPath = ""
    }

    // 离开编辑页且未保存时：清理本次新挑选但未入库的头像文件（防止孤儿文件）
    DisposableEffect(Unit) {
        onDispose {
            if (!avatarSaved) {
                val pending = avatarPath
                if (pending.isNotBlank() && pending != originalAvatar) {
                    runCatching { File(pending).delete() }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = stringResource(
                    if (state.isEdit) R.string.vault_edit_title else R.string.vault_add_title
                ),
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
                    IconButton(
                        onClick = {
                            if (saving) return@IconButton
                            titleError = null
                            passwordError = null
                            saveError = null
                            if (title.isBlank()) {
                                titleError = titleRequired
                                return@IconButton
                            }
                            if (password.isBlank()) {
                                passwordError = passwordRequired
                                return@IconButton
                            }
                            saving = true
                            viewModel.save(
                                title = title,
                                username = username,
                                email = email,
                                phone = phone,
                                password = password,
                                url = url,
                                notes = notes,
                                category = category,
                                avatarPath = avatarPath,
                                onResult = { ok ->
                                    saving = false
                                    if (ok) {
                                        avatarSaved = true
                                        // 保存成功：若原头像被替换或移除，清理旧文件
                                        if (originalAvatar.isNotBlank() && originalAvatar != avatarPath) {
                                            runCatching { File(originalAvatar).delete() }
                                        }
                                        onNavigateBack()
                                    } else {
                                        saveError = saveFailed
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
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══════════════════════════════════════
            // 登录头部（图标 + 标题输入）
            // ═══════════════════════════════════════
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(vaultTint().copy(alpha = 0.15f))
                            .clickable { avatarPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarPath.isNotBlank()) {
                            AsyncImage(
                                model = File(avatarPath),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = entryIcon(url),
                                contentDescription = null,
                                tint = vaultTint(),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    if (avatarPath.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { removeAvatar() }) {
                            Text(
                                text = stringResource(R.string.vault_avatar_remove),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.vault_avatar_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; titleError = null },
                        label = { Text(stringResource(R.string.vault_field_title)) },
                        singleLine = true,
                        isError = titleError != null,
                        supportingText = titleError?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.medium,
                        colors = vaultFieldDefaults(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ═══════════════════════════════════════
            // 登录信息
            // ═══════════════════════════════════════
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                SectionHeader(null, stringResource(R.string.vault_section_login))
                Spacer(Modifier.height(4.dp))

                VaultTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.vault_field_username),
                    leading = Icons.Outlined.Person
                )
                Spacer(Modifier.height(10.dp))

                VaultTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.vault_field_email),
                    leading = Icons.Outlined.Mail
                )
                Spacer(Modifier.height(10.dp))

                VaultTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = stringResource(R.string.vault_field_phone),
                    leading = Icons.Outlined.PhoneAndroid,
                    keyboardType = KeyboardType.Phone
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = null },
                    label = { Text(stringResource(R.string.vault_field_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } },
                    leadingIcon = { Icon(Icons.Outlined.Key, null, tint = vaultTint()) },
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
                                    contentDescription = stringResource(R.string.vault_generate_password),
                                    tint = vaultTint()
                                )
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    PasswordStrengthBar(password = password)
                }
                if (saveError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = saveError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ═══════════════════════════════════════
            // 站点信息
            // ═══════════════════════════════════════
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                SectionHeader(null, stringResource(R.string.vault_section_site))
                Spacer(Modifier.height(4.dp))

                VaultTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.vault_field_url),
                    keyboardType = KeyboardType.Uri
                )

                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.vault_field_category_input),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = vaultTint().copy(alpha = 0.15f),
                                    selectedLabelColor = vaultTint()
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.vault_field_category_input)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = vaultFieldDefaults(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ═══════════════════════════════════════
            // 备注
            // ═══════════════════════════════════════
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                SectionHeader(null, stringResource(R.string.vault_section_notes))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.vault_field_notes)) },
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showGenerator) {
        VaultPasswordGeneratorSheet(
            onUse = { generated ->
                password = generated
                passwordError = null
            },
            onDismiss = { showGenerator = false }
        )
    }
}

/** 站点信息区使用的通用输入框（带 leading 图标）。 */
@Composable
private fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = if (leading != null) {
            { Icon(leading, null, tint = vaultTint()) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
        colors = vaultFieldDefaults(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun vaultFieldDefaults() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = vaultTint(),
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

/** 分组标题：可选图标 + 标题。 */
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

/** 密码强度分段条：4 段格子显示，段数与当前强度匹配点亮。 */
@Composable
private fun PasswordStrengthBar(password: String) {
    val (label, color, fraction) = passwordStrength(password)
    val segments = 4
    val filled = (fraction * segments).toInt().coerceIn(0, segments)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(segments) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (index < filled) color else color.copy(alpha = 0.15f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.vault_edit_password_strength, label),
            style = MaterialTheme.typography.bodySmall,
            color = color
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
            vaultTint(),
            0.75f
        )
        PasswordStrength.VERY_STRONG -> Triple(
            stringResource(R.string.vault_strength_very_strong),
            vaultTint(),
            1f
        )
}
}
