package com.palmnote.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 五档归并的边界。
 *
 * 目录里只做 4×2 / 2×2 / 4×1 / 2×1 / 4×4（2026-10-21 用户定），其余尺寸由桌面自由拖拽，
 * 组件按宽高阈值归到**相邻**一档重排 —— 这里锁住阈值，免得日后调数字时把某一档吃掉。
 */
class WidgetSizeClassTest {

    @Test
    fun `五档设计尺寸各自归回自己`() {
        WidgetSizeClass.entries.forEach { sizeClass ->
            assertEquals(
                sizeClass,
                WidgetSizeClass.of(sizeClass.designWidthDp, sizeClass.designHeightDp)
            )
        }
    }

    @Test
    fun `旧版桌面给的脏组合不会跳到对角档`() {
        // Android 12 以下常见：横屏取「最宽 × 最矮」、竖屏取「最窄 × 最高」。
        // 用最近邻会从 4×1 跳到 2×4，阈值法只降一档，不会串味。
        assertEquals(WidgetSizeClass.BAR, WidgetSizeClass.of(340, 74))
        assertEquals(WidgetSizeClass.TALL, WidgetSizeClass.of(160, 360))
    }

    @Test
    fun `跨档尺寸按阈值归到相邻档`() {
        // 3 格宽 2 格高：宽度过了 210 阈值 → 两栏横幅版，不是方卡
        assertEquals(WidgetSizeClass.WIDE, WidgetSizeClass.of(245, 148))
        // 2 格宽 2.5 格高：高度还没到 220 的大卡线，宽度也不够 → 方卡
        assertEquals(WidgetSizeClass.SQUARE, WidgetSizeClass.of(160, 190))
        // 2 格宽 3 格高：只满足高度条件 → 大卡
        assertEquals(WidgetSizeClass.TALL, WidgetSizeClass.of(160, 268))
        // 宽度 209 差 1dp 不够宽，仍算窄档（阈值不能写成「大于等于 200」这种含糊值）
        assertEquals(WidgetSizeClass.SQUARE, WidgetSizeClass.of(209, 150))
    }

    @Test
    fun `尺寸为零时按单行窄档兜底而不是崩`() {
        // 个别桌面 resize 过程中会给 0；归到最小的 CHIP 比抛异常好，
        // 调用方本来也会用组件自己的设计尺寸先兜一次（见 sizedRemoteViews）。
        assertEquals(WidgetSizeClass.CHIP, WidgetSizeClass.of(0, 0))
    }

    @Test
    fun `只有单行两档是横向铺开的版式`() {
        assertTrue(WidgetSizeClass.BAR.isRow)
        assertTrue(WidgetSizeClass.CHIP.isRow)
        assertFalse(WidgetSizeClass.WIDE.isRow)
        assertFalse(WidgetSizeClass.SQUARE.isRow)
        assertFalse(WidgetSizeClass.TALL.isRow)
    }
}
