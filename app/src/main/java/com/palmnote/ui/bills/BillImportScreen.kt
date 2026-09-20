package com.palmnote.ui.bills

import android.net.Uri
import com.palmnote.domain.model.BillType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.palmnote.app.R
import com.palmnote.data.export.BillCsvImporter
import com.palmnote.data.export.ParsedBill
import com.palmnote.data.ocr.FieldConfidence
import com.palmnote.data.ocr.OcrBillResult
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================ 导入页设计令牌 ============================
/** 三屏统一卡片纵向间距（对齐 App 卡片节奏） */
private val ImportCardGap = 8.dp

/** 文件导入渠道品牌色（降饱和）——仅用于来源徽章，非数据库数据色 */
private val ImportChannelWechat = Color(0xFF67AC5B)
private val ImportChannelAlipay = Color(0xFF4E8ED8)

/**
 * 教程卡「查看教程」按钮的前景：**故意固定为深色**——按钮底色是恒定黄
 * （`warning` 在深浅主题下都是黄），而 `onSurface` 浅色主题近黑、深色主题是浅灰，
 * 套上去在黄底上会读不出来。底色本身仍走 `colorScheme.warning()`（暗色自动切 DarkWarning）。
 */
private val ImportHelpOnTint = Color(0xFF3A2E10)

/** 「分拣台」待复核卡描边：暖色，仅表达「待处理」语义，非数据色、非大色块 */
private val ImportPendingStroke = Color(0xFFF0DCC8)

/** 待复核卡「点此可改」的橙色虚线间距（dp）：3 实 3 空 */
private const val IMPORT_DASH_DP = 3f

/**
 * 三屏统一卡片外壳：与 core 共享组件 ModuleCard 完全同参——
 * surface 底 + shapes.large(16dp) 圆角 + 1dp outlineVariant 描边 + 0 阴影 + 内距 12dp。
 */
@Composable
private fun ImportCardSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

