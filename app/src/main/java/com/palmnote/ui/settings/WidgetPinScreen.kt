// 「画廊 + 详情面板」两层同处一屏：面板那叠组件（顶栏 / 大预览 / 两种选项行 / 添加按钮）与
// 画廊格共用同一个预览缩放算法，拆文件反而要把那套私有算法对外公开，因此按 life 各屏的既有
// 做法在文件级放宽函数条数（detekt 阈值 30）。
@file:Suppress("TooManyFunctions")

package com.palmnote.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.palmnote.app.R
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.COMPACT_DRAG_HANDLE_PADDING
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.widget.WidgetPin
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** 画廊单卡最小宽度：手机恰好两列，平板 / 横屏自动多列（预览按实际宽度等比缩放，不会失真）。 */
private val GRID_MIN_TILE_WIDTH = 150.dp

/** 画廊卡片里预览底托的固定高度（托就是卡身）：以 4×3 横向恰好吃满为准。 */
private val PREVIEW_HEIGHT = 126.dp

/**
 * 面板高度占**可用高度**的比例：对齐系统「添加小部件」那种**近全高面板**（顶部留出状态栏与把手）。
 * 里边用 `weight` 分配余量 —— 预览吃掉中间那块空档、按钮钉在底部，全程不用滚。
 * 给比例而不是具体 dp：可用高度已扣掉系统栏与把手，面板因此永远不会顶出屏幕。
 */
private const val SHEET_HEIGHT_FRACTION = 0.92f

/** 预览底托的内边距（画廊卡用）：底托比 widget 外扩这一圈，widget 才不会贴着底托边，也留出投影的位置。 */
private val STAGE_PADDING = 10.dp

/**
 * 面板里尺寸分页圆点的直径与间距。
 *
 * 尺寸行撤掉之后，这排点就是**尺寸的唯一指示**，所以它得跟着手指连续变（见 [SizeDots]），
 * 不能只在落页时跳一下。直径对齐系统组件预览页的分页点（实测 4dp，取 5dp 留一点触底余量）。
 */
private val SIZE_DOT_SIZE = 5.dp
private val SIZE_DOT_GAP = 6.dp

/**
 * 面板顶栏：左「←」+ 中间「应用图标 + 应用名」—— 系统「添加小部件」页顶部就是这一行（系统
 * 选择器的招牌 chrome），也是面板里唯一的显式返回入口（此前只能下滑 / 点遮罩 / 系统返回）。
 * 高度取 44dp：够放下 48dp 触控盒的返回键，又不比把手那一槽更高（对齐系统页 30dp 上下就贴住把手）。
 */
private val SHEET_TOPBAR_HEIGHT = 44.dp

/** 顶栏里应用图标底框的边长（图形占底框一半，见 [SheetTopBar] 里 1.5 倍的取法）。 */
private val SHEET_APP_ICON = 20.dp

/**
 * 面板配置区的胶囊：**无描边实底**（系统选择器那套），比 M3 的 `FilterChip` 高一档、
 * 不用描边。系统页的选项行都是实底圆片 / 胶囊，带 1dp 描边的 `FilterChip` 是应用内表单的语汇，
 * 摆进面板会立刻显得「旧」。
 */
private val PILL_HEIGHT = 36.dp
private val PILL_H_PADDING = 16.dp
private val PILL_GAP = 8.dp

/** 胶囊选中态的淡入时长：颜色突变（尤其五档透明度来回点）会显得生硬，给一小段过渡就顺了。 */
private const val PILL_FADE_MILLIS = 160

/** 面板底部添加按钮：对齐系统页的 50dp 实底胶囊（M3 默认 40dp 偏矮）。 */
private val SHEET_ADD_HEIGHT = 50.dp

/** 面板里可选的透明度档位：与配置页同一套四档（另加「跟随默认」= null）。 */
private val OPACITY_LEVELS = listOf(1f, 0.8f, 0.6f, 0.4f)

/** 画廊卡预览右上角「已在桌面」小勾的直径与内缩。 */
private val ADDED_BADGE_SIZE = 18.dp
private val ADDED_BADGE_INSET = 4.dp

/** 卡身右侧「＋」快捷添加按钮的触控盒边长：显式 48dp 达 Material 最小触控目标。 */
private val QUICK_ADD_SIZE = 48.dp

/**
 * 预览的柔影：底托是淡灰、widget 是白底，只靠颜色差分层不够，加一层投影才像浮在桌面上。
 * 投影跟着缩放系数一起缩，画廊卡上的小预览才不会拖一条大影。
 * 画廊卡垫在底托上、影轻一点（[PREVIEW_SHADOW]）；面板里没有底托、直接浮在白底上，
 * 影要更明显才立得住（[FLOATING_SHADOW]）。
 */
private val PREVIEW_SHADOW = 5.dp
private val FLOATING_SHADOW = 10.dp

/** widget 卡片自身的圆角（与 drawable/widget_card_round.xml 的 22dp 同源）：投影轮廓得和它对齐。 */
private val WIDGET_CARD_CORNER = 22.dp

