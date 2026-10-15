package com.palmnote.ui.widget

import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.util.BuiltinGoalTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 概览组件目标卡取数（[WidgetData.goalTileData]）。
 *
 * 目标自 v1.4.2 起是目标模板（kind = GOAL）下的生活条目，这里用**真实模板**的
 * fieldsConfig 验证选卡、百分比与计数文案 —— 含递减方向（起始值 > 目标值）。
 */
class WidgetGoalTileDataTest {

    private val config = BuiltinGoalTemplate.FIELDS_CONFIG

    private fun goal(title: String, fieldsData: String) = LifeItem(
        templateId = 1L,
        title = title,
        fieldsData = fieldsData
    )

    @Test
    fun `no items means no tile - the widget side shows the empty state`() {
        assertNull(WidgetData.goalTileData(config, emptyList(), "全部完成"))
    }

    @Test
    fun `increase goal shows percent and current over target counts`() {
        val tile = WidgetData.goalTileData(
            config,
            listOf(goal("今年读完 12 本书", """{"target":"12","current":"7","unitText":"本"}""")),
            "全部完成"
        )!!
        assertEquals("58%", tile.pctText)
        assertEquals("今年读完 12 本书", tile.name)
        assertEquals("7/12", tile.countsText)
    }

    @Test
    fun `decrease goal measures from the start value and uses arrow counts`() {
        // 戒烟：起始 20 → 目标 0，当前读数 15 ＝ 已走 5/20
        val tile = WidgetData.goalTileData(
            config,
            listOf(goal("戒烟", """{"target":"0","current":"15","initial":"20","unitText":"支"}""")),
            "全部完成"
        )!!
        assertEquals("25%", tile.pctText)
        assertEquals("戒烟", tile.name)
        assertEquals("15 → 0", tile.countsText)
    }

    @Test
    fun `all goals finished shows full percent with the done label`() {
        val tile = WidgetData.goalTileData(
            config,
            listOf(goal("背完单词", """{"target":"30","current":"30"}""")),
            "全部完成"
        )!!
        assertEquals("100%", tile.pctText)
        assertEquals("全部完成", tile.name)
        assertEquals("--", tile.countsText)
    }

    @Test
    fun `finished goals are skipped in favor of the first unfinished one`() {
        val tile = WidgetData.goalTileData(
            config,
            listOf(
                goal("背完单词", """{"target":"30","current":"30"}"""),
                goal("戒烟", """{"target":"0","current":"15","initial":"20"}""")
            ),
            "全部完成"
        )!!
        assertEquals("戒烟", tile.name)
    }

    @Test
    fun `fractional counts keep their decimal instead of padding zeros`() {
        // 减重：80 → 70，当前 72.5；计数不能写成「72.5 → 70.0」
        val tile = WidgetData.goalTileData(
            config,
            listOf(goal("减重", """{"target":"70","current":"72.5","initial":"80"}""")),
            "全部完成"
        )!!
        assertEquals("72.5 → 70", tile.countsText)
    }

    @Test
    fun `goals without a progressable value show the name with dashes - never all-done`() {
        // 还没填当前值：算不出进度。谎报「100% 全部完成」比没有进度更糟。
        val tile = WidgetData.goalTileData(
            config,
            listOf(goal("今年读完 12 本书", """{"target":"12"}""")),
            "全部完成"
        )!!
        assertEquals("--", tile.pctText)
        assertEquals("今年读完 12 本书", tile.name)
        assertEquals("--", tile.countsText)
    }

    @Test
    fun `mixed finished and uncomputable goals do not claim all-done`() {
        // 一条完成、一条还没填当前值 → 有条目可展示就不该说「全部完成」
        val tile = WidgetData.goalTileData(
            config,
            listOf(
                goal("背完单词", """{"target":"30","current":"30"}"""),
                goal("今年读完 12 本书", """{"target":"12"}""")
            ),
            "全部完成"
        )!!
        assertEquals("--", tile.pctText)
        assertEquals("背完单词", tile.name)
    }
}
