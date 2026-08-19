package com.palmnote.ui.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.palmnote.domain.util.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 桌面待办快捷完成：点击待办组件条目直接切换 ACTIVE↔COMPLETED，无需打开应用
class TodoToggleReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ToggleEntryPoint {
        fun lifeItemDao(): com.palmnote.data.db.dao.LifeItemDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext, ToggleEntryPoint::class.java
                ).lifeItemDao()
                val item = dao.getItemById(itemId)
                if (item != null && item.parentId == null) {
                    val next = if (item.status == "COMPLETED") "ACTIVE" else "COMPLETED"
                    dao.updateStatus(itemId, next)
                }
                WidgetUpdateHelper.refreshTodoWidgets()
                WidgetUpdateHelper.refreshDashboardWidgets()
            } catch (e: Exception) {
                AppLogger.e("TodoToggleReceiver", "Widget todo toggle failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.palmnote.widget.action.TODO_TOGGLE"
        const val EXTRA_ITEM_ID = "extra_item_id"

        fun togglePendingIntent(context: Context, itemId: Long): PendingIntent {
            val intent = Intent(context, TodoToggleReceiver::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_ITEM_ID, itemId)
            return PendingIntent.getBroadcast(
                context,
                (21_000_000 + itemId).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