/** 三屏统一底部操作栏：顶边 1dp 分隔线 + 16/10 内边距 + 0 阴影贴底 */
@Composable
private fun ImportBottomBar(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * 导入页顶栏。文件导入预览态（分拣台）用 15sp/Medium 的「导入确认」标题 + 右侧「全部确认」；
 * 「全部确认」语义 = 清空复核队列（把待复核一并标记已确认），**不触发导入** —— 导入只由底部主按钮触发。
 * 无待复核项时隐藏该按钮（[showConfirmAll] = false）。其余阶段沿用全局 [CompactTopAppBar] 大标题，
 * 行为与历史一致。OCR 页不受影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillImportTopBar(
    fileReviewStage: Boolean,
    showConfirmAll: Boolean,
    onBack: () -> Unit,
    onConfirmAll: () -> Unit
) {
    if (fileReviewStage) {
        CompactTopAppBar(
            title = {
                Text(
                    stringResource(R.string.bill_import_confirm_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            navigationIcon = { ImportBackButton(onBack) },
            actions = {
                if (showConfirmAll) {
                    TextButton(onClick = onConfirmAll) {
                        Text(
                            stringResource(R.string.bill_import_confirm_all),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        )
    } else {
        CompactTopAppBar(
            title = stringResource(R.string.bill_import_title),
            navigationIcon = { ImportBackButton(onBack) }
        )
    }
}

@Composable
private fun ImportBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillImportScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BillImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 「分拣台」已确认集合：纯 UI 状态（不改业务/不落库）；离开文件预览即清空，避免下次导入残留
    var confirmedReview by remember { mutableStateOf(emptySet<Int>()) }
    // 文件页三态筛选；默认「待复核」（进页面即分拣台），离开文件预览一并复位
    var fileFilter by remember { mutableStateOf(ReviewFilter.REVIEW) }
    // 「待复核」队列只统计**会被导入**的行（已勾选）：默认不勾选（不计收支）或用户手动取消勾选的行不占额度
    val reviewIndices = remember(state.parsed, state.selectedIndices) {
        state.parsed.indices
            .filter {
                it in state.selectedIndices &&
                    reviewReasonOf(
                        state.parsed[it].amount, state.parsed[it].date,
                        state.parsed[it].merchant, state.parsed[it].category,
                        state.parsed[it].categoryResolved
                    ) != null
            }
            .toSet()
    }
    LaunchedEffect(state.stage, state.mode) {
        if (state.stage != ImportStage.PREVIEW || state.mode != ImportMode.FILE) {
            confirmedReview = emptySet()
            fileFilter = ReviewFilter.REVIEW
        }
    }
    // 仅文件导入的预览态走「分拣台」顶栏（标题/全部确认）；OCR 页保持原样
    val fileReviewStage = state.stage == ImportStage.PREVIEW && state.mode == ImportMode.FILE
    // 顶栏「全部确认」可见性：仅「待复核」档且有待复核项时（= 复核队列 - 已确认）才显示
    val pendingReviewCount = (reviewIndices - confirmedReview).size
    val showConfirmAll = fileReviewStage && fileFilter == ReviewFilter.REVIEW && pendingReviewCount > 0

    val unknownFileLabel = stringResource(R.string.bill_import_unknown_file)
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: unknownFileLabel
            viewModel.parseFile(context, uri, fileName)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.processOcrImage(context, uri)
    }
    val launchImagePicker = {
        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // 失败记录导出：SAF 建文件，写出 BillCsvImporter 可读回的 CSV
    val failedCsvName = stringResource(R.string.bill_import_failed_csv_name)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportFailures(uri)
    }

    Scaffold(
        topBar = {
            BillImportTopBar(
                fileReviewStage = fileReviewStage,
                showConfirmAll = showConfirmAll,
                onBack = onNavigateBack,
                onConfirmAll = { confirmedReview = reviewIndices }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.stage == ImportStage.IDLE) {
                ModeSelection(
                    onSelectFile = { viewModel.setMode(ImportMode.FILE); filePickerLauncher.launch(arrayOf("text/*", "*/*")) },
                    onSelectOcr = { viewModel.setMode(ImportMode.OCR); launchImagePicker() }
                )
            } else {
                TabRow(
                    selectedTabIndex = if (state.mode == ImportMode.FILE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(selected = state.mode == ImportMode.FILE, onClick = {
                        viewModel.setMode(ImportMode.FILE)
                        filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                    }) { Text(stringResource(R.string.bill_import_file), modifier = Modifier.padding(12.dp)) }
                    Tab(selected = state.mode == ImportMode.OCR, onClick = {
                        viewModel.setMode(ImportMode.OCR)
                        launchImagePicker()
                    }) { Text(stringResource(R.string.bill_import_image), modifier = Modifier.padding(12.dp)) }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.stage) {
                        ImportStage.IDLE -> {}
                        ImportStage.PARSING -> LoadingContent(stringResource(R.string.bill_import_parsing), onCancel = viewModel::cancelWork)
                        ImportStage.IMPORTING -> LoadingContent(stringResource(R.string.bill_import_importing), onCancel = viewModel::cancelWork)
                        // 加密 zip：内容区留空，由页面级的密码弹窗承载交互
                        ImportStage.PASSWORD -> {}
                        ImportStage.DONE -> DoneContent(
                            count = state.importCount,
                            skipped = state.skippedCount,
                            failedCount = state.failures.size,
                            canUndo = state.importedBillIds.isNotEmpty(),
                            message = state.actionMessage,
                            onUndo = viewModel::undoImport,
                            onExportFailures = { exportLauncher.launch(failedCsvName) },
                            onBack = onNavigateBack,
                            onContinue = { viewModel.reset() }
                        )
                        ImportStage.ERROR -> ErrorContent(
                            error = state.error ?: stringResource(R.string.bill_import_failed),
                            diagnostic = state.diagnostic,
                            onRetry = {
                                viewModel.reset()
                                if (state.mode == ImportMode.FILE) filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                                else launchImagePicker()
                            },
                            onBack = onNavigateBack
                        )
                        ImportStage.PREVIEW -> {
                            if (state.mode == ImportMode.FILE) {
                                FilePreviewContent(
                                    state = state,
                                    viewModel = viewModel,
                                    confirmedReview = confirmedReview,
                                    filter = fileFilter,
                                    onFilter = { fileFilter = it },
                                    onConfirmReview = { confirmedReview = confirmedReview + it }
                                )
                            } else {
                                OcrPreviewContent(state, viewModel, context, onPickAnother = { launchImagePicker() })
                            }
                        }
                    }
                }
            }
        }
    }

    // 加密 zip：页面级密码弹窗（内容区留空，由弹窗承载交互）
    if (state.stage == ImportStage.PASSWORD) {
        ZipPasswordDialog(
            wrong = state.zipPasswordWrong,
            onSubmit = viewModel::submitZipPassword,
            onCancel = viewModel::cancelWork
        )
    }
}

/**
 * ZipCrypto 加密 zip 的解压密码弹窗：输入密码后交给 [BillImportViewModel.submitZipPassword]。
 * 密码错误时内联提示（不关闭弹窗），让用户原地重试。
 */
@Composable
private fun ZipPasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.bill_import_zip_password_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                NoDialogWindowAnimation()
                Text(
                    stringResource(R.string.bill_import_zip_password_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    isError = wrong,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (wrong) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.bill_import_zip_password_wrong),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** 导入方式选择首页：英雄卡 + 双入口卡（可横向滑动聚焦）+ 三步流程 + 教程引导 */
@Composable
private fun ModeSelection(onSelectFile: () -> Unit, onSelectOcr: () -> Unit) {
    var showTutorial by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ImportHeroCard()
        Spacer(modifier = Modifier.height(24.dp))
        ImportEntrySwitcher(onSelectFile = onSelectFile, onSelectOcr = onSelectOcr)
        Spacer(modifier = Modifier.height(16.dp))
        ImportStepsCard()
        Spacer(modifier = Modifier.height(16.dp))
        ImportHelpCard(onTutorial = { showTutorial = true })
        Spacer(modifier = Modifier.height(24.dp))
    }
    if (showTutorial) {
        ImportTutorialSheet(onDismiss = { showTutorial = false })
    }
}

/** 滑动切换的判定速度（px/s）：超过它直接翻到另一张，不看拖了多远 */
private const val ENTRY_SWIPE_VELOCITY = 700f

/** 焦点弹簧：拖动手势与点击选中共用，保证两处动画观感一致 */
private val entryFocusSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 900f)

/** 两张入口卡各向中间平移的距离：各 10dp 而原间距 12dp → 净重叠约 8dp */
private val ENTRY_CARD_OVERLAP = 10.dp

/** 未选中卡的底色：表面色里掺入这个比例的主色，让它与选中卡同属一个色系而不是灰扑扑的 */
private const val ENTRY_IDLE_TINT = 0.10f

/** 卡内水印图标尺寸（dp）：超出右下角被卡片裁切，形成"图标从卡里长出来"的出血观感 */
private val ENTRY_WATERMARK_SIZE = 104.dp

/** 卡后叠层的偏移量（近层 / 远层）：同时向下、向卡片外侧偏移，避开两卡中间的重叠区 */
private val ENTRY_STACK_NEAR = 4.dp
private val ENTRY_STACK_FAR = 9.dp

/**
 * 入口叠层卡圆角 = **24dp**：这是**有意例外**，不跟全局 `Shapes.large`(16dp)。
 * 叠层必须与卡片本体同值才像同一叠纸（卡体见 `EntryCard` 的 `shapes.extraLarge`）；
 * 改主题形状或此处时，`ENTRY_CARD_CORNER` 与 `EntryCard` 的 clip 必须同步。
 */
private val ENTRY_CARD_CORNER = 24.dp

/**
 * 入口卡的**高度下限** = 200dp。
 *
 * 卡片高度本来是"内容撑多高就多高"，但删掉卡内徽章后内容只剩 ~164dp，而卡宽约 158dp ——
 * 接近正方形，配上 24dp 大圆角就显矮胖、跟同屏其他卡片不成比例。这个下限把卡组拉回
 * 竖长比例（158 : 200 ≈ 4 : 5），内容是居中的，多出来的量上下均分。
 *
 * 用 `heightIn(min =)` 而不是固定 `height =`：大字号无障碍设置下内容变高时仍能正常撑开。
 */
private val ENTRY_CARD_MIN_HEIGHT = 200.dp

/**
 * 卡后叠层：在卡片外侧与下方再露两层同形薄片，让它看起来是"一叠卡"而不是一块色板。
 * [dirX] = ±1 决定往哪一侧错——两张卡各朝自己那侧，中间的重叠区就不会被叠层糊到。
 *
 * 必须挂在 `clip` **之前**：那时还没进入裁剪层，绘制不会被卡片圆角切掉，
 * 溢出卡片外的部分自然被卡片本体盖住，只在侧下方留下两道月牙。
 */
private fun Modifier.entryStackLayers(
    base: Color,
    backdrop: Color,
    dirX: Float
): Modifier = drawBehind {
    // DrawScope 本身即 Density，圆角在这里换算成像素
    val corner = CornerRadius(ENTRY_CARD_CORNER.toPx())
    val near = ENTRY_STACK_NEAR.toPx()
    val far = ENTRY_STACK_FAR.toPx()
    // 先画远层，近层压在上面
    drawRoundRect(
        color = lerp(base, backdrop, 0.62f),
        topLeft = Offset(dirX * far, far),
        size = size,
        cornerRadius = corner
    )
    drawRoundRect(
        color = lerp(base, backdrop, 0.36f),
        topLeft = Offset(dirX * near, near),
        size = size,
        cornerRadius = corner
    )
}

/**
 * 双入口卡：保持左右并排的错落倾斜布局。**横向滑动与点击都能切换选中**（哪张放大、
 * 浮到上层、占满主色），**只有已选中的那张能被点击进入**。
 *
 * 进入逻辑分两段，保证点任何地方都有反馈、又不会"手滑点到旁边那张就直接跳走"：
 * 点未选中的卡 → 只把它切为选中；点已选中的卡 → 进入对应流程。
 *
 * [focus] ∈ [-1f, 1f]：-1 文件导入卡选中（默认）/ 1 图片识别卡选中，中间值为拖动过程。
 */
@Composable
private fun ImportEntrySwitcher(onSelectFile: () -> Unit, onSelectOcr: () -> Unit) {
    var focus by remember { mutableFloatStateOf(-1f) }
    val scope = rememberCoroutineScope()
    val dragStep = with(LocalDensity.current) { 60.dp.toPx() }
    val pick: (Float) -> Unit = { target ->
        scope.launch { animate(focus, target, animationSpec = entryFocusSpring) { value, _ -> focus = value } }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        EntryCardRow(
            focus = focus,
            onFileClick = { if (focus < 0f) onSelectFile() else pick(-1f) },
            onOcrClick = { if (focus > 0f) onSelectOcr() else pick(1f) },
            modifier = Modifier.entryFocusDrag(focus = { focus }, setFocus = { focus = it }, dragStep = dragStep)
        )
        EntryGuide(focus = focus)
    }
}

/**
 * 两张入口卡的并排布局：焦点度由 [focus] 派生（-1 文件卡 / 1 图片卡）。
 * 两卡各向中间平移 `ENTRY_CARD_OVERLAP` 形成轻微压叠，焦点那张用 zIndex 提到上层，
 * 因为左右位移量相同，卡组整体仍是居中对称的。
 */
@Composable
private fun EntryCardRow(
    focus: Float,
    onFileClick: () -> Unit,
    onOcrClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileEmphasis = (-focus).coerceIn(0f, 1f)
    val ocrEmphasis = focus.coerceIn(0f, 1f)
    val overlapPx = with(LocalDensity.current) { ENTRY_CARD_OVERLAP.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 高度下限写在 `height(IntrinsicSize.Max)` 之前（外侧），这样两卡最终的公共高度
            // = max(内容高者, 下限)，两张卡与叠层薄片始终等高。
            .heightIn(min = ENTRY_CARD_MIN_HEIGHT)
            // 两张卡按内容里更高的那张等高（`IntrinsicSize.Max` 会把可分配宽度
            // 传给 weighted 子项做 intrinsic 计算，行数不同的文案也能量准），
            // 短的那张内部垂直居中，视觉上两卡一样高。
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntryCard(
            title = stringResource(R.string.bill_import_file),
            desc = stringResource(R.string.bill_import_file_desc_short),
            icon = Icons.Outlined.FileUpload,
            emphasize = fileEmphasis,
            rotationDeg = -2f,
            stackDirX = -1f,
            onClick = onFileClick,
            modifier = Modifier
                .weight(1f)
                .zIndex(fileEmphasis)
                .graphicsLayer {
                    val s = 1f + 0.06f * fileEmphasis - 0.12f * ocrEmphasis
                    scaleX = s
                    scaleY = s
                    translationX = overlapPx
                }
        )
        EntryCard(
            title = stringResource(R.string.bill_import_image),
            desc = stringResource(R.string.bill_import_ocr_desc_short),
            icon = Icons.Outlined.CameraAlt,
            emphasize = ocrEmphasis,
            rotationDeg = 2f,
            stackDirX = 1f,
            onClick = onOcrClick,
            modifier = Modifier
                .weight(1f)
                .zIndex(ocrEmphasis)
                .graphicsLayer {
                    val s = 1f + 0.06f * ocrEmphasis - 0.12f * fileEmphasis
                    scaleX = s
                    scaleY = s
                    translationX = -overlapPx
                    // 错落下移必须走绘制层：之前这里是 padding(top = 16dp - 8dp * focus)，
                    // 属布局属性，focus 一动就把 Row 的高度改掉，整页内容跟着上下跳。
                    translationY = (16.dp - 8.dp * ocrEmphasis).toPx()
                }
        )
    }
}

/**
 * 切换引导：卡组下方的 `● ●` 指示点，标出"共两张、当前选的是哪张"。
 * 由 `GuideDot` 的宽度动画承担状态表达，**纯展示、不可点击**；不再配常驻文案。
 */
@Composable
private fun EntryGuide(focus: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 32dp = 16dp 视觉间距 + 16dp：错落卡下移的量走绘制层（不占布局），
            // 这份留白补在指示点上方，避免下移的卡片底部压到指示点。
            .padding(top = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GuideDot(active = focus < 0f)
        Spacer(modifier = Modifier.width(4.dp))
        GuideDot(active = focus > 0f)
    }
}

/** 指示点：选中态拉长为胶囊（只作指示，不响应点击） */
@Composable
private fun GuideDot(active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val width by animateDpAsState(if (active) 20.dp else 7.dp, label = "guideDotWidth")
    Box(
        modifier = Modifier
            .width(width)
            .height(7.dp)
            .clip(CircleShape)
            .background(if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.35f))
    )
}

/**
 * 横向拖动手势：拖动跟手，松手按速度优先、位移兜底吸附到 -1 / 1。
 * 用 `draggable` 而不是 `detectHorizontalDragGestures`，因为只有前者会回传松手速度——
 * 轻扫（位移很小）也能切换，否则会被"位移不够"判回原位、看起来像没有滑动效果。
 */
@Composable
private fun Modifier.entryFocusDrag(
    focus: () -> Float,
    setFocus: (Float) -> Unit,
    dragStep: Float
): Modifier {
    val state = rememberDraggableState { delta ->
        setFocus((focus() - delta / dragStep).coerceIn(-1f, 1f))
    }
    return this.draggable(
        state = state,
        orientation = Orientation.Horizontal,
        onDragStopped = { velocity ->
            val target = when {
                velocity > ENTRY_SWIPE_VELOCITY -> -1f
                velocity < -ENTRY_SWIPE_VELOCITY -> 1f
                focus() <= 0f -> -1f
                else -> 1f
            }
            animate(focus(), target, animationSpec = entryFocusSpring) { value, _ ->
                setFocus(value)
            }
        }
    )
}

/**
 * 入口卡：底色 = 主色染色程度的连续插值（未选中也带一层浅主色，两张卡始终同色系），
 * 再叠一道**同形状的对角渐变**避免纯色块发闷；右下角放一枚**出血裁切的大号图标**当水印，
 * 卡片因此有了"图标从卡里长出来"的辨识度；卡片外侧与下方另有**两层同形薄片**（见
 * [entryStackLayers]），整张卡看起来是一叠纸片而不是一块色板。
 * [emphasize] 0 = 未选中（浅色底 + 主色图标），1 = 焦点（主色填充 + 白字）。
 */
@Composable
private fun EntryCard(
    title: String,
    desc: String,
    icon: ImageVector,
    emphasize: Float,
    rotationDeg: Float,
    stackDirX: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val container = lerp(lerp(scheme.surfaceVariant, accent, ENTRY_IDLE_TINT), accent, emphasize)
    val onContainer = lerp(scheme.onSurfaceVariant, scheme.onPrimary, emphasize)
    val iconBg = lerp(accent.copy(alpha = 0.12f), scheme.onPrimary.copy(alpha = 0.18f), emphasize)
    val iconTint = lerp(accent, scheme.onPrimary, emphasize)
    // 渐变两端都由 theme 颜色插值得到，深浅主题都不会串色
    val sheen = lerp(container, scheme.onPrimary, 0.12f)

    Box(
        modifier = modifier
            // 撑满 Row 给到的高度（该高度已由 IntrinsicSize.Max 取两卡最大值），
            // 内容再整体居中 —— 否则短卡内容贴顶、看起来比另一张矮一截。
            .fillMaxHeight()
            .graphicsLayer { rotationZ = rotationDeg }
            .entryStackLayers(base = container, backdrop = scheme.background, dirX = stackDirX)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(container, sheen)))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        EntryCardWatermark(icon = icon, tint = onContainer.copy(alpha = 0.12f))
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = onContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.82f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 卡内水印：右下角一枚出血被裁的大号图标。
 * 用 `matchParentSize` 占位——它不参与父 Box 的尺寸测量，所以图标再大也不会把卡片撑开。
 */
@Composable
private fun BoxScope.EntryCardWatermark(icon: ImageVector, tint: Color) {
    Box(modifier = Modifier.matchParentSize()) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 26.dp, y = 26.dp)
                .size(ENTRY_WATERMARK_SIZE)
        )
    }
}

/** 顶部英雄卡：主题色底 + 标题/副标题 + 简易账单插画 */
@Composable
private fun ImportHeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.bill_import_hero_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.bill_import_hero_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            BillIllustration()
        }
    }
}

