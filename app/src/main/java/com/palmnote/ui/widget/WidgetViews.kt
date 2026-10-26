package com.palmnote.ui.widget

import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R
import kotlin.math.roundToInt

/**
 * 建 RemoteViews 的**唯一入口**：按该实例的色调（[WidgetToneContext]）把布局里写死的内容色
 * 重下一遍（[WidgetToneSweep]）。
 *
 * 放在**创建处**而不是发布出口，是为了让组件随后设的语义色（逾期红、色族强调色、今日高亮）
 * 压在上面 —— 顺序天然正确，不必再给十几个组件逐个传色调参数。
 *
 * **没接管背景时也扫**（色调取系统那一档）：写回去的颜色与布局自己解析的完全相同，外观零变化，
 * 但能复位上一轮留在视图树上的旧色 —— launcher 是复用视图树的，`RemoteViews` 又没有
 * 「恢复布局默认值」这种动作。少了这一步，「深色自定义底 → 切回默认」会留下一片浅字配白卡。
 *
 * 不透明度（[WidgetToneContext.alpha]）只在**默认卡底**那条路上乘进底色；一旦接管了背景
 * （或拖过不透明度滑杆），内层面板改为「卡底 + 极淡覆盖层」[cardRelative] ——
 * 半透明卡底上再叠一层半透明面板，会越叠越深，卡边与面板就对不上（2026-10-20 用户反馈）。
 */
internal fun widgetViews(context: Context, layoutRes: Int): RemoteViews {
    val views = RemoteViews(context.packageName, layoutRes)
    val wrapper = context as? WidgetToneContext
    val tone = wrapper?.tone ?: WidgetTone.ofSystem(context)
    // 色调就是系统那套时直接用原 Context：不必为了「原样」再包一层 Resources
    val themed = if (tone == systemToneOf(context)) context else WidgetToneContext.of(context, tone)
    // 包装过 = 这个实例有自定义背景 / 不透明度 < 100%：面板走卡片相对方案
    WidgetToneSweep.apply(themed, views, layoutRes, tone, wrapper?.alpha ?: 1f, cardRelative = wrapper != null)
    return views
}

/**
 * 内层「大色块」跟着实例不透明度一起变透。
 *
 * 概览（四块瓷砖）与快捷入口（四格按钮）的卡面几乎被实色砖盖满：只把卡底变透的话，透出来的
 * 只有砖与砖之间的缝，用户看到的就是「不透明度只调了边框」（2026-10-19 实测反馈）。所以这些砖
 * 也要跟着透。
 *
 * 砖本身是**叶子 ImageView**（`android:src` 是一张纯色 shape，颜色由组件 `setColorFilter` 上），
 * 所以 `setImageAlpha` 只影响砖、不影响砖上的文字/图标 —— 与「背景变透、内容实色」同一条原则。
 * `ImageView.setImageAlpha` 在所有版本都有 RemoteViews 入口，不需要版本分支；id 不在该布局里的
 * 组件会被框架静默跳过（[RemoteViews] 找不到 view 直接 return）。
 *
 * **不透明度 100% 也要写**（= 255）：同一实例从 20% 调回 100% 时，view 上还留着上一轮的 alpha，
 * 早退会让砖永远是半透的。
 */
internal object WidgetOpacityFade {

    /**
     * 会盖住整张卡面的内层色砖（`src` 纯色图）。预览图分层也要用：这些砖属于「背景层」，
     * 在预览里得跟着不透明度淡出，`WidgetPreviewPngGenerator` 把它们单独出成 `_blocks` 层 ——
     * 它们的颜色是色族给的话，不能跟中性面板一起被 tint 成极淡白/黑（见 `WidgetToneSweep` 的
     * 卡片相对方案）。
     *
     * v22 语义（2026-10-23）：各族胶囊底（共享件 [R.layout.widget_v22_chip]，底是色族 tint 的
     * 圆角砖、上面压白/墨内容）+ 快捷入口八格的圆底（[R.layout.widget_sc_v22_tile]）。
     * 旧的概览四砖 / 快捷 2×2 四格 id 已随旧布局删除。
     */
    internal val blockIds = intArrayOf(
        R.id.widget_v22_chip_bg,
        R.id.widget_sc_tile_bg
    )

    fun apply(views: RemoteViews, opacity: Float) {
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        blockIds.forEach { views.setInt(it, "setImageAlpha", alpha) }
    }
}
