package com.palmnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.palmnote.ui.theme.BottomSheetShape

/** 顶部把手条上下留白的默认值：对齐 M3 `BottomSheetDefaults.DragHandle()`（4dp 条 + 上下各 22dp）。 */
val DEFAULT_DRAG_HANDLE_PADDING = 22.dp

/** 紧凑把手：上下各 12dp（槽高 28dp），对齐系统选择器那种「把手贴顶」的面板。 */
val COMPACT_DRAG_HANDLE_PADDING = 12.dp

/**
 * 顶部把手：32dp × 4dp 的圆角条，自带宽满 + 居中。
 * 等价于 M3 的 `BottomSheetDefaults.DragHandle()`（同尺寸、同 `onSurfaceVariant` 色），
 * 但留白可调 —— 近全高面板要让把手贴顶时用得上；出图测试也直接调它，保证 mock 与真机同源。
 */
@Composable
fun SheetDragHandle(verticalPadding: Dp = DEFAULT_DRAG_HANDLE_PADDING) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    /**
     * 面板高度占**可用高度**的比例：给了就按这个比例定住、内容**不再滚动**（内部改用 `weight`
     * 分配余量，适合「中间一块吃掉空档 + 底部按钮钉住」的近全高面板）；不给就按内容自适应并纵向滚动。
     * 用比例而不是具体 dp：可用高度已扣掉系统栏与把手，面板因此永远不会顶出屏幕。
     */
    heightFraction: Float? = null,
    /**
     * 顶部把手的上下留白。近全高面板（[heightFraction]）会把把手以下的内容**整体下压一个槽高**：
     * M3 默认的 48dp 槽会把顶栏与标题推到比系统选择器低 20dp 上下的位置，传
     * [COMPACT_DRAG_HANDLE_PADDING] 即可贴齐；别处保持默认值不受影响。
     */
    dragHandlePadding: Dp = DEFAULT_DRAG_HANDLE_PADDING,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SheetDragHandle(dragHandlePadding) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (heightFraction != null) Modifier.fillMaxHeight(heightFraction) else Modifier
                )
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .then(if (heightFraction == null) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
