package com.palmnote.ui.asset

import org.junit.Assert.assertEquals
import org.junit.Test

/** 松手吸附方向规则（[panelSnapTarget]）：快甩看方向（iOS 大标题同款），慢放看 0.8 分界。 */
class PanelSnapTargetTest {

    @Test
    fun `快速上甩即使位置已近展开档也判收合`() {
        // 只收了一点点（0.9，位置规则会判展开）但速度向上很快 → 直接收合
        assertEquals(0f, panelSnapTarget(fraction = 0.9f, velocity = -2f), 0.0001f)
    }

    @Test
    fun `快速下甩即使位置在收起侧也判展开`() {
        // 只开了 10% 但速度向下很快 → 直接展开
        assertEquals(1f, panelSnapTarget(fraction = 0.1f, velocity = 2f), 0.0001f)
    }

    @Test
    fun `慢放未过八成分界吸附收合`() {
        assertEquals(0f, panelSnapTarget(fraction = 0.5f, velocity = 0f), 0.0001f)
    }

    @Test
    fun `慢放开过八成分界吸附展开`() {
        assertEquals(1f, panelSnapTarget(fraction = 0.85f, velocity = 0f), 0.0001f)
    }

    @Test
    fun `慢放贴着两档边缘回到该档`() {
        assertEquals(0f, panelSnapTarget(fraction = 0.02f, velocity = 0f), 0.0001f)
        assertEquals(1f, panelSnapTarget(fraction = 0.98f, velocity = 0f), 0.0001f)
    }
}
