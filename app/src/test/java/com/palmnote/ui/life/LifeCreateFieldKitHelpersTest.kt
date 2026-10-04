package com.palmnote.ui.life

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.RangeModel
import com.palmnote.domain.model.TableModel
import com.palmnote.domain.model.compoundRawOf
import com.palmnote.domain.model.encodeRange
import com.palmnote.domain.model.encodeTable
import com.palmnote.domain.model.parseRange
import com.palmnote.domain.model.parseTable
import com.palmnote.domain.util.DateUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * 本轮新增的三个字段控件（DATETIME / SLIDER / COLOR）里**纯函数**部分的契约。
 *
 * 这些函数是「表单存出去的值」与「别的模块读进来的值」之间的接缝，正是审计
 * §13.5 那类缺陷（同一事实两种写法、静默解析失败）最容易复发的地方，所以单独钉住。
 * 组合式渲染部分不在单测范围（无 UI 测试基建）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeCreateFieldKitHelpersTest {

    /**
     * **跨模块契约**：表单存出来的 DATETIME 文本，必须能被 `DateUtils.parseDateValueOrNull` 解析。
     * 它俩对不上时，DATETIME 记录的 `dueDate` 执行列就镜像不出来 —— 记录不上日历、不进今日、
     * 不算逾期，而且**全程不报错**（这正是审计 §13.5 记载的原始缺陷）。
     */
    @Test
    fun `stored datetime is parseable by DateUtils so the dueDate mirror keeps working`() {
        val stored = storeDateTime(LocalDate.of(2026, 9, 29), LocalTime.of(20, 5))

        assertEquals("2026-09-29 20:05", stored)
        val millis = DateUtils.parseDateValueOrNull(stored)
        assertNotNull("DATETIME 值必须能被 DateUtils 解析，否则 dueDate 镜像静默失效", millis)
        assertEquals(LocalDate.of(2026, 9, 29), DateUtils.millisToLocalDate(millis!!))
    }

    @Test
    fun `storeDateTime fills the missing half with today or now instead of writing a broken value`() {
        val dateOnly = storeDateTime(LocalDate.of(2026, 1, 2), null)
        assertTrue(dateOnly.startsWith("2026-01-02 "))
        assertNotNull(DateUtils.parseDateValueOrNull(dateOnly))

        val timeOnly = storeDateTime(null, LocalTime.of(7, 8))
        assertTrue(timeOnly.endsWith(" 07:08"))
        assertTrue(timeOnly.startsWith(LocalDate.now().toString()))
        assertNotNull(DateUtils.parseDateValueOrNull(timeOnly))
    }

    @Test
    fun `parseDateTimeParts accepts full, date-only and time-only stored values`() {
        assertEquals(
            LocalDate.of(2026, 9, 29) to LocalTime.of(20, 5),
            parseDateTimeParts("2026-09-29 20:05")
        )
        assertEquals(LocalDate.of(2026, 9, 29) to null, parseDateTimeParts("2026-09-29"))
        assertEquals(null to LocalTime.of(20, 5), parseDateTimeParts("20:05"))
        assertEquals(null to null, parseDateTimeParts(""))
        assertEquals(null to null, parseDateTimeParts("不是日期"))
    }

    /** 日期与时间分别改一半时，另一半必须保住（这正是拆成两枚胶囊的理由）。 */
    @Test
    fun `changing one half keeps the other`() {
        val (date, time) = parseDateTimeParts("2026-09-29 20:05")
        val newDate = storeDateTime(LocalDate.of(2026, 10, 1), time)
        val newTime = storeDateTime(date, LocalTime.of(9, 30))

        assertEquals("2026-10-01 20:05", newDate)
        assertEquals("2026-09-29 09:30", newTime)
    }

    @Test
    fun `slider values snap to the step and drop trailing zeros`() {
        assertEquals("50", snapSliderValue(50.4f, 1f))
        assertEquals("51", snapSliderValue(50.6f, 1f))
        assertEquals("2.5", snapSliderValue(2.49f, 0.5f))
        assertEquals("50", trimNumber(50.0f))
        assertEquals("50.5", trimNumber(50.5f))
    }

    @Test
    fun `hex color parsing maps known values and never throws on dirty input`() {
        val red = Color(0xFFE53935)

        assertEquals(red, parseHexColor("#E53935"))
        assertEquals(red, parseHexColor("E53935"))
        assertEquals(Color(0x80123456), parseHexColor("#80123456"))
        assertEquals(Color.Transparent, parseHexColor(""))
        assertEquals(Color.Transparent, parseHexColor("#GGGGGG"))
        assertEquals(Color.Transparent, parseHexColor("#12345"))
    }

    /** 「清除」写空串之后，控件必须回到「未选」而不是崩或留一个透明块。 */
    @Test
    fun `cleared color value is treated as unselected`() {
        assertNull(parseDateTimeParts("").first)
        assertEquals(Color.Transparent, parseHexColor(""))
    }

    /**
     * `content://` 是不透明 id，直接显示等于没显示；但**绝不能返回空串** ——
     * 空白行会让用户以为「没选上」，于是重复选择。
     */
    @Test
    fun `media file display name never comes back empty`() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("clip.mp4", displayNameOf(context, "file:///sdcard/Movies/clip.mp4"))
        assertEquals("42", displayNameOf(context, "content://media/external/video/media/42"))
        assertEquals("兜底也要有字", displayNameOf(context, "兜底也要有字"))
    }

    // ── TABLE：列定义来自 options，这是本轮才第一次被读到的格式 ──

    /** 样本用**内置模板里真实存在**的那两条列定义（存钱「明细」）。 */
    @Test
    fun `table columns are decoded from the option spec used by builtin templates`() {
        val cols = tableColumnsOf(tableCfg(listOf("name:名目:TEXT", "amount:金额:CURRENCY")))

        assertEquals(listOf("name", "amount"), cols.map { it.key })
        assertEquals(listOf("名目", "金额"), cols.map { it.label })
        assertEquals(listOf(FieldType.TEXT, FieldType.CURRENCY), cols.map { it.type })
    }

    @Test
    fun `table column spec tolerates missing label, missing type and unknown type`() {
        val cols = tableColumnsOf(
            tableCfg(listOf("only_key", "qty:数量", "x:标签:NOT_A_TYPE", "   ", ":空key:NUMBER"))
        )

        // 只有 key 时 label 回落成 key，类型回落 TEXT
        assertEquals(listOf("only_key", "qty", "x"), cols.map { it.key })
        assertEquals("only_key", cols[0].label)
        assertEquals(listOf(FieldType.TEXT, FieldType.TEXT, FieldType.TEXT), cols.map { it.type })
    }

    /**
     * 载荷编解码往返：`encodeTable` 此前**零调用方**，这条把它第一次真正跑起来。
     * 单元格必须原样回来 —— 它是详情页显示的唯一来源。
     */
    @Test
    fun `table payload round trip keeps columns and cells`() {
        val cols = tableColumnsOf(tableCfg(listOf("name:名目:TEXT", "amount:金额:CURRENCY")))
        val encoded = encodeTable(TableModel(cols, listOf(listOf("早餐", "12"), listOf("打车", "30"))))

        val back = parseTable(encoded)

        assertEquals(listOf("名目", "金额"), back.columns.map { it.label })
        assertEquals(listOf(listOf("早餐", "12"), listOf("打车", "30")), back.rows)
    }

    // ── RANGE：端点必须能被详情页当日期渲染（`encodeRange` 同样是零调用方） ──

    @Test
    fun `range payload round trip keeps endpoints readable as dates`() {
        val back = parseRange(encodeRange(RangeModel("2026-09-01", "2026-09-30")))

        assertNotNull(back)
        assertEquals("2026-09-01", back!!.start)
        assertEquals("2026-09-30", back.end)
        // 详情页就是靠这两个值按日期格式化的 —— 解不出来那一行就是空白
        assertEquals(
            LocalDate.of(2026, 9, 30),
            DateUtils.millisToLocalDate(DateUtils.parseDateValueOrNull(back.end)!!)
        )
    }

    /** 只填了一端也要能存能读：另一端留空，不该被编造成今天。 */
    @Test
    fun `range with one empty endpoint round trips without inventing the other`() {
        val back = parseRange(encodeRange(RangeModel("2026-09-01", "")))

        assertEquals("2026-09-01", back?.start)
        assertEquals("", back?.end)
        assertNull(DateUtils.parseDateValueOrNull(back!!.end))
    }

    @Test
    fun `parseRange rejects garbage instead of inventing endpoints`() {
        assertNull(parseRange(null))
        assertNull(parseRange(""))
        assertNull(parseRange("不是载荷"))
        assertNull(parseRange("""{"v":1,"start":"2026-09-01"}"""))
    }

    // ── 进度就地推进（用户真机反馈：「进度无法更新，添加后只能看」） ──

    /**
     * 步长规则：模板给了 `step` 就用它（存钱模板明确给 500）；否则按**目标 5%**（至少 1）。
     * 这条规则直接决定「点一下推进多少」，写错会让用户觉得按钮没用。
     */
    @Test
    fun `progress step prefers the template step then falls back to a nice integer step`() {
        assertEquals(500.0, progressStepOf(progressCfg(step = 500.0), 10_000.0), 0.0)
        assertEquals(100.0, progressStepOf(progressCfg(step = null), 2_000.0), 0.0) // 购物预算 2000
        assertEquals(1.0, progressStepOf(progressCfg(step = null), null), 0.0) // 没有分母 → 每次 1
        assertEquals(1.0, progressStepOf(progressCfg(step = null), 10.0), 0.0) // 档位不足 1 → 至少 1
        assertEquals(1.0, progressStepOf(progressCfg(step = 0.0), null), 0.0) // 非法 step 不生效
    }

    /**
     * 真机反馈（2026-10-01 截图）：步长原本是 `目标 ÷ 20`，于是
     * **350 页 → 步长 17.5**（点一下变成「164.5 页」）、**24 节 → 步长 1.2**（变成「9.2 节」）——
     * 页数 / 节数 / 天数是**计数**，出现小数根本不成立。
     *
     * 现在步长取"好用档位"，且**目标是整数时步长也取整**。
     */
    @Test
    fun `计数类目标的步长必须是整数（真机出现小数进度的根因）`() {
        assertEquals(20.0, progressStepOf(progressCfg(step = null), 350.0), 0.0) // 阅读 350 页
        assertEquals(1.0, progressStepOf(progressCfg(step = null), 24.0), 0.0) // 学习 24 节
        assertEquals(20.0, progressStepOf(progressCfg(step = null), 300.0), 0.0) // 阅读 300 页
        assertEquals(5.0, progressStepOf(progressCfg(step = null), 100.0), 0.0) // 100 → 5
        assertEquals(20_000.0, progressStepOf(progressCfg(step = null), 300_000.0), 0.0) // 存钱 30 万
    }

    /** 步长为整数时把结果取整：既不再加出小数，也顺手修正历史遗留的小数。 */
    @Test
    fun `推进结果按步长取整`() {
        assertEquals(185.0, snapNudged(164.5 + 20.0, 20.0), 0.0) // 旧的 164.5 → 下一次推进回到整数
        assertEquals(9.0, snapNudged(8.0 + 1.0, 1.0), 0.0)
        assertEquals(58.5, snapNudged(58.0 + 0.5, 0.5), 0.0) // 步长非整数（体重）→ 不动小数语义
    }

    /** 就地推进**只动目标 key**，其余字段原样保留 —— 否则推一次就把别的数据洗掉了。 */
    @Test
    fun `set numeric field only touches the target key and keeps the rest`() {
        val before = """{"targetAmount":10000,"currentAmount":3000,"note":"攒钱"}"""

        val after = Json.parseToJsonElement(setNumericField(before, "currentAmount", 3500.0)) as JsonObject

        assertEquals(3500.0, (after["currentAmount"] as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals(10_000.0, (after["targetAmount"] as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals("攒钱", (after["note"] as JsonPrimitive).content)
    }

    @Test
    fun `set numeric field clamps to the field bounds and survives dirty input`() {
        val clamped = Json.parseToJsonElement(
            setNumericField("{}", "spent", 5_000.0, min = 0.0, max = 2_000.0)
        ) as JsonObject
        assertEquals(2_000.0, (clamped["spent"] as JsonPrimitive).content.toDouble(), 0.0)

        // 脏 fieldsData 不能让它崩，也不能丢新值
        val salvaged = Json.parseToJsonElement(setNumericField("不是 JSON", "spent", 10.0)) as JsonObject
        assertEquals(10.0, (salvaged["spent"] as JsonPrimitive).content.toDouble(), 0.0)
    }

    // ── 明细表勾选（购物「已买」）：约定必须与演示数据、「已买 N/M」统计一致 ──

    @Test
    fun `tick cell accepts the demo convention and the legacy checkmark`() {
        assertTrue(isTickCell("true"))
        assertTrue(isTickCell("TRUE")) // 大小写不敏感
        assertTrue(isTickCell("✓ 已买"))
        assertFalse(isTickCell("false"))
        assertFalse(isTickCell(""))
        assertFalse(isTickCell(null))
    }

    /**
     * 勾选必须落成演示数据同款的 `"true"`/`"false"`，否则详情页「已买 N / M」指标读不到
     * —— 那正是这条链路最容易再次分叉的地方。
     */
    @Test
    fun `toggle flips a table cell using the demo convention`() {
        val before = """{"items_detail":{"v":1,"columns":[""" +
            """{"key":"name","label":"品名","type":"TEXT"},""" +
            """{"key":"bought","label":"已买","type":"BOOLEAN"}],""" +
            """"rows":[["鲜牛奶","true"],["鸡蛋","false"]]}}"""

        val after = toggleTableCellValue(before, "items_detail", 1, 1)

        assertNotNull(after)
        // 演示数据是嵌套对象形态；写回后是字符串原语（compoundRawOf 两种都认）
        val payload = compoundRawOf((Json.parseToJsonElement(after!!) as JsonObject)["items_detail"])
        assertEquals(
            listOf(listOf("鲜牛奶", "true"), listOf("鸡蛋", "true")),
            parseTable(payload).rows
        )
    }

    /** 越界 / 脏载荷 / 没有列定义 ⇒ 返回 null：宁可什么都不做，也不写一份变形载荷。 */
    @Test
    fun `toggle refuses out of range or unparsable payloads`() {
        val payload = """{"t":{"v":1,"columns":[{"key":"a","label":"A","type":"BOOLEAN"}],"rows":[["true"]]}}"""

        assertNull(toggleTableCellValue(payload, "t", 5, 0)) // 行越界
        assertNull(toggleTableCellValue(payload, "t", 0, 9)) // 列越界
        assertNull(toggleTableCellValue(payload, "missing", 0, 0)) // 没有这个 key
        assertNull(toggleTableCellValue("不是 JSON", "t", 0, 0)) // 脏载荷
        assertNull(toggleTableCellValue("""{"t":{"v":1,"rows":[["true"]]}}""", "t", 0, 0)) // 没有列定义
    }

    /**
     * 表单侧「要不要给步进器」：只有**模板显式给了 step** 或**带分母的进度字段**才给。
     * 普通数字（身高 / 体重）不给 —— 没有有意义的步长，加了只是噪音。
     */
    @Test
    fun `form stepper appears only for explicit step or progress fields`() {
        // 存钱「已存金额」：模板显式给 500
        assertEquals(500.0, progressStepFor(progressCfg(step = 500.0), 10_000.0) ?: 0.0, 0.0)
        // 购物「已花费」：没给 step，但它是带分母的进度字段 → 预算的 5%
        assertEquals(100.0, progressStepFor(progressCfg(step = null), 2_000.0) ?: 0.0, 0.0)
        // 进度字段但分母还没填 → 不给（无从推断步长）
        assertNull(progressStepFor(progressCfg(step = null), null))
        // 普通数字字段 → 不给
        assertNull(progressStepFor(plainNumberCfg(), 100.0))
        // 非法 step 不生效，退回进度字段规则
        assertEquals(100.0, progressStepFor(progressCfg(step = 0.0), 2_000.0) ?: 0.0, 0.0)
    }

    // ── 字段编解码：表单保存与详情页就地编辑共用同一口径 ──

    /**
     * 往返：`encode → decode` 出来的表单串必须能**再编码回同一个 JSON 值**。
     * 这条一旦破坏，「点值即改一个日期」与「进编辑页改同一个日期」就会存出两种形态
     * —— 正是审计 §13.5 那类静默黑洞的复发点。
     */
    @Test
    fun `field codec round trips for every simple type`() {
        val cases = listOf(
            FieldConfig("t", "标题", FieldType.TEXT) to "买菜",
            FieldConfig("n", "金额", FieldType.CURRENCY) to "12.5",
            FieldConfig("d", "日期", FieldType.DATE) to "2026-09-26",
            FieldConfig("dt", "时间点", FieldType.DATETIME) to "2026-09-26 20:05",
            FieldConfig("b", "已买", FieldType.BOOLEAN) to "true",
            FieldConfig("tm", "时刻", FieldType.TIME) to "08:30"
        )
        for ((cfg, raw) in cases) {
            val encoded = encodeFieldValue(cfg, raw)
            assertNotNull("${cfg.type} 应当编码成功", encoded)
            val decoded = decodeFieldValue(cfg, encoded)
            assertEquals("${cfg.type} 往返后的表单串", raw, decoded)
            assertEquals("${cfg.type} 再编码应一致", encoded, encodeFieldValue(cfg, decoded!!))
        }
    }

    /** 就地改一个键**不能碰**别的键；清空 = **删键**（与表单保存「空值不写」一致）。 */
    @Test
    fun `apply field value only touches one key and removes it when blank`() {
        val cfg = FieldConfig("note", "备注", FieldType.TEXT)
        val before = """{"title":"买菜","note":"旧的"}"""

        val changed = Json.parseToJsonElement(applyFieldValue(before, cfg, "新的")) as JsonObject
        assertEquals("新的", (changed["note"] as JsonPrimitive).content)
        assertEquals("买菜", (changed["title"] as JsonPrimitive).content)

        val cleared = Json.parseToJsonElement(applyFieldValue(changed.toString(), cfg, "")) as JsonObject
        assertNull(cleared["note"])
        assertEquals("买菜", (cleared["title"] as JsonPrimitive).content)
    }

    // ── 选项类就地编辑 / 日期快捷项 ──

    @Test
    fun `csv helpers keep the multi select form shape`() {
        assertEquals(listOf("a", "b", "c"), csvValues("a, b ,,c"))
        assertEquals(emptyList<String>(), csvValues(""))
        assertEquals("日用,食品", toggleCsv("日用", "食品"))
        assertEquals("食品", toggleCsv("日用,食品", "日用"))
        assertEquals("日用", toggleCsv("", "日用"))
        assertEquals("", toggleCsv("日用", "日用"))
    }

    /** 「周末」= 即将到来的周六；今天就是周六则就是今天（不能给成「下周六」）。 */
    @Test
    fun `upcoming weekend lands on the coming saturday`() {
        // 2026-09-21 是周一，09-26 是周六，09-27 是周日
        assertEquals(LocalDate.of(2026, 9, 26), upcomingWeekend(LocalDate.of(2026, 9, 21)))
        assertEquals(LocalDate.of(2026, 9, 26), upcomingWeekend(LocalDate.of(2026, 9, 26)))
        assertEquals(LocalDate.of(2026, 10, 3), upcomingWeekend(LocalDate.of(2026, 9, 27)))
    }

    private fun progressCfg(step: Double?) = FieldConfig(
        key = "currentAmount",
        label = "已存金额",
        type = FieldType.CURRENCY,
        step = step,
        showAsProgress = true,
        progressTargetKey = "targetAmount"
    )

    /** 普通数字字段（身高之类）：既没 step 也不是进度字段。 */
    private fun plainNumberCfg() = FieldConfig(
        key = "height",
        label = "身高",
        type = FieldType.NUMBER
    )

    private fun tableCfg(options: List<String>) = FieldConfig(
        key = "details",
        label = "明细",
        type = FieldType.TABLE,
        options = options
    )
}
