package com.palmnote.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** 取不到图片比例时的兜底（组件预览图多是 3:2）。 */
private const val FALLBACK_RATIO = 1.5f

/** 预览图的测试标签：单测靠它量 dp 尺寸，锁住「不随屏幕密度变」。 */
internal const val PREVIEW_IMAGE_TAG = "widget_preview_image"

/**
 * 添加页：**只在应用内一键添加**（`requestPinAppWidget`），不讲系统的手动添加路径。
 *
 * 版式：一次一张大预览，左右滑换组件，页码点示位，**添加按钮只有底部一个**。
 * 按钮不跟着卡片走，也就不存在"横滑时右侧卡片的按钮被裁在屏幕外"的问题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPinScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isMiui = remember { WidgetPin.isMiuiHome(context) }
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(WidgetPin.snapshot(context)) }
    val pagerState = rememberPagerState(pageCount = { WidgetPin.entries.size })
    val current = WidgetPin.entries[pagerState.currentPage.coerceIn(WidgetPin.entries.indices)]

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
    fun addWidget(entry: WidgetPin.Entry, before: Int) {
        if (!WidgetPin.requestPin(context, entry)) {
            toast(context, R.string.widget_pin_failed)
            return
        }
        scope.launch {
            val added = WidgetPin.awaitPinned(context, entry, before)
            snapshot = WidgetPin.snapshot(context)
            toast(context, if (added) R.string.widget_pin_added else R.string.widget_pin_not_added)
        }
    }

    Scaffold(
        topBar = {
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
        },
        bottomBar = {
            BottomBar(
                bound = snapshot.boundCount(current),
                pinSupported = snapshot.pinSupported,
                onAdd = { addWidget(current, snapshot.boundCount(current)) }
            )
        }
    ) { innerPadding ->
        WidgetPinContent(
            modifier = Modifier.padding(innerPadding),
            entry = current,
            snapshot = snapshot,
            isMiui = isMiui,
            pagerState = pagerState
        )
    }
}

/** 页面主体：标题 → 轮播大预览 → 页码点 → 页脚提示 → 前提提示。 */
@Composable
private fun WidgetPinContent(
    modifier: Modifier,
    entry: WidgetPin.Entry,
    snapshot: WidgetPin.Snapshot,
    isMiui: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 高屏上富余的高度上下平分：内容块居中，前提提示与按钮在底部成组，
        // 不会出现「上面挤满、中间空一大片」
        Spacer(Modifier.weight(1f))
        CurrentTitle(entry = entry, bound = snapshot.boundCount(entry))
        Spacer(Modifier.height(14.dp))
        HorizontalPager(
            state = pagerState,
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            PreviewPage(entry = WidgetPin.entries[page])
        }
        Spacer(Modifier.height(12.dp))
        PageDots(
            count = WidgetPin.entries.size,
            current = pagerState.currentPage,
            onJump = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.widget_page_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
        // 富余高度全部吃掉：前提提示因此贴着底部按钮，中间不再留一大片空背景
        Spacer(Modifier.weight(1f))
        PermissionStrip(pinSupported = snapshot.pinSupported, isMiui = isMiui)
        Spacer(Modifier.height(10.dp))
    }
}

/** 当前组件的名称 + 说明 + 尺寸/已添加：跟着滑动的那一页变。 */
@Composable
private fun CurrentTitle(entry: WidgetPin.Entry, bound: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(entry.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(entry.descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        val cells = WidgetPin.cellsOf(entry)
        val meta = if (bound == 0) {
            stringResource(R.string.widget_pin_none)
        } else {
            stringResource(R.string.widget_pin_count, bound)
        }
        Text(
            if (cells.isEmpty()) meta else "$cells · $meta",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** 一页：一张卡里放真实预览。
 *  卡底用浅中性色而不是白：组件卡自己就是白的，白底上分不出边界，预览像浮在空白里。 */
@Composable
private fun PreviewPage(entry: WidgetPin.Entry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_STAGE_HEIGHT)
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
            PreviewImage(entry.previewImageRes)
        }
    }
}

/**
 * 预览图：**宽度撑满预览区，高度按图片自身比例算**，预览区就贴着图。
 *
 * 不能只写 `fillMaxWidth()` 让 Image 自己定尺寸 —— 那样画出来的是「PNG 像素 ÷ 屏幕密度」dp，
 * 同一个组件在 3x 屏上 240dp、2x 屏上 318dp，大小随机型变（而且缩在预览区中间显得很小）。
 * 这里显式按比例定尺寸，尺寸只跟页面宽度有关，任何机型都一样。
 */
@Composable
private fun PreviewImage(imageRes: Int) {
    val painter = painterResource(imageRes)
    val intrinsic = painter.intrinsicSize
    val ratio = if (intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        FALLBACK_RATIO
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val width = maxWidth
        // 正方形那种太高的按可用高度封顶，免得把预览区撑爆
        val height = minOf(width / ratio, maxHeight)
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .width(width)
                .height(height)
                .testTag(PREVIEW_IMAGE_TAG),
            contentScale = ContentScale.Fit
        )
    }
}

/** 页码点：当前页实心、其余淡色；点了能跳过去。外层 padding 撑出可点范围。 */
@Composable
private fun PageDots(count: Int, current: Int, onJump: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onJump(index) }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                            }
                        )
                )
            }
        }
    }
}

/**
 * 前提提示条（只在需要时出现，用**中性底**而不是警示色——它是前提条件，不是报错）：
 * - 小米桌面：落桌前校验「桌面快捷方式」权限，没开点添加会静默失败；
 * - 桌面不支持应用内添加：只能退回系统那条路（这时才提，平时不讲）。
 */
@Composable
private fun PermissionStrip(pinSupported: Boolean, isMiui: Boolean) {
    if (!pinSupported) {
        InfoBlock(
            title = stringResource(R.string.widget_page_unsupported),
            body = null,
            action = null,
            onClick = {}
        )
        return
    }
    if (!isMiui) return

    val context = LocalContext.current
    InfoBlock(
        title = stringResource(R.string.widget_page_perm_title),
        body = stringResource(R.string.widget_page_perm_desc),
        action = stringResource(R.string.widget_page_perm_action),
        onClick = {
            // MIUI 的「桌面快捷方式」开关不在任何公开 API 里，只能带用户去应用详情页手动开
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

/** 底部通栏按钮：整页只有这一个动作，点它添加当前这一页的组件。 */
@Composable
private fun BottomBar(bound: Int, pinSupported: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Button(
            enabled = pinSupported,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    if (bound == 0) R.string.widget_pin_add else R.string.widget_page_add_again
                )
            )
        }
    }
}
