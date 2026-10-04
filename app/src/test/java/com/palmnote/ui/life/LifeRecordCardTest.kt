package com.palmnote.ui.life

import android.app.Application
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 记录卡片（`LifeRecordCard`）里**纯函数**部分的契约。
 *
 * 这一层是「`showInCard` 死配置」缺陷的修复面：卡面取值同时决定
 * ① 首页列表显示什么 ② 模板预览显示什么，两处必须永远是同一份结果。
 * 所以这里钉住的是**取值规则本身**（谁进卡面、值怎么格式化、算不出怎么办），
 * 而不是像素。组合式渲染不在单测范围（无 UI 测试基建）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeRecordCardTest {

    private val today = LocalDate.of(2026, 10, 4)

    /** 测试专用的 `FieldConfig` 构造器：参数多但全是默认值，读起来比 10 次具名构造清楚。 */
    @Suppress("LongParameterList")
    private fun cfg(
        key: String,
        type: FieldType,
        label: String = key,
        showInCard: Boolean = true,
        disabled: Boolean = false,
        unit: String = "",
        options: List<String> = emptyList(),
        defaultValue: String = "",
        max: Double? = null,
        showAsProgress: Boolean = false,
        progressTargetKey: String = ""
    ) = FieldConfig(
        key = key, label = label, type = type, showInCard = showInCard, disabled = disabled,
        unit = unit, options = options, defaultValue = defaultValue, max = max,
        showAsProgress = showAsProgress, progressTargetKey = progressTargetKey
    )

    private fun obj(vararg pairs: Pair<String, String>) =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    // ── 取值规则 ──────────────────────────────────────────────────────────────

    @Test
    fun `只取已启用且勾了卡片字段的字段`() {
        val configs = listOf(
            cfg("a", FieldType.TEXT, showInCard = true),
            cfg("b", FieldType.TEXT, showInCard = false),
            cfg("c", FieldType.TEXT, showInCard = true, disabled = true)
        )
        val out = cardFieldValues(configs, obj("a" to "甲", "b" to "乙", "c" to "丙"), today)
        assertEquals(listOf("甲"), out.map { it.value })
    }

    @Test
    fun `金额带人民币符号，数值带单位，空值不出现在卡面`() {
        val configs = listOf(
            cfg("amount", FieldType.CURRENCY),
            cfg("pages", FieldType.NUMBER, unit = "页"),
            cfg("note", FieldType.TEXT)
        )
        val out = cardFieldValues(configs, obj("amount" to "3500", "pages" to "120", "note" to ""), today)
        assertEquals(listOf(fmtMoney(3500.0), "120 页"), out.map { it.value })
    }

    @Test
    fun `日期用同年短格式，解析不出就不出`() {
        val configs = listOf(cfg("d", FieldType.DATE), cfg("bad", FieldType.DATE))
        val out = cardFieldValues(configs, obj("d" to "2026-10-04", "bad" to "不是日期"), today)
        assertEquals(listOf("10-04"), out.map { it.value })
    }

    @Test
    fun `布尔只在为真时以勾号出现`() {
        val configs = listOf(cfg("yes", FieldType.BOOLEAN), cfg("no", FieldType.BOOLEAN))
        val out = cardFieldValues(configs, obj("yes" to "true", "no" to "false"), today)
        assertEquals(listOf("✓"), out.map { it.value })
    }

    @Test
    fun `长文本截断，不把卡片撑歪`() {
        val configs = listOf(cfg("note", FieldType.TEXT))
        val raw = "一".repeat(40)
        val out = cardFieldValues(configs, obj("note" to raw), today)
        assertEquals(1, out.size)
        assertEquals(19, out.first().value.length) // 18 字 + 省略号
        assertTrue(out.first().value.endsWith("…"))
    }

    @Test
    fun `一行卡面最多三项，超出折成加号计数`() {
        val fields = (1..5).map { CardFieldValue("f$it", "$it") }
        val (shown, more) = visibleCardFields(fields)
        assertEquals(listOf("f1", "f2", "f3"), shown.map { it.label })
        assertEquals(2, more)
        assertEquals(0, visibleCardFields(fields.take(2)).second)
    }

    @Test
    fun `天数类字段带上方向（供 UI 措辞成 还有 已过 就是今天）`() {
        val configs = listOf(
            cfg("start", FieldType.DATE, showInCard = false),
            cfg("elapsed", FieldType.ELAPSED, label = "已经过", options = listOf("start"))
        )
        // 起始日在 3 天前 → 天数为 -3（负数 = 已过），UI 侧措辞成「已过 3 天」
        val past = cardFieldValues(configs, obj("start" to atStartOfDay(today.minusDays(3)).toString()), today)
        assertEquals(-3L, past.first { it.label == "已经过" }.days)
        // 非天数类字段不带方向
        val plain = cardFieldValues(listOf(cfg("n", FieldType.NUMBER)), obj("n" to "3"), today)
        assertNull(plain.first().days)
    }

    // ── 派生字段：现算，并按被引用字段的语义格式化 ─────────────────────────────

    @Test
    fun `还差字段现算，并继承目标字段的币种`() {
        val configs = listOf(
            cfg("targetAmount", FieldType.CURRENCY, label = "目标金额"),
            cfg("currentAmount", FieldType.CURRENCY, label = "已存金额"),
            cfg("remain", FieldType.REMAINING, label = "还差", options = listOf("targetAmount", "currentAmount"))
        )
        val out = cardFieldValues(
            configs,
            obj("targetAmount" to "10000", "currentAmount" to "3500"),
            today
        )
        assertEquals("还差", out.last().label)
        assertEquals(fmtMoney(6500.0), out.last().value)
    }

    @Test
    fun `已经过字段以天为单位，缺引用就不出`() {
        val configs = listOf(
            cfg("start", FieldType.DATE),
            cfg("elapsed", FieldType.ELAPSED, options = listOf("start")),
            cfg("orphan", FieldType.ELAPSED, options = emptyList())
        )
        val out = cardFieldValues(configs, obj("start" to "2026-10-01"), today)
        // 正负号语义归 `DerivedEvaluator`（另有其测试）；卡片层的契约只是「有引用就出、以天计」。
        val elapsed = out.firstOrNull { it.label == "elapsed" }?.value
        assertTrue("应算出天数，实际=$elapsed", elapsed != null && elapsed.endsWith(" 天"))
        assertTrue(out.none { it.label == "orphan" })
    }

    /**
     * 回归：ELAPSED 的引用日期在**表单写入的 ISO 形态**下也必须算得出。
     *
     * `DerivedEvaluator.dateMs` 曾经只 `toLongOrNull()`（仅认毫秒），而记录表单写的是
     * `LocalDate.toString()`（ISO），于是「剩余天数 / 已经过」这类派生字段对**用户手填的日期**
     * 永远算不出、在卡面与详情页静默消失（demo 数据看不出来，因为演示数据写的是毫秒）。
     */
    @Test
    fun `ELAPSED 的引用日期是表单写入的 ISO 字符串也能算`() {
        val configs = listOf(
            cfg("start", FieldType.DATE),
            cfg("elapsed", FieldType.ELAPSED, options = listOf("start"))
        )
        val out = cardFieldValues(configs, obj("start" to "2026-10-01"), today)
        assertTrue("ISO 日期应能算出天数，实际=$out", out.any { it.label == "elapsed" })
    }

    @Test
    fun `ELAPSED 的引用日期是毫秒也能算（演示数据形态）`() {
        val startMs = java.time.LocalDate.of(2026, 10, 1)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val configs = listOf(
            cfg("start", FieldType.DATE),
            cfg("elapsed", FieldType.ELAPSED, options = listOf("start"))
        )
        val out = cardFieldValues(configs, obj("start" to startMs.toString()), today)
        assertTrue("毫秒日期应能算出天数，实际=$out", out.any { it.label == "elapsed" })
    }

    @Test
    fun `一行表达不了的字段（表格 清单 媒体）不进卡面`() {
        val configs = listOf(
            cfg("t", FieldType.TABLE, options = listOf("name:名目:TEXT")),
            cfg("c", FieldType.CHECKLIST),
            cfg("img", FieldType.IMAGE)
        )
        val out = cardFieldValues(configs, obj("t" to "x", "c" to "y", "img" to "z"), today)
        assertTrue(out.isEmpty())
    }

    // ── 进度分母 ──────────────────────────────────────────────────────────────

    @Test
    fun `进度分母取目标字段的值`() {
        val configs = listOf(
            cfg("targetAmount", FieldType.CURRENCY),
            cfg("currentAmount", FieldType.CURRENCY, showAsProgress = true, progressTargetKey = "targetAmount")
        )
        val f = cardProgressFraction(configs, obj("targetAmount" to "10000", "currentAmount" to "3500"))
        assertEquals(0.35f, f!!, 0.0001f)
    }

    @Test
    fun `缺目标字段时回落 max，两端都缺就不出条`() {
        val withMax = listOf(cfg("cur", FieldType.NUMBER, showAsProgress = true, max = 7000.0))
        assertEquals(0.5f, cardProgressFraction(withMax, obj("cur" to "3500"))!!, 0.0001f)
        val noTarget = listOf(cfg("cur", FieldType.NUMBER, showAsProgress = true))
        assertNull(cardProgressFraction(noTarget, obj("cur" to "3500")))
    }

    @Test
    fun `没有进度字段就没有进度条，目标为 0 也不出`() {
        assertNull(cardProgressFraction(listOf(cfg("n", FieldType.NUMBER)), obj("n" to "1")))
        val zero = listOf(
            cfg("target", FieldType.NUMBER),
            cfg("cur", FieldType.NUMBER, showAsProgress = true, progressTargetKey = "target")
        )
        assertNull(cardProgressFraction(zero, obj("target" to "0", "cur" to "1")))
    }

    // ── 存储态解析：脏数据不崩 ────────────────────────────────────────────────

    @Test
    fun `字段配置是脏 JSON 时返回空表，不抛异常`() {
        assertTrue(cardFieldValues("{不是数组", """{"a":"1"}""", today).isEmpty())
        assertNull(cardProgressFraction("{不是数组", """{"a":"1"}"""))
    }

    @Test
    fun `字段值是脏 JSON 时返回空表，不抛异常`() {
        val configJson = """[{"key":"a","label":"甲","type":"TEXT","showInCard":true}]"""
        assertTrue(cardFieldValues(configJson, "不是 JSON", today).isEmpty())
        assertNull(cardProgressFraction(configJson, "不是 JSON"))
    }

    @Test
    fun `未知键不影响解析（老数据带新键也能读）`() {
        val configJson =
            """[{"key":"a","label":"甲","type":"TEXT","showInCard":true,"未来新增的键":123}]"""
        val out = cardFieldValues(configJson, """{"a":"甲"}""", today)
        assertEquals(listOf("甲"), out.map { it.value })
    }

    // ── 「上次记录：N 天前」（模板维度读数） ──────────────────────────────────

    private fun atStartOfDay(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `从未记录返回 null，今天记过返回 0`() {
        assertNull(relativeDaysSince(null, today))
        assertEquals(0L, relativeDaysSince(atStartOfDay(today), today))
    }

    @Test
    fun `往前数天返回天数，未来时间夹到 0`() {
        assertEquals(3L, relativeDaysSince(atStartOfDay(today.minusDays(3)), today))
        assertEquals(0L, relativeDaysSince(atStartOfDay(today.plusDays(1)), today))
    }

    // ── 共用预览：用「配置 + 值」装配出真实详情结构 ────────────────────────────

    /**
     * 模板编辑器用「默认值」预览、填写页用「用户刚填的值」预览，两边调的是同一个
     * `previewDetailUiOf` —— 这里钉住它的产物结构（每组一个字段），
     * 也就钉住了"预览与详情页同源"这件事。
     */
    // ── 共用预览：用「配置 + 值」装配出真实详情结构 ────────────────────────────

    /**
     * 模板编辑器用「默认值」预览、填写页用「用户刚填的值」预览，两边调的是同一个
     * `previewDetailUiOf` —— 这里钉住它的产物结构（每组一个字段），
     * 也就钉住了"预览与详情页同源"这件事。
     */
    @Test
    fun `预览详情按字段装配出分组`() {
        val input = RecordPreviewInput(
            name = "喝奶茶",
            iconKey = "note_add",
            colorHex = "#FF8844",
            fields = listOf(
                cfg("brand", FieldType.SELECT, label = "品牌", options = listOf("喜茶", "奈雪")),
                cfg("cups", FieldType.NUMBER, label = "杯数", unit = "杯")
            ),
            values = mapOf("brand" to "喜茶", "cups" to "2")
        )
        val ui = previewDetailUiOf(input, "喝奶茶")
        // 结构区按「手」分组（§14.2）：组标题是**手名**（走 titleRes），
        // 所以断言**行的标签** —— 它才是"哪些字段进了结构区"的直接证据。
        assertEquals(
            listOf("品牌", "杯数"),
            ui.groups.flatMap { it.rows }.mapNotNull { row ->
                when (row) {
                    is DetailRowModel.Chips -> row.label
                    is DetailRowModel.Kv -> row.label
                    else -> null
                }
            }
        )
    }

    // ── 数字 / 金额格式化：显示层唯一口径 ─────────────────────────────────────

    /**
     * 这三条钉住的是「显示口径只有一处」：
     * 此前 hero 用 `#,##0.####`、详情页用 `%,.1f`、金额自己拼 `"¥" + 数字`
     * —— 同一个数在两处精度不同，而且负数会写成 `¥-100`（规则明写该写 `-¥100`）。
     */
    @Test
    fun `数字分组与小数位统一最多两位`() {
        assertEquals("12,345", fmtNumber(12345.0))
        assertEquals("12,345.5", fmtNumber(12345.5))
        assertEquals("3,500.13", fmtNumber(3500.125))
        assertEquals("0.5", fmtNumber(0.5))
    }

    @Test
    fun `金额的负号在币种之前`() {
        assertEquals("¥1,234", fmtMoney(1234.0))
        assertEquals("-¥1,234", fmtMoney(-1234.0))
    }

    @Test
    fun `列表与卡面的天数类字段仍按方向措辞`() {
        // 与上一组共用同一实现：正=还有、0=今天、负=已过
        val configs = listOf(
            cfg("d", FieldType.DATE, showInCard = false),
            cfg("days", FieldType.ELAPSED, label = "还有", options = listOf("d"))
        )
        val out = cardFieldValues(configs, obj("d" to atStartOfDay(today.plusDays(7)).toString()), today)
        assertEquals(7L, out.first { it.label == "还有" }.days)
    }
}
