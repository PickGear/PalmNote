package com.palmnote.ui.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import com.palmnote.app.R
import kotlin.math.pow

/**
 * 卡片**色调**：由实例背景的明暗决定，给卡上内容配 on-color（M3 的 container / on-container 规则）。
 *
 * 为什么需要它：布局里写死的是浅色令牌（`widget_v2_text_primary` = #15181A），桌面按**桌面的 uiMode**
 * 解析 `values-night`。用户给这个实例挑了黑底、系统还在浅色模式时，卡是黑的、字还是黑的（2026-10-19 实测）。
 * RemoteViews 没有「换主题」的口子，只能在 bind 时按 view id 把颜色重新下发一遍。
 *
 * **接管了实例背景**（自定义纯色 / 图片）时，卡上内容的明暗由用户挑的色决定，与系统明暗无关，
 * 必须自己配色调；**没接管**时色调就是系统那一档，下发下去与布局自己解析出来的颜色完全相同，
 * 默认外观零变化 —— 但依然要下发，因为 launcher 的视图树是**复用**的：上一轮的自定义背景
 * 留在 view 上的旧文字色/旧 tint 不会自己消失，而 RemoteViews 没有「恢复布局默认值」这种动作
 * （§[widgetViews]）。
 *
 * 落地方式是 [WidgetToneContext]：把色调塞进 Context 的 `uiMode`，让**同一个 bind 里的每一处取色**
 * （`context.getColor`、`WidgetData.colorFamily`、以及 [widgetViews] 建 RemoteViews 时的布局色）
 * 都自动走对应明暗的令牌，不必在十几个组件里逐个传参。
 */
enum class WidgetTone {
    /** 卡片底色偏亮 → 内容用**深色**（values/ 那套令牌）。 */
    LIGHT,

    /** 卡片底色偏暗 → 内容用**浅色**（values-night/ 那套令牌）。 */
    DARK;