/** 简易插画：斜置的账单纸片 + 金额角标 */
@Composable
private fun BillIllustration() {
    Box(
        modifier = Modifier.size(width = 72.dp, height = 84.dp),
        contentAlignment = Alignment.Center
    ) {
        // 底层卡片（背景衬纸）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 8.dp, y = 6.dp)
                .size(width = 56.dp, height = 72.dp)
                .graphicsLayer { rotationZ = 10f }
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
        )
        // 上层账单纸片
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { rotationZ = -6f }
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.width(34.dp).height(6.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
            Box(modifier = Modifier.width(26.dp).height(4.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)))
            Box(modifier = Modifier.width(30.dp).height(4.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)))
            Box(modifier = Modifier.width(20.dp).height(4.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)))
        }
        // 金额角标
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            Text("¥", color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** 三步流程卡：编号节点 + 连接线 + 步骤说明 */
@Composable
private fun ImportStepsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            ImportStepNode(1, stringResource(R.string.bill_import_step_1), Modifier.weight(1.1f))
            StepConnector(Modifier.weight(0.5f).padding(top = 12.dp))
            ImportStepNode(2, stringResource(R.string.bill_import_step_2), Modifier.weight(1.2f))
            StepConnector(Modifier.weight(0.5f).padding(top = 12.dp))
            ImportStepNode(3, stringResource(R.string.bill_import_step_3), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ImportStepNode(number: Int, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun StepConnector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    )
}

/** 教程引导卡：警示黄底 + 「查看教程」按钮 */
@Composable
private fun ImportHelpCard(onTutorial: () -> Unit) {
    // 底色/描边同源取色：色调 15% 铺底，35% 收 1dp 暖色描边（与步骤卡的描边语言一致）
    val warning = MaterialTheme.colorScheme.warning()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = warning.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, warning.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.bill_import_help_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.bill_import_help_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onTutorial,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = warning,
                    contentColor = ImportHelpOnTint
                )
            ) {
                Text(stringResource(R.string.bill_import_help_tutorial))
            }
        }
    }
}

/**
 * 导出官方账单的教程：底部面板（与编辑账单面板同一套外壳与外观参数）。
 *
 * 内容按**平台分卡 + 编号步骤**组织。原先两条 60/53 字的「→ 长路径」折行后就断了步骤层次，
 * 用户得自己数箭头才知道走到哪一步；拆成编号步骤后每一步都是一个可对照的动作，
 * 关键的那一步（用途选「用于个人对账」）还能单独着色。结尾用 [TutorialNote] 补上
 * 全篇最实用的一条：邮件里发来的是**压缩包**，App 能直接读，不必先手动解压。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportTutorialSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        SheetHeader(title = stringResource(R.string.bill_import_tutorial_title), onDismiss = onDismiss)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.bill_import_tutorial_lead),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            TutorialChannelCard(
                title = stringResource(R.string.bill_import_wechat_bill),
                accent = ImportChannelWechat,
                highlightIndex = 2,
                steps = listOf(
                    stringResource(R.string.bill_import_tutorial_wechat_step1),
                    stringResource(R.string.bill_import_tutorial_wechat_step2),
                    stringResource(R.string.bill_import_tutorial_use_reconcile),
                    stringResource(R.string.bill_import_tutorial_send)
                )
            )
            TutorialChannelCard(
                title = stringResource(R.string.bill_import_alipay_bill),
                accent = ImportChannelAlipay,
                highlightIndex = 2,
                steps = listOf(
                    stringResource(R.string.bill_import_tutorial_alipay_step1),
                    stringResource(R.string.bill_import_tutorial_alipay_step2),
                    stringResource(R.string.bill_import_tutorial_use_reconcile),
                    stringResource(R.string.bill_import_tutorial_send)
                )
            )
            TutorialNote(stringResource(R.string.bill_import_tutorial_note))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onDismiss,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .height(48.dp)
        ) {
            Text(stringResource(R.string.bill_import_tutorial_ack), fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 教程里的单个平台卡：品牌色圆点 + 平台名 + 编号步骤列表。
 * 复用 [ImportCardSurface]（16dp 圆角 + 1dp `outlineVariant` 描边）与三屏其他卡片同参。
 *
 * [highlightIndex] 指向「用途选『用于个人对账』」那一步：选错只能拿到 PDF，App 读不了，
 * 所以它是整篇教程里**唯一一步着色强调**的。
 */
@Composable
private fun TutorialChannelCard(
    title: String,
    accent: Color,
    steps: List<String>,
    highlightIndex: Int = -1
) {
    ImportCardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        steps.forEachIndexed { index, step ->
            TutorialStepRow(
                number = index + 1,
                text = step,
                accent = accent,
                highlight = index == highlightIndex
            )
            if (index != steps.lastIndex) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/**
 * 教程步骤行：品牌色编号圆芯片 + 文字。
 * 编号芯片用**固定品牌色 + 固定白字**——底色不随主题变，前景也不能跟着变
 * （同 `ImportHelpOnTint` 的道理）。
 */
@Composable
private fun TutorialStepRow(
    number: Int,
    text: String,
    accent: Color,
    highlight: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 教程底部提示条：左侧 3dp 主色竖条 + 说明。
 * 承载全篇最实用的一条信息 —— 邮件里发来的是**压缩包**（微信/支付宝都不发裸 CSV），
 * 而 App 原生支持加密 zip 直读，不必先手动解压。用 `IntrinsicSize.Min` 让竖条贴合文字高度。
 */
@Composable
private fun TutorialNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LoadingContent(text: String, onCancel: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text)
            if (onCancel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/** 结果页：导入 N / 跳过 M / 失败 K 三档统计 + 失败导出 + 撤销本次导入 */
@Composable
private fun DoneContent(
    count: Int,
    skipped: Int,
    failedCount: Int,
    canUndo: Boolean,
    message: String?,
    onUndo: () -> Unit,
    onExportFailures: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        DoneSuccessHeader(count = count, skipped = skipped, failed = failedCount)
        Spacer(modifier = Modifier.height(16.dp))
        DoneStatRow(count = count, skipped = skipped, failed = failedCount)
        if (failedCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onExportFailures,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, ExpenseRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed)
            ) {
                Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bill_import_export_failures, failedCount))
            }
        }
        if (message != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        DoneActionArea(canUndo = canUndo, onUndo = onUndo, onDone = onBack, onContinue = onContinue)
    }
}

/** 结果页顶部成功标识：主色 12% 底圆 + 主色对勾 + 标题 + 「本次共处理 N 条」 */
@Composable
private fun DoneSuccessHeader(count: Int, skipped: Int, failed: Int) {
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.bill_import_complete), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.bill_import_result_total_subtitle, count + skipped + failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 结果页底部动作区：撤销（可选）+ 完成并排一行（各占半宽）；继续导入独立居中下置 */
@Composable
private fun DoneActionArea(canUndo: Boolean, onUndo: () -> Unit, onDone: () -> Unit, onContinue: () -> Unit) {
    if (canUndo) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f).height(48.dp), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.bill_import_undo))
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f).height(48.dp), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.done))
            }
        }
    } else {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.done))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onContinue) { Text(stringResource(R.string.bill_import_continue)) }
    }
}

/** 结果页三档统计卡：标签在上、大数字在下；导入（主色底）/ 跳过（中性底）/ 失败（有失败时红底） */
@Composable
private fun DoneStatRow(count: Int, skipped: Int, failed: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DoneStatCard(
            label = stringResource(R.string.bill_import_result_imported_label),
            value = count,
            valueColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.weight(1f)
        )
        DoneStatCard(
            label = stringResource(R.string.bill_import_result_skipped_label),
            value = skipped,
            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f)
        )
        DoneStatCard(
            label = stringResource(R.string.bill_import_result_failed_label),
            value = failed,
            valueColor = if (failed > 0) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = if (failed > 0) ExpenseRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DoneStatCard(label: String, value: Int, valueColor: Color, containerColor: Color, modifier: Modifier = Modifier) {
    ImportCardSurface(
        modifier = modifier,
        color = containerColor,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun ErrorContent(error: String, diagnostic: String = "", onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())) {
            Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = ExpenseRed)
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            if (diagnostic.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(diagnostic, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.bill_import_reselect)) }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}

/**
 * 文件导入结果页。顶部三态筛选条 + 主区按档渲染 + 固定底部区。
 *
 * 信息结构（自上而下）：
 *  (a) 三态筛选条「待复核 | 全部 N | 已跳过 M」：默认停在「待复核」；筛选只影响展示，不影响勾选/导入；
 *  (b) 「待复核」档：头区（待确认条数 + 原因摘要）+ 待复核卡（商户 / 金额 + 橙色虚线 + 理由行），
 *      点金额·商户就地改、点卡体标记已确认；无待复核项时一行空态文案（明细只在「全部」档显示）；
 *  (c) 「全部」/「已跳过」档：分别渲染全量明细 / 未勾选明细；「已跳过」为空时一行空态文案；
 *  (d) 「存入 账本 / 账户 · 更改」行：点开 [ImportSettingSheet]；
 *  (e) 底部主按钮「确认 n 笔，导入全部 N 笔」。
 *
 * 本页**不渲染缩略图/占位块**：文件导入的数据是 ParsedBill（CSV/Excel 行，无源图无 cropBox），
 * 不确定性是**字段级**（商户/分类），画假缩略图会被读成「图片坏了」。
 *
 * 业务不改：导入 / 余额 / 撤销 / 去重 / 跳过计数全部沿用 ViewModel 既有实现。
 */
