package com.palmnote.ui.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.util.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 桌面打卡入口：点小组件里的行直接切换当天打卡，不用打开应用。
 *
 * **数据源与生活页详情完全同源**（LifeItem + LifeTemplate，kind = HABIT）：
 * 「打卡」不是某个字段，而是「今天这条模板有没有一条非 ARCHIVED 的行」。
 * 此前这里写的是已废弃的 Goal/GoalCheckIn 表——UI 层根本没有创建 Goal 的入口，
 * 于是组件只能显示演示数据、且打了卡生活页看不到。
 */
class HabitCheckInReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CheckInEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK_IN) return
        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
        if (templateId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    CheckInEntryPoint::class.java
                )
                val template = entryPoint.lifeTemplateDao().getTemplateById(templateId)
                toggle(
                    dao = entryPoint.lifeItemDao(),
                    templateId = templateId,
                    title = template?.getDisplayName(context).orEmpty(),
                    includeDemo = entryPoint.preferencesManager().lifeDemoMode.first(),
                    demoMeta = LIFE_DEMO_META
                )
                HabitWidgetProvider.requestUpdateAll(context)
                // Dashboard 的「今日打卡」卡也读同一份数据，不刷新就要等下一轮轮询
                WidgetUpdateHelper.refreshDashboardWidgets()
            } catch (e: Exception) {
                AppLogger.e("HabitCheckInReceiver", "Widget check-in failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_IN = "com.palmnote.widget.action.HABIT_CHECK_IN"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"

        /** 打卡开关（见 [com.palmnote.domain.util.HabitCheckIn]）：接收器薄壳与单测共用同一实现。 */
        suspend fun toggle(
            dao: LifeItemDao,
            templateId: Long,
            title: String,
            includeDemo: Boolean,
            demoMeta: String,
            now: Long = System.currentTimeMillis(),
            today: LocalDate = LocalDate.now()
        ): Boolean = com.palmnote.domain.util.HabitCheckIn.toggle(
            dao = dao,
            templateId = templateId,
            title = title,
            includeDemo = includeDemo,
            demoMeta = demoMeta,
            now = now,
            today = today
        )

        fun checkInPendingIntent(context: Context, templateId: Long): PendingIntent {
            val intent = Intent(context, HabitCheckInReceiver::class.java)
                .setAction(ACTION_CHECK_IN)
                .putExtra(EXTRA_TEMPLATE_ID, templateId)
            return PendingIntent.getBroadcast(
                context,
                (WidgetDeepLink.SEG_HABIT_TOGGLE + templateId).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
