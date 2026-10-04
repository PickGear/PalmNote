package com.palmnote.ui.life

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldContract
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.derivedUnconfigured
import com.palmnote.domain.model.ProgressStyleSetting
import com.palmnote.domain.model.ReminderSpec
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.Warning

/**
 * 模板添加 / 编辑页（单栏编排器，iOS 分组式）。
 *
 * **视觉语言**与记录填写页（`LifeCreateRecordScreen`）完全一致：20dp 圆角浮卡 + 极淡投影、
 * 发丝分隔线内缩对齐、「图标+标签左 / 值右」、虚线添加区、底部 52dp 全宽主按钮。
 *
 * **结构**（自上而下）：
 * 1. **身份头卡** —— 图标 / 名称 / 分类 / 「已自定义」徽标，点开换图标与颜色；
 * 2. **字段编排** —— 一个浮卡装下全部字段：类型图标 + 名称 + 类型 chip + 字段级实时预览，
 *    **长按整行拖拽排序**（§4.7(3) 单栏富余宽度换来的字段级预览），
 *    「⋮」溢出菜单收纳 编辑 / 停用 / 删除；
 * 3. **基本信息卡** —— 名称 / 分类 / 描述（无边框输入，右对齐）；
 * 4. **底部主按钮** —— 校验横幅贴在按钮正上方，不再让用户滚到底才知道为什么置灰。
 *
 * 字段库是**全屏二级页**（§4.7(2)：34 种 + 分组 + 搜索，半屏弹层放不下），
 * 由 [LifeFieldLibraryScreen] 承担。
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun LifeTemplateEditScreen(
    onBack: () -> Unit,
    onOpenFieldLibrary: () -> Unit,
    viewModel: LifeTemplateEditViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showIcon by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val validation = remember(s) { viewModel.validate() }
    val accent = identityColor(s.color)
    val reorder = rememberReorderState { from, to -> viewModel.moveFieldTo(from, to) }
    // 预取这两条文案：另存为新模板的名字要在 lambda 里用，不能在 lambda 内调 stringResource
    val newTemplateLabel = stringResource(R.string.life_template_new)
    val copySuffixLabel = stringResource(R.string.life_template_copy_suffix)

    // 字段库全屏页返回后消费暂存的字段类型
    LaunchedEffect(s.pendingAddType) {
        if (s.pendingAddType != null) viewModel.consumePendingAddField()
    }

    // 返回拦截：动过草稿且未保存 → 弹「放弃更改」
    val requestBack = { if (s.dirty && !s.saved) showDiscard = true else onBack() }
    BackHandler { requestBack() }

    // 加载失败必须挡住编辑器本身：此时 existingId 仍为 null，继续「编辑」再保存会走插入分支，
    // 凭空多出一条空白模板。此前 loadError 被写进 state 却无人读取（P0-4）。
    val loadError = s.loadError
    if (loadError != null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                SecondaryTopAppBar(
                    backgroundColor = Color.Transparent,
                    title = {
                        Text(
                            stringResource(R.string.life_template_edit),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_navigate_back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    loadError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.lg))
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_navigate_back))
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SecondaryTopAppBar(
                backgroundColor = Color.Transparent,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        IconTile(
                            icon = iconFor(s.icon),
                            tint = accent,
                            size = 30.dp,
                            iconSize = 17.dp
                        )
                        Text(
                            if (s.existingId != null) {
                                stringResource(R.string.life_template_edit)
                            } else {
                                stringResource(R.string.life_template_new)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                },
                actions = {
                    // 预览提到顶栏常驻（§4.7：预览是编排器存在的唯一理由）
                    IconButton(onClick = { showPreview = true }) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.life_template_preview),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── 身份头卡：图标 + 名称 + 分类 + 已自定义 ──
            item {
                IdentityCard(
                    name = s.name,
                    category = s.category,
                    iconKey = s.icon,
                    accent = accent,
                    isBuiltin = s.isBuiltin && s.existingId != null,
                    onClick = { showIcon = true }
                )
            }

            // ── 字段编排 ──
            item {
                SectionHeader(
                    title = stringResource(R.string.life_template_section_fields),
                    trailing = stringResource(
                        R.string.life_template_edit_field_count,
                        s.fields.count { !it.disabled },
                        s.fields.size
                    )
                )
            }
            item {
                FieldListCard(
                    fields = s.fields,
                    accent = accent,
                    isBuiltin = s.isBuiltin,
                    reorder = reorder,
                    onFieldClick = { editIndex = it },
                    onDisableField = { viewModel.disableField(it) },
                    onDeleteField = { viewModel.deleteField(it) },
                    onAddClick = onOpenFieldLibrary
                )
            }

            // ── 基本信息 ──
            item { SectionHeader(stringResource(R.string.life_template_step_basic)) }
            item {
                BasicInfoCard(
                    name = s.name,
                    category = s.category,
                    description = s.description,
                    onNameChange = viewModel::setName,
                    onCategoryChange = viewModel::setCategory,
                    onDescriptionChange = viewModel::setDescription
                )
            }

            // ── 每年重复（生日 / 纪念日这类事件每年都过）──
            // 落点在**读数**而不是记录：开启后该模板的「剩余天数」按下一次周年算，
            // 于是生日过去后显示「还有 364 天」而不是「已过 1 天」。口径与提醒 Worker 共用。
            item { SectionHeader(stringResource(R.string.life_template_repeat_section)) }
            item {
                GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    GroupRow(
                        icon = Icons.Filled.Event,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        label = stringResource(R.string.life_template_repeat_yearly),
                        trailing = {
                            Switch(checked = s.repeatYearly, onCheckedChange = viewModel::setRepeatYearly)
                        }
                    )
                    GroupDivider()
                    Text(
                        stringResource(R.string.life_template_repeat_yearly_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 8.dp)
                    )
                }
            }

            // ── 提醒配置（§提醒显式化）：开关 + 类型 + 日期字段，显式落在模板行上 ──
            item { SectionHeader(stringResource(R.string.life_template_reminder_section)) }
            item {
                ReminderCard(
                    reminder = s.reminder,
                    dateFields = s.fields.filter { !it.disabled && it.type in LifeTemplateEditViewModel.DATE_TYPES },
                    onToggle = viewModel::setReminderEnabled,
                    onPatch = viewModel::updateReminder
                )
            }

            // ── 内置模板边界说明 + 另存为新模板（§4.1）──
            if (s.isBuiltin) {
                item {
                    // stringResource 只能在组合上下文取：先在组合期求值，再传进 lambda
                    val newTemplateName = s.name.ifBlank { newTemplateLabel } +
                        " · " + copySuffixLabel
                    BuiltinNoticeCard(
                        onSaveAsNew = { viewModel.saveAsNew(newTemplateName) { onBack() } }
                    )
                }
            }

            // ── 删除（仅自定义模板）──
            if (s.existingId != null && !s.isBuiltin) {
                item {
                    TextButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.life_template_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ── 校验贴在保存按钮正上方：阻断原因不必滚到底才知道 ──
            if (validation.blocks.isNotEmpty() || validation.advisories.isNotEmpty()) {
                item {
                    ValidationPanel(validation, enabled = validation.canSave && s.name.isNotBlank())
                }
            }

            item {
                Button(
                    onClick = { viewModel.save { onBack() } },
                    enabled = validation.canSave && s.name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(
                        stringResource(R.string.save),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }

    // ── 字段设置面板 ──
    editIndex?.let { idx ->
        val cfg = s.fields.getOrNull(idx)
        if (cfg != null) {
            FieldSettingsSheet(
                cfg = cfg,
                locked = s.isBuiltin,
                hasData = cfg.key in s.fieldKeysWithData,
                progressTargets = viewModel.progressTargetCandidates(cfg.key),
                fields = s.fields,
                showOptions = viewModel.hasOptions(cfg.type),
                showDefault = viewModel.supportsDefaultValue(cfg.type),
                onDismiss = { editIndex = null },
                onUpdate = { next -> viewModel.patchField(idx) { next } },
                onDisable = {
                    viewModel.disableField(idx)
                    editIndex = null
                },
                onDelete = {
                    viewModel.deleteField(idx)
                    editIndex = null
                }
            )
        }
    }

    // ── 图标与颜色 ──
    if (showIcon) {
        IconColorSheet(
            currentIcon = s.icon,
            currentColor = s.color,
            onPickIcon = viewModel::setIcon,
            onPickColor = viewModel::setColor,
            onDismiss = { showIcon = false }
        )
    }

    // ── 预览三态 ──
    if (showPreview) {
        RecordPreviewSheet(
            input = RecordPreviewInput(
                name = s.name,
                iconKey = s.icon,
                colorHex = s.color,
                fields = s.fields,
                // 编辑器没有"用户输入"：用字段默认值预览（与「详情」态同源）
                values = s.fields.associate { it.key to it.defaultValue },
                category = s.category,
                templateId = s.existingId ?: 0L,
                description = s.description,
                isBuiltin = s.isBuiltin,
                repeatYearly = s.repeatYearly
            ),
            onDismiss = { showPreview = false }
        )
    }

    // ── 删除确认 ──
    if (showDelete) {
        AppDialog(
            onDismissRequest = { showDelete = false },
            title = {
                Text(
                    stringResource(R.string.life_template_delete_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.life_template_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate { onBack() }
                    showDelete = false
                }) {
                    Text(
                        stringResource(R.string.life_template_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    // ── 放弃更改确认 ──
    if (showDiscard) {
        AppDialog(
            onDismissRequest = { showDiscard = false },
            title = {
                Text(
                    stringResource(R.string.life_discard_changes_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(R.string.life_discard_changes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onBack()
                }) {
                    Text(
                        stringResource(R.string.life_discard),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

// ============================================================ 身份头卡

/** 身份头卡：模板的「名片」——图标 + 名称 + 分类 + 徽标，整卡点开换图标与颜色。 */
@Composable
private fun IdentityCard(
    name: String,
    category: String,
    iconKey: String,
    accent: Color,
    isBuiltin: Boolean,
    onClick: () -> Unit
) {
    GroupCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            IconTile(icon = iconFor(iconKey), tint = accent, size = 48.dp, iconSize = 26.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { stringResource(R.string.life_template_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.ifBlank { stringResource(R.string.life_template_category_custom) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isBuiltin) {
                        Spacer(Modifier.width(6.dp))
                        MiniBadge(
                            stringResource(R.string.life_template_customized),
                            MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            Icon(
                Icons.Filled.Palette,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================================ 字段编排卡

/**
 * 字段编排卡：一张浮卡装下全部字段 + 「+ 添加字段」虚线区。
 *
 * 每行 = 类型图标 + 名称（+ 停用标记） + 类型 chip + 字段级实时预览 + 拖拽手柄 + 溢出菜单。
 * 腾出上/下箭头那 ~50dp 后，右侧留给「这个字段在卡片里长什么样」（§4.7(3)）。
 */
@Composable
private fun FieldListCard(
    fields: List<FieldConfig>,
    accent: Color,
    isBuiltin: Boolean,
    reorder: ReorderState,
    onFieldClick: (Int) -> Unit,
    onDisableField: (Int) -> Unit,
    onDeleteField: (Int) -> Unit,
    onAddClick: () -> Unit
) {
    // 数据变化时同步拖拽用的 key 顺序（拖拽中不同步，避免打断手势）
    LaunchedEffect(fields) { reorder.syncOrder(fields.map { it.key }) }

    GroupCard(contentPadding = PaddingValues(vertical = 0.dp)) {
        if (fields.isEmpty()) {
            // 空态：一张卡里直接说清「还没有字段」+ 下一步
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.DragIndicator,
                    null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.life_template_no_fields),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.life_template_no_fields_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        } else {
            fields.forEachIndexed { index, cfg ->
                if (index > 0) GroupDivider(startInset = 62.dp)
                FieldDragRow(reorder, cfg.key) { dragging ->
                    FieldRow(
                        cfg = cfg,
                        fields = fields,
                        accent = accent,
                        locked = isBuiltin && cfg.key.isNotBlank(),
                        dragging = dragging,
                        onClick = { onFieldClick(index) },
                        onDisableField = { onDisableField(index) },
                        onDeleteField = { onDeleteField(index) }
                    )
                }
            }
        }
        // 「+ 添加字段」独立成虚线区，视觉上与内容区分开
        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            DashedAddArea(
                label = stringResource(R.string.life_template_add_field),
                iconTint = accent,
                onClick = onAddClick
            )
        }
    }
}

/**
 * 字段行：类型图标 + 名称 + chip + 实时预览 + 拖拽手柄 + 溢出菜单。
 *
 * 整行可点进设置（单击）、可长按起拖（拖拽）；右端「⋮」把破坏性操作收进菜单，
 * 替掉旧版常驻的上/下箭头（那对箭头热区只有 22dp，低于 Material 48dp 下限）。
 */
@Suppress("LongMethod")
@Composable
private fun FieldRow(
    cfg: FieldConfig,
    fields: List<FieldConfig>,
    accent: Color,
    locked: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onDisableField: () -> Unit,
    onDeleteField: () -> Unit
) {
    // 以字段 key 为身份，而不是靠"位置"：字段列表可拖拽排序、可增删，
    // 位置型状态一旦遇到重排就会串到别人身上（打开着的「⋮」菜单挂到另一个字段）。
    var menuOpen by remember(cfg.key) { mutableStateOf(false) }
    val fraction = previewFraction(cfg, fields)
    val preview = fieldPreviewText(cfg)
    // 派生字段「配不全就一定算不出」：与其等它静默消失，不如在编排器里当场标出来。
    val unconfigured = derivedUnconfigured(cfg, fields)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .draggingAppearance(dragging)
            .disabledAppearance(cfg.disabled)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = fieldTypeIcon(cfg.type),
            tint = if (cfg.disabled) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            size = 30.dp,
            iconSize = 17.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cfg.label.ifBlank { cfg.key },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cfg.disabled) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.life_template_field_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (unconfigured) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.life_template_field_derived_missing),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoftChip(fieldTypeLabel(cfg.type), MaterialTheme.colorScheme.onSurfaceVariant)
                if (cfg.showAsProgress) {
                    Spacer(Modifier.width(5.dp))
                    SoftChip(progressStyleLabel(cfg.progressStyle), accent)
                }
                if (locked) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Filled.Lock,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            // 字段级实时预览（§4.7(3)）：进度字段画迷你条，数值字段给换算后的值
            if (fraction != null) {
                Spacer(Modifier.height(6.dp))
                FieldMiniProgress(fraction = fraction, color = accent)
            } else if (preview != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    preview,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // 拖拽提示：整行长按即可起拖，这里只做视觉说明
        DragHandleIcon(tint = MaterialTheme.colorScheme.outline)
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.life_template_field_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(14.dp)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.life_template_field_edit),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = { menuOpen = false; onClick() }
                )
                if (!cfg.disabled) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.life_template_field_disable),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = { menuOpen = false; onDisableField() }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.life_template_field_delete),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = { menuOpen = false; onDeleteField() }
                )
            }
        }
    }
}

