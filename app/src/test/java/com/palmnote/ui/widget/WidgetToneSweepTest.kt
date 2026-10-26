package com.palmnote.ui.widget

import android.app.Application
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.palmnote.app.R
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 布局扫色（[WidgetToneSweep]）：接管背景后，布局里**写死的浅色令牌**要按实例色调重下一遍。
 *
 * 覆盖三件事：
 * 1. 正文/面板底都换到夜间令牌（深色卡上不出现「浅色板 + 浅字」）；
 * 2. 组件随后设的语义色（逾期红）压在上面 —— 扫色发生在建 RemoteViews 时，顺序天然正确；
 * 3. 色调等于系统时**外观不变**（写回去的就是布局自己的颜色），但依然会写 —— 只有这样才能
 *    复位上一轮自定义背景留在 launcher 视图树上的旧色；
 * 4. 与 XML 侧交叉核对：每个在 XML 里写了文字令牌的 id，都必须在扫色表里（防布局改了表没跟上）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetToneSweepTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun render(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun night(): Context = WidgetToneContext.of(context, WidgetTone.DARK)

    @Test
    fun `深色卡把面板覆盖层与正文一起换成夜间那套`() {
        val dark = night()
        val root = render(widgetViews(dark, R.layout.widget_bill_item))

        assertEquals(
            dark.getColor(R.color.widget_v2_text_primary),
            root.findViewById<TextView>(R.id.widget_item_category).currentTextColor
        )
        assertEquals(
            dark.getColor(R.color.widget_v2_text_primary),
            root.findViewById<TextView>(R.id.widget_item_amount).currentTextColor
        )
        // 卡片相对：面板不再是令牌色的实底/半透底，而是卡底上一档极淡白（31 以下 setBackgroundColor）
        assertEquals(
            WidgetToneSweep.surfaceOverlay(WidgetTone.DARK),
            (root.background as ColorDrawable).color
        )
    }

    @Test
    fun `天数胶囊与单位文字也一起换`() {
        val dark = night()
        val root = render(widgetViews(dark, R.layout.widget_counter_item))

        val pill = root.findViewById<View>(R.id.widget_item_days_pill)
        assertEquals(WidgetToneSweep.surfaceOverlay(WidgetTone.DARK), (pill.background as ColorDrawable).color)
        assertEquals(
            dark.getColor(R.color.widget_v2_text_secondary),
            root.findViewById<TextView>(R.id.widget_item_unit).currentTextColor
        )
        // 强调色（天数）**不**参与扫色：语义色由组件自己按色调取，扫色只管中性令牌
        assertEquals(
            context.getColor(R.color.widget_v2_accent),
            root.findViewById<TextView>(R.id.widget_item_days).currentTextColor
        )
    }

    @Test
    fun `面板覆盖层不随实例不透明度加深，文字仍实色`() {
        // 卡片相对方案：面板 = 卡底 + 固定 6% 覆盖层。若跟实例 alpha 一起乘，半透明卡底上再叠
        // 一层半透明面板就会越叠越深 —— 正是「卡底与内层面板深浅不一致」的来源（2026-10-20）。
        val faded = WidgetToneContext.of(night(), WidgetTone.DARK, 0.4f)
        val root = render(widgetViews(faded, R.layout.widget_bill_item))

        val panel = (root.background as ColorDrawable).color
        assertEquals(
            "覆盖层 alpha 固定，不乘实例不透明度",
            (WidgetToneSweep.SURFACE_OVERLAY_ALPHA * 255f).roundToInt(),
            android.graphics.Color.alpha(panel)
        )
        assertEquals("深色卡叠极淡白", 0xFFFFFF, panel and 0x00FFFFFF)
        assertEquals(
            "文字不跟着透",
            255,
            android.graphics.Color.alpha(
                root.findViewById<TextView>(R.id.widget_item_category).currentTextColor
            )
        )
    }

    @Test
    fun `轨道走卡底相对：深色卡极淡白，浅色卡日间浅灰轨`() {
        // 深色卡（默认夜卡 / 自选深色彩卡）：白 + 6% —— colorFilter 走 SRC_ATOP 只换色不换
        // 透明度，半透白会叠在 drawable 自己的深色上（实测夜轨 (55,61,58)），6% 必须靠 imageAlpha
        val dark = WidgetToneSweep.trackStyleOf(night())
        assertEquals(0xFFFFFF, dark.color and 0x00FFFFFF)
        assertEquals(
            (WidgetToneSweep.SURFACE_OVERLAY_ALPHA * 255f).roundToInt(),
            dark.alpha
        )
        // 浅色卡（默认白卡 / 自选白底）：日间那档浅灰轨 + 不透明 —— 与默认卡底观感一致，
        // 同时防「系统深色 + 白底」时 drawable 自己解析成夜轨
        val light = WidgetToneSweep.trackStyleOf(WidgetToneContext.of(context, WidgetTone.LIGHT))
        assertEquals(context.colorInTone(WidgetTone.LIGHT, R.color.widget_v2_ring), light.color)
        assertEquals(255, light.alpha)
    }

    @Test
    fun `浅色卡的面板覆盖层是极淡黑`() {
        val faded = WidgetToneContext.of(context, WidgetTone.LIGHT, 0.5f)
        val root = render(widgetViews(faded, R.layout.widget_bill_item))

        val panel = (root.background as ColorDrawable).color
        assertEquals(0, panel and 0x00FFFFFF)
        assertEquals(
            (WidgetToneSweep.SURFACE_OVERLAY_ALPHA * 255f).roundToInt(),
            android.graphics.Color.alpha(panel)
        )
    }

    @Test
    fun `色调等于系统时不改变外观`() {
        val root = render(widgetViews(context, R.layout.widget_bill_item))

        assertEquals(
            context.getColor(R.color.widget_v2_text_primary),
            root.findViewById<TextView>(R.id.widget_item_category).currentTextColor
        )
        // 还是布局里原来的圆角 shape，没有被 setBackgroundColor 换成直角
        assertEquals(
            R.drawable.widget_item_bg,
            org.robolectric.Shadows.shadowOf(root.background).createdFromResId
        )
    }

    @Test
    fun `从深色背景切回默认会复位上一轮的旧色`() {
        // 第一轮：这个实例接管了深色背景
        val root = render(widgetViews(night(), R.layout.widget_bill_item))
        assertEquals(
            "扫色应已把浅色卡上的深色字换成夜间那套",
            night().getColor(R.color.widget_v2_text_primary),
            root.findViewById<TextView>(R.id.widget_item_category).currentTextColor
        )

        // 第二轮：同一个实例切回默认卡底。桌面复用视图树，不会重新 inflate
        widgetViews(context, R.layout.widget_bill_item).reapply(context, root)

        assertEquals(
            "切回默认后必须把字复位成系统那套：夜间色留在浅色卡上就是一片读不了的字",
            context.getColor(R.color.widget_v2_text_primary),
            root.findViewById<TextView>(R.id.widget_item_category).currentTextColor
        )
        assertEquals(
            "面板底也要复位（31 以下换回原 drawable，圆角跟着回来）",
            R.drawable.widget_item_bg,
            org.robolectric.Shadows.shadowOf(root.background).createdFromResId
        )
    }

    @Test
    fun `组件随后设的语义色压过扫色`() {
        val dark = night()
        val overdue = com.palmnote.data.db.entity.LifeItem(
            templateId = 1,
            title = "交房租",
            status = "ACTIVE",
            dueDate = System.currentTimeMillis() - 86_400_000L
        )

        val root = render(WidgetHelper.todoAgendaRowViews(dark, overdue, timeText = "昨天", isOverdue = true))

        assertEquals(
            "逾期红是组件后设的，必须压过扫色结果",
            dark.getColor(R.color.widget_risk_red),
            root.findViewById<TextView>(R.id.widget_todo_agenda_time).currentTextColor
        )
        assertEquals(
            "名称没有语义色，走扫色 → 夜间主文字",
            dark.getColor(R.color.widget_v2_text_primary),
            root.findViewById<TextView>(R.id.widget_todo_agenda_name).currentTextColor
        )
    }

    /**
     * XML 侧的独立交叉核对（比扫色表自己更硬）。布局里**写死**的令牌都要满足两件事：
     *
     * 1. 那个 view 必须有 `android:id` —— RemoteViews 只能按 id 改写，没 id 的 view 永远跟着
     *    启动器的明暗走（深色自定义底上标题还是黑的，正是这条要防的）；
     * 2. 它必须出现在扫色表里 —— 防「布局换了写法、表没跟上」（比如哪天改成 textAppearance / ?attr）。
     *
     * 文字与底色一起数：底色漏了更隐蔽 —— 预览分层是按视图树判的（不依赖 id），
     * 于是预览里淡出了、真机上还是块实色板，正好对不上。
     */
    @Test
    fun `XML 里写死令牌的 view 都有 id 且在扫色表里`() {
        val dir = File("src/main/res/layout")
        assumeTrue("布局目录不在工作目录下（$dir），跳过 XML 交叉核对", dir.isDirectory)

        // `*_preview` 是只喂给系统选择器 `android:previewLayout` 的**静态**布局（真机不 bind）。
        // App 内 pin 确认页已改用 `_card` 卡面图，但系统选择器的 previewLayout 仍需要它们；
        // 这里守着「名字叫 _preview 的布局必须被某个 info 的 previewLayout 真的引用」。
        val previewLayouts = previewLayoutsReferencedByInfo()

        val tag = Regex("<[a-zA-Z][^>]*>")
        val idAttr = Regex("""android:id="@\+id/([\w.]+)"""")
        // v2 的 primary/secondary/tertiary 与 v22 的 1/2/3 都是中性文字令牌，都要有 id + 进扫色表
        val textToken = Regex("""android:textColor="@color/((?:widget_v2_text_(?:primary|secondary|tertiary)|widget_v22_text_[123]))"""")
        // 令牌名分两段拼（一行太长过不了 MaxLineLength）：新的 widget_v2_* 与老的 widget_* 画布都算
        val bgTokens = listOf(
            "widget_v2_(?:item_bg|chip_bg|soft_mint|soft_gray|days_bg|ring)",
            "widget_(?:item_bg|chip_bg|days_bg|soft_card|soft_card_gray)"
        ).joinToString("|")
        val bgToken = Regex("""android:background="@(?:color|drawable)/($bgTokens)"""")
        val failures = mutableListOf<String>()
        var checked = 0

        dir.listFiles { file -> file.name.startsWith("widget_") && file.name.endsWith(".xml") }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                val layoutRes = context.resources.getIdentifier(
                    file.nameWithoutExtension, "layout", context.packageName
                )
                if (layoutRes == 0) {
                    failures += "${file.name}: 找不到 layout 资源"
                    return@forEach
                }
                if (file.name.endsWith("_preview.xml")) {
                    if (layoutRes !in previewLayouts) {
                        failures += "${file.name}: 名字像预览布局，但没有任何 info 的 previewLayout 引用它"
                    }
                    return@forEach
                }
                val swept = WidgetToneSweep.sweptIds(context, layoutRes)
                var found = 0
                tag.findAll(file.readText()).forEach { match ->
                    val token = textToken.find(match.value)?.groupValues?.get(1)
                        ?: bgToken.find(match.value)?.groupValues?.get(1)
                        ?: return@forEach
                    found++
                    val idName = idAttr.find(match.value)?.groupValues?.get(1)
                    if (idName == null) {
                        failures += "${file.name}: $token 写死了但没有 android:id"
                        return@forEach
                    }
                    val id = context.resources.getIdentifier(idName, "id", context.packageName)
                    if (id == 0) {
                        failures += "${file.name}: $idName 找不到 id 资源"
                    } else if (id !in swept) {
                        failures += "${file.name}: $idName ($token) 没被扫到"
                    }
                }
                if (found > 0) checked++
            }

        assertTrue("写死令牌交叉核对失败：\n${failures.joinToString("\n")}", failures.isEmpty())
        assertTrue("一个带令牌的布局都没核对到，测试已失效", checked > 5)
    }

    /** 系统选择器 `previewLayout` 引用到的静态预览布局集合（`*_preview.xml` 必须落在里面）。 */
    private fun previewLayoutsReferencedByInfo(): Set<Int> = File("src/main/res/xml")
        .listFiles { f -> f.name.endsWith("_info.xml") }
        .orEmpty()
        .flatMap { info ->
            Regex("""android:previewLayout="@layout/([a-z0-9_]+)"""")
                .findAll(info.readText())
                .map { it.groupValues[1] }
                .toList()
        }
        .map { context.resources.getIdentifier(it, "layout", context.packageName) }
        .toSet()

    /**
     * 进度槽一律用 `android:src` 画（内容层，**不跟不透明度变透**）。
     *
     * 用 `android:background` 画就会被扫色表认成「面板底」：低不透明度下槽跟着淡出，
     * 而真机上各形态的槽都是 src、始终实色 —— 面板预览与真机就对不上了
     * （`widget_todo_preview` 原来就是这么画的，2026-10-20 改正）。
     * 填充（`widget_bar_fill`）同理由 Provider `setColorFilter` 上色，也不进底色层。
     */
    @Test
    fun `进度槽不用底色层画`() {
        val dir = File("src/main/res/layout")
        assumeTrue("布局目录不在工作目录下（$dir），跳过进度槽核对", dir.isDirectory)
        val offenders = dir.listFiles { file -> file.name.startsWith("widget_") && file.name.endsWith(".xml") }
            .orEmpty()
            .filter { it.readText().contains("""android:background="@drawable/widget_bar_track"""") }
            .map { it.name }
        assertEquals("这些布局把进度槽画成了底色层（会跟着不透明度淡出）：$offenders", emptyList<String>(), offenders)
    }

    /**
     * 每张**卡片**布局都要有 `widget_bg` 这一层：[WidgetWallpaper.apply] 把自定义背景（图片，
     * 或 31 以下的纯色位图）画在 `widget_bg` 上、同时把卡底清成透明 —— 布局里没有这层，
     * 清完就什么也不剩，整张卡在桌面上「消失」。4×1 快捷入口就是这么漏的（2026-10-20）。
     */
    @Test
    fun `卡片布局都带 widget_bg 背景层`() {
        val dir = File("src/main/res/layout")
        assumeTrue("布局目录不在工作目录下（$dir），跳过卡片结构核对", dir.isDirectory)
        val missing = dir.listFiles { file -> file.name.startsWith("widget_") && file.name.endsWith(".xml") }
            .orEmpty()
            .filter { it.readText().contains("@+id/widget_layout") }
            .filterNot { it.readText().contains("@+id/widget_bg") }
            .map { it.name }
        assertEquals("这些卡片布局缺 widget_bg：$missing", emptyList<String>(), missing)
    }
}
