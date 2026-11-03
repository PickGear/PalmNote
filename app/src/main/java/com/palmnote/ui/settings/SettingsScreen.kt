package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CropPurpose
import com.palmnote.ui.components.ImageCropDialog
import com.palmnote.ui.components.SettingsMenuItem
import com.palmnote.ui.components.CompactTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.palmnote.app.BuildConfig
import com.palmnote.app.R
import com.palmnote.ui.theme.*
import java.io.File
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToGeneral: () -> Unit = {},
    onNavigateToReminder: () -> Unit = {},
    onNavigateToManageCategory: () -> Unit = {},
    onNavigateToDataStorage: () -> Unit = {},
    onNavigateToWidget: () -> Unit = {},
    onNavigateToAppLock: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showProfileEdit by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_title),
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showProfileEdit = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.profileAvatarPath.isNotBlank()) {
                                    AsyncImage(
                                        model = File(state.profileAvatarPath),
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(ModuleHome),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "P",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.profileNickname.isNotBlank()) state.profileNickname else "PalmNote",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                                )
                                if (state.profileSignature.isNotBlank()) {
                                    Text(state.profileSignature, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            // 统计条用设置页多巴胺装饰色（视觉节奏；归属语义见各模块页）
                            StatItemSmall(
                                Icons.Outlined.Inventory2, "${state.assetCount}",
                                stringResource(R.string.settings_items), DopamineSky
                            )
                            StatItemSmall(
                                Icons.Outlined.Flag, "${state.goalCount}",
                                stringResource(R.string.settings_goals), DopamineCoral
                            )
                            StatItemSmall(
                                Icons.Outlined.FavoriteBorder, "${state.anniversaryCount}",
                                stringResource(R.string.settings_anniversaries), DopamineViolet
                            )
                            StatItemSmall(
                                Icons.Outlined.AutoAwesome, "${state.momentCount}",
                                stringResource(R.string.settings_moments), DopaminePink
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        // 设置项用多巴胺装饰色（每项一个稳定色，纯视觉节奏，不表达归属/状态）
                        SettingsMenuItem(
                            icon = Icons.Outlined.Palette,
                            title = stringResource(R.string.settings_appearance),
                            subtitle = stringResource(R.string.settings_appearance_subtitle),
                            tint = DopamineCoral,
                            onClick = onNavigateToGeneral
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(
                            icon = Icons.Outlined.Notifications,
                            title = stringResource(R.string.settings_reminder),
                            subtitle = stringResource(R.string.settings_reminder_subtitle),
                            tint = DopamineAmber,
                            onClick = onNavigateToReminder
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(
                            icon = Icons.Outlined.Category,
                            title = stringResource(R.string.settings_category_manage),
                            subtitle = stringResource(R.string.settings_category_manage_subtitle),
                            tint = DopamineMint,
                            onClick = onNavigateToManageCategory
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(
                            icon = Icons.Outlined.Storage,
                            title = stringResource(R.string.settings_data),
                            subtitle = stringResource(R.string.settings_data_subtitle),
                            tint = DopamineSky,
                            onClick = onNavigateToDataStorage
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(
                            icon = Icons.Outlined.Widgets,
                            title = stringResource(R.string.settings_widget_title),
                            subtitle = stringResource(R.string.settings_widget_subtitle),
                            tint = DopaminePink,
                            onClick = onNavigateToWidget
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(
                            icon = Icons.Outlined.Lock,
                            title = stringResource(R.string.settings_security),
                            subtitle = if (state.appLockEnabled) {
                                stringResource(R.string.settings_security_subtitle_on)
                            } else {
                                stringResource(R.string.settings_security_subtitle)
                            },
                            tint = DopamineViolet,
                            onClick = onNavigateToAppLock
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.settings_about_app),
                            subtitle = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onNavigateToAbout
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showProfileEdit) {
        ProfileEditDialog(
            initialNickname = state.profileNickname,
            initialSignature = state.profileSignature,
            initialAvatar = state.profileAvatar,
            initialAvatarPath = state.profileAvatarPath,
            savedAvatarPath = state.profileAvatarPath,
            onDismiss = { showProfileEdit = false },
            onSave = { nickname, signature, avatar, avatarPath ->
                viewModel.setProfileNickname(nickname)
                viewModel.setProfileSignature(signature)
                viewModel.setProfileAvatar(avatar)
                viewModel.setProfileAvatarPath(avatarPath)
            }
        )
    }
}

@Composable
private fun StatItemSmall(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 资料编辑弹窗：昵称 / 签名 / 头像。
 *
 * 头像走统一裁剪（1:1 / 512）；本次新选但未保存的文件在取消时删除，
 * 换头像保存后删除上一张已保存的文件——避免 avatars/ 目录堆积孤儿。
 */
@Composable
private fun ProfileEditDialog(
    initialNickname: String,
    initialSignature: String,
    initialAvatar: String,
    initialAvatarPath: String,
    savedAvatarPath: String,
    onDismiss: () -> Unit,
    onSave: (nickname: String, signature: String, avatar: String, avatarPath: String) -> Unit
) {
    var editName by remember { mutableStateOf(initialNickname) }
    var editSig by remember { mutableStateOf(initialSignature) }
    var editAvatar by remember { mutableStateOf(initialAvatar) }
    var editAvatarPath by remember { mutableStateOf(initialAvatarPath) }
    var replacedPath by remember { mutableStateOf<String?>(null) }

    fun discardUnsavedAvatar(keep: String?) {
        val picked = replacedPath
        if (picked != null && picked != keep) runCatching { File(picked).delete() }
        replacedPath = null
    }

    fun closeWithoutSave() {
        discardUnsavedAvatar(keep = null)
        onDismiss()
    }

    // 头像选图 + 统一裁剪；本次新选但未保存的文件记入 replacedPath，保存/关闭时结算
    val launchAvatarPicker = ProfileAvatarCropLauncher { path ->
        discardUnsavedAvatar(keep = null)
        replacedPath = path
        editAvatarPath = path
        editAvatar = ""
    }

    // 旋转/进程重建销毁弹窗时：本次新裁、尚未入库的头像文件就地清理（与密码本编辑页同口径；
    // 显式取消/保存路径各自结算后 replacedPath 已为 null，此处幂等不误删）
    DisposableEffect(Unit) {
        onDispose { discardUnsavedAvatar(keep = null) }
    }

    AppDialog(
        onDismissRequest = ::closeWithoutSave,
        title = { Text(stringResource(R.string.settings_profile_edit), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatarRow(
                    avatarPath = editAvatarPath,
                    onPick = { launchAvatarPicker() },
                    onReset = { editAvatarPath = "" }
                )
                ProfileNameFields(
                    name = editName,
                    signature = editSig,
                    onNameChange = { editName = it },
                    onSignatureChange = { editSig = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (savedAvatarPath.isNotBlank() && savedAvatarPath != editAvatarPath) {
                    runCatching { File(savedAvatarPath).delete() }
                }
                discardUnsavedAvatar(keep = editAvatarPath)
                onSave(editName, editSig, editAvatar, editAvatarPath)
                onDismiss()
            }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = ::closeWithoutSave) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * 「我的」页头像的选图 + 裁剪宿主：注册系统照片选择器（13+ 原生网格，旧系统自动
 * 回退文档选择器），选完图进统一裁剪页，返回触发选图的回调；裁剪产物经 [onCropped]
 * 回传，取消不回调。
 */
@Composable
private fun ProfileAvatarCropLauncher(onCropped: (String) -> Unit): () -> Unit {
    // 注意不能用 rememberSaveable：本弹窗由 showProfileEdit（普通 remember）条件组合，
    // 旋转后弹窗不恢复，saveable 的旧 URI 会留在 savedInstanceState 里，下次打开弹窗时
    // 按位置 key 恢复 → 裁剪页凭空自弹。泄漏由上方 DisposableEffect 兜底，行为与密码本一致。
    var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingCropUri = uri
    }
    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            purpose = CropPurpose.PROFILE_AVATAR,
            onCancel = { pendingCropUri = null },
            onDone = { path ->
                pendingCropUri = null
                onCropped(path)
            }
        )
    }
    return {
        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/** 资料弹窗的头像行：圆形预览 + 更换/重置。 */
@Composable
private fun ProfileAvatarRow(avatarPath: String, onPick: () -> Unit, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (avatarPath.isNotBlank()) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(ModuleHome),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "P",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Button(onClick = onPick) {
            Text(stringResource(R.string.settings_profile_change_avatar))
        }
        if (avatarPath.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.settings_profile_reset_avatar))
            }
        }
    }
}

/** 资料弹窗的两个输入框：昵称、签名。 */
@Composable
private fun ProfileNameFields(
    name: String,
    signature: String,
    onNameChange: (String) -> Unit,
    onSignatureChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.settings_profile_nickname)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        OutlinedTextField(
            value = signature,
            onValueChange = onSignatureChange,
            label = { Text(stringResource(R.string.settings_profile_signature)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
    }
}