/** 进度字段卡的迷你条分母：progressTargetKey 指向字段的默认值，缺省用 max（与 previewProgress 同口径）。 */
private fun previewFraction(cfg: FieldConfig, fields: List<FieldConfig>): Float? {
    if (!cfg.showAsProgress) return null
    val current = cfg.defaultValue.toDoubleOrNull() ?: return null
    val target = fields.firstOrNull { it.key == cfg.progressTargetKey }?.defaultValue?.toDoubleOrNull()
        ?: cfg.max
        ?: return null
    if (target <= 0.0) return null
    return (current / target).toFloat().coerceIn(0f, 1f)
}

/** ed_2 字段卡右侧的实时值预览（由默认值渲染；进度字段改画迷你条，不出文字）。 */
private fun fieldPreviewText(cfg: FieldConfig): String? {
    val v = cfg.defaultValue
    if (v.isBlank() || cfg.showAsProgress) return null
    return when (cfg.type) {
        FieldType.CURRENCY -> v.toDoubleOrNull()?.let { fmtMoney(it) }
        FieldType.NUMBER, FieldType.DURATION, FieldType.SLIDER,
        FieldType.PERCENT, FieldType.PERCENTAGE, FieldType.RATING ->
            v.toDoubleOrNull()?.let {
                fmtNumber(it) + cfg.unit.takeIf { u -> u.isNotBlank() }?.let { u -> " $u" }.orEmpty()
            }
        else -> v
    }
}

