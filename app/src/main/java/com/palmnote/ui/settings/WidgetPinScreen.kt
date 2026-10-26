// 「画廊 + 详情面板」两层同处一屏：面板那叠组件（顶栏 / 大预览 / 背景 · 不透明度 / 添加按钮）与
// 画廊格共用同一个预览缩放算法，拆文件反而要把那套私有算法对外公开，因此按 life 各屏的既有
// 做法在文件级放宽函数条数（detekt 阈值 30）。
@file:Suppress("TooManyFunctions")

package com.palmnote.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.palmnote.app.R
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.COMPACT_DRAG_HANDLE_PADDING
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.components.SecondaryTopAppBar
import androidx.compose.ui.window.Dialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import com.palmnote.ui.widget.WidgetPin
import com.palmnote.ui.widget.WidgetSizeClass
import com.palmnote.ui.widget.WidgetInstanceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import com.palmnote.ui.widget.BookListCard
import com.palmnote.ui.widget.AccentThemeCard
import com.palmnote.ui.widget.WidgetTone
import com.palmnote.ui.widget.WidgetToneContext
import com.palmnote.ui.widget.WidgetTonePicker
import com.palmnote.ui.widget.WidgetToneSweep
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.wallpaper.decodeWallpaperBitmap
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.filled.ChevronRight
import com.palmnote.ui.components.toComposeColor
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.theme.warning
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import android.graphics.BitmapFactory
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 画廊固定两列（2026-10-20 参考图样式）：预览按实际列宽等比缩放，不会失真。
 * 之所以不用 Adaptive：参考图就是稳定两列，平板上一列一个反而稀。
 */
private const val GRID_COLUMN_COUNT = 2

/** 画廊两列之间的间距：行与行之间用同一档（11dp，稿 §5「160 + 11 + 160 = 331dp」），方块感一致。 */
private val GRID_COLUMN_GAP = 11.dp

/**
 * 画廊卡预览盒的上下呼吸位（各一份，两侧共 8dp）。
 *
 * 盒高按**入口卡位两档**算（见 [showcaseCardHeightOf]）：2×2 方档 = 列宽、4×2 宽档 = 列宽 × 0.60，
 * 同一行取更高那档、各卡 widget 居中 —— 全页只有这两种卡，名称行严格对齐。
 *
 * 这 4dp 同时给投影留位（画廊投影乘完缩放系数也就 4dp 上下）。
 */
private val PREVIEW_V_PADDING = 4.dp

/**
 * 入口卡里 widget 的放大上限：按列宽铺满时，1×1（82dp）要放到 2.1 倍，位图会糊 ——
 * 这类小方块宁可小一号居中，也不拉成马赛克。
 */
private const val SHOWCASE_MAX_SCALE = 1.5f

/**
 * 面板高度占**可用高度**的比例：对齐系统「添加小部件」那种**近全高面板**（顶部留出状态栏与把手）。
 * 里边用 `weight` 分配余量 —— 预览吃掉中间那块空档、按钮钉在底部，全程不用滚。
 * 给比例而不是具体 dp：可用高度已扣掉系统栏与把手，面板因此永远不会顶出屏幕。
 */
private const val SHEET_HEIGHT_FRACTION = 0.92f

/**
 * 胶囊层在「卡片相对 + 深色卡」时叠的白：与真机 `widget_pill_chip` 的 0xE0/FF 同值
 * （见 `WidgetPin.CHIP_SUFFIX`）。
 */
private const val CHIP_OVERLAY_ALPHA = 0.88f

/** 分页标签行（尺寸胶囊）的间距。 */
private val STYLE_DOT_GAP = 6.dp

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

/** 选择行左置标签的列宽：背景/不透明度共用，选项行吃满剩余宽度（省一行高度给预览）。 */
private val PICKER_LABEL_WIDTH = 72.dp
private val PILL_H_PADDING = 16.dp
private val PILL_GAP = 8.dp

/** 胶囊选中态的淡入时长：颜色突变（尤其不透明度滑杆来回拖）会显得生硬，给一小段过渡就顺了。 */
private const val PILL_FADE_MILLIS = 160

/** 面板底部添加按钮：对齐系统页的 50dp 实底胶囊（M3 默认 40dp 偏矮）。 */
private val SHEET_ADD_HEIGHT = 50.dp


/**
 * 面板里可选的背景色板，**五个预设**（2026-10-25 用户定：换「正常一点」的柔和色）：
 * 白 = 默认卡底（不接管）；三个低饱和浅色（浅灰 / 米白 / 浅青）走「浅底深字」；
 * 墨 = v37 夜卡面（夜间或深色桌面的选择）。内容色调链按背景明暗自动切字色。
 */
internal val BACKGROUND_SWATCHES: List<Int> = listOf(
    0xFFFFFFFF.toInt(), // 白（默认卡底）
    0xFFF1F2F4.toInt(), // 浅灰
    0xFFF7F3EA.toInt(), // 米白
    0xFFE7F1F2.toInt(), // 浅青（品牌青淡档）
    0xFF1C1F26.toInt() // 墨（夜间卡面）
)

internal const val SHEET_BG_ROW_TAG = "widget_pin_sheet_background"

/** 背景色块的直径：与画廊「＋」触控盒一致，点起来不费劲。 */
internal val SWATCH_SIZE = 32.dp

/** 画廊卡预览右上角的小勾已下线（2026-10-24 用户定：勾挪进弹窗头部，画廊只是入口）。 */

/**
 * 名称行与预览卡之间只留一点呼吸位：卡位外已有 [PREVIEW_V_PADDING] 的 4dp，
 * 再补 1dp 正好是稿 §5 `.gitem` 的「卡与标签间距 5px」（标签行本身没有固定高度）。
 */
private val NAME_TOP_PADDING = 1.dp

/**
 * 预览的柔影：底托是淡灰、widget 是白底，只靠颜色差分层不够，加一层投影才像浮在桌面上。
 * 投影跟着缩放系数一起缩，画廊卡上的小预览才不会拖一条大影。
 * 画廊卡垫在底托上、影轻一点（[PREVIEW_SHADOW]）；面板里没有底托、直接浮在白底上，
 * 影要更明显才立得住（[FLOATING_SHADOW]）。
 */
private val PREVIEW_SHADOW = 8.dp
private val FLOATING_SHADOW = 14.dp

/** widget 卡片自身的圆角（v37：全档统一 14dp），投影轮廓与它对齐。 */
/**
 * 预览卡的圆角：与 `@dimen/widget_card_radius` 同值（28dp，Android 12+ 跟系统圆角）。
 * 这里写死而不是读 dimen —— Compose 的 `RoundedCornerShape` 要 Dp，且预览卡是自绘阴影，
 * 两处必须同值；改一处漏一处就会出现「卡片直角色块从圆角里露出来」。
 */
private val WIDGET_CARD_CORNER = 28.dp

/** 历史按档常量并入 14dp（v37 圆角统一），保留名字避免大面积改调用点。 */
private val WIDE_CARD_CORNER = 28.dp

/** 面板/卡片相对预览里「标签行灰字」的稿色（§5 标签 10px #AEB3BB；夜间次级 #9AA1AC，§8）。 */

/** 页面底色（稿 §5「页面 #F4F5F7」；夜间取 §8 夜段底 #101216，卡面夜色由 night PNG 自带）。 */

/**
 * 半透明卡的**参照壁纸**：只画在卡自己的圆角内，100% 时被卡底完全盖住。
 *
 * 预览页底色是平的（浅色 `#F8F6F3` / 深色 `#121212`），默认白卡拉到 50% 就是 `#FCFBF9`、
 * 20% 是 `#FAF9F7` —— 和页面底几乎同色，卡边看起来"消失"，而里面的色砖/文字还在，
 * 于是成了「边框和内部不一致」（2026-10-20 用户反馈）。系统小组件选择器、iOS 小组件库、
 * 各家启动器都是把预览压在**壁纸**上，半透明才有参照；组件本身与桌面壁纸解耦、也读不到真实壁纸，
 * 这里用一张中性示例壁纸代替。
 *
 * 注意这是**卡内**的一层，不是卡外那圈底托 —— 底托会让组件看着多一层背景（用户明确否过）。
 */
private val PREVIEW_WALLPAPER_LIGHT = Color(0xFF8E8B86)
private val PREVIEW_WALLPAPER_DARK = Color(0xFF3B3A38)

/**
 * 全目录统一的缩放基准：设计稿里最大的卡（4×3 = 340×268dp，见 design-v4.html 的 `.s43`）。
 * 所有预览乘同一个系数 —— 1×1 是小方块、2×1 是矮条、4×3 是大块，相对大小等于桌面占格比例。
 */
private const val DESIGN_MAX_WIDTH = 340f
private const val DESIGN_MAX_HEIGHT = 268f

/**
 * 翻页器预览轨封顶时给下方**样式标签行**预留的高度（胶囊 ~30dp + 上距 12dp，取整放宽）。
 * 常规屏幕上固定内容压缩后（徽章并标题行 / 行距 8dp / 编辑行 44dp）余量足够，轨高走满设计值、
 * 预览不缩小；这条只在**极矮屏幕**余量真的不够时兜底 —— 宁可预览小一点也不能裁掉标签行
 * （2026-10-25 实机：本月记账的标签行曾整行被挤出 weight 区）。
 */
private const val STYLE_TABS_RESERVED_DP = 46f

/** 组件没登记设计尺寸时的兜底：按 3×2 卡摆。 */
private val FALLBACK_SIZE = 245 to 148

/**
 * 入口卡位**只有两种画布**（2026-10-21 用户要求：「入口显示的大小要是 4×2 或 2×2」）：
 * - 2×2：方阵型组件（总资产 / 总览 / 密码本 / 快捷入口）→ 占一列，高 = 列宽；
 * - 4×2：其余 → 占满一整行，高 = 整行宽 × [WIDE_ASPECT]。
 *
 * 谁走哪档由 [WidgetPin.entryCellOf] 显式指定，别在这里按宽高比再猜一遍 ——
 * 2×1（2.16）与 4×2（2.30）光看比例分不开，但 2×1 那版内容是「三行拉开的数字」。
 *
 * 返回值是**卡片本身**的高度，不含上下呼吸位（[PREVIEW_V_PADDING]）—— 呼吸位由调用方加在
 * 卡位**外面**，这样点击层的波纹严格按卡片圆角裁，不会多出 4dp 伸到名称行方向。
 *
 * @param cell 该组件在画廊里占的画布（WIDE = 4×2 / SQUARE = 2×2）。
 * @param columnWidth 一列的宽度（调用方用 BoxWithConstraints 取）。
 */
private fun showcaseCardHeightOf(cell: WidgetSizeClass, columnWidth: Dp): Dp =
    when (cell) {
        WidgetSizeClass.SQUARE -> columnWidth
        else -> (columnWidth * GRID_COLUMN_COUNT + GRID_COLUMN_GAP * (GRID_COLUMN_COUNT - 1)) * WIDE_ASPECT
    }

/** 4×2 卡位的高宽比：148 / 340（用户定的档位尺寸）。 */
private const val WIDE_ASPECT = 148f / 340f

/** 弹窗预览的卡身圆角（v37 全档统一 14dp），与真机 [WidgetV22.bindCardBg] 的 drawable 同表。 */
@Suppress("UnusedParameter")
private fun designCornerOf(designSize: Pair<Int, Int>?): Dp = WIDGET_CARD_CORNER

/** 网格左右留白与行间距（稿 §5：左右各留 16dp，卡片间距 11px）。 */
private val GRID_SIDE_PADDING = 16.dp
private val GRID_ROW_GAP = 11.dp

/** 画廊卡（含预览图、名称、尺寸标）的测试标签。 */
internal const val CATALOG_CELL_TAG = "widget_catalog_cell"

