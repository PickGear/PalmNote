package com.palmnote.ui.life

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.ui.components.CompactTopAppBar
import androidx.activity.compose.BackHandler
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.Warning
import java.time.LocalDate
import java.time.YearMonth
import java.util.*
import kotlinx.coroutines.delay

/** 格子档位（§4.1 五档）。 */
private enum class LifeSpan { XS, S, M, XL, L }

private val LifeSpan.spanCols: Int
    get() = when (this) {
        LifeSpan.XS -> 1
        LifeSpan.S -> 2
        LifeSpan.M -> 2
        LifeSpan.XL -> 3
        LifeSpan.L -> 3
    }

private val LifeSpan.cardHeight: Dp
    get() = when (this) {
        LifeSpan.XS -> 104.dp
        LifeSpan.S -> 104.dp
        LifeSpan.L -> 36.dp
        LifeSpan.M -> 216.dp
        LifeSpan.XL -> 216.dp
    }

private enum class LifeCardKind { TODO, NUMBER, PROGRESS, TEXT, INVITE }

private data class LifeSampleCard(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val iconKey: String,
    val accentHex: String,
    val span: LifeSpan,
    val kind: LifeCardKind,
    val protagonist: Boolean = false,
    val primary: String = "",
    val secondary: String = "",
    val progress: Float? = null,
    val warning: Boolean = false
)

private data class FabIntent(
    val label: String,
    val icon: ImageVector,
    val iconKey: String,
    val colorHex: String
)

/** 虚线描边（§4.3 邀请条）。 */
private fun Modifier.dashedBorder(
    strokeWidth: Dp,
    color: Color,
    cornerRadius: Dp
): Modifier = this.drawWithContent {
    drawContent()
    val sw = strokeWidth.toPx()
    val r = cornerRadius.toPx()
    val path = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    }
    drawPath(
        path, color,
        style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f))
    )
}

private fun lifeIdentityColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