// ============================================================ 基本信息卡

/** 基本信息卡：名称 / 分类（三选一）/ 描述。 */
@Composable
private fun BasicInfoCard(
    name: String,
    category: String,
    description: String,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        InfoInputRow(
            label = stringResource(R.string.life_template_name),
            value = name,
            placeholder = stringResource(R.string.life_template_name_hint),
            onValueChange = onNameChange
        )
        GroupDivider()
        // 分类是**封闭三选一**，不是输入框。
        //
        // 它决定这条模板出现在生活页哪张分类卡、用哪套色、是否参与逾期 —— 生活页只认这三张卡，
        // 所以自由输入造出来的分类在首页没有任何入口（真机反馈「新建自定义模板不生效」）。
        // 历史数据里已经存在的自造分类在下面点名提示，保存时会被归一（见 ViewModel.save）。
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.life_template_category),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BuiltinTemplates.ALL_CATEGORIES.forEach { value ->
                    FilterChip(
                        selected = category == value,
                        onClick = { onCategoryChange(value) },
                        label = { Text(categoryChipLabel(value)) }
                    )
                }
            }
            if (category.isNotBlank() && category !in BuiltinTemplates.ALL_CATEGORIES) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.life_template_category_unsupported, category),
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning
                )
            }
        }
        GroupDivider()
        InfoInputRow(
            label = stringResource(R.string.life_template_desc),
            value = description,
            placeholder = stringResource(R.string.life_template_desc_hint),
            onValueChange = onDescriptionChange,
            singleLine = true
        )
    }
}

