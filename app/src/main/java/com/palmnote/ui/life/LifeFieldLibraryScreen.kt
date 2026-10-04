package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldContracts
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.FieldGroup
import com.palmnote.domain.model.ProgressForm
import com.palmnote.domain.model.derivedRefCandidates
import com.palmnote.domain.model.withDerivedRef
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.theme.BottomSheetShape
import com.palmnote.ui.theme.Spacing

/**
 * 字段设置面板（ed_3）：分组式重构。
 *
 * **按契约露出**（§3.5 `FieldContract` 是唯一真源）——面板不写死「哪些字段有什么」，
 * 而是每次打开现查契约：进度能力 / 选项能力 / 默认值能力 / 数值范围，各字段看到自己该看的项。
 * 相比旧版补齐了 `FieldConfig` 里有、旧 UI 没露出的 `defaultValue` / `min` / `max` / `options` /
 * `progressTargetKey`，否则「必填字段要有默认值」这条校验在 UI 上根本无从满足。
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldSettingsSheet(
    cfg: FieldConfig,
    locked: Boolean,
    hasData: Boolean,
    progressTargets: List<FieldConfig>,
    fields: List<FieldConfig>,
    showOptions: Boolean,
    showDefault: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (FieldConfig) -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var name by remember(cfg) { mutableStateOf(cfg.label) }
    var unit by remember(cfg) { mutableStateOf(cfg.unit) }
    var defaultValue by remember(cfg) { mutableStateOf(cfg.defaultValue) }
    var minText by remember(cfg) { mutableStateOf(cfg.min?.let { formatNum(it) } ?: "") }
    var maxText by remember(cfg) { mutableStateOf(cfg.max?.let { formatNum(it) } ?: "") }
    var options by remember(cfg) { mutableStateOf(cfg.options.joinToString(", ")) }
    var targetKey by remember(cfg) { mutableStateOf(cfg.progressTargetKey) }
    var required by remember(cfg) { mutableStateOf(cfg.required) }
    var showInCard by remember(cfg) { mutableStateOf(cfg.showInCard) }
    var showAsProgress by remember(cfg) { mutableStateOf(cfg.showAsProgress) }
    var showStyle by remember { mutableStateOf(false) }
    var showTargets by remember { mutableStateOf(false) }
    /** 正在选参考字段的槽位（0 = 被减/日期，1 = 减数）；null = 没开选择器。 */
    var refSlot by remember { mutableStateOf<Int?>(null) }
    var refOptions by remember(cfg) { mutableStateOf(cfg.options) }

    val contract = FieldContracts.of(cfg.type)
    val isDerived = contract.group == FieldGroup.DERIVED
    val numeric = cfg.type in LifeTemplateEditViewModel.NUMERIC_TARGET_TYPES
    /** key → 字段显示名（参考字段行内文案；空 key 得到 null）。 */
    val refLabel: (String) -> String? = { key -> fields.firstOrNull { it.key == key }?.label?.ifBlank { key } }

    /**
     * 把本地编辑写回。**所有写回都必须走这里**，包括「进度形态 / 分母字段」这类
     * 不在本地 state 里的字段 —— 用 [extra] 叠加，而不是另起一次 `onUpdate(cfg.copy(...))`。
     *
     * 为什么：`cfg` 是**参数**（上一轮渲染时的快照）。此前两个 sheet 的 `onPick` 各自
     * `onUpdate(cfg.copy(仅改自己那一项))`，于是「先改 label 再点进度形态」会拿旧 `cfg`
     * 把 label 静默回退；分母选择器连 `commit()` 都没调，本地编辑整体丢失。
     */
    fun commit(extra: (FieldConfig) -> FieldConfig = { it }) = onUpdate(
        extra(
            cfg.copy(
                label = name,
                unit = unit,
                defaultValue = defaultValue,
                min = minText.toDoubleOrNull(),
                max = maxText.toDoubleOrNull(),
                options = options.split(',').map { it.trim() }.filter { it.isNotBlank() },
                progressTargetKey = targetKey,
                required = required,
                showInCard = showInCard,
                showAsProgress = showAsProgress
            )
        )
    )

    AppSheet(onDismiss = onDismiss) {
        // 头部：类型图标 + 名称 + 类型/key
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = fieldTypeIcon(cfg.type),
                tint = MaterialTheme.colorScheme.primary,
                size = 34.dp,
                iconSize = 19.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { cfg.key },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${fieldTypeLabel(cfg.type)} · ${cfg.key}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (locked) {
            Spacer(Modifier.height(10.dp))
            NoticeLine(
                icon = Icons.Filled.VisibilityOff,
                text = stringResource(R.string.life_template_field_locked)
            )
        }

        // ── 基本设置 ──
        SectionHeader(stringResource(R.string.life_template_field_basic))
        GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            LabeledInput(
                label = stringResource(R.string.life_template_field_name),
                value = name,
                placeholder = cfg.key,
                onValueChange = { name = it; commit() }
            )
            if (numeric) {
                GroupDivider()
                LabeledInput(
                    label = stringResource(R.string.life_template_field_unit),
                    value = unit,
                    placeholder = "¥ / min",
                    onValueChange = { unit = it; commit() }
                )
            }
            if (showDefault) {
                GroupDivider()
                LabeledInput(
                    label = stringResource(R.string.life_template_field_default),
                    value = defaultValue,
                    placeholder = stringResource(R.string.life_template_field_default_hint),
                    onValueChange = { defaultValue = it; commit() }
                )
            }
            if (showOptions) {
                GroupDivider()
                LabeledInput(
                    label = stringResource(R.string.life_template_field_options),
                    value = options,
                    placeholder = stringResource(R.string.life_template_field_options_hint),
                    onValueChange = { options = it; commit() }
                )
            }
        }

        // ── 数值范围（只有数值类字段有）──
        if (numeric) {
            SectionHeader(stringResource(R.string.life_template_field_range))
            GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                LabeledInput(
                    label = stringResource(R.string.life_template_field_min),
                    value = minText,
                    placeholder = "0",
                    keyboardNumeric = true,
                    onValueChange = { minText = it; commit() }
                )
                GroupDivider()
                LabeledInput(
                    label = stringResource(R.string.life_template_field_max),
                    value = maxText,
                    placeholder = "100",
                    keyboardNumeric = true,
                    onValueChange = { maxText = it; commit() }
                )
            }
        }

        // ── 派生字段的参考字段（§3.4 F 组）──
        // 这里此前**完全没有 UI**：派生类型只借 options / defaultValue 存参考信息，
        // 而面板只对「点选类」露出 options 输入框，于是用户只能手打字段 key（`targetAmount` 这种），
        // 打错就静默算不出、字段在卡片与详情页里直接消失。现在改成选字段。
        if (isDerived) {
            SectionHeader(stringResource(R.string.life_template_field_derived_ref))
            GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                when (cfg.type) {
                    FieldType.FORMULA -> LabeledInput(
                        label = stringResource(R.string.life_template_field_formula),
                        value = defaultValue,
                        placeholder = "currentAmount / goalAmount",
                        onValueChange = { defaultValue = it; commit() }
                    )
                    FieldType.STREAK -> Text(
                        stringResource(R.string.life_template_field_derived_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp)
                    )
                    else -> {
                        val minuend = refOptions.getOrNull(0).orEmpty()
                        ValueRow(
                            label = stringResource(
                                if (cfg.type == FieldType.ELAPSED) {
                                    R.string.life_template_field_ref_date
                                } else {
                                    R.string.life_template_field_ref_minuend
                                }
                            ),
                            value = refLabel(minuend) ?: stringResource(R.string.life_template_field_ref_none)
                        ) { refSlot = 0 }
                        // 「减数」槽只在被减字段选好之后才出现：options 是**位置型**的，
                        // 先填槽 1 会留出空洞，把「减数」串位读成「被减」。
                        if (cfg.type == FieldType.REMAINING && minuend.isNotBlank()) {
                            GroupDivider()
                            ValueRow(
                                label = stringResource(R.string.life_template_field_ref_subtrahend),
                                value = refLabel(refOptions.getOrNull(1).orEmpty())
                                    ?: stringResource(R.string.life_template_field_ref_none)
                            ) { refSlot = 1 }
                        }
                    }
                }
            }
        }

        // ── 展示方式 ──
        SectionHeader(stringResource(R.string.life_template_field_display))
        GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            SwitchRow(
                label = stringResource(R.string.life_template_field_show_in_card),
                checked = showInCard
            ) { showInCard = it; commit() }
            if (contract.progressCapable) {
                GroupDivider()
                SwitchRow(
                    label = stringResource(R.string.life_template_field_show_as_progress),
                    checked = showAsProgress
                ) { showAsProgress = it; commit() }
            }
        }

        if (contract.progressCapable && showAsProgress) {
            SectionHeader(stringResource(R.string.life_template_field_progress))
            GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                ValueRow(
                    label = stringResource(R.string.life_template_field_progress_style),
                    value = progressStyleLabel(cfg.progressStyle)
                ) { showStyle = true }
                if (progressTargets.isNotEmpty()) {
                    GroupDivider()
                    ValueRow(
                        label = stringResource(R.string.life_template_field_progress_target),
                        value = progressTargets.firstOrNull { it.key == targetKey }?.label
                            ?: stringResource(R.string.life_template_field_progress_target_none)
                    ) { showTargets = true }
                }
            }
        }

        // ── 字段操作（内置字段：有数据只能停用，无数据才可删；不可用时灰掉而不是隐藏）──
        SectionHeader(stringResource(R.string.life_template_field_actions))
        GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            ActionRow(
                icon = Icons.Filled.VisibilityOff,
                label = stringResource(R.string.life_template_field_disable),
                subtitle = stringResource(R.string.life_template_field_disable_hint),
                tint = MaterialTheme.colorScheme.tertiary,
                enabled = locked && hasData && !cfg.disabled
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDisable()
            }
            GroupDivider()
            ActionRow(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.life_template_field_delete),
                subtitle = if (locked) stringResource(R.string.life_template_field_delete_hint) else "",
                tint = MaterialTheme.colorScheme.error,
                enabled = !locked || !hasData
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }

    if (showStyle) {
        ProgressStyleSheet(
            type = cfg.type,
            current = cfg.progressStyle,
            onPick = { style ->
                // 叠加而不是另起一次 onUpdate：否则会把本次 sheet 里已改的 label/unit/min/max 等回退
                commit { it.copy(progressStyle = style, showAsProgress = true) }
                showStyle = false
            },
            onDismiss = { showStyle = false }
        )
    }

    if (showTargets) {
        TargetPickerSheet(
            targets = progressTargets,
            onPick = { key ->
                targetKey = key
                // 此前这里连 commit() 都没调：选完分母，sheet 里其余编辑全部丢失
                commit { it.copy(progressTargetKey = key) }
                showTargets = false
            },
            onDismiss = { showTargets = false }
        )
    }

    refSlot?.let { slot ->
        ReferencePickerSheet(
            candidates = derivedRefCandidates(cfg.type, fields, cfg.key),
            onPick = { key ->
                refOptions = withDerivedRef(refOptions, slot, key)
                // 同步给 commit() 用的字符串状态（commit 是按 options 字符串重建列表的）
                options = refOptions.joinToString(", ")
                commit()
                refSlot = null
            },
            onDismiss = { refSlot = null }
        )
    }
}

