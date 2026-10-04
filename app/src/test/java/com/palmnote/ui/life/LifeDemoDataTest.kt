package com.palmnote.ui.life

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeDemoDataTest {

    @Test
    fun `demo data keeps the documented item count`() {
        val dailyIcons = setOf("calendar_month", "mood", "book", "timer", "fitness_center")
        val oneTime = LifeDemoData.items.filterNot { it.templateIcon in dailyIcons }
        val daily = LifeDemoData.items.filter { it.templateIcon in dailyIcons }

        assertEquals(61, LifeDemoData.items.size)
        assertEquals(13, oneTime.size)
        assertEquals(48, daily.size)
    }

    @Test
    fun `diary demo data matches the D13 aggregate targets`() {
        val diary = LifeDemoData.items.filter { it.templateIcon == "book" }

        assertEquals(14, diary.size)
        assertEquals((0..4).toList() + (6..14).toList(), diary.map { it.daysAgo }.sorted())

        // 配图已改为真实 IMAGE 字段值（播种器物化 assets 图片），不再是文本数组占位
        assertEquals("demo_flower.jpg", diary.single { it.daysAgo == 0 }.images["photos"])
        assertEquals("demo_sunset.jpg", diary.single { it.daysAgo == 1 }.images["photos"])
        assertTrue(diary.filter { it.images.isNotEmpty() }.size == 2)
        assertEquals("家里", diary.fields(0)["location"]?.jsonPrimitive?.content)
    }

    @Test
    fun `dtl 07 and dtl 09 use the designed templates`() {
        val countdown = LifeDemoData.items.single { it.title == "妈妈生日" }
        val birthday = LifeDemoData.items.single { it.title == "爸爸" }

        assertEquals("timer_off", countdown.templateIcon)
        // 备注与演示图是后续补充的展示字段：字段值断言只锁关键部分
        val countdownFields = Json.decodeFromString<JsonObject>(countdown.fieldsData)
        // 日期一律写成相对占位符（写死 ISO 会随时间漂移，见 DemoDataIntegrityTest 的守卫）
        assertEquals("+87d", countdownFields["targetDate"]?.jsonPrimitive?.content)
        assertEquals("true", countdownFields["reminder"]?.jsonPrimitive?.content)
        assertEquals("demo_gift.jpg", countdown.images["photo"])
        assertEquals("cake", birthday.templateIcon)
        assertEquals(23, birthday.dueInDays)
    }

    @Test
    fun `travel demo carries enough route points for the route board`() {
        val travel = LifeDemoData.items.single { it.templateIcon == "flight" }
        val route = travel.fields()["route"]
        val model = com.palmnote.domain.model.parseMap(route.toString())

        assertTrue("旅行演示记录必须有路线图数据", model.route.size >= 2)
        assertEquals(listOf("西湖", "灵隐寺", "西溪湿地", "良渚博物院"), model.route.map { it.name })
    }

    private fun List<LifeDemoItem>.fields(daysAgo: Int): JsonObject = single { it.daysAgo == daysAgo }.fields()

    private fun LifeDemoItem.fields(): JsonObject = Json.decodeFromString(fieldsData)
}
