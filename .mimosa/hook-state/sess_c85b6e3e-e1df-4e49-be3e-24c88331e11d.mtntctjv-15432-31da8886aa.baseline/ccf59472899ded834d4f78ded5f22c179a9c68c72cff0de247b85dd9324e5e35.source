package com.palmnote.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
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

    fun show(context: Context, channelId: String, title: String, message: String) {
        val id = notificationId.incrementAndGet()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(message)
            .setPriority(if (channelId == CHANNEL_CHECKIN || channelId == CHANNEL_REMINDER) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(id, notification) } catch (_: SecurityException) { /* Notification permission not granted */ }
    }
}
