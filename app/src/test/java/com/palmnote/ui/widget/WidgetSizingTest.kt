package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 尺寸相关的两件事：
 * - [sizedRemoteViews] 一次给出多个尺寸的 RemoteViews（新版用桌面给的精确尺寸，旧版横竖两版）；
 * - [todoPreviewCount] 尺寸档 → 预览条数。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetSizingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 记录 bind 被问到的尺寸；返回一份最省的布局，只为验证尺寸列表。 */
    private fun recorder(sizes: MutableList<Pair<Int, Int>>) = { width: Int, height: Int ->
        sizes += width to height
        RemoteViews(context.packageName, R.layout.widget_todo_unified)
    }

    @Test
    fun `旧版没有尺寸信息时用声明尺寸兜底`() {
        val asked = mutableListOf<Pair<Int, Int>>()
        sizedRemoteViews(Bundle(), 250, 180, recorder(asked))

        // 横屏在前、竖屏在后；两边都退到声明的 250×180
        assertEquals(listOf(250 to 180, 250 to 180), asked)
    }

    @Test
    fun `旧版按 min 与 max 推出横竖两版`() {
        val asked = mutableListOf<Pair<Int, Int>>()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 400)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250)
        }
        sizedRemoteViews(options, 250, 180, recorder(asked))

        // 横屏=最宽×最矮，竖屏=最窄×最高
        assertEquals(listOf(400 to 110, 110 to 250), asked)
    }

    @Test
    fun `旧版拿到 0 尺寸时按声明尺寸兜底`() {
        val asked = mutableListOf<Pair<Int, Int>>()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        }
        sizedRemoteViews(options, 110, 40, recorder(asked))

        assertEquals(listOf(110 to 40, 110 to 40), asked)
    }

    @Test
    @Config(sdk = [33])
    fun `十二及以上按桌面给的精确尺寸各构建一份并去重`() {
        val asked = mutableListOf<Pair<Int, Int>>()
        val options = Bundle().apply {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(110f, 40f), SizeF(180f, 110f), SizeF(180f, 110f))
            )
        }
        sizedRemoteViews(options, 250, 180, recorder(asked))

        assertEquals(listOf(110 to 40, 180 to 110), asked)
    }

    @Test
    @Config(sdk = [33])
    fun `十二及以上尺寸列表为空时仍退回横竖两版`() {
        val asked = mutableListOf<Pair<Int, Int>>()
        sizedRemoteViews(Bundle(), 250, 180, recorder(asked))

        assertEquals(listOf(250 to 180, 250 to 180), asked)
    }
}