/**
 * 全目录统一的缩放基准：设计稿里最大的卡（4×3 = 340×268dp，见 design-v4.html 的 `.s43`）。
 * 所有预览乘同一个系数 —— 1×1 是小方块、2×1 是矮条、4×3 是大块，相对大小等于桌面占格比例。
 */
private const val DESIGN_MAX_WIDTH = 340f
private const val DESIGN_MAX_HEIGHT = 268f

/** 组件没登记设计尺寸时的兜底：按 3×2 卡摆。 */
private val FALLBACK_SIZE = 245 to 148

/** 画廊卡（含预览图、名称、尺寸标）的测试标签。 */
internal const val CATALOG_CELL_TAG = "widget_catalog_cell"

/** 画廊网格的测试标签：滚动断言得点名它（面板里还有别的可滚节点）。 */
internal const val CATALOG_LIST_TAG = "widget_catalog_list"

/** 画廊卡内预览图的测试标签（单测靠它量 dp 尺寸）。 */
internal const val PREVIEW_IMAGE_TAG = "widget_preview_image"

/** 画廊卡名称下那行小字右端「＋」快捷添加按钮的测试标签（不弹面板、按当前尺寸直接落桌）。 */
internal const val QUICK_ADD_TAG = "widget_quick_add"

/** 画廊卡预览右上角「已在桌面」小勾的测试标签。 */
internal const val ADDED_BADGE_TAG = "widget_added_badge"

/** 搜索框的测试标签。 */
internal const val SEARCH_FIELD_TAG = "widget_search_field"

/** 底部面板内容的测试标签。 */
internal const val SHEET_TAG = "widget_pin_sheet"

/** 面板顶栏（返回键 + 应用图标 + 应用名）的测试标签。 */
internal const val SHEET_TOPBAR_TAG = "widget_pin_sheet_topbar"

/** 面板顶栏返回键的测试标签。 */
internal const val SHEET_BACK_TAG = "widget_pin_sheet_back"

/**
 * 面板里大预览的测试标签：只挂**当前页** —— 翻页途中相邻页也在组合里，同一标签会撞成多节点。
 */
internal const val SHEET_PREVIEW_TAG = "widget_pin_sheet_preview"

/** 面板里相邻页预览的测试标签（非当前页）：只为把当前页那个标签腾干净，单测不点它。 */
private const val SHEET_PREVIEW_IDLE_TAG = "widget_pin_sheet_preview_idle"

/** 面板里添加按钮的测试标签。 */
internal const val SHEET_ADD_TAG = "widget_pin_sheet_add"

/** 面板里尺寸分页圆点行的测试标签（单尺寸组件同样不显示）。 */
internal const val SHEET_DOTS_TAG = "widget_pin_sheet_dots"

/** 面板里单颗分页圆点的测试标签：点数 = 该组件的尺寸个数。 */
internal const val SHEET_DOT_TAG = "widget_pin_sheet_dot"

/** 面板里透明度档位行的测试标签（带配置页的组件不显示这一行）。 */
internal const val SHEET_OPACITY_ROW_TAG = "widget_pin_sheet_opacity"

/**
 * 添加页：**只在应用内一键添加**（`requestPinAppWidget`），不讲系统的手动添加路径。
 *
 * 页面走中性底（`surface`）+ 二级页顶栏：整页观感对齐各家小部件页，但顶栏与搜索框仍用项目组件。
 *
 * 首屏是**画廊**：按用途分组（记账 / 生活 / 效率 / 资产 / 其他），两列铺开，每张卡 =
 * **灰底卡身**（预览的底托就是卡本身，不再套一层白卡）+ 托下居中的名称 + 一行灰色小字
 * （未落桌写尺寸标、落过桌写「已有 N 个」，右端一枚「＋」）—— 先「看长相」找组件；
 * 已落桌的卡在预览右上角带小勾。点卡身弹出**近全高面板**（对齐各家「添加小部件」的观感）：
 * 名称 + 说明两行居中 + 大预览（按桌面真实尺寸浮在面板上，**左右滑动换尺寸**、下缘一排分页圆点）
 * + 透明度档位 + 添加按钮，当场选当场落桌；卡上的「＋」不走面板，
 * 直接按当前尺寸落桌，省一次点击。同组件的多个尺寸形态合并成一张卡（默认基类尺寸），
 * 不必退回目录找另一个格子。
 */
