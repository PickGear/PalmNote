package com.palmnote.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.palmnote.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 全屏裁剪页：与主流相册同形态 —— **选区固定在屏幕中部，图片在下面拖动与双指缩放**。
 *
 * 为什么不是「拖选框」：
 * - 选框贴边时四角手柄会落进系统返回手势热区，手指一拖就被系统抢走（真机实测过）；
 * - 精修小目标（票面数字、合影里的某张脸）必须能把图片放大了再框，选框方案做不到。
 *
 * 口径见 [CropSpec]：物品 / 头像 1:1、壁纸 9:16 锁比例；小票自由比例但一进来就看全图。
 * 取消不落任何文件；确认前可「重置」回初始状态；确认保存后不留原图。
 */
// 裁剪页是「状态 + 三块 UI」的脚手架，长度与分支来自 Compose 本身：
// 几何与夹取全在 ImageCrop.kt 的纯函数里，这里只做组装与回调接线。
@Composable
fun ImageCropDialog(
    sourceUri: Uri,
    purpose: CropPurpose,
    onCancel: () -> Unit,
    onDone: (String) -> Unit
) {
    // saving 提升到 Dialog 外壳：返回键/点外部的禁用判断要在窗口层做
    var saving by remember(sourceUri) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            // 保存中不许退出：协程被取消会中断写盘，可能留下未清理的 .tmp
            dismissOnBackPress = !saving,
            dismissOnClickOutside = !saving
        )
    ) {
        ImageCropDialogContent(
            sourceUri = sourceUri,
            purpose = purpose,
            onCancel = onCancel,
            onDone = onDone,
            saving = saving,
            onSavingChange = { saving = it }
        )
    }
}

/**
 * 裁剪页内容（不含 Dialog 外壳）：状态 + 几何 + 三块 UI 的装配。
 *
 * 独立成 composable 是为了离屏渲染测试能直接驱动真实手势（Dialog 是独立窗口，
 * 测试注入的触摸到不了）；[onGeometryChange] 供测试在每次组合后取当前几何做断言。
 */