/** 一行「标签 + 无边框输入」：输入框右对齐，不画框，靠分隔线分组。 */
@Composable
private fun InfoInputRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                ),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { inner() }
                }
            )
        }
    }
}

// ============================================================ 内置模板边界

/** 内置模板边界说明：字段 key/type 只读，想改结构走「另存为新模板」（§4.1）。 */
@Composable
private fun BuiltinNoticeCard(onSaveAsNew: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = EditCardShape,
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.life_template_builtin_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSaveAsNew,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.life_template_save_as_new),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ============================================================ 校验面板

/**
 * 校验面板：贴在保存按钮上方。
 * 阻断项把保存按钮一起「按住」并说明原因；提示项只说不拦。
 */
@Composable
private fun ValidationPanel(
    v: LifeTemplateEditViewModel.Validation,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        v.blocks.forEach { b ->
            val (text, suggest) = when (b) {
                is LifeTemplateEditViewModel.Validation.Block.TextBudget ->
                    stringResource(
                        R.string.life_template_block_text_budget,
                        LifeTemplateEditViewModel.TEXT_BUDGET,
                        b.count
                    ) to stringResource(R.string.life_template_block_text_budget_suggest)

                is LifeTemplateEditViewModel.Validation.Block.RequiredNoDefault ->
                    stringResource(R.string.life_template_block_required, b.label) to
                        stringResource(R.string.life_template_block_required_suggest)

                is LifeTemplateEditViewModel.Validation.Block.DerivedUnconfigured ->
                    stringResource(R.string.life_template_block_derived, b.label) to
                        stringResource(R.string.life_template_block_derived_suggest)
            }
            ValidationRow(
                icon = Icons.Filled.Block,
                title = text,
                suggest = suggest,
                tone = MaterialTheme.colorScheme.error
            )
        }
        if (!enabled && v.blocks.isEmpty()) {
            ValidationRow(
                icon = Icons.Filled.ErrorOutline,
                title = stringResource(R.string.life_template_block_name_required),
                suggest = "",
                tone = MaterialTheme.colorScheme.error
            )
        }
        v.advisories.forEach { a ->
            val text = when (a) {
                LifeTemplateEditViewModel.Validation.Advisory.RingToCard ->
                    stringResource(R.string.life_template_progress_ring_hint)

                LifeTemplateEditViewModel.Validation.Advisory.LowContrast ->
                    stringResource(R.string.life_template_advisory_contrast)

                LifeTemplateEditViewModel.Validation.Advisory.ReminderNoDateKey ->
                    stringResource(R.string.life_template_reminder_no_date)
            }
            ValidationRow(
                icon = Icons.Filled.CheckCircle,
                title = text,
                suggest = "",
                tone = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun ValidationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    suggest: String,
    tone: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EditCardShape)
            .background(tone.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = tone)
            if (suggest.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    suggest,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================ 图标与颜色

@Suppress("LongMethod")
@Composable
private fun IconColorSheet(
    currentIcon: String,
    currentColor: String,
    onPickIcon: (String) -> Unit,
    onPickColor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    AppSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.life_template_select_icon),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ICON_KEYS.size) { i ->
                val key = ICON_KEYS[i]
                val sel = key == currentIcon
                val tint = identityColor(currentColor)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (sel) tint.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPickIcon(key)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        iconFor(key),
                        null,
                        Modifier.size(22.dp),
                        tint = if (sel) tint else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.life_template_select_color),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(IDENTITY_COLORS.size) { i ->
                val hex = IDENTITY_COLORS[i]
                val c = runCatching {
                    Color(hex.removePrefix("#").toLong(16) or 0xFF000000)
                }.getOrDefault(Color.Gray)
                val sel = c.equalsIgnoringAlpha(currentColor)
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPickColor(hex)
                        }
                ) {
                    if (sel) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(18.dp)
                        )
                    }
                }
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

