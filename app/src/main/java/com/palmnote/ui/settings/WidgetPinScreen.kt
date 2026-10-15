package com.palmnote.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.palmnote.app.R
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.widget.WidgetPin
import kotlinx.coroutines.launch

/** 预览区高度：所有页统一，横滑时页码点不会上下跳。 */
private val PREVIEW_STAGE_HEIGHT = 272.dp

/** 预览区留白：卡片边框到屏幕边 / 到预览图各留一点，预览图本身撑满剩下的宽度。 */
private val PAGE_H_PADDING = 24.dp
private val PREVIEW_STAGE_PADDING = 16.dp

/**
 * 内容高低于这个值就切「可滚版式」：横屏 / 分屏小窗 / 系统大字体下，两个 weight(1f)
 * 富余吸收会塌缩到 0 仍放不下 —— 权限条和页脚会被裁掉且无处可滚。
 */
private val MIN_BALANCED_HEIGHT = 640.dp

/** 可滚版式下预览舞台的高度占比：让标题与页码点尽量留在同屏，少滚一点。 */
private const val COMPACT_STAGE_RATIO = 0.45f

/**
 * 全目录统一的缩放基准：设计稿里最大的卡（4×3 = 340×268dp，见 design-v4.html 的 `.s43`）。
 * 所有卡片乘同一个系数 —— 1×1 就摆成小方块、4×3 摆成大块，相对大小等于桌面占格比例；
 * 旧实现是每张各自拉满预览区宽度，于是 1×1 和 4×3 一样宽、比例完全失真。
 */
private const val DESIGN_MAX_WIDTH = 340f
private const val DESIGN_MAX_HEIGHT = 268f

/** 组件没登记设计尺寸时的兜底：按 3×2 卡摆。 */
private val FALLBACK_SIZE = 245 to 148

/** 预览图的测试标签：单测靠它量 dp 尺寸，锁住「不随屏幕密度变」。 */
internal const val PREVIEW_IMAGE_TAG = "widget_preview_image"

/** 目录格子的测试标签：单测靠它定位首格、验证网格可滚。 */
internal const val CATALOG_CELL_TAG = "widget_catalog_cell"

/** 目录网格列数：两列总览，缩略图按设计尺寸等比缩，一眼扫完 22 个形态。 */
private const val CATALOG_COLUMNS = 2

/** 目录缩略图舞台高：4×3 大卡撑满此高，其余按同一系数缩小 —— 格间相对大小 = 桌面占格比。 */
private val CATALOG_THUMB_HEIGHT = 88.dp

/**
 * 添加页：**只在应用内一键添加**（`requestPinAppWidget`），不讲系统的手动添加路径。
 *
 * 两层版式：首屏是 2 列网格总览（22 个形态一屏扫完，名称 + 尺寸标 + 已添加提示），
 * 点格子进详情落桌页（一次一张大预览 + 底部唯一添加按钮）。轮播已退役 ——
 * 22 页横滑没人滑得动，网格两列六七行就见底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPinScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isMiui = remember { WidgetPin.isMiuiHome(context) }
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(WidgetPin.snapshot(context)) }
    // 落桌核对期（最多 5×1.5s）锁住按钮：不锁的话连点会连发多个系统确认框
    var verifying by remember { mutableStateOf(false) }
    // 两层导航：null = 目录网格（首屏），非空 = 该组件的详情落桌页
    var selected by remember { mutableStateOf<WidgetPin.Entry?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) snapshot = WidgetPin.snapshot(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 点了「添加」不立刻报结果：requestPinAppWidget 的返回值只代表「这个桌面支持应用内添加」，
    // 桌面缺少相关权限时会静默失败（请求被吞、桌面什么都没有），照着返回值报成功就是假消息。
    // 所以等几秒重新数一遍桌面实例，数到了才算成了，没数到就如实说没成功。
    // 核对期间按钮转「确认中…」禁用 —— 不然连点会连发多个系统确认框。
    fun addWidget(entry: WidgetPin.Entry, before: Int) {
        if (verifying) return
        if (!WidgetPin.requestPin(context, entry)) {
            toast(context, R.string.widget_pin_failed)
            return
        }
        verifying = true
        scope.launch {
            val added = WidgetPin.awaitPinned(context, entry, before)
            snapshot = WidgetPin.snapshot(context)
            verifying = false
            toast(context, if (added) R.string.widget_pin_added else R.string.widget_pin_not_added)
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_widget_title),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // 详情里先退回目录；目录里再退才是离开设置页
                            if (selected != null) selected = null else onNavigateBack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // 目录页没有「当前组件」，不摆添加按钮；进详情才出现
            selected?.let { entry ->
                BottomBar(
                    bound = snapshot.boundCount(entry),
                    pinSupported = snapshot.pinSupported,
                    verifying = verifying,
                    onAdd = { addWidget(entry, snapshot.boundCount(entry)) }
                )
            }
        }
    ) { innerPadding ->
        WidgetPinContent(
            modifier = Modifier.padding(innerPadding),
            selected = selected,
            snapshot = snapshot,
            isMiui = isMiui,
            onSelect = { selected = it }
        )
    }
}

/**
 * 页面主体两层：
 * - 目录（首屏）：2 列滚动网格总览全部形态 —— 缩略图按设计尺寸等比缩（4×3 大块、1×1 小方），
 *   网格自己滚，矮屏也不塌（不需要版式切换）；
 * - 详情（点选进入）：标题 → 大预览 → 页脚 → 前提提示，添加按钮在底部栏（[BottomBar]）。
 */
