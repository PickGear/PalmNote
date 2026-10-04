package com.palmnote.ui.life

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * −/+ 的**增量推进**链路：读库里最新值 → 加减 → 钳制。
 *
 * 审查时发现的覆盖问题就出在这条链路上：旧版把 `current ± step` 算在**界面**上再写库，
 * 而"点数值就地编辑"的提交是异步写库 —— 两条写入交错时，± 用界面旧值算出的绝对值
 * 会**把刚提交的数字覆盖掉**。改成"写入那刻读最新值再加增量"之后，这个覆盖不可能发生。
 *
 * 这里钉住链路的两端：读取口径（含脏值）与钳制。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeProgressStepperTest {

    @Test
    fun `从 fieldsData 读当前数值`() {
        assertEquals(173.0, numericFieldOf("""{"pages":173}""", "pages"), 0.0001)
        assertEquals(17.5, numericFieldOf("""{"w":17.5}""", "w"), 0.0001)
    }

    @Test
    fun `读不出就按 0 起算（不猜、不崩）`() {
        assertEquals(0.0, numericFieldOf("""{"pages":"abc"}""", "pages"), 0.0001)
        assertEquals(0.0, numericFieldOf("""{"other":1}""", "pages"), 0.0001)
        assertEquals(0.0, numericFieldOf("not json at all", "pages"), 0.0001)
    }

    @Test
    fun `增量推进在边界处被钳制`() {
        val data = """{"pages":290}"""
        // 290 + 20 → 超上限，钳到 300
        val up = setNumericField(data, "pages", numericFieldOf(data, "pages") + 20, max = 300.0)
        assertEquals(300.0, numericFieldOf(up, "pages"), 0.0001)
        // 290 − 400 → 低于下限，钳到 0
        val down = setNumericField(data, "pages", numericFieldOf(data, "pages") - 400, min = 0.0)
        assertEquals(0.0, numericFieldOf(down, "pages"), 0.0001)
    }

    @Test
    fun `长按连发：起步 150ms，6 步后加速到 80ms`() {
        // 时序本身没法单测，但"第几步该多快"这条曲线可以 —— 写错的话长按要么慢得像卡住、要么快到失控
        assertEquals(150L, repeatIntervalMs(1))
        assertEquals(150L, repeatIntervalMs(5))
        assertEquals(80L, repeatIntervalMs(6))
        assertEquals(80L, repeatIntervalMs(50))
    }

    @Test
    fun `推进基于写入那刻的值，而不是别处算好的绝对值`() {
        val data = """{"pages":100}"""
        // 先"提交"到 250（就地编辑），再推进 +10 —— 必须得到 260，而不是 110
        val committed = setNumericField(data, "pages", 250.0)
        val nudged = setNumericField(committed, "pages", numericFieldOf(committed, "pages") + 10)
        assertEquals(260.0, numericFieldOf(nudged, "pages"), 0.0001)
    }
}