// ============================================================ 预览（共用实现）
// 三态预览已抽成 `LifeRecordPreviewKit.kt`：模板编辑器用「字段默认值」预览，
// 记录填写页用「用户刚填的值」预览，两边共用同一份渲染（卡片态调 `LifeRecordCard`、
// 详情态调 `DetailAssembler`），所以预览与真实页面不会漂移。
// 「填写」态的控件示意在 `LifePreviewFieldKit.kt`。

// ============================================================ 标签 / 资源映射

/** 字段类型 → 标签资源（编辑器 UI 与 VM 默认 label 共用同一映射，避免双份漂移）。 */
@Suppress("CyclomaticComplexMethod")
internal fun fieldTypeLabelRes(type: FieldType): Int = when (type) {
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

@Composable
internal fun fieldTypeLabel(type: FieldType): String = stringResource(fieldTypeLabelRes(type))

@Composable
internal fun progressStyleLabel(style: String?): String =
    when (val parsed = FieldContract.parseProgressStyle(style)) {
        is ProgressStyleSetting.FIXED -> progressFormLabel(parsed.form)
        ProgressStyleSetting.AUTO -> stringResource(R.string.life_template_progress_auto)
    }

internal val IDENTITY_COLORS = listOf(
    "#EC407A", "#7C8CF0", "#FF7043", "#26A69A", "#AB47BC",
    "#5C6BC0", "#FFCA28", "#00ACC1", "#66BB6A", "#F07070"
)

internal val ICON_KEYS = listOf(
    "savings", "shopping_cart", "checklist", "flight", "menu_book", "school",
    "timer_off", "trending_up", "cake", "celebration", "calendar_month", "mood",
    "book", "subscriptions", "BarChart", "fitness_center", "note_add"
)

/** 字段库分组（ed_5）：A–F 六组，与设计稿 §3.1 一致。 */
internal val FIELD_LIBRARY_GROUPS = listOf(
    FieldGroup("A 数值与进度", listOf(FieldType.NUMBER, FieldType.CURRENCY, FieldType.PERCENT, FieldType.SLIDER, FieldType.DURATION, FieldType.RATING)),
    FieldGroup("B 点选", listOf(FieldType.BOOLEAN, FieldType.SELECT, FieldType.MULTI_SELECT, FieldType.TAG, FieldType.COLOR, FieldType.DATE, FieldType.TIME, FieldType.DATETIME, FieldType.LOCATION)),
    FieldGroup("C 媒体与空间", listOf(FieldType.IMAGE, FieldType.VIDEO, FieldType.AUDIO, FieldType.FILE, FieldType.MAP)),
    FieldGroup("D 文本", listOf(FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT, FieldType.URL, FieldType.EMAIL, FieldType.PHONE)),
    FieldGroup("E 复合", listOf(FieldType.CHECKLIST, FieldType.TABLE, FieldType.RANGE, FieldType.PERSON)),
    FieldGroup("F 派生", listOf(FieldType.FORMULA, FieldType.REMAINING, FieldType.STREAK, FieldType.ELAPSED))
)

internal data class FieldGroup(val name: String, val types: List<FieldType>)

// ============================================================ 提醒配置（§提醒显式化）

/**
 * 提醒卡：开关 + 类型 + 日期字段。配置显式写在模板行上（`LifeTemplate.reminderConfig`），
 * Worker 按配置分发——改图标、改字段 key 都不会像旧图标匹配那样静默弄丢提醒。
 */
@Composable
private fun ReminderCard(
    reminder: ReminderSpec?,
    dateFields: List<FieldConfig>,
    onToggle: (Boolean) -> Unit,
    onPatch: (ReminderSpec) -> Unit
) {
    var showKindSheet by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        GroupRow(
            icon = Icons.Filled.Notifications,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            label = stringResource(R.string.life_template_reminder_toggle),
            trailing = {
                Switch(checked = reminder != null, onCheckedChange = onToggle)
            }
        )
        if (reminder != null) {
            GroupDivider()
            GroupRow(
                icon = Icons.Filled.Event,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                label = stringResource(R.string.life_template_reminder_kind),
                trailingValue = reminderKindLabel(reminder.kind),
                onRowClick = { showKindSheet = true }
            )
            if (reminder.kind != ReminderSpec.Kind.SUBSCRIPTION) {
                GroupDivider()
                GroupRow(
                    icon = Icons.Filled.Today,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = stringResource(R.string.life_template_reminder_date),
                    trailingValue = dateFields.firstOrNull { it.key == reminder.dateKey }
                        ?.let { f -> f.label.ifBlank { f.key } }
                        ?: stringResource(R.string.life_template_reminder_date_unset),
                    onRowClick = { showDateSheet = true }
                )
            }
        }
    }
    if (showKindSheet) {
        ReminderKindSheet(
            current = reminder?.kind ?: ReminderSpec.Kind.COUNTDOWN,
            onPick = { kind ->
                reminder?.let { onPatch(it.copy(kind = kind)) }
                showKindSheet = false
            },
            onDismiss = { showKindSheet = false }
        )
    }
    if (showDateSheet) {
        ReminderDateSheet(
            fields = dateFields,
            currentKey = reminder?.dateKey,
            onPick = { key ->
                reminder?.let { onPatch(it.copy(dateKey = key)) }
                showDateSheet = false
            },
            onDismiss = { showDateSheet = false }
        )
    }
}

