package com.palmnote.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.palmnote.R
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    const val CHANNEL_LIFE = "life_general"
    const val CHANNEL_CHECKIN = "life_checkin"
    const val CHANNEL_REMINDER = "life_reminder"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(NotificationChannel(CHANNEL_LIFE, context.getString(R.string.notification_channel_life), NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.notification_channel_life_desc) })
        mgr.createNotificationChannel(NotificationChannel(CHANNEL_CHECKIN, context.getString(R.string.notification_channel_checkin), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.notification_channel_checkin_desc) })
        mgr.createNotificationChannel(NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.notification_channel_reminder), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.notification_channel_reminder_desc) })
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
     */
    fun show(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        contentIntent: PendingIntent? = null,
        actions: List<Action> = emptyList()
    ) {
        val id = notificationId.incrementAndGet()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(message)
            .setPriority(if (channelId == CHANNEL_CHECKIN || channelId == CHANNEL_REMINDER) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        (contentIntent ?: appEntryIntent?.invoke(context))?.let { builder.setContentIntent(it) }
        // icon 传 0 = 纯文字按钮（项目没有成套的通知小图标，不硬凑）
        actions.forEach { builder.addAction(0, it.label, it.intent) }
        try { NotificationManagerCompat.from(context).notify(id, builder.build()) } catch (_: SecurityException) { /* Notification permission not granted */ }
    }
}