    companion object {
        /** 系统（= 桌面）当前明暗：桌面按自己的 uiMode 解析布局里的 @color，这里与它对齐。 */
        fun ofSystem(context: Context): WidgetTone {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (mode == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
        }
    }
}

/**
 * 一个实例的外观：不透明度 + 色调。
 *
 * @param opacity **不透明度** 0.2–1.0，只作用在**背景层**（内容保持实色，字号/图标不跟着发灰）。
 * @param tone 内容色调；null = 未接管背景（布局里写死的颜色跟随系统，不去动它）。
 *   接管了背景时按用户定的规则算：白底用深色字、其他任何背景色都用浅色字（[WidgetTonePicker]）。
 */
data class WidgetStyle(
    val opacity: Float,
    val tone: WidgetTone?
) {
    companion object {
        val DEFAULT = WidgetStyle(1f, null)
    }
}

/**
 * 带色调的 Context：`getResources()` 换成强制了日夜的同一份配置（其余限定符照抄原 Context），
 * 于是这个 Context 里读到的一切 `@color` 都是对应明暗的那一套。
 *
 * `WidgetTone.LIGHT`/`DARK` 与 `values`/`values-night` 一一对应，是同一个调色板的两个方向 ——
 * 深色底用夜间那套内容色，正是设计稿里夜间模式的样子（design-v4 的 .dark 段）。
 *
 * 顺带把实例的**不透明度**也捎上（[alpha]）：默认卡底时它乘进 [WidgetToneSweep] 写的底色；
 * 一旦接管了背景，面板改走「卡底 + 极淡覆盖层」，不透明度只作用在卡底（与背景图同一条路），
 * 文字/图标始终实色。
 */
internal class WidgetToneContext private constructor(
    base: Context,
    val tone: WidgetTone,
    /**
     * 背景层不透明度（1 = 不透）。只用来把实例的不透明度送到 [widgetViews] ——
     * 它影响的是 [WidgetToneSweep] 写下去的**底色**，文字/语义色一律实色。
     */
    val alpha: Float
) : ContextWrapper(base) {

    private val themed: Resources = run {
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (tone == WidgetTone.DARK) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
        createConfigurationContext(config).resources
    }

    override fun getResources(): Resources = themed

    companion object {
        /**
         * @param tone null（未接管背景）= 用系统那一档；色调只决定取哪套令牌，不改内容语义。
         * @param alpha 背景层不透明度；tone 为 null 且 alpha 为 1 时原样返回，不做任何包装。
         */
        fun of(context: Context, tone: WidgetTone?, alpha: Float = 1f): Context {
            val a = alpha.coerceIn(0f, 1f)
            if (tone == null && a >= 1f) return context
            val forced = tone ?: systemToneOf(context)
            if (context is WidgetToneContext && context.tone == forced && context.alpha == a) return context
            return WidgetToneContext(context, forced, a)
        }
    }
}

/**
 * 系统（= 桌面）的明暗，不受 [WidgetToneContext] 强制的那一档影响：顺着包装链取到根 Context 再问。
 *
 * 用来判断「这个色调是不是就是桌面会自己解析出来的那套」—— 是的话，写入颜色只是复位（外观不变），
 * 不是的话才是真正的换色。API 31 以下换底手段不同，就靠这个区分（见 [WidgetToneSweep]）。
 */
internal tailrec fun systemToneOf(context: Context): WidgetTone =
    if (context is WidgetToneContext) systemToneOf(context.baseContext) else WidgetTone.ofSystem(context)

/**
 * 在**指定色调**下取一个颜色令牌（不管当前 Context 被强制成了哪档）。
 *
 * 给「按卡底明暗配 on 色」的场景用：比如自选深色背景时「新增」胶囊翻成浅色底，
 * 字要配日间那档的深色（[WidgetTone.LIGHT]），而当前 Context 是夜间强制档。
 */
internal fun Context.colorInTone(tone: WidgetTone, resId: Int): Int =
    WidgetToneContext.of(this, tone).getColor(resId)

/**
 * 按背景色算内容色调 —— **一刀切**规则（用户 2026-10-20 定）：
 * **白色底用深色字，其他任何背景色都用浅色字**。
 *
 * 不做「谁对比度高用谁」的自适应：饱和色（青 `#0891B2`、橙 `#FF8A3D`）按对比度算常常黑字略高，
 * 但设计上要的是白字；自适应还让同一个色板里有的色翻成黑字、有的不翻，解释不了。用户明确要
 * 这条可预测的简单规则：只有白底例外。
 *
 * 「白色」按**背景色本身**判（不是与卡底的合成色），所以与实例不透明度无关：白底在 20% 时
 * 依旧深色字，黑底在 20% 时依旧浅色字。低不透明度时卡其实主要由壁纸决定，这是这种
 * 「任意背景 + 任意不透明度」功能的固有代价（Google 官方小组件干脆不给逐实例背景色，
 * 只给深浅两套）。
 */
internal object WidgetTonePicker {

    /**
     * 「白色背景」的判定线：相对亮度 ≥ 0.8。
     * 2026-10-25 预设色板收敛为「白 + 三浅 + 墨」后重定：浅灰 #F1F2F4 = 0.887、
     * 米白 #F7F3EA = 0.898、浅青 #E7F1F2 = 0.863 都要走浅底深字；墨 #1C1F26 = 0.014
     * 走深底浅字。0.8 落在浅青与墨之间的空档，图片背景按平均色判明暗同样适用。
     */
    internal const val WHITE_BACKGROUND_LUMINANCE = 0.8

    fun of(context: Context, backgroundArgb: Int?): WidgetTone {
        val systemTone = WidgetTone.ofSystem(context)
        val base = WidgetToneContext.of(context, systemTone).getColor(R.color.widget_v2_card_bg)
        val color = backgroundArgb ?: base
        return if (luminance(color) >= WHITE_BACKGROUND_LUMINANCE) WidgetTone.LIGHT else WidgetTone.DARK
    }

    private fun luminance(color: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
