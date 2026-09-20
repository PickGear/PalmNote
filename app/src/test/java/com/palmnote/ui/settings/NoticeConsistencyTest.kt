package com.palmnote.ui.settings

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 依赖清单漂移守卫（纯 JVM 测试，不依赖 Android / Robolectric）。
 *
 * 开源许可信息存在三处真值，本测试确保三者一致：
 *  - gradle/libs.versions.toml 的 [versions] 段
 *  - 仓库根目录的 NOTICE（随安装包分发，权威来源）
 *  - 应用内 LicenseScreen.THIRD_PARTY_LIBRARIES
 *
 * 任何依赖升级若忘记同步 NOTICE，本测试即红，避免许可声明与真实依赖漂移。
 */
class NoticeConsistencyTest {

    private val repoRoot: File by lazy { locateRepoRoot() }

    /** 从测试工作目录向上最多 [MAX_UP_LEVELS] 层，查找含 settings.gradle.kts 的仓库根。 */
    private fun locateRepoRoot(): File {
        val start = File("").absoluteFile
        var dir: File? = start
        var upLevels = 0
        while (dir != null && upLevels <= MAX_UP_LEVELS) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
            upLevels++
        }
        fail("无法定位仓库根（向上 $MAX_UP_LEVELS 层未见 settings.gradle.kts），起始目录=$start")
        // fail(...) 正常路径下必然抛出；此行仅为满足函数返回类型。
        return start
    }

    private fun readRepoFile(relativePath: String): String =
        File(repoRoot, relativePath).readText()

    /** 解析 toml 的 [versions] 段为 key→value。只做逐行 `key = "value"`，不引入 TOML 库。 */
    private fun parseTomlVersions(tomlText: String): Map<String, String> {
        val versions = linkedMapOf<String, String>()
        var inVersionsSection = false
        tomlText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("[") -> inVersionsSection = line == "[versions]"
                inVersionsSection && line.isNotEmpty() && !line.startsWith("#") -> {
                    val eq = line.indexOf('=')
                    if (eq > 0) {
                        val key = line.substring(0, eq).trim()
                        val value = line.substring(eq + 1).trim().trim('"')
                        versions[key] = value
                    }
                }
            }
        }
        return versions
    }

    @Test
    fun `noticeListsEveryShippedDependencyVersion`() {
        val versions = parseTomlVersions(readRepoFile("gradle/libs.versions.toml"))
        val notice = readRepoFile("NOTICE")
        val problems = mutableListOf<String>()
        SHIPPED_VERSION_KEYS.forEach { key ->
            val value = versions[key]
            if (value == null) {
                problems += "toml [versions] 缺少键「$key」"
            } else if (!notice.contains(value)) {
                problems += "「$key」版本 $value 未出现在 NOTICE 中"
            }
        }
        assertTrue(
            "以下随包分发的依赖版本未写入 NOTICE：\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    @Test
    fun `noticeAssetMatchesRepoNotice`() {
        val repoNotice = File(repoRoot, "NOTICE").readBytes()
        val assetNotice = File(repoRoot, "app/src/main/assets/NOTICE.txt").readBytes()
        assertArrayEquals(
            "assets/NOTICE.txt 必须与仓库根 NOTICE 字节一致，否则安装包内声明与源码不符",
            repoNotice,
            assetNotice
        )
    }

    @Test
    fun `licenseScreenListMatchesNotice`() {
        val notice = readRepoFile("NOTICE")
        val versionToken = Regex("\\d+\\.\\d+(?:\\.\\d+)*")
        val problems = mutableListOf<String>()
        THIRD_PARTY_LIBRARIES.forEach { line ->
            val tokens = versionToken.findAll(line).map { it.value }.toList()
            if (tokens.isEmpty()) {
                problems += "「$line」未解析出版本号"
            }
            tokens.filterNot { notice.contains(it) }.forEach { token ->
                problems += "「$line」中的版本 $token 未出现在 NOTICE 中"
            }
        }
        assertTrue(
            "LicenseScreen 清单与 NOTICE 存在漂移：\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    private companion object {
        /** 向上查找仓库根的最大层数。 */
        const val MAX_UP_LEVELS = 6

        /** 随包分发、且在 [versions] 中有对应键的依赖。agp / ksp / detekt 为构建期工具，排除。 */
        val SHIPPED_VERSION_KEYS = listOf(
            "kotlin", "room", "navigation", "compose-bom", "coil", "datastore",
            "sqlcipher", "work", "core-ktx", "activity-compose", "lifecycle",
            "coroutines", "onnxruntime", "opencv", "lunar", "kotlinx-serialization-json",
            "junit", "androidx-junit", "espresso", "mockk", "turbine", "robolectric",
            "hilt", "hilt-navigation-compose"
        )
    }
}
