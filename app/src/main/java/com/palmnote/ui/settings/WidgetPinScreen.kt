package com.palmnote.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.palmnote.app.R
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.widget.WidgetPin
import kotlinx.coroutines.launch

/** 卡片里预览舞台的固定高度：所有卡片同高，列表整齐好扫。 */
private val PREVIEW_HEIGHT = 110.dp

/**
 * 全目录统一的缩放基准：设计稿里最大的卡（4×3 = 340×268dp，见 design-v4.html 的 `.s43`）。
 * 所有卡片乘同一个系数 —— 1×1 是小方块、2×1 是矮条、4×3 是大块，相对大小等于桌面占格比例。
 */
private const val DESIGN_MAX_WIDTH = 340f
private const val DESIGN_MAX_HEIGHT = 268f

/** 组件没登记设计尺寸时的兜底：按 3×2 卡摆。 */
private val FALLBACK_SIZE = 245 to 148

/** 预览图的测试标签：单测靠它量 dp 尺寸，锁住「不随屏幕密度变」。 */
internal const val PREVIEW_IMAGE_TAG = "widget_preview_image"

/** 组件卡的测试标签：单测靠它定位首卡、验证分组与尺寸切换。 */
internal const val CATALOG_CELL_TAG = "widget_catalog_cell"

/** 目录列表的测试标签：卡内还有横向滚动的尺寸胶囊，滚动断言得点名这个纵向列表。 */
internal const val CATALOG_LIST_TAG = "widget_catalog_list"

/** 搜索框的测试标签。 */
internal const val SEARCH_FIELD_TAG = "widget_search_field"

/**
 * 添加页：**只在应用内一键添加**（`requestPinAppWidget`），不讲系统的手动添加路径。
 *
 * 一屏完成「选组件 → 选尺寸 → 添加」：
 * - 首屏是按用途分组的组件列表（记账 / 生活 / 效率 / 资产 / 其他），每张卡 = 一个大预览
 *   + 名称 + 说明 + 尺寸切换胶囊 + 添加按钮；
 * - 同组件的多个尺寸形态合并进一张卡，点尺寸胶囊预览实时切换（不再退回目录找另一个格子）；
 * - 添加按钮直接在卡上，点一下就落桌，不再进详情页；
 * - 顶部搜索框按名称过滤。
 */
@Composable
fun WidgetPinScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { WidgetPin.catalog() }
    val isMiui = remember { WidgetPin.isMiuiHome(context) }

    var snapshot by remember { mutableStateOf(WidgetPin.snapshot(context)) }
    // 落桌核对期（最多 5×1.5s）锁住按钮：不锁的话连点会连发多个系统确认框
    var verifying by remember { mutableStateOf(false) }
    // 每个组件当前选中的尺寸（族键 → entries 下标），默认 0 = 基类形态
    var selectedIndex by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var query by remember { mutableStateOf("") }

    // 从系统确认框回来时（ON_RESUME）重数一遍桌面实例，把「已添加 N 个」刷新到最新
    RefreshSnapshotOnResume { snapshot = WidgetPin.snapshot(context) }

    // 点了「添加」不立刻报结果：requestPinAppWidget 的返回值只代表「这个桌面支持应用内添加」，
    // 桌面缺少相关权限时会静默失败（请求被吞、桌面什么都没有），照着返回值报成功就是假消息。
    // 所以等几秒重新数一遍桌面实例，数到了才算成了，没数到就如实说没成功。
    // 核对期间按钮转「确认中…」并禁用 —— 不然连点会连发多个系统确认框。
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

    Scaffold(topBar = { WidgetPinTopBar(onNavigateBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            WidgetSearchField(query = query, onQueryChange = { query = it })
            CatalogList(
                modifier = Modifier.weight(1f),
                catalog = catalog,
                query = query,
                selectedIndex = selectedIndex,
                snapshot = snapshot,
                verifying = verifying,
                onSelectSize = { familyKey, index ->
                    selectedIndex = selectedIndex.toMutableMap().apply { put(familyKey, index) }
                },
                onAdd = { component ->
                    val selected = selectedEntry(component, selectedIndex)
                    addWidget(selected, snapshot.boundCount(selected))
                }
            )
            PermissionStrip(
                pinSupported = snapshot.pinSupported,
                isMiui = isMiui,
                pinOk = snapshot.pinOk
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 顶栏：标题 + 返回。 */
@Composable
private fun WidgetPinTopBar(onNavigateBack: () -> Unit) {
    CompactTopAppBar(
        title = stringResource(R.string.settings_widget_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_navigate_back)
                )
            }
        }
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

/** 顶部搜索框：按组件名称过滤（分组浏览时隐藏空组）。 */
@Composable
private fun WidgetSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.widget_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(SEARCH_FIELD_TAG)
    )
}

/**
 * 组件目录：无搜索词时按分组列出（组标题 + 组件卡），有搜索词时拍平成匹配结果。
 * 两种呈现共用同一张卡 [CatalogCardItem]。
 */
@Composable
private fun CatalogList(
    modifier: Modifier,
    catalog: List<WidgetPin.CatalogGroup>,
    query: String,
    selectedIndex: Map<Int, Int>,
    snapshot: WidgetPin.Snapshot,
    verifying: Boolean,
    onSelectSize: (familyKey: Int, index: Int) -> Unit,
    onAdd: (WidgetPin.CatalogComponent) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CATALOG_LIST_TAG),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (query.isBlank()) {
            catalog.forEach { group ->
                item(key = "h_${group.group.name}") { GroupHeader(group.group) }
                items(group.components, key = { "c_${it.familyKey}" }) { component ->
                    CatalogCardItem(component, selectedIndex, snapshot, verifying, onSelectSize, onAdd)
                }
            }
        } else {
            // 过滤在普通 lambda 里做，用 context.getString（stringResource 是 @Composable，不能在这用）
            val results = catalog
                .flatMap { it.components }
                .filter { context.getString(it.titleRes).contains(query, ignoreCase = true) }
            items(results, key = { "s_${it.familyKey}" }) { component ->
                CatalogCardItem(component, selectedIndex, snapshot, verifying, onSelectSize, onAdd)
            }
        }
    }
}