@Composable
fun LifeScreen(
    onOpenDetail: (title: String, iconKey: String, accentHex: String, heroLabel: String, heroValue: String) -> Unit,
    onOpenList: (title: String, subtitle: String) -> Unit,
    onOpenDay: (dayLabel: String) -> Unit,
    onCrossTab: (Any) -> Unit,
    onOpenStats: () -> Unit,
    defaultView: String = LifeViewKey.TODAY,
    onSetDefaultView: (String) -> Unit = {}
) {
    var view by remember { mutableStateOf(lifeViewOf(defaultView)) }
    // 默认页由 DataStore 异步载入（或长按改默认后回填），视图跟随落位
    LaunchedEffect(defaultView) { view = lifeViewOf(defaultView) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var fabPanelOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val openSearch = { showSearch = true }
    val closeSearch = { showSearch = false; searchQuery = "" }
    BackHandler(enabled = showSearch) { closeSearch() }

    // 左上角标题即视图切换器：点击循环「生活→日历→全部」，长按震动并把当前视图设为默认页
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cycleView = {
        selectedDay = null
        view = view.next
    }
    val setDefaultView = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSetDefaultView(view.prefKey)
        Toast.makeText(context, R.string.life_view_default_set, Toast.LENGTH_SHORT).show()
    }

    // 页面指示器：平时隐藏，切换视图（及首次进入，兼当首次引导）时浮现 2 秒后淡出
    var indicatorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(view, showSearch) {
        if (showSearch) {
            indicatorVisible = false
        } else {
            indicatorVisible = true
            delay(2000)
            indicatorVisible = false
        }
    }

    // 用 Scaffold 包裹：FAB 放回 floatingActionButton 槽（与其他主页面、旧版位置一致）。
    // 顶栏高度与记账/资产/主页一致（同一 CompactTopAppBar：statusBars + 内容行，M3 源码确认
    // topBar 存在时 innerPadding.top = topBarHeight，不叠加 statusBars）。
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
            topBar = {
                LifeTopBar(
                    showSearch = showSearch,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    onCancelSearch = closeSearch,
                    onOpenStats = onOpenStats,
                    onOpenSearch = openSearch,
                    onOpenManage = { onOpenList("模板管理", "生活模板与卡片") },
                    view = view,
                    onCycleView = cycleView,
                    onLongPressTitle = setDefaultView
                )
            },
            floatingActionButton = {
                LifeFab(open = fabPanelOpen, onToggle = { fabPanelOpen = it })
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (showSearch) {
                    // 页内搜索（§旧版：ModuleSearchBar 浮层 + LifeSearchContent，不跳 App 全局搜索）
                    LifeSearchContent(
                        query = searchQuery,
                        onOpenDetail = onOpenDetail
                    )
                } else {
                    // 顶栏下直接铺内容（与其他主页面间距一致）
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (view) {
                            LifeView.TODAY -> GridBoard(
                                modifier = Modifier.fillMaxSize(),
                                onOpenDetail = onOpenDetail,
                                onOpenList = onOpenList
                            )
                            LifeView.CALENDAR -> MonthCalendar(
                                modifier = Modifier.fillMaxSize(),
                                selectedDay = selectedDay,
                                onSelectDay = { selectedDay = it },
                                onOpenDay = onOpenDay
                            )
                            LifeView.ALL -> AllFlow(
                                modifier = Modifier.fillMaxSize(),
                                onOpenDetail = onOpenDetail
                            )
                        }

                        // 视图指示器（底部中间悬浮药丸）：切换/首次进入时浮现、2 秒后淡出。
                        // 放底部中间的原因：顶部是主角卡（不能挡），底部多为滚动收尾空白；
                        // 短暂切换反馈的惯例位置也是底部中间（snackbar 位）。FAB 在右下，中心不冲突。
                        // 显式调顶层 AnimatedVisibility：处于 Column 作用域内会被误解析成 ColumnScope 扩展。
                        androidx.compose.animation.AnimatedVisibility(
                            visible = indicatorVisible,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(500)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        ) {
                            LifeViewIndicator(
                                current = view,
                                onSelect = {
                                    selectedDay = null
                                    view = it
                                }
                            )
                        }
                    }
                }
            }
        }

        // 底部面板（窗口级 ModalBottomSheet，盖住底部导航栏）；FAB 本体已进 Scaffold 槽，
        // 弹窗打开时被遮罩盖住属标准行为，关时立即可见（无 AnimatedVisibility，不再短暂消失）。
        FabPanel(
            open = fabPanelOpen,
            onToggle = { fabPanelOpen = it },
            onIntent = { intent ->
                fabPanelOpen = false
                onOpenDetail(intent.label, intent.iconKey, intent.colorHex, intent.label, "")
            }
        )
    }
}

private enum class LifeView { TODAY, CALENDAR, ALL }

/** 视图持久化 key（与 PreferencesManager.LIFE_HOME_VIEW 一致）。 */
internal object LifeViewKey {
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val ALL = "all"
}

/** 存储值 → 视图（未知值回退今天）。 */
private fun lifeViewOf(key: String): LifeView = when (key) {
    LifeViewKey.CALENDAR -> LifeView.CALENDAR
    LifeViewKey.ALL -> LifeView.ALL
    else -> LifeView.TODAY
}

/** 点击标题的循环顺序：生活(今天) → 日历 → 全部 → 生活(今天)。 */
private val LifeView.next: LifeView
    get() = when (this) {
        LifeView.TODAY -> LifeView.CALENDAR
        LifeView.CALENDAR -> LifeView.ALL
        LifeView.ALL -> LifeView.TODAY
    }

/** 标题文字：今天视图沿用模块名「生活」，其余显示视图名。 */
private val LifeView.titleRes: Int
    get() = when (this) {
        LifeView.TODAY -> R.string.nav_life
        LifeView.CALENDAR -> R.string.life_view_calendar
        LifeView.ALL -> R.string.life_view_board
    }

