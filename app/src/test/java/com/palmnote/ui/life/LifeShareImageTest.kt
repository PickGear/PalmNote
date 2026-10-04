package com.palmnote.ui.life

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.palmnote.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 分享图的落点与 FileProvider 契约。
 *
 * ## 为什么要有这组测试
 *
 * 月度回顾的分享**曾经一直是坏的**：它把 PNG 写到 `getExternalFilesDir(null)/reviews`，
 * 而 `file_paths.xml` 只配置了内部 `filesDir/images`。于是 `FileProvider.getUriForFile`
 * 必然抛 `IllegalArgumentException`，异常又被 `runCatching` 吞掉 —— 界面只显示
 * 「生成回顾图失败」，**原因完全不可见**，只能靠人读代码发现。
 *
 * 这里把那条不变量变成可执行的断言：**分享目录必须落在某个已配置的根之内**，
 * 且**代码里的 authority 必须与清单里声明的一致**。以后谁改了目录或路径配置，
 * 构建就会失败，而不是等到用户点分享才发现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeShareImageTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `文件名带前缀与时间戳且是 png`() {
        assertEquals("palmnote-review-1700000000000.png", lifeShareFileName("review", 1_700_000_000_000))
        assertEquals("palmnote-item-1.png", lifeShareFileName("item", 1))
    }

    @Test
    fun `不同时间戳不会互相覆盖`() {
        assertTrue(lifeShareFileName("item", 1) != lifeShareFileName("item", 2))
    }

    @Test
    fun `分享目录落在 file_paths 已配置的根之内`() {
        // 断言的是**生产代码的落点**（shareDir），不是测试自己拼的路径 ——
        // 否则无论生产代码写到哪，这条断言都成立（同义反复，起不到守门作用）。
        val shareDir = shareDir(context).canonicalPath
        val roots = configuredShareRoots(context)
        assertTrue(
            "分享目录 $shareDir 不在任何已配置的根里（FileProvider 会抛异常）：$roots",
            roots.any { shareDir == it || shareDir.startsWith("$it${File.separator}") }
        )
    }

    @Test
    fun `代码用的 authority 与清单声明一致`() {
        val expected = "${context.packageName}.fileprovider"
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PROVIDERS
        )
        val authorities = info.providers?.mapNotNull { it.authority }.orEmpty()
        assertTrue("清单里没有 $expected，实际为 $authorities", expected in authorities)
    }

    /** 解析 `res/xml/file_paths.xml`，把每个根标签映射成真实目录（canonical）。 */
    private fun configuredShareRoots(context: Context): List<String> {
        val parser = context.resources.getXml(R.xml.file_paths)
        val roots = mutableListOf<String>()
        var event = parser.next()
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                val base = when (parser.name) {
                    "files-path" -> context.filesDir
                    "cache-path" -> context.cacheDir
                    "external-files-path" -> context.getExternalFilesDir(null)
                    "external-cache-path" -> context.externalCacheDir
                    else -> null
                }
                val sub = parser.getAttributeValue(null, "path").orEmpty()
                if (base != null) roots += File(base, sub).canonicalPath
            }
            event = parser.next()
        }
        parser.close()
        return roots
    }
}
