package com.palmnote.ui.notification

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 通知渠道表的**不变量**测试。
 *
 * 拆渠道这件事有两处「改错了也不报错、只是静默失效」的坑，所以钉住它们：
 * ① 渠道 id 是**已发布接口**——改一个 id 等于开一条新渠道，用户之前设的静音悄无声息地作废；
 * ② 名称/说明来自字符串资源，**漏了某个语言就是空标题**（系统那页显示一条没名字的开关）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NotificationChannelTableTest {

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `渠道 id 是对外接口，改名会让用户的静音设置失效`() {
        assertEquals(
            setOf(
                "life_checkin",
                "life_bill",
                "life_countdown",
                "life_asset",
                "life_events",
                "life_subscription",
                "life_general"
            ),
            NotificationHelper.Channel.entries.map { it.id }.toSet()
        )
    }

    @Test
    fun `每个渠道都真的被创建出来`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        NotificationHelper.createChannels(context)

        val ids = manager(context).notificationChannels.map { it.id }.toSet()
        NotificationHelper.Channel.entries.forEach { channel ->
            assertTrue("渠道 ${channel.id} 没被创建", channel.id in ids)
        }
    }

    @Test
    fun `拆掉的那个泛渠道被删干净，不留看不懂的死开关`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        NotificationHelper.createChannels(context)

        val ids = manager(context).notificationChannels.map { it.id }.toSet()
        assertFalse("life_reminder 应该被删掉", "life_reminder" in ids)
    }

    @Test
    fun `渠道名与说明都不为空`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        NotificationHelper.Channel.entries.forEach { channel ->
            assertTrue("${channel.id} 没名字", context.getString(channel.nameRes).isNotBlank())
            assertTrue("${channel.id} 没说明", context.getString(channel.descRes).isNotBlank())
        }
    }

    @Test
    fun `渠道 id 不重复`() {
        val ids = NotificationHelper.Channel.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
