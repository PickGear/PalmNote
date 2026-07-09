package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onNavigateBack: () -> Unit = {},
    initialType: String = "ASSET",
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialType) {
        val index = viewModel.state.value.categoryTypes.indexOfFirst { it.key == initialType }
        if (index >= 0 && index != viewModel.state.value.selectedTypeIndex) {
            viewModel.selectType(index)
        }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<com.palmnote.data.db.entity.CategoryConfig?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<com.palmnote.data.db.entity.CategoryConfig?>(null) }
    var detailCategory by remember { mutableStateOf<com.palmnote.data.db.entity.CategoryConfig?>(null) }

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
                onClick = { editingCategory = null; showAddSheet = true },
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Text(stringResource(R.string.category_manage_count_format, state.categories.size), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.categories.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Category,
                            title = stringResource(R.string.category_manage_empty_title),
                            subtitle = stringResource(R.string.category_manage_empty_hint)
                        )
                    }
                } else {
                    items(state.categories.size, key = { state.categories[it].id }) { index ->
                        val category = state.categories[index]
                        CategoryItem(
                            category = category,
                            onClick = { detailCategory = category },
                            onToggleEnabled = { viewModel.toggleCategoryEnabled(category.id) }
                        )
                    }
                }
            }
        }
    }

    detailCategory?.let { category ->
        CategoryDetailDialog(
            category = category,
            onEdit = { editingCategory = category; showAddSheet = true; detailCategory = null },
            onDelete = {
                if (!category.isDefault) {
                    categoryToDelete = category; showDeleteDialog = true
                }
                detailCategory = null
            },
            onDismiss = { detailCategory = null }
        )
    }

    if (showAddSheet) {
        CategoryEditBottomSheet(
            category = editingCategory,
            type = state.currentType,
            onSave = { category ->
                if (editingCategory != null) viewModel.updateCategory(category) else viewModel.addCategory(category)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
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
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) } }
        )
    }
}

@Composable
private fun CategoryItem(
    category: com.palmnote.data.db.entity.CategoryConfig,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val iconColor = try {
        if (category.color.isNotEmpty()) category.color.toComposeColor() else AccentOrange
    } catch (_: Exception) { AccentOrange }

    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon.imageVector, null, modifier = Modifier.size(20.dp), tint = iconColor)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                    color = if (category.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                if (category.isDefault) {
                    Text(stringResource(R.string.category_manage_system_default), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            XiaomiSwitch(
                checked = category.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                checkedTrackColor = LocalSwitchColor.current
            )
        }
    }
}

@Composable
private fun CategoryDetailDialog(
    category: com.palmnote.data.db.entity.CategoryConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val iconColor = try {
        if (category.color.isNotEmpty()) category.color.toComposeColor() else AccentOrange
    } catch (_: Exception) { AccentOrange }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(category.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(category.icon.imageVector, null, modifier = Modifier.size(28.dp), tint = iconColor)
                    }
                    Column {
                        Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (category.isEnabled) stringResource(R.string.category_manage_enabled) else stringResource(R.string.category_manage_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                DetailRow(stringResource(R.string.bill_type), category.type)
                if (category.isDefault) DetailRow(stringResource(R.string.category_manage_default), stringResource(R.string.category_manage_system_category))
                DetailRow(stringResource(R.string.asset_status), if (category.isEnabled) stringResource(R.string.category_manage_enable) else stringResource(R.string.category_manage_disable))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!category.isDefault) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = ErrorLight) }
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit), color = AccentOrange) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), fontWeight = FontWeight.Bold) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditBottomSheet(
    category: com.palmnote.data.db.entity.CategoryConfig?,
    type: String,
    onSave: (com.palmnote.data.db.entity.CategoryConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var icon by remember { mutableStateOf(category?.icon ?: AppIcon.Restaurant) }
    var color by remember { mutableStateOf(category?.color ?: "#4285F4") }
    var nameError by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(if (category != null) stringResource(R.string.category_manage_edit) else stringResource(R.string.category_manage_add), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(value = name, onValueChange = { name = it; nameError = null }, label = { Text(stringResource(R.string.category_manage_name)) },
            modifier = Modifier.fillMaxWidth(), isError = nameError != null, supportingText = nameError?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium, singleLine = true)

        Text(stringResource(R.string.category_manage_icon), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        IconPickerGrid(selectedIcon = icon, onSelected = { icon = it })

        Text(stringResource(R.string.category_manage_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categoryColorOptions.forEach { c ->
                Box(modifier = Modifier.size(28.dp).clip(MaterialTheme.shapes.small).background(c.toComposeColor())
                    .clickable { color = c }, contentAlignment = Alignment.Center) {
                    if (color == c) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }

        val categoryNameRequired = stringResource(R.string.category_manage_name_required)
        AppSaveButton(
            onClick = {
                if (name.isBlank()) { nameError = categoryNameRequired; return@AppSaveButton }
                onSave(com.palmnote.data.db.entity.CategoryConfig(
                    id = category?.id ?: 0L, type = type, name = name.trim(), icon = icon,
                    color = color, sortOrder = category?.sortOrder ?: 0, isDefault = category?.isDefault ?: false,
                    isEnabled = category?.isEnabled ?: true
                ))
            },
            enabled = name.isNotBlank()
        )
    }
}
