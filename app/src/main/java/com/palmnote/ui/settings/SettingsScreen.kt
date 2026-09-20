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
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.components.AppDialog
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
                        SettingsMenuItem(icon = Icons.Outlined.Palette, title = stringResource(R.string.settings_appearance), subtitle = stringResource(R.string.settings_appearance_subtitle), tint = LifePlan, onClick = onNavigateToGeneral)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(icon = Icons.Outlined.Notifications, title = stringResource(R.string.settings_reminder), subtitle = stringResource(R.string.settings_reminder_subtitle), tint = AccentOrange, onClick = onNavigateToReminder)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(icon = Icons.Outlined.Category, title = stringResource(R.string.settings_category_manage), subtitle = stringResource(R.string.settings_category_manage_subtitle), tint = InfoBlue, onClick = onNavigateToManageCategory)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(icon = Icons.Outlined.Storage, title = stringResource(R.string.settings_data), subtitle = stringResource(R.string.settings_data_subtitle), tint = LifeRecord, onClick = onNavigateToDataStorage)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuItem(icon = Icons.Outlined.Lock, title = stringResource(R.string.settings_security), subtitle = if (state.appLockEnabled) stringResource(R.string.settings_security_subtitle_on) else stringResource(R.string.settings_security_subtitle), tint = ModuleSettings, onClick = onNavigateToAppLock)
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
        var editName by remember { mutableStateOf(state.profileNickname) }
        var editSig by remember { mutableStateOf(state.profileSignature) }
        var editAvatar by remember { mutableStateOf(state.profileAvatar) }
        var editAvatarPath by remember { mutableStateOf(state.profileAvatarPath) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val avatarDir = File(context.filesDir, "avatars")
                        avatarDir.mkdirs()
                        val destFile = File(avatarDir, "profile_avatar_${System.currentTimeMillis()}.jpg")
                        withContext(Dispatchers.IO) {
                            inputStream?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        editAvatarPath = destFile.absolutePath
                        editAvatar = ""
                    } catch (e: Exception) { e.printStackTrace() }
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
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
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
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
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
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text(stringResource(R.string.settings_profile_change_avatar))
                        }
                        if (editAvatarPath.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { editAvatarPath = "" }) {
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
private fun StatItemSmall(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