@Composable
private fun FilePreviewContent(
    state: BillImportState,
    viewModel: BillImportViewModel,
    confirmedReview: Set<Int>,
    filter: ReviewFilter,
    onFilter: (ReviewFilter) -> Unit,
    onConfirmReview: (Int) -> Unit
) {
    val context = LocalContext.current
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    // 「分类未识别」卡片点开分类选择面板的目标行（null = 面板关闭）
    var categoryEditIndex by remember { mutableStateOf<Int?>(null) }

    // 待复核判定（文件页金额/日期恒有值，实测只会命中「商户待确认 / 分类未识别」）
    val reviewReasons = remember(state.parsed) {
        state.parsed.map { reviewReasonOf(it.amount, it.date, it.merchant, it.category, it.categoryResolved) }
    }
    // 复核卡 = 已勾选（将导入） 且 有待复核理由 且 尚未被「点卡片」标记确认
    val pendingIndices = remember(state.parsed, reviewReasons, confirmedReview, state.selectedIndices) {
        state.parsed.indices.filter { it in state.selectedIndices && reviewReasons[it] != null && it !in confirmedReview }
    }
    // 「已跳过」= 用户取消勾选、本次不导入的行（派生量，随勾选实时变，务必不要缓存）
    val skippedIndices = state.parsed.indices.filter { it !in state.selectedIndices }

    Column(modifier = Modifier.fillMaxSize()) {
        ReviewFilterRow(
            filter = filter,
            reviewCount = pendingIndices.size,
            onFilter = onFilter,
            totalCount = state.parsed.size,
            skippedCount = skippedIndices.size
        )
        FileFilteredBody(
            filter = filter,
            state = state,
            viewModel = viewModel,
            pendingIndices = pendingIndices,
            skippedIndices = skippedIndices,
            reviewReasons = reviewReasons,
            onConfirmReview = onConfirmReview,
            onEditCategory = { categoryEditIndex = it },
            onEditBill = { editingIndex = it }
        )
        ImportTargetRow(state, context, onClick = { showSettings = true })
        FileImportBottomBar(
            pendingCount = pendingIndices.size,
            total = state.parsed.size,
            selectedCount = state.selectedIndices.size,
            onImport = { viewModel.importSelected() }
        )
    }
    if (showSettings) {
        ImportSettingSheet(state, viewModel, context, onDismiss = { showSettings = false })
    }
    FileEditSheetHost(editingIndex, state, viewModel, onDismiss = { editingIndex = null })
    ReviewCategorySheetHost(categoryEditIndex, state, viewModel, onDismiss = { categoryEditIndex = null })
}

/**
 * 文件页主区按筛选档渲染（ColumnScope：由内部分支各自吃掉 weight(1f)，保证每档恰好一个 weight，避免冲突）：
 * - REVIEW：分拣台（头区 + 待复核卡）；无待复核时一行空态文案（不退回全量明细）。
 * - ALL：全量明细列表。
 * - SKIPPED：未勾选明细；为空时一行空态文案 + 撑底 Spacer。
 */