@VisibleForTesting
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
fun ImageCropDialogContent(
    sourceUri: Uri,
    purpose: CropPurpose,
    onCancel: () -> Unit,
    onDone: (String) -> Unit,
    saving: Boolean,
    onSavingChange: (Boolean) -> Unit,
    onGeometryChange: ((imageRect: Rect, frame: Rect) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = remember(purpose) { cropSpecOf(purpose) }
    val density = LocalDensity.current

    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(sourceUri) { mutableStateOf(false) }
    var saveFailed by remember(sourceUri) { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(sourceUri) {
        val loaded = decodeImageForCrop(context, sourceUri, max(2048, spec.maxOutputPx))
        bitmap = loaded
        loadFailed = loaded == null
    }

    val viewport = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
    // 选区可活动范围：四周留 24dp，手柄永远够不着屏幕边缘的系统手势区
    val edgeInset = with(density) { 24.dp.toPx() }
    val frameBounds = remember(viewport, edgeInset) {
        if (viewport.width <= edgeInset * 2f || viewport.height <= edgeInset * 2f) {
            Rect.Zero
        } else {
            Rect(edgeInset, edgeInset, viewport.width - edgeInset, viewport.height - edgeInset)
        }
    }
    // 1x 适配也放进内缩区域：这样「整张图」本身离屏幕边就有 24dp，
    // 缩小到最小时选区贴着图片边缘，手柄同样不会落到系统手势区里。
    val fit = remember(containerSize, bitmap, frameBounds) {
        val bmp = bitmap
        if (bmp == null || frameBounds == Rect.Zero) {
            Rect.Zero
        } else {
            fitImageRect(bmp.width, bmp.height, frameBounds.size)
                .translate(frameBounds.left, frameBounds.top)
        }
    }
    val minFrameSide = with(density) { 64.dp.toPx() }
        .coerceAtMost(min(frameBounds.width, frameBounds.height) * 0.2f)

    // 手势检测器的闭包可能停留在旧组合（离屏手势测试实测）：旋转后 fit 已变、闭包里还是
    // 旧值，按旧 fit 夹取就放跑了越界偏移。经 rememberUpdatedState 给闭包一个**稳定的
    // State 对象**（对象只建一次、值每次组合刷新），手势层读 .value 永远是最新几何；
    // frameSize/scale/offset 本身就是无 key 或稳定 key 的状态，同理永远新鲜。
    val fitState = rememberUpdatedState(fit)

    // 当前生效的比例：锁比例场景（头像/首图/壁纸）由 spec 定死；自由比例场景（账单附件、
    // 物品附加图）允许用户从常见比例里挑一个。切比例只改这个值，几何全部读它。
    var activeAspect by remember(sourceUri, spec.aspect) { mutableStateOf(spec.aspect) }
    // 选框只有「尺寸」一个状态：中心恒为可用区域中心（与小米相册、iOS 同形态）。
    // 位置不进状态，也就不存在「框跑偏 / 框被拖出图片」这类问题的空间。
    // 尺寸是临时的：拖角松手即收口（settleAfterResize）到「同比例的最大框」，
    // 比例本身保留下来；静止时框恒为该比例下的最大尺寸。
    // ⚠️ 不能带 remember key：key 变化会重建 State 对象，而手势检测器闭包
    // （pointerInput 只创建一次）还握着旧对象——UI 读新、手势读旧，两边分裂
    // （离屏手势测试实测：点比例胶囊后手势层看到的是永远为 0 的旧状态）。
    // 需要重置的场景（换比例/换图）都走显式赋值。
    var frameSize by remember { mutableStateOf(Size.Zero) }
    val frame = remember(frameSize, frameBounds) {
        if (frameBounds == Rect.Zero) Rect.Zero else centeredRectOf(frameSize, frameBounds.center)
    }
    var scale by remember(sourceUri) { mutableStateOf(1f) }
    var offset by remember(sourceUri) { mutableStateOf(Offset.Zero) }
    // 顺时针转过几个 90°（0..3）。落盘时必须带上它：显示图是转过方向的，
    // 而高清裁切会从 sourceUri 重新解码出**未旋转**的原图，不补转就裁错位置、方向也不对。
    var rotationTurns by remember(sourceUri) { mutableStateOf(0) }

    val imageRect = imageRectFor(fit, scale, offset, viewport)

    // 该比例在「屏幕可用区域 ∩ 图片」内能取到的最大尺寸：初始值、拖角上限，
    // 也是拖角松手收口后的回归尺寸（settleToMaxFrame）。
    // 再大就会超出图片，所以用户只能往小调。
    // 直接派生、不 remember：这几个值都是纯几何计算，缓存反而可能在切比例后
    // 留住旧比例的最大框，被收口写回 frameSize 时把固定比例抹成整图比例
    val frameArea = intersectRects(frameBounds, fit)
    val maxFrameSize = if (fit == Rect.Zero || frameArea == Rect.Zero) {
        Size.Zero
    } else {
        initialCropFrame(fit, frameArea, activeAspect).size
    }

    // 几何入参变化（图片加载/旋转、容器尺寸、换比例）后补齐选区与平移：
    // - 尺寸为 0 = 还没算过（或刚被置零），按当前上限填最大值；
    // - 上限变小时选框同比例收进（frameSize 是独立状态不会自己跟），否则框比图大、
    //   裁切坐标越界、输出被钳成整图；
    // - fit/框变化后平移的合法范围也变窄了：把图片重新夹回「盖住选框」，
    //   不然先前贴边停放的图片会在撑大后的框里露出一圈图外背景（2026-11-04 截图反馈）。
    // key 用几何入参而不是 maxFrameSize 的值：锁比例下 fit 变了、最大框尺寸可能恰好不变，
    // 按 value 记 key 会漏掉这类变化。
    LaunchedEffect(fit, frameBounds, activeAspect) {
        if (fit == Rect.Zero || maxFrameSize == Size.Zero) return@LaunchedEffect
        if (frameSize == Size.Zero) {
            frameSize = maxFrameSize
        } else {
            val clamped = clampFrameSizeInside(frameSize, maxFrameSize)
            if (clamped != frameSize) frameSize = clamped
        }
        offset = clampImageOffset(offset, fit.size, scale, centeredRectOf(frameSize, frameBounds.center), viewport)
    }

    // 手势处理器里禁止使用 `frame` 这类「组合期捕获的 val」做夹取：检测器闭包可能停留在
    // 旧组合（真机/离屏都实测过），而 frameSize/scale/offset 是状态、永远新鲜。
    // 一律由状态现算当前框，夹取输入才与渲染一致。
    fun currentFrame(): Rect = centeredRectOf(frameSize, frameBounds.center)

    fun applyTransform(centroid: Offset, pan: Offset, zoom: Float) {
        val currentFit = fitState.value
        if (saving || currentFit == Rect.Zero) return
        val previousScale = scale
        val nextScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        // 只在「从放大缩回 1x」这一下把选区贴回图片边界；停在 1x 时仍要能拖动，
        // 否则用户无法上下滑动去选图里的另一块（1x 只是「整图可见」，不是「不能动」）
        val justReachedMin = nextScale <= 1.001f && scale > 1.001f
        scale = nextScale
        if (justReachedMin) {
            // 缩回 1x 只把图片归中；框的尺寸与位置都不动（这正是「框固定」的含义）
            offset = Offset.Zero
            return
        }
        // 以双指中心为锚缩放：手指下的内容留在原地（不这样的话放大时画面会从图片中心蹿走），
        // 再叠加本次平移量
        val anchored = zoomAround(centroid, previousScale, offset, nextScale, currentFit, currentFrame(), viewport)
        // 平移只夹偏移：框「被拖着走」是错觉的来源——框应当钉住，图片在下面滑
        offset = clampImageOffset(anchored + pan, currentFit.size, nextScale, currentFrame(), viewport)
    }

    /** 拖角只改尺寸；位置由 [centeredRectOf] 从中心推导，永远不会跑偏。 */
    fun applyFrameSize(next: Size) {
        if (saving || next == Size.Zero) return
        frameSize = next
        // 框撑大后平移的合法范围变窄，先前贴边停放的图片要重新夹回「盖住选框」，
        // 否则框会伸到图外（2026-11-04 截图反馈）
        offset = clampImageOffset(offset, fitState.value.size, scale, currentFrame(), viewport)
    }

    /**
     * 拖角松手后的收口：图片放大到所选区域、选框按**当前比例**回到同比例的最大居中框
     * （松手放大重取景、拖图时框不动、框 ⊆ fit ⊆ 图片保证永不越出图片）。
     * 收口尺寸由 [settleToMaxFrame] 在可用区域内按当前框比例现算，不再照抄
     * maxFrameSize——否则自由比例拖出的形状会被整图比例抹平。
     */
    fun settleAfterResize() {
        val currentFit = fitState.value
        if (saving || currentFit == Rect.Zero || maxFrameSize == Size.Zero) return
        val result = settleToMaxFrame(
            frameSize = frameSize,
            maxFrameSize = maxFrameSize,
            frame = currentFrame(),
            scale = scale,
            offset = offset,
            fit = currentFit,
            container = viewport,
            maxZoom = MAX_ZOOM,
            // 自由比例下 maxFrameSize 就是整图比例，必须给原始可用区域，
            // 收口才能按「用户刚拖出的比例」求最大框；锁比例时以所选比例为准
            availableSize = frameArea.size,
            lockedAspect = activeAspect
        )
        scale = result.scale
        offset = result.offset
        frameSize = result.frameSize
    }

    // 旋转替换下来的位图：隔一代再回收（当前帧的绘制队列可能还握着它）
    var retiredBitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }

    fun resetAll() {
        // 重置 = 回到刚打开时的样子：方向、缩放、选区一起复原。
        // 转过方向时先把位图转回去；选区置零交给 LaunchedEffect 按新方向的 fit 重建 ——
        // fit 是 remember(bitmap…) 算出来的，本帧拿到的还是旋转态的旧值，直接回填会把框
        // 钉在错误方向的可用范围上。没转过（或没转回去）时 maxFrameSize 就是当前位图
        // 的正确初值，必须直接回填：effect 的 key 不变不会重启，置零会让选框消失。
        var restoredOrientation = false
        val bmp = bitmap
        if (bmp != null && rotationTurns != 0) {
            val restored = rotateBitmapQuarter(bmp, 4 - rotationTurns)
            // 转不回去（极端低内存）时保持现状：把 bmp 同时登记成 retired 会让下次旋转
            // 回收掉正在显示的位图，宁可方向没复原也不能崩
            if (restored !== bmp) {
                retiredBitmap?.recycle()
                retiredBitmap = bmp
                bitmap = restored
                rotationTurns = 0
                restoredOrientation = true
            }
        }
        scale = 1f
        offset = Offset.Zero
        frameSize = if (restoredOrientation) Size.Zero else maxFrameSize
    }

    fun rotateQuarter() {
        val bmp = bitmap ?: return
        if (saving) return
        // 直接旋转位图：坐标系不变，裁切映射无需另算。
        // 只回收「再上一张」——刚被替换的那张可能还在当前帧的绘制队列里，立刻 recycle 会崩。
        val rotated = rotateBitmapQuarter(bmp, 1)
        if (rotated !== bmp) {
            retiredBitmap?.recycle()
            retiredBitmap = bmp
            bitmap = rotated
            rotationTurns = (rotationTurns + 1) % 4
        }
        // 旋转后图片宽高互换，旧的中心偏移不再对应同一块内容 —— 归零让新图重新居中，
        // 缩放级别保留（放大看细节后转方向不该被打回 1x）。
        // 选框尺寸保持不变（保住用户框好的范围）；新方向下若超限，由几何 effect
        // （LaunchedEffect(fit, …)）同比例收进。
        offset = Offset.Zero
    }

    /** 切到某个比例；[next] 为 null 表示不锁定。选择器只在自由比例场景出现。 */
    fun selectAspect(next: Float?) {
        if (sameRatio(activeAspect, next)) return
        activeAspect = next
        scale = 1f
        offset = Offset.Zero
        // 直接按新比例算出最大内接框：切比例当帧生效，也不给「旧比例的框尺寸」
        // 留存活窗口（先前清零再靠 effect 回填，缩放/重组后容易看到旧框比例）。
        frameSize = if (fit == Rect.Zero || frameArea == Rect.Zero) {
            Size.Zero
        } else {
            initialCropFrame(fit, frameArea, next).size
        }
    }

    fun toggleZoom(target: Offset) {
        val currentFit = fitState.value
        if (saving || currentFit == Rect.Zero) return
        if (scale > 1.02f) {
            // 回到 1x：只把图片归中，框保持当前尺寸（框是固定的）
            scale = 1f
            offset = Offset.Zero
        } else {
            val targetScale = min(MAX_ZOOM, 2.5f)
            offset = zoomAround(target, scale, offset, targetScale, currentFit, currentFrame(), viewport)
            scale = targetScale
        }
    }

    fun confirm() {
        val bmp = bitmap ?: return
        if (saving || frame == Rect.Zero || imageRect.width <= 0f) return
        onSavingChange(true)
        saveFailed = false
        val normalized = Rect(
            (frame.left - imageRect.left) / imageRect.width,
            (frame.top - imageRect.top) / imageRect.height,
            (frame.right - imageRect.left) / imageRect.width,
            (frame.bottom - imageRect.top) / imageRect.height
        )
        // 进协程前取快照：saving 期间画布与顶栏手势全部禁用，值不会变，取一次更明确
        val turns = rotationTurns
        scope.launch {
            // 显示用图只解到 2048：小区域再放大会发虚，确认时按选区占比重解一次拿原生像素
            val decoded = decodeForCropQuality(context, sourceUri, spec, normalized)
            // normalized 是相对「可能已旋转」的显示图算出来的，而重解码拿到的是**未旋转**原图：
            // 不补转就会裁到错误区域、输出还是原方向（2026-11-03 真机反馈「旋转后应用不生效」）
            val oriented = if (decoded != null && turns != 0) {
                rotateBitmapQuarter(decoded, turns)
            } else {
                decoded ?: bmp
            }
            val path = cropBitmapToFile(context, oriented, spec, normalized)
            when {
                // 用的就是状态里的显示图，后面还要继续显示，不能回收
                oriented === bmp -> Unit
                // 只有重解码图这一张
                decoded === oriented -> oriented.recycle()
                // 重解码图 + 旋转副本两张
                else -> {
                    oriented.recycle()
                    decoded?.recycle()
                }
            }
            onSavingChange(false)
            if (path != null) onDone(path) else saveFailed = true
        }
    }

    fun useOriginal() {
        if (saving) return
        onSavingChange(true)
        saveFailed = false
        val turns = rotationTurns
        scope.launch {
            // 转过方向就不能原样拷字节（原文不带旋转），改用「重解 → 旋转 → 落盘」，仍不裁剪
            val path = if (turns == 0) {
                saveOriginalCopy(context, sourceUri, spec)
            } else {
                saveRotatedOriginal(context, sourceUri, turns, spec)
            }
            onSavingChange(false)
            if (path != null) onDone(path) else saveFailed = true
        }
    }

    // effect 只在进入时创建一次，必须读实时值，否则 onDispose 拿到的是初始的 null
    val latestRetired by rememberUpdatedState(retiredBitmap)
    DisposableEffect(Unit) {
        onDispose { latestRetired?.recycle() }
    }

    // 每次组合后把当前几何交给外部（离屏测试据此断言「图片盖住选框」并按框角注入手势）。
    // 裁剪几何是裁剪区局部坐标；测试注入的是窗口根坐标，挂「裁剪区」的根内原点做平移
    // （注意不能挂外层 Box——它不含顶栏偏移，会整体差一个顶栏高度）。
    var cropAreaOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    SideEffect {
        onGeometryChange?.invoke(imageRect.translate(cropAreaOriginInRoot), frame.translate(cropAreaOriginInRoot))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CropTopBar(enabled = !saving, onCancel = onCancel, onRotate = ::rotateQuarter, onReset = ::resetAll)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 放大后的图片会超出这个区域，必须裁剪，否则会盖到顶栏/底栏上
                    .clipToBounds()
                    .onSizeChanged { containerSize = it }
                    .onGloballyPositioned { cropAreaOriginInRoot = it.boundsInRoot().topLeft }
            ) {
                val bmp = bitmap
                if (bmp == null) {
                    CropPlaceholder(loadFailed = loadFailed)
                } else {
                    CropCanvas(
                        bitmap = bmp,
                        imageRect = imageRect,
                        frame = frame,
                        circular = spec.maskShape == CropMaskShape.CIRCLE,
                        sizeBounds = CropSizeBounds(frameSize, maxFrameSize, activeAspect, minFrameSide),
                        onTransform = ::applyTransform,
                        onFrameSizeChange = ::applyFrameSize,
                        onFrameSizeSettle = ::settleAfterResize,
                        onDoubleTap = ::toggleZoom
                    )
                }
            }
            CropBottomBar(
                ratioBar = {
                    if (spec.optionalCrop) {
                        RatioSelector(selected = activeAspect, onSelect = ::selectAspect)
                    } else {
                        RatioTag(activeAspect)
                    }
                },
                saving = saving,
                saveFailed = saveFailed,
                canConfirm = bitmap != null && !saving && frame != Rect.Zero && imageRect != Rect.Zero,
                optionalOriginal = spec.optionalCrop,
                onUseOriginal = ::useOriginal,
                onConfirm = ::confirm
            )
        }
    }
}

