package com.palmnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进度求值的**方向语义**（v1.4.2 起支持递减）。
 *
 * 方向写在数据里而不是配置里：配了起始值字段、且起始值 > 目标值时按递减
 * `(起始 − 当前) / (起始 − 目标)`，否则按递增 `当前 / 目标`。详情页 / 列表卡 / 小组件共用这一份求值。
 */
class ProgressEvalTest {

    @Test
    fun `increase divides current by target`() {
        val p = evalProgress(cur = 7.0, target = 12.0, start = null)!!
        assertEquals(7f / 12f, p.fraction, 0.001f)
        assertFalse(p.decreasing)
    }

    @Test
    fun `missing or zero target yields no progress`() {
        // 绝不拿 0 冒充进度（§14.1 缺陷类）
        assertNull(evalProgress(cur = 7.0, target = 0.0, start = null))
    }

    @Test
    fun `decrease measures how far the reading has moved`() {
        // 戒烟：起始 20 → 目标 0，当前读数 15 ＝ 已走 5/20
        val p = evalProgress(cur = 15.0, target = 0.0, start = 20.0)!!
        assertTrue(p.decreasing)
        assertEquals(0.25f, p.fraction, 0.001f)
    }

    @Test
    fun `decrease with non-zero target`() {
        // 减重：80 → 70，当前 75 ＝ 5/10
        assertEquals(0.5f, evalProgress(cur = 75.0, target = 70.0, start = 80.0)!!.fraction, 0.001f)
    }

    @Test
    fun `start not greater than target stays increase`() {
        // 起始值 ≤ 目标值不算递减：脏数据也不能把进度算反
        val p = evalProgress(cur = 3.0, target = 10.0, start = 10.0)!!
        assertFalse(p.decreasing)
        assertEquals(0.3f, p.fraction, 0.001f)
    }

    @Test
    fun `fraction clamps at both ends`() {
        assertEquals(1f, evalProgress(cur = 15.0, target = 12.0, start = null)!!.fraction, 0.001f)
        assertEquals(0f, evalProgress(cur = -5.0, target = 12.0, start = null)!!.fraction, 0.001f)
        assertEquals(0f, evalProgress(cur = 25.0, target = 0.0, start = 20.0)!!.fraction, 0.001f)
        assertEquals(1f, evalProgress(cur = 0.0, target = 0.0, start = 20.0)!!.fraction, 0.001f)
    }

    @Test
    fun `stored progress follows the field config contract`() {
        val config = """[{"key":"target","label":"目标值","type":"NUMBER"},""" +
            """{"key":"current","label":"当前值","type":"NUMBER","showAsProgress":true,""" +
            """"progressTargetKey":"target","progressStartKey":"initial"},""" +
            """{"key":"initial","label":"起始值","type":"NUMBER"}]"""

        val decreased = progressOfStored(config, """{"target":0,"current":15,"initial":20}""")!!
        assertTrue(decreased.decreasing)
        assertEquals(0.25f, decreased.fraction, 0.001f)

        val increased = progressOfStored(config, """{"target":12,"current":7}""")!!
        assertFalse(increased.decreasing)
        assertEquals(7f / 12f, increased.fraction, 0.001f)

        // 缺分母 / 脏 JSON / 模板里没有进度字段 → 不给进度
        assertNull(progressOfStored(config, """{"current":7}"""))
        assertNull(progressOfStored(config, "not-json"))
        assertNull(progressOfStored("""[{"key":"title","label":"标题","type":"TEXT"}]""", """{"title":"x"}"""))
    }
}
