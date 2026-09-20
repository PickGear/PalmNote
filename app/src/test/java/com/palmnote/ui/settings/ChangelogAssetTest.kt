package com.palmnote.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 版本历史资产漂移守卫（纯 JVM 测试，不依赖 Android / Robolectric）。
 *
 * 应用内「版本历史」页展示的 app/src/main/assets/changelog.txt 由仓库根 CHANGELOG.md 转换而来。
 * 本测试确保：
 *  - changelog.txt 存在且非空；
 *  - gradle/libs.versions.toml 的 [versions] 当前版本号（palmnote）出现在 changelog.txt 中
 *    （发版时忘了更新版本历史 -> 测试变红）；
 *  - CHANGELOG.md 同样包含该版本号（版本历史资产与 CHANGELOG 对得上）。
 */
class ChangelogAssetTest {

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
        throw AssertionError("无法定位仓库根（向上 $MAX_UP_LEVELS 层未见 settings.gradle.kts），起始目录=$start")
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

    /** 当前发布版本号（libs.versions.toml 的 palmnote）；缺失则测试失败。 */
    private fun currentVersion(): String {
        val version = parseTomlVersions(readRepoFile("gradle/libs.versions.toml"))["palmnote"]
        if (version.isNullOrBlank()) {
            fail("gradle/libs.versions.toml 的 [versions] 缺少键「palmnote」或值为空")
            return ""
        }
        return version
    }

    @Test
    fun `changelogAssetExistsAndIsNotEmpty`() {
        val asset = File(repoRoot, CHANGELOG_ASSET_PATH)
        assertTrue("缺少版本历史资产 $CHANGELOG_ASSET_PATH", asset.isFile)
        assertTrue("版本历史资产 $CHANGELOG_ASSET_PATH 为空", asset.readText().isNotBlank())
    }

    @Test
    fun `changelogAssetCoversCurrentVersion`() {
        val version = currentVersion()
        val changelog = readRepoFile(CHANGELOG_ASSET_PATH)
        assertTrue(
            "当前版本 $version 未出现在 $CHANGELOG_ASSET_PATH（发版时忘记更新版本历史？）",
            changelog.contains(version)
        )
    }

    @Test
    fun `repoChangelogCoversCurrentVersion`() {
        val version = currentVersion()
        val changelogMd = readRepoFile("CHANGELOG.md")
        assertTrue(
            "CHANGELOG.md 未包含当前版本 $version（版本历史资产与 CHANGELOG 不一致）",
            changelogMd.contains(version)
        )
    }

    private companion object {
        /** 向上查找仓库根的最大层数。 */
        const val MAX_UP_LEVELS = 6

        /** 随安装包分发的版本历史资产（.txt 后缀必需：aapt 不打包无扩展名 assets）。 */
        const val CHANGELOG_ASSET_PATH = "app/src/main/assets/changelog.txt"
    }
}