/** 单张组件卡：从状态里取当前选中尺寸与桌面实例数，交给 [ComponentCard] 渲染。 */
@Composable
private fun CatalogCardItem(
    component: WidgetPin.CatalogComponent,
    selectedIndex: Map<Int, Int>,
    snapshot: WidgetPin.Snapshot,
    verifying: Boolean,
    onSelectSize: (familyKey: Int, index: Int) -> Unit,
    onAdd: (WidgetPin.CatalogComponent) -> Unit
) {
    ComponentCard(
        component = component,
        selected = selectedEntry(component, selectedIndex),
        bound = snapshot.boundCountOf(component),
        pinSupported = snapshot.pinSupported,
        verifying = verifying,
        onSelectSize = { entry -> onSelectSize(component.familyKey, component.entries.indexOf(entry)) },
        onAdd = { onAdd(component) }
    )
}

/** 组件当前选中的尺寸形态：优先用户选过，否则基类（entries 首位）。 */
private fun selectedEntry(
    component: WidgetPin.CatalogComponent,
    selectedIndex: Map<Int, Int>
): WidgetPin.Entry = component.entries.getOrElse(selectedIndex[component.familyKey] ?: 0) {
    component.entries.first()
}

/** 分组标题。 */
@Composable
private fun GroupHeader(group: WidgetPin.WidgetGroup) {
    Text(
        stringResource(group.titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

/**
 * 组件卡：大预览（随选中尺寸切换）+ 名称 + 说明 + 尺寸胶囊 + 添加按钮。
 * 读屏把整卡合成一条：名称、尺寸、已添加一起给，图与卡本身无从辨认。
 */
@Composable
private fun ComponentCard(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    bound: Int,
    pinSupported: Boolean,
    verifying: Boolean,
    onSelectSize: (WidgetPin.Entry) -> Unit,
    onAdd: () -> Unit
) {
    // 读屏描述要在 Compose 作用域里先取好字符串，再交给 semantics（其 lambda 不是 Compose 作用域）
    val cellDesc = listOfNotNull(
        stringResource(component.titleRes),
        component.cells.joinToString("/"),
        if (bound > 0) stringResource(R.string.widget_pin_count, bound) else null
    ).joinToString("，")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CATALOG_CELL_TAG)
            .semantics { contentDescription = cellDesc }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            CardPreview(selected)
            CardTitleRow(component.titleRes, bound)
            Text(
                stringResource(component.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            CardSizeRow(component, selected, onSelectSize)
            CardAddButton(bound, pinSupported, verifying, onAdd)
        }
    }
}

/** 卡内预览舞台：固定高度 + 居中，随选中尺寸换图（整卡已有合成读屏描述，图不再单独念）。 */
@Composable
private fun CardPreview(selected: WidgetPin.Entry) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        PreviewImage(
            imageRes = selected.previewImageRes,
            designSize = WidgetPin.designSizeOf(selected),
            contentDescription = null
        )
    }
}

/** 卡内名称行：名称（左）+「已添加 N」（右，仅已添加时）。 */
@Composable
private fun CardTitleRow(titleRes: Int, bound: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (bound > 0) {
            Text(
                stringResource(R.string.widget_pin_count, bound),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 卡内尺寸切换行：每个形态一颗胶囊，横向可滚（习惯有 4 个尺寸）。 */
@Composable
private fun CardSizeRow(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    onSelectSize: (WidgetPin.Entry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        component.entries.forEach { entry ->
            SizeChip(WidgetPin.cellsOf(entry), entry == selected) { onSelectSize(entry) }
        }
    }
}

/** 卡内添加按钮：核对期转「确认中…」并禁用，防连发确认框。 */
@Composable
private fun CardAddButton(bound: Int, pinSupported: Boolean, verifying: Boolean, onAdd: () -> Unit) {
    Button(
        onClick = onAdd,
        enabled = pinSupported && !verifying,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
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

/** 尺寸胶囊：选中态用主色填充，未选中用中性底。 */
@Composable
private fun SizeChip(cells: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            cells,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
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