/** 进度形态选择器（ed_4）：能力过滤后只列该类型能用的形态。 */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressStyleSheet(
    type: FieldType,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.life_template_progress_auto)) +
        FieldContracts.selectableProgressForms(type).map { it.name to progressOptionLabel(it) }
    AppSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.life_template_field_progress_style),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.life_template_progress_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        options.forEach { (value, label) ->
            val sel = (current ?: "AUTO").equals(value ?: "AUTO", ignoreCase = true)
            val isRing = value in RING_FORMS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                    .clickable { onPick(value) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isRing) {
                        Text(
                            stringResource(R.string.life_template_progress_ring_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (sel) {
                    Icon(
                        Icons.Filled.Check,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 进度分母选择器（§7.2）：只列数值型、未停用、且不是它自己的字段。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetPickerSheet(
    targets: List<FieldConfig>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.life_template_field_progress_target),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.life_template_field_progress_target_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        // 「用字段自己的 max」是默认值
        ValueRow(
            label = stringResource(R.string.life_template_field_progress_target_none),
            value = ""
        ) { onPick("") }
        targets.forEach { t ->
            ValueRow(label = t.label.ifBlank { t.key }, value = t.unit) { onPick(t.key) }
        }
    }
}

// ============================================================ 面板内小组件

/** 参考字段选择器：派生字段的「源字段」，按类型过滤（日期类 / 数值类）且排除自己。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferencePickerSheet(
    candidates: List<FieldConfig>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.life_template_field_derived_ref),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.life_template_field_ref_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        ValueRow(
            label = stringResource(R.string.life_template_field_ref_none),
            value = ""
        ) { onPick("") }
        candidates.forEach { f ->
            ValueRow(label = f.label.ifBlank { f.key }, value = f.unit) { onPick(f.key) }
        }
        // 候选为空时不能只剩一个「未选择」：得说清为什么没有可选项
        if (candidates.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.life_template_field_ref_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 面板内「标签 + 输入」行：左标签右值，无边框（与基本信息卡同语言）。 */
@Composable
private fun LabeledInput(
    label: String,
    value: String,
    placeholder: String,
    keyboardNumeric: Boolean = false,
    onValueChange: (String) -> Unit
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
            modifier = Modifier.width(84.dp)
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
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (keyboardNumeric) {
                        androidx.compose.ui.text.input.KeyboardType.Decimal
                    } else {
                        androidx.compose.ui.text.input.KeyboardType.Text
                    }
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                ),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { inner() }
                }
            )
        }
    }
}

