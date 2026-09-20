package com.palmnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.R

/**
 * 设置区共享组件：菜单行 / 分区标题 / 值行 / 单选对话框。
 * 从 SettingsScreen.kt 迁出——页面文件不该充当组件库。
 */

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    /**
     * 点击整行的动作。**不传 = 整行不可点**（没有涟漪，按下也不会在整行铺 state layer）。
     * 开关型条目只让控件可点：既保住卡片底色，也不让「点哪里都能切换」。既有调用点都传了 onClick，行为不变。
     */
    onClick: (() -> Unit)? = null,
    /**
     * 行尾内容。**不传 = 右箭头**（既有调用点行为不变）；
     * 传 `CapsuleSwitch` 之类即可让「开关型」设置项与其它行保持同一套视觉。
     */
    trailing: @Composable (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(modifier = rowModifier.padding(vertical = 10.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * 互斥单选对话框：`SettingRow` 的配套控件。
 *
 * 设置页里「点一行 → 在若干互斥选项里选一个」是最常见的交互。系统设置、Google 系应用、
 * iOS 都把它做成**行内只显示当前值、点开才列出选项**。把选项平铺在行内（chips）会让一页的
 * 视觉密度失控，也装不下「每 7 天」「本机保留 30 份」这类带单位的选项。
 *
 * 选项一律渲染成「36dp 圆形色底图标 + 标签 + `RadioButton`」，分隔线缩进 52dp。
 * 图标与色是必填参数而非可选：不允许再出现"纯文字版"的选择器。
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    optionIcon: (T) -> ImageVector,
    optionTint: (T) -> Color,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    val tint = optionTint(option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(option); onDismiss() }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(tint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                optionIcon(option),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option); onDismiss() },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (index != options.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 52.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), fontWeight = FontWeight.Bold)
            }
        }
    )
}
