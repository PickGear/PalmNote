package com.palmnote.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import com.palmnote.app.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 按布局把「写死的浅色令牌」改写成一个色调对应的颜色。
 *
 * 表不是手写的：把布局 inflate 一遍、逐个 view 比对它**当天解析出来**的颜色落在哪个令牌上，
 * 得出「这个布局里哪些 id 需要改写」。好处是布局改了表自动跟上（不会漏），
 * `WidgetToneSweepTest` 再从 XML 侧做一次交叉核对。
 *
 * 只认三类令牌，刻意收得很窄：
 * - 正文/次要/三级文字（换色后深浅两套对比度都成立）；
 * - 内层面板底色（`widget_item_bg` / `widget_chip_bg` / 软卡两色 / 天数胶囊）—— 不一起换的话
 *   深色卡上会留下一块块浅色板，而板上的字已经变浅了，直接读不了；
 * - 分隔线/轨道色 `widget_v2_ring`。
 * 强调色、色族 tint/hue、警示红这些**语义色不在这里动**（它们由组件按色调自己取，见
 * [WidgetToneContext]），所以不会把「今日高亮」「逾期红」抹平。
 *
 * 每次建 RemoteViews 都会扫，色调等于系统时写回去的就是布局自己的颜色（外观不变）。这不是浪费：
 * launcher 复用同一棵视图树，只有把颜色重新下发一遍，才不会残留上一轮自定义背景的旧文字色/旧 tint。
 *
 * 面板底有两种写法：
 * - **默认卡底**（没有自定义背景、不透明度 100%）：写令牌色，与布局自己解析出来的完全一样；
 * - **卡片相对**（[apply] 的 `cardRelative`，自定义背景 / 不透明度 < 100% 时）：
 *   写一层**固定 6% 的白或黑覆盖层**，叠在卡底上永远只差一档。为什么不写「令牌色 × 实例 alpha」：
 *   那等于在已经半透的卡底上再叠一层半透面板，50% 黑底在浅壁纸上会变成「浅灰卡边 + 深灰面板」，
 *   正是用户反馈的「卡底与内部面板深浅不一致」（2026-10-20 实测）。
 */
internal object WidgetToneSweep {

    /**
     * 面板相对卡底的覆盖层不透明度：6%。
     *
     * 与实例不透明度**无关** —— 覆盖层是「卡底上的一档提亮/压暗」，卡本身多透它就多透。
     * 方向跟 [apply] 传进来的 `tone`（也就是卡上内容那套令牌：白底深字、其他背景浅字）。
     * 6% 按现有令牌的观感定：日间 `item_bg` 比白卡暗 3%、夜间 `item_bg` 比黑卡亮 1.6%；
     * 再淡在青底/橙底上看不出层次，再重又会让黑卡上的板变成一块灰砖。
     */
    internal const val SURFACE_OVERLAY_ALPHA = 0.06f

    /** 卡片相对模式的面板覆盖层：深色卡叠极淡白、浅色卡叠极淡黑。 */
    internal fun surfaceOverlay(tone: WidgetTone): Int {
        val channel = if (tone == WidgetTone.DARK) 255 else 0
        return Color.argb((SURFACE_OVERLAY_ALPHA * 255f).roundToInt(), channel, channel, channel)
    }

    /**
     * 语义 tint 胶囊（账单「新增」）的 id：**刻意不进** [BACKGROUND_TOKENS] ——
     * 语义色不参与中性扫色（防把高亮/警示抹平，见类 KDoc）。但自选深色背景时，按近黑卡
     * 调的那颗深棕胶囊（`widget_tint_1` 夜间 #3A2A1C）落在彩卡上就是一块黑
     * （2026-10-20 用户反馈），要翻成浅色胶囊。
     *
     * 翻转的**应用**在 BillWidgetProvider（胶囊字是组件自己的强调色，只有组件知道配什么），
     * 这里只做登记：预览分层（`WidgetPreviewPngGenerator`）按同一份 id 把胶囊底单独出一层，
     * 面板才能把它 tint 成浅色 —— 与 [isSurface] 同一条「预览与真机同一套判定」的线。
     * 其他组件以后有同样需求，把 id 补进来即可。
     */
    internal val chipIds = intArrayOf(R.id.widget_add_btn)

    /**
     * 轨道 / 空柱的画法（颜色 + 不透明度）：走**卡底相对**，不用写死的 `widget_v2_ring` 令牌。
     *
     * 固定令牌只对着默认卡底调过（夜 #2A312E 对近黑卡、日 #E9EDEC 对白卡，本就是「一档差」），
     * 用户自选彩卡时它跟着色调翻成深色就成了黑块（2026-10-20 反馈）。
     * 深色卡：**白 + 6%**（与 [surfaceOverlay] 同一档差）。注意不能指望 colorFilter 透出 alpha ——
     * `ImageView.setColorFilter(int)` 走的是 SRC_ATOP，只换色不换透明度：半透白会叠在 drawable
     * 自己的深色上，实测夜轨变成 (55,61,58)，还是深。透出的 6% 靠 `setImageAlpha` 出。
     * 浅色卡（只有白底会落到这档）：日间那档的浅灰轨 + 不透明 —— 与默认卡底观感一致，
     * 同时防「系统深色 + 白底」时 drawable 自己解析成夜轨。
     * 预览侧同源：内容层按卡底合成，默认卡上与令牌数值几乎重合，彩卡上自动浅一档。
     */
    fun trackStyleOf(context: Context): TrackStyle {
        val tone = (context as? WidgetToneContext)?.tone ?: WidgetTone.ofSystem(context)
        return if (tone == WidgetTone.DARK) {
            TrackStyle(Color.WHITE, (SURFACE_OVERLAY_ALPHA * 255f).roundToInt())
        } else {
            TrackStyle(context.colorInTone(WidgetTone.LIGHT, R.color.widget_v2_ring), 255)
        }
    }