@VisibleForTesting
@Composable
fun CropTopBar(
    enabled: Boolean,
    onCancel: () -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit
) {
    // 标题要相对**整条顶栏**居中：右侧有两个动作（旋转 + 重置）、左侧只有一个，
    // 用 Row.weight 分到的剩余空间居中会整体偏左（2026-11-04 用户反馈）。
    // 用 Box 叠一层：标题绝对居中，按钮各自贴边，互不影响。
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text = stringResource(R.string.crop_title),
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, enabled = enabled) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRotate, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Filled.Rotate90DegreesCw,
                        contentDescription = stringResource(R.string.crop_rotate),
                        tint = Color.White
                    )
                }
                TextButton(onClick = onReset, enabled = enabled) {
                    Text(stringResource(R.string.crop_reset), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CropPlaceholder(loadFailed: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loadFailed) {
            Text(
                text = stringResource(R.string.crop_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun CropBottomBar(
    ratioBar: @Composable () -> Unit,
    saving: Boolean,
    saveFailed: Boolean,
    canConfirm: Boolean,
    optionalOriginal: Boolean,
    onUseOriginal: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ratioBar()
        Text(
            text = stringResource(R.string.crop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        if (saveFailed) {
            Text(
                text = stringResource(R.string.crop_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (optionalOriginal) {
                // 与「使用这张」同一套可用条件：保存中不能再发第二个写盘任务
                TextButton(onClick = onUseOriginal, enabled = canConfirm) {
                    Text(stringResource(R.string.crop_use_original), color = Color.White)
                }
            }
            Button(onClick = onConfirm, enabled = canConfirm, modifier = Modifier.weight(1f)) {
                // 重解码 + 压缩在大图上有 0.5–2s 无输出窗口：按钮内转圈，
                // 否则用户以为没点上（2026-11-04 增强）
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.crop_use))
            }
        }
    }
}

/** 拖角需要的全部约束：当前尺寸、上限、比例、最小边。打包成一份，免得参数一路往下传。 */
private data class CropSizeBounds(
    val current: Size,
    val max: Size,
    val aspect: Float?,
    val minSide: Float
)

/** 图片层 + 遮罩 + 手势：单指/双指都在图片上操作，四角手柄单独接管自己的热区。 */
@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    imageRect: Rect,
    frame: Rect,
    circular: Boolean,
    sizeBounds: CropSizeBounds,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onFrameSizeChange: (Size) -> Unit,
    onFrameSizeSettle: () -> Unit,
    onDoubleTap: (Offset) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnFrameSizeChange by rememberUpdatedState(onFrameSizeChange)
    val currentOnFrameSizeSettle by rememberUpdatedState(onFrameSizeSettle)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentBounds by rememberUpdatedState(sizeBounds)

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (imageRect != Rect.Zero) {
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt())
            )
        }
    }
    CropOverlay(frame = frame, circular = circular)

    // 图片手势：拖动平移、双指缩放；双击在 1x 与 2.5x 之间切换（以点击点为锚）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { position -> currentOnDoubleTap(position) })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    currentOnTransform(centroid, pan, zoom)
                }
            }
    )

    // 四角手柄：热区 56dp，独立于图片手势。拖动只改**尺寸**，位置恒为居中
    val handles = listOf(
        CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT
    )
    handles.forEach { handle ->
        CropCornerHandle(
            handle = handle,
            frame = frame,
            sizeBounds = currentBounds,
            onSizeChange = currentOnFrameSizeChange,
            onResizeEnd = currentOnFrameSizeSettle
        )
    }
}

