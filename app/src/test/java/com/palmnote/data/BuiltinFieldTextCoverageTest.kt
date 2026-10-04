package com.palmnote.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.palmnote.data.db.entity.BuiltinFieldText
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 内置模板**字段文案**本地化的完整性守卫。
 *
 * 内置模板的字段名 / 单位 / 表头 / 占位提示都是**建库时的中文**，写在 `LifeDataSeeder`
 * 的 `lifeTemplateSeeds` 里，展示层由 `BuiltinFieldText` 翻成英文。本测试扫描该文件里
 * `label` / `unit` / `placeholder` 三类中文值，断言每一条都有英文映射——
 * 将来往种子里加字段却忘了配翻译，会在这里直接报红，而不是等英文截图冒出中文。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "en")
class BuiltinFieldTextCoverageTest {

    private val repoRoot: File by lazy { locateRepoRoot() }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun locateRepoRoot(): File {
        var dir: File? = File("").absoluteFile
        var up = 0
        while (dir != null && up <= 6) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
            up++
        }
        throw AssertionError("无法定位仓库根")
    }

    /** 抽出种子里三类会出现在界面上的中文文案。 */
    private fun seedTexts(): Map<String, Set<String>> {
        val text = File(repoRoot, "app/src/main/java/com/palmnote/data/LifeDataSeeder.kt").readText()
        fun valuesOf(field: String): Set<String> =
            Regex("\"$field\":\"([^\"]*[\\u4e00-\\u9fff][^\"]*)\"")
                .findAll(text)
                .map { it.groupValues[1] }
                .toSet()
        return mapOf(
            "label" to valuesOf("label"),
            "unit" to valuesOf("unit"),
            "placeholder" to valuesOf("placeholder"),
            "表头" to tableColumnHeaders(text)
        )
    }

    /** TABLE 字段的 options 形如 `key:表头:类型`；中间那段是列头，也是要翻译的文案。 */
    private fun tableColumnHeaders(text: String): Set<String> {
        val headers = mutableSetOf<String>()
        Regex("\"options\":\\[([^\\]]*)\\]").findAll(text).forEach { optionList ->
            Regex("\"([^\"]+)\"").findAll(optionList.groupValues[1]).forEach { option ->
                val parts = option.groupValues[1].split(":")
                if (parts.size == 3 && parts[1].any { it in '\u4e00'..'\u9fff' }) headers.add(parts[1])
            }
        }
        return headers
    }

    @Test
    fun `every builtin field text has an english mapping`() {
        val missing = mutableListOf<String>()
        seedTexts().forEach { (field, values) ->
            values.forEach { zh ->
                if (BuiltinFieldText.localize(context, zh) == zh) missing.add("$field → 「$zh」")
            }
        }
        assertTrue(
            "以下内置模板字段文案缺少英文映射（英文界面会显示中文）：\n" + missing.sorted().joinToString("\n"),
            missing.isEmpty()
        )
    }

    @Test
    fun `seed scan actually finds text`() {
        // 防「正则没匹配到 → 空集合 → 上面那条断言永远通过」这类假绿。
        val texts = seedTexts()
        assertTrue("label 一条都没扫到，正则失效了", texts.getValue("label").size > 50)
        assertTrue("表头一条都没扫到，正则失效了", texts.getValue("表头").size >= 5)
    }
}
