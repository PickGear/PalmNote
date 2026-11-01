package com.palmnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
    Row(modifier = rowModifier.padding(vertical = 16.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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

/**
 * 分区脚注：解释性文字挂在**卡片外面**，不进卡（分组列表页的通用做法）。
 *
 * 卡片只装可点的行，卡片高度就只由行数决定；一旦卡里塞进小字段落，
 * 它就从「一列可点项」变成内容块，扫读节奏断掉。缩进对齐卡片左缘（4dp 补差），
 * 与行内文字的 16dp 缩进形成两档：行文字更深、脚注更浅也更靠外。
 *
 * @param paragraphs 段落列表，每段一行独立小字，段间 2dp——同组的多条规则不挤成一段。
 */
@Composable
fun SettingsSectionFooter(paragraphs: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        paragraphs.forEach { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RowScope.SettingRowContent(title: String, subtitle: String? = null, value: String? = null, showChevron: Boolean = false) {
    // 文字块按「档位」给最小高度：单行 24dp、双行 40dp。
    // 这样行高不用靠 padding 凑：40 + 上下 16×2 = 72dp，24 + 32 由 SettingRow 的 min 56 兜住。
    // （实测文字块只有 36.7dp，若不强制，双行行会落在 68.7 而不是规范的 72。）
    val minTextHeight = if (subtitle != null) 40.dp else 24.dp
    Column(modifier = Modifier.weight(1f).heightIn(min = minTextHeight), verticalArrangement = Arrangement.Center) {
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

/**
 * 设置卡：**零内边距**，水平 16dp 与垂直留白全部交给内部的行 / 按钮 / 脚注自己（design-spec §6.15）。
 *
 * 为什么不直接用 [ModuleCard]：`ModuleCard` 默认四周各 12dp，`SettingRow` 左右还有 16dp、上下 16dp，
 * 一叠之后卡片边缘到文字的水平缩进变成 28dp、垂直留白随行数变化。卡里只有一行时那两圈空白尤其显眼，
 * 而行数还会变（「应用锁」卡锁关闭时一行、开启后三行），静态「单行卡去边距」根本管不住。
 *
 * 所以把规则钉死在组件里：卡片不参与留白，一切由内容自己给 16dp。
 * 于是**边缘恒为 16dp、行高恒为 56dp（单行）或 72dp（双行），与卡里有几行无关**。
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    ModuleCard(
        modifier = modifier,
        tint = tint,
        // 零内边距：水平 16dp 由内部的行 / 按钮 / 脚注各自提供，卡片不再叠一圈，
        // 否则 12(卡) + 16(行) = 28dp，与「所有元素对齐到 16dp」的约定冲突
        contentPadding = PaddingValues(0.dp),
        content = content
    )
}

/**
 * 设置行：12dp 上下 + 16dp 左右内边距（design-spec §6.15）。
 *
 * 上下取 12dp 而不是 10dp：[SettingsMenuItem] 同为 12dp，两者同值才能让 [SettingsCard]
 * 里的一行与另一张卡里的一行严格等高。差 4dp 肉眼能看出来。
 */
@Composable
fun SettingRow(
    clickable: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val mod = if (clickable != null) {
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = clickable)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        // 最小行高 56dp（design-spec §6.8）：行高完全由内容撑开时，只有标题的行 46dp、
        // 带开关的行 51dp、带副标题的行 62dp，同一张卡里三种高度，看着参差。
        // 给一个下限后，单行统一 56dp，带副标题的自然更高（62dp），层级关系仍然清楚。
        modifier = mod.heightIn(min = 56.dp).padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 互斥单选对话框：`SettingRow` 的配套控件。
 *
 * 设置页里「点一行 → 在若干互斥选项里选一个」是最常见的交互。系统设置与主流应用
 * 都把它做成**行内只显示当前值、点开才列出选项**。把选项平铺在行内（chips）会让一页的
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
