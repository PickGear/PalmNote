package com.palmnote.ui.life

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.ProgressForm
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.theme.ListCardShape
import com.palmnote.ui.theme.Spacing
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * 模板编辑器（设计稿 ed_2 编排器主界面，单栏）。
 * 顶部 返回 / 预览 / 保存；头部图标+名称+「已自定义」徽标；字段列表（单击进设置、
 * 上/下调序）；+ 添加字段（进字段库）；模板元信息（名称 / 图标与颜色 / 分类）；
 * 内置模板底部警示。保存前按 ed_9 校验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeTemplateEditScreen(
    onBack: () -> Unit,
    viewModel: LifeTemplateEditViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showLibrary by remember { mutableStateOf(false) }
    var showIcon by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val validation = remember(s) { viewModel.validate() }

    val accent = identityColor(s.color)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompactTopAppBar(
                title = if (s.existingId != null) stringResource(R.string.life_template_edit) else stringResource(R.string.life_template_new),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    TextButton(onClick = { showPreview = true }) {
                        Text(stringResource(R.string.life_template_preview), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { viewModel.save { onBack() } },
                        enabled = validation.canSave && s.name.isNotBlank(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text(stringResource(R.string.save), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(inner).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 头部：图标 + 名称 + 分类 + 已自定义
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(iconFor(s.icon), null, Modifier.size(24.dp), tint = accent) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { stringResource(R.string.life_template_new) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(s.category.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.isBuiltin && s.existingId != null) {
                        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(stringResource(R.string.life_template_customized), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            // 字段
            item { SectionTitle(stringResource(R.string.life_template_section_fields), s.fields.count { !it.disabled }.toString()) }
            items(s.fields.size, key = { it }) { i ->
                val cfg = s.fields[i]
                FieldRow(
                    cfg = cfg,
                    locked = s.isBuiltin && cfg.key.isNotBlank(),
                    canMoveUp = i > 0,
                    canMoveDown = i < s.fields.lastIndex,
                    onEdit = { editIndex = i },
                    onMoveUp = { viewModel.moveField(i, -1) },
                    onMoveDown = { viewModel.moveField(i, 1) }
                )
            }
            item {
                OutlinedCardRow(onClick = { showLibrary = true }) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.life_template_add_field), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 模板元信息
            item { SectionTitle(stringResource(R.string.life_template_step_basic)) }
            item {
                OutlinedCardRow(onClick = { showIcon = true }) {
                    Text(stringResource(R.string.life_template_select_icon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Row {
                        val swatches = IDENTITY_COLORS.take(5)
                        swatches.forEach { c ->
                            Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(identityColor(c)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(4.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(iconFor(s.icon), null, Modifier.size(20.dp), tint = accent)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                EditCard {
                    Labeled(stringResource(R.string.life_template_name))
                    UnderlinedText(s.name, stringResource(R.string.life_template_name_hint)) { viewModel.setName(it) }
                }
            }
            item {
                EditCard {
                    Labeled(stringResource(R.string.life_template_category))
                    UnderlinedText(s.category, stringResource(R.string.life_template_category_custom_hint)) { viewModel.setCategory(it) }
                }
            }
            item {
                EditCard {
                    Labeled(stringResource(R.string.life_template_desc))
                    UnderlinedText(s.description, "", single = true) { viewModel.setDescription(it) }
                }
            }

            // 内置模板警示
            if (s.isBuiltin) {
                item {
                    Box(Modifier.fillMaxWidth().clip(ListCardShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)).border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), ListCardShape).padding(12.dp)) {
                        Text(stringResource(R.string.life_template_builtin_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 删除（仅自定义模板）
            if (s.existingId != null && !s.isBuiltin) {
                item {
                    OutlinedButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.life_template_delete))
                    }
                }
            }

            // 保存前校验横幅（ed_9）
            if (!validation.canSave || validation.advisories.isNotEmpty()) {
                item {
                    ValidationBanner(validation)
                }
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }

    // 字段设置面板（ed_3）
    editIndex?.let { idx ->
        FieldSettingsSheet(
            cfg = s.fields[idx],
            locked = s.isBuiltin,
            hasData = s.itemCount > 0,
            onDismiss = { editIndex = null },
            onUpdate = { viewModel.updateField(idx, it) },
            onRemoveOrDisable = { viewModel.removeOrDisableField(idx); editIndex = null }
        )
    }

    // 字段库面板（ed_5）
    if (showLibrary) {
        FieldLibrarySheet(onDismiss = { showLibrary = false }) { viewModel.addField(it); showLibrary = false }
    }

    // 图标与颜色面板
    if (showIcon) {
        IconColorSheet(
            currentIcon = s.icon, currentColor = s.color,
            onPickIcon = viewModel::setIcon, onPickColor = viewModel::setColor,
            onDismiss = { showIcon = false }
        )
    }

    // 预览三态（ed_6）
    if (showPreview) {
        PreviewSheet(state = s, onDismiss = { showPreview = false })
    }

    // 删除确认（ed_8）
    if (showDelete) {
        AppDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.life_template_delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_template_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTemplate { onBack() }; showDelete = false }) {
                    Text(stringResource(R.string.life_template_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.settings_cancel)) } }
        )
    }
}

// ============================================================ 行 / 卡片基元

@Composable
private fun SectionTitle(text: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (trailing.isNotBlank()) Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OutlinedCardRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(ListCardShape).clickable(onClick = onClick),
        shape = ListCardShape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun EditCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(ListCardShape), shape = ListCardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable
private fun Labeled(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun UnderlinedText(value: String, hint: String, single: Boolean = false, onValue: (String) -> Unit) {
    BasicTextField(
        value = value, onValueChange = onValue,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        singleLine = single,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).border(0.dp, Color.Transparent).then(Modifier)) {
                if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                inner()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    )
}

@Composable
private fun FieldRow(
    cfg: FieldConfig,
    locked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val dim = if (cfg.disabled) 0.5f else 1f
    Surface(
        modifier = Modifier.fillMaxWidth().clip(ListCardShape).clickable(onClick = onEdit),
        shape = ListCardShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // 拖拽手柄（装饰）+ 上/下
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Row {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(22.dp)) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp)) }
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(22.dp)) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).alpha(dim)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cfg.label.ifBlank { cfg.key }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cfg.disabled) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.life_template_field_disabled), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(3.dp))
                // 类型 chip
                Surface(Modifier.clip(RoundedCornerShape(7.5.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(fieldTypeLabel(cfg.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (cfg.showAsProgress) {
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.life_template_field_show_as_progress) + " · " + progressStyleLabel(cfg.progressStyle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (locked) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ValidationBanner(v: LifeTemplateEditViewModel.Validation) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        v.blocks.forEach { b ->
            Box(Modifier.fillMaxWidth().clip(ListCardShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)).border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), ListCardShape).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        v.advisories.forEach { a ->
            Box(Modifier.fillMaxWidth().clip(ListCardShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)).border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), ListCardShape).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============================================================ ed_3 字段设置面板

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldSettingsSheet(
    cfg: FieldConfig,
    locked: Boolean,
    hasData: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (FieldConfig) -> Unit,
    onRemoveOrDisable: () -> Unit
) {
    var name by remember(cfg) { mutableStateOf(cfg.label) }
    var unit by remember(cfg) { mutableStateOf(cfg.unit) }
    var required by remember(cfg) { mutableStateOf(cfg.required) }
    var showInCard by remember(cfg) { mutableStateOf(cfg.showInCard) }
    var showAsProgress by remember(cfg) { mutableStateOf(cfg.showAsProgress) }
    var showStyle by remember { mutableStateOf(false) }

    fun commit(next: FieldConfig = cfg) = onUpdate(next.copy(label = name, unit = unit, required = required, showInCard = showInCard, showAsProgress = showAsProgress))

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(name.ifBlank { cfg.key }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(Modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text("${fieldTypeLabel(cfg.type)} · ${cfg.key}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (locked) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.life_template_field_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))
        Labeled(stringResource(R.string.life_template_field_name))
        UnderlinedText(name, "") { name = it; commit() }

        Spacer(Modifier.height(8.dp))
        Labeled(stringResource(R.string.life_template_field_unit))
        UnderlinedText(unit, "") { unit = it; commit() }

        SettingSwitch(stringResource(R.string.life_template_field_required), required) { required = it; commit() }
        SettingSwitch(stringResource(R.string.life_template_field_show_in_card), showInCard) { showInCard = it; commit() }
        SettingSwitch(stringResource(R.string.life_template_field_show_as_progress), showAsProgress) { showAsProgress = it; commit() }

        if (showAsProgress) {
            Spacer(Modifier.height(4.dp))
            OutlinedCardRow(onClick = { showStyle = true }) {
                Text(stringResource(R.string.life_template_field_progress_style), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(progressStyleLabel(cfg.progressStyle), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.life_template_field_actions))
        Spacer(Modifier.height(4.dp))
        OutlinedCardRow(onClick = onRemoveOrDisable) {
            Icon(Icons.Filled.VisibilityOff, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(if (hasData && locked) stringResource(R.string.life_template_field_disable) else stringResource(R.string.life_template_field_delete), color = MaterialTheme.colorScheme.tertiary)
                Text(if (hasData && locked) stringResource(R.string.life_template_field_disable_hint) else stringResource(R.string.life_template_field_delete_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showStyle) {
        ProgressStyleSheet(current = cfg.progressStyle, onPick = { style ->
            onUpdate(cfg.copy(label = name, unit = unit, required = required, showInCard = showInCard, showAsProgress = showAsProgress, progressStyle = style))
            showStyle = false
        }, onDismiss = { showStyle = false })
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        CapsuleSwitch(checked = checked, onCheckedChange = onChanged, checkedTrackColor = MaterialTheme.colorScheme.primary)
    }
}

// ============================================================ ed_4 进度形态选择器

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressStyleSheet(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        null to stringResource(R.string.life_template_progress_auto),
        ProgressForm.THICK_CAPSULE.name to stringResource(R.string.life_template_progress_linear),
        ProgressForm.THIN_TRACK.name to stringResource(R.string.life_template_progress_linear) + " · 细",
        ProgressForm.SEGMENTED_BAR.name to "分段条",
        ProgressForm.THICK_RING.name to stringResource(R.string.life_template_progress_ring) + " · 厚",
        ProgressForm.SEGMENTED_RING.name to "分段齿环"
    )
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.life_template_field_progress_style), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.life_template_progress_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        options.forEach { (value, label) ->
            val sel = (current ?: "AUTO").equals(value ?: "AUTO", ignoreCase = true)
            val isRing = value?.contains("RING") == true
            Surface(
                modifier = Modifier.fillMaxWidth().clip(ListCardShape).clickable { onPick(value) }
                    .border(if (sel) 1.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, ListCardShape),
                shape = ListCardShape, color = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        if (isRing) Text(stringResource(R.string.life_template_progress_ring_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (sel) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================ ed_5 字段库面板

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldLibrarySheet(onDismiss: () -> Unit, onPick: (FieldType) -> Unit) {
    var query by remember { mutableStateOf("") }
    val groups = remember { FIELD_LIBRARY_GROUPS }
    // fieldTypeLabel 是 @Composable（内部 stringResource），不能在 remember / associateWith 的普通 lambda 里调用；
    // 在组合函数体内用 for 循环逐个取值（组合上下文，合法），remember 里只查表。
    val typeLabels = mutableMapOf<FieldType, String>()
    for (g in groups) for (t in g.types) typeLabels[t] = fieldTypeLabel(t)
    val filtered = remember(query) {
        if (query.isBlank()) groups else groups.map { g -> g.copy(types = g.types.filter { typeLabels[it]!!.contains(query, ignoreCase = true) }) }.filter { it.types.isNotEmpty() }
    }
    AppBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.life_template_search_field)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, null) } }
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.life_template_field_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        filtered.forEach { g ->
            Text(g.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(6.dp))
            FlowRowContentM3(g.types) { t ->
                Surface(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onPick(t) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(fieldTypeLabel(t), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowContentM3(items: List<FieldType>, content: @Composable (FieldType) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = { items.forEach { content(it) } }
    )
}

// ============================================================ 图标与颜色面板

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconColorSheet(
    currentIcon: String, currentColor: String,
    onPickIcon: (String) -> Unit, onPickColor: (String) -> Unit, onDismiss: () -> Unit
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.life_template_select_icon), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        val icons = ICON_KEYS
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(icons) { key ->
                val sel = key == currentIcon
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) identityColor(currentColor).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (sel) 1.5.dp else 1.dp, if (sel) identityColor(currentColor) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { onPickIcon(key) },
                    contentAlignment = Alignment.Center
                ) { Icon(iconFor(key), null, Modifier.size(22.dp), tint = if (sel) identityColor(currentColor) else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.life_template_select_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(IDENTITY_COLORS) { hex ->
                val c = runCatching { Color(hex.removePrefix("#").toLong(16) or 0xFF000000) }.getOrDefault(Color.Gray)
                val sel = c.equalsIgnoringAlpha(currentColor)
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(c)
                        .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onPickColor(hex) }
                )
            }
        }
    }
}

private fun Color.equalsIgnoringAlpha(other: String): Boolean {
    val h = other.removePrefix("#")
    if (h.length != 6) return false
    val r = h.substring(0, 2).toIntOrNull(16) ?: return false
    val g = h.substring(2, 4).toIntOrNull(16) ?: return false
    val b = h.substring(4, 6).toIntOrNull(16) ?: return false
    return (red * 255).toInt() == r && (green * 255).toInt() == g && (blue * 255).toInt() == b
}

// ============================================================ ed_6 预览三态

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(state: LifeTemplateEditViewModel.EditorState, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(stringResource(R.string.life_template_preview_card), stringResource(R.string.life_template_preview_detail), stringResource(R.string.life_template_preview_fill))
    AppBottomSheet(onDismissRequest = onDismiss) {
        val accent = identityColor(state.color)
        // 三段切换
        Surface(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, t ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                        .background(if (i == tab) accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { tab = i }, contentAlignment = Alignment.Center) {
                        Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = if (i == tab) FontWeight.SemiBold else FontWeight.Normal, color = if (i == tab) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> PreviewCard(state, accent)
            1 -> PreviewDetail(state)
            2 -> PreviewFill(state)
        }
    }
}

@Composable
private fun PreviewCard(state: LifeTemplateEditViewModel.EditorState, accent: Color) {
    Surface(Modifier.fillMaxWidth().height(120.dp).clip(ListCardShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ListCardShape)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.name.ifBlank { stringResource(R.string.life_template_new) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val progress = state.fields.firstOrNull { it.showAsProgress && !it.disabled }
            if (progress != null) {
                val frac = (progress.defaultValue.toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(frac).height(12.dp).clip(RoundedCornerShape(6.dp)).background(accent))
                }
            } else {
                Text(state.fields.count { !it.disabled }.toString() + " " + stringResource(R.string.life_template_section_fields), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreviewDetail(state: LifeTemplateEditViewModel.EditorState) {
    Surface(Modifier.fillMaxWidth().clip(ListCardShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ListCardShape).padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.name.ifBlank { stringResource(R.string.life_template_new) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            state.fields.filter { !it.disabled }.forEach { cfg ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(cfg.label.ifBlank { cfg.key }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(if (cfg.required) "*" else "—", color = if (cfg.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PreviewFill(state: LifeTemplateEditViewModel.EditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.fields.filter { !it.disabled }.forEach { cfg ->
            Surface(Modifier.fillMaxWidth().clip(ListCardShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ListCardShape).padding(12.dp)) {
                Column {
                    Text(cfg.label.ifBlank { cfg.key }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

// ============================================================ 标签 / 资源映射

@Composable
private fun fieldTypeLabel(type: FieldType): String = stringResource(
    when (type) {
        FieldType.TEXT -> R.string.life_template_field_text
        FieldType.SHORT_TEXT -> R.string.life_template_field_short_text
        FieldType.RICH_TEXT -> R.string.life_template_field_rich_text
        FieldType.NUMBER -> R.string.life_template_field_number
        FieldType.CURRENCY -> R.string.life_template_field_currency
        FieldType.PERCENT -> R.string.life_template_field_percentage
        FieldType.DATE -> R.string.life_template_field_date
        FieldType.TIME -> R.string.life_template_field_time
        FieldType.DATETIME -> R.string.life_template_field_datetime
        FieldType.BOOLEAN -> R.string.life_template_field_boolean
        FieldType.SELECT -> R.string.life_template_field_select
        FieldType.MULTI_SELECT -> R.string.life_template_field_multi_select
        FieldType.RATING -> R.string.life_template_field_rating
        FieldType.SLIDER -> R.string.life_template_field_slider
        FieldType.EMAIL -> R.string.life_template_field_email
        FieldType.PHONE -> R.string.life_template_field_phone
        FieldType.URL -> R.string.life_template_field_url
        FieldType.COLOR -> R.string.life_template_field_color
        FieldType.DURATION -> R.string.life_template_field_duration
        FieldType.LOCATION -> R.string.life_template_field_location
        FieldType.IMAGE -> R.string.life_template_field_image
        FieldType.VIDEO -> R.string.life_field_type_video
        FieldType.AUDIO -> R.string.life_field_type_audio
        FieldType.TAG -> R.string.life_field_type_tag
        FieldType.MAP -> R.string.life_field_type_map
        FieldType.CHECKLIST -> R.string.life_field_type_checklist
        FieldType.TABLE -> R.string.life_field_type_table
        FieldType.RANGE -> R.string.life_field_type_range
        FieldType.PERSON -> R.string.life_field_type_person
        FieldType.FORMULA -> R.string.life_field_type_formula
        FieldType.REMAINING -> R.string.life_field_type_remaining
        FieldType.STREAK -> R.string.life_field_type_streak
        FieldType.ELAPSED -> R.string.life_field_type_elapsed
        FieldType.PERCENTAGE -> R.string.life_template_field_percentage
        FieldType.FILE -> R.string.life_template_field_file
    }
)

private fun progressStyleLabel(style: String?): String =
    when (style?.uppercase()) {
        null, "", "AUTO" -> "自动"
        "THICK_CAPSULE" -> "厚胶囊"
        "THIN_TRACK" -> "细轨"
        "SEGMENTED_BAR" -> "分段条"
        "THICK_RING" -> "厚环"
        "SEGMENTED_RING" -> "分段齿环"
        else -> style
    }

private val IDENTITY_COLORS = listOf(
    "#EC407A", "#7C8CF0", "#FF7043", "#26A69A", "#AB47BC",
    "#5C6BC0", "#FFCA28", "#00ACC1", "#66BB6A", "#F07070"
)

private val ICON_KEYS = listOf(
    "savings", "shopping_cart", "checklist", "flight", "menu_book", "school",
    "timer_off", "trending_up", "cake", "celebration", "calendar_month", "mood",
    "book", "subscriptions", "BarChart", "fitness_center", "note_add"
)

/** 字段库分组（ed_5）：A–F 共 34 种，与设计稿一致。 */
private val FIELD_LIBRARY_GROUPS = listOf(
    FieldGroup("A 数值与进度", listOf(FieldType.NUMBER, FieldType.CURRENCY, FieldType.PERCENT, FieldType.SLIDER, FieldType.DURATION, FieldType.RATING)),
    FieldGroup("B 点选", listOf(FieldType.BOOLEAN, FieldType.SELECT, FieldType.MULTI_SELECT, FieldType.TAG, FieldType.COLOR, FieldType.DATE, FieldType.TIME, FieldType.DATETIME, FieldType.LOCATION)),
    FieldGroup("C 媒体与空间", listOf(FieldType.IMAGE, FieldType.VIDEO, FieldType.AUDIO, FieldType.FILE, FieldType.MAP)),
    FieldGroup("D 文本", listOf(FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT, FieldType.URL, FieldType.EMAIL, FieldType.PHONE)),
    FieldGroup("E 复合", listOf(FieldType.CHECKLIST, FieldType.TABLE, FieldType.RANGE, FieldType.PERSON)),
    FieldGroup("F 派生", listOf(FieldType.FORMULA, FieldType.REMAINING, FieldType.STREAK, FieldType.ELAPSED))
)

private data class FieldGroup(val name: String, val types: List<FieldType>)
