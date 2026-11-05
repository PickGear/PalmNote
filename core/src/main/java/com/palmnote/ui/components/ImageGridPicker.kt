package com.palmnote.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.palmnote.R
import java.io.File
import kotlin.math.abs

/**
 * 图片网格选择器：最多 [maxCount] 张，长按拖拽排序、右上角删除、空槽点击上传。
 * 账单与资产表单共用，消除重复的拖拽网格逻辑。
 *
 * 选完图先进入裁剪页（[ImageCropDialog]），确认后回调的是**已裁好并落盘**的文件路径；
 * 取消则什么也不发生。各上传位通过 [cropPurpose] 声明比例与输出尺寸。
 *
 * [independentCoverSlot] 打开时首图是**独立槽位**：第 0 格只放封面（1:1），删掉封面后
 * 第 0 格回到空态、后面的图不顶上来的；第 1..3 格自成一组，删其中一张时后面的自行补位。
 */
@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun ImageGridPicker(
    title: String,
    images: List<String>,
    accentColor: Color,
    hint: String,
    cropPurpose: CropPurpose,
    /** 回调带**槽位号**：首图位与后面几格的落地规则不同（1:1 / 自由比例）。 */
    onAddImage: (String, Int) -> Unit,
    additionalCropPurpose: CropPurpose = cropPurpose,
    onRemoveImage: (Int) -> Unit,
    onReorderImages: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    maxCount: Int = 4,
    /**
     * 第一格占位文案（物品的封面位写「首图」）。为 null 时四格一律「上传」——
     * 账单附件是并列的凭证、没有封面概念，文案不能跟着物品一起改。
     */
    firstSlotLabel: String? = null,
    /** 其余格的占位文案（物品写「附件」）；null = 沿用通用的「上传」。 */
    otherSlotLabel: String? = null,
    /**
     * 首图是否独立成槽：真 = 删掉首图后第 0 格空着、后续图不补位，且首图不能被拖走/被顶替
     * （要换封面就删了在第一格重挑，顺带保证它始终按 [cropPurpose] 的 1:1 裁剪）。
     * 账单附件是并列凭证，保持紧密排列。
     */
    independentCoverSlot: Boolean = false
) {
    // 槽位视图：索引 0 = 首图位（可能空着），1.. 为其余图；账单模式下就是原列表顺序。
    // 用它渲染而不是直接用 images，是为了让「首图位空着但后面有图」这种状态能画出来。
    val coverPath = images.coverImagePath()
    val extraPaths = if (independentCoverSlot) images.drop(1).filter { it.isNotBlank() } else emptyList()
    val slotImages: List<String?> = List(maxCount) { i ->
        when {
            !independentCoverSlot -> images.getOrNull(i)
            i == 0 -> coverPath
            else -> extraPaths.getOrNull(i - 1)
        }
    }
    // 可拖动区间：首图独立时只有第 1 格起的一段能拖（首图不能拖走、也不能被顶替）
    val dragMin = if (independentCoverSlot) 1 else 0
    val dragMax = if (independentCoverSlot) extraPaths.size else images.size - 1
    val canDrag = dragMax - dragMin >= 1
    val occupiedCount = slotImages.count { it != null }
    /** 拍照没有「点哪格」这一步，落第一个空槽：首图空着就先补封面。 */
    val firstEmptySlot = slotImages.indexOfFirst { it == null }

    // saveable：旋转/进程重建后带着已选图片回到裁剪页（裁剪调整本身重新来，但不用重新选图）
    var pendingCropUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    // 正在给哪一格配图：决定裁剪规格（首图 1:1 / 其余自由），也决定回填到哪一格
    var pendingCropSlot by rememberSaveable { mutableIntStateOf(0) }
    // 拍摄件的路径，从点「拍照」存到裁剪页关闭：成功后作为裁剪源，关闭时删除。
    // saveable：相机画面期间旋转/进程重建，回来仍能接住 TakePicture 的结果
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // 系统照片选择器：13+ 原生网格，旧系统自动回退文档选择器；与生活模块一致
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pendingCropUri = it } }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (success && path != null) {
            pendingCropUri = cameraCaptureUri(context, File(path))
        } else {
            path?.let { runCatching { File(it).delete() } }
        }
    }

    /** 关掉裁剪页并结算拍摄件：确认与取消共用，删过了就是无害的幂等操作。 */
    fun closeCrop() {
        pendingCropUri = null
        pendingCameraPath?.let { path -> runCatching { File(path).delete() } }
        pendingCameraPath = null
    }

    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            // 首图格按卡片缩略图规格（1:1）裁切；其余格允许自由比例、也允许直接用原图
            purpose = if (pendingCropSlot == 0) cropPurpose else additionalCropPurpose,
            onCancel = { closeCrop() },
            onDone = { path ->
                closeCrop()
                onAddImage(path, pendingCropSlot)
            }
        )
    }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 拍照直进裁剪页：小票/物品常常就在眼前，不必先经相册中转。
            // 用 40dp 圆形可点区替代 IconButton：IconButton 的 48dp 最小触控**布局**
            // 会把标题行撑高，标题文字再居中，与缩略图之间叠出 ~26dp 空白
            // （2026-11-05 真机反馈「附件图片下面空白太大」）。40dp 触控与
            // 「修改预算」胶囊同口径；常驻不隐藏——4/4 时置灰，行高恒定，
            // 传满后头部不再跳动。涟漪按 clip 后的圆形裁切（clickable 在 clip 之后）。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (occupiedCount < maxCount) 1f else 0.38f)
                    .clip(CircleShape)
                    .clickable(
                        enabled = occupiedCount < maxCount && firstEmptySlot >= 0,
                        onClick = {
                            pendingCropSlot = firstEmptySlot.coerceAtLeast(0)
                            val file = cameraCaptureFile(context)
                            pendingCameraPath = file.absolutePath
                            cameraLauncher.launch(cameraCaptureUri(context, file))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = stringResource(R.string.crop_camera),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "$occupiedCount/$maxCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 标题行与槽位行之间刻意零间距：标题文字在 40dp 行内居中，下方自带约 10dp 视觉
    // 留白（扣字形下降部 ≈ 8dp，即提交信息所说的「8dp 口径」）。旧版是 48dp IconButton
    // 行 + 8dp Spacer 叠成 ~26dp 才被真机反馈「空白太大」；行高回到 40dp 后再补
    // spacer/padding 会得 ~18dp，比已定稿的观感松——本组件挂在 ModuleCard 的
    // ColumnScope 里，那层 Column 没有 spacedBy，间距只能显式给，所以这里什么都不加。
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
            // key 只能用稳定值：slotImages 每次重组都是新 List，拿它当 key 会在拖拽途中
            // 反复重启手势（拖动时 dragTotal 变化就会重组），拖到一半就断
            .pointerInput(images.size, canDrag, dragMin, dragMax) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // 首图独立时第 0 格不参与拖拽：封面不能被拖走、也不能被后面的图顶替
                        if (!canDrag) return@detectDragGesturesAfterLongPress
                        val idx = (offset.x / (currentSlotWidth + spacingPx)).toInt()
                        // 按压点不在可拖区间（封面格 / 之后的空槽）就不启动拖拽：
                        // coerce 会把这次按压「挪」到相邻可拖格——手指按在封面上，
                        // 抬起来的却是附件 1，松手前一直是错位视觉（2026-11-06 修）。
                        // 附带效果：账单模式长按最后一张之后的空白也不再拖最后一张。
                        if (idx < dragMin || idx > dragMax) return@detectDragGesturesAfterLongPress
                        draggedIndex = idx
                        dragTotal = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        // onDragStart 可能因按压点越界而未启动（draggedIndex 停在 -1）：
                        // 此时本次长按不应产生任何重排事件，位移直接吞掉——否则
                        // target=(-1+1) 会被 coerce 进可拖区间，发出 (-1, x) 事件
                        if (currentDragIndex < dragMin || currentDragIndex > dragMax) {
                            return@detectDragGesturesAfterLongPress
                        }
                        dragTotal += amount.x
                        val step = currentSlotWidth + spacingPx
                        if (abs(dragTotal) > step * 0.5f) {
                            val target = (currentDragIndex + if (dragTotal > 0) 1 else -1)
                                .coerceIn(dragMin, dragMax)
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
                val path = slotImages[i]
                if (path != null) {
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
                            model = path,
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
                        if (canDrag && i >= dragMin) {
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
                                // 记下用户点的是哪一格：首图格按 1:1 裁、其余格自由比例
                                pendingCropSlot = i
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
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
                            // 第一格是封面位，标「首图」；它按 1:1 裁切、后三格可用原比例，
                            // 四格同写「上传」的话这个差别在界面上完全看不出来
                            val uploadLabel = stringResource(R.string.asset_upload)
                            Text(
                                text = if (i == 0) {
                                    firstSlotLabel ?: uploadLabel
                                } else {
                                    otherSlotLabel ?: uploadLabel
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
    if (occupiedCount == 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