@Composable
private fun CropCornerHandle(
    handle: CropHandle,
    frame: Rect,
    sizeBounds: CropSizeBounds,
    onSizeChange: (Size) -> Unit,
    onResizeEnd: () -> Unit
) {
    // 约束必须是「当前值」：写成首帧快照的话，换比例或缩放后手柄会按旧约束算
    val currentBounds by rememberUpdatedState(sizeBounds)
    val currentOnSizeChange by rememberUpdatedState(onSizeChange)
    val currentOnResizeEnd by rememberUpdatedState(onResizeEnd)
    val handleSize = 56.dp
    val sizePx = with(LocalDensity.current) { handleSize.toPx() }
    val center = when (handle) {
        CropHandle.TOP_LEFT -> Offset(frame.left, frame.top)
        CropHandle.TOP_RIGHT -> Offset(frame.right, frame.top)
        CropHandle.BOTTOM_LEFT -> Offset(frame.left, frame.bottom)
        CropHandle.BOTTOM_RIGHT -> Offset(frame.right, frame.bottom)
    }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - sizePx / 2f).roundToInt(), (center.y - sizePx / 2f).roundToInt()) }
            .size(handleSize)
            .pointerInput(Unit) {
                // 按下即认领指针（consume down）：图片层的整体手势遇到任何已消费的 change
                // 会整事件退出（detectTransformGestures 的取消口径），从此这个指针只归手柄。
                // 不认领的话，慢速拖角时预滑距阶段的移动会先漏给图片层被抢成平移，
                // 框会「跑出去」（2026-11-04 真机反馈的另一半根因）。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        // 本指针抬起或全部指针离屏（含本指针被挤掉）即结束本轮手势
                        if (change == null || !change.pressed || event.changes.none { it.pressed }) break
                        val drag = change.positionChange()
                        if (drag != Offset.Zero) {
                            change.consume()
                            // 只改尺寸（对称映射：手指移 dx，宽变化 2dx），位置由中心推导
                            currentOnSizeChange(
                                resizeCenteredCropSize(
                                    currentBounds.current, handle, drag.x, drag.y,
                                    currentBounds.aspect, currentBounds.minSide, currentBounds.max
                                )
                            )
                        }
                    }
                    // 手势流结束（松手/被系统抢走）即收口：放大所选区域、选框回最大居中。
                    // 放在循环外保证取消路径也会收口，框不会停在中间尺寸。
                    currentOnResizeEnd()
                }
            }
    )
}

