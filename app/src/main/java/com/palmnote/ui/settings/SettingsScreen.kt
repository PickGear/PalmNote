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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CompactTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.palmnote.R
import com.palmnote.ui.theme.*
import com.palmnote.ui.theme.AppIcon
import java.io.File
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale


@Composable
fun SettingsMenuItem(icon: ImageVector, title: String, subtitle: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector = Icons.Filled.Settings, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(modifier = Modifier.size(20.dp).background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RowScope.SettingRowContent(title: String, subtitle: String? = null, value: String? = null, showChevron: Boolean = false) {
    Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (value != null || showChevron) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showChevron) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingRow(clickable: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val mod = if (clickable != null) Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = clickable) else Modifier.fillMaxWidth()
    Row(modifier = mod.padding(vertical = 12.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToGeneral: () -> Unit = {},
    onNavigateToReminder: () -> Unit = {},
    onNavigateToManageCategory: () -> Unit = {},
    onNavigateToDataStorage: () -> Unit = {},
    onNavigateToAppLock: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showProfileEdit by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ModuleSettings) },
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showProfileEdit = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val avatarIcon = try { AppIcon.valueOf(state.profileAvatar).imageVector } catch (_: Exception) { Icons.Filled.Spa }
                            Box(
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(PrimaryGreenLight.copy(alpha = 0.15f)),
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
                                    Icon(avatarIcon, null, tint = PrimaryGreenLight, modifier = Modifier.size(28.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.profileNickname.isNotBlank()) state.profileNickname else stringResource(R.string.app_name),
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
                            StatItemSmall(Icons.Outlined.Inventory2, "${state.assetCount}", stringResource(R.string.settings_items), InfoBlue)
                            StatItemSmall(Icons.Outlined.Flag, "${state.goalCount}", stringResource(R.string.settings_goals), LifePlan)
                            StatItemSmall(Icons.Outlined.FavoriteBorder, "${state.anniversaryCount}", stringResource(R.string.settings_anniversaries), AccentOrange)
                            StatItemSmall(Icons.Outlined.AutoAwesome, "${state.momentCount}", stringResource(R.string.settings_moments), ModuleLife)
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
                        SettingsRowItem(icon = Icons.Outlined.Palette, title = stringResource(R.string.settings_appearance), subtitle = stringResource(R.string.settings_appearance_subtitle), tint = LifePlan, onClick = onNavigateToGeneral)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRowItem(icon = Icons.Outlined.Notifications, title = stringResource(R.string.settings_reminder), subtitle = stringResource(R.string.settings_reminder_subtitle), tint = AccentOrange, onClick = onNavigateToReminder)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRowItem(icon = Icons.Outlined.Category, title = stringResource(R.string.settings_category_manage), subtitle = stringResource(R.string.settings_category_manage_subtitle), tint = InfoBlue, onClick = onNavigateToManageCategory)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRowItem(icon = Icons.Outlined.Storage, title = stringResource(R.string.settings_data), subtitle = stringResource(R.string.settings_data_subtitle), tint = LifeRecord, onClick = onNavigateToDataStorage)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRowItem(icon = Icons.Outlined.Lock, title = stringResource(R.string.settings_security), subtitle = if (state.appLockEnabled) stringResource(R.string.settings_security_subtitle_on) else stringResource(R.string.settings_security_subtitle), tint = ModuleSettings, onClick = onNavigateToAppLock)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRowItem(icon = Icons.Outlined.Info, title = stringResource(R.string.settings_about_app), subtitle = stringResource(R.string.settings_about_version), tint = PrimaryGreenLight, onClick = onNavigateToAbout)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showProfileEdit) {
        var editName by remember { mutableStateOf(state.profileNickname) }
        var editSig by remember { mutableStateOf(state.profileSignature) }
        var editAvatar by remember { mutableStateOf(state.profileAvatar) }
        var editAvatarPath by remember { mutableStateOf(state.profileAvatarPath) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val avatarDir = File(context.filesDir, "avatars")
                        avatarDir.mkdirs()
                        val destFile = File(avatarDir, "profile_avatar.jpg")
                        inputStream?.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        editAvatarPath = destFile.absolutePath
                        editAvatar = ""
                    } catch (e: Exception) { }
                }
            }
        }

        AppDialog(
            onDismissRequest = { showProfileEdit = false },
            title = { Text(stringResource(R.string.settings_profile_edit), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(PrimaryGreenLight.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (editAvatarPath.isNotBlank()) {
                                AsyncImage(
                                    model = File(editAvatarPath),
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val icon = try { AppIcon.valueOf(editAvatar).imageVector } catch (_: Exception) { Icons.Filled.Spa }
                                Icon(icon, null, tint = PrimaryGreenLight, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text(stringResource(R.string.settings_profile_change_avatar))
                        }
                        if (editAvatarPath.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { editAvatarPath = ""; editAvatar = "Spa" }) {
                                Text(stringResource(R.string.settings_profile_reset_avatar))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.settings_profile_nickname)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = editSig,
                        onValueChange = { editSig = it },
                        label = { Text(stringResource(R.string.settings_profile_signature)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setProfileNickname(editName)
                    viewModel.setProfileSignature(editSig)
                    viewModel.setProfileAvatar(editAvatar)
                    viewModel.setProfileAvatarPath(editAvatarPath)
                    showProfileEdit = false
                }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEdit = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
private fun SettingsRowItem(icon: ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