    /** 轨道 / 空柱的画法：颜色 + 不透明度（见 [trackStyleOf]）。 */
    data class TrackStyle(val color: Int, val alpha: Int)

    private val TEXT_TOKENS = intArrayOf(
        R.color.widget_v2_text_primary,
        R.color.widget_v2_text_secondary,
        R.color.widget_v2_text_tertiary,
        // v22 中性文字（稿 --t1/t2/t3）：新构件布局写死这三档，自选深色背景时要一并扫
        R.color.widget_v22_text_1,
        R.color.widget_v22_text_2,
        R.color.widget_v22_text_3
    )

    private val BACKGROUND_TOKENS = intArrayOf(
        R.color.widget_v2_item_bg,
        R.color.widget_v2_chip_bg,
        R.color.widget_v2_soft_mint,
        R.color.widget_v2_soft_gray,
        R.color.widget_v2_days_bg,
        R.color.widget_v2_ring
    )

    private const val KIND_TEXT = 0

    /** 底色来自纯色（`android:background="@color/..."`）：没有形状，换色就是换色。 */
    private const val KIND_BG_COLOR = 1

    /** 底色来自 shape drawable：换色时要保住圆角，31 以下靠换回原 drawable。 */
    private const val KIND_BG_SHAPE = 2

    private class Entry(val id: Int, val token: Int, val kind: Int)

    /**
     * 底色令牌 → 承载它的 shape drawable（API 31 以下「复位」用：换回原 drawable 才能保留圆角）。
     * 一个令牌只对应一个 shape drawable（由 `drawable/` 侧保证）；纯色底（[KIND_BG_COLOR]）用不到这张表。
     */
    private val SURFACE_DRAWABLES = mapOf(
        R.color.widget_v2_item_bg to R.drawable.widget_item_bg,
        R.color.widget_v2_chip_bg to R.drawable.widget_chip_bg,
        R.color.widget_v2_days_bg to R.drawable.widget_days_bg,
        R.color.widget_v2_soft_mint to R.drawable.widget_soft_card,
        R.color.widget_v2_soft_gray to R.drawable.widget_soft_card_gray,
        // 轨道色多数场合是纯色底（divider），唯一用 drawable 的是 todo 预览里的进度轨道
        R.color.widget_v2_ring to R.drawable.widget_bar_track
    )

    /** 布局 → 改写表。只跟布局内容有关（用强制浅色的 Context 解析），可进程内长存。 */
    private val cache = ConcurrentHashMap<Int, List<Entry>>()

    /**
     * @param context 取色用的 Context，必须已经解析到 [tone]（见 [widgetViews]）。
     * @param tone 该布局该用的色调。与系统一致时下发的是**布局自己的颜色**，因此这一步也是复位 ——
     *   上一轮自定义背景留在 view 上的旧色（文字色、面板 tint）全靠它清掉。
     * @param alpha 背景层不透明度：只在**不是**卡片相对模式时乘进底色，文字始终实色。
     * @param cardRelative 面板改成「卡底 + 极淡覆盖层」（自定义背景 / 不透明度 < 100%）。
     */
    fun apply(
        context: Context,
        views: RemoteViews,
        layoutRes: Int,
        tone: WidgetTone,
        alpha: Float,
        cardRelative: Boolean = false
    ) {
        val sameAsSystem = tone == systemToneOf(context)
        val opacity = alpha.coerceIn(0f, 1f)
        val overlay = if (cardRelative) surfaceOverlay(tone) else 0
        entries(context, layoutRes).forEach { entry ->
            val color = context.getColor(entry.token)
            when {
                entry.kind == KIND_TEXT -> views.setTextColor(entry.id, color)
                // 覆盖层 alpha 已经定死：不再乘实例不透明度，也不能换回原 drawable（那是令牌色）
                cardRelative -> tintBackground(views, entry, overlay, opacity, sameAsSystem = false)
                else -> tintBackground(views, entry, color.atAlpha(opacity), opacity, sameAsSystem)
            }
        }
    }

