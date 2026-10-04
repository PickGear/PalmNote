package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.palmnote.ui.theme.TypeScale

/**
 * 房内风格的**分段切换**：胶囊轨道 + 选中侧**主色实心圆片**。
 *
 * ## 为什么抽出来（这一处就是"不精致"的来源）
 *
 * 这个形态原本只活在月历头部（`LifeMonthCalendar.ModeToggle`，且是 private），
 * 于是别处遇到"两个选项二选一"时各写各的：月度回顾用了 Material 的 `SegmentedButton`
 * （**带对勾 + 描边**），我新加的金额弹层也跟着用了它 —— **同一 App 里两种分段控件**，
 * 并排看就露怯。现在统一到这一处：
 *
 * | 位置 | 选项 |
 * |---|---|
 * | 月历头部 | 周 / 月 |
 * | 金额弹层 | 增加 / 减少 |
 * | 月度回顾 | 上月 / 去年 |
 *
 * 尺寸对齐全仓其他紧凑分段控件（不套 Material 的 48dp 最小触控尺寸）：
 * 轨道高约 26dp、选中片 22dp；触控面积靠外层 padding 撑到 40dp 左右 ——
 * 仍在可点范围内，但不撑大视觉高度。
 */
@Composable
internal fun PillToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptions: List<String> = emptyList(),
    /** 选中片高度：默认 22dp（月历头部与标题同行的紧凑档）；整页级开关传更大值。 */
    optionHeight: Dp = PILL_OPTION_HEIGHT,
    /** 单个选项的左右内边距。 */
    optionHorizontalPadding: Dp = PILL_OPTION_H_PADDING,
    /** 选项字号。 */
    labelFontSize: TextUnit = TypeScale.labelM
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            PillToggleOption(
                label = label,
                selected = index == selectedIndex,
                contentDescription = contentDescriptions.getOrNull(index) ?: label,
                onClick = { onSelect(index) },
                optionHeight = optionHeight,
                optionHorizontalPadding = optionHorizontalPadding,
                labelFontSize = labelFontSize
            )
        }
    }
}

@Composable
private fun PillToggleOption(
    label: String,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    optionHeight: Dp,
    optionHorizontalPadding: Dp,
    labelFontSize: TextUnit
) {
    Box(
        modifier = Modifier
            .height(optionHeight)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick, onClickLabel = contentDescription)
            .padding(horizontal = optionHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = labelFontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 选中片高度：与标题 / 年月同属一行，贴近设计稿 26px 胶囊的观感。 */
private val PILL_OPTION_HEIGHT = 22.dp

/** 单个选项的左右内边距：控制胶囊总宽，避免同一行里压过相邻文字。 */
private val PILL_OPTION_H_PADDING = 10.dp
