package com.palmnote.ui.life

import android.app.Application
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.demoAssets
import com.palmnote.data.demoBills
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.ui.asset.assetCategoryItems
import com.palmnote.ui.bills.expenseCategoryItems
import com.palmnote.ui.bills.incomeCategoryItems
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * **演示数据完整性审计**（把一次性的人工审计脚本固化成测试）。
 *
 * 起因：演示数据是「真实写进库的行」，改动代码不会自动纠正内容——本轮就发生过
 * 分类用了不存在的自造词（「住房」应为内置「居住」）、选项越界（心情因子写「睡眠」
 * 但选项里没有）、名称与配图不符（照片是 ASICS 却叫 HOKA）、字段写在不存在的键上
 * （教学备注落在没有 note 字段的模板里）等「瞎编」问题，且全都**静默**不报错。
 *
 * 本测试守住四条底线：
 * 1. 演示字段必须真实存在于模板 fieldsConfig（唯一白名单：专注的 duration 是 UI 虚拟字段）；
 * 2. SELECT / MULTI_SELECT 的值必须在选项内，SLIDER 值必须在 min..max 内；
 * 3. 演示账单分类、物品分类必须在 app 内置分类表内（否则落到未知分类的兜底色）；
 * 4. 演示数据引用的每张图都必须真实存在于 assets/demo/。
 */
