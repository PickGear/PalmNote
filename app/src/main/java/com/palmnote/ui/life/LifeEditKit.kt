@file:Suppress("TooManyFunctions")

package com.palmnote.ui.life

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// getValue / setValue 是 `by mutableStateOf` 委托的隐式依赖 —— 文件里没有 `by remember`，
// 但 ReorderState 的类内属性用了委托，删掉这两个 import 会直接编译失败。
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 模板编辑页的共享 UI 基元（与记录填写页 `LifeCreateFieldKit` 同一套视觉语言）：
 * 20dp 圆角浮卡 + 极淡投影、发丝分隔线内缩对齐图标、图标+标签左 / 值右、虚线添加区。
 *
 * 分工：`LifeTemplateEditScreen` 用它搭页面，`LifeFieldLibraryScreen` 复用同一批基元，
 * 保证「编辑主界面」与「字段库全屏页」看起来是同一个产品。
 */

/** 浮卡圆角（全站 20dp，与记录填写页一致）。 */
internal val EditCardShape = RoundedCornerShape(20.dp)

/** 浮卡投影：极淡，靠层次而非描边表达高度。 */
private fun Modifier.cardElevation() = shadow(
    2.dp, EditCardShape,
    ambientColor = Color.Black.copy(alpha = 0.08f),
    spotColor = Color.Black.copy(alpha = 0.06f)
)

/** 20dp 浮卡：白底 + 极淡投影，无描边（分组内嵌式语言的核心）。 */
@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =
        PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().cardElevation(),
        shape = EditCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/**
 * 分组卡内的发丝分隔线：内缩到与行内图标后的文字对齐（48dp = 14 内边距 + 19 图标 + 10 间距 + 余量）。
 * 不画满宽，是分组表「分隔线跟着内容缩进」的关键细节。
 */
@Composable
internal fun GroupDivider(startInset: Dp = 43.dp) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = startInset)
    )
}

/** 分组标题：section 名 + 可选右侧计数/说明。 */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String = ""
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (trailing.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * 虚线添加区（空态动作位）：复刻记录填写页的 `DashedArea` 视觉，
 * 用于「+ 添加字段」，让「这是动作」而不是「这是内容」一眼可分。
 */
@Composable
internal fun DashedAddArea(
    label: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(EditCardShape)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dash)
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = iconTint,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 「图标 + 标签左 / 值右」的单行 —— 分组式表单的主力行式。
 *
 * @param trailingValue 右侧值文本（空则不显示），[onRowClick] 非空时整行可点。
 * @param minHeight 48dp 起步，保证热区达标（Material 无障碍下限）。
 */
@Suppress("LongParameterList")
@Composable
internal fun GroupRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingValue: String = "",
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueFontWeight: FontWeight = FontWeight.SemiBold,
    onRowClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = modifier
        .fillMaxWidth()
        .height(minHeight)
        .let { if (onRowClick != null) it.clickable(onClick = onRowClick) else it }
        .padding(horizontal = 14.dp, vertical = 10.dp)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingValue.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                trailingValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = valueFontWeight,
                color = trailingColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.let {
            Spacer(Modifier.width(6.dp))
            it()
        }
    }
}