/**
 * 选区外的压暗遮罩 + 选框描边 + 四角加粗手柄；矩形场景另带三分线。
 *
 * [circular]（头像场景）：遮罩挖圆洞而不是方洞、不画三分线——输出按方形裁切、
 * 界面按圆形显示，预览与最终观感一致；方形边界淡化保留，四角手柄仍按方形工作。
 */
@VisibleForTesting
@Composable
fun CropOverlay(frame: Rect, circular: Boolean) {
    if (frame == Rect.Zero) return
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        // 先整层压暗，再把选区「擦」出来（Offscreen 保证 Clear 只作用于本层）
        drawRect(Color.Black.copy(alpha = 0.55f))
        if (circular) {
            drawCircle(
                color = Color.Transparent,
                radius = min(frame.width, frame.height) / 2f,
                center = frame.center,
                blendMode = BlendMode.Clear
            )
        } else {
            drawRect(
                color = Color.Transparent,
                topLeft = frame.topLeft,
                size = frame.size,
                blendMode = BlendMode.Clear
            )
        }
        val thin = 1.dp.toPx()
        if (circular) {
            // 将被保留的圆形区域用实线描出；方形边界淡化（四角手柄在方框角上）
            drawCircle(
                color = Color.White,
                radius = min(frame.width, frame.height) / 2f,
                center = frame.center,
                style = Stroke(width = thin)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = frame.topLeft,
                size = frame.size,
                style = Stroke(width = thin)
            )
        } else {
            // 三分线：只做构图参考，透明度压得很低
            for (i in 1..2) {
                val x = frame.left + frame.width * i / 3f
                val y = frame.top + frame.height * i / 3f
                drawLine(Color.White.copy(alpha = 0.25f), Offset(x, frame.top), Offset(x, frame.bottom), thin)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(frame.left, y), Offset(frame.right, y), thin)
            }
            drawRect(
                color = Color.White,
                topLeft = frame.topLeft,
                size = frame.size,
                style = Stroke(width = thin)
            )
        }
        drawCornerTicks(frame)
    }
}

