package com.palmnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 派生字段「参考字段」的槽位契约。
 *
 * 这一层修的是「派生字段只能手打字段 key、打错就静默消失」：
 * 槽位语义（谁放 `options[0]`、谁放 `options[1]`、表达式放哪）现在是可测的纯函数，
 * UI 只调它们，不再各写一份拼串逻辑。
 */
class LifeDerivedFieldRefTest {

    private fun cfg(
        key: String,
        type: FieldType,
        label: String = key,
        disabled: Boolean = false,
        options: List<String> = emptyList(),
        defaultValue: String = ""
    ) = FieldConfig(
        key = key, label = label, type = type, disabled = disabled,
        options = options, defaultValue = defaultValue
    )

    private val numberField = cfg("amount", FieldType.NUMBER, label = "金额")
    private val dateField = cfg("when", FieldType.DATE, label = "日期")
    private val textField = cfg("note", FieldType.TEXT, label = "备注")
    private val disabledDate = cfg("old", FieldType.DATE, disabled = true)

    // ── 需要参考 / 槽位数 ─────────────────────────────────────────────────────

    @Test
    fun `只有差值类派生字段需要参考字段`() {
        assertTrue(needsDerivedRef(FieldType.REMAINING))
        assertTrue(needsDerivedRef(FieldType.ELAPSED))
        assertFalse(needsDerivedRef(FieldType.FORMULA)) // 表达式自足
        assertFalse(needsDerivedRef(FieldType.STREAK)) // 跨记录自动统计
        assertFalse(needsDerivedRef(FieldType.NUMBER))
    }

    @Test
    fun `REMAINING 两个槽位，其余一个`() {
        assertEquals(2, derivedRefSlots(FieldType.REMAINING))
        assertEquals(1, derivedRefSlots(FieldType.ELAPSED))
    }

    // ── 候选集 ───────────────────────────────────────────────────────────────

    @Test
    fun `ELAPSED 只能引日期类，且排除自己与停用字段`() {
        val fields = listOf(numberField, dateField, textField, disabledDate)
        val self = cfg("days", FieldType.ELAPSED)
        val out = derivedRefCandidates(FieldType.ELAPSED, fields + self, self.key)
        assertEquals(listOf("when"), out.map { it.key })
    }

    @Test
    fun `REMAINING 只能引数值类，且排除自己`() {
        val fields = listOf(numberField, dateField, textField)
        val self = cfg("remain", FieldType.REMAINING)
        val out = derivedRefCandidates(FieldType.REMAINING, fields + self, self.key)
        assertEquals(listOf("amount"), out.map { it.key })
    }

    // ── 写入槽位 ─────────────────────────────────────────────────────────────

    @Test
    fun `写入槽 0 与追加槽 1`() {
        assertEquals(listOf("a"), withDerivedRef(emptyList(), 0, "a"))
        assertEquals(listOf("a", "b"), withDerivedRef(listOf("a"), 1, "b"))
        assertEquals(listOf("a", "c"), withDerivedRef(listOf("a", "b"), 1, "c"))
    }

    @Test
    fun `清空槽 0 会连同后面的槽一起裁掉`() {
        val cleared = withDerivedRef(listOf("a", "b"), 0, "")
        assertTrue("位置型槽位不该留下空洞：实际=$cleared", cleared.isEmpty())
    }

    @Test
    fun `前导槽为空时写入被忽略，不制造空洞`() {
        // 位置型槽位一旦出现空洞，"减数" 会被串位读成 "被减"
        assertTrue(withDerivedRef(emptyList(), 1, "b").isEmpty())
    }

    @Test
    fun `key 前后的空白被裁掉`() {
        assertEquals(listOf("a"), withDerivedRef(emptyList(), 0, "  a  "))
    }

    // ── 配置完整性 ───────────────────────────────────────────────────────────

    @Test
    fun `FORMULA 没有表达式就是配不全`() {
        // 空表达式一定算不出
        assertTrue(derivedUnconfigured(cfg("bmi", FieldType.FORMULA), emptyList()))
        // 有表达式但模板里一个字段都没有 → 引用的 key 指不到东西，同样是"配不全"
        //（合法表达式的判定见下面「引用解析不到的字段」那条）
        assertTrue(
            derivedUnconfigured(
                cfg("bmi", FieldType.FORMULA, defaultValue = "weight / height"),
                emptyList()
            )
        )
    }