/** 面板内「标签 + 值 + 右箭头」行（点开二级选择）。 */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (value.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 面板内开关行。 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        CapsuleSwitch(
            checked = checked,
            onCheckedChange = onChanged,
            checkedTrackColor = MaterialTheme.colorScheme.primary
        )
    }
}

/** 面板内破坏性操作行：不可用时灰掉而不是隐藏（让用户知道为什么点不动）。 */
@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .disabledAppearance(!enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 面板内提示行（内置模板边界等）。 */
@Composable
private fun NoticeLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================ 共享底部弹层

/**
 * 面板共用的底部弹层外壳：24dp 圆角、拖拽把手、内容可滚动。
 * 与 `AppBottomSheet` 的差异：内容内边距更宽（20dp），适配分组卡浮卡语言。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

// ============================================================ 字段库（全屏二级页）

/**
 * 字段库全屏页（§4.7(2)）：34 种按 A–F 分组 + 搜索。
 *
 * 为什么是全屏页而不是弹层：34 种字段要分组浏览 + 搜索，半屏弹层放不下，
 * 且**加字段是编排器的高频动作**，值得一整屏。
 * 视觉复用编辑主界面的同一批基元（`LifeEditKit`），保证两页像同一个产品。
 */
@Suppress("LongMethod")
@Composable
fun LifeFieldLibraryScreen(
    onBack: () -> Unit,
    onPick: (FieldType) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }

    // fieldTypeLabel 是 @Composable（内部 stringResource），不能在 remember / associateWith 的普通
    // lambda 里调用；在组合函数体内逐个取值（组合上下文，合法），remember 里只查表。
    val typeLabels = remember { mutableMapOf<FieldType, String>() }
    FIELD_LIBRARY_GROUPS.forEach { g -> g.types.forEach { typeLabels[it] = fieldTypeLabel(it) } }

    val filtered = remember(query, typeLabels.size) {
        if (query.isBlank()) {
            FIELD_LIBRARY_GROUPS.map { it to it.types }
        } else {
            FIELD_LIBRARY_GROUPS.mapNotNull { g ->
                g.types.filter { typeLabels[it].orEmpty().contains(query, ignoreCase = true) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { g to it }
            }
        }
    }
    val totalShown = filtered.sumOf { it.second.size }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.life_template_field_library),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { inner ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(inner)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item {
                OutlinedSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.life_template_search_field)
                )
            }
            item {
                Text(
                    stringResource(R.string.life_template_field_library_result, totalShown),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.life_template_field_library_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            filtered.forEach { (group, types) ->
                item(key = "head-${group.name}") {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 6.dp)
                    )
                }
                item(key = "card-${group.name}") {
                    GroupCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        types.forEachIndexed { i, type ->
                            if (i > 0) GroupDivider()
                            LibraryRow(
                                type = type,
                                onPick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPick(type)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 字段库的一行：类型图标 + 名称 + 类型说明 + 「添加」。 */
@Composable
private fun LibraryRow(type: FieldType, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = fieldTypeIcon(type),
            tint = MaterialTheme.colorScheme.primary,
            size = 32.dp,
            iconSize = 18.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                fieldTypeLabel(type),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val hint = fieldTypeHint(type)
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 搜索框：无边框 + 表面色底，聚焦时描边。 */
@Composable
private fun OutlinedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { inner -> inner() }
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** 字段类型的一句话说明（字段库行的副标题）。 */
@Suppress("CyclomaticComplexMethod")
@Composable
private fun fieldTypeHint(type: FieldType): String = stringResource(
    when (type) {
        FieldType.NUMBER -> R.string.life_field_hint_number
        FieldType.CURRENCY -> R.string.life_field_hint_currency
        FieldType.PERCENT, FieldType.PERCENTAGE -> R.string.life_field_hint_percent
        FieldType.SLIDER -> R.string.life_field_hint_slider
        FieldType.DURATION -> R.string.life_field_hint_duration
        FieldType.RATING -> R.string.life_field_hint_rating
        FieldType.BOOLEAN -> R.string.life_field_hint_boolean
        FieldType.SELECT -> R.string.life_field_hint_select
        FieldType.MULTI_SELECT -> R.string.life_field_hint_multi_select
        FieldType.TAG -> R.string.life_field_hint_tag
        FieldType.COLOR -> R.string.life_field_hint_color
        FieldType.DATE -> R.string.life_field_hint_date
        FieldType.TIME -> R.string.life_field_hint_time
        FieldType.DATETIME -> R.string.life_field_hint_datetime
        FieldType.LOCATION -> R.string.life_field_hint_location
        FieldType.IMAGE -> R.string.life_field_hint_image
        FieldType.VIDEO -> R.string.life_field_hint_video
        FieldType.AUDIO -> R.string.life_field_hint_audio
        FieldType.FILE -> R.string.life_field_hint_file
        FieldType.MAP -> R.string.life_field_hint_map
        FieldType.TEXT -> R.string.life_field_hint_text
        FieldType.SHORT_TEXT -> R.string.life_field_hint_short_text
        FieldType.RICH_TEXT -> R.string.life_field_hint_rich_text
        FieldType.URL -> R.string.life_field_hint_url
        FieldType.EMAIL -> R.string.life_field_hint_email
        FieldType.PHONE -> R.string.life_field_hint_phone
        FieldType.CHECKLIST -> R.string.life_field_hint_checklist
        FieldType.TABLE -> R.string.life_field_hint_table
        FieldType.RANGE -> R.string.life_field_hint_range
        FieldType.PERSON -> R.string.life_field_hint_person
        FieldType.FORMULA -> R.string.life_field_hint_formula
        FieldType.REMAINING -> R.string.life_field_hint_remaining
        FieldType.STREAK -> R.string.life_field_hint_streak
        FieldType.ELAPSED -> R.string.life_field_hint_elapsed
    }
)

// ============================================================ 共享常量

/** 圆环族（落到卡片流会自动换成横向；§4.2 降级表）。 */
internal val RING_FORMS = setOf(
    ProgressForm.THICK_RING.name,
    ProgressForm.THIN_RING.name,
    ProgressForm.SEGMENTED_RING.name,
    ProgressForm.BEADED_RING.name
)

@Composable
internal fun progressOptionLabel(form: ProgressForm): String = when (form) {
    ProgressForm.THICK_CAPSULE -> "③ " + progressFormLabel(form)
    ProgressForm.THICK_RING -> "⑥ " + progressFormLabel(form)
    ProgressForm.THIN_RING -> "⑨ " + progressFormLabel(form)
    ProgressForm.SEGMENTED_RING -> "⑩ " + progressFormLabel(form)
    ProgressForm.BEADED_RING -> "⑪ " + progressFormLabel(form)
    ProgressForm.THIN_TRACK, ProgressForm.SEGMENTED_BAR -> progressFormLabel(form)
}

@Composable
internal fun progressFormLabel(form: ProgressForm): String = when (form) {
    ProgressForm.THICK_CAPSULE -> stringResource(R.string.life_progress_form_capsule)
    ProgressForm.THIN_TRACK -> stringResource(R.string.life_progress_form_thin_track)
    ProgressForm.SEGMENTED_BAR -> stringResource(R.string.life_progress_form_segmented_bar)
    ProgressForm.THICK_RING -> stringResource(R.string.life_progress_form_ring)
    ProgressForm.THIN_RING -> stringResource(R.string.life_progress_form_thin_ring)
    ProgressForm.SEGMENTED_RING -> stringResource(R.string.life_progress_form_segmented_ring)
    ProgressForm.BEADED_RING -> stringResource(R.string.life_progress_form_beaded_ring)
}