@Composable
fun WidgetPinScreen(
    onNavigateBack: () -> Unit = {},
    onApplyOpacity: (Int, Float?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { WidgetPin.catalog() }
    val homeNeedsPerm = remember { WidgetPin.homeNeedsPerm(context) }

    var snapshot by remember { mutableStateOf(WidgetPin.snapshot(context)) }
    // 落桌核对期（最多 5×1.5s）锁住按钮：不锁的话连点会连发多个系统确认框
    var verifying by remember { mutableStateOf(false) }
    // 每个组件选过的尺寸（族键 → entries 下标）；面板里换了尺寸，收起来后画廊卡也跟着换
    var selectedIndex by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    // 搜索态与账单 / 资产各页一致：平时顶栏只有个搜索图标，点了才换成搜索框 + 「取消」
    var showSearch by remember { mutableStateOf(false) }
    // 当前打开面板的组件（族键）；null = 没开
    var openFamilyKey by remember { mutableStateOf<Int?>(null) }
    // 面板里挑的透明度：null = 跟随全局默认。只对**本次添加**有效，落桌成功或收起面板就清掉（免得继承上次的选择）
    var chosenOpacity by remember { mutableStateOf<Float?>(null) }

    // 从系统确认框回来时（ON_RESUME）重数一遍桌面实例，把「桌面已有 N 个」刷新到最新
    RefreshSnapshotOnResume { snapshot = WidgetPin.snapshot(context) }
    // 搜索态用系统返回退出（先关搜索、再退页面），取消键同样清词回画廊
    BackHandler(enabled = showSearch) { showSearch = false; query = "" }

    // 点了「添加」不立刻报结果：requestPinAppWidget 返回 true 只说明通道可用，桌面缺权限时会静默失败；
    // 所以落桌后等几秒重数桌面实例，数到了才算成了。核对期间按钮转「确认中…」并禁用，连点不会连发多个系统确认框
    fun addWidget(entry: WidgetPin.Entry) {
        if (verifying) return
        val before = WidgetPin.boundIds(context, entry.providerClass)
        if (!WidgetPin.requestPin(context, entry)) {
            toast(context, R.string.widget_pin_failed)
            return
        }
        verifying = true
        scope.launch {
            // 核对结果顺带带回新实例 id：面板里挑的透明度要落到这个实例上
            val newId = WidgetPin.awaitPinned(context, entry, before)
            if (newId != null) {
                val picked = pickedOpacityForNewInstance(entry, chosenOpacity)
                if (picked != null) onApplyOpacity(newId, picked)
            }
            snapshot = WidgetPin.snapshot(context)
            verifying = false
            // 确认落桌才收起面板：加完还停在原地，用户会以为没成功
            if (newId != null) { openFamilyKey = null; chosenOpacity = null }
            toast(context, if (newId != null) R.string.widget_pin_added else R.string.widget_pin_not_added)
        }
    }

    // 落桌统一入口：面板按钮和卡片「＋」都走这里 —— 按该组件当前选中的尺寸落桌
    fun pinCurrent(component: WidgetPin.CatalogComponent) {
        addWidget(selectedEntry(component, selectedIndex))
    }

    // 页面走中性底（白 / 深色 #1E1E1E）：与各家小部件页一致，顶栏也跟着走，别留一条米色
    Scaffold(
        topBar = {
            WidgetPinTopBar(
                onNavigateBack = onNavigateBack,
                query = query, onQueryChange = { query = it },
                showSearch = showSearch, onToggleSearch = { showSearch = it; if (!it) query = "" }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            CatalogGrid(
                modifier = Modifier.weight(1f),
                catalog = catalog, query = query, snapshot = snapshot,
                selectedIndex = selectedIndex, verifying = verifying,
                onOpen = { openFamilyKey = it.familyKey },
                onQuickAdd = { pinCurrent(it) }
            )
            PermissionStrip(
                pinSupported = snapshot.pinSupported, homeNeedsPerm = homeNeedsPerm, pinOk = snapshot.pinOk
            )
            Spacer(Modifier.height(10.dp))
        }
    }

    val openComponent = catalog.firstComponentOrNull(openFamilyKey)
    if (openComponent != null) {
        WidgetPinSheet(
            component = openComponent,
            selectedIndex = selectedIndex,
            snapshot = snapshot,
            verifying = verifying,
            opacity = chosenOpacity,
            onDismiss = {
                openFamilyKey = null
                chosenOpacity = null
            },
            onSelectSize = { entry ->
                selectedIndex = selectedIndex +
                    (openComponent.familyKey to openComponent.entries.indexOf(entry))
            },
            onSelectOpacity = { chosenOpacity = it },
            onAdd = { pinCurrent(openComponent) }
        )
    }
}

/** 按族键找回组件；没开面板（null）或找不到就给 null。 */
private fun List<WidgetPin.CatalogGroup>.firstComponentOrNull(familyKey: Int?): WidgetPin.CatalogComponent? {
    if (familyKey == null) return null
    return asSequence().flatMap { it.components }.firstOrNull { it.familyKey == familyKey }
}

/**
 * 底部面板：近全高的悬浮面板（高度 = 可用高度 × [SHEET_HEIGHT_FRACTION]），内容交给
 * [WidgetPinSheetContent]。抽出来让 [WidgetPinScreen] 只留状态与装配。
 */
@Composable
private fun WidgetPinSheet(
    component: WidgetPin.CatalogComponent,
    selectedIndex: Map<Int, Int>,
    snapshot: WidgetPin.Snapshot,
    verifying: Boolean,
    opacity: Float?,
    onDismiss: () -> Unit,
    onSelectSize: (WidgetPin.Entry) -> Unit,
    onSelectOpacity: (Float?) -> Unit,
    onAdd: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        heightFraction = SHEET_HEIGHT_FRACTION,
        // 把手贴顶：M3 默认 48dp 的槽会把顶栏 / 标题整体压低 20dp 上下，系统选择器那一行是贴着的
        dragHandlePadding = COMPACT_DRAG_HANDLE_PADDING
    ) {
        WidgetPinSheetContent(
            component = component,
            selected = selectedEntry(component, selectedIndex),
            bound = snapshot.boundCountOf(component),
            pinSupported = snapshot.pinSupported,
            verifying = verifying,
            opacity = opacity,
            onBack = onDismiss,
            onSelectSize = onSelectSize,
            onSelectOpacity = onSelectOpacity,
            onAdd = onAdd
        )
    }
}

/**
 * 顶栏：标题 + 返回 + 搜索入口 —— 与账单 / 资产各页同一套打法：平时只是一个搜索图标，
 * 点了才换成整行搜索框 + 「取消」，不让一条常驻搜索框占掉画廊首屏（它们也常驻了）。
 * 标题用 [SecondaryTopAppBar] 的 22sp 黑体（二级页朴素黑字，别学首页的 36sp 主色大标题）；
 * 底色随页面走（白色），别留一条米色顶栏。
 */
@Composable
private fun WidgetPinTopBar(
    onNavigateBack: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit
) {
    SecondaryTopAppBar(
        title = {
            if (showSearch) {
                // IME「搜索」键收键盘：结果就在下面这屏，没有「提交搜索」这一步
                val focusManager = LocalFocusManager.current
                ModuleSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { focusManager.clearFocus() },
                    onClear = { onQueryChange("") },
                    placeholder = stringResource(R.string.widget_search_hint),
                    modifier = Modifier.fillMaxWidth().testTag(SEARCH_FIELD_TAG)
                )
            } else {
                Text(
                    stringResource(R.string.settings_widget_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_navigate_back)
                )
            }
        },
        actions = {
            if (showSearch) {
                TextButton(
                    onClick = { onToggleSearch(false) },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                IconButton(onClick = { onToggleSearch(true) }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

/** 回到前台时回调一次：用于重数桌面实例。 */
@Composable
private fun RefreshSnapshotOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * 画廊网格：无搜索词时按分组铺（组标题横跨整行），有搜索词时拍平成匹配结果。
 * 两种呈现共用同一张卡 [CatalogTile]。
 */
@Composable
private fun CatalogGrid(
    modifier: Modifier,
    catalog: List<WidgetPin.CatalogGroup>,
    query: String,
    selectedIndex: Map<Int, Int>,
    snapshot: WidgetPin.Snapshot,
    verifying: Boolean,
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onQuickAdd: (WidgetPin.CatalogComponent) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // 过滤在普通 lambda 里做，用 context.getString（stringResource 是 @Composable，不能在这用）。
    // 名称与说明都参与匹配（搜「预算」也能找到「本月记账」），但名称命中的排在说明命中的前面。
    val results = if (query.isBlank()) {
        null
    } else {
        catalog.flatMap { it.components }
            .mapNotNull { component ->
                val rank = when {
                    context.getString(component.titleRes).contains(query, ignoreCase = true) -> 0
                    context.getString(component.descRes).contains(query, ignoreCase = true) -> 1
                    else -> return@mapNotNull null
                }
                component to rank
            }
            .sortedBy { it.second } // sortedBy 稳定：同档内保持目录顺序
            .map { it.first }
    }
    if (results != null && results.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.search_no_results),
                subtitle = stringResource(R.string.widget_search_empty_hint)
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_MIN_TILE_WIDTH),
        modifier = modifier
            .fillMaxWidth()
            .testTag(CATALOG_LIST_TAG)
            // 搜完滑画廊看卡时就把键盘收掉（点按也一样），别让键盘一直盖着大半屏
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    focusManager.clearFocus()
                }
            },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // 纵向与首页卡片行距一致（8dp）：组间不另垫 padding，别空出一大截
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (results == null) {
            catalog.forEach { group ->
                item(key = "h_${group.group.name}", span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeader(group.group)
                }
                items(group.components, key = { "c_${it.familyKey}" }) { component ->
                    CatalogTileItem(component, selectedIndex, snapshot, verifying, query, onOpen, onQuickAdd)
                }
            }
        } else {
            items(results, key = { "s_${it.familyKey}" }) { component ->
                CatalogTileItem(component, selectedIndex, snapshot, verifying, query, onOpen, onQuickAdd)
            }
        }
    }
}

/** 单张画廊卡：从状态里取当前尺寸与桌面实例数，交给 [CatalogTile] 渲染。 */
@Composable
private fun CatalogTileItem(
    component: WidgetPin.CatalogComponent,
    selectedIndex: Map<Int, Int>,
    snapshot: WidgetPin.Snapshot,
    verifying: Boolean,
    query: String,
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onQuickAdd: (WidgetPin.CatalogComponent) -> Unit
) {
    CatalogTile(
        component = component,
        selected = selectedEntry(component, selectedIndex),
        bound = snapshot.boundCountOf(component),
        query = query,
        quickAddEnabled = snapshot.pinSupported && !verifying,
        onOpen = onOpen,
        onQuickAdd = onQuickAdd
    )
}

/** 组件当前选中的尺寸形态：优先用户选过，否则基类（entries 首位）。 */
private fun selectedEntry(
    component: WidgetPin.CatalogComponent,
    selectedIndex: Map<Int, Int>
): WidgetPin.Entry = component.entries.getOrElse(selectedIndex[component.familyKey] ?: 0) {
    component.entries.first()
}

/**
 * 面板里挑的透明度要不要写到刚落桌的那个实例上：**只在没有配置页的组件上写**。
 * 账单那种带配置页的组件，落桌后系统会拉起它的配置页，那里同时管账本与透明度 ——
 * 两边都写会互相覆盖（我们写得早，配置页确认时又按它自己那份值写一遍）。
 */
internal fun pickedOpacityForNewInstance(entry: WidgetPin.Entry, picked: Float?): Float? =
    picked.takeIf { entry.configureActivity == null }

/**
 * 搜索态下把名称里命中的那段染成主色加粗；没有搜索词或没命中就原样返回。
 * 读屏读到的仍是完整名称（[AnnotatedString] 的纯文本部分不变）。
 */
@Composable
private fun highlightMatch(text: String, query: String): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return remember(text, query, accent) {
        val start = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
        if (start < 0) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text.take(start))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                    append(text.substring(start, start + query.length))
                }
                append(text.drop(start + query.length))
            }
        }
    }
}

