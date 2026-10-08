package com.palmnote.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.palmnote.R
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    /**
     * 通知渠道**按功能一条**，用户在系统通知设置里能逐个关掉。
     *
     * 以前只有三条泛渠道（生活动态 / 打卡提醒 / 事件提醒），保质期、倒计时、账单、生日、订阅
     * 全挤在「事件提醒」里——想单独静音保质期做不到，那页列出来的名字也看不出对应什么功能。
     *
     * ⚠️ **渠道是 `enum` 而不是裸 `String`**，这是有意的：`show()` 原来收 `channelId: String`，
     * 调用方随手拼一个（曾有人传 `"trigger_" + item.id`）就能编译通过，而 Android 8+ 对
     * **不存在的渠道**是**静默丢弃**——通知根本不弹、也没有崩溃，只在 logcat 留一行。
     * 换成枚举后这类 id 在编译期就过不去。
     *
     * @param id 渠道 id。**已发布的 id 不能改**（改了等于新渠道，用户既有设置全丢）。
     * @param nameRes/descRes 名称与说明：这两个**可以**在后续版本改，系统会照新的显示；
     *   但 `importance` 只在创建那一刻生效，之后改代码对已装用户无效。
     */
    enum class Channel(
        val id: String,
        // 显式写 @get: 目标：Kotlin 2.2 起裸注解只作用于构造参数，不写会出警告
        @get:StringRes val nameRes: Int,
        @get:StringRes val descRes: Int,
        val highImportance: Boolean
    ) {
        /** 每日生活记录提醒（"今天还没记过"）。 */
        CHECKIN("life_checkin", R.string.notification_channel_checkin, R.string.notification_channel_checkin_desc, true),

        /** 记账提醒。 */
        BILL("life_bill", R.string.notification_channel_bill, R.string.notification_channel_bill_desc, true),

        /** 倒计时到期。 */
        COUNTDOWN("life_countdown", R.string.notification_channel_countdown, R.string.notification_channel_countdown_desc, true),

        /** 物品保质期 / 质保到期。 */
        ASSET_EXPIRY("life_asset", R.string.notification_channel_asset, R.string.notification_channel_asset_desc, true),

        /** 生日与纪念日。 */
        EVENTS("life_events", R.string.notification_channel_events, R.string.notification_channel_events_desc, true),

        /** 订阅扣费。 */
        SUBSCRIPTION(
            "life_subscription", R.string.notification_channel_subscription,
            R.string.notification_channel_subscription_desc, true
        ),

        /** 里程碑、存钱达标这类成就型通知：不紧急，不要响铃打扰。id 沿用旧的 `life_general`（功能没变，别让用户的静音选择白丢）。 */
        MILESTONE("life_general", R.string.notification_channel_milestone, R.string.notification_channel_milestone_desc, false)
    }

    /**
     * 拆渠道之前的历史渠道，只用于删除。
     *
     * 只删「事件提醒」这一条：它原来同时承担保质期/倒计时/账单/生日四件事，现在各归各家。
     * 另外两条（`life_general` 里程碑、`life_checkin` 每日提醒）**功能没变、id 就不能动**——
     * 换个 id 等于开一条新渠道，用户之前设的静音会悄无声息地失效。
     */
    private val OBSOLETE_CHANNEL_IDS = listOf("life_reminder")

    fun createChannels(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Channel.entries.forEach { channel ->
            val importance = if (channel.highImportance) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
            mgr.createNotificationChannel(
                NotificationChannel(channel.id, context.getString(channel.nameRes), importance).apply {
                    description = context.getString(channel.descRes)
                }
            )
        }
        // 留着它只会让系统那页多出一个谁也看不懂、点了也没反应的开关。
        OBSOLETE_CHANNEL_IDS.forEach(mgr::deleteNotificationChannel)
    }

    private val notificationId = AtomicInteger(1000)

    /**
     * **点通知正文要打开的入口**，由 app 层注入（core 不认识 `MainActivity`）。
     *
     * 此前 `show()` 既没有 `setContentIntent` 也没有动作按钮 —— **提醒点下去什么都不发生**，
     * 只能眼看着它消失。注入默认入口后，所有既有调用点**一行都不用改**就有了"点开应用"。
     * 需要深链到具体记录的调用方，自己传 `contentIntent`（优先于这里）。
     */
    var appEntryIntent: ((Context) -> PendingIntent?)? = null

    /** 通知上的一个动作按钮（如「标记完成」）。 */
    data class Action(val label: String, val intent: PendingIntent)

    /**
     * 弹一条通知。
     *
     * @param contentIntent 点正文的入口；null 时回退到 [appEntryIntent]（通常就是"打开应用"）
     * @param actions 动作按钮（在通知上直接操作，不必先打开应用）
     * @param id 固定 id。传了就**就地更新同一条通知**而不是再堆一条——重复提醒（每天算一次、
     *   内容随天数变）必须传，否则连着几天会在通知栏里堆成一摞。不传则自增（一次性通知适用）。
     */
    fun show(
        context: Context,
        channel: Channel,
        title: String,
        message: String,
        contentIntent: PendingIntent? = null,
        actions: List<Action> = emptyList(),
        id: Int? = null
    ) {
        val notifyId = id ?: notificationId.incrementAndGet()
        // 不再 setPriority：minSdk 26，importance 只认渠道上设的那一档，Builder 上的优先级被忽略。
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(message)
            .setAutoCancel(true)
        (contentIntent ?: appEntryIntent?.invoke(context))?.let { builder.setContentIntent(it) }
        // icon 传 0 = 纯文字按钮（项目没有成套的通知小图标，不硬凑）
        actions.forEach { builder.addAction(0, it.label, it.intent) }
        try {
            NotificationManagerCompat.from(context).notify(notifyId, builder.build())
        } catch (_: SecurityException) {
            // Notification permission not granted
        }
    }
}
