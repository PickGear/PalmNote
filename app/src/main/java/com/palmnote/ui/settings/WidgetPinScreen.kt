package com.palmnote.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.widget.WidgetPin
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 画廊单卡最小宽度：手机恰好两列，平板 / 横屏自动多列（预览按实际宽度等比缩放，不会失真）。 */
private val GRID_MIN_TILE_WIDTH = 150.dp

/** 画廊卡片里预览底托的固定高度（托就是卡身）：以 4×3 横向恰好吃满为准。 */
private val PREVIEW_HEIGHT = 126.dp

/**
 * 面板高度占**可用高度**的比例：对齐小米「添加小部件」那种**近全高面板**（顶部留出状态栏与把手）。
 * 里边用 `weight` 分配余量 —— 预览吃掉中间那块空档、按钮钉在底部，全程不用滚。
 * 给比例而不是具体 dp：可用高度已扣掉系统栏与把手，面板因此永远不会顶出屏幕。
 */
private const val SHEET_HEIGHT_FRACTION = 0.92f

/** 预览底托的内边距（画廊卡用）：底托比 widget 外扩这一圈，widget 才不会贴着底托边，也留出投影的位置。 */
private val STAGE_PADDING = 10.dp

/** 面板预览上左右滑动换尺寸的位移阈值：轻扫一下不会误触，明确滑一段才切。 */
private val SIZE_SWIPE_THRESHOLD = 20.dp

/** 面板里尺寸分页圆点的直径与间距：只做「这里能翻页」的指示，能点着选的仍是尺寸胶囊。 */
private val SIZE_DOT_SIZE = 6.dp
private val SIZE_DOT_GAP = 6.dp

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

/** 面板里大预览的测试标签。 */
internal const val SHEET_PREVIEW_TAG = "widget_pin_sheet_preview"

/** 面板里添加按钮的测试标签。 */
internal const val SHEET_ADD_TAG = "widget_pin_sheet_add"