/** 四角加粗短线，告诉用户这里可以拖。 */
private fun DrawScope.drawCornerTicks(frame: Rect) {
    val tick = 20.dp.toPx()
    val thick = 3.dp.toPx()
    val corners = listOf(
        Triple(frame.topLeft, Offset(1f, 0f), Offset(0f, 1f)),
        Triple(Offset(frame.right, frame.top), Offset(-1f, 0f), Offset(0f, 1f)),
        Triple(Offset(frame.left, frame.bottom), Offset(1f, 0f), Offset(0f, -1f)),
        Triple(Offset(frame.right, frame.bottom), Offset(-1f, 0f), Offset(0f, -1f))
    )
    corners.forEach { (origin, dx, dy) ->
        drawLine(
            Color.White, origin, Offset(origin.x + dx.x * tick, origin.y + dy.x * tick),
            strokeWidth = thick, cap = StrokeCap.Round
        )
        drawLine(
            Color.White, origin, Offset(origin.x + dx.y * tick, origin.y + dy.y * tick),
            strokeWidth = thick, cap = StrokeCap.Round
        )
    }
}

/** 自由比例场景可选的比例；null = 不锁定。前六项覆盖绝大多数用法。 */
private val RATIO_OPTIONS: List<Float?> = listOf(
    null, 1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f
)

