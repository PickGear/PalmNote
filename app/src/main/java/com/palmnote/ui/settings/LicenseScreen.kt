package com.palmnote.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.palmnote.app.R

/** 开源许可页：项目版权/要点 + GPL-3.0 全文 + 第三方依赖清单。 */
private const val SECTION_PROJECT_ZH = "本项目"
private const val SECTION_PROJECT_EN = "This App"
private const val SECTION_GPL_ZH = "开源许可证全文（GPL-3.0）"
private const val SECTION_GPL_EN = "Full License Text (GPL-3.0)"
private const val SECTION_DEPS_ZH = "第三方依赖"
private const val SECTION_DEPS_EN = "Third-Party Dependencies"
private const val SECTION_NOTICE_ZH = "第三方声明原文（NOTICE）"
private const val SECTION_NOTICE_EN = "Third-Party Notices (Full Text)"

/** 无编号的 GPL 小节标题（去掉首尾空白后精确匹配或前缀匹配）。 */
private val LICENSE_NUMBERED_HEADING = Regex("^\\d+\\.\\s+[A-Z]")

/**
 * 第三方依赖：库名 · 版本 · 许可证。版本取自 gradle/libs.versions.toml 与 app/build.gradle.kts，许可依各库官方声明。
 *
 * NOTICE 文件为权威来源，改依赖时先改 NOTICE，再同步本清单，NoticeConsistencyTest 会校验两者一致。
 */
internal val THIRD_PARTY_LIBRARIES = listOf(
    "AndroidX Core KTX · 1.15.0 · Apache License 2.0",
    "AndroidX AppCompat · 1.8.0 · Apache License 2.0",
    "AndroidX Activity Compose · 1.9.3 · Apache License 2.0",
    "AndroidX Lifecycle · 2.8.7 · Apache License 2.0",
    "AndroidX Navigation Compose · 2.8.5 · Apache License 2.0",
    "AndroidX Room · 2.7.2 · Apache License 2.0",
    "AndroidX DataStore · 1.1.1 · Apache License 2.0",
    "AndroidX WorkManager · 2.10.0 · Apache License 2.0",
    "AndroidX Paging Runtime KTX · 3.3.4 · Apache License 2.0",
    "AndroidX Paging Compose · 3.3.4 · Apache License 2.0",
    "AndroidX DocumentFile · 1.1.0 · Apache License 2.0",
    "AndroidX Biometric · 1.1.0 · Apache License 2.0",
    "AndroidX Material3 Window Size Class · Compose BOM 2025.06.01 · Apache License 2.0",
    "Jetpack Compose (BOM) · 2025.06.01 · Apache License 2.0",
    "Dagger Hilt · 2.58 · Apache License 2.0",
    "AndroidX Hilt Navigation Compose · 1.2.0 · Apache License 2.0",
    "Kotlinx Coroutines · 1.10.2 · Apache License 2.0",
    "Kotlinx Serialization JSON · 1.8.1 · Apache License 2.0",
    "Coil · 3.3.0 · Apache License 2.0",
    "SQLCipher (Zetetic) · 4.17.0 · BSD 3-Clause",
    "ONNX Runtime (Microsoft) · 1.29.0 · MIT",
    "OpenCV · 4.14.0 · Apache License 2.0",
    "Lunar (6tail) · 1.7.7 · MIT"
)

/**
 * 开源许可屏。正文三块：项目版权与要点、GPL-3.0 全文（来自 assets/LICENSE.txt）、第三方依赖清单。
 */
@Composable
fun LicenseScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val isZh = context.resources.configuration.locales[0].language == "zh"
    val gplLines = remember { loadLicenseText(context) }
    val noticeText = remember { loadNoticeText(context) }
    val loadFailed = stringResource(R.string.license_load_failed)
    val lines = remember(isZh, gplLines, noticeText, loadFailed) {
        buildList {
            addAll(getLicenseIntroLines(isZh))
            addAll(gplLines ?: listOf(loadFailed))
            addAll(getThirdPartyDependencyLines(isZh))
            addAll(getNoticeSectionLines(isZh, noticeText != null))
        }
    }
    DocumentScreen(
        title = stringResource(R.string.about_open_source_license),
        lines = lines,
        isHeading = ::isLicenseHeading,
        onNavigateBack = onNavigateBack,
        verbatimText = noticeText
    )
}

