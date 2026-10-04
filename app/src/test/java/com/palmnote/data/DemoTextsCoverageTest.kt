package com.palmnote.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 演示数据本地化的**完整性守卫**。
 *
 * 演示内容是固定精品集（中文文案写在 `LifeDemoData` / `DemoWealthData` / `HabitDemoSeeder` 里），
 * 英文界面下由 [DemoTexts] 映射成英文。本测试扫描三个源文件里的**全部中文串**，
 * 断言每一条都在映射表里——新增演示内容忘了翻译会直接报红（英文截图里冒出中文就是这么来的）。
 */
class DemoTextsCoverageTest {

    private val repoRoot: File by lazy { locateRepoRoot() }

    private val sources = listOf(
        "app/src/main/java/com/palmnote/ui/life/LifeDemoData.kt",
        "app/src/main/java/com/palmnote/data/DemoWealthData.kt",
        "app/src/main/java/com/palmnote/data/HabitDemoSeeder.kt"
    )

    /** 从测试工作目录向上查找含 settings.gradle.kts 的仓库根（同 ChangelogAssetTest 的定位法）。 */
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

    /** 提取源文件里带中文的字符串字面量；剔除注释/文档片段这类非数据串。 */
    private fun chineseStringsIn(path: String): Set<String> {
        val text = File(repoRoot, path).readText()
        val regex = Regex("\"([^\"]*[\\u4e00-\\u9fff][^\"]*)\"")
        return regex.findAll(text)
            .map { it.groupValues[1].replace("\\n", "\n") }   // 源码里的字面 \n 要还原成真换行再比对
            .filterNot { it.contains('…') || it.contains('{') || it.contains('}') || it.contains("LifeDemoItem") }
            .filterNot { it.startsWith(':') || it.startsWith('/') }
            .filter { it.length < 80 }
            .toSet()
    }

    @Test
    fun `every chinese demo string has an english mapping`() {
        val missing = mutableListOf<String>()
        sources.forEach { path ->
            chineseStringsIn(path).forEach { zh ->
                if (zh !in DemoTexts.mappedKeys) missing.add("$path → 「$zh」")
            }
        }
        assertTrue(
            "以下演示文案缺少英文映射（英文界面会显示中文）：\n" + missing.sorted().joinToString("\n"),
            missing.isEmpty()
        )
    }
}