@Composable
private fun ColumnScope.FileFilteredBody(
    filter: ReviewFilter,
    state: BillImportState,
    viewModel: BillImportViewModel,
    pendingIndices: List<Int>,
    skippedIndices: List<Int>,
    reviewReasons: List<ReviewReason?>,
    onConfirmReview: (Int) -> Unit,
    onEditCategory: (Int) -> Unit,
    onEditBill: (Int) -> Unit
) {
    when (filter) {
        ReviewFilter.REVIEW -> {
            // 分拣台只在「待复核」档出现（ALL / SKIPPED 下不显示，避免与筛选条计数重复）
            FileSortingHeader(
                pendingCount = pendingIndices.size,
                reviewCount = reviewReasons.count { it != null },
                total = state.parsed.size,
                reason = dominantReason(pendingIndices.map { reviewReasons[it] })
            )
            if (pendingIndices.isNotEmpty()) {
                FileReviewList(
                    indices = pendingIndices,
                    state = state,
                    onSave = { i, b -> viewModel.updateParsedBill(i, b) },
                    onConfirm = onConfirmReview,
                    onEditCategory = onEditCategory,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = stringResource(R.string.bill_import_no_review),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        ReviewFilter.ALL -> FileBillList(
            remember(state.parsed) { state.parsed.indices.toList() }, state, viewModel, onEdit = onEditBill, modifier = Modifier.weight(1f)
        )
        ReviewFilter.SKIPPED -> if (skippedIndices.isEmpty()) {
            Text(
                text = stringResource(R.string.bill_import_no_skipped),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            SkippedExcludedNote(skippedIndices.count { !state.parsed[it].defaultSelected })
            FileBillList(skippedIndices, state, viewModel, onEdit = onEditBill, modifier = Modifier.weight(1f))
        }
    }
}

/** 「已跳过」档说明条：提示其中有多少条是默认不导入的「不计收支」行（为 0 时不渲染） */
@Composable
private fun SkippedExcludedNote(excludedCount: Int) {
    if (excludedCount <= 0) return
    Text(
        text = stringResource(R.string.bill_import_skipped_excluded_note, excludedCount),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 待复核行里出现最多的那个原因（头区副标题的「原因」摘要）；无待复核行时返回 null */
private fun dominantReason(reasons: List<ReviewReason?>): ReviewReason? =
    reasons.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** 就地编辑目标字段 */
private enum class ReviewEditField { AMOUNT, MERCHANT }

/** 金额分 → 编辑用字符串（两位小数，不带 ¥ / 千分位，便于直接改写） */
private fun formatCentsForEdit(cents: Long): String =
    String.format(Locale.US, "%.2f", cents / 100.0)

/** 分拣台头区：待确认条数主标题（15sp/Medium）+ 原因摘要副标题（11sp 次要色） */
@Composable
private fun FileSortingHeader(pendingCount: Int, reviewCount: Int, total: Int, reason: ReviewReason?) {
    val reasonText = reason?.let { reviewReasonLabel(it) } ?: ""
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (pendingCount > 0) stringResource(R.string.bill_import_review_headline, pendingCount)
            else stringResource(R.string.bill_import_review_all_ready, total),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (pendingCount > 0 && reasonText.isNotEmpty()) {
            Text(
                text = stringResource(R.string.bill_import_review_subtitle, reasonText, (total - reviewCount).coerceAtLeast(0)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 待复核卡可视上限：CSV 常有几十条「分类=其他」，默认封顶避免长列表淹没头区与底部主按钮 */
private const val MAX_REVIEW_CARDS = 5

/**
 * 待复核卡列表：16dp 页边 + 12dp 卡距，weight(1f) 吃掉剩余高度。
 * 默认只渲染前 [MAX_REVIEW_CARDS] 张，其余收敛为一行「还有 n 条待确认 · 查看全部」；
 * 点该行 → 本地 [expandedPending] 置 true，页内展开全部待确认卡（不新增路由）。
 */
@Composable
private fun FileReviewList(
    indices: List<Int>,
    state: BillImportState,
    onSave: (Int, ParsedBill) -> Unit,
    onConfirm: (Int) -> Unit,
    onEditCategory: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedPending by remember { mutableStateOf(false) }
    val visibleIndices = if (expandedPending) indices else indices.take(MAX_REVIEW_CARDS)
    val hiddenCount = indices.size - visibleIndices.size
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(visibleIndices, key = { it }) { index ->
            val bill = state.parsed[index]
            ReviewSortingCard(
                bill = bill,
                reason = reviewReasonOf(bill.amount, bill.date, bill.merchant, bill.category, bill.categoryResolved),
                onConfirm = { onConfirm(index) },
                onSave = { onSave(index, it) },
                onEditCategory = { onEditCategory(index) }
            )
        }
        if (hiddenCount > 0) {
            item(key = "review_pending_more") {
                ReviewMoreRow(hiddenCount = hiddenCount, onClick = { expandedPending = true })
            }
        }
    }
}

/** 待复核上限提示行：11sp 蓝字 + 右箭头，点此页内展开全部待确认卡 */
@Composable
private fun ReviewMoreRow(hiddenCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bill_import_review_more, hiddenCount),
            fontSize = 11.sp,
            color = InfoBlue,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = InfoBlue,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 单张待复核卡：白底 + 16dp 圆角 + 1dp 待处理暖色描边；点卡体 = 标记已确认并移出复核区。
 *
 * 主操作按 [reason] 分流（金额/商户两种原因下均可改，只是「先看哪个字段」不同）：
 *  - [ReviewReason.UNKNOWN_CATEGORY]：补一个「分类：其他 ▸」入口，点开复用分类选择面板；
 *  - [ReviewReason.BLANK_MERCHANT]：商户名就地改（点商户名），维持原状。
 *
 * [evidence] 为可选证据槽（缩略图等）：文件导入的数据是 ParsedBill（CSV/Excel 行，**无源图**），
 * 其不确定性是**字段级**（商户/分类）而非「图片能否看清」，故本批传 null、**不渲染任何占位块**。
 * 若强行画一个假缩略图，用户会读成「图片裂了」，把「分类未识别」误判为「UI 坏了」，反而误导。
 * OCR 页将来需要缩略图时，可复用同一槽位传入 cropBox 裁剪的 bitmap。
 */
@Composable
private fun ReviewSortingCard(
    bill: ParsedBill,
    reason: ReviewReason?,
    onConfirm: () -> Unit,
    onSave: (ParsedBill) -> Unit,
    onEditCategory: () -> Unit,
    evidence: (@Composable () -> Unit)? = null
) {
    var editing by remember(bill) { mutableStateOf<ReviewEditField?>(null) }
    var amountDraft by remember(bill) { mutableStateOf(formatCentsForEdit(bill.amount)) }
    var merchantDraft by remember(bill) { mutableStateOf(bill.merchant) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onConfirm),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, ImportPendingStroke)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            if (evidence != null) {
                evidence()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                ReviewMerchantBlock(
                    merchant = bill.merchant,
                    editing = editing == ReviewEditField.MERCHANT,
                    draft = merchantDraft,
                    onDraft = { merchantDraft = it },
                    onStartEdit = { editing = ReviewEditField.MERCHANT },
                    onCommit = { editing = null; onSave(bill.copy(merchant = merchantDraft.trim())) },
                    onCancel = { merchantDraft = bill.merchant; editing = null }
                )
                Spacer(modifier = Modifier.height(6.dp))
                ReviewAmountBlock(
                    amountCents = bill.amount,
                    isIncome = bill.type == BillType.INCOME.value,
                    editing = editing == ReviewEditField.AMOUNT,
                    draft = amountDraft,
                    onDraft = { amountDraft = it },
                    onStartEdit = { editing = ReviewEditField.AMOUNT },
                    onCommit = {
                        Money.parse(amountDraft)?.let { onSave(bill.copy(amount = it.cents)) }
                        editing = null
                    },
                    onCancel = { amountDraft = formatCentsForEdit(bill.amount); editing = null }
                )
                if (reason == ReviewReason.UNKNOWN_CATEGORY) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ReviewCategoryEntry(category = bill.category, onClick = onEditCategory)
                }
                if (reason != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ReviewReasonLine(reason)
                }
            }
        }
    }
}

/**
 * 分类入口行（「分类：其他 ▸」）：仅「分类未识别」时出现。自带 clickable，点击被本行消费，
 * **不会冒泡**到卡片的 onConfirm（即不会顺手把卡片标记已确认），与商户/金额块的就地编辑一致。
 */
@Composable
private fun ReviewCategoryEntry(category: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bill_import_category_entry, category),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 分类选择面板宿主：把 null 判空与「选中即落回」收敛在一处，避免主内容 composable 过长 */
@Composable
private fun ReviewCategorySheetHost(
    index: Int?,
    state: BillImportState,
    viewModel: BillImportViewModel,
    onDismiss: () -> Unit
) {
    if (index == null) return
    val bill = state.parsed.getOrNull(index) ?: return
    ReviewCategorySheet(
        bill = bill,
        viewModel = viewModel,
        onDismiss = onDismiss,
        onSelected = { newCategory ->
            viewModel.updateParsedBill(index, bill.copy(category = newCategory))
            onDismiss()
        }
    )
}

/**
 * 待复核卡的分类选择面板：与 OCR 逐笔编辑面板（ImportEditSheet → OcrCategorySection）**同源**——
 * 同一个 ModalBottomSheet 外壳（BottomSheetShape + DragHandle）+ 同一个 [CategoryPicker] 组件 +
 * 同一个 [rememberImportCategories] 数据构建（预设/自定义/频次排序/名称解析全复用），不新造选择器。
 * 选中即经 [onSelected] 落回（无需「保存」二次确认）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCategorySheet(
    bill: ParsedBill,
    viewModel: BillImportViewModel,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val presetOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    val isIncome = bill.type == BillType.INCOME.value
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.bill_category),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
            }
        }
        val categories = rememberImportCategories(viewModel, !isIncome, ensureCategory = bill.category)
        CategoryPicker(
            selected = bill.category,
            onSelected = onSelected,
            categories = categories,
            getDisplayName = { key ->
                resolvePresetCategoryName(presetOverrides, key, if (isIncome) "INCOME" else "EXPENSE", context)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 商户块：未编辑为文本（可点就地改），编辑中为下划线输入 + 确定/取消 */
@Composable
private fun ReviewMerchantBlock(
    merchant: String,
    editing: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit
) {
    if (editing) {
        UnderlineField(value = draft, onValueChange = onDraft, placeholder = stringResource(R.string.bill_import_merchant))
        ReviewEditActions(onCommit = onCommit, onCancel = onCancel)
    } else {
        Text(
            text = merchant.ifBlank { stringResource(R.string.bill_import_unrecognized) },
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable { onStartEdit() }
        )
    }
}

/** 金额块：未编辑为等宽金额（红/绿）+ 1dp 橙色虚线（表达可点改），编辑中为下划线金额输入 */
@Composable
private fun ReviewAmountBlock(
    amountCents: Long,
    isIncome: Boolean,
    editing: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit
) {
    if (editing) {
        UnderlineAmountField(value = draft, onValueChange = onDraft)
        ReviewEditActions(onCommit = onCommit, onCancel = onCancel)
    } else {
        val context = LocalContext.current
        Text(
            text = CurrencyUtils.formatCurrency(context, amountCents.toMoney()),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (isIncome) IncomeGreen else ExpenseRed,
            modifier = Modifier.clickable { onStartEdit() }
        )
        DashedUnderline(color = AccentOrange, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 就地编辑的 确定 / 取消 小操作行 */
@Composable
private fun ReviewEditActions(onCommit: () -> Unit, onCancel: () -> Unit) {
    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCommit, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(R.string.confirm), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(
                stringResource(R.string.cancel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 1dp 橙色虚线：表达「此处可点改」，非装饰性分割线（3 实 3 空） */
@Composable
private fun DashedUnderline(color: Color, modifier: Modifier = Modifier) {
    val dash = with(LocalDensity.current) { IMPORT_DASH_DP.dp.toPx() }
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f)
        )
    }
}

/** 复核理由行（11sp 次要色）：按字段给出「点哪里可改」的提示 */
@Composable
private fun ReviewReasonLine(reason: ReviewReason) {
    Text(reviewHintLabel(reason), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun reviewHintLabel(reason: ReviewReason): String = stringResource(
    when (reason) {
        ReviewReason.MISSING_AMOUNT -> R.string.bill_import_review_hint_amount
        ReviewReason.MISSING_DATE -> R.string.bill_import_review_hint_date
        ReviewReason.BLANK_MERCHANT -> R.string.bill_import_review_hint_merchant
        ReviewReason.UNKNOWN_CATEGORY -> R.string.bill_import_review_hint_category
    }
)

/** 底部主按钮上方一行：存入 {账本} / {账户} · 更改（点开设置面板） */
@Composable
private fun ImportTargetRow(state: BillImportState, context: android.content.Context, onClick: () -> Unit) {
    val book = state.accountBooks.firstOrNull { it.id == state.importBookId }?.getDisplayName(context).orEmpty()
    val wallet = state.wallets.firstOrNull { it.id == state.importWalletId }
        ?.let { com.palmnote.ui.components.getLocalizedWalletDisplayName(it, context) }.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.bill_import_deposit_to, book, wallet),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.bill_import_change),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = AccentOrange,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 底部主按钮：确认 n 笔、导入 N 笔（n==0 时退化为「导入 N 笔」） */
@Composable
private fun FileImportBottomBar(pendingCount: Int, total: Int, selectedCount: Int, onImport: () -> Unit) {
    ImportBottomBar {
        Button(
            onClick = onImport,
            modifier = Modifier.weight(1f).height(52.dp),
            enabled = selectedCount > 0,
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.SupportAgent, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    pendingCount <= 0 -> stringResource(R.string.bill_import_import_total, selectedCount)
                    selectedCount == total -> stringResource(R.string.bill_import_confirm_import_all, pendingCount, total)
                    else -> stringResource(R.string.bill_import_confirm_import_some, pendingCount, selectedCount)
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 文件导入明细列表：weight(1f) 吃掉剩余全部高度（页边 16 / 行距 8） */
@Composable
private fun FileBillList(
    indices: List<Int>,
    state: BillImportState,
    viewModel: BillImportViewModel,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(indices, key = { it }) { index ->
            val bill = state.parsed[index]
            BillPreviewRow(
                selected = index in state.selectedIndices,
                type = BillType.from(bill.type),
                amountCents = bill.amount,
                merchant = bill.merchant,
                category = bill.category,
                dateMillis = bill.date,
                note = bill.note,
                onToggle = { viewModel.toggleFileSelection(index) },
                onEdit = { onEdit(index) }
            )
        }
    }
}

/** 逐笔编辑面板宿主：把 null 判空与保存回调收敛在一处，避免主内容 composable 过长 */
@Composable
private fun FileEditSheetHost(index: Int?, state: BillImportState, viewModel: BillImportViewModel, onDismiss: () -> Unit) {
    if (index == null) return
    val bill = state.parsed.getOrNull(index) ?: return
    FileEditSheet(
        bill = bill,
        viewModel = viewModel,
        onDismiss = onDismiss,
        onSave = { updated ->
            viewModel.updateParsedBill(index, updated)
            onDismiss()
        }
    )
}

/** 汇总卡右上两个次操作按钮（全不选 / 重新选择）——与 App 按钮语言一致（shapes.medium） */
@Composable
private fun ImportSecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** 统计条：一张白卡三列紧凑并排——已选 N/M · 支出合计 · 收入合计；金额仅用红/绿文字着色（不铺浅色底块） */
@Composable
private fun ImportStatBar(selectedCount: Int, totalCount: Int, expenseCents: Long, incomeCents: Long) {
    val context = LocalContext.current
    ImportCardSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatCell(
                label = stringResource(R.string.bill_import_selected),
                value = "$selectedCount/$totalCount",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = stringResource(R.string.bill_import_expense_sum),
                value = CurrencyUtils.formatCurrency(context, expenseCents.toMoney()),
                valueColor = ExpenseRed,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = stringResource(R.string.bill_import_income_sum),
                value = CurrencyUtils.formatCurrency(context, incomeCents.toMoney()),
                valueColor = IncomeGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 统计条单列：上小标签（labelMedium）下金额（titleSmall 加粗 + 语义色） */
@Composable
private fun StatCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

/** 设置分组：小标签（labelMedium）在上 + 内容在下 */
@Composable
private fun ImportSettingGroup(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

/** 选择方块：72×56 + shapes.medium；选中填自身色 + 白图标，未选 surfaceVariant 底 + 自身色图标（对齐记账页账本/账户） */
@Composable
private fun SelectionSquare(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    val base = Modifier
        .size(width = 72.dp, height = 56.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant)
    val withClick = if (onClick != null) {
        base.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { onClick() }
    } else {
        base
    }
    Box(modifier = withClick, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) Color.White else selectedColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/** 横向方块选择行 */
@Composable
private fun <T> SelectionSquareRow(
    options: List<T>,
    selectedId: Long?,
    idOf: (T) -> Long,
    labelOf: (T) -> String,
    colorOf: (T) -> Color,
    iconOf: (T) -> ImageVector,
    onSelect: (Long) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { idOf(it) }) { item ->
            SelectionSquare(
                label = labelOf(item),
                selected = idOf(item) == selectedId,
                selectedColor = colorOf(item),
                icon = iconOf(item),
                onClick = { onSelect(idOf(item)) }
            )
        }
    }
}

/** 导入来源徽章：文案 + 品牌色（仅 FILE 模式且格式已识别时非空） */
private data class ImportSourceBadge(val text: String, val color: Color)

/** 当前来源徽章；FILE 且格式已知才返回，否则 null（OCR 模式没有「来源」概念） */
@Composable
private fun rememberImportSourceBadge(state: BillImportState): ImportSourceBadge? {
    if (state.mode != ImportMode.FILE || state.format == BillCsvImporter.CsvFormat.UNKNOWN) return null
    return when (state.format) {
        BillCsvImporter.CsvFormat.WECHAT -> ImportSourceBadge(stringResource(R.string.bill_import_wechat_bill), ImportChannelWechat)
        BillCsvImporter.CsvFormat.ALIPAY -> ImportSourceBadge(stringResource(R.string.bill_import_alipay_bill), ImportChannelAlipay)
        else -> ImportSourceBadge(stringResource(R.string.bill_import_source_file), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 当前去向摘要：FILE 拼「来源 · 账本 · 账户」，OCR 拼「账本 · 账户」；空段自动跳过 */
@Composable
private fun rememberImportTargetLabel(state: BillImportState, context: android.content.Context): String {
    val badge = rememberImportSourceBadge(state)
    val book = state.accountBooks.firstOrNull { it.id == state.importBookId }?.getDisplayName(context)
    val wallet = state.wallets.firstOrNull { it.id == state.importWalletId }
        ?.let { com.palmnote.ui.components.getLocalizedWalletDisplayName(it, context) }
    return remember(badge, book, wallet) {
        listOfNotNull(badge?.text, book, wallet).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

/** 收起后的「当前去向」摘要行（高 44dp）：左摘要文本，右「更改」；整行可点，点开 [ImportSettingSheet] */
@Composable
private fun ImportSettingSummary(
    state: BillImportState,
    context: android.content.Context,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp)
) {
    val label = rememberImportTargetLabel(state, context)
    ImportCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.bill_import_change),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 记账去向设置面板：把「来源（仅 FILE 且格式已识别）/ 账本 / 账户」三组图标方块选择器
 * 自页面主体收进底部面板，把纵向空间还给主任务。复用 [ImportSettingGroup] / [SelectionSquareRow] / [SelectionSquare]。
 * 底部「完成」关闭面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSettingSheet(
    state: BillImportState,
    viewModel: BillImportViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        ImportSettingGroups(state, viewModel, context)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
        ) {
            Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 设置面板内的三组选择器：来源（可选）→ 账本 → 账户，均为记账页同款图标方块 */
@Composable
private fun ImportSettingGroups(state: BillImportState, viewModel: BillImportViewModel, context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rememberImportSourceBadge(state)?.let { badge ->
            ImportSettingGroup(stringResource(R.string.bill_import_preview_source)) {
                SelectionSquare(
                    label = badge.text,
                    selected = true,
                    selectedColor = badge.color,
                    icon = Icons.Outlined.Description
                )
            }
        }
        ImportSettingGroup(stringResource(R.string.bill_book)) {
            SelectionSquareRow(
                options = state.accountBooks,
                selectedId = state.importBookId,
                idOf = { it.id },
                labelOf = { it.getDisplayName(context) },
                colorOf = { it.color.toComposeColor() },
                iconOf = { it.icon.imageVector },
                onSelect = { viewModel.updateImportBook(it) }
            )
        }
        ImportSettingGroup(stringResource(R.string.bill_wallet)) {
            SelectionSquareRow(
                options = state.wallets,
                selectedId = state.importWalletId,
                idOf = { it.id },
                labelOf = { com.palmnote.ui.components.getLocalizedWalletDisplayName(it, context) },
                colorOf = { it.color.toComposeColor() },
                iconOf = { it.icon.imageVector },
                onSelect = { viewModel.updateImportWallet(it) }
            )
        }
    }
}

/**
 * 预览页筛选：左侧分段控件（待复核 N / 全部 [N] / [已跳过 N]）+ 可选右侧「全不选」次按钮
 * （筛选只影响展示，不隐藏勾选）。
 * - [totalCount] 非空 → 「全部」段带总数（文件页）；为空 → 纯「全部」（OCR 页，外观不变）。
 * - [skippedCount] 非空 → 追加第三段「已跳过」（文件页）；为空 → 不渲染（OCR 页仍两段）。
 * - [onDeselectAll] 为空 → 不渲染右侧按钮，左侧分段容器铺满整行（文件页；用户截图中无「全不选」）。
 */
@Composable
private fun ReviewFilterRow(
    filter: ReviewFilter,
    reviewCount: Int,
    onFilter: (ReviewFilter) -> Unit,
    totalCount: Int? = null,
    skippedCount: Int? = null,
    onDeselectAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .then(if (onDeselectAll != null) Modifier.weight(1f) else Modifier.fillMaxWidth())
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SegmentedTab(
                text = stringResource(R.string.bill_import_review_only, reviewCount),
                selected = filter == ReviewFilter.REVIEW,
                onClick = { onFilter(ReviewFilter.REVIEW) },
                modifier = Modifier.weight(1f)
            )
            SegmentedTab(
                text = if (totalCount != null) stringResource(R.string.bill_import_filter_all_count, totalCount)
                else stringResource(R.string.bill_import_review_all),
                selected = filter == ReviewFilter.ALL,
                onClick = { onFilter(ReviewFilter.ALL) },
                modifier = Modifier.weight(1f)
            )
            if (skippedCount != null) {
                SegmentedTab(
                    text = stringResource(R.string.bill_import_filter_skipped, skippedCount),
                    selected = filter == ReviewFilter.SKIPPED,
                    onClick = { onFilter(ReviewFilter.SKIPPED) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (onDeselectAll != null) {
            Spacer(modifier = Modifier.width(8.dp))
            ImportSecondaryButton(text = stringResource(R.string.bill_import_select_none), onClick = onDeselectAll)
        }
    }
}

/** 分段控件单段：选中 = surface 底 + 加粗 + onSurface；未选 = 透明底 + onSurfaceVariant */
@Composable
private fun SegmentedTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 待复核理由 → 颜色：严重（缺金额/缺日期）红，一般（商户待确认/分类未识别）琥珀 */
private fun reviewReasonColor(reason: ReviewReason): Color = if (reason.severe) ExpenseRed else Warning

/** 字段置信度小圆点（8dp 实心圆）：绿=可靠识别，琥珀=规则推断，红=必填缺失 */
@Composable
private fun ConfidenceDot(confidence: FieldConfidence) {
    val color = when (confidence) {
        FieldConfidence.HIGH -> IncomeGreen
        FieldConfidence.INFERRED -> Warning
        FieldConfidence.MISSING -> ExpenseRed
    }
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** 字段置信度图例：3 行圆点 + 文案（说明分级含义，不承诺阻断行为） */
@Composable
private fun ConfidenceLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.bill_import_confidence_legend_title),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LegendLine(FieldConfidence.HIGH, stringResource(R.string.bill_import_confidence_high))
        LegendLine(FieldConfidence.INFERRED, stringResource(R.string.bill_import_confidence_inferred))
        LegendLine(FieldConfidence.MISSING, stringResource(R.string.bill_import_confidence_missing))
    }
}

/** 置信度图例折叠区：默认收起只留一行开关；展开才渲染 [ConfidenceLegend]，避免常驻占屏 */
@Composable
private fun ConfidenceLegendSection() {
    var legendOpen by remember { mutableStateOf(false) }
    TextButton(
        onClick = { legendOpen = !legendOpen },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            stringResource(R.string.bill_import_confidence_legend_title),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (legendOpen) 90f else 0f }
        )
    }
    if (legendOpen) ConfidenceLegend()
}

@Composable
private fun LegendLine(confidence: FieldConfidence, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConfidenceDot(confidence)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun reviewReasonLabel(reason: ReviewReason): String = stringResource(
    when (reason) {
        ReviewReason.MISSING_AMOUNT -> R.string.bill_import_review_missing_amount
        ReviewReason.MISSING_DATE -> R.string.bill_import_review_missing_date
        ReviewReason.BLANK_MERCHANT -> R.string.bill_import_review_blank_merchant
        ReviewReason.UNKNOWN_CATEGORY -> R.string.bill_import_review_unknown_category
    }
)

/**
 * 单条票据预览行：文件导入 / OCR 多笔识别 共用同一布局
 *  (◉) 商户名                        ¥金额
 *      09月09日 · 分类
 *  交互：点圆形勾选标记 = 切换选中（金额缺失时不可选）；点卡片其它任意位置 = 打开逐笔编辑。
 *  金额缺失（OCR 未识别到）时金额显示 "--" 且不可勾选；商户缺失显示「未识别」+ 警示图标。
 */
@Composable
private fun BillPreviewRow(
    selected: Boolean,
    type: BillType,
    amountCents: Long?,
    merchant: String,
    category: String,
    dateMillis: Long?,
    note: String,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    isDuplicate: Boolean = false
) {
    val context = LocalContext.current
    val amountOk = amountCents != null && amountCents > 0
    val merchantMissing = merchant.isBlank()
    val effectiveSelected = selected && amountOk
    // 待复核标记由已有字段派生（不新增数据列）：缺金额/缺日期=红，商户待确认/分类未识别=琥珀
    val reviewReason = reviewReasonOf(amountCents, dateMillis, merchant, category)
    // 参考图：列表始终是纯白卡，选中态只用圆形勾表达（不加描边、不染底）
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 圆形勾选标记（视觉 24dp 保持不变；触摸热区放大到 40dp，便于点按）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = amountOk, role = Role.Checkbox, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (effectiveSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (effectiveSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (effectiveSelected) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            merchantMissing && category.isNotBlank() && category != "其他" -> category
                            merchantMissing -> stringResource(R.string.bill_import_unrecognized)
                            else -> merchant
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (merchantMissing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (merchantMissing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = stringResource(R.string.bill_import_unrecognized),
                            modifier = Modifier.size(14.dp),
                            tint = ExpenseRed
                        )
                    }
                    if (isDuplicate) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            shape = CircleShape
                        ) {
                            Text(
                                stringResource(R.string.bill_import_duplicate_existing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = dateMillis?.let { DateUtils.formatDisplayDate(context, it) }
                            ?: stringResource(R.string.bill_import_unrecognized),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dateMillis == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (category.isNotBlank() && category != "其他") {
                        Text(" · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                // 待复核提示行：位于「日期 · 分类」下方
                if (reviewReason != null) {
                    Text(
                        text = stringResource(R.string.bill_import_review_line, reviewReasonLabel(reviewReason)),
                        style = MaterialTheme.typography.labelSmall,
                        color = reviewReasonColor(reviewReason),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // 过滤 "/"、"-" 等无意义占位备注（微信账单常见）
                if (!note.isBlank() && note != "/" && note != "-") {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            // 参考图：金额不带 ± 号，仅用红/绿表达收支
            Text(
                text = if (amountOk) CurrencyUtils.formatCurrency(context, amountCents!!.toMoney()) else "--",
                fontWeight = FontWeight.Bold,
                color = when {
                    !amountOk -> MaterialTheme.colorScheme.error
                    type == BillType.INCOME -> IncomeGreen
                    else -> ExpenseRed
                },
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

/** 底部面板通用头部：标题 + 关闭按钮（编辑面板与教程面板共用，保证两个面板头部完全一致） */
@Composable
private fun SheetHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
        }
    }
}

/**
 * 底部编辑面板外壳：ModalBottomSheet（顶部 24dp 圆角）+ 标题 + 可滚动内容 + 贴底操作栏。
 * 取代原居中 AlertDialog，观感与其他记账 App 的底部编辑面板一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportEditSheet(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        SheetHeader(title = title, onDismiss = onDismiss)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onDismiss, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = onSave, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(2f).height(48.dp)) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 金额 Hero：收支分段控件 + 大号金额下划线输入（2dp 下划线，作为编辑面板视觉焦点）。
 */
@Composable
private fun AmountHero(
    amount: String,
    isIncome: Boolean,
    error: Boolean,
    onAmount: (String) -> Unit,
    onType: (BillType) -> Unit,
    confidence: FieldConfidence? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OcrTypeToggle(selected = if (isIncome) BillType.INCOME else BillType.EXPENSE, onSelect = onType)
        Spacer(modifier = Modifier.height(16.dp))
        FieldBlock(stringResource(R.string.bill_import_amount), confidence) {
            UnderlineAmountField(value = amount, onValueChange = onAmount, error = error)
        }
    }
}

/**
 * 编辑表单的字段块：小标题（含可选置信度圆点）+ 常显下划线输入。
 * 输入控件自身不带标签，避免与块标题重复。
 */
@Composable
private fun FieldBlock(
    label: String,
    confidence: FieldConfidence? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (confidence != null) {
                ConfidenceDot(confidence)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

/** 文件导入编辑面板的初始表单：各字段直接取自解析结果 */
@Composable
private fun rememberFileEditForm(bill: ParsedBill): MutableState<OcrEditForm> = remember(bill) {
    mutableStateOf(
        OcrEditForm(
            amount = String.format(Locale.US, "%.2f", bill.amount / 100.0),
            isIncome = bill.type == BillType.INCOME.value,
            merchant = bill.merchant,
            category = bill.category,
            note = sanitizeNotePlaceholder(bill.note),
            dateStr = DateUtils.formatDate(bill.date)
        )
    )
}

/** 文件导入预览的逐笔编辑面板：金额/类型/商户/分类/日期/备注（表单与 OCR 编辑共用） */
@Composable
private fun FileEditSheet(bill: ParsedBill, viewModel: BillImportViewModel, onDismiss: () -> Unit, onSave: (ParsedBill) -> Unit) {
    var form by rememberFileEditForm(bill)
    var dateError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    ImportEditSheet(
        title = stringResource(R.string.bill_import_edit_bill),
        onDismiss = onDismiss,
        onSave = {
            val cents = Money.parse(form.amount)?.cents
            if (cents == null || cents <= 0) {
                amountError = true
                return@ImportEditSheet
            }
            val dateMillis = if (form.dateStr.isBlank()) bill.date else runCatching {
                LocalDate.parse(form.dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse {
                dateError = true
                return@ImportEditSheet
            }
            onSave(
                bill.copy(
                    amount = cents,
                    type = if (form.isIncome) BillType.INCOME.value else BillType.EXPENSE.value,
                    merchant = form.merchant.trim(),
                    category = form.category.ifBlank { "其他" },
                    note = form.note.trim(),
                    date = dateMillis
                )
            )
        }
    ) {
        OcrEditFields(
            form = form,
            onForm = { form = it; amountError = false; dateError = false },
            dateError = dateError,
            viewModel = viewModel,
            amountError = amountError
        )
    }
}

@Composable
private fun OcrPreviewContent(state: BillImportState, viewModel: BillImportViewModel, context: android.content.Context, onPickAnother: () -> Unit) {
    val isMulti = state.ocrResults.size > 1
    var showZoom by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (isMulti) {
            OcrMultiReview(
                state,
                viewModel,
                context,
                onOpenSettings = { showSettings = true },
                onEdit = { editingIndex = it },
                onZoom = { showZoom = true }
            )
        } else {
            // weight(1f)：把自滚动表单压在底部按钮行之内，避免按钮被挤出屏幕
            OcrSingleEditor(
                state = state,
                viewModel = viewModel,
                context = context,
                onOpenSettings = { showSettings = true },
                onZoom = { showZoom = true },
                modifier = Modifier.weight(1f)
            )
        }
        ImportBottomBar {
            if (!isMulti) {
                OutlinedButton(
                    onClick = onPickAnother,
                    modifier = Modifier.width(96.dp).height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) { Text(stringResource(R.string.bill_import_reselect)) }
            }
            Button(
                onClick = { viewModel.saveOcrSelected() },
                modifier = Modifier.weight(1f).height(52.dp),
                enabled = state.ocrSelectedIndices.isNotEmpty(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    if (isMulti) stringResource(R.string.bill_import_batch_save, state.ocrSelectedIndices.size)
                    else stringResource(R.string.bill_import_save),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    if (showSettings) {
        ImportSettingSheet(state, viewModel, context, onDismiss = { showSettings = false })
    }
    if (showZoom && state.ocrImageUri != null) {
        ZoomableImageDialog(uri = state.ocrImageUri, onDismiss = { showZoom = false })
    }
    OcrEditSheetHost(editingIndex, state, viewModel, onDismiss = { editingIndex = null })
}

/** OCR 多笔校对：原图入口窄行 → 去向摘要 → 统计条 → 筛选 → 明细列表（weight(1f) 吃掉剩余高度） */
@Composable
private fun OcrMultiReview(
    state: BillImportState,
    viewModel: BillImportViewModel,
    context: android.content.Context,
    onOpenSettings: () -> Unit,
    onEdit: (Int) -> Unit,
    onZoom: () -> Unit
) {
    val expenseSum = remember(state.ocrResults, state.ocrSelectedIndices) {
        state.ocrResults.filterIndexed { i, _ -> i in state.ocrSelectedIndices }
            .filter { it.type != BillType.INCOME }.sumOf { it.amount ?: 0L }
    }
    val incomeSum = remember(state.ocrResults, state.ocrSelectedIndices) {
        state.ocrResults.filterIndexed { i, _ -> i in state.ocrSelectedIndices }
            .filter { it.type == BillType.INCOME }.sumOf { it.amount ?: 0L }
    }
    // 待复核筛选（多笔页）：默认「全部」；筛选只影响展示，待复核行仍可勾选
    var filter by remember { mutableStateOf(ReviewFilter.ALL) }
    val reviewReasons = remember(state.ocrResults) {
        state.ocrResults.map { reviewReasonOf(it.amount, it.date, it.merchant, it.category) }
    }
    val visibleIndices = remember(state.ocrResults, filter, reviewReasons) {
        state.ocrResults.indices.filter { index ->
            when (filter) {
                ReviewFilter.REVIEW -> reviewReasons[index] != null
                // ALL 与（OCR 页不会出现的）SKIPPED 均展示全部，保证与历史行为一致
                ReviewFilter.ALL, ReviewFilter.SKIPPED -> true
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))
        // 多笔页与单笔共用同一原图 URI / ZoomableImageDialog，仅此前未接 onZoom；
        // 多笔不写 ocrDate，中间文案改用「截图」避免误显示「未识别」
        OcrThumbnailRow(
            state = state,
            onZoom = onZoom,
            subtitle = stringResource(R.string.bill_import_screenshot),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        ImportSettingSummary(state, context, onClick = onOpenSettings)
        Spacer(modifier = Modifier.height(ImportCardGap))
        ImportStatBar(state.ocrSelectedIndices.size, state.ocrResults.size, expenseSum, incomeSum)
        ReviewFilterRow(
            filter = filter,
            reviewCount = reviewReasons.count { it != null },
            onFilter = { filter = it },
            onDeselectAll = { viewModel.deselectAllOcr() }
        )
        OcrBillList(visibleIndices, state, viewModel, onEdit = onEdit, modifier = Modifier.weight(1f))
    }
}

/** 多笔识别明细列表：weight(1f) 吃掉剩余全部高度（页边 16 / 行距 8） */
@Composable
private fun OcrBillList(
    indices: List<Int>,
    state: BillImportState,
    viewModel: BillImportViewModel,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(indices, key = { it }) { index ->
            val result = state.ocrResults[index]
            BillPreviewRow(
                selected = index in state.ocrSelectedIndices,
                type = result.type ?: BillType.EXPENSE,
                amountCents = result.amount,
                merchant = result.merchant,
                category = result.category,
                dateMillis = result.date,
                note = result.note,
                onToggle = { viewModel.toggleOcrSelection(index) },
                onEdit = { onEdit(index) }
            )
        }
    }
}

/** 多笔逐笔编辑面板宿主：收敛 null 判空与保存回调 */
@Composable
private fun OcrEditSheetHost(index: Int?, state: BillImportState, viewModel: BillImportViewModel, onDismiss: () -> Unit) {
    if (index == null) return
    val result = state.ocrResults.getOrNull(index) ?: return
    OcrEditSheet(
        result = result,
        viewModel = viewModel,
        onDismiss = onDismiss,
        onSave = { updated ->
            viewModel.updateOcrResult(index, updated)
            onDismiss()
        }
    )
}

/** 识别结果的图片入口窄行（高 44dp）：28dp 缩略图 + [subtitle]（缺省用 ocrDate） + 右侧「查看原图 ›」；整行可点放大（单笔/多笔共用） */
@Composable
private fun OcrThumbnailRow(
    state: BillImportState,
    onZoom: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    if (state.ocrImageUri == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onZoom),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = state.ocrImageUri,
            contentDescription = stringResource(R.string.bill_import_screenshot),
            modifier = Modifier.size(28.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            subtitle ?: state.ocrDate.ifBlank { stringResource(R.string.bill_import_unrecognized) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.bill_import_view_original),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 单笔识别：缩略图窄行 → 金额 → 识别详情(商户/日期/备注) → 分类网格 → 去向摘要 → 识别详情文字（全部随内容滚动） */
@Composable
private fun OcrSingleEditor(
    state: BillImportState,
    viewModel: BillImportViewModel,
    context: android.content.Context,
    onOpenSettings: () -> Unit,
    onZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        OcrThumbnailRow(state = state, onZoom = onZoom)
        OcrAmountCard(state, viewModel)
        OcrDetailCard(state, viewModel)
        OcrCategoryCard(state, viewModel)
        ImportSettingSummary(state, context, onClick = onOpenSettings, modifier = Modifier)
        // 字段置信度图例：默认折叠，点标题展开（信息性说明，不阻断任何操作）
        ConfidenceLegendSection()
        // 识别详情（原始文字）：随内容滚动，不再固定占一行
        OcrRawTextSection(state)
        if (state.error != null) {
            Text(state.error, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** 「查看识别详情」折叠区 + 原始识别文字 */
@Composable
private fun OcrRawTextSection(state: BillImportState) {
    var showRaw by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { showRaw = !showRaw },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Text(
                stringResource(R.string.bill_import_ocr_details),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (showRaw && state.ocrRawText.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    state.ocrRawText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 收支分段 + 大金额下划线输入卡；金额字段前置置信度圆点 */
@Composable
private fun OcrAmountCard(state: BillImportState, viewModel: BillImportViewModel) {
    ImportCardSurface {
        OcrTypeToggle(selected = state.ocrType, onSelect = viewModel::updateOcrType)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfidenceDot(state.ocrAmountConfidence)
            Spacer(modifier = Modifier.width(8.dp))
            UnderlineAmountField(
                value = state.ocrAmount,
                onValueChange = viewModel::updateOcrAmount,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 识别详情卡：商户 / 日期 / 备注 三个带标签的下划线字段（顺序 商户 → 日期 → 备注） */
@Composable
private fun OcrDetailCard(state: BillImportState, viewModel: BillImportViewModel) {
    ImportCardSurface {
        FieldBlock(stringResource(R.string.bill_import_merchant), state.ocrMerchantConfidence) {
            UnderlineField(value = state.ocrMerchant, onValueChange = viewModel::updateOcrMerchant)
        }
        Spacer(modifier = Modifier.height(12.dp))
        FieldBlock(stringResource(R.string.bill_import_date), state.ocrDateConfidence) {
            UnderlineDateField(value = state.ocrDate, onValueChange = viewModel::updateOcrDate)
        }
        Spacer(modifier = Modifier.height(12.dp))
        FieldBlock(stringResource(R.string.bill_import_note)) {
            UnderlineField(value = state.ocrNote, onValueChange = viewModel::updateOcrNote)
        }
    }
}

/** 收支切换：与记账页（AddBillScreen）同款两段分段控件——支出选中填 ExpenseRed、收入选中填 StatusActive */
@Composable
private fun OcrTypeToggle(selected: BillType, onSelect: (BillType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(BillType.EXPENSE to R.string.bill_expense, BillType.INCOME to R.string.bill_income).forEach { (t, labelRes) ->
            val isSelected = selected == t
            val segmentColor = if (t == BillType.INCOME) StatusActive else ExpenseRed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) segmentColor else Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSelect(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 与手动记账页（AddBillScreen）完全一致的分类列表构建：
 * 预设分类（按开关过滤 + 颜色/名称覆盖解析）+ 用户自定义分类，按使用频次降序排列；
 * ensureCategory 保证当前已选/识别出的分类不会因不在列表里而丢失。
 */
@Composable
private fun rememberImportCategories(
    viewModel: BillImportViewModel,
    isExpense: Boolean,
    ensureCategory: String? = null
): List<CategoryItem> {
    val presetOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    val customExpense by viewModel.customExpenseCategories.collectAsStateWithLifecycle()
    val customIncome by viewModel.customIncomeCategories.collectAsStateWithLifecycle()
    val usageCounts by viewModel.categoryUsageCounts.collectAsStateWithLifecycle()
    val fallbackColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(isExpense, presetOverrides, customExpense, customIncome, usageCounts, ensureCategory, fallbackColor) {
        val rawPresets = if (isExpense) expenseCategoryItems else incomeCategoryItems
        val prefix = if (isExpense) "EXPENSE_" else "INCOME_"
        val filteredPresets = rawPresets.filter { item ->
            val key = "preset_$prefix${item.name}"
            val json = presetOverrides[key]
            if (json != null) {
                try {
                    org.json.JSONObject(json).optBoolean("enabled", true)
                } catch (_: Exception) { true }
            } else true
        }.map { item ->
            val resolved = com.palmnote.ui.theme.ColorResolver.resolve(item.name, item.color)
            if (resolved != item.color) item.copy(color = resolved) else item
        }
        val base = filteredPresets + if (isExpense) customExpense else customIncome
        val sorted = base.sortedByDescending { usageCounts[it.name] ?: 0 }
        if (!ensureCategory.isNullOrBlank() && sorted.none { it.name == ensureCategory }) {
            sorted + CategoryItem(ensureCategory, Icons.Outlined.Edit, fallbackColor)
        } else sorted
    }
}

/** 分类卡：图标网格，图标与排列和记账页（AddBillScreen）完全一致（默认 2 行 × 5 列 + 更多） */
@Composable
private fun OcrCategoryCard(state: BillImportState, viewModel: BillImportViewModel) {
    val context = LocalContext.current
    val presetOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    ImportCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfidenceDot(state.ocrCategoryConfidence)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.bill_category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        val categories = rememberImportCategories(viewModel, state.ocrType != BillType.INCOME, ensureCategory = state.ocrCategory)
        CategoryPicker(
            selected = state.ocrCategory,
            onSelected = viewModel::updateOcrCategory,
            categories = categories,
            getDisplayName = { key -> resolvePresetCategoryName(presetOverrides, key, state.ocrType.value, context) }
        )
    }
}

/**
 * 下划线式单行输入（用户认可的 Batch 9/10 形态）：底部一条细线，无外框；
 * 整行高 ≥40dp 保证触摸目标，placeholder 居左、trailing 居右。
 */
@Composable
private fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isBlank() && placeholder.isNotBlank()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 下划线日期输入：可手输 / 点尾部日历图标弹 M3 DatePickerDialog；
 * 值模型沿用 `yyyy-MM-dd` 字符串（与保存路径的 DateTimeFormatter.ofPattern("yyyy-MM-dd") 一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnderlineDateField(value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    UnderlineField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "yyyy-MM-dd",
        trailing = {
            IconButton(onClick = { showPicker = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.bill_import_date))
            }
        }
    )
    if (showPicker) {
        val initialMillis = runCatching {
            LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }.getOrElse { LocalDate.now() }
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onValueChange(
                            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        )
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            NoDialogWindowAnimation()
            DatePicker(state = pickerState)
        }
    }
}

/**
 * 下划线金额输入（金额是视觉焦点，下划线有意用 2dp；其余字段仍 1dp）：
 * ¥ 前缀 + headlineSmall Bold；非法时下划线转 error 并显示红字校验。
 */
@Composable
private fun UnderlineAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "¥",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        inner()
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(
            thickness = 2.dp,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        if (error) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.bill_import_amount_invalid),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** 逐笔编辑对话框的表单值 */
private data class OcrEditForm(
    val amount: String,
    val isIncome: Boolean,
    val merchant: String,
    val category: String,
    val note: String,
    val dateStr: String
)

/** 微信/支付宝 CSV 的备注列常用 "/"、"-" 当占位符，编辑时显示为空，避免用户误以为是真实备注 */
private fun sanitizeNotePlaceholder(note: String): String =
    if (note.trim() == "/" || note.trim() == "-") "" else note

/** 多笔识别编辑面板的初始表单：各字段取自识别结果 */
@Composable
private fun rememberOcrEditForm(result: OcrBillResult): MutableState<OcrEditForm> = remember(result) {
    mutableStateOf(
        OcrEditForm(
            amount = result.amount?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: "",
            isIncome = result.type == BillType.INCOME,
            merchant = result.merchant,
            category = result.category,
            note = sanitizeNotePlaceholder(result.note),
            dateStr = result.date?.let { DateUtils.formatDate(it) } ?: ""
        )
    )
}

/** 多笔识别结果的逐笔编辑面板：金额/类型/商户/分类/日期/备注 */
@Composable
private fun OcrEditSheet(result: OcrBillResult, viewModel: BillImportViewModel, onDismiss: () -> Unit, onSave: (OcrBillResult) -> Unit) {
    var form by rememberOcrEditForm(result)
    var dateError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    ImportEditSheet(
        title = stringResource(R.string.bill_import_ocr_edit_title),
        onDismiss = onDismiss,
        onSave = {
            val cents = Money.parse(form.amount)?.cents
            if (cents == null || cents <= 0) {
                amountError = true
                return@ImportEditSheet
            }
            val dateMillis = if (form.dateStr.isBlank()) null else runCatching {
                LocalDate.parse(form.dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse {
                dateError = true
                return@ImportEditSheet
            }
            // 编辑面板为整笔确认：保存即视为用户已核对，各字段置信度置为 HIGH
            onSave(
                result.copy(
                    amount = cents,
                    type = if (form.isIncome) BillType.INCOME else BillType.EXPENSE,
                    merchant = form.merchant.trim(),
                    category = form.category.ifBlank { "其他" },
                    note = form.note.trim(),
                    date = dateMillis,
                    amountConfidence = FieldConfidence.HIGH,
                    merchantConfidence = FieldConfidence.HIGH,
                    dateConfidence = FieldConfidence.HIGH,
                    categoryConfidence = FieldConfidence.HIGH
                )
            )
        }
    ) {
        OcrEditFields(
            form = form,
            onForm = { form = it; amountError = false; dateError = false },
            dateError = dateError,
            viewModel = viewModel,
            amountError = amountError,
            amountConfidence = result.amountConfidence,
            merchantConfidence = result.merchantConfidence,
            dateConfidence = result.dateConfidence,
            categoryConfidence = result.categoryConfidence
        )
    }
}

/** 逐笔编辑表单区（金额 Hero + 商户/日期/备注常显字段块 + 分类用记账页同款图标网格） */
@Composable
private fun OcrEditFields(
    form: OcrEditForm,
    onForm: (OcrEditForm) -> Unit,
    dateError: Boolean,
    viewModel: BillImportViewModel,
    amountError: Boolean = false,
    amountConfidence: FieldConfidence? = null,
    merchantConfidence: FieldConfidence? = null,
    dateConfidence: FieldConfidence? = null,
    categoryConfidence: FieldConfidence? = null
) {
    // 金额 Hero：收支分段 + 大号金额下划线输入（视觉焦点）
    AmountHero(
        amount = form.amount,
        isIncome = form.isIncome,
        error = amountError,
        onAmount = { onForm(form.copy(amount = it)) },
        onType = { onForm(form.copy(isIncome = it == BillType.INCOME)) },
        confidence = amountConfidence
    )
    Spacer(modifier = Modifier.height(16.dp))
    // 识别详情：商户 / 日期 / 备注 三个带标签的下划线字段
    FieldBlock(stringResource(R.string.bill_import_merchant), merchantConfidence) {
        UnderlineField(value = form.merchant, onValueChange = { onForm(form.copy(merchant = it)) })
    }
    Spacer(modifier = Modifier.height(12.dp))
    FieldBlock(stringResource(R.string.bill_import_date), dateConfidence) {
        UnderlineDateField(value = form.dateStr, onValueChange = { onForm(form.copy(dateStr = it)) })
    }
    if (dateError) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("yyyy-MM-dd", color = ExpenseRed, style = MaterialTheme.typography.labelSmall)
    }
    Spacer(modifier = Modifier.height(12.dp))
    FieldBlock(stringResource(R.string.bill_import_note)) {
        UnderlineField(value = form.note, onValueChange = { onForm(form.copy(note = it)) })
    }
    Spacer(modifier = Modifier.height(16.dp))
    // 分类：与记账页一致的图标网格（2 行 × 5 列 + 更多）
    OcrCategorySection(form = form, onForm = onForm, viewModel = viewModel, confidence = categoryConfidence)
}

/** 编辑表单的分类区：与记账页一致的图标网格（2 行 × 5 列 + 更多） */
@Composable
private fun OcrCategorySection(
    form: OcrEditForm,
    onForm: (OcrEditForm) -> Unit,
    viewModel: BillImportViewModel,
    confidence: FieldConfidence?
) {
    val context = LocalContext.current
    val presetOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (confidence != null) {
            ConfidenceDot(confidence)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            stringResource(R.string.bill_category),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
    val categories = rememberImportCategories(viewModel, !form.isIncome, ensureCategory = form.category)
    CategoryPicker(
        selected = form.category,
        onSelected = { onForm(form.copy(category = it)) },
        categories = categories,
        getDisplayName = { key ->
            resolvePresetCategoryName(presetOverrides, key, if (form.isIncome) "INCOME" else "EXPENSE", context)
        }
    )
}

/** 全屏可缩放查看识别原图：双指缩放 + 拖动，点背景关闭 */
@Composable
private fun ZoomableImageDialog(uri: Uri, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        NoDialogWindowAnimation()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.bill_import_screenshot),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .clickable(enabled = false) { }
            )
            Text(
                "×%.1f".format(scale),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            )
        }
    }
}
