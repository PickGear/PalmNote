package com.palmnote.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 版本历史分区标签的着色规则。
 *
 * 背景：真机截图里 1.4.0 的分区标签（「生活模块全新改版」这类领域分组）全是灰的——
 * 原映射只认「新增/变更/修复/安全」四个中文词，其余一律回退灰色，英文标题也一样落灰。
 * 本测试把修正后的规则钉住：语义段中英同色、领域分组按索引轮转且**不再是灰的**。
 */
class VersionHistoryTintTest {

    @Test
    fun `semantic sections are recognized in both languages`() {
        assertEquals(sectionTint("新增", 0), sectionTint("Added", 0))
        assertEquals(sectionTint("变更", 0), sectionTint("Changed", 0))
        assertEquals(sectionTint("修复", 0), sectionTint("Fixed", 0))
        assertEquals(sectionTint("安全", 0), sectionTint("Security", 0))
        assertEquals(sectionTint("说明", 0), sectionTint("Notes", 0))
    }

    @Test
    fun `semantic colors do not depend on position`() {
        assertEquals(sectionTint("修复", 0), sectionTint("修复", 3))
        assertEquals(sectionTint("新增", 5), sectionTint("新增", 1))
    }

    @Test
    fun `domain groups cycle the palette by index`() {
        val first = sectionTint("生活模块全新改版", 0)
        val second = sectionTint("示例数据模式", 1)
        assertNotEquals("相邻领域分组应取不同颜色", first, second)
        // 色轮长度固定，索引取模：第 0 个与第 6 个同色
        assertEquals(sectionTint("任意分组", 0), sectionTint("另一分组", 6))
    }

    @Test
    fun `domain groups are no longer grey`() {
        val neutral = sectionTint("说明", 0)
        listOf("生活模块全新改版", "示例数据模式", "桌面小组件 2.0", "主题与个性化").forEachIndexed { i, title ->
            assertNotEquals("领域分组「$title」不该是中性灰", neutral, sectionTint(title, i))
        }
    }

    @Test
    fun `notes stay neutral on purpose`() {
        // 「说明」是补充说明而非变更类型，故意保持中性灰，不参与色轮
        assertEquals(sectionTint("说明", 0), sectionTint("说明", 3))
    }
}