/** 圆形图标衬底：字段类型图标 / 模板身份的图标位。 */
@Composable
internal fun IconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 19.dp,
    shape: Shape = CircleShape,
    bgAlpha: Float = 0.14f
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 小徽标（已自定义 / 已停用 / 预设）。 */
@Composable
internal fun MiniBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** 进度字段行的迷你条（字段级预览，§4.7(3)：单栏富余宽度换来的东西）。 */
@Composable
internal fun FieldMiniProgress(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction, label = "fieldMiniProgress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

// ============================================================ 长按拖拽排序

/**
 * 长按拖拽排序状态（字段编排主交互）。
 *
 * **不引三方库、也不依赖 LazyListState**：字段行渲染在分组浮卡的 Column 里（不是 LazyColumn 项），
 * 因此改为每行用 `onGloballyPositioned` 上报自己在**同一个父容器**里的 y 区间，
 * 拖动时按「拖拽中心线越过相邻行中线」判定换位，逐格 `moveFieldTo`。
 *
 * 之所以不用 `LazyListState.layoutInfo`：一是字段行本就不是列表项，
 * 二是同一张卡里还夹着「+ 添加字段」虚线区，索引与字段下标根本不对齐（key 回找更脆）。
 */
internal class ReorderState(
    private val onMove: (from: Int, to: Int) -> Unit
) {
    /** 正在拖动的行的 key（null = 未在拖拽）。 */
    var draggingKey: String? by mutableStateOf(null)
        private set

    /** 拖动行相对原位的视觉位移。 */
    var draggedDistance: Float by mutableStateOf(0f)
        private set

    /** key 顺序 = 字段在列表里的真实顺序。 */
    private val order = mutableStateListOf<String>()

    /** key → (top, bottom) 相对父容器的像素区间。 */
    private val bounds = mutableMapOf<String, Pair<Float, Float>>()

    val isDragging: Boolean get() = draggingKey != null

    /** 列表数据变化时同步 key 顺序（只在非拖拽态同步，避免打断手势）。 */
    fun syncOrder(keys: List<String>) {
        if (isDragging) return
        if (order != keys) {
            order.clear()
            order.addAll(keys)
        }
    }

    /** 行布局后上报自己的区间。 */
    fun reportBounds(key: String, top: Float, bottom: Float) {
        bounds[key] = top to bottom
    }

    fun onDragStart(key: String) {
        draggingKey = key
        draggedDistance = 0f
    }

    /** 手指每帧位移：累加视觉位移，再判断中心线是否越过相邻行。 */
    fun onDrag(deltaY: Float) {
        val key = draggingKey ?: return
        val (top, bottom) = bounds[key] ?: return
        draggedDistance += deltaY
        val center = (top + bottom) / 2f + draggedDistance

        val fromIndex = order.indexOf(key)
        if (fromIndex < 0) return

        // 向下拖：找中心线以下第一行；向上拖：找中心线以上最后一行
        val targetIndex = if (deltaY >= 0) {
            (fromIndex + 1..order.lastIndex).firstOrNull { i ->
                val b = bounds[order[i]]?.first ?: return@firstOrNull false
                center > b
            }
        } else {
            (fromIndex - 1 downTo 0).firstOrNull { i ->
                val b = bounds[order[i]]?.second ?: return@firstOrNull false
                center < b
            }
        } ?: return

        onMove(fromIndex, targetIndex)
        // 换位后该行的区间会上报为新位置，视觉位移据下一帧的 bounds 自然对齐
    }

    fun onDragEnd() {
        draggingKey = null
        draggedDistance = 0f
    }
}

/** 记住一个排序状态。 */
@Composable
internal fun rememberReorderState(
    onMove: (from: Int, to: Int) -> Unit
): ReorderState = remember { ReorderState(onMove) }

/**
 * 拖拽手柄：长按整块行都能起拖，这里只做「可拖」的视觉提示。
 * 视觉 20dp、整行热区由外层的 `detectDragGesturesAfterLongPress` 承担。
 */
@Composable
internal fun DragHandleIcon(tint: Color, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.DragIndicator,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(20.dp)
    )
}

/** 拖起态的行容器：抬升 + 轻微放大，与静态行拉开层次。 */
internal fun Modifier.draggingAppearance(active: Boolean): Modifier = this.then(
    if (active) {
        Modifier.graphicsLayer {
            scaleX = 1.02f
            scaleY = 1.02f
            shadowElevation = 12.dp.toPx()
        }
    } else {
        Modifier
    }
)

/** 停用态：整行降透明度 + 内容不可点。 */
internal fun Modifier.disabledAppearance(disabled: Boolean): Modifier =
    this.then(if (disabled) Modifier.alpha(0.45f) else Modifier)

/**
 * 字段行：接上长按拖拽手势 + 位置上报。
 *
 * 整行都是起拖热区（不是 20dp 手柄），单击仍可进字段设置 ——
 * `detectDragGesturesAfterLongPress` 不会吃掉短按，两者互不冲突。
 */
@Composable
internal fun FieldDragRow(
    state: ReorderState,
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable (dragging: Boolean) -> Unit
) {
    val dragging = state.isDragging && state.draggingKey == key
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val p = coords.positionInParent()
                state.reportBounds(key, p.y, p.y + coords.size.height.toFloat())
            }
            .pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { state.onDragStart(key) },
                    onDrag = { change, delta ->
                        change.consume()
                        state.onDrag(delta.y)
                    },
                    onDragEnd = { state.onDragEnd() },
                    onDragCancel = { state.onDragEnd() }
                )
            }
            .graphicsLayer { translationY = if (dragging) state.draggedDistance else 0f }
            .draggingAppearance(dragging)
    ) {
        content(dragging)
    }
}

/** 描边胶囊（字段类型 / 进度形态等只读标记）。 */
@Composable
internal fun SoftChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (filled) Modifier.background(color.copy(alpha = 0.13f))
                else Modifier.border(BorderStroke(1.dp, color.copy(alpha = 0.5f)))
            )
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
