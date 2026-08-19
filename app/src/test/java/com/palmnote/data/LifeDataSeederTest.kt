package com.palmnote.data

import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验内置生活模板清单完整性：
 * 防止误删/误改模板（如"存钱计划"曾被事务化重构意外丢失）导致新用户功能不可达。
 */
class LifeDataSeederTest {

    private fun seeder() = LifeDataSeeder(
        mockk<LifeTemplateRepository>(relaxed = true),
        mockk<AppDatabase>(relaxed = true)
    )

    @Test
    fun `lifeTemplateSeeds contains all 15 builtin templates`() {
        val seeds = seeder().lifeTemplateSeeds
        assertEquals(15, seeds.size)
    }

    @Test
    fun `savings template is present with correct key fields`() {
        val seeds = seeder().lifeTemplateSeeds
        val savings = seeds.firstOrNull { it.icon == "savings" }
        assertTrue("savings 模板缺失", savings != null)
        assertEquals("savings", savings?.icon)
        assertEquals("card", savings?.layoutType)
        assertTrue(savings!!.isBuiltin)
    }

    @Test
    fun `template names are unique`() {
        val seeds = seeder().lifeTemplateSeeds
        assertEquals(seeds.size, seeds.map { it.name }.distinct().size)
    }

    @Test
    fun `template icons are unique`() {
        val seeds = seeder().lifeTemplateSeeds
        assertEquals(seeds.size, seeds.map { it.icon }.distinct().size)
    }

    @Test
    fun `all templates have positive sort order`() {
        val seeds = seeder().lifeTemplateSeeds
        assertTrue(seeds.all { it.sortOrder > 0 })
    }

    @Test
    fun `all template fieldsConfig is valid JSON array`() {
        val seeds = seeder().lifeTemplateSeeds
        seeds.forEach { tpl ->
            val parsed = runCatching {
                kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonArray>(tpl.fieldsConfig)
            }
            assertTrue("模板 ${tpl.name} (icon=${tpl.icon}) 的 fieldsConfig 非法 JSON: ${parsed.exceptionOrNull()?.message}", parsed.isSuccess)
        }
    }

    @Test
    fun `subscription template options is a valid JSON array`() {
        val seeds = seeder().lifeTemplateSeeds
        val sub = seeds.firstOrNull { it.icon == "subscriptions" }
        assertTrue("订阅模板缺失", sub != null)
        val json = kotlinx.serialization.json.Json
        val arr = json.decodeFromString<kotlinx.serialization.json.JsonArray>(sub!!.fieldsConfig)
        val cycle = arr.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "billingCycle" }
        assertTrue("billingCycle 字段缺失", cycle != null)
        val options = cycle!!.jsonObject["options"]
        assertTrue("options 应为 JSON 数组", options is kotlinx.serialization.json.JsonArray)
        val values = (options as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }
        assertEquals(listOf("monthly", "quarterly", "yearly"), values)
    }

    @Test
    fun `seedIfEmpty with existing templates repairs builtin fieldsConfig`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val stored = LifeTemplate(
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#66BB6A",
            description = "", fieldsConfig = "[]", layoutType = "card", availableLayouts = "[\"card\",\"list\"]",
            statusFlowConfig = "{}", linkConfig = "{}", isBuiltin = true, isHidden = false, isSpecial = false,
            sortOrder = 8
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true))
        seeder.seedIfEmpty()
        coVerify(exactly = 1) {
            repo.updateTemplate(match { it.id == 1L && it.fieldsConfig != "[]" && it.icon == "subscriptions" })
        }
    }

    @Test
    fun `seedIfEmpty with existing templates skips custom templates`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val custom = LifeTemplate(
            id = 2, name = "我的模板", category = "计划", icon = "custom_icon", color = "#FFFFFF",
            description = "", fieldsConfig = "[]", layoutType = "card", availableLayouts = "[\"card\"]",
            statusFlowConfig = "{}", linkConfig = "{}", isBuiltin = false, isHidden = false, isSpecial = false,
            sortOrder = 1
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(custom))
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true))
        seeder.seedIfEmpty()
        coVerify(exactly = 0) { repo.updateTemplate(any()) }
    }
}
