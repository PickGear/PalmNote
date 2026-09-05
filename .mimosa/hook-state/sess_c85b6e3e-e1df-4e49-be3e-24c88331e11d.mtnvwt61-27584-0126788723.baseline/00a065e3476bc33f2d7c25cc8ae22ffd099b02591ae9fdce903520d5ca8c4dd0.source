package com.palmnote.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * SwipeActionBox — 滑动操作组件
 *
 * 支持左右滑动触发操作，带有弹性回弹效果。
 */
@Composable
fun SwipeActionBox(
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    leftAction: @Composable () -> Unit = {},
    rightAction: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "swipe_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onSwipeLeft, onSwipeRight) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = 100f
                        when {
                            offsetX < -threshold && onSwipeLeft != null -> onSwipeLeft()
                            offsetX > threshold && onSwipeRight != null -> onSwipeRight()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, amount ->
                        offsetX = (offsetX + amount).coerceIn(-200f, 200f)
                    }
                )
            }
    ) {
        // Background actions
        if (offsetX < -30f && onSwipeLeft != null) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) { leftAction() }
        }
        if (offsetX > 30f && onSwipeRight != null) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) { rightAction() }
        }
        // Foreground content
        Box(Modifier.offset { IntOffset(animatedOffsetX.roundToInt(), 0) }) {
            content()
        }
    }
}