@RunWith(RobolectricTestRunner::class)
// 用裸 Application：不能启动 PalmNoteApp（它会初始化加密数据库/Keystore，纯 JVM 测试环境没有）
@Config(sdk = [30], application = Application::class)
class DemoDataIntegrityTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 模板 icon → 字段表（lifeTemplateSeeds 是 LifeDataSeeder 的实例成员，用宽松 mock 取静态数据表）。 */
    private val templateFields: Map<String, Map<String, FieldConfig>> =
        LifeDataSeeder(
            io.mockk.mockk(relaxed = true),
            io.mockk.mockk(relaxed = true),
            null
        ).lifeTemplateSeeds.associate { tpl ->
            tpl.icon to json.decodeFromString<List<FieldConfig>>(tpl.fieldsConfig).associateBy { it.key }
        }

    @Test
    fun `life demo fields must exist in their template`() {
        val problems = mutableListOf<String>()
        LifeDemoData.items.forEach { item ->
            val fields = templateFields[item.templateIcon] ?: return@forEach
            val obj = runCatching { json.decodeFromString<JsonObject>(item.fieldsData) }.getOrNull()
                ?: return@forEach
            obj.keys.forEach { key ->
                // 专注的 duration 由 LifeDetailViewModel 按毫秒读写，模板里没有这个字段
                val virtual = item.templateIcon == "timer" && key == "duration"
                if (!virtual && key !in fields) {
                    problems += "${item.templateIcon}/「${item.title}」字段 $key 不在模板中"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `life demo option and range values must be valid`() {
        val problems = LifeDemoData.items.flatMap { item ->
            val fields = templateFields[item.templateIcon] ?: return@flatMap emptyList()
            val obj = runCatching { json.decodeFromString<JsonObject>(item.fieldsData) }.getOrNull()
                ?: return@flatMap emptyList()
            obj.mapNotNull { (key, element) ->
                invalidValue("${item.templateIcon}/「${item.title}」$key", element, fields[key])
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** 单个字段值是否越界；合法返回 null。按字段类型分派到窄函数（值不一定是 JSON 原始类型）。 */
    private fun invalidValue(where: String, element: JsonElement, cfg: FieldConfig?): String? {
        cfg ?: return null
        return when (cfg.type) {
            FieldType.SELECT -> badSelect(where, element, cfg)
            FieldType.MULTI_SELECT -> badMultiSelect(where, element, cfg)
            FieldType.SLIDER -> badSlider(where, element, cfg)
            else -> null
        }
    }

    private fun badSelect(where: String, element: JsonElement, cfg: FieldConfig): String? {
        val v = (element as? JsonPrimitive)?.content ?: return null
        if (cfg.options.isEmpty() || v in cfg.options) return null
        return "$where=$v 不在选项 ${cfg.options}"
    }

    private fun badMultiSelect(where: String, element: JsonElement, cfg: FieldConfig): String? {
        val bad = multiSelectTexts(element).firstOrNull { it !in cfg.options } ?: return null
        return "$where 含越界项 $bad"
    }

    private fun badSlider(where: String, element: JsonElement, cfg: FieldConfig): String? {
        val v = (element as? JsonPrimitive)?.content?.toDoubleOrNull()
        val lo = cfg.min
        val hi = cfg.max
        val outOfRange = v != null && lo != null && hi != null && (v < lo || v > hi)
        return if (outOfRange) "$where=$v 超出 [$lo,$hi]" else null
    }

    /** MULTI_SELECT 的两种存储形态（纯字符串数组 / CompoundPayload items）都抽成文本列表。 */
    private fun multiSelectTexts(element: JsonElement): List<String> {
        (element as? JsonArray)?.let { return it.map { node -> (node as? JsonPrimitive)?.content.orEmpty() } }
        val items = runCatching { json.decodeFromString<JsonObject>(element.toString())["items"] }.getOrNull()
        val itemArr = items as? JsonArray ?: return emptyList()
        return itemArr.mapNotNull { node ->
            runCatching { json.decodeFromString<JsonObject>(node.toString())["text"]?.jsonPrimitive?.content }
                .getOrNull()
        }
    }

    @Test
    fun `demo dates must be relative placeholders, not hardcoded ISO`() {
        // 写死的"未来日期"过一阵子就变成过去，会与执行列（按 daysAgo/dueInDays 相对今天算）
        // 互相打架——演示数据里出现过「截止日写 10-06、条目却排在今天」这种自相矛盾。
        // 形如 "deadline":"2026-10-06"（用字符类而非转义，保持可读）
        val hardcoded = Regex("\"[A-Za-z_]+\":\"[0-9]{4}-[0-9]{2}-[0-9]{2}\"")
        val offenders = LifeDemoData.items
            .filter { hardcoded.containsMatchIn(it.fieldsData) }
            .map { it.title }
        assertTrue("这些演示条目的日期字段写死了 ISO（请改成 +Nd / -Nd）：$offenders", offenders.isEmpty())
    }

    @Test
    fun `wealth demo categories must use built-in keys`() {
        // 演示账单存的是分类的**内部键**（与 BillCategoryData 的 name 一致的中文常量），
        // 不是本地化后的显示名 —— 存显示名会让英文环境下按键查图标/颜色全部落空。
        val expenseKeys = expenseCategoryItems.map { it.name }
        val incomeKeys = incomeCategoryItems.map { it.name }

        demoBills.forEach { bill ->
            val allowed = if (bill.type == "INCOME") incomeKeys else expenseKeys
            assertTrue(
                "演示账单「${bill.merchant}」分类键「${bill.categoryKey}」不在内置分类内",
                bill.categoryKey in allowed
            )
        }

        val assetCategories = assetCategoryItems.map { it.name }
        demoAssets.forEach { asset ->
            assertTrue(
                "演示物品「${asset.name}」分类 ${asset.category} 不在预设分类内",
                asset.category in assetCategories
            )
        }
    }

    @Test
    fun `every demo image reference exists in assets`() {
        val assetsDir = File("src/main/assets/demo")
        assertTrue("assets/demo 目录缺失（测试工作目录：${File(".").absolutePath}）", assetsDir.isDirectory)
        val available = assetsDir.listFiles()?.map { it.name }?.toSet().orEmpty()

        val referenced = buildList {
            LifeDemoData.items.forEach { addAll(it.images.values) }
            demoBills.mapNotNullTo(this) { it.receipt }
            demoAssets.mapNotNullTo(this) { it.photo }
        }
        referenced.forEach { name ->
            assertTrue("演示数据引用了不存在的图：$name", name in available)
        }
        assertEquals("演示数据里有重复引用的图片（去重后数量不符）", referenced.size, referenced.distinct().size)
    }

    @Test
    fun `anchor records keep created date aligned with their start date`() {
        // 正数日 / 倒计时这类「锚点型记录」：字段里的起始日（-Nd 占位符）与 daysAgo 必须对齐，
        // 即 createdAt 不早于起始日（daysAgo ≤ 起始日距今天数）。示例数据是固定的精品集，
        // 这条不变量靠本测试钉住——改动示例条目（尤其是起始日或 daysAgo）时会立刻报红。
        val placeholder = Regex("""-(\d+)d""")
        val offenders = LifeDemoData.items.mapNotNull { item ->
            val days = placeholder.find(item.fieldsData)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            if (item.daysAgo > days) {
                "${item.templateIcon}/「${item.title}」daysAgo=${item.daysAgo} 晚于起始日 ${days}d（创建早于起始日）"
            } else {
                null
            }
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `demo prefs keys stay in sync with seeders`() {
        // 三个播种器的版本号都必须 > 0（0 表示从未播种，会让 ensureSeeded 判定失效）
        assertTrue(com.palmnote.data.LifeDemoSeeder.SEED_VERSION > 0)
        assertTrue(com.palmnote.data.WealthDemoSeeder.SEED_VERSION > 0)
        assertTrue(com.palmnote.data.HabitDemoSeeder.SEED_VERSION > 0)
        // 引用一下 PreferencesManager 的演示偏好，保证改名时编译期就断
        val p = PreferencesManager::class.java
        assertTrue(p.methods.any { it.name == "setLifeCategoryCompact" })
    }
}