/** 画廊网格的测试标签：滚动断言得点名它（面板里还有别的可滚节点）。 */
internal const val CATALOG_LIST_TAG = "widget_catalog_list"

/** 画廊卡内预览图的测试标签（单测靠它量 dp 尺寸）。 */
internal const val PREVIEW_IMAGE_TAG = "widget_preview_image"

/** 实例选择面板里的单行实例（多个实例时先选一个再进编辑面板）。 */
internal const val INSTANCE_ROW_TAG = "widget_instance_row"

/** 编辑面板底部的「保存」按钮（与添加按钮同一颗，文案不同）。 */
internal const val SHEET_SAVE_TAG = "widget_pin_sheet_save"

/** 画廊卡预览右上角「已在桌面」小勾的测试标签。 */
/** 底部面板内容的测试标签。 */
internal const val SHEET_TAG = "widget_pin_sheet"

/** 面板顶栏（返回键 + 应用图标 + 应用名）的测试标签。 */
internal const val SHEET_TOPBAR_TAG = "widget_pin_sheet_topbar"

/** 面板顶栏返回键的测试标签。 */
internal const val SHEET_BACK_TAG = "widget_pin_sheet_back"

/** 面板里大预览的测试标签：只挂**当前页** —— 翻页途中相邻页也在组合里，同一标签会撞成多节点。 */
internal const val SHEET_PREVIEW_TAG = "widget_pin_sheet_preview"

/** 面板里相邻页预览的测试标签（非当前页）：只为把当前页那个标签腾干净，单测不点它。 */
private const val SHEET_PREVIEW_IDLE_TAG = "widget_pin_sheet_preview_idle"

/** 面板里添加按钮的测试标签。 */
internal const val SHEET_ADD_TAG = "widget_pin_sheet_add"

/** 面板里样式分页圆点行的测试标签（只有一件样式的组件不显示）。 */
internal const val SHEET_DOTS_TAG = "widget_pin_sheet_dots"

/** 面板里单颗分页圆点的测试标签：点数 = 该类样式的个数。 */
internal const val SHEET_DOT_TAG = "widget_pin_sheet_dot"

/** 面板里不透明度滑杆行的测试标签（所有组件都显示）。 */
internal const val SHEET_OPACITY_ROW_TAG = "widget_pin_sheet_opacity"

/** 面板里不透明度滑杆的测试标签：手势/值断言都直接打滑杆，别打整行（行内 Text 会吃掉手势）。 */
internal const val SHEET_OPACITY_SLIDER_TAG = "widget_pin_sheet_opacity_slider"

/**
 * 面板里挑好、等落桌确认后写到新实例上的配置（`appWidgetId` 要等桌面确认才存在）。
 *
 * 合在一个对象里：落桌成功/收起面板时一处清空，不会出现「背景清了、账本还留着」
 * 这种半截状态；四个值各自 null = 跟随默认（不写覆盖）。
 */
private data class WidgetDraft(
    val opacity: Float? = null,
    val background: String? = null,
    val blur: Float? = null,
    val book: Long? = null,
    val accent: String? = null
)

/** 把面板草稿写到刚落桌的实例上；null 的项不写（= 跟随默认，不产生覆盖键）。 */
private fun applyDraft(
    appWidgetId: Int,
    draft: WidgetDraft,
    onApplyOpacity: (Int, Float?) -> Unit,
    onApplyBackground: (Int, String?) -> Unit,
    onApplyBlur: (Int, Float?) -> Unit,
    onApplyBook: (Int, Long?) -> Unit,
    onApplyAccent: (Int, String?) -> Unit
) {
    draft.opacity?.let { onApplyOpacity(appWidgetId, it) }
    draft.background?.let { onApplyBackground(appWidgetId, it) }
    draft.blur?.let { onApplyBlur(appWidgetId, it) }
    draft.book?.let { onApplyBook(appWidgetId, it) }
    draft.accent?.let { onApplyAccent(appWidgetId, it) }
}

/**
 * 编辑已落桌实例的会话：组件（面板文案与「编辑更多」入口）+ 实例（尺寸与 id）+ 草稿。
 * 编辑态与添加态的草稿共用 [WidgetDraft]，差别只在保存时 null 的语义（见 `saveInstance`）。
 */
private data class EditSession(
    val component: WidgetPin.CatalogComponent,
    val instance: WidgetPin.PlacedInstance,
    val draft: WidgetDraft
)

/** 实例配置 → 面板草稿（五项都是覆盖值，null = 跟随默认）。 */
private fun WidgetInstanceConfig.toDraft(): WidgetDraft = WidgetDraft(opacity, background, blur, book, accent)

/** 面板草稿 → 实例配置：编辑态五项都写，null 是「回到默认」的明确选择。 */
private fun WidgetDraft.toConfig(): WidgetInstanceConfig =
    WidgetInstanceConfig(opacity, background, blur, book, accent)

/**
 * 已落桌实例编辑的状态与动作：画廊「编辑」入口 → 单份直接进面板 / 多份先选实例 → 面板改完整体写回。
 * 从 [WidgetPinScreen] 抽出来：这一整条链的主函数放不下（detekt 长度/复杂度）。
 *
 * 状态用 Compose 的 `mutableStateOf` 托管；`load`/`save`/`instancesOf` 在 [remember] 时闭包捕获，
 * 调用方传的都是稳定的 ViewModel 引用。`instancesOf` 每次点击现取，不缓存快照，避免陈旧数据。
 */
private class InstanceEditor(
    private val scope: CoroutineScope,
    /** 读实例的当前配置（编辑面板回填）。 */
    val load: suspend (Int) -> WidgetInstanceConfig,
    /** 写回该实例（四项整体写）。 */
    private val save: (Int, WidgetInstanceConfig) -> Unit,
    /** 数某组件在桌面上的实例（点「编辑」的瞬间才取）。 */
    val instancesOf: (WidgetPin.CatalogComponent) -> List<WidgetPin.PlacedInstance>,
    /** 进编辑链路前先退出添加态面板，否则两层 sheet 叠在一起。 */
    private val onCloseAddSheet: () -> Unit,
    /** 保存完成的收尾（toast）。 */
    private val onSaved: () -> Unit
) {
    /** 同组件多份时先列实例选一个（族键）；null = 没开。 */
    var pickFamilyKey by mutableStateOf<Int?>(null)
        private set

    /** 编辑中的会话：读不到配置（加载中）之前不进面板，避免先闪一版默认值。 */
    var session by mutableStateOf<EditSession?>(null)
        private set

    /**
     * 进编辑链路：只有一个实例就直接进面板，多份则先选实例。
     * 入口有两处 —— 添加态面板底部的「编辑桌面上的 N 个」，以及长按画廊卡。
     */
    fun editPlaced(component: WidgetPin.CatalogComponent) {
        val instances = instancesOf(component)
        if (instances.isEmpty()) return
        onCloseAddSheet()
        when (instances.size) {
            1 -> openEditor(component, instances.first())
            else -> pickFamilyKey = component.familyKey
        }
    }

    /** 读实例配置 → 回填草稿；读的过程中面板不出现（DataStore 首帧很快，看不到空窗）。 */
    fun openEditor(component: WidgetPin.CatalogComponent, instance: WidgetPin.PlacedInstance) {
        scope.launch {
            session = EditSession(component, instance, load(instance.appWidgetId).toDraft())
            pickFamilyKey = null
        }
    }

    /** 面板里改的都是草稿，保存时才写回。 */
    fun changeDraft(draft: WidgetDraft) {
        session = session?.copy(draft = draft)
    }

    /** 编辑态四项都写（null = 回到默认），与添加态的「null 不写」相反；写完收起面板。 */
    fun save() {
        session?.let { save(it.instance.appWidgetId, it.draft.toConfig()) }
        session = null
        onSaved()
    }

    /**
     * 下滑/返回收起面板：选实例层与编辑层互斥、只有一个在场，但一起清空没有副作用 ——
     * 也就不必区分「关哪一层」。
     */
    fun dismissAll() {
        pickFamilyKey = null
        session = null
    }
}

/**
 * 添加页：**只在应用内一键添加**（`requestPinAppWidget`），不讲系统的手动添加路径。
 *
 * 首屏是**画廊**（设计稿 V22 §5，2026-10-24 对稿定版）：页头「小组件」+ 右侧灰字计数，
 * 下面一条平铺列表 —— 4×2 的卡占整行、2×2 的卡两两并排；每张卡 = 预览图 + 图下一行
 * 「名称左 · 尺寸右」的灰字标签。不设分组筛选与搜索（稿里没有），已落桌的卡在预览右上角带小勾。
 *
 * **落桌必须经过面板**（2026-10-21 用户定：「必须点进去选择小组件再添加」，卡上的「＋」已删除）：
 * 点卡身弹出**近全高面板**（对齐各家「添加小部件」的观感）—— 名称 + 说明 + 大预览（按桌面真实
 * 尺寸浮在面板上，左右滑动换样式、下缘一排分页圆点）+ 背景色 + 不透明度滑杆 + 「添加这个样式」。
 *
 * **已落桌实例可编辑**：两条入口 —— 面板底部的「编辑桌面上的 N 个」（仅在真落过桌时出现），
 * 以及长按卡片。进的是同一套面板，只是样式定死在该实例的形态、底部换成「保存」，
 * 保存时把背景/不透明度（以及账本/配色）直接写回该 `appWidgetId`；同组件摆了多份时先选实例。
 */
/** 顶栏搜索状态（2026-10-25 用户定）：展开后铺满顶栏；返回键先收搜索再退出页。 */
private class SearchState {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    fun close() {
        open = false
        query = ""
    }
}

@Composable
private fun rememberSearchState(): SearchState {
    val state = remember { SearchState() }
    BackHandler(enabled = state.open) { state.close() }
    return state
}

/**
 * 「添加到桌面」动作的收口（2026-10-25 拆出 [WidgetPinScreen]，主函数已顶到 detekt 长度上限）：
 * pin 发起 + 落桌核对（最多 5×1.5s）+ 结果回报。核对期间按钮转「确认中…」并禁用，
 * 连点不会连发多个系统确认框。
 */
@Suppress("LongParameterList")
private class AddWidgetAction(
    private val context: Context,
    private val scope: CoroutineScope,
    private val isVerifying: () -> Boolean,
    private val onVerifyingChange: (Boolean) -> Unit,
    /** 落桌核对完回调（newId 非空 = 成功）；写实例配置、收面板、报结果都在这里。 */
    private val onPlaced: (Int?) -> Unit
) {
    fun add(entry: WidgetPin.Entry) {
        if (isVerifying()) return
        val before = WidgetPin.boundIds(context, entry.providerClass)
        if (!WidgetPin.requestPin(context, entry)) {
            toast(context, R.string.widget_pin_failed)
            return
        }
        onVerifyingChange(true)
        scope.launch {
            // 核对结果顺带带回新实例 id：面板里挑的不透明度要落到这个实例上
            val newId = WidgetPin.awaitPinned(context, entry, before)
            onVerifyingChange(false)
            onPlaced(newId)
        }
    }
}

/**
 * 页面底色：**跟随主题 background**（2026-10-24 用户定：要随自定义壁纸变化）。
 * 壁纸模式下 `background` 是透明的（见 Theme.kt），壁纸直接透出来，与其他页面一致；
 * 非壁纸模式就是主题面色。此前对稿 V22 §5 钉过固定 `#F4F5F7/#101216`，按用户要求回退。
 */
@Composable
private fun widgetPageBackground(): Color = MaterialTheme.colorScheme.background

