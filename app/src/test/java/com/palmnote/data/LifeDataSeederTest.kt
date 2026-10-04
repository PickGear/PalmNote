package com.palmnote.data

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.ui.theme.lifeTemplateIdentityHex
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private typealias StringSet = MutableSet<String>

/**
 * 校验内置生活模板清单完整性：
 * 防止误删/误改模板（如"存钱计划"曾被事务化重构意外丢失）导致新用户功能不可达。
 */
class LifeDataSeederTest {

    /**
     * Room 2.7 的 `withTransaction` 会把块投递到 RoomDatabase 自己的事务 Dispatcher 上执行；
     * relaxed mock 的 AppDatabase 交出的 Dispatcher 永远不消费任务，runBlocking 会死等。
     * 这里静态 stub 掉扩展，让块在调用线程同步跑完（seedIfEmpty 的断言不依赖真实事务）。
     */
    @Before
    fun stubRoomTransaction() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery {
            any<AppDatabase>().withTransaction(any<suspend () -> Any?>())
        } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun unstubRoomTransaction() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun seeder() = LifeDataSeeder(
        mockk<LifeTemplateRepository>(relaxed = true),
        mockk<AppDatabase>(relaxed = true)
    )

    private fun relaxedPrefs(): SharedPreferences = mockk<SharedPreferences>(relaxed = true).also { prefs ->
        every { prefs.contains(any()) } returns false
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getStringSet(any(), any()) } returns emptySet()
    }

    @Test
    fun `lifeTemplateSeeds contains all 17 builtin templates`() {
        val seeds = seeder().lifeTemplateSeeds
        // 17 = 18 个内置模板 − 1 个退役（「周报月报」，v1.27 → 报告改由统计页承担）
        assertEquals(17, seeds.size)
    }

    @Test
    fun `checkin template includes note field`() {
        val checkin = seeder().lifeTemplateSeeds.first { it.icon == "calendar_month" }
        val fields = Json.decodeFromString<JsonArray>(checkin.fieldsConfig)
        val note = fields.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "note" }
        assertEquals("备注", note?.jsonObject?.get("label")?.jsonPrimitive?.content)
        assertEquals("TEXT", note?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `focus template includes note field`() {
        val focus = seeder().lifeTemplateSeeds.first { it.icon == "timer" }
        val fields = Json.decodeFromString<JsonArray>(focus.fieldsConfig)
        val note = fields.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "note" }
        assertEquals("备注", note?.jsonObject?.get("label")?.jsonPrimitive?.content)
        assertEquals("TEXT", note?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `legacy focus template receives note on first seed delivery`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), relaxedPrefs())
        val focusSeed = seeder.lifeTemplateSeeds.first { it.icon == "timer" }
        val stored = focusSeed.copy(id = 1, fieldsConfig = "[]")
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))

        seeder.seedIfEmpty()

        coVerify(exactly = 1) {
            repo.updateTemplate(match { it.id == 1L && it.fieldsConfig == focusSeed.fieldsConfig })
        }
    }

    @Test
    fun `first seed delivery preserves existing fields and append order`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), relaxedPrefs())
        val focusSeed = seeder.lifeTemplateSeeds.first { it.icon == "timer" }
        val stored = focusSeed.copy(
            id = 1,
            fieldsConfig = """[{"key":"myField","label":"我的字段","type":"TEXT","sortOrder":1}]"""
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))

        seeder.seedIfEmpty()

        coVerify(exactly = 1) {
            repo.updateTemplate(
                match { updated ->
                    val keys = Json.decodeFromString<JsonArray>(updated.fieldsConfig)
                        .map { it.jsonObject.getValue("key").jsonPrimitive.content }
                    updated.id == 1L && keys == listOf("myField", "note")
                }
            )
        }
    }

    @Test
    fun `first seed delivery leaves malformed stored fields untouched`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), relaxedPrefs())
        val stored = seeder.lifeTemplateSeeds.first { it.icon == "timer" }
            .copy(id = 1, fieldsConfig = """["not-an-object"]""")
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))

        seeder.seedIfEmpty()

        coVerify(exactly = 0) { repo.updateTemplate(match { it.id == 1L }) }
    }

    @Test
    fun `customized focus template is not overwritten`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), relaxedPrefs())
        val stored = seeder.lifeTemplateSeeds.first { it.icon == "timer" }
            .copy(id = 1, fieldsConfig = """[{"key":"myField"}]""")
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.contains("customized_template_ids") } returns true
        every { prefs.getStringSet("customized_template_ids", any()) } returns setOf("1")
        every { prefs.getString(any(), any()) } returns null

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        coVerify(exactly = 0) { repo.updateTemplate(match { it.id == 1L }) }
    }

    @Test
    fun `first customized-set migration includes modified focus template`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), relaxedPrefs())
        val stored = seeder.lifeTemplateSeeds.first { it.icon == "timer" }
            .copy(id = 1, fieldsConfig = """[{"key":"myField"}]""")
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.contains(any()) } returns false
        every { prefs.getString("fields_config:timer", null) } returns "[]"
        every { prefs.getStringSet("customized_template_ids", any()) } returns setOf("1")

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        verify { prefs.edit().putStringSet("customized_template_ids", setOf("1")) }
        coVerify(exactly = 0) { repo.updateTemplate(match { it.id == 1L }) }
    }

    @Test
    fun `first seed delivery protects user fields from the next seed upgrade`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences()
        val updates = mutableListOf<LifeTemplate>()
        coEvery { repo.updateTemplate(any()) } coAnswers {
            updates += firstArg<LifeTemplate>()
        }
        val seed = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true)).lifeTemplateSeeds
            .first { it.icon == "timer" }
        var stored = seed.copy(
            id = 1,
            fieldsConfig = """[{"key":"myField","label":"我的字段","type":"TEXT","sortOrder":1}]"""
        )
        every { repo.getAllTemplates() } answers { flowOf(listOf(stored)) }

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()
        val mergedConfig = updates.single { it.id == 1L }.fieldsConfig
        assertTrue(mergedConfig.contains("\"myField\""))
        assertTrue(mergedConfig.contains("\"note\""))
        assertEquals(setOf("1"), prefs.getStringSet("customized_template_ids", mutableSetOf()))

        stored = stored.copy(fieldsConfig = mergedConfig)
        val updateCountAfterFirstDelivery = updates.count { it.id == 1L }
        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        assertEquals(updateCountAfterFirstDelivery, updates.count { it.id == 1L })
    }

    @Test
    fun `v2 fresh install without field baselines receives upgraded fields`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        // v2 全新安装：只写了 seedVersion，没有写任何 fields_config:* 基线
        val focusSeed = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true)).lifeTemplateSeeds
            .first { it.icon == "timer" }
        val stored = focusSeed.copy(id = 1, fieldsConfig = "[]")
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("life_seed_version", 2).apply()

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        coVerify(exactly = 1) {
            repo.updateTemplate(match { it.id == 1L && it.fieldsConfig == focusSeed.fieldsConfig })
        }
        assertEquals(focusSeed.fieldsConfig, prefs.getString("fields_config:timer", null))
        assertEquals(LifeDataSeeder.SEED_VERSION, prefs.getInt("life_seed_version", 0))
    }

    @Test
    fun `v2 fresh install without baselines still protects customized template`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val stored = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true)).lifeTemplateSeeds
            .first { it.icon == "timer" }
            .copy(id = 1, fieldsConfig = """[{"key":"myField","type":"TEXT"}]""")
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("life_seed_version", 2)
            .putStringSet("customized_template_ids", setOf("1"))
            .apply()

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        coVerify(exactly = 0) { repo.updateTemplate(match { it.id == 1L }) }
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
    fun `sortOrder values are globally unique`() {
        val seeds = seeder().lifeTemplateSeeds
        assertEquals(seeds.size, seeds.map { it.sortOrder }.distinct().size)
    }

    @Test
    fun `system templates are marked special`() {
        val seeds = seeder().lifeTemplateSeeds
        // 仅「专注」是系统型；「周报月报」已退役、不再是模板（v1.27）
        assertEquals(listOf("timer"), seeds.filter { it.isSpecial }.map { it.icon }.sorted())
    }

    @Test
    fun `all seed icons have identity color from the single source table`() {
        val seeds = seeder().lifeTemplateSeeds
        seeds.forEach { tpl ->
            assertTrue("模板 ${tpl.name} (icon=${tpl.icon}) 缺少身份色", lifeTemplateIdentityHex(tpl.icon) != null)
            assertEquals(lifeTemplateIdentityHex(tpl.icon), tpl.color)
        }
    }

    @Test
    fun `seedIfEmpty syncs builtin identity color to seed value`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val legacy = LifeTemplate(
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#66BB6A",
            description = "", fieldsConfig = "[]", layoutType = "card", availableLayouts = "[\"card\",\"list\"]",
            statusFlowConfig = "{}", linkConfig = "{}", isBuiltin = true, isHidden = false, isSpecial = false,
            sortOrder = 8
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(legacy))
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true))
        seeder.seedIfEmpty()
        coVerify(exactly = 1) {
            repo.updateTemplate(match { it.id == 1L && it.color == "#FFB300" && !it.isSpecial })
        }
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
    fun `new install records fields config baseline for every seed`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        every { repo.getAllTemplates() } returns flowOf(emptyList())
        val prefs = relaxedPrefs()

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true)).lifeTemplateSeeds.forEach { seed ->
            verify {
                prefs.edit().putString("fields_config:${seed.icon}", seed.fieldsConfig)
            }
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
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#FFB300",
            description = "", fieldsConfig = "[]", layoutType = "card", availableLayouts = "[\"card\",\"list\"]",
            statusFlowConfig = "{}", linkConfig = "{}", isBuiltin = true, isHidden = false, isSpecial = false,
            sortOrder = 8
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))
        // 上次同步写入的值与当前存储一致（= 旧种子 "[]"）→ 用户没改过，应修复为新种子
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns "[]"
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs)
        seeder.seedIfEmpty()
        coVerify(exactly = 1) {
            repo.updateTemplate(match { it.id == 1L && it.fieldsConfig != "[]" && it.icon == "subscriptions" })
        }
    }

    @Test
    fun `seed upgrade applies full seed config when baseline matches`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val stored = LifeTemplate(
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#FFB300",
            description = "", layoutType = "card", availableLayouts = "[\"card\",\"list\"]",
            fieldsConfig = """[{"key":"price","label":"旧价格标签","type":"NUMBER","sortOrder":1}]""",
            statusFlowConfig = "{}", linkConfig = "{}", isBuiltin = true, isHidden = false, isSpecial = false,
            sortOrder = 8
        )
        val seed = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true)).lifeTemplateSeeds
            .first { it.icon == "subscriptions" }
        every { repo.getAllTemplates() } returns flowOf(listOf(stored))
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString("fields_config:subscriptions", any()) } returns stored.fieldsConfig

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        coVerify(exactly = 1) {
            repo.updateTemplate(match { it.id == 1L && it.fieldsConfig == seed.fieldsConfig })
        }
        verify { prefs.edit().putString("fields_config:subscriptions", seed.fieldsConfig) }
    }

    @Test
    fun `first seed delivery applies seed when no baseline exists`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val legacy = LifeTemplate(
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#FFB300",
            description = "", fieldsConfig = "[]", layoutType = "card", availableLayouts = "[\"card\",\"list\"]",
            statusFlowConfig = "{}", linkConfig = "{}", isBuiltin = true, isHidden = false, isSpecial = false,
            sortOrder = 8
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(legacy))
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any<String>()) } returns null // 机制上线后首次启动：无基线
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs)
        seeder.seedIfEmpty()
        // 首次投递没有历史基线：直接应用新种子，并记录新种子快照与 seedVersion。
        coVerify(exactly = 1) {
            repo.updateTemplate(match { it.id == 1L && it.fieldsConfig != "[]" && it.icon == "subscriptions" })
        }
        verify { prefs.edit().putString("fields_config:subscriptions", match { it != "[]" }) }
        verify { prefs.edit().putInt("life_seed_version", LifeDataSeeder.SEED_VERSION) }
    }

    @Test
    fun `first migration protects modified templates before applying seed`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val modified = LifeTemplate(
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#FFB300",
            description = "", fieldsConfig = "[{\"key\":\"myField\"}]", layoutType = "card",
            availableLayouts = "[\"card\"]", statusFlowConfig = "{}", linkConfig = "{}",
            isBuiltin = true, isHidden = false, isSpecial = false, sortOrder = 8
        )
        val untouched = modified.copy(id = 2, icon = "savings", fieldsConfig = "[]")
        every { repo.getAllTemplates() } returns flowOf(listOf(modified, untouched))
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.contains("customized_template_ids") } returns false
        every { prefs.getString(any(), any()) } returns "[]"

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        verify { prefs.edit().putStringSet("customized_template_ids", setOf("1")) }
        coVerify(exactly = 0) { repo.updateTemplate(match { it.id == 1L }) }
        coVerify(exactly = 1) { repo.updateTemplate(match { it.id == 2L && it.fieldsConfig != "[]" }) }
    }

    @Test
    fun `baseline without seed version keeps customized template`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val modified = LifeTemplate(
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#FFB300",
            description = "", fieldsConfig = "[{\"key\":\"myField\"}]", layoutType = "card",
            availableLayouts = "[\"card\"]", statusFlowConfig = "{}", linkConfig = "{}",
            isBuiltin = true, isHidden = false, isSpecial = false, sortOrder = 8
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(modified))
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.contains("life_seed_version") } returns false
        every { prefs.contains("customized_template_ids") } returns false
        every { prefs.getString("fields_config:subscriptions", any()) } returns "[]"

        LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs).seedIfEmpty()

        coVerify(exactly = 0) { repo.updateTemplate(match { it.id == 1L }) }
        verify { prefs.edit().putStringSet("customized_template_ids", setOf("1")) }
    }

    @Test
    fun `seedIfEmpty does not overwrite user-customized builtin template`() = runBlocking {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val customized = LifeTemplate(
            id = 1, name = "订阅记录", category = "记录", icon = "subscriptions", color = "#FFB300",
            description = "", fieldsConfig = "[{\"key\":\"myField\"}]", layoutType = "card",
            availableLayouts = "[\"card\"]", statusFlowConfig = "{}", linkConfig = "{}",
            isBuiltin = true, isHidden = false, isSpecial = false, sortOrder = 8
        )
        every { repo.getAllTemplates() } returns flowOf(listOf(customized))
        // 上次同步写入的是旧种子（≠ 当前存储值）→ 当前值是用户改的，必须跳过
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns "[]"
        val seeder = LifeDataSeeder(repo, mockk<AppDatabase>(relaxed = true), prefs)
        seeder.seedIfEmpty()
        coVerify(exactly = 0) { repo.updateTemplate(any()) }
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

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defaults: StringSet?): StringSet? = (values[key] as? Set<String>)?.toMutableSet() ?: defaults

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = applyChange(key, value)

            override fun putStringSet(key: String, values: StringSet?): SharedPreferences.Editor = applyChange(key, values?.toMutableSet())

            override fun putInt(key: String, value: Int): SharedPreferences.Editor = applyChange(key, value)
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = applyChange(key, value)
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = applyChange(key, value)
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = applyChange(key, value)

            override fun remove(key: String): SharedPreferences.Editor {
                removals += key
                changes.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                changes.clear()
                removals.clear()
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) values.clear()
                removals.forEach(values::remove)
                values.putAll(changes)
            }

            private fun applyChange(key: String, value: Any?): SharedPreferences.Editor {
                changes[key] = value
                removals -= key
                return this
            }
        }
    }
}
