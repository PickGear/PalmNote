package com.palmnote.ui.life

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.ListCardShape
import com.palmnote.ui.theme.Warning

/**
 * 模板管理页（设计稿 `ed_10` / `ed_11`，总纲 §4.8）。
 *
 * 与**旧实现**的两处关键差别（§4.8(2)、(7)）：
 * 1. 列表读**全量**模板，已关闭项**留在原位** + 「已关闭」徽标 + 整行降透明度 ——
 *    旧实现只读可见列表，关掉即从页面消失，于是**没有任何入口能再打开它**；
 * 2. 开关**直接在行内**（不再藏进二级弹层），且**只有该模板已有记录时才弹确认**
 *    —— 无数据就没有不可逆损失，开关本身就是撤销。
 *
 * 「编辑模板」与「删除」两个动作留在编辑器那一批（`ed_1`–`ed_9`）：入口指向一个
 * 尚不存在的页面比缺一个入口更糟，故本页先只上「开关 + 恢复出厂」。
 */
@Composable
fun LifeTemplateManageScreen(
    onBack: () -> Unit,
    onEditTemplate: (Long) -> Unit,
    viewModel: LifeTemplateManageViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var pendingClose by remember { mutableStateOf<LifeTemplateManageViewModel.TemplateRow?>(null) }
    var pendingRestore by remember { mutableStateOf<LifeTemplateManageViewModel.TemplateRow?>(null) }
    var actionRow by remember { mutableStateOf<LifeTemplateManageViewModel.TemplateRow?>(null) }
    var pendingDelete by remember { mutableStateOf<LifeTemplateManageViewModel.TemplateRow?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.life_template_manage),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item {
                Text(
                    stringResource(R.string.life_template_manage_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
            groups.forEach { group ->
                item(key = "header-${group.category}") {
                    // 组标题右侧计数：「4 个 · 2 个已关闭」（ed_10）
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            group.category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(
                                R.string.life_template_group_count,
                                group.rows.size,
                                group.closedCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(group.rows, key = { it.id }) { row ->
                    TemplateRowCard(
                        row = row,
                        onToggle = { checked ->
                            // 关闭「有记录」的模板才二次确认；开启永远直接生效（开关即撤销）
                            if (!checked && row.itemCount > 0) pendingClose = row
                            else viewModel.setHidden(row.id, !checked)
                        },
                        onLongPress = { actionRow = row }
                    )
                }
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }

    // 关闭确认（§4.8(7)）：必须说清两件事 —— 记录不会被删除 + 哪些出口会一起静默
    pendingClose?.let { row ->
        AppDialog(
            onDismissRequest = { pendingClose = null },
            title = { Text(stringResource(R.string.life_template_close_title, row.name), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.life_template_close_body, row.itemCount))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.life_template_close_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (row.isBuiltin) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.life_template_manage_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setHidden(row.id, true)
                    pendingClose = null
                }) {
                    Text(
                        stringResource(R.string.life_template_disable),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClose = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    // 恢复出厂确认（§4.1 / ed_9）：只重写模板内容，不碰记录、也不改开关状态
    pendingRestore?.let { row ->
        AppDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.life_template_restore_factory), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_template_restore_factory_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreFactory(row.id)
                    pendingRestore = null
                }) {
                    Text(stringResource(R.string.life_template_restore_factory), color = Warning, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    // 长按操作菜单（ed_8）：编辑 / 恢复出厂 / 隐藏 / 删除
    actionRow?.let { row ->
        AppDialog(
            onDismissRequest = { actionRow = null },
            title = { Text(row.name, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onEditTemplate(row.id); actionRow = null
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.life_template_edit), color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (row.isBuiltin) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                pendingRestore = row; actionRow = null
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Restore, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.life_template_restore_factory), color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setHidden(row.id, !row.isHidden); actionRow = null
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (row.isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (row.isHidden) R.string.life_template_restore else R.string.life_template_hide), color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (!row.isBuiltin) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                pendingDelete = row; actionRow = null
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.life_template_delete), color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.life_template_builtin_no_delete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionRow = null }) { Text(stringResource(R.string.settings_cancel)) } }
        )
    }

    // 删除自定义模板确认（ed_8）：关联删除其记录
    pendingDelete?.let { row ->
        AppDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.life_template_delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_template_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(row.id); pendingDelete = null
                }) {
                    Text(stringResource(R.string.life_template_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.settings_cancel)) } }
        )
    }
}

/** 模板行（ed_10）：图标 + 名称 + 徽标 + 副行 + 右端行内开关；已关闭整行降透明度。 */
@Composable
private fun TemplateRowCard(
    row: LifeTemplateManageViewModel.TemplateRow,
    onToggle: (Boolean) -> Unit,
    onLongPress: () -> Unit
) {
    val accent = identityColor(row.colorHex)
    val dim = if (row.isHidden) 0.55f else 1f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(dim)
            .clip(ListCardShape)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        shape = ListCardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconFor(row.icon), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    if (row.customized) RowBadge(stringResource(R.string.life_template_customized), Warning)
                    if (!row.customized && row.isBuiltin) {
                        RowBadge(stringResource(R.string.life_preset), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (row.isHidden) {
                        Spacer(Modifier.width(6.dp))
                        RowBadge(stringResource(R.string.life_template_disabled), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    // 已关闭的行不报「几个字段」，改为说明「关掉意味着什么」+ 记录保留下来的事实
                    if (row.isHidden) {
                        if (row.itemCount > 0) stringResource(R.string.life_template_closed_kept, row.itemCount)
                        else stringResource(R.string.life_template_closed_sub)
                    } else {
                        stringResource(R.string.life_template_field_count, row.fieldCount, row.cardFieldCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            CapsuleSwitch(
                checked = !row.isHidden,
                onCheckedChange = onToggle,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 行内徽标（ed_10：h17 / rx8.5 / 9.5sp，色 @16% 底）。 */
@Composable
private fun RowBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
