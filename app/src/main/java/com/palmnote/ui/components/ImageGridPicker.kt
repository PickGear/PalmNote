package com.palmnote.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.palmnote.R
import kotlin.math.abs

/**
 * 图片网格选择器：最多 [maxCount] 张，长按拖拽排序、右上角删除、空槽点击上传。
 * 账单与资产表单共用，消除重复的拖拽网格逻辑。
 */
@Composable
@Suppress("LongMethod")
fun ImageGridPicker(
    title: String,
    images: List<String>,
    accentColor: Color,
    hint: String,
    onAddImage: (android.net.Uri) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onReorderImages: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    maxCount: Int = 4
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onAddImage) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            text = "${images.size}/$maxCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    var slotWidthPx by remember { mutableFloatStateOf(80f) }
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }
    val currentSlotWidth by rememberUpdatedState(slotWidthPx)
    val currentDragIndex by rememberUpdatedState(draggedIndex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { slotWidthPx = (it.width - spacingPx * 3) / 4f }
            .pointerInput(images.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (images.isEmpty()) return@detectDragGesturesAfterLongPress
                        val idx = (offset.x / (currentSlotWidth + spacingPx)).toInt()
                            .coerceIn(0, images.size - 1)
                        draggedIndex = idx
                        dragTotal = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragTotal += amount.x
                        val step = currentSlotWidth + spacingPx
                        if (abs(dragTotal) > step * 0.5f) {
                            val target = (currentDragIndex + if (dragTotal > 0) 1 else -1)
                                .coerceIn(0, images.size - 1)
                            if (target != currentDragIndex) {
                                onReorderImages(currentDragIndex, target)
                                draggedIndex = target
                                dragTotal -= step * if (dragTotal > 0) 1f else -1f
                            }
                        }
                    },
                    onDragEnd = { draggedIndex = -1; dragTotal = 0f },
                    onDragCancel = { draggedIndex = -1; dragTotal = 0f }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until maxCount) {
            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                if (i < images.size) {
                    val isDragging = i == draggedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isDragging) 2f else 0f)
                            .then(
                                if (isDragging) Modifier.graphicsLayer {
                                    translationX = dragTotal
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                } else Modifier
                            )
                            .clip(MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = images[i],
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemoveImage(i) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    MaterialTheme.shapes.medium
                                )
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (images.size > 1) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = stringResource(R.string.asset_drag_to_reorder),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium)
                            .background(accentColor.copy(alpha = 0.1f))
                            .clickable {
                                if (images.size < maxCount) {
                                    imagePickerLauncher.launch("image/*")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                null,
                                tint = accentColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                stringResource(R.string.asset_upload),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
    if (images.isEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