@Composable
private fun ReminderKindSheet(
    current: ReminderSpec.Kind,
    onPick: (ReminderSpec.Kind) -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.life_template_reminder_kind_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        ReminderSpec.Kind.entries.forEach { kind ->
            ReminderOptionRow(
                title = reminderKindLabel(kind),
                subtitle = reminderKindHint(kind),
                selected = kind == current
            ) { onPick(kind) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ReminderDateSheet(
    fields: List<FieldConfig>,
    currentKey: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.life_template_reminder_date_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        if (fields.isEmpty()) {
            Text(
                stringResource(R.string.life_template_reminder_date_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        fields.forEach { f ->
            ReminderOptionRow(
                title = f.label.ifBlank { f.key },
                subtitle = f.key,
                selected = f.key == currentKey
            ) { onPick(f.key) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ReminderOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun reminderKindLabel(kind: ReminderSpec.Kind): String = stringResource(
    when (kind) {
        ReminderSpec.Kind.MILESTONE -> R.string.life_reminder_kind_milestone
        ReminderSpec.Kind.COUNTDOWN -> R.string.life_reminder_kind_countdown
        ReminderSpec.Kind.BIRTHDAY -> R.string.life_reminder_kind_birthday
        ReminderSpec.Kind.ANNIVERSARY -> R.string.life_reminder_kind_anniversary
        ReminderSpec.Kind.SUBSCRIPTION -> R.string.life_reminder_kind_subscription
    }
)

@Composable
private fun reminderKindHint(kind: ReminderSpec.Kind): String = stringResource(
    when (kind) {
        ReminderSpec.Kind.MILESTONE -> R.string.life_reminder_kind_milestone_hint
        ReminderSpec.Kind.COUNTDOWN -> R.string.life_reminder_kind_countdown_hint
        ReminderSpec.Kind.BIRTHDAY -> R.string.life_reminder_kind_birthday_hint
        ReminderSpec.Kind.ANNIVERSARY -> R.string.life_reminder_kind_anniversary_hint
        ReminderSpec.Kind.SUBSCRIPTION -> R.string.life_reminder_kind_subscription_hint
    }
)