/** 项目版权 / 要点 / 源码仓库 + GPL 小节标题。 */
internal fun getLicenseIntroLines(isZh: Boolean): List<String> {
    return if (isZh) {
        listOf(
            SECTION_PROJECT_ZH,
            "本程序（PalmNote 掌记）为自由软件，依据 GNU 通用公共许可证第 3 版（GPL-3.0）发布，无任何担保。",
            "版权所有 (C) 2026 PickGear",
            "源代码仓库：https://github.com/PickGear/PalmNote",
            SECTION_GPL_ZH
        )
    } else {
        listOf(
            SECTION_PROJECT_EN,
            "This program (PalmNote) is free software, released under the GNU " +
                "General Public License v3.0 (GPL-3.0), with ABSOLUTELY NO WARRANTY.",
            "Copyright (C) 2026 PickGear",
            "Source repository: https://github.com/PickGear/PalmNote",
            SECTION_GPL_EN
        )
    }
}

/** 第三方依赖清单。 */
internal fun getThirdPartyDependencyLines(isZh: Boolean): List<String> {
    return if (isZh) {
        listOf(
            SECTION_DEPS_ZH,
            "本项目集成的第三方开源库均在本机运行，不收集、不上传任何数据。以下版本取自项目依赖清单："
        ) + THIRD_PARTY_LIBRARIES + "以上许可证信息以各项目官方发布为准。"
    } else {
        listOf(
            SECTION_DEPS_EN,
            "The third-party libraries below run locally and never collect or upload any data. " +
                "Versions are taken from the project dependency manifest:"
        ) + THIRD_PARTY_LIBRARIES +
            "License information above is subject to each project's official distribution."
    }
}

/** NOTICE 小节：标题 + 一句说明；文件缺失时给出失败提示。 */
internal fun getNoticeSectionLines(isZh: Boolean, hasNotice: Boolean): List<String> {
    val title = if (isZh) SECTION_NOTICE_ZH else SECTION_NOTICE_EN
    val intro = if (isZh) {
        "以下为随安装包一同分发的第三方声明原文（NOTICE），与源码仓库根目录的 NOTICE 文件内容一致。"
    } else {
        "The following is the third-party notice distributed with this build (NOTICE); " +
            "it is identical to the NOTICE file in the source repository."
    }
    return buildList {
        add(title)
        add(intro)
        if (!hasNotice) add(if (isZh) "声明文件读取失败。" else "Failed to read the notice file.")
    }
}

/**
 * 判断某行是否为许可屏的小节标题。谓词在 unwrap（按空行分段）之后匹配。
 * GPL 小节标题带 2 空格缩进（如 "  0. Definitions."），故先 trim 再匹配。
 */
internal fun isLicenseHeading(line: String): Boolean {
    val text = line.trim()
    return when {
        text.isEmpty() -> false
        text.startsWith("GNU GENERAL PUBLIC LICENSE") -> true
        text == "Preamble" -> true
        text == "TERMS AND CONDITIONS" -> true
        text == "END OF TERMS AND CONDITIONS" -> true
        text.startsWith("How to Apply These Terms") -> true
        text == SECTION_PROJECT_ZH || text == SECTION_PROJECT_EN -> true
        text == SECTION_GPL_ZH || text == SECTION_GPL_EN -> true
        text == SECTION_DEPS_ZH || text == SECTION_DEPS_EN -> true
        text == SECTION_NOTICE_ZH || text == SECTION_NOTICE_EN -> true
        LICENSE_NUMBERED_HEADING.containsMatchIn(text) -> true
        else -> false
    }
}

/**
 * 从 assets/LICENSE.txt 读取 GPL-3.0 全文并复原为段落列表；读取失败返回 null。
 * 用 .txt 后缀是必需的：aapt 不会打包没有扩展名的 assets 文件。
 */
internal fun loadLicenseText(context: Context): List<String>? {
    return try {
        val raw = context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() }
        unwrapLicenseText(raw)
    } catch (_: Exception) {
        null
    }
}

/**
 * 从 assets/NOTICE.txt 读取第三方声明原文（原样返回，不做 unwrap）；读取失败返回 null。
 * 用 .txt 后缀是必需的：aapt 不会打包没有扩展名的 assets 文件。
 */
internal fun loadNoticeText(context: Context): String? {
    return try {
        context.assets.open("NOTICE.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}

/**
 * GPL 原文是硬折行（约 70 列）。按空行分段，段内以单空格 join 复原为整句，
 * 并保留段首缩进（标题的 2 空格缩进 / 首行居中缩进）。
 */
internal fun unwrapLicenseText(raw: String): List<String> {
    return raw.replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\n[ \t]*\n"))
        .mapNotNull { paragraph ->
            val lines = paragraph.split('\n')
            val firstNonBlank = lines.firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
            val indent = firstNonBlank.takeWhile { it == ' ' }
            val text = lines.joinToString(separator = " ") { it.trim() }.trim()
            if (text.isEmpty()) null else indent + text
        }
}