/** 视图 → 持久化 key。 */
private val LifeView.prefKey: String
    get() = when (this) {
        LifeView.TODAY -> LifeViewKey.TODAY
        LifeView.CALENDAR -> LifeViewKey.CALENDAR
        LifeView.ALL -> LifeViewKey.ALL
    }

@Composable
private fun LifeTopBar(
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onCancelSearch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenManage: () -> Unit,
    view: LifeView,
    onCycleView: () -> Unit,
    onLongPressTitle: () -> Unit
) {
    CompactTopAppBar(
        title = {
            if (showSearch) {
                ModuleSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClear = onClear,
                    placeholder = stringResource(R.string.search),
                    autoFocus = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // 标题即视图切换器：点击循环视图，长按把当前视图设为默认页
                //（视图指示器为底部中间的悬浮药丸，见 LifeScreen 内容区）
                Text(
                    stringResource(view.titleRes),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = ModuleLife,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = onCycleView, onLongClick = onLongPressTitle)
                )
            }
        },
        actions = {
            if (showSearch) {
                TextButton(onClick = onCancelSearch, modifier = Modifier.padding(end = 4.dp)) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                IconButton(onClick = onOpenStats) {
                    Icon(
                        Icons.Outlined.BarChart,
                        stringResource(R.string.life_home_stats),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Outlined.Search,
                        stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenManage) {
                    Icon(
                        Icons.Outlined.GridView,
                        stringResource(R.string.life_template_manage),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun LifeViewIndicator(current: LifeView, onSelect: (LifeView) -> Unit) {
    // 三视图指示器（底部中间悬浮药丸）：当前视图圆点放大 + 主题色，其余灰点；可点直达。
    // 药丸底 + 描边 + 阴影保证浮在卡片内容上时依然清晰。
    // iPhone 风格扁胶囊：高度压到 28dp（20dp 圆点热区 + 上下各 4dp），端部全圆。
    // 不用 shadowElevation：Android 的 elevation 投影不随透明度淡出，消失动画时
    // 本体已渐隐而阴影仍全浓，看起来像"下边被遮挡"；改用描边 + surface 底区分背景。
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LifeView.entries.forEach { v ->
                val selected = v == current
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(v) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) ModuleLife else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun LifeSearchContent(
    query: String,
    onOpenDetail: (title: String, iconKey: String, accentHex: String, heroLabel: String, heroValue: String) -> Unit
) {
    // 第一阶段：页内搜索静态骨架（无业务逻辑 / 后端），仅演示搜索框与结果布局
    val samples = listOf(
        Triple("写日记", "book", "#7E57C2"),
        Triple("今日待办", "checklist", "#5C6BC0"),
        Triple("健身打卡", "fitness_center", "#FF7043")
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (query.isBlank()) {
            Spacer(Modifier.height(48.dp))
            Text(
                "搜索生活记录、模板、日记…",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            samples.forEach { (title, iconKey, colorHex) ->
                val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrNull() ?: ModuleLife
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenDetail(title, iconKey, colorHex, title, "") }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = color.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(iconFor(iconKey), null, tint = color, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ───────────────────────── 今天：格子板 ─────────────────────────

@Composable
private fun GridBoard(
    modifier: Modifier,
    onOpenDetail: (title: String, iconKey: String, accentHex: String, heroLabel: String, heroValue: String) -> Unit,
    onOpenList: (title: String, subtitle: String) -> Unit
) {
    val cards = remember {
        listOf(
            LifeSampleCard("todo", "今天要做什么", Icons.Filled.CheckBox, "checklist", "#5C6BC0", LifeSpan.M, LifeCardKind.TODO, protagonist = true,
                primary = "3 项待办", secondary = "买菜 · 回邮件 · 取快递"),
            LifeSampleCard("savings", "存钱计划", Icons.Filled.Savings, "savings", "#EC407A", LifeSpan.XS, LifeCardKind.PROGRESS,
                primary = "¥6,800", secondary = "目标 ¥10,000", progress = 0.68f),
            LifeSampleCard("checkin", "打卡", Icons.Filled.CheckCircle, "calendar_month", "#3F51B5", LifeSpan.XS, LifeCardKind.PROGRESS,
                primary = "18 天", secondary = "连续打卡", progress = 0.8f),
            LifeSampleCard("countdown", "倒计时", Icons.Filled.HourglassBottom, "timer_off", "#FFCA28", LifeSpan.XS, LifeCardKind.NUMBER,
                primary = "12 天", secondary = "距旅行"),
            LifeSampleCard("anniversary", "纪念日", Icons.Filled.Cake, "cake", "#F06292", LifeSpan.XS, LifeCardKind.NUMBER,
                primary = "还有 3 天", secondary = "结婚纪念日", warning = true),
            LifeSampleCard("focus", "专注", Icons.Filled.Timer, "timer", "#00ACC1", LifeSpan.XS, LifeCardKind.NUMBER,
                primary = "42 分钟", secondary = "今日专注"),
            LifeSampleCard("record", "今天记了什么", Icons.Filled.EventNote, "book", "#7E57C2", LifeSpan.S, LifeCardKind.TEXT,
                primary = "下午去了健身房", secondary = "身体记录 · 14:30"),
            LifeSampleCard("mood", "今天心情", Icons.Filled.SentimentSatisfied, "mood", "#FFA726", LifeSpan.S, LifeCardKind.TEXT,
                primary = "不错", secondary = "平稳的一天"),
            LifeSampleCard("invite", "还没写日记", Icons.Filled.EditNote, "", "#000000", LifeSpan.L, LifeCardKind.INVITE,
                primary = "记一条 →", secondary = "")
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(cards, key = { it.key }, span = { GridItemSpan(it.span.spanCols) }) { card ->
            Box(Modifier.height(card.span.cardHeight)) {
                GridCard(card = card, onClick = {
                    if (card.kind == LifeCardKind.INVITE) {
                        onOpenDetail("写日记", "book", "#7E57C2", "日记", "记一笔")
                    } else if (card.span == LifeSpan.M || card.span == LifeSpan.XL) {
                        onOpenDetail(card.title, card.iconKey, card.accentHex, card.secondary, card.primary)
                    } else {
                        onOpenList(card.title, card.secondary)
                    }
                })
            }
        }
        item(span = { GridItemSpan(3) }) { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun GridCard(card: LifeSampleCard, onClick: () -> Unit) {
    val accent = lifeIdentityColor(card.accentHex)

    if (card.kind == LifeCardKind.INVITE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dashedBorder(1.dp, MaterialTheme.colorScheme.outlineVariant, 16.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable { onClick() }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(card.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ModuleLife)
            }
        }
        return
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(card.icon, null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    card.title, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            when (card.kind) {
                LifeCardKind.NUMBER -> {
                    Text(card.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (card.warning) Warning else MaterialTheme.colorScheme.onSurface)
                    Text(card.secondary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                LifeCardKind.PROGRESS -> {
                    Text(card.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { card.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = accent, trackColor = accent.copy(alpha = 0.15f)
                    )
                    Text(card.secondary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                LifeCardKind.TEXT -> {
                    Text(card.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(card.secondary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                LifeCardKind.TODO -> {
                    // 待办清单：可就地勾选。Phase 1 为静态页，勾选只改本地状态（不落库），
                    // 真实读写等数据层接入后再替换。
                    val todos = remember(card.key) {
                        mutableStateListOf<Pair<String, Boolean>>().apply {
                            card.secondary.split(" · ").filter { it.isNotBlank() }.forEach { add(it to false) }
                        }
                    }
                    todos.take(3).forEachIndexed { index, item ->
                        val done = item.second
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { todos[index] = item.first to !done }
                                .padding(vertical = 3.dp)
                        ) {
                            Icon(
                                if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (done) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                item.first,
                                fontSize = 13.sp,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (done) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (todos.size > 3) {
                        Text(
                            stringResource(R.string.life_hero_more_items, todos.size - 3),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ModuleLife,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

// ───────────────────────── 日历：月格 ─────────────────────────

@Composable
private fun MonthCalendar(
    modifier: Modifier,
    selectedDay: Int?,
    onSelectDay: (Int) -> Unit,
    onOpenDay: (String) -> Unit
) {
    val ym = remember { YearMonth.now() }
    val days = ym.lengthOfMonth()
    val leading = (ym.atDay(1).dayOfWeek.value - 1).coerceAtLeast(0)
    val today = LocalDate.now().dayOfMonth

    val density = remember {
        mapOf(3 to 3, 4 to 5, 5 to 9, 6 to 2, 10 to 6, 11 to 4, 12 to 2, 15 to 7, 16 to 3, 18 to 1, 19 to 5, 20 to 2, 22 to 4, 25 to 8, 26 to 3, 28 to 1)
    }
    val dots = remember {
        mapOf(4 to listOf(Color(0xFF50C890), Color(0xFF7C8CF0)), 5 to listOf(Color(0xFFF07070)),
            10 to listOf(Color(0xFF50C890), Color(0xFF7C8CF0), Color(0xFFF07070)), 19 to listOf(Color(0xFF50C890)), 25 to listOf(Color(0xFF7C8CF0), Color(0xFFF07070)))
    }
    val monthTotal = 42

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        item(span = { GridItemSpan(7) }) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${ym.year} 年 ${ym.monthValue} 月", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
        item(span = { GridItemSpan(7) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(List(leading) { null } + (1..days).toList()) { day ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (day == null) Modifier else Modifier
                            .background(cellTint(density[day] ?: 0))
                            .then(if (day == selectedDay || day == today) Modifier.border(1.5.dp, ModuleLife, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable { onSelectDay(day); onOpenDay("${ym.monthValue}月${day}日") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (day != null) {
                    Text("$day", fontSize = 12.sp, color = if (day == today) ModuleLife else MaterialTheme.colorScheme.onSurface)
                    val d = dots[day]
                    if (!d.isNullOrEmpty()) {
                        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                            d.take(3).forEach { c ->
                                Box(Modifier.size(4.dp).clip(CircleShape).background(c))
                                Spacer(Modifier.width(2.dp))
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(7) }) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("本月 $monthTotal 条", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("点一天看那天 →", fontSize = 12.sp, color = ModuleLife)
            }
        }
        item(span = { GridItemSpan(7) }) { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun cellTint(count: Int): Color {
    val alpha = when {
        count <= 0 -> 0f
        count <= 2 -> 0.22f
        count <= 4 -> 0.42f
        count <= 7 -> 0.62f
        else -> 0.80f
    }
    return ModuleLife.copy(alpha = alpha)
}

// ───────────────────────── 全部：内容流 ─────────────────────────

@Composable
private fun AllFlow(
    modifier: Modifier,
    onOpenDetail: (title: String, iconKey: String, accentHex: String, heroLabel: String, heroValue: String) -> Unit
) {
    var chip by remember { mutableStateOf(0) }
    val chips = listOf("全部", "记录", "计划", "目标", "纪念")
    val items = remember {
        listOf(
            FlowItem("购物清单", "shopping_cart", "#FF7043", "¥1,280", "3 件商品 · 今天"),
            FlowItem("晨跑", "fitness_center", "#00897B", "5.2 km", "身体记录 · 07:10"),
            FlowItem("读书笔记", "menu_book", "#26A69A", "第 42 页", "远方与成长 · 昨天"),
            FlowItem("旅行计划", "flight", "#66BB6A", "12 天", "距出发"),
            FlowItem("周报", "BarChart", "#42A5F5", "本周概览", "系统 · 周一")
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEachIndexed { i, c ->
                        val sel = i == chip
                        Surface(color = if (sel) ModuleLife else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.clickable { chip = i }) {
                            Text(c, fontSize = 12.sp, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
        items(items, key = { it.title }) { it ->
            FlowCard(it) { onOpenDetail(it.title, it.iconKey, it.accentHex, it.sub, it.primary) }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

private data class FlowItem(val title: String, val iconKey: String, val accentHex: String, val primary: String, val sub: String)

@Composable
private fun FlowCard(item: FlowItem, onClick: () -> Unit) {
    val accent = lifeIdentityColor(item.accentHex)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Icon(iconFor(item.iconKey), null, tint = accent, modifier = Modifier.size(22.dp).wrapContentSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(item.sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

internal fun iconFor(key: String): ImageVector = when (key) {
    "checklist", "note_add" -> Icons.Filled.CheckBox
    "savings" -> Icons.Filled.Savings
    "calendar_month" -> Icons.Filled.CalendarMonth
    "timer_off", "timer" -> Icons.Filled.Timer
    "cake" -> Icons.Filled.Cake
    "book", "mood" -> Icons.Filled.Book
    "shopping_cart" -> Icons.Filled.ShoppingCart
    "fitness_center" -> Icons.Filled.FitnessCenter
    "menu_book" -> Icons.Filled.MenuBook
    "flight" -> Icons.Filled.Flight
    "BarChart" -> Icons.Filled.BarChart
    else -> Icons.Filled.Circle
}

// ───────────────────────── FAB + 长按面板（§7.1） ─────────────────────────

// FAB 本体：56dp 圆 + ModuleLife（旧版样式），由 LifeScreen 的 Scaffold.floatingActionButton 槽承载，
// 位置与其他主页面、旧版一致。单击切换面板、长按直接打开面板（沿用此前用户决定）。
@Composable
private fun LifeFab(
    open: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        color = ModuleLife, shape = CircleShape, shadowElevation = 4.dp,
        modifier = Modifier
            .size(56.dp)
            .combinedClickable(onClick = { onToggle(!open) }, onLongClick = { onToggle(true) })
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

// 底部面板（窗口级 ModalBottomSheet，盖住底部导航栏）
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FabPanel(
    open: Boolean,
    onToggle: (Boolean) -> Unit,
    onIntent: (FabIntent) -> Unit
) {
    if (!open) return
    val intents = listOf(
        FabIntent("心情", Icons.Filled.SentimentSatisfied, "mood", "#FFCA28"),
        FabIntent("日记", Icons.Filled.Book, "book", "#AB47BC"),
        FabIntent("打卡", Icons.Filled.CheckCircle, "checklist", "#FF7043"),
        FabIntent("专注", Icons.Filled.Timer, "timer", "#00ACC1"),
        FabIntent("待办", Icons.Filled.CheckBox, "checklist", "#5C6BC0"),
        FabIntent("计划", Icons.Filled.Event, "calendar_month", "#EC407A"),
        FabIntent("纪念日", Icons.Filled.Cake, "cake", "#F07070"),
        FabIntent("订阅", Icons.Filled.Subscriptions, "subscriptions", "#66BB6A")
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onToggle(false) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text("记录什么", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            intents.chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { intent -> FabIntentButton(intent) { onIntent(intent) } }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun FabIntentButton(intent: FabIntent, onClick: () -> Unit) {
    val color = lifeIdentityColor(intent.colorHex)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(color = color.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
            Icon(intent.icon, null, tint = color, modifier = Modifier.size(24.dp).wrapContentSize())
        }
        Spacer(Modifier.height(5.dp))
        Text(intent.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