@Composable
fun WidgetPinScreen(
    onNavigateBack: () -> Unit = {},
    onApplyOpacity: (Int, Float?) -> Unit = { _, _ -> },
    /** 面板里挑的背景色落到新实例；null = 用默认卡片底色。 */
    onApplyBackground: (Int, String?) -> Unit = { _, _ -> },
    /** 图片背景的模糊度落到新实例；null/0 = 不模糊。 */
    onApplyBlur: (Int, Float?) -> Unit = { _, _ -> },
    /** 「编辑账本与配色」层的选择落到新实例（仅账单组件用）。 */
    books: Flow<List<AccountBook>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    onApplyBook: (Int, Long?) -> Unit = { _, _ -> },
    onApplyAccent: (Int, String?) -> Unit = { _, _ -> },
    /** 编辑已落桌实例时读它的当前配置（面板回填用）。 */
    loadInstance: suspend (Int) -> WidgetInstanceConfig = { WidgetInstanceConfig() },
    /** 编辑面板点「保存」：四项整体写回该实例（null = 回到默认）。 */
    onSaveInstance: (Int, WidgetInstanceConfig) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { WidgetPin.catalog() }
    val homeNeedsPerm = remember { WidgetPin.homeNeedsPerm(context) }

    // 页面状态收口在 [CatalogPageState]（2026-10-25：主函数顶到 detekt 长度上限）。
    // 快照**同步初始化**：首帧就拿到真实 pinOk —— 否则顶部权限条先按「从未成功」闪一帧琥珀
    // 等ON_RESUME 重数才翻面（2026-10-25 用户见「黄条闪一下」）
    val state = remember {
        CatalogPageState(
            initialSnapshot = WidgetPin.snapshot(context),
            onApplyOpacity = onApplyOpacity,
            onApplyBackground = onApplyBackground,
            onApplyBlur = onApplyBlur,
            onApplyBook = onApplyBook,
            onApplyAccent = onApplyAccent
        )
    }
    // 点了「添加」不立刻报结果：requestPinAppWidget 返回 true 只说明通道可用，桌面缺权限时会静默失败；
    // 所以落桌后等几秒重数桌面实例，数到了才算成了。核对期间按钮转「确认中…」并禁用，连点不会连发多个系统确认框
    val addWidget = remember(context, scope) {
        AddWidgetAction(context, scope, { state.verifying }, { state.verifying = it }) { newId ->
            if (newId != null) state.applyDraft(newId)
            state.snapshot = WidgetPin.snapshot(context)
            // 确认落桌才收起面板：加完还停在原地，用户会以为没成功
            if (newId != null) state.closeSheet()
            toast(context, if (newId != null) R.string.widget_pin_added else R.string.widget_pin_not_added)
        }
    }

    // 已落桌实例的编辑：状态与动作收口在 [InstanceEditor]（主函数放不下这一整条链）
    val editor = remember {
        InstanceEditor(
            scope, loadInstance, onSaveInstance,
        instancesOf = { state.snapshot.instancesOf(it) }, onCloseAddSheet = state::closeSheet,
            onSaved = { toast(context, R.string.widget_pin_saved) }
        )
    }

    // 从系统确认框回来时（ON_RESUME）重数一遍桌面实例，把「桌面已有 N 个」刷新到最新
    RefreshSnapshotOnResume { state.snapshot = WidgetPin.snapshot(context) }

    CatalogScaffold(
        onNavigateBack = onNavigateBack,
        // 顶栏搜索（2026-10-25 用户定）：展开后铺满顶栏，按短名/长名/说明过滤画廊
        search = rememberSearchState(),
        catalog = catalog,
        snapshot = state.snapshot,
        homeNeedsPerm = homeNeedsPerm,
        onOpen = { state.openFamilyKey = it.familyKey }, onEdit = editor::editPlaced
    )

    WidgetSheetHost(
        openComponent = catalog.firstComponentOrNull(state.openFamilyKey),
        selectedStyleIndex = state.selectedStyleIndex,
        snapshot = state.snapshot,
        verifying = state.verifying,
        draft = state.draft,
        books = books,
        onSelectStyle = { c, e ->
            state.selectedStyleIndex +=
                (c.familyKey to c.entries.indexOf(e))
        },
        onDraftChange = { state.draft = it },
        onDismiss = state::closeSheet,
        onAdd = { addWidget.add(selectedStyleEntry(it, state.selectedStyleIndex)) }
    )
    InstanceEditorSheets(editor = editor, catalog = catalog, books = books)
}
// 面板里滑到哪件样式就落哪件；大小交给桌面拖拽（组件按实际宽高自适应）

/**
 * [WidgetPinScreen] 的页面状态：落桌核对、面板草稿、样式选择、打开的族。
 * 2026-10-25 拆出 —— 状态声明占了主函数一大段，摊开就顶破 detekt 的函数长度上限。
 */
private class CatalogPageState(
    /** 进入页面时的真实快照（同步取，首帧即正确——见 [WidgetPinScreen] 里的说明）。 */
    initialSnapshot: WidgetPin.Snapshot,
    private val onApplyOpacity: (Int, Float?) -> Unit,
    private val onApplyBackground: (Int, String?) -> Unit,
    private val onApplyBlur: (Int, Float?) -> Unit,
    private val onApplyBook: (Int, Long?) -> Unit,
    private val onApplyAccent: (Int, String?) -> Unit
) {
    /** 桌面实例快照（pin 后重数）。 */
    var snapshot by mutableStateOf(initialSnapshot)

    /** 落桌核对期（最多 5×1.5s）：锁住按钮，防连点连发多个系统确认框。 */
    var verifying by mutableStateOf(false)

    /** 每个组件在弹窗里滑到的样式（族键 → entries 下标）；收起后画廊入口仍固定展示主样式。 */
    var selectedStyleIndex by mutableStateOf<Map<Int, Int>>(emptyMap())

    /** 当前打开面板的组件（族键）；null = 没开。 */
    var openFamilyKey by mutableStateOf<Int?>(null)

    /** 面板里挑的所有配置：只对**本次添加**有效，落桌成功或收起面板就整体清掉（免得继承上次的选择）。 */
    var draft by mutableStateOf(WidgetDraft())

    /** 收起面板并丢掉草稿。 */
    fun closeSheet() {
        openFamilyKey = null
        draft = WidgetDraft()
    }

    /** 落桌成功后把面板里挑的配置写到**这个实例**上（不透明度/背景/模糊/账本/配色）。 */
    fun applyDraft(appWidgetId: Int) = applyDraft(
        appWidgetId, draft,
        onApplyOpacity, onApplyBackground, onApplyBlur, onApplyBook, onApplyAccent
    )
}

/**
 * 画廊页的骨架：顶栏（返回 + 搜索）+ [CatalogScreen]。
 * 2026-10-25 从 [WidgetPinScreen] 拆出 —— 主函数已顶到 detekt 长度上限。
 * 页面底色按稿 §5（#F4F5F7 / 夜 #101216）—— 现跟主题 background，见 [widgetPageBackground]。
 */
@Composable
private fun CatalogScaffold(
    onNavigateBack: () -> Unit,
    search: SearchState,
    catalog: List<WidgetPin.CatalogGroup>,
    snapshot: WidgetPin.Snapshot,
    homeNeedsPerm: Boolean,
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onEdit: (WidgetPin.CatalogComponent) -> Unit
) {
    Scaffold(
        topBar = {
            WidgetPinTopBar(
                onNavigateBack = onNavigateBack,
                searchQuery = search.query,
                onSearchQueryChange = { search.query = it },
                searchOpen = search.open,
                onSearchOpenChange = { search.open = it }
            )
        },
        containerColor = widgetPageBackground()
    ) { innerPadding ->
        CatalogScreen(
            modifier = Modifier.padding(innerPadding),
            catalog = catalog, snapshot = snapshot, homeNeedsPerm = homeNeedsPerm,
            searchQuery = search.query,
            onOpen = onOpen,
            onEdit = onEdit
        )
    }
}

/**
 * 面板的装配层：把 [WidgetPinScreen] 里的状态搬到 [WidgetPinSheet] 的参数上。
 * 抽出来的唯一理由是控制 `WidgetPinScreen` 的长度（面板 18 个参数摊在主函数里会把它撑爆）。
 */
@Suppress("LongParameterList")
@Composable
private fun WidgetSheetHost(
    openComponent: WidgetPin.CatalogComponent?,
    selectedStyleIndex: Map<Int, Int>,
    snapshot: WidgetPin.Snapshot,
    verifying: Boolean,
    draft: WidgetDraft,
    books: Flow<List<AccountBook>>,
    onSelectStyle: (WidgetPin.CatalogComponent, WidgetPin.Entry) -> Unit,
    onDraftChange: (WidgetDraft) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (WidgetPin.CatalogComponent) -> Unit
) {
    if (openComponent == null) return
    WidgetPinSheet(
        component = openComponent,
        selectedStyleIndex = selectedStyleIndex,
        bound = snapshot.boundCountOf(openComponent),
        pinSupported = snapshot.pinSupported,
        verifying = verifying,
        opacity = draft.opacity,
        background = draft.background,
        blur = draft.blur,
        placedPerStyle = openComponent.entries.map { snapshot.boundCount(it) },
        books = books,
        chosenBook = draft.book,
        onChooseBook = { onDraftChange(draft.copy(book = it)) },
        chosenAccent = draft.accent,
        onChooseAccent = { onDraftChange(draft.copy(accent = it)) },
        onDismiss = onDismiss,
        onSelectStyle = { onSelectStyle(openComponent, it) },
        onSelectOpacity = { onDraftChange(draft.copy(opacity = it)) },
        onSelectBackground = { onDraftChange(draft.copy(background = it)) },
        onSelectBlur = { onDraftChange(draft.copy(blur = it)) },
        onAdd = { onAdd(openComponent) }
    )
}

/**
 * 实例编辑的两层面板：同组件多份时先选实例，选完进编辑面板。与添加面板（[WidgetSheetHost]）
 * 并列在场，同一时刻最多开一层（[InstanceEditor] 的两个状态互斥）。
 */
@Composable
private fun InstanceEditorSheets(
    editor: InstanceEditor,
    catalog: List<WidgetPin.CatalogGroup>,
    books: Flow<List<AccountBook>>
) {
    val picking = catalog.firstComponentOrNull(editor.pickFamilyKey)
    InstancePickerSheet(
        component = picking,
        instances = picking?.let { editor.instancesOf(it) }.orEmpty(),
        loadInstance = editor.load,
        onPick = { instance -> picking?.let { editor.openEditor(it, instance) } },
        onDismiss = editor::dismissAll
    )
    EditSheetHost(
        session = editor.session,
        books = books,
        onDraftChange = editor::changeDraft,
        onSave = editor::save,
        onDismiss = editor::dismissAll
    )
}

/**
 * 编辑面板的装配层：与添加面板是**同一颗** [WidgetPinSheet]，只有两处不同 ——
 * 尺寸由实例定死（不翻页）、底部按钮是「保存」（写回该 `appWidgetId`）。
 */
