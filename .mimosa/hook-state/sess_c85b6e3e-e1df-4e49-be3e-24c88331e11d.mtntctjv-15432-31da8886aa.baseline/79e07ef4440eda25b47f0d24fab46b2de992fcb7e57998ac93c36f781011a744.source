package com.palmnote.ui.settings
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.palmnote.app.R
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.ui.asset.assetCategoryItems
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

private fun getPresetDisplayName(name: String, context: android.content.Context): String =
    if (com.palmnote.ui.asset.assetCategoryItems.any { it.name == name })
        com.palmnote.ui.components.getCategoryName(name, context)
    else name

private data class PendingPresetData(
    val key: String,
    val name: String,
    val colorHex: String,
    val enabled: Boolean
)

@Composable
private fun CustomCategoryDetailDialog(
    entry: CategoryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(entry.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(entry.icon, null, modifier = Modifier.size(28.dp), tint = entry.color)
                    }
                    Column {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (entry.isEnabled) stringResource(R.string.cat_enabled) else stringResource(R.string.cat_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.cat_custom_detail_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = ErrorLight) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.cat_edit), color = AccentOrange) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cat_close), fontWeight = FontWeight.Bold) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onNavigateBack: () -> Unit = {},
    initialType: String = "ASSET",
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialType) {
        val index = CategoryState().categoryTypes.indexOfFirst { it.key == initialType }
        if (index >= 0 && index != state.selectedTypeIndex) {
            viewModel.selectType(index)
        }
    }

    var editingPreset by remember { mutableStateOf<CategoryEntry?>(null) }
    var editingCustom by remember { mutableStateOf<CategoryConfig?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<CategoryConfig?>(null) }
    var detailPreset by remember { mutableStateOf<CategoryEntry?>(null) }
    var detailCustom by remember { mutableStateOf<CategoryEntry?>(null) }
    var showDeleteWarning by remember { mutableStateOf(false) }
    var deleteWarningData by remember { mutableStateOf<Triple<String, Long?, Pair<Int, Int>>?>(null) }
    var showMatchPrompt by remember { mutableStateOf(false) }
    var matchPromptData by remember { mutableStateOf<Pair<String, Pair<Int, Int>>?>(null) }
    var pendingSaveCategory by remember { mutableStateOf<CategoryConfig?>(null) }
    var pendingPresetData by remember { mutableStateOf<PendingPresetData?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.category_manage_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingCustom = null; showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.category_manage_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTypeIndex,
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 16.dp,
                divider = {}
            ) {
                state.categoryTypes.forEachIndexed { index, type ->
                    Tab(
                        selected = state.selectedTypeIndex == index,
                        onClick = { viewModel.selectType(index) },
                        text = {
                            Text(
                                text = stringResource(type.labelResId),
                                fontWeight = if (state.selectedTypeIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.presetEntries.isNotEmpty()) {
                    item(key = "preset_header") {
                        val presetsExpanded = remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.cat_preset_section),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            if (state.presetEntries.size > 5) {
                                TextButton(onClick = { presetsExpanded.value = !presetsExpanded.value }) {
                                    Icon(
                                        if (presetsExpanded.value) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        null, modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(if (presetsExpanded.value) stringResource(R.string.cat_collapse) else stringResource(R.string.cat_all_count, state.presetEntries.size),
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        val displayPresets = if (presetsExpanded.value) state.presetEntries else state.presetEntries.take(5)
                        Column {
                            displayPresets.forEach { entry ->
                                PresetCategoryItem(
                                    entry = entry,
                                    onClick = { detailPreset = entry },
                                    onToggleEnabled = { viewModel.togglePresetEnabled(entry.key) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                if (state.customEntries.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.cat_custom_section),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(state.customEntries.size, key = { state.customEntries[it].key }) { index ->
                        val entry = state.customEntries[index]
                        CustomCategoryItem(
                            entry = entry,
                            onClick = { detailCustom = entry },
                            onToggleEnabled = {
                                entry.configId?.let { viewModel.toggleCategoryEnabled(it) }
                            }
                        )
                    }
                }

                if (state.presetEntries.isEmpty() && state.customEntries.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Category,
                            title = stringResource(R.string.category_manage_empty_title),
                            subtitle = stringResource(R.string.category_manage_empty_hint)
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    detailPreset?.let { entry ->
        PresetCategoryDetailDialog(
            entry = entry,
            onEdit = { editingPreset = entry; detailPreset = null },
            onDismiss = { detailPreset = null }
        )
    }

    detailCustom?.let { entry ->
        CustomCategoryDetailDialog(
            entry = entry,
            onEdit = {
                entry.configId?.let { id ->
                    val config = CategoryConfig(
                        id = id, type = state.currentType, name = entry.name,
                        icon = entry.configIcon ?: com.palmnote.ui.theme.AppIcon.Restaurant,
                        color = "#%02X%02X%02X".format(
                            (entry.color.red * 255).toInt(),
                            (entry.color.green * 255).toInt(),
                            (entry.color.blue * 255).toInt()
                        ),
                        isEnabled = entry.isEnabled
                    )
                    editingCustom = config; showAddSheet = true; detailCustom = null
                }
            },
            onDelete = {
                val catName = entry.name
                val catId = entry.configId
                scope.launch {
                    val (billCount, assetCount) = viewModel.getCategoryUsageCount(catName)
                    if (billCount > 0 || assetCount > 0) {
                        deleteWarningData = Triple(catName, catId, billCount to assetCount)
                        showDeleteWarning = true
                    } else {
                        categoryToDelete = CategoryConfig(id = catId ?: 0L, name = catName, type = state.currentType)
                        showDeleteDialog = true
                    }
                    detailCustom = null
                }
            },
            onDismiss = { detailCustom = null }
        )
    }

    editingPreset?.let { entry ->
        PresetCategoryEditSheet(
            entry = entry,
            onSave = { name, colorHex, enabled ->
                if (name != entry.name) {
                    scope.launch {
                        val (billCount, assetCount) = viewModel.getCategoryUsageCount(name)
                        if (billCount > 0 || assetCount > 0) {
                            pendingPresetData = PendingPresetData(entry.key, name, colorHex, enabled)
                            matchPromptData = name to (billCount to assetCount)
                            showMatchPrompt = true
                        } else {
                            viewModel.savePresetOverride(entry.key, name, colorHex, enabled)
                            editingPreset = null
                        }
                    }
                } else {
                    viewModel.savePresetOverride(entry.key, name, colorHex, enabled)
                    editingPreset = null
                }
            },
            onReset = {
                viewModel.resetPresetOverride(entry.key)
                editingPreset = null
            },
            onDismiss = { editingPreset = null }
        )
    }

    if (showAddSheet) {
        CategoryEditBottomSheet(
            category = editingCustom,
            type = state.currentType,
            onSave = { category ->
                if (editingCustom != null) {
                    val nameChanged = editingCustom?.name != category.name
                    if (nameChanged) {
                        scope.launch {
                            val (billCount, assetCount) = viewModel.getCategoryUsageCount(category.name)
                            if (billCount > 0 || assetCount > 0) {
                                pendingSaveCategory = category
                                matchPromptData = category.name to (billCount to assetCount)
                                showMatchPrompt = true
                            } else {
                                viewModel.updateCategory(category)
                                showAddSheet = false
                            }
                        }
                    } else {
                        viewModel.updateCategory(category)
                        showAddSheet = false
                    }
                } else {
                    scope.launch {
                        val (billCount, assetCount) = viewModel.getCategoryUsageCount(category.name)
                        if (billCount > 0 || assetCount > 0) {
                            pendingSaveCategory = category
                            matchPromptData = category.name to (billCount to assetCount)
                            showMatchPrompt = true
                        } else {
                            viewModel.addCategory(category)
                            showAddSheet = false
                        }
                    }
                }
            },
            onDismiss = { showAddSheet = false }
        )
    }

    deleteWarningData?.let { (name, catId, counts) ->
        if (showDeleteWarning) {
            val (billCount, assetCount) = counts
            val countText = when (state.currentType) {
                "ASSET" -> stringResource(R.string.category_count_items, assetCount)
                "BILL_EXPENSE", "BILL_INCOME" -> stringResource(R.string.category_count_bills, billCount)
                else -> stringResource(R.string.category_count_both, billCount, assetCount)
            }
            AppDialog(
                onDismissRequest = { showDeleteWarning = false; deleteWarningData = null },
                title = { Text(stringResource(R.string.category_delete_title, name), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.category_delete_related_hint, countText))
                        Text(stringResource(R.string.category_delete_all_desc), color = ErrorLight, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.category_keep_records_desc), color = AccentOrange, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            if (catId != null) viewModel.deleteCategoryWithData(name, catId)
                            showDeleteWarning = false; deleteWarningData = null
                        }) { Text(stringResource(R.string.category_delete_all), color = ErrorLight) }
                        TextButton(onClick = {
                            viewModel.deleteCategory(catId ?: 0L)
                            showDeleteWarning = false; deleteWarningData = null
                        }) { Text(stringResource(R.string.category_keep_records), color = AccentOrange) }
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteWarning = false; deleteWarningData = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }

    matchPromptData?.let { (name, counts) ->
        if (showMatchPrompt) {
            val (billCount, assetCount) = counts
            val countText = when (state.currentType) {
                "ASSET" -> stringResource(R.string.category_count_items, assetCount)
                "BILL_EXPENSE", "BILL_INCOME" -> stringResource(R.string.category_count_bills, billCount)
                else -> stringResource(R.string.category_count_both, billCount, assetCount)
            }
            val isPreset = pendingPresetData != null
            val isEdit = !isPreset && editingCustom != null
            AppDialog(
                onDismissRequest = { showMatchPrompt = false; matchPromptData = null; pendingSaveCategory = null; pendingPresetData = null },
                title = { Text(
                    stringResource(
                        when {
                            isPreset -> R.string.category_match_title_preset
                            isEdit -> R.string.category_match_title_edit
                            else -> R.string.category_match_title_create
                        }, name
                    ), fontWeight = FontWeight.Bold
                ) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.category_match_hint, countText, stringResource(
                            when {
                                isPreset -> R.string.category_adj_preset
                                isEdit -> R.string.category_adj_edit
                                else -> R.string.category_adj_new
                            }
                        )))
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            if (isPreset) {
                                pendingPresetData?.let { viewModel.savePresetOverride(it.key, it.name, it.colorHex, it.enabled); editingPreset = null }
                            } else if (isEdit) {
                                pendingSaveCategory?.let { viewModel.updateCategory(it) }
                            } else {
                                pendingSaveCategory?.let { viewModel.addCategory(it) }
                            }
                            showMatchPrompt = false; matchPromptData = null; pendingSaveCategory = null; pendingPresetData = null
                            showAddSheet = false
                        }) { Text(stringResource(R.string.category_confirm_action, stringResource(
                            when {
                                isPreset -> R.string.category_action_rename
                                isEdit -> R.string.category_action_edit
                                else -> R.string.category_action_create
                            }
                        )), color = AccentOrange) }
                    }
                },
                dismissButton = { TextButton(onClick = { showMatchPrompt = false; matchPromptData = null; pendingSaveCategory = null; pendingPresetData = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }

    val categoryToDeleteSnapshot = categoryToDelete
    if (showDeleteDialog && categoryToDeleteSnapshot != null) {
        AppDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.category_manage_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.category_manage_delete_confirm, categoryToDeleteSnapshot.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(categoryToDeleteSnapshot.id); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = ErrorLight)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun PresetCategoryItem(
    entry: CategoryEntry,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val context = LocalContext.current
    val displayName = getPresetDisplayName(entry.name, context)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.medium).background(
                    if (entry.isEnabled) entry.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(entry.icon, null, modifier = Modifier.size(20.dp),
                    tint = if (entry.isEnabled) entry.color else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                    color = if (entry.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(stringResource(R.string.cat_preset_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            CapsuleSwitch(
                checked = entry.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                checkedTrackColor = LocalSwitchColor.current,
            )
        }
    }
}

@Composable
private fun CustomCategoryItem(
    entry: CategoryEntry,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.medium).background(
                    if (entry.isEnabled) entry.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(entry.icon, null, modifier = Modifier.size(20.dp),
                    tint = if (entry.isEnabled) entry.color else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                    color = if (entry.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            CapsuleSwitch(
                checked = entry.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                checkedTrackColor = LocalSwitchColor.current,
            )
        }
    }
}

@Composable
private fun PresetCategoryDetailDialog(
    entry: CategoryEntry,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val displayName = getPresetDisplayName(entry.name, context)
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(displayName, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(entry.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(entry.icon, null, modifier = Modifier.size(28.dp), tint = entry.color)
                    }
                    Column {
                        Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (entry.isEnabled) stringResource(R.string.cat_enabled) else stringResource(R.string.cat_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.cat_preset_detail_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.cat_edit), color = AccentOrange) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cat_close), fontWeight = FontWeight.Bold) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PresetCategoryEditSheet(
    entry: CategoryEntry,
    onSave: (String, String, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localizedName = getPresetDisplayName(entry.name, context)
    val defaultHex = "#%02X%02X%02X".format(
        (entry.color.red * 255).toInt(),
        (entry.color.green * 255).toInt(),
        (entry.color.blue * 255).toInt()
    )
    var name by remember { mutableStateOf(localizedName) }
    var colorHex by remember { mutableStateOf(defaultHex) }
    var nameError by remember { mutableStateOf<String?>(null) }
    val nameRequiredError = stringResource(R.string.cat_name_required)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(stringResource(R.string.cat_edit_preset_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.cat_edit_preset_desc, localizedName),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it; nameError = null },
            label = { Text(stringResource(R.string.cat_field_name)) },
            modifier = Modifier.fillMaxWidth(), isError = nameError != null, supportingText = nameError?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium, singleLine = true)

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.cat_field_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        ColorPicker(selectedColor = colorHex, onColorSelected = { colorHex = it })

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cat_reset_default))
            }
            Button(onClick = {
                if (name.isBlank()) { nameError = nameRequiredError; return@Button }
                val saveName = if (name.trim() == localizedName) entry.name else name.trim()
                onSave(saveName, colorHex, entry.isEnabled)
            }, modifier = Modifier.weight(1f), enabled = name.isNotBlank()) {
                Text(stringResource(R.string.cat_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditBottomSheet(
    category: CategoryConfig?,
    type: String,
    onSave: (CategoryConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var icon by remember { mutableStateOf(category?.icon ?: com.palmnote.ui.theme.AppIcon.Restaurant) }
    var color by remember { mutableStateOf(category?.color ?: "#4285F4") }
    var nameError by remember { mutableStateOf<String?>(null) }
    val nameRequiredError = stringResource(R.string.cat_name_required)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(if (category != null) stringResource(R.string.cat_edit_category) else stringResource(R.string.cat_add_category), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it; nameError = null }, label = { Text(stringResource(R.string.cat_field_name)) },
            modifier = Modifier.fillMaxWidth(), isError = nameError != null, supportingText = nameError?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium, singleLine = true)

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.cat_field_icon), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        IconPickerGrid(selectedIcon = icon, onSelected = { icon = it })

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.cat_field_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        ColorPicker(selectedColor = color, onColorSelected = { color = it })

        Spacer(Modifier.height(16.dp))
        AppSaveButton(
            onClick = {
                if (name.isBlank()) { nameError = nameRequiredError; return@AppSaveButton }
                onSave(CategoryConfig(
                    id = category?.id ?: 0L, type = type, name = name.trim(), icon = icon,
                    color = color, sortOrder = category?.sortOrder ?: 0, isDefault = category?.isDefault ?: false,
                    isEnabled = category?.isEnabled ?: true
                ))
            },
            enabled = name.isNotBlank()
        )
    }
}
