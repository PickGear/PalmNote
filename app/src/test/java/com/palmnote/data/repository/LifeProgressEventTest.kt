package com.palmnote.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 存钱达标提醒的**触发闸门**：进度是否上涨。
 *
 * 它判错就会"该响不响"或"乱响"，所以四种边界都钉住：
 * 上涨算、下调不算、不变不算、配置/数据不可用一律不算（宁可不提醒，也不误提醒）。
 */
class LifeProgressEventTest {

    private val config =
        """[{"key":"targetAmount","label":"目标","type":"CURRENCY"},""" +
            """{"key":"currentAmount","label":"已存","type":"CURRENCY","showAsProgress":true,"progressTargetKey":"targetAmount"}]"""

    @Test
    fun `进度上涨才算推进`() {
        assertTrue(progressIncreased(config, """{"currentAmount":1000}""", """{"currentAmount":1500}"""))
    }

    @Test
    fun `下调与不变都不算推进`() {
        assertFalse(progressIncreased(config, """{"currentAmount":1500}""", """{"currentAmount":1000}"""))
        assertFalse(progressIncreased(config, """{"currentAmount":1500}""", """{"currentAmount":1500}"""))
    }

    @Test
    fun `配置或数据不可用时一律不触发`() {
        assertFalse(progressIncreased("not json", """{"currentAmount":1}""", """{"currentAmount":2}"""))
        assertFalse(progressIncreased(config, "not json", """{"currentAmount":2}"""))
        assertFalse(progressIncreased(config, """{"currentAmount":"abc"}""", """{"currentAmount":2}"""))
        // 模板里没有标 showAsProgress 的字段 → 不是进度型条目
        assertFalse(progressIncreased("""[{"key":"a","type":"TEXT"}]""", """{"a":1}""", """{"a":2}"""))
    }
}