/** 比例标签：自由比例显示文案，锁比例显示「1:1 / 4:3 / 9:16」这类短标记。 */
@Composable
private fun ratioLabelOf(aspect: Float?): String =
    if (aspect == null || aspect <= 0f) stringResource(R.string.crop_ratio_free) else ratioText(aspect)

/** 已知比例精确映射：9:16 用「1:x」四舍五入会变成 1:2，必须逐个判。 */
private fun ratioText(aspect: Float): String = when {
    abs(aspect - 1f) < 0.01f -> "1:1"
    abs(aspect - 4f / 3f) < 0.01f -> "4:3"
    abs(aspect - 3f / 4f) < 0.01f -> "3:4"
    abs(aspect - 16f / 9f) < 0.01f -> "16:9"
    abs(aspect - 9f / 16f) < 0.01f -> "9:16"
    aspect >= 1f -> "${aspect.roundToInt()}:1"
    else -> "1:${(1f / aspect).roundToInt()}"
}

/** 浮点比例判等：拖动/夹取会产生尾差，不能直接 ==。 */
private fun sameRatio(a: Float?, b: Float?): Boolean =
    if (a == null || b == null) a == null && b == null else abs(a - b) < 0.01f

/** 锁比例场景的静态标签（不可点，比例由产品规格定死）。 */
@Composable
private fun RatioTag(aspect: Float?) {
    Text(
        text = ratioLabelOf(aspect),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.9f),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/**
 * 自由比例场景的比例选择器：一排可点胶囊，选中项反白。
 *
 * 只在 `spec.optionalCrop` 时出现——头像/首图/壁纸的比例是产品规格（圆形头像塞 16:9 没意义），
 * 不开放给用户改；账单附件与物品附加图本来就自由，给几个常见比例省得手动拖。
 */
@VisibleForTesting
@Composable
fun RatioSelector(selected: Float?, onSelect: (Float?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RATIO_OPTIONS.forEach { aspect ->
            val isSelected = sameRatio(selected, aspect)
            Text(
                text = ratioLabelOf(aspect),
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.14f)
                    )
                    .clickable { onSelect(aspect) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/** 最大放大倍数：再高只是在看像素点。 */
private const val MAX_ZOOM = 6f