/** 面板里尺寸切换行的测试标签（单尺寸组件不显示这一行）。 */
internal const val SHEET_SIZE_ROW_TAG = "widget_pin_sheet_size_row"

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
 * 名称 + 说明两行居中 + 大预览（按桌面真实尺寸浮在面板上，左右滑动换尺寸、下缘一排分页圆点）
 * + 尺寸胶囊 + 透明度档位 + 添加按钮，当场选当场落桌；卡上的「＋」不走面板，
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
    val isMiui = remember { WidgetPin.isMiuiHome(context) }

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
                pinSupported = snapshot.pinSupported, isMiui = isMiui, pinOk = snapshot.pinOk
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
    AppBottomSheet(onDismissRequest = onDismiss, heightFraction = SHEET_HEIGHT_FRACTION) {
        WidgetPinSheetContent(
            component = component,
            selected = selectedEntry(component, selectedIndex),
            bound = snapshot.boundCountOf(component),
            pinSupported = snapshot.pinSupported,
            verifying = verifying,
            opacity = opacity,
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
 * 面板内容：头部（名称 / 桌面已有 N 个 / 说明，居中一叠）+ 大预览（**吃掉中间余量**，
 * 下缘一排尺寸分页圆点）+ 尺寸胶囊 + 透明度档位 + 添加按钮（抽出来是为了能单独出图）。
 * 高度由 [AppBottomSheet] 的 height 定住，预览用 weight 占住中间那块空档 —— 面板是「近全高」的。
 */
@Composable
internal fun WidgetPinSheetContent(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    bound: Int,
    pinSupported: Boolean,
    verifying: Boolean,
    opacity: Float?,
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
        SheetHeader(component.titleRes, component.descRes)
        // 预览块吃掉中间余量：widget 与它下面的分页圆点作为**一组**居中 ——
        // 圆点是「这一页能翻」的指示，必须贴着 widget，不能被甩到面板底部去
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SheetPreview(
                component = component,
                selected = selected,
                onSelectSize = onSelectSize,
                // fill = false：这块只占 widget 自己的高度，剩下的余量由上面这层居中分配
                modifier = Modifier.weight(1f, fill = false)
            )
            // 只有一个尺寸的组件不摆尺寸行：一颗孤零零的胶囊看着像坏了
            if (component.entries.size > 1) {
                SizeDots(
                    count = component.entries.size,
                    selectedIndex = component.entries.indexOf(selected),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        if (component.entries.size > 1) {
            SizeChipRow(component = component, selected = selected, onSelectSize = onSelectSize)
        }
        // 带配置页的组件（账单）落桌后由系统拉起配置页，那里也管透明度，这里就不再重复一档
        if (component.configureActivity == null) {
            OpacityRow(selected = opacity, onSelect = onSelectOpacity)
        }
        SheetAddButton(bound, pinSupported, verifying, onAdd)
    }
}

/** 面板头部：名称 + 说明两行居中一叠（各家「添加小部件」的面板也是这两行，实例数由画廊卡说）。 */
@Composable
private fun SheetHeader(titleRes: Int, descRes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
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
 * 胶囊文字比 M3 默认略小一档：透明度五档要在一行里排得下，不靠横向滚动去找最后一档。
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
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options().forEach { option ->
                FilterChip(
                    selected = option.selected,
                    onClick = option.onClick,
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
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
 * 面板里的大预览：左右滑动就换到下一个 / 上一个尺寸（各家「卡片预览页」都是这个手势），
 * 与点尺寸胶囊等效 —— 大预览是面板里最大的一块，滑它比去够下面那排胶囊顺手。
 * 位移要超过 [SIZE_SWIPE_THRESHOLD] 才算切换，轻轻一碰不会跳；到两端就不动。
 * 高度只包住 widget 自己（由调用方的 weight 给上限），面板里的余量交给外层居中分配。
 */
@Composable
private fun SheetPreview(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    onSelectSize: (WidgetPin.Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    val threshold = with(LocalDensity.current) { SIZE_SWIPE_THRESHOLD.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 手势跟着当前尺寸重启（key 含 selected），否则闭包里的 selected 会是旧的、切错方向
            .pointerInput(component.familyKey, selected) {
                // 本次手势的累计位移：只放本地变量，拖动过程不写状态、不触发重组
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragCancel = { dragged = 0f },
                    onDragEnd = {
                        val target = when {
                            dragged <= -threshold -> component.entries.swipeTarget(selected, forward = true)
                            dragged >= threshold -> component.entries.swipeTarget(selected, forward = false)
                            else -> null
                        }
                        dragged = 0f
                        target?.let(onSelectSize)
                    }
                ) { _, amount -> dragged += amount }
            }
    ) {
        WidgetPreview(
            imageRes = selected.previewImageRes,
            designSize = WidgetPin.designSizeOf(selected),
            stage = false,
            tag = SHEET_PREVIEW_TAG,
            // 只铺满宽、高包住图片自己：外层的 weight(fill = false) 才能靠它算高度
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 滑动要落到的尺寸：左滑取下一个、右滑取上一个；已在两端给 null（不动）。 */
private fun List<WidgetPin.Entry>.swipeTarget(current: WidgetPin.Entry, forward: Boolean): WidgetPin.Entry? {
    val index = indexOf(current)
    if (index < 0) return null
    return getOrNull(if (forward) index + 1 else index - 1)
}

/** 面板里的添加按钮：核对期转「确认中…」并禁用，防连发确认框。 */
@Composable
private fun SheetAddButton(bound: Int, pinSupported: Boolean, verifying: Boolean, onAdd: () -> Unit) {
    Button(
        onClick = onAdd,
        enabled = pinSupported && !verifying,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SHEET_ADD_TAG)
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

/**
 * 尺寸分页圆点：预览下面一排小点，标记当前是第几个尺寸（各家卡片预览页的分页指示）。
 * 纯视觉 —— 真要点着选尺寸是下面那排胶囊，读屏也只读胶囊，这里不重复播报。
 * 选中那颗用前景色（各家分页点都是深浅灰，不拿品牌色去点）。
 */
@Composable
private fun SizeDots(count: Int, selectedIndex: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.testTag(SHEET_DOTS_TAG),
        horizontalArrangement = Arrangement.spacedBy(SIZE_DOT_GAP)
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(SIZE_DOT_SIZE)
                    .clip(CircleShape)
                    .background(
                        if (index == selectedIndex) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                    .testTag(SHEET_DOT_TAG)
            )
        }
    }
}

/** 面板里尺寸切换行：标题 + 一排胶囊，横向可滚（习惯有 4 个尺寸）。 */
@Composable
private fun SizeChipRow(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    onSelectSize: (WidgetPin.Entry) -> Unit
) {
    PickerRow(
        label = stringResource(R.string.widget_pin_choose_size),
        tag = SHEET_SIZE_ROW_TAG,
        options = {
            component.entries.map { entry ->
                PickerOption(
                    label = WidgetPin.cellsOf(entry),
                    selected = entry == selected,
                    onClick = { onSelectSize(entry) }
                )
            }
        }
    )
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