@Composable
private fun EditSheetHost(
    session: EditSession?,
    books: Flow<List<AccountBook>>,
    onDraftChange: (WidgetDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (session == null) return
    val draft = session.draft
    WidgetPinSheet(
        component = session.component,
        selectedStyleIndex = emptyMap(),
        // 编辑态不摆添加按钮，这两个值用不到
        bound = 0,
        pinSupported = false,
        verifying = false,
        opacity = draft.opacity,
        background = draft.background,
        blur = draft.blur,
        // 编辑态 styles 被收成「实例那一件」，计数也必须同长（1 件 = 就在桌面）：
        // 给整族列表会让 styles.indexOf(selected)=0 撞上族里第 0 档，实例是别的档时勾会掉
        placedPerStyle = listOf(1),
        books = books,
        chosenBook = draft.book,
        onChooseBook = { onDraftChange(draft.copy(book = it)) },
        chosenAccent = draft.accent,
        onChooseAccent = { onDraftChange(draft.copy(accent = it)) },
        onDismiss = onDismiss,
        onSelectStyle = {},
        onSelectOpacity = { onDraftChange(draft.copy(opacity = it)) },
        onSelectBackground = { onDraftChange(draft.copy(background = it)) },
        onSelectBlur = { onDraftChange(draft.copy(blur = it)) },
        onAdd = {},
        editEntry = session.instance.entry,
        onSave = onSave
    )
}

/**
 * 同组件摆了多份时先选实例：每行用「背景色点 + 尺寸 + 不透明度」帮忙认出要改哪一个
 * （同尺寸的两份从列表上分不出谁是谁，进面板看到背景才知道；这是刻意的，不做缩略图重渲染）。
 */
@Composable
private fun InstancePickerSheet(
    component: WidgetPin.CatalogComponent?,
    instances: List<WidgetPin.PlacedInstance>,
    loadInstance: suspend (Int) -> WidgetInstanceConfig,
    onPick: (WidgetPin.PlacedInstance) -> Unit,
    onDismiss: () -> Unit
) {
    if (component == null) return
    var configs by remember(instances) { mutableStateOf<Map<Int, WidgetInstanceConfig>>(emptyMap()) }
    LaunchedEffect(instances) {
        configs = instances.associate { it.appWidgetId to loadInstance(it.appWidgetId) }
    }
    AppBottomSheet(onDismissRequest = onDismiss) {
        SheetTopBar(onBack = onDismiss)
        SheetHeader(
            component.titleRes,
            stringResource(R.string.widget_pin_instances_desc, instances.size)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            instances.forEachIndexed { index, instance ->
                InstanceRow(
                    index = index + 1,
                    instance = instance,
                    config = configs[instance.appWidgetId],
                    onClick = { onPick(instance) }
                )
            }
        }
    }
}

/** 实例选择面板的一行：背景色点 + 「实例 N」+ 尺寸与不透明度。 */
@Composable
private fun InstanceRow(
    index: Int,
    instance: WidgetPin.PlacedInstance,
    config: WidgetInstanceConfig?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(INSTANCE_ROW_TAG)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BackgroundDot(background = config?.background)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.widget_pin_instance_name, index),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                instanceSubtitle(instance, config),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 「3×2 · 80%」：读不到配置时只写尺寸，别先报一个假的 100%。 */
@Composable
private fun instanceSubtitle(instance: WidgetPin.PlacedInstance, config: WidgetInstanceConfig?): String {
    val cells = WidgetPin.cellsOf(instance.entry)
    val opacity = config?.let {
        val value = it.opacity ?: PreferencesManager.DEFAULT_WIDGET_OPACITY
        "${(value * 100f).roundToInt()}%"
    }
    return listOfNotNull(cells.takeIf { it.isNotBlank() }, opacity).joinToString(" · ")
}

/** 实例的背景色点：默认卡底 / 纯色 / 图片背景（图片用中性色点占位，不解析 uri）。 */
@Composable
private fun BackgroundDot(background: String?) {
    val color = when {
        background == null -> colorResource(R.color.widget_v2_card_bg)
        background.startsWith("#") -> background.toComposeColor()
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

/** 按族键找回组件；没开面板（null）或找不到就给 null。 */
private fun List<WidgetPin.CatalogGroup>.firstComponentOrNull(familyKey: Int?): WidgetPin.CatalogComponent? {
    if (familyKey == null) return null
    return asSequence().flatMap { it.components }.firstOrNull { it.familyKey == familyKey }
}

/**
 * 底部面板：近全高的悬浮面板（高度 = 可用高度 × [SHEET_HEIGHT_FRACTION]），内容交给
 * [WidgetPinSheetContent]。抽出来让 [WidgetPinScreen] 只留状态与装配。
 *
 * 参数多且都是「面板内容 + 回调」的直通：沿用 [WidgetPinSheetContent] 同一口径就地放宽。
 */
@Suppress("LongParameterList")
@Composable
private fun WidgetPinSheet(
    component: WidgetPin.CatalogComponent,
    selectedStyleIndex: Map<Int, Int>,
    bound: Int,
    pinSupported: Boolean,
    verifying: Boolean,
    opacity: Float?,
    background: String?,
    books: Flow<List<AccountBook>>,
    chosenBook: Long?,
    onChooseBook: (Long?) -> Unit,
    chosenAccent: String?,
    onChooseAccent: (String?) -> Unit,
    onDismiss: () -> Unit,
    onSelectStyle: (WidgetPin.Entry) -> Unit,
    onSelectOpacity: (Float?) -> Unit,
    onSelectBackground: (String?) -> Unit,
    /** 图片背景的模糊度；null = 不模糊。 */
    blur: Float? = null,
    onSelectBlur: (Float?) -> Unit = {},
    /** 各形态已落桌实例数（与 component.entries 同序）。 */
    placedPerStyle: List<Int> = emptyList(),
    onAdd: () -> Unit,
    /** 非空 = 编辑已落桌实例：样式定死在这一页，底部按钮换成「保存」。 */
    editEntry: WidgetPin.Entry? = null,
    onSave: () -> Unit = {}
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        heightFraction = SHEET_HEIGHT_FRACTION,
        // 把手贴顶：M3 默认 48dp 的槽会把顶栏 / 标题整体压低 20dp 上下，系统选择器那一行是贴着的
        dragHandlePadding = COMPACT_DRAG_HANDLE_PADDING
    ) {
        WidgetPinSheetContent(
            component = component,
            selected = editEntry ?: selectedStyleEntry(component, selectedStyleIndex),
            bound = bound,
            pinSupported = pinSupported,
            verifying = verifying,
            opacity = opacity,
            background = background,
            books = books,
            chosenBook = chosenBook,
            onChooseBook = onChooseBook,
            chosenAccent = chosenAccent,
            onChooseAccent = onChooseAccent,
            onBack = onDismiss,
            onSelectStyle = onSelectStyle,
            onSelectOpacity = onSelectOpacity,
            onSelectBackground = onSelectBackground,
            chosenBlur = blur,
            onSelectBlur = onSelectBlur,
            placedPerStyle = placedPerStyle,
            onAdd = onAdd,
            editEntry = editEntry,
            onSave = onSave
        )
    }
}

/**
 * 顶栏：返回 + 「小组件」标题 + 右上搜索。2026-10-25 用户定：
 * - 加搜索图标，展开后搜索栏铺满顶栏（与物品/账单页同款 `ModuleSearchBar`）；
 * - 「N 个组件」计数去掉。
 * 标题 17sp 黑体；底色透出页面底（[widgetPageBackground]），不单独配色。
 */
@Composable
private fun WidgetPinTopBar(
    onNavigateBack: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    SecondaryTopAppBar(
        title = {
            if (searchOpen) {
                ModuleSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClear = { onSearchQueryChange("") },
                    placeholder = stringResource(R.string.search),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    stringResource(R.string.widget_page_title),
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
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
            if (searchOpen) {
                TextButton(
                    onClick = { onSearchOpenChange(false); onSearchQueryChange("") },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                IconButton(onClick = { onSearchOpenChange(true) }) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
 * 画廊主体：顶部权限提示条 + 网格（吃掉余量）。
 * 2026-10-25 用户定：权限提示**放顶部**并带颜色；底部不再放常驻小字入口
 * （那个入口并进顶部提示条的「去开启」），列表不再被底部折叠。
 */
@Suppress("LongParameterList")
@Composable
private fun CatalogScreen(
    modifier: Modifier = Modifier,
    catalog: List<WidgetPin.CatalogGroup>,
    snapshot: WidgetPin.Snapshot,
    homeNeedsPerm: Boolean,
    searchQuery: String = "",
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onEdit: (WidgetPin.CatalogComponent) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        PermissionStrip(
            pinSupported = snapshot.pinSupported, homeNeedsPerm = homeNeedsPerm, pinOk = snapshot.pinOk
        )
        CatalogGrid(
            modifier = Modifier.weight(1f),
            catalog = catalog,
            searchQuery = searchQuery,
            onOpen = onOpen,
            onEdit = onEdit
        )
    }
}

/**
 * 画廊列表（设计稿 V22 §5，2026-10-24 对稿）：**一条平铺流** —— 4×2 的卡占整行、
 * 2×2 的卡两两并排，顺序按 [WidgetPin.GALLERY_ORDER]。不设分组筛选 / 占位格
 * （稿里都没有；此前 chip 的「瀑布流零空洞」由 2×2 成对排序天然保证）。
 * 搜索（2026-10-25 用户定）：按短名 / 长名 / 说明匹配，命中排前不打乱稿序。
 *
 * 卡位只有两种（[WidgetPin.entryCellOf]）：4×2 的卡 `span = 整行`、2×2 的卡 `span = 1`。
 * 用 `LazyVerticalGrid` 而不是「手工每两个一组」：整行卡会把后面的卡推到下一行，
 * 手写分行必须自己算余量，组件数量一变就错位。
 */
@Composable
private fun CatalogGrid(
    modifier: Modifier,
    catalog: List<WidgetPin.CatalogGroup>,
    searchQuery: String = "",
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onEdit: (WidgetPin.CatalogComponent) -> Unit
) {
    val context = LocalContext.current
    // 平铺顺序：稿 §5 的封面次序（物品 → 记账 → 净资产+快捷 → 订阅 → …），目录分组不参与排序
    val shown = remember(catalog, searchQuery, context) {
        val all = catalog.flatMap { it.components }
            .sortedBy { WidgetPin.gallerySortIndex(it.titleRes) }
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            all
        } else {
            all.filter {
                catalogMatches(
                    q,
                    context.getString(it.titleRes),
                    context.getString(it.titleShortRes),
                    context.getString(it.descRes)
                )
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 卡位宽度在网格外先算好：item 内部拿不到「整行宽」，而 4×2 的卡要按整行算高
        val columnWidth =
            (maxWidth - GRID_SIDE_PADDING * 2 - GRID_COLUMN_GAP) / GRID_COLUMN_COUNT
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .testTag(CATALOG_LIST_TAG),
            columns = GridCells.Fixed(GRID_COLUMN_COUNT),
            contentPadding = PaddingValues(horizontal = GRID_SIDE_PADDING, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_COLUMN_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP)
        ) {
            shown.forEach { component ->
                catalogItem(
                    component, columnWidth,
                    onOpen, onEdit
                )
            }
        }
    }
}

/**
 * 搜索命中：短名 / 长名 / 说明任一包含关键词即算（不区分大小写）。
 * 必须非 @Composable：在 `remember(catalog, searchQuery)` 的过滤 lambda 里调用，
 * 那里不是组合上下文。文本在调用前解析好传进来。
 */
private fun catalogMatches(
    query: String,
    title: String,
    titleShort: String,
    desc: String
): Boolean {
    val q = query.lowercase()
    return title.lowercase().contains(q) ||
        titleShort.lowercase().contains(q) ||
        desc.lowercase().contains(q)
}

/**
 * 一张画廊卡的网格项：先按它的画布定跨几列，再把卡位高算好交给 [CatalogTileItem]。
 */
private fun LazyGridScope.catalogItem(
    component: WidgetPin.CatalogComponent,
    columnWidth: Dp,
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onEdit: (WidgetPin.CatalogComponent) -> Unit
) {
    val cell = WidgetPin.entryCellOf(component)
    val span = if (cell == WidgetSizeClass.SQUARE) 1 else GRID_COLUMN_COUNT
    item(key = component.familyKey, span = { GridItemSpan(span) }) {
        CatalogTileItem(
            component = component,
            cell = cell,
            previewHeight = showcaseCardHeightOf(cell, columnWidth),
            onOpen = onOpen,
            onEdit = onEdit
        )
    }
}

/** 单张画廊卡：交给 [CatalogTile] 渲染。 */
@Composable
private fun CatalogTileItem(
    component: WidgetPin.CatalogComponent,
    cell: WidgetSizeClass,
    previewHeight: Dp,
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onEdit: (WidgetPin.CatalogComponent) -> Unit
) {
    CatalogTile(
        component = component,
        cell = cell,
        previewHeight = previewHeight,
        onOpen = onOpen,
        onEdit = onEdit
    )
}

/**
 * 当前选中的样式：弹窗里滑过就用滑到的那件，否则用**画廊卡位对应的那一档**
 * （[WidgetPin.defaultStyleEntry]：2×2 族取 2×2，其余取 4×2）—— 打开面板第一眼
 * 看到的就是刚才那张卡的形状，页序（4×2 → 2×2 → 4×1 → 4×4）只影响左右滑的顺序。
 */
private fun selectedStyleEntry(
    component: WidgetPin.CatalogComponent,
    selectedStyleIndex: Map<Int, Int>
): WidgetPin.Entry {
    val index = selectedStyleIndex[component.familyKey]
    if (index != null) return component.entries.getOrElse(index) { component.entries.first() }
    return WidgetPin.defaultStyleEntry(component)
}


/**
 * 卡片预览区：入口图 + 已落桌小勾 + **贴着卡片圆角的点击层**。
 *
 * 点击层的波纹必须与卡片同形状、同尺寸（用户 2026-10-21 两次提：「波纹动效要贴合小组件，
 * 不要把下面文字也包括进来」）：
 * - 点击挂在预览区内那个 `matchParentSize` 的空 Box 上，**不挂整列** —— 否则波纹是个
 *   盖住名称行的方角矩形；
 * - 呼吸位（[PREVIEW_V_PADDING]）加在**卡位之外**，所以点击层与卡片严格重合，
 *   不会多出 4dp 伸向名称行；
 * - `clip` 用卡片同款 22dp 圆角，波纹被裁在圆角内。阴影不受影响：它由 [WidgetPreview]
 *   在更下一层按同一个圆角画。
 */
@Composable
private fun CatalogPreview(
    component: WidgetPin.CatalogComponent,
    cell: WidgetSizeClass,
    height: Dp,
    chooseLabel: String,
    editLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 圆角按档走（稿 §1：4×2=24dp、2×2=22dp）：裁切与投影轮廓都要与卡身同形
    val corner = if (cell == WidgetSizeClass.SQUARE) WIDGET_CARD_CORNER else WIDE_CARD_CORNER
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 呼吸位加在卡位**外面**：里面那个高度就是卡片本身，
            // 点击层（matchParentSize）因此与卡片完全重合，波纹不会溢到名称行
            .padding(vertical = PREVIEW_V_PADDING)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        // 入口图按 4×2 / 2×2 画布重出（见 WidgetPin.entryImageOf），设计尺寸与卡位同源
        WidgetPreview(
            imageRes = WidgetPin.entryImageOf(component),
            designSize = WidgetPin.entrySizeOf(WidgetPin.entryCellOf(component)),
            shadow = PREVIEW_SHADOW,
            tag = PREVIEW_IMAGE_TAG,
            maxScale = SHOWCASE_MAX_SCALE,
            corner = corner,
            modifier = Modifier.fillMaxSize()
        )
        // 点击层：**波纹按卡片圆角裁切**，并且铺在预览之上、阴影之下（阴影由预览自己画）。
        // 之前 `combinedClickable` 挂在整列上，波纹是一个盖住名称行的方角矩形，
        // 和 22dp 圆角的卡片对不上（2026-10-21 反馈）。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(corner))
                .combinedClickable(
                    onClickLabel = chooseLabel,
                    onLongClickLabel = editLabel,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        )
    }
}

/**
 * 画廊卡（设计稿 V22 §5，2026-10-24 对稿）：**入口只摆大卡** —— 卡位按 [WidgetPin.entryCellOf] 二选一，
 * 4×2 占满整行、2×2 占一列；预览用该组件最饱满的形态（[WidgetPin.showcaseEntryOf]）铺满卡位。
 * 具体用哪个尺寸，点卡身进面板再挑（面板里仍可左右滑动换样式）。
 *
 * 卡下方是稿里的标签行：**名称（weight 500）在左、尺寸（4×2 / 2×2，带下划线）在右**，
 * 10sp 灰字（[widgetPageCaptionColor]），两端对齐；长名称省略号截断不挤走尺寸（稿 §9）。
 * 已落桌的卡右上角叠小勾。标签行只是标签不可点 —— 点击/长按都归卡片预览（波纹层）。
 */
@Composable
private fun CatalogTile(
    component: WidgetPin.CatalogComponent,
    cell: WidgetSizeClass,
    previewHeight: Dp,
    onOpen: (WidgetPin.CatalogComponent) -> Unit,
    onEdit: (WidgetPin.CatalogComponent) -> Unit
) {
    // 点 = 进面板挑样式；长按 = 编辑桌面上的实例（两条入口之一，另一条是面板底部的编辑行）
    val chooseLabel = stringResource(R.string.widget_pin_choose_size)
    val editLabel = stringResource(R.string.widget_pin_edit_action)
    val onTileClick = { onOpen(component) }
    val onTileLongClick = { onEdit(component) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CATALOG_CELL_TAG)
            // 标签行不是语义边界（不可点），显式合并让读屏把「预览 + 名称」当一张卡念完，
            // 测试也能按卡片节点匹配文本
            .semantics(mergeDescendants = true) {}
    ) {
        CatalogPreview(
            component = component,
            cell = cell,
            height = previewHeight,
            chooseLabel = chooseLabel,
            editLabel = editLabel,
            onClick = onTileClick,
            onLongClick = onTileLongClick
        )
        // 名称行（用户 2026-10-24 定）：只有一行**居中**的名称，不带尺寸标注。
        // 2026-10-25 用户定：画廊标签用**短名**（半宽卡上长名截断），长名仍作面板标题。
        // 2026-10-21 用户定：「加号不需要了吧，必须点进去选择小组件再添加」——
        // 卡片上不再有快捷添加钮，落桌一律走「点卡片 → 面板 → 添加这个样式」。
        // 名称也可点（连点文字都点不动会显得卡失灵），波纹用一个小圆角块，别溢到卡外。
        Text(
            text = stringResource(component.titleShortRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NAME_TOP_PADDING)
                .padding(vertical = 3.dp)
                // 用手势而不是 clickable：名称节点一旦自带 clickable 就会成为语义边界，
                // 卡片整体的 mergeDescendants 收不到它的文本。名称本身不给波纹（波纹归卡片）。
                .pointerInput(component.familyKey) {
                    detectTapGestures(
                        onTap = { onTileClick() },
                        onLongPress = { onTileLongClick() }
                    )
                }
        )
    }
}

/**
 * 面板内容：顶栏（返回 + 应用身份）+ 头部（名称 / 说明，居中一叠）+ 尺寸翻页器（**吃掉中间余量**：
 * 大预览左右滑换尺寸，下缘一排分页圆点）+ 背景色 + 不透明度 + 添加/保存按钮（抽出来是为了能单独出图）。
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
    background: String?,
    books: Flow<List<AccountBook>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    /** 「编辑更多」层的草稿选择（状态提升在面板层，落桌时统一写入新实例）。 */
    chosenBook: Long? = null,
    onChooseBook: (Long?) -> Unit = {},
    chosenAccent: String? = null,
    onChooseAccent: (String?) -> Unit = {},
    onBack: () -> Unit,
    onSelectStyle: (WidgetPin.Entry) -> Unit,
    onSelectOpacity: (Float?) -> Unit,
    onSelectBackground: (String?) -> Unit,
    /** 图片背景的模糊度（0..1）；null = 不模糊。 */
    chosenBlur: Float? = null,
    onSelectBlur: (Float?) -> Unit = {},
    /** 各形态（与 [WidgetPin.CatalogComponent.entries] 同序）已落桌的实例数：尺寸标注打勾用。 */
    placedPerStyle: List<Int> = emptyList(),
    onAdd: () -> Unit,
    /** 非空 = 编辑已落桌实例：翻页器只剩这一页（不换样式），底部按钮换成「保存」。 */
    editEntry: WidgetPin.Entry? = null,
    onSave: () -> Unit = {}
) {
    // 「编辑更多」层（仅带配置页的组件有入口）：账本/配色的选择是**草稿**——
    // 状态提升在面板层（chosenBook/chosenAccent），落桌核对到新 id 后统一写入
    var editingBookAccent by remember { mutableStateOf(false) }
    BackHandler(enabled = editingBookAccent) { editingBookAccent = false }
    // 同一类型的多件样式（清单版 / 横条版 / 迷你版…）在这里左右滑动挑；编辑态定死在实例那个样式上
    val styles = editEntry?.let { listOf(it) } ?: component.entries

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SHEET_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        // 2026-10-25 收紧到 8dp：面板是定高的，固定块间距每省一截预览就多一截（原 12dp）
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (editingBookAccent) {
            // 编辑态：整块替换面板内容（MIUI 时钟「编辑城市」同款）——此前做成叠加，
            // 编辑层被面板高度裁掉，真机点进去什么都看不到
            BookAccentEditor(
                books = books.collectAsStateWithLifecycle(initialValue = emptyList()).value,
                showBooks = component.configureActivity != null,
                showAccent = component.accentEditable || component.configureActivity != null,
                chosenBook = chosenBook,
                onChooseBook = onChooseBook,
                chosenAccent = chosenAccent,
                onChooseAccent = onChooseAccent,
                onBack = { editingBookAccent = false },
                modifier = Modifier.weight(1f)
            )
        } else {
            SheetMainContent(
                component = component,
                selected = selected,
                styles = styles,
                bound = bound,
                pinSupported = pinSupported,
                verifying = verifying,
                opacity = opacity,
                background = background,
                placedPerStyle = placedPerStyle,
                chosenBlur = chosenBlur,
                onBack = onBack,
                onSelectStyle = onSelectStyle,
                onSelectOpacity = onSelectOpacity,
                onSelectBackground = onSelectBackground,
                onSelectBlur = onSelectBlur,
                onAdd = onAdd,
                onOpenEditor = { editingBookAccent = true },
                editEntry = editEntry,
                onSave = onSave
            )
        }
    }
}

/** 面板主内容（非「编辑更多」态）：顶栏 + 头部 + 翻页器 + 外观行 + 尾部按钮。 */
@Suppress("LongParameterList")
@Composable
private fun SheetMainContent(
    component: WidgetPin.CatalogComponent,
    selected: WidgetPin.Entry,
    styles: List<WidgetPin.Entry>,
    bound: Int,
    pinSupported: Boolean,
    verifying: Boolean,
    opacity: Float?,
    background: String?,
    placedPerStyle: List<Int>,
    chosenBlur: Float?,
    onBack: () -> Unit,
    onSelectStyle: (WidgetPin.Entry) -> Unit,
    onSelectOpacity: (Float?) -> Unit,
    onSelectBackground: (String?) -> Unit,
    onSelectBlur: (Float?) -> Unit,
    onAdd: () -> Unit,
    onOpenEditor: () -> Unit,
    editEntry: WidgetPin.Entry?,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        // 与外层同步收紧到 8dp（原 12dp）—— 预览不缩小的前提下塞下徽章/编辑行/标签行
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
    SheetTopBar(onBack = onBack)
    SheetHeader(
        component.titleRes,
        stringResource(component.descRes)
    )
        // 预览块吃掉中间余量：**同类样式的翻页器**——翻的是样式（尺寸档），大小交给桌面拖拽。
        StylePager(
            styles = styles,
            selected = selected,
            onSelectIndex = { onSelectStyle(styles[it]) },
            placedPerStyle = placedPerStyle,
            // 图片背景（"img:"）：真图解码 + 模糊当卡底（2026-10-25 用户定：要能预览）；
            // 解码失败（uri 失效）垫默认卡底，不留刺眼白块
            backgroundColor = background
                ?.takeIf { it.startsWith("#") }
                ?.toComposeColor(Color.White),
            backgroundImage = background?.takeIf { it.startsWith("img:") },
            blur = chosenBlur ?: 0f,
            cardOpacity = opacity ?: PreferencesManager.DEFAULT_WIDGET_OPACITY,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        // 背景与不透明度对**所有**组件直接可编：模糊度只对图片背景有意义，选了图片才出现。
        AppearanceRows(
            background = background,
            onSelectBackground = onSelectBackground,
            opacity = opacity,
            onSelectOpacity = onSelectOpacity,
            chosenBlur = chosenBlur,
            onSelectBlur = onSelectBlur
        )
        SheetFooter(
            component = component,
            bound = bound,
            pinSupported = pinSupported,
            verifying = verifying,
            editEntry = editEntry,
            onOpenEditor = onOpenEditor,
            onAdd = onAdd,
            onSave = onSave
        )
    }
}

/**
 * 面板尾部：「编辑更多」入口行（有额外编辑项的组件才有）+ 添加/保存按钮 + 已落桌实例入口。
 * 从 [WidgetPinSheetContent] 抽出为准其过函数长度门。
 */
@Composable
private fun SheetFooter(
    component: WidgetPin.CatalogComponent,
    bound: Int,
    pinSupported: Boolean,
    verifying: Boolean,
    editEntry: WidgetPin.Entry?,
    onOpenEditor: () -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit
) {
    // 「编辑更多」入口行（MIUI 时钟「编辑城市」同款）：有额外编辑项的组件才显示
    // （账单=账本+配色，概览/接下来=配色），点进去整块切换成编辑层，返回键逐层退。
    if (component.configureActivity != null || component.accentEditable) {
        EditEntryRow(
            onClick = onOpenEditor,
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (editEntry != null) {
        // 编辑已落桌实例：尺寸不能换（桌面上就那么大），按钮是写回该实例
        SheetSaveButton(onSave)
    } else {
        SheetAddButton(bound, pinSupported, verifying, onAdd)
        // 「编辑桌面上的 N 个」行已去掉（2026-10-25 用户定）：改已落桌实例走画廊卡长按
    }
}

/** 「编辑账本与配色」层：账本卡 + 配色卡，返回后面板预览实时反映背景/不透明度。 */
@Composable
private fun BookAccentEditor(
    books: List<AccountBook>,
    showBooks: Boolean,
    showAccent: Boolean,
    chosenBook: Long?,
    onChooseBook: (Long?) -> Unit,
    chosenAccent: String?,
    onChooseAccent: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SHEET_EDIT_ENTRY_TAG)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 复用面板返回键的 tag：编辑层也是面板栈的一层，测试按 tag 逐层退（页面顶栏的同文案键不会撞）。
            IconButton(onClick = onBack, modifier = Modifier.testTag(SHEET_BACK_TAG)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_navigate_back)
                )
            }
            Text(
                stringResource(R.string.widget_pin_edit_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (books.isNotEmpty() && showBooks) {
            BookListCard(books = books, selected = chosenBook, onSelect = onChooseBook, context = context)
        }
        if (showAccent) {
            AccentThemeCard(selected = chosenAccent, onSelect = onChooseAccent)
        }
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
private fun SheetHeader(
    titleRes: Int,
    desc: String
) {
    // 「已在桌面」徽章不进头部：放在预览页右上角（见 [StylePager]）——它会随翻页出现/消失，
    // 放标题旁边会把居中的标题顶得反复横跳（2026-10-25 用户定）
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
            desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 配置区的一行选项：文案 + 选中态 + 点击（尺寸与不透明度共用）。 */
private class PickerOption(val label: String, val selected: Boolean, val onClick: () -> Unit)


/**
 * 无描边实底胶囊：未选中走中性淡底 + 次级文字，选中走主色淡底 + 主色文字。
 *
 * 不用 M3 的 `FilterChip`：那颗未选中态带 1dp 描边，是应用内表单的语汇；系统选择器的选项行是
 * **实底无描边**（系统页的「文字颜色」圆片同一逻辑），面板里换成它才跟系统页一个体系。
 * `selectable` 挂在内层容器上，胶囊自带选中语义（与 `FilterChip` 的读屏口径一致）。
 */
@Composable
private fun PillOption(option: PickerOption) {
    // 底色与文字色都走一段短过渡：不透明度滑杆来回拖时，颜色突变比尺寸跳变更显生硬
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
 * 不透明度：**无极滑杆**（0.2–1.0 连续），拖动时预览实时跟着变。
 * 没拖过 = 跟随全局默认（落桌不写覆盖值，全局以后调了它还跟着）；一旦拖动就写成
 * 该实例的覆盖值 —— 拖回 100% 与全局默认数值相同，语义差异可忽略。
 *
 * 语义是**不透明度**（100% = 完全不透明、20% = 几乎全透），滑杆数值与 Photoshop/Figma 一致；
 * 只作用在背景层（卡底 + 盖住整卡的内层大色块），卡上的文字/图标保持实色不发灰。
 */
@Composable
private fun OpacityRow(selected: Float?, onSelect: (Float?) -> Unit) {
    val display = selected ?: PreferencesManager.DEFAULT_WIDGET_OPACITY
    Row(
        modifier = Modifier.fillMaxWidth().testTag(SHEET_OPACITY_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.widget_pin_opacity_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(PICKER_LABEL_WIDTH)
        )
        Slider(
            value = display,
            onValueChange = { onSelect(it) },
            valueRange = PreferencesManager.MIN_WIDGET_OPACITY..1f,
            modifier = Modifier
                .weight(1f)
                .testTag(SHEET_OPACITY_SLIDER_TAG)
        )
        Text(
            "${(display * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
    }
}

internal const val SHEET_EDIT_ENTRY_TAG = "widget_pin_sheet_edit_entry"

/** 面板里模糊度滑杆行的测试标签（与不透明度一样常驻）。 */
internal const val SHEET_BLUR_ROW_TAG = "widget_pin_sheet_blur_row"
internal const val SHEET_BLUR_SLIDER_TAG = "widget_pin_sheet_blur_slider"

/**
 * 面板的「外观」行：背景色板 + 不透明度 + 模糊度。
 * 三行**常驻**（2026-10-25 用户定：不透明度/模糊度是常规调节项，不因背景类型显隐；
 * 纯色背景下模糊度无视觉效果但保留入口，行为与图片一致）。
 */
@Composable
private fun AppearanceRows(
    background: String?,
    onSelectBackground: (String?) -> Unit,
    opacity: Float?,
    onSelectOpacity: (Float?) -> Unit,
    chosenBlur: Float?,
    onSelectBlur: (Float?) -> Unit
) {
    BackgroundRow(selected = background, onSelect = onSelectBackground)
    OpacityRow(selected = opacity, onSelect = onSelectOpacity)
    BlurRow(selected = chosenBlur, onSelect = onSelectBlur)
}

/**
 * 模糊度滑杆（0–100%）。与不透明度同一套「标签 + 滑杆 + 百分比」排法，**常驻显示**：
 * 真正生效的是图片背景（`img:`）；纯色背景调了没有视觉效果，但选项不藏起来 ——
 * 免得用户以为「只有换了图片才能调」（2026-10-25 用户定）。
 */
@Composable
private fun BlurRow(selected: Float?, onSelect: (Float?) -> Unit) {
    val display = selected ?: 0f
    Row(
        modifier = Modifier.fillMaxWidth().testTag(SHEET_BLUR_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.widget_pin_blur_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(PICKER_LABEL_WIDTH)
        )
        Slider(
            value = display,
            onValueChange = { onSelect(it) },
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .testTag(SHEET_BLUR_SLIDER_TAG)
        )
        Text(
            "${(display * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
    }
}

/** 面板里的一行编辑入口：左标题、右箭头（MIUI 时钟「编辑城市」同款）。 */
@Composable
private fun EditEntryRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(44.dp)
            .testTag(SHEET_EDIT_ENTRY_TAG)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.widget_pin_edit_entry),
            style = MaterialTheme.typography.bodyLarge
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.widget_pin_edit_entry),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 背景色板行：五个预设色块（白 = 默认）+ 图片胶囊（2026-10-24 用户定：去自定义色盘）。
 * 白块点击 = 不接管（null，回默认卡底）；其余写 "#RRGGBB" 大写，与 [WidgetWallpaper] 的解析口径一致。
 */
@Composable
internal fun BackgroundRow(selected: String?, onSelect: (String?) -> Unit) {
    val context = LocalContext.current
    // 图片背景走系统文档选择器：takePersistableUriPermission 让 uri 在进程重启后仍可读。
    // 拿不到持久授权时**不能**写进偏好：进程重启后解码必失败，组件会静默回退默认卡底，
    // 用户只会觉得「我选的图丢了」。当场提示、保持原选择更诚实。
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            if (granted) {
                onSelect("img:$uri")
            } else {
                Toast.makeText(
                    context,
                    R.string.widget_pin_background_permission_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    // 换背景 / 清除背景时释放上一条图片的持久授权：系统对每应用能持久持有的 URI 有上限，
    // 只 take 不 release 的话，反复换图最终会让新图再也拿不到授权。
    val selectBackground: (String?) -> Unit = { next ->
        val previous = selected
        if (previous != next && previous?.startsWith("img:") == true) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(previous.removePrefix("img:")),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        onSelect(next)
    }

    Column(modifier = Modifier.fillMaxWidth().testTag(SHEET_BG_ROW_TAG)) {
        // 「背景颜色」加粗独立一行（MIUI 配色选择的样式，用户 2026-10-19 定），色块排在其下
        Text(
            stringResource(R.string.widget_pin_background_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        BackgroundOptionsRow(
            selected = selected,
            onSelect = selectBackground,
            onPickImage = { imageLauncher.launch(arrayOf("image/*")) },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** 背景选项行：默认（白）+ 四个深色预设 + 「图片」，横向可滚。 */
@Composable
private fun BackgroundOptionsRow(
    selected: String?,
    onSelect: (String?) -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PILL_GAP)
    ) {
        BACKGROUND_SWATCHES.forEachIndexed { index, argb ->
            // 白（首块）= 默认卡底：不接管；选中态按「null 或 #FFFFFF」认
            val isDefault = index == 0
            val hex = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
            val isSel = if (isDefault) {
                selected == null || selected.equals(hex, ignoreCase = true)
            } else {
                selected.equals(hex, ignoreCase = true)
            }
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (isSel) 2.dp else 1.dp,
                        color = if (isSel) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
                    .selectable(selected = isSel, role = Role.RadioButton) {
                        onSelect(if (isDefault) null else hex)
                    }
            )
        }
        PillOption(
            PickerOption(
                label = stringResource(R.string.widget_pin_background_image),
                selected = selected?.startsWith("img:") == true,
                onClick = onPickImage
            )
        )
    }
}

/**
 * 同类样式的翻页器：同一类型的小组件（「待办清单」的清单版 / 横条版 / 迷你版）放在一个弹窗里，
 * 左右滑动挑（2026-10-21 用户定：「同一类型、种类的小组件可以放在一个弹窗里面，左右滑动选择」）。
 *
 * **翻的是样式，不是大小**：大小交给桌面拖拽，组件按实际宽高自适应重排
 * （各 provider 在 `onAppWidgetOptionsChanged` 里重绘）。
 *
 * 用 `HorizontalPager` 而不是自己接 `detectHorizontalDragGestures`：拖动时预览跟着手指走、
 * 松手按**甩动速度**落页，圆点在拖动过程中就连续跟着变 —— 之前是「松手才跳一下」，
 * 拖动期间毫无反馈，所以滑起来发涩。
 *
 * 页高按**全局最大卡**（4×3）算，不按本组最大的那个样式，这条是刻意钉住的：
 * ① 页高 = 页内缩放系数的分母，按本组最大算的话，只有小件的组会被整体缩小；
 * ② 组件之间对齐 —— 不管打开哪个组件，圆点行都落在面板里同一行上，翻页时容器也不跳。
 */
@Composable
private fun StylePager(
    styles: List<WidgetPin.Entry>,
    selected: WidgetPin.Entry,
    onSelectIndex: (Int) -> Unit,
    placedPerStyle: List<Int> = emptyList(),
    backgroundColor: Color?,
    backgroundImage: String? = null,
    blur: Float = 0f,
    cardOpacity: Float,
    modifier: Modifier = Modifier
) {
    val selectedIndex = styles.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { styles.size }
    // 落页后回报上层（写回选中样式再落桌）；首帧那一发就是开页用的初始页，跳过不回报
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
            // 与 [WidgetPreview] 同一条系数：预览按桌面真实尺寸 1:1 尺寸封顶
            val scale = minOf(maxWidth.value / DESIGN_MAX_WIDTH, 1f)
            // 封顶时扣掉下方标签行的高度：标签行永远可见（见 STYLE_TABS_RESERVED_DP 的说明）
            val track = (DESIGN_MAX_HEIGHT * scale)
                .coerceAtMost((maxHeight.value - STYLE_TABS_RESERVED_DP).coerceAtLeast(0f))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(track.dp),
                // 只有一件样式就没什么可翻的：关掉手势，免得白划一下
                userScrollEnabled = styles.size > 1
            ) { page ->
                val entry = styles[page]
                StylePage(
                    entry = entry,
                    isCurrent = entry == selected,
                    backgroundColor = backgroundColor,
                    backgroundImage = backgroundImage,
                    blur = blur,
                    cardOpacity = cardOpacity
                )
            }
        }
        StyleTabs(
            styles = styles,
            pagerState = pagerState,
            placedPerStyle = placedPerStyle,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * 翻页器的一页：预览卡。从 [StylePager] 抽出为准函数长度门。
 * 「已在桌面」徽章已删（2026-10-25 用户定）：尺寸标签行自带的「✓」前缀就是落桌标记，
 * 徽章试过头部/标题行/预览角三个位置都不理想。
 */
@Composable
private fun StylePage(
    entry: WidgetPin.Entry,
    /** 是否为当前选中页（只有当前页挂预览的测试标签，免得相邻页撞出多节点）。 */
    isCurrent: Boolean,
    backgroundColor: Color?,
    backgroundImage: String?,
    blur: Float,
    cardOpacity: Float
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        WidgetPreview(
            imageRes = entry.previewImageRes,
            designSize = WidgetPin.designSizeOf(entry),
            shadow = FLOATING_SHADOW,
            backgroundColor = backgroundColor,
            backgroundImage = backgroundImage,
            blur = blur,
            cardOpacity = cardOpacity,
            // 卡身圆角按档（稿 §1）：预览的裁切/投影与真机卡同形
            corner = designCornerOf(WidgetPin.designSizeOf(entry)),
            // 预览里的文字/图标与卡底同源：白底深字、其他背景色浅字
            // （与桌面上 WidgetTonePicker 同一套判定；默认卡底按系统明暗）
            contentTone = WidgetTonePicker.of(
                LocalContext.current,
                backgroundColor?.toArgb()
            ),
            tag = if (isCurrent) SHEET_PREVIEW_TAG else SHEET_PREVIEW_IDLE_TAG,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 样式分页标签行（2026-10-24 用户定：每个尺寸单独标注，已添加的单独打勾）。
 *
 * 每个形态一枚胶囊：文本 = 尺寸标（4×2 / 2×2 / …），已落桌的加「✓」前缀；
 * 激活态主色实底白字（颜色随翻页连续过渡），未选灰底；点击直接跳到该页。
 * 单样式（编辑态）也显示——用户要看清自己编辑的是哪一档。
 */
@Composable
private fun StyleTabs(
    styles: List<WidgetPin.Entry>,
    pagerState: PagerState,
    placedPerStyle: List<Int>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val active = pagerState.currentPage + pagerState.currentPageOffsetFraction
    Row(
        modifier = modifier.testTag(SHEET_DOTS_TAG),
        horizontalArrangement = Arrangement.spacedBy(STYLE_DOT_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        styles.forEachIndexed { index, entry ->
            val away = (index - active).absoluteValue.coerceIn(0f, 1f)
            val placed = placedPerStyle.getOrNull(index)?.let { it > 0 } == true
            val label = (if (placed) "✓ " else "") + WidgetPin.cellsOf(entry)
            val bg = lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                1f - away
            )
            val fg = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.onPrimary,
                1f - away
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(bg)
                    .testTag(SHEET_DOT_TAG)
                    .clickable {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
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
 * 面板里的「保存」按钮（编辑已落桌实例用）：与添加按钮同一颗的体量，文案换成「保存」。
 * 保存即写回该实例，不需要核对期，所以不置灰。
 */
@Composable
private fun SheetSaveButton(onSave: () -> Unit) {
    Button(
        onClick = onSave,
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier
            .fillMaxWidth()
            .height(SHEET_ADD_HEIGHT)
            .testTag(SHEET_SAVE_TAG)
    ) {
        Text(
            stringResource(R.string.widget_pin_save),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 组件预览：按它在**桌面上的真实占格尺寸**等比摆放，**不垫任何底托** —— 画廊里它直接浮在
 * 页面底色上（与其他页面同一个 background），面板里浮在白底上，两种场合都只有 widget 自己。
 *
 * 缩放取 `min(可用宽/设计宽, 可用高/设计高, maxScale)`：画廊里调用方给的是「列宽 × 行高」的卡位，
 * 因此 widget 会**按列宽铺满**（受 [SHOWCASE_MAX_SCALE] 防糊），面板里则是 1:1 封顶的「真尺寸预览」。
 * 尺寸只跟可用空间有关，不随屏幕密度变。
 *
 * @param shadow 投影浓度（会再乘缩放系数）：画廊小卡取 [PREVIEW_SHADOW]，面板大预览取 [FLOATING_SHADOW]。
 * @param maxScale 放大上限：画廊给 [SHOWCASE_MAX_SCALE]（铺满列宽），面板保持默认 1:1。
 */
@Suppress("LongParameterList") // 参数全是「预览怎么摆」的独立开关，与 [WidgetPreviewCard] 同一口径
@Composable
private fun WidgetPreview(
    imageRes: Int,
    designSize: Pair<Int, Int>?,
    shadow: Dp,
    tag: String,
    modifier: Modifier = Modifier,
    /** 该实例的背景色（hex 由调用方转 Color）；null = 默认卡底（画廊/选择器，昼夜自适应）。 */
    backgroundColor: Color? = null,
    /** 卡片不透明度：弹窗里拖的值实时套到垫底色上。 */
    cardOpacity: Float = 1f,
    /**
     * 预览内容（PNG 里的文字/图标）取哪一套：`null` = 跟随当前配置（画廊用，与改动前一致）；
     * 面板里由**卡片色调**决定 —— 深色卡配浅字那套，浅色卡配深字那套，与卡上内容同源。
     */
    contentTone: WidgetTone? = null,
    maxScale: Float = 1f,
    /** 卡身圆角（1dp 基准）：画廊按档给 24/22，面板默认 22。 */
    corner: Dp = WIDGET_CARD_CORNER,
    /** 图片背景（"img:<uri>" 原样）：非空时解码真图铺卡底，配合 [blur]。 */
    backgroundImage: String? = null,
    /** 图片背景的模糊度 0..1。 */
    blur: Float = 0f
) {
    val defaultCard = colorResource(R.color.widget_v2_card_bg)
    val cardColor = (backgroundColor ?: defaultCard).copy(alpha = cardOpacity)
    /**
     * 面板走哪套画法，与真机 `widgetViews` 的 `cardRelative` 同一条件：
     * 默认卡底 + 100% 才用令牌色面板，其余都是「卡底 + 极淡覆盖层」。
     */
    val cardRelative = backgroundColor != null || cardOpacity < 1f
    val content: @Composable BoxWithConstraintsScope.() -> Unit = {
        val (designW, designH) = designSize ?: FALLBACK_SIZE
        // 宽项按**组件自己的**设计宽算（不是全局 340 基准）：画廊里 widget 要铺满列宽，
        // 2×2 才不会被 340 的分母压成半个格；高项同样按自身设计高，卡位高矮不参与比例计算。
        val scale = minOf(
            maxWidth.value / designW,
            maxHeight.value / designH,
            maxScale
        )
        val bitmap = rememberPreviewBitmap(imageRes, contentTone)
        val surface = rememberSurfaceBitmap(imageRes, contentTone)
        val blocks = rememberBlocksBitmap(imageRes, contentTone)
        val chip = rememberChipBitmap(imageRes, contentTone)
        val photo = rememberBackgroundPhoto(backgroundImage, blur)
        Box(modifier = Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
            WidgetPreviewCard(
                bitmap = bitmap,
                surface = surface,
                blocks = blocks,
                chip = chip,
                cardColor = cardColor,
                cardRelative = cardRelative,
                overlayTone = contentTone ?: WidgetTone.ofSystem(LocalContext.current),
                scale = scale,
                designSize = designW to designH,
                shadow = shadow,
                corner = corner,
                photo = photo,
                tag = tag
            )
        }
    }
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * 图片背景的预览位图：解码 → 缩到卡内尺寸 →（blur>0 时）复用渲染端同款盒模糊。
 * 失败（uri 失效）返回 null，预览垫默认卡底；IO 在 Dispatchers.IO，
 * produceState 异步加载、先 null 后图，不阻塞面板首帧。
 */
@Composable
private fun rememberBackgroundPhoto(backgroundImage: String?, blur: Float): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    return produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        backgroundImage, blur
    ) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val uri = backgroundImage?.removePrefix("img:") ?: return@withContext null
            val decoded = decodeWallpaperBitmap(context, uri, 680, 592) ?: return@withContext null
            var bmp = decoded.asAndroidBitmap()
            if (blur > 0f) bmp = com.palmnote.ui.widget.WidgetWallpaper.blurForPreview(bmp, blur)
            bmp.asImageBitmap()
        }
    }.value
}

/**
 * 预览 PNG 解码：按 `contentTone` 挑浅色/夜间那一套资源（与卡上内容同源），
 * 直接给 High 采样 —— 源是 3x 位图，默认 Low 的下采样会把边缘糊掉。
 */
@Composable
private fun rememberPreviewBitmap(imageRes: Int, contentTone: WidgetTone?): androidx.compose.ui.graphics.ImageBitmap {
    val context = LocalContext.current
    return remember(imageRes, contentTone) {
        val resources = contentTone
            ?.let { WidgetToneContext.of(context, it).resources }
            ?: context.resources
        BitmapFactory.decodeResource(resources, imageRes).asImageBitmap()
    }
}

/**
 * 面板层 PNG 解码（与内容层同一套明暗资源）：按资源名后缀约定查找，没有就返回 null，
 * 预览退回单层（见 [WidgetPin.surfacePreviewRes]）。
 */
@Composable
private fun rememberSurfaceBitmap(
    imageRes: Int,
    contentTone: WidgetTone?
): androidx.compose.ui.graphics.ImageBitmap? =
    rememberLayerBitmap(imageRes, contentTone, WidgetPin.SURFACE_SUFFIX)

/**
 * 内层色砖层（概览四块砖 / 快捷入口四格）：颜色由组件给，预览里只跟实例不透明度淡出。
 * 大多数形态没有这一层，返回 null。
 */
@Composable
private fun rememberBlocksBitmap(
    imageRes: Int,
    contentTone: WidgetTone?
): androidx.compose.ui.graphics.ImageBitmap? =
    rememberLayerBitmap(imageRes, contentTone, WidgetPin.BLOCKS_SUFFIX)

/**
 * 胶囊层 PNG 解码（与内容层同一套明暗资源）：按资源名后缀约定查找，没有就返回 null，
 * 预览跳过（见 [WidgetPin.chipPreviewRes]）。
 */
@Composable
private fun rememberChipBitmap(
    imageRes: Int,
    contentTone: WidgetTone?
): androidx.compose.ui.graphics.ImageBitmap? =
    rememberLayerBitmap(imageRes, contentTone, WidgetPin.CHIP_SUFFIX)

@Composable
private fun rememberLayerBitmap(
    imageRes: Int,
    contentTone: WidgetTone?,
    suffix: String
): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    return remember(imageRes, contentTone, suffix) {
        val resources = contentTone
            ?.let { WidgetToneContext.of(context, it).resources }
            ?: context.resources
        val res = when (suffix) {
            WidgetPin.SURFACE_SUFFIX -> WidgetPin.surfacePreviewRes(resources, imageRes)
            WidgetPin.CHIP_SUFFIX -> WidgetPin.chipPreviewRes(resources, imageRes)
            else -> WidgetPin.blocksPreviewRes(resources, imageRes)
        }
        if (res == 0) null else BitmapFactory.decodeResource(resources, res)?.asImageBitmap()
    }
}

/**
 * 预览卡本身：**示例壁纸（<100% 时，卡内）→ 卡底（用户选的背景色 @ 不透明度）→ 内层色砖（同 alpha）
 * → 面板 → 胶囊 → 内容层（实色）**。
 *
 * 示例壁纸只做半透明档的参照（见 [PREVIEW_WALLPAPER_LIGHT]）：100% 时被卡底完全盖住，
 * 和没有这一层时逐像素相同；投影也跟着卡一起透，低档位不会剩一圈比卡身还实的"假边"。
 *
 * 面板有两种画法，与真机同一条件：
 * - 默认卡底 + 100%（`cardRelative = false`）：面板层 PNG 原色叠，不透明度当 alpha；
 * - 其余（`cardRelative = true`）：面板层当**白模**，按卡底明暗 tint 成极淡白/黑、按固定
 *   [WidgetToneSweep.SURFACE_OVERLAY_ALPHA] 叠在卡底上 —— 面板永远只比卡底差一档，
 *   不会因为「半透明面板又叠在半透明卡底上」越叠越深（2026-10-20 用户反馈）。
 *
 * 胶囊（「新增」的底）也有两种画法：默认卡底/浅色卡按原色叠；卡片相对 + 深色卡 tint 成
 * [CHIP_OVERLAY_ALPHA] 的白 —— 那颗语义 tint 按近黑卡调的深棕底，落在彩卡上就是一块黑
 * （2026-10-20 用户反馈）。
 *
 * 预览图的卡底已清成透明、面板/色砖/胶囊/内容拆成四张 PNG（见 `WidgetPreviewPngGenerator`），
 * 所以不透明度能实时、且和真机一致。
 */
@Suppress("LongParameterList")
@Composable
private fun WidgetPreviewCard(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    surface: androidx.compose.ui.graphics.ImageBitmap?,
    blocks: androidx.compose.ui.graphics.ImageBitmap?,
    chip: androidx.compose.ui.graphics.ImageBitmap?,
    cardColor: Color,
    cardRelative: Boolean,
    overlayTone: WidgetTone,
    scale: Float,
    designSize: Pair<Int, Int>,
    /** 投影浓度（调用方给 [PREVIEW_SHADOW] / [FLOATING_SHADOW]，这里再乘缩放系数）。 */
    shadow: Dp,
    /** 卡身圆角（1dp 基准，随缩放系数走）：稿按档给值（4×2=24 / 2×2=22 / 4×1=20 / 2×1=18）。 */
    corner: Dp = WIDGET_CARD_CORNER,
    /** 图片背景位图（已按 [rememberBackgroundPhoto] 模糊好）；null = 无图片背景。 */
    photo: androidx.compose.ui.graphics.ImageBitmap? = null,
    tag: String
) {
    val (designW, designH) = designSize
    val cardShape = RoundedCornerShape(corner * scale)
    Box(
        modifier = Modifier
            .shadow(
                elevation = shadow * scale,
                shape = cardShape,
                // 投影跟卡一起透：否则 20% 的卡会拖一圈 100% 的柔影，看着像边框比卡身还实
                ambientColor = Color.Black.copy(alpha = 0.24f * cardColor.alpha),
                spotColor = Color.Black.copy(alpha = 0.24f * cardColor.alpha)
            )
            .size((designW * scale).dp, (designH * scale).dp)
            .testTag(tag)
    ) {
        PreviewWallpaper(cardColor.alpha, cardShape, Modifier.matchParentSize())
        // 图片背景：真图铺满卡（含模糊），再叠 cardColor 的 alpha（不透明度 <100% 时透出底托）
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(cardShape)
            )
        }
        Box(Modifier.matchParentSize().background(cardColor, cardShape))
        if (blocks != null) {
            // 色砖是背景层，跟卡底一起透；颜色是组件给的语义色，不参与面板 tint
            PreviewLayerImage(blocks, colorFilter = null, alpha = cardColor.alpha)
        }
        if (surface != null) {
            if (cardRelative) {
                val overlay = if (overlayTone == WidgetTone.DARK) Color.White else Color.Black
                // 白模只借 alpha：tint 换成极淡白/黑，再按固定的 6% 叠在卡底上
                PreviewLayerImage(
                    surface,
                    colorFilter = ColorFilter.tint(overlay, BlendMode.SrcIn),
                    alpha = WidgetToneSweep.SURFACE_OVERLAY_ALPHA
                )
            } else {
                // 默认外观：面板层按原色叠，与实例不透明度同一档
                PreviewLayerImage(surface, colorFilter = null, alpha = cardColor.alpha)
            }
        }
        if (chip != null) {
            // 胶囊层两种画法，与真机 bindAddButton 同一条件（WidgetPin.CHIP_SUFFIX）：
            // 卡片相对 + 深色卡 → 当白模 tint 成 88% 白（真机换上 widget_pill_chip，同值）；
            // 其余（默认卡底 / 浅色卡）→ 原色叠 —— 此时不透明度必为 100%，不用淡出。
            if (cardRelative && overlayTone == WidgetTone.DARK) {
                PreviewLayerImage(
                    chip,
                    colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
                    alpha = CHIP_OVERLAY_ALPHA
                )
            } else {
                PreviewLayerImage(chip, colorFilter = null, alpha = null)
            }
        }
        PreviewLayerImage(bitmap, colorFilter = null, alpha = null)
    }
}

/**
 * 预览分层里的一张整卡图：内容 / 面板 / 色砖 / 胶囊四层同一条画法 —— 整卡铺满、高清采样，
 * 颜色与透明度交给调用方（语义层原色直叠；白模层靠 tint 换色 + alpha 出那一档差）。
 */
@Composable
private fun BoxScope.PreviewLayerImage(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    colorFilter: ColorFilter?,
    alpha: Float?,
    modifier: Modifier = Modifier
) {
    val alphaModifier = if (alpha != null) Modifier.alpha(alpha) else Modifier
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
        colorFilter = colorFilter,
        modifier = modifier.matchParentSize().then(alphaModifier)
    )
}

/**
 * 半透明卡的卡内示例壁纸（见 [PREVIEW_WALLPAPER_LIGHT]）。
 *
 * 只在 <100% 时画：100% 时它被卡底完全盖住，省这一层。圆角跟卡一致，
 * 外层 `shadow` 还会再按卡形裁一次 —— 圆角外不会露出。
 */
@Composable
private fun PreviewWallpaper(alpha: Float, shape: Shape, modifier: Modifier = Modifier) {
    if (alpha >= 1f) return
    val tone = WidgetTone.ofSystem(LocalContext.current)
    val color = if (tone == WidgetTone.DARK) PREVIEW_WALLPAPER_DARK else PREVIEW_WALLPAPER_LIGHT
    Box(modifier.background(color, shape))
}

/**
 * 权限入口（**放页面顶部**，2026-10-25 用户定）：
 * - 桌面不支持应用内添加：琥珀警示块，只能退回系统那条路（这时才提，平时不讲）；
 * - 当前桌面 pin 前会校验「桌面快捷方式」权限（[WidgetPin.showHomePermStrip]）：
 *   **从未成功落桌** → 琥珀警示条「去开启 ›」（没开权限时添加会静默失败，得让它一眼跳出来）；
 *   **成功落桌过** → 警示块收成**单行**（同款琥珀底 16%、琥珀字），入口仍常驻可点 ——
 *   权限之后被关掉时本侧感知不到（pinOk 只增不删），得随时能进设置核实（2026-10-25 用户定）。
 * 点整条跳应用详情页（桌面快捷方式开关在那）。
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
    if (!homeNeedsPerm) return

    val context = LocalContext.current
    // 该「桌面快捷方式」开关不在任何公开 API 里，只能带用户去应用详情页手动开
    val openPermSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
        )
    }
    if (!WidgetPin.showHomePermStrip(pinSupported = pinSupported, homeNeedsPerm = homeNeedsPerm, pinOk = pinOk)) {
        // 成功落桌过一次：警示块收成**单行**，但同样带琥珀底色——权限之后被关掉时（pinOk 不回退，
        // 感知不到）添加会重新静默失败，入口得一直可见且能进设置核实（2026-10-25 用户定：也要颜色提示）。
        // 右侧带关闭叉：用户手动关掉就持久不再显示（进设置走系统那条路）
        PermEntryBar(context = context, onClick = openPermSettings)
        return
    }

    InfoBlock(
        title = stringResource(R.string.widget_page_perm_title),
        body = stringResource(R.string.widget_page_perm_desc),
        action = stringResource(R.string.widget_page_perm_action),
        onClick = openPermSettings
    )
}

/**
 * 成功落桌后的**单行**权限入口：琥珀底 16% + 琥珀字，右侧关闭叉。
 * 用户点叉持久记一笔（[WidgetPin.dismissPermEntry]），之后进页面不再出现 ——
 * 权限事后被关掉时本侧感知不到（pinOk 只增不删），但用户已明确不要常驻，尊重关闭。
 */
@Composable
private fun PermEntryBar(context: Context, onClick: () -> Unit) {
    var dismissed by remember { mutableStateOf(WidgetPin.permEntryDismissed(context)) }
    if (dismissed) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.warning().copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 整行做点击层：波纹铺满整条并按 Surface 同形圆角裁切（2026-10-25 用户定：
                // 涟漪覆盖完整、贴合卡形 —— 与 [CatalogPreview] 点击层同一手法）。
                // 叉的 IconButton 是子级：点它自己消费、出它自己的涟漪，不会误触主动作
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.widget_page_perm_entry),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.warning(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
            // 关闭叉走标准 IconButton（48dp 触控盒，Material 无障碍规范）——不豁免不绕行，
            // 单行入口因此高一些，规范优先（2026-10-25 用户定）
            IconButton(onClick = {
                WidgetPin.dismissPermEntry(context)
                dismissed = true
            }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.warning()
                )
            }
        }
    }
}

/** 短提示：添加结果只在这里回报，所以只报核对过的事实。 */
private fun toast(context: Context, resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
}

/**
 * 提示块：标题 + 可选说明 + 可选行内动作。
 * 2026-10-25 用户定：加**警示色**（琥珀底 + 深琥珀字），不再用中性淡蓝——
 * 权限没开时添加会静默失败，得让它一眼跳出来。
 */
@Composable
private fun InfoBlock(title: String, body: String?, action: String?, onClick: () -> Unit) {
    val warn = MaterialTheme.colorScheme.warning()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = warn.copy(alpha = 0.16f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
                    Text(it, color = warn)
                }
            }
        }
    }
}