/** 分组标题，横跨整行：黑体不加彩（主色留给交互元素，别让每个组标题都在喊）。 */
@Composable
private fun GroupHeader(group: WidgetPin.WidgetGroup) {
    // 不另垫上下 padding：与卡片同吃网格的 8dp 行距，组间不再空出一大截
    Text(
        stringResource(group.titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

/**
 * 画廊卡：**卡身就是预览底托**（淡灰圆角块，不再套一层白卡）—— 各家小部件页都是这么摆的：
 * 灰底托上是真尺寸预览，名称与尺寸标写在托下面，「一块预览 + 两行说明」。
 * 点卡身开底部面板挑尺寸；右侧小「＋」按当前尺寸直接落桌，省掉进面板那一步。
 * 搜索态下命中词在名称里高亮。
 */
@Composable
private fun CatalogTile(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    bound: Int,
    query: String,
    quickAddEnabled: Boolean,
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onQuickAdd: (WidgetPin.CatalogComponent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CATALOG_CELL_TAG)
            .clickable(
                onClickLabel = stringResource(R.string.widget_pin_choose_size),
                onClick = { onOpen(component) }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 已落桌的在预览右上角叠一枚小勾：一眼扫过去就知道哪些已经在桌面上了
        Box {
            WidgetPreview(
                imageRes = selected.previewImageRes,
                designSize = WidgetPin.designSizeOf(selected),
                stage = true,
                tag = PREVIEW_IMAGE_TAG,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
            )
            if (bound > 0) AddedBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = highlightMatch(stringResource(component.titleRes), query),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        TileMeta(
            cells = WidgetPin.cellsOf(selected),
            bound = bound,
            quickAddEnabled = quickAddEnabled,
            onQuickAdd = { onQuickAdd(component) }
        )
    }
}

/**
 * 名称下面那行灰色小字：没落过桌就写尺寸标，落过桌就写「已有 N 个」；右端一枚小「＋」快捷添加。
 * 文案居中、和名称同一条中轴，跟各家小部件页「预览 + 名称 + 一行灰色说明」一个样子。
 */
@Composable
private fun TileMeta(cells: String, bound: Int, quickAddEnabled: Boolean, onQuickAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (bound > 0) stringResource(R.string.widget_pin_count_short, bound) else cells,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center)
        )
        QuickAddButton(
            enabled = quickAddEnabled,
            onClick = onQuickAdd,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

/** 卡身右侧的「＋」：按当前尺寸直接落桌；不可用（桌面不支持 / 正在核对）时置灰。 */
@Composable
private fun QuickAddButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 触控盒显式 48dp（Material 最小触控目标；minimumInteractiveComponentSize 只扩触控区、
    // 不改布局尺寸，这行文字要对齐中轴就得显式定尺寸）。内部状态层仍 40dp，视觉与 Material 默认一致。
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(QUICK_ADD_SIZE)
            .testTag(QUICK_ADD_TAG)
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = stringResource(R.string.widget_pin_quick_add),
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 画廊卡预览右上角的小勾：这个组件已经在桌面上（具体几个由底行的「已有 N 个」说）。
 * 纯视觉标记，不带 contentDescription —— 数量文案已经把这层意思读出来了，再读一遍是噪音。
 */
@Composable
private fun AddedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(ADDED_BADGE_INSET)
            .size(ADDED_BADGE_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .testTag(ADDED_BADGE_TAG),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(12.dp)
        )
    }
}

/**
 * 面板内容：顶栏（返回 + 应用身份）+ 头部（名称 / 说明，居中一叠）+ 尺寸翻页器（**吃掉中间余量**：
 * 大预览左右滑换尺寸，下缘一排分页圆点）+ 透明度档位 + 添加按钮（抽出来是为了能单独出图）。
 * 高度由 [AppBottomSheet] 的 height 定住，预览用 weight 占住中间那块空档 —— 面板是「近全高」的。
 *
 * 参数多且都从外面注入：这一层是纯展示（状态与动作都归 `WidgetPinScreen` / ViewModel），
 * 按仓库既有口径（`WidgetConfigScreen` 同款）就地放宽参数条数。
 */
@Suppress("LongParameterList")
@Composable
internal fun WidgetPinSheetContent(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    bound: Int,
    pinSupported: Boolean,
    verifying: Boolean,
    opacity: Float?,
    onBack: () -> Unit,
    onSelectSize: (WidgetPin.Entry) -> Unit,
    onSelectOpacity: (Float?) -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SHEET_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SheetTopBar(onBack = onBack)
        SheetHeader(component.titleRes, component.descRes)
        // 预览块吃掉中间余量：widget 与它下面的分页圆点作为**一组**居中 ——
        // 圆点是「这一页能翻」的指示，必须贴着 widget，不能被甩到面板底部去
        SizePager(
            entries = component.entries,
            selected = selected,
            onSelectIndex = { onSelectSize(component.entries[it]) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        // 尺寸不再摆一排胶囊：大预览左右滑就是切换（各家卡片预览页的手势都是滑它），
        // 圆点标出翻到第几个 —— 面板里少一行，也少一个和手势重复的入口
        // 带配置页的组件（账单）落桌后由系统拉起配置页，那里也管透明度，这里就不再重复一档
        if (component.configureActivity == null) {
            OpacityRow(selected = opacity, onSelect = onSelectOpacity)
        }
        SheetAddButton(bound, pinSupported, verifying, onAdd)
    }
}

/**
 * 面板顶栏：左「←」+ 中间「应用图标 + 应用名」。
 *
 * 系统「添加小部件」页顶部就是这一行 —— 系统选择器的招牌 chrome，也是面板里唯一的**显式**
 * 返回入口（此前只能下滑 / 点遮罩 / 系统返回）。返回 = 收起面板回画廊，与系统返回键同义。
 * 图标取启动图标的图形 + 主色底，画法与「关于」页同一套，只是缩到行内小图标的尺寸。
 */
@Composable
private fun SheetTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SHEET_TOPBAR_HEIGHT)
            .testTag(SHEET_TOPBAR_TAG)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag(SHEET_BACK_TAG)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_navigate_back),
                modifier = Modifier.size(22.dp)
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(SHEET_APP_ICON)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_bw),
                    contentDescription = null,
                    // onPrimary 而不是写死白：深色模式下主题色是亮青，白图形会糊成一片
                    tint = MaterialTheme.colorScheme.onPrimary,
                    // 该 vector 的 viewport 是 108dp、图形只占居中的 36dp（= 底框的 1.5 倍处）
                    modifier = Modifier.requiredSize(SHEET_APP_ICON * 1.5f)
                )
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * 面板头部：名称 + 说明两行居中一叠（系统「添加小部件」的面板也是这两行，实例数由画廊卡说）。
 * 名称用 22sp 的 `headlineLarge`（本就是粗体）：对齐系统面板标题的实测字号（墨迹 20dp ≈ 22sp），
 * 18sp 的 `titleLarge` 在这个近全高面板里显小。
 */