@Composable
private fun WidgetPinContent(
    modifier: Modifier,
    selected: WidgetPin.Entry?,
    snapshot: WidgetPin.Snapshot,
    isMiui: Boolean,
    onSelect: (WidgetPin.Entry) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val balanced = maxHeight >= MIN_BALANCED_HEIGHT
        val stageHeight =
            if (balanced) {
                PREVIEW_STAGE_HEIGHT
            } else {
                minOf(PREVIEW_STAGE_HEIGHT, maxHeight * COMPACT_STAGE_RATIO)
            }
        if (selected == null) {
            CatalogBody(snapshot = snapshot, isMiui = isMiui, onSelect = onSelect)
        } else {
            DetailBody(
                entry = selected,
                snapshot = snapshot,
                isMiui = isMiui,
                stageHeight = stageHeight,
                balanced = balanced
            )
        }
    }
}

/** 目录页：网格占满中间自己滚，前提提示固定在底部。 */
@Composable
private fun CatalogBody(
    snapshot: WidgetPin.Snapshot,
    isMiui: Boolean,
    onSelect: (WidgetPin.Entry) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            CatalogGrid(snapshot = snapshot, onSelect = onSelect)
        }
        PermissionStrip(
            pinSupported = snapshot.pinSupported,
            isMiui = isMiui,
            pinOk = snapshot.pinOk
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CatalogGrid(snapshot: WidgetPin.Snapshot, onSelect: (WidgetPin.Entry) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(CATALOG_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(WidgetPin.entries) { entry ->
            CatalogCell(
                entry = entry,
                bound = snapshot.boundCount(entry),
                onClick = { onSelect(entry) }
            )
        }
    }
}

/**
 * 目录格子：缩略图（沿用统一等比缩放，格间相对大小 = 桌面占格比）+ 名称 + 尺寸标 + 已添加。
 * 读屏把整格念成一条：名称、尺寸、已添加一起给，图与格子本身无从辨认。
 */
@Composable
private fun CatalogCell(entry: WidgetPin.Entry, bound: Int, onClick: () -> Unit) {
    val title = stringResource(entry.titleRes)
    val cells = WidgetPin.cellsOf(entry)
    val added = if (bound == 0) null else stringResource(R.string.widget_pin_count, bound)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(CATALOG_CELL_TAG)
            .semantics {
                contentDescription = listOfNotNull(title, cells, added).joinToString("，")
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CATALOG_THUMB_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                PreviewImage(
                    imageRes = entry.previewImageRes,
                    designSize = WidgetPin.designSizeOf(entry),
                    // 整格已有合成读屏描述，图不再单独念一遍
                    contentDescription = null
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    cells,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (added != null) {
                    Text(
                        " · $added",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 详情落桌页（目录点选进入）：标题 → 大预览 → 页脚 → 前提提示。
 * 两种版式按可用高切换：
 * - 常规竖屏（≥ [MIN_BALANCED_HEIGHT]）：富余高度上下平分、前提提示贴底；
 * - 矮一屏（横屏 / 分屏小窗 / 系统大字体）：整列可滚 + 预览舞台按比例收缩 ——
 *   weight 版式在矮屏会塌缩到 0 仍放不下，权限条和页脚会被裁掉且无处可滚。
 */
@Composable
private fun DetailBody(
    entry: WidgetPin.Entry,
    snapshot: WidgetPin.Snapshot,
    isMiui: Boolean,
    stageHeight: Dp,
    balanced: Boolean
) {
    if (balanced) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            CurrentTitle(entry = entry, snapshot = snapshot)
            Spacer(Modifier.height(14.dp))
            PreviewPage(entry = entry, stageHeight = stageHeight)
            Spacer(Modifier.height(12.dp))
            PageFooter()
            Spacer(Modifier.weight(1f))
            PermissionStrip(
                pinSupported = snapshot.pinSupported,
                isMiui = isMiui,
                pinOk = snapshot.pinOk
            )
            Spacer(Modifier.height(10.dp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            CurrentTitle(entry = entry, snapshot = snapshot)
            Spacer(Modifier.height(14.dp))
            PreviewPage(entry = entry, stageHeight = stageHeight)
            Spacer(Modifier.height(12.dp))
            PageFooter()
            PermissionStrip(
                pinSupported = snapshot.pinSupported,
                isMiui = isMiui,
                pinOk = snapshot.pinOk
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PageFooter() {
    Text(
        stringResource(R.string.widget_page_footer),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )
}

/**
 * 当前组件的名称 + 说明 + 尺寸/已添加：进详情时交叉淡入。
 */
@Composable
private fun CurrentTitle(entry: WidgetPin.Entry, snapshot: WidgetPin.Snapshot) {
    AnimatedContent(
        targetState = entry,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        label = "widgetPinTitle",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) { current ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(current.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(current.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            val cells = WidgetPin.cellsOf(current)
            val bound = snapshot.boundCount(current)
            val added = if (bound == 0) {
                stringResource(R.string.widget_pin_none)
            } else {
                stringResource(R.string.widget_pin_count, bound)
            }
            val meta = listOf(cells, added)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * 一页：一张卡摆在浅中性底的预览舞台上。
 *
 * 曾试过「迷你桌面」情境化背景（渐变壁纸 + 4 列网格 + 底部 Dock 胶囊，同 iOS/系统选择器），
 * 真机看过后用户定稿换回浅色纯底：壁纸本身抢视觉焦点，预览的主角是卡不是舞台。
 * 网格/Dock 是画在壁纸上的白 alpha 线，浅底上无意义，一并退场。
 */
@Composable
private fun PreviewPage(entry: WidgetPin.Entry, stageHeight: Dp) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(stageHeight)
            .padding(horizontal = PAGE_H_PADDING),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PREVIEW_STAGE_PADDING),
            contentAlignment = Alignment.Center
        ) {
            PreviewImage(
                imageRes = entry.previewImageRes,
                designSize = WidgetPin.designSizeOf(entry),
                // 读屏用户滑到这页能知道是哪个组件（图本身无从辨认）
                contentDescription = stringResource(entry.titleRes)
            )
        }
    }
}

/**
 * 预览图：按组件在**桌面上的真实占格尺寸**等比摆放。
 *
 * 关键是一套**统一**缩放系数（以最大的卡 4×3 为基准），这样 1×1 是小方块、2×1 是矮条、
 * 4×3 是大块 —— 相对大小就等于它们占的格数。尺寸只跟预览区宽度有关，不随屏幕密度变。
 */
@Composable
private fun PreviewImage(imageRes: Int, designSize: Pair<Int, Int>?, contentDescription: String?) {
    val painter = painterResource(imageRes)
    val (designW, designH) = designSize ?: FALLBACK_SIZE
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 最大的卡（4×3）恰好装进预览区，其余按同一系数缩小 —— 各自 fit 会让小卡也撑满，
        // 看上去尺寸都一样（旧实现的毛病）。
        val scale = minOf(
            maxWidth.value / DESIGN_MAX_WIDTH,
            maxHeight.value / DESIGN_MAX_HEIGHT
        )
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .width((designW * scale).dp)
                .height((designH * scale).dp)
                .testTag(PREVIEW_IMAGE_TAG),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 前提提示条（只在需要时出现，用**中性底**而不是警示色——它是前提条件，不是报错）：
 * - 桌面不支持应用内添加：只能退回系统那条路（这时才提，平时不讲）；
 * - 系统桌面且**从未成功落桌**：落桌前校验「桌面快捷方式」权限，没开点添加会静默失败；
 *   成功过一次就不再提示（权限已开的实证，条件见 [WidgetPin.showMiuiPermStrip]）。
 */
@Composable
private fun PermissionStrip(pinSupported: Boolean, isMiui: Boolean, pinOk: Boolean) {
    if (!pinSupported) {
        InfoBlock(
            title = stringResource(R.string.widget_page_unsupported),
            body = null,
            action = null,
            onClick = {}
        )
        return
    }
    if (!WidgetPin.showMiuiPermStrip(pinSupported = pinSupported, isMiui = isMiui, pinOk = pinOk)) return

    val context = LocalContext.current
    InfoBlock(
        title = stringResource(R.string.widget_page_perm_title),
        body = stringResource(R.string.widget_page_perm_desc),
        action = stringResource(R.string.widget_page_perm_action),
        onClick = {
            // 系统桌面 的「桌面快捷方式」开关不在任何公开 API 里，只能带用户去应用详情页手动开
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
            )
        }
    )
}

/** 短提示：添加结果只在这里回报，所以只报核对过的事实。 */
private fun toast(context: Context, resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
}

/** 中性提示块：标题 + 可选说明 + 可选行内动作。 */
@Composable
private fun InfoBlock(title: String, body: String?, action: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            body?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            action?.let {
                TextButton(contentPadding = PaddingValues(horizontal = 0.dp), onClick = onClick) {
                    Text(it)
                }
            }
        }
    }
}

/** 底部通栏按钮：整页只有这一个动作，点它添加当前这一页的组件。
 *  落桌核对期转「确认中…」并禁用 —— 返回值不等于成功，核对完才放行下一次点击。 */
@Composable
private fun BottomBar(bound: Int, pinSupported: Boolean, verifying: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Button(
            enabled = pinSupported && !verifying,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    when {
                        verifying -> R.string.widget_page_add_confirming
                        bound == 0 -> R.string.widget_pin_add
                        else -> R.string.widget_page_add_again
                    }
                )
            )
        }
    }
}