    /**
     * 底色用 tint 而不是 `setBackgroundColor`：shape 的圆角/aspect 都留着，只换颜色。
     * `View.setBackgroundTintList` 是 @RemotableViewMethod，但 RemoteViews 给 ColorStateList 参数
     * 的路（[RemoteViews.setColorStateList]）是 API 31 起。
     *
     * 31 以下分两种：
     * - 色调**偏离**系统（深色自定义底 / 浅色系统）：只能 `setBackgroundColor`（方角，内容看得清 ——
     *   可读性优先于圆角）；
     * - 色调**就是**系统那套：shape 换回原 drawable（圆角留着，颜色也还是系统解析的那一档），
     *   纯色底直接写回颜色。
     *
     * 不透明度 <100% 时没法换 drawable（alpha 写不进资源），一律 `setBackgroundColor` 带 alpha 的色 ——
     * 31 以下内层面板在这个档位会丢圆角，但「整块一起透」比「边透里面不透」重要。
     */
    private fun tintBackground(
        views: RemoteViews,
        entry: Entry,
        color: Int,
        alpha: Float,
        sameAsSystem: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(entry.id, "setBackgroundTintList", ColorStateList.valueOf(color))
            return
        }
        val original = SURFACE_DRAWABLES[entry.token]
            ?.takeIf { alpha >= 1f && sameAsSystem && entry.kind == KIND_BG_SHAPE }
        if (original != null) {
            // 不作透 + 色调就是系统那套：换回原 drawable 才是布局自己的样子（圆角也才在）
            views.setInt(entry.id, "setBackgroundResource", original)
        } else {
            views.setInt(entry.id, "setBackgroundColor", color)
        }
    }

    /** 把实例不透明度乘进颜色自带的 alpha 里（令牌都是不透明的，等于直接换 alpha）。 */
    private fun Int.atAlpha(alpha: Float): Int {
        if (alpha >= 1f) return this
        val a = (Color.alpha(this) * alpha).roundToInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }

    /** 该布局里会被改写的 view id（测试用来与 XML 侧交叉核对）。 */
    internal fun sweptIds(context: Context, layoutRes: Int): Set<Int> =
        entries(context, layoutRes).mapTo(LinkedHashSet()) { it.id }

    private fun entries(context: Context, layoutRes: Int): List<Entry> = cache.getOrPut(layoutRes) {
        val day = WidgetToneContext.of(context, WidgetTone.LIGHT)
        val found = ArrayList<Entry>()
        collect(LayoutInflater.from(day).inflate(layoutRes, null, false), day, found)
        found
    }

    /**
     * 一个 view 可能**同时**要看两样：文字色与底色（带底的字，如日程分段胶囊）。
     * 只认一样就会漏 —— 底色没换的表现在低不透明度下就是「字跟着变浅、底还是那块实色板」。
     */
    private fun collect(view: View, day: Context, found: MutableList<Entry>) {
        val id = view.id
        if (id != View.NO_ID) {
            if (view is TextView) {
                TEXT_TOKENS.firstOrNull { it.colorIn(day) == view.currentTextColor }
                    ?.let { found += Entry(id, it, KIND_TEXT) }
            }
            surfaceEntry(view, id, day)?.let { found += it }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collect(view.getChildAt(i), day, found)
            }
        }
    }

    /**
     * 这个 view 的底色是不是**中性底**（跟着色调/不透明度换的那几种）。
     *
     * 预览图分层要用同一套判定（`WidgetPreviewPngGenerator` 把面板层单独出一张图），
     * 否则「预览里会淡出的东西」和真机上会淡出的东西就会走偏。
     * 判定跑在**渲染后**的视图树上，所以：令牌按 `context` 自己解析（夜间要取夜间那套值），
     * 31+ 还要认 tint（那时底色不在 drawable 上，见 [surfaceEntry]）。
     */
    internal fun isSurface(view: View, context: Context): Boolean =
        surfaceEntry(view, view.id, context) != null

    private fun surfaceEntry(view: View, id: Int, day: Context): Entry? {
        // 31+ 的底色是 tint（见 [tintBackground]）：drawable 自身的颜色还是布局里那套浅色，
        // 只有读 tint 才知道这个 view 现在是什么底。[isSurface] 判的是**渲染后**的视图树，
        // 必须先看 tint；建表时（刚 inflate、还没有 tint）这一步自然跳过。
        // 卡底自己的 tint（widget_v2_card_bg / 用户自定色）不在 BACKGROUND_TOKENS 里，
        // 不会被误判成内层面板。
        view.backgroundTintList?.defaultColor?.let { tinted ->
            R.color.widget_v2_ring.takeIf { it.colorIn(day) == tinted }
                ?.let { return Entry(id, it, KIND_BG_COLOR) }
            BACKGROUND_TOKENS.firstOrNull { it.colorIn(day) == tinted }
                ?.let { return Entry(id, it, KIND_BG_SHAPE) }
        }
        return when (val background = view.background) {
            is ColorDrawable -> R.color.widget_v2_ring.takeIf { background.color == it.colorIn(day) }
                ?.let { Entry(id, it, KIND_BG_COLOR) }
            is GradientDrawable -> background.color?.defaultColor?.let { solid ->
                BACKGROUND_TOKENS.firstOrNull { it.colorIn(day) == solid }
                    ?.let { Entry(id, it, KIND_BG_SHAPE) }
            }
            else -> null
        }
    }

    private fun Int.colorIn(context: Context): Int = context.getColor(this)
}
