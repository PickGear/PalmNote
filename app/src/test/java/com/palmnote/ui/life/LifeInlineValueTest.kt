package com.palmnote.ui.life

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 进度值**就地编辑**的纯逻辑。
 *
 * 钉住两件事：
 * 1. **输入净化** —— 系统数字键盘也可能带出字母或第二个小数点（粘贴/输入法），必须过滤；
 * 2. **越界判定** —— 就地编辑没有"按钮置灰"这个手段，所以越界要靠判定 + 提示拦住，
 *    而不是把用户输的数悄悄改掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeInlineValueTest {

    @Test
    fun `输入净化只留数字与一个小数点`() {
        assertEquals("173", sanitizeNumericInput("173"))
        assertEquals("17.5", sanitizeNumericInput("17.5"))
        // 第二个小数点被去掉，但**数字保留**（"17.5.3" → "17.53"）：
        // 就地编辑不该静默丢掉用户敲过的数字
        assertEquals("17.53", sanitizeNumericInput("17.5.3"))
        assertEquals("1234", sanitizeNumericInput("a1b2c3d4"))
        assertEquals("", sanitizeNumericInput("abc"))
        assertEquals("123456789", sanitizeNumericInput("123456789012")) // 限长 9
    }

    @Test
    fun `空值算"还没填"`() {
        assertEquals(ValueBlock.EMPTY, valueBlockReason(null, min = null, max = 10.0))
    }

    @Test
    fun `越界会被判定出来（就地编辑靠它拦住，而不是静默改数）`() {
        assertEquals(ValueBlock.OVER_MAX, valueBlockReason(301.0, min = null, max = 300.0))
        assertEquals(ValueBlock.UNDER_MIN, valueBlockReason(2.0, min = 5.0, max = null))
        assertNull(valueBlockReason(173.0, min = 0.0, max = 300.0))
    }

    @Test
    fun `回填时整数不带小数点`() {
        assertEquals("173", trimNumber(173.0))
        assertEquals("17.5", trimNumber(17.5))
    }

    @Test
    fun `计数显示串带千分位与单位`() {
        assertEquals("173 / 300 页", progressCountDisplay(173.0, 300.0, "页"))
        assertEquals("3,500 / 10,000", progressCountDisplay(3_500.0, 10_000.0, ""))
        assertEquals("12 天", progressCountDisplay(12.0, null, "天"))
        assertEquals("0", progressCountDisplay(0.0, 0.0, "")) // 目标为 0 不画分母
    }
}
