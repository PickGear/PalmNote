package com.palmnote.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 用 AlarmManager 可靠重启进程（避免 startActivity + exit(0) 竞态）。 */
object AppRestarter {

    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context, 1001, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 300
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent)
        } else {
            // 无精确闹钟权限时用 setAndAllowWhileIdle 兜底（可能被 Doze 延迟，但可手动重启）
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent)
        }
        Runtime.getRuntime().exit(0)
    }
}