    @Test
    fun `表达式引用的 key 与求值同一口径解析`() {
        assertEquals(listOf("weight", "height"), formulaRefKeys("weight / (height * height / 10000)"))
        assertTrue(formulaRefKeys("1 + 2").isEmpty())
    }

    /**
     * 回归：表达式**引用了解析不到的字段**（打错一个字母）也要算配不全。
     *
     * 只查"表达式是否为空"时，`weight / hight` 会被判为"已配置"，
     * 而 `DerivedEvaluator.formula` 引用缺失会返回 null、上层不渲染 ——
     * 结果是**字段静默消失**，与「参考字段填错」是同一类洞。
     */
    @Test
    fun `FORMULA 引用解析不到的字段也算配不全`() {
        val fields = listOf(
            cfg("weight", FieldType.NUMBER, label = "体重"),
            cfg("height", FieldType.NUMBER, label = "身高")
        )
        assertTrue(
            derivedUnconfigured(
                cfg("bmi", FieldType.FORMULA, defaultValue = "weight / (hight * hight / 10000)"),
                fields
            )
        )
        assertFalse(
            derivedUnconfigured(
                cfg("bmi", FieldType.FORMULA, defaultValue = "weight / (height * height / 10000)"),
                fields
            )
        )
    }

    @Test
    fun `FORMULA 引用已停用字段也算配不全`() {
        val fields = listOf(
            cfg("weight", FieldType.NUMBER),
            cfg("height", FieldType.NUMBER, disabled = true)
        )
        assertTrue(derivedUnconfigured(cfg("bmi", FieldType.FORMULA, defaultValue = "weight / height"), fields))
    }

    /** 减数槽可选（缺省按 0），但**一旦填了就必须指得到字段**。 */
    @Test
    fun `REMAINING 的减数槽填了就必须指得到字段`() {
        val fields = listOf(cfg("target", FieldType.NUMBER), cfg("current", FieldType.NUMBER))
        assertFalse(derivedUnconfigured(cfg("remain", FieldType.REMAINING, options = listOf("target")), fields))
        assertTrue(
            derivedUnconfigured(
                cfg("remain", FieldType.REMAINING, options = listOf("target", "curren")),
                fields
            )
        )
    }

    @Test
    fun `ELAPSED 没选日期字段就是配不全`() {
        assertTrue(derivedUnconfigured(cfg("days", FieldType.ELAPSED), listOf(dateField)))
        assertFalse(
            derivedUnconfigured(cfg("days", FieldType.ELAPSED, options = listOf("when")), listOf(dateField))
        )
    }

    @Test
    fun `差值类没有参考 key 就是配不全`() {
        assertTrue(derivedUnconfigured(cfg("remain", FieldType.REMAINING), listOf(numberField)))
        assertFalse(
            derivedUnconfigured(
                cfg("remain", FieldType.REMAINING, options = listOf("amount")),
                listOf(numberField)
            )
        )
    }

    @Test
    fun `参考指向不存在的字段也算配不全（手打 key 打错就是这种）`() {
        val bad = cfg("remain", FieldType.REMAINING, options = listOf("targetAmount"))
        assertTrue(derivedUnconfigured(bad, listOf(numberField, dateField)))
    }

    @Test
    fun `参考指向已停用的字段也算配不全（等于引用空值）`() {
        val bad = cfg("days", FieldType.ELAPSED, options = listOf("old"))
        assertTrue(derivedUnconfigured(bad, listOf(numberField, disabledDate)))
    }

    @Test
    fun `STREAK 与已停用字段不提示配不全`() {
        assertFalse(derivedUnconfigured(cfg("s", FieldType.STREAK), emptyList()))
        assertFalse(
            derivedUnconfigured(
                cfg("remain", FieldType.REMAINING, disabled = true),
                listOf(numberField)
            )
        )
    }
}