@Composable
private fun SheetHeader(titleRes: Int, descRes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 配置区的一行选项：文案 + 选中态 + 点击（尺寸与透明度共用）。 */
private class PickerOption(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * 配置区的一行：标题（左对齐小字灰）+ 一排可选胶囊，横向可滚、左贴齐（尺寸与透明度共用）。
 * 胶囊文字比 M3 默认小一档：透明度五档要在一行里排得下，不靠横向滚动去找最后一档。
 */
@Composable
private fun PickerRow(label: String, tag: String, options: @Composable () -> List<PickerOption>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(PILL_GAP)
        ) {
            options().forEach { option ->
                PillOption(option)
            }
        }
    }
}

/**
 * 无描边实底胶囊：未选中走中性淡底 + 次级文字，选中走主色淡底 + 主色文字。
 *
 * 不用 M3 的 `FilterChip`：那颗未选中态带 1dp 描边，是应用内表单的语汇；系统选择器的选项行是
 * **实底无描边**（系统页的「文字颜色」圆片同一逻辑），面板里换成它才跟系统页一个体系。
 * `selectable` 挂在内层容器上，胶囊自带选中语义（与 `FilterChip` 的读屏口径一致）。
 */
@Composable
private fun PillOption(option: PickerOption) {
    // 底色与文字色都走一段短过渡：五档透明度来回点时，颜色突变比尺寸跳变更显生硬
    val background by animateColorAsState(
        targetValue = if (option.selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
        },
        animationSpec = tween(PILL_FADE_MILLIS),
        label = "pillBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (option.selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(PILL_FADE_MILLIS),
        label = "pillContent"
    )
    Box(
        modifier = Modifier
            .height(PILL_HEIGHT)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .selectable(selected = option.selected, role = Role.RadioButton, onClick = option.onClick)
            .padding(horizontal = PILL_H_PADDING),
        contentAlignment = Alignment.Center
    ) {
        Text(
            option.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

/**
 * 透明度档位：与配置页同一套四档，外加「跟随」（= 不动全局默认）。
 * 在面板里先挑好、落桌时写到新实例上 —— 就是各家「可编辑的小组件」把配置摆在预览下面的做法。
 */
@Composable
private fun OpacityRow(selected: Float?, onSelect: (Float?) -> Unit) {
    PickerRow(
        label = stringResource(R.string.widget_pin_opacity_label),
        tag = SHEET_OPACITY_ROW_TAG,
        options = {
            listOf(
                PickerOption(
                    label = stringResource(R.string.widget_pin_opacity_follow),
                    selected = selected == null,
                    onClick = { onSelect(null) }
                )
            ) + OPACITY_LEVELS.map { level ->
                PickerOption(
                    label = stringResource(
                        R.string.widget_pin_opacity_level,
                        (level * 100f).roundToInt()
                    ),
                    selected = selected == level,
                    onClick = { onSelect(level) }
                )
            }
        }
    )
}

/**
 * 尺寸翻页器：widget 与它下面的分页圆点作为**一组**居中（圆点是「这一页能翻」的指示）。
 *
 * 用 `HorizontalPager` 而不是自己接 `detectHorizontalDragGestures`：拖动时预览跟着手指走、
 * 松手按**甩动速度**落页，圆点在拖动过程中就连续跟着变 —— 之前是「松手才跳一下」，
 * 拖动期间毫无反馈，所以滑起来发涩。
 *
 * 页高按**全局最大卡**（4×3）算，不按本组最大的那个尺寸，这条是刻意钉住的：
 * ① 页高 = 页内缩放系数的分母，按本组最大算的话，只有矮条的组件（快捷入口最大 1×1）
 *    整组预览会被缩到桌面真实尺寸的六成；② 组件之间对齐 —— 不管打开哪个组件，
 * 圆点行都落在面板里同一行上，翻页时容器也不跳、小尺寸在页内居中。
 */
@Composable
private fun SizePager(
    entries: List<WidgetPin.Entry>,
    selected: WidgetPin.Entry,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = entries.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { entries.size }
    // 落页后回报上层（写回 selected 再落桌）；首帧那一发就是开页用的初始页，跳过不回报
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { onSelectIndex(it) }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 与 [WidgetPreview] 同一条系数：相对大小才等于它们在桌面上的占格比例
            val scale = minOf(maxWidth.value / DESIGN_MAX_WIDTH, 1f)
            val track = (DESIGN_MAX_HEIGHT * scale).coerceAtMost(maxHeight.value)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(track.dp),
                // 只有一个尺寸就没什么可翻的：关掉手势，免得白划一下
                userScrollEnabled = entries.size > 1
            ) { page ->
                val entry = entries[page]
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    WidgetPreview(
                        imageRes = entry.previewImageRes,
                        designSize = WidgetPin.designSizeOf(entry),
                        stage = false,
                        // 标签只挂「当前选中那一页」：翻页途中相邻页也在组合里，同一标签会撞出多节点。
                        // 拿 selected 比而不是拿 currentPage 比：这样「翻页结果有没有回报给上层」
                        // 直接体现在渲染上 —— 回报断了，这一页就再也挂不上这个标签。
                        tag = if (entry == selected) SHEET_PREVIEW_TAG else SHEET_PREVIEW_IDLE_TAG,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (entries.size > 1) {
            SizeDots(
                count = entries.size,
                pagerState = pagerState,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * 面板里的添加按钮：核对期转「确认中…」并禁用，防连发确认框。
 * 50dp 实底胶囊 + 16sp 粗白字：对齐系统页底部那颗按钮的体量（M3 默认 40dp 偏矮，
 * 在一屏留白里显得没落款）。
 */
@Composable
private fun SheetAddButton(bound: Int, pinSupported: Boolean, verifying: Boolean, onAdd: () -> Unit) {
    Button(
        onClick = onAdd,
        enabled = pinSupported && !verifying,
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier
            .fillMaxWidth()
            .height(SHEET_ADD_HEIGHT)
            .testTag(SHEET_ADD_TAG)
    ) {
        Text(
            stringResource(
                when {
                    verifying -> R.string.widget_page_add_confirming
                    bound == 0 -> R.string.widget_pin_add
                    else -> R.string.widget_page_add_again
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 尺寸分页圆点：预览下面一排小点，标记当前翻到第几个尺寸。
 *
 * 位置从 `currentPageOffsetFraction` **连续**取，不是只在落页时改索引 —— 拖动过程中点就跟着
 * 手指在灰/深之间过渡，松手不用再「啪」地跳一下（这是「顺不顺」的大头）。
 * 纯视觉：翻页手势本身就是入口（读屏靠 pager 自己的滚动动作），这里不重复播报。
 * 选中那颗用前景色（各家分页点都是深浅灰，不拿品牌色去点）。
 */
@Composable
private fun SizeDots(count: Int, pagerState: PagerState, modifier: Modifier = Modifier) {
    val active = pagerState.currentPage + pagerState.currentPageOffsetFraction
    Row(
        modifier = modifier.testTag(SHEET_DOTS_TAG),
        horizontalArrangement = Arrangement.spacedBy(SIZE_DOT_GAP)
    ) {
        repeat(count) { index ->
            // 离「当前页」多远：0 = 就是它，1 = 隔壁那颗；用来把颜色从淡灰连到前景色
            val away = (index - active).absoluteValue.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(SIZE_DOT_SIZE)
                    .clip(CircleShape)
                    .background(
                        lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.onSurface,
                            1f - away
                        )
                    )
                    .testTag(SHEET_DOT_TAG)
            )
        }
    }
}

/**
 * 组件预览：按它在**桌面上的真实占格尺寸**等比摆放。两种穿法：
 * - `stage = true`：垫一层淡底托（像一个桌面格位）—— 画廊小卡用，白底 widget 才不会和卡片糊在一起；
 * - `stage = false`：直接浮在白底上，只靠柔影分层 —— 面板大预览用（各家「添加小部件」的大预览
 *   都是真尺寸浮空，没有底托）。
 *
 * 关键是一套**统一**缩放系数（以最大的卡 4×3 为基准，且**不超过 1:1**）：1×1 是小方块、
 * 2×1 是矮条、4×3 是大块 —— 相对大小就等于它们占的格数。
 * 尺寸只跟可用空间有关，不随屏幕密度变。
 */
@Composable
private fun WidgetPreview(
    imageRes: Int,
    designSize: Pair<Int, Int>?,
    stage: Boolean,
    tag: String,
    modifier: Modifier = Modifier
) {
    val padding = if (stage) STAGE_PADDING else 0.dp
    val content: @Composable BoxWithConstraintsScope.() -> Unit = {
        // 能吃满就吃满，但最大只到 1:1（就是桌面上原大，再放大就不是「真尺寸预览」了）
        val scale = minOf(
            (maxWidth - padding * 2).value / DESIGN_MAX_WIDTH,
            (maxHeight - padding * 2).value / DESIGN_MAX_HEIGHT,
            1f
        )
        val (designW, designH) = designSize ?: FALLBACK_SIZE
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .shadow(
                    elevation = (if (stage) PREVIEW_SHADOW else FLOATING_SHADOW) * scale,
                    shape = RoundedCornerShape(WIDGET_CARD_CORNER * scale),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .size((designW * scale).dp, (designH * scale).dp)
                .testTag(tag)
        )
    }
    if (stage) {
        Surface(
            // 托就是卡本身（画廊卡不再套一层白卡），圆角跟 widget 自己的卡角对齐
            shape = RoundedCornerShape(WIDGET_CARD_CORNER),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
    } else {
        BoxWithConstraints(
            modifier = modifier,
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * 前提提示条（只在需要时出现，用**中性底**而不是警示色——它是前提条件，不是报错）：
 * - 桌面不支持应用内添加：只能退回系统那条路（这时才提，平时不讲）；
 * - 当前桌面 pin 前会校验「桌面快捷方式」权限、且**从未成功落桌**：落桌时该权限没开就静默失败；
 *   成功过一次就不再提示（权限已开的实证，条件见 [WidgetPin.showHomePermStrip]）。
 */
@Composable
private fun PermissionStrip(pinSupported: Boolean, homeNeedsPerm: Boolean, pinOk: Boolean) {
    if (!pinSupported) {
        InfoBlock(
            title = stringResource(R.string.widget_page_unsupported),
            body = null,
            action = null,
            onClick = {}
        )
        return
    }
    if (!WidgetPin.showHomePermStrip(pinSupported = pinSupported, homeNeedsPerm = homeNeedsPerm, pinOk = pinOk)) return

    val context = LocalContext.current
    InfoBlock(
        title = stringResource(R.string.widget_page_perm_title),
        body = stringResource(R.string.widget_page_perm_desc),
        action = stringResource(R.string.widget_page_perm_action),
        onClick = {
            // 该「桌面快捷方式」开关不在任何公开 API 里，只能带用户去应用详情页手动开
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
