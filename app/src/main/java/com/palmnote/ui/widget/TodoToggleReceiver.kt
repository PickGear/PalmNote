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
                    context.applicationContext,
                    ToggleEntryPoint::class.java
                ).lifeItemDao()
                toggle(dao, itemId)
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

        /**
         * 勾选核心动作（接收器薄壳与单测共用）：ACTIVE↔COMPLETED 双向切换。
         * 子项（parentId != null）与不存在的 id 一律不动；返回是否发生了切换。
         */
        suspend fun toggle(dao: com.palmnote.data.db.dao.LifeItemDao, itemId: Long): Boolean {
            val item = dao.getItemById(itemId) ?: return false
            if (item.parentId != null) return false
            val next = if (item.status == "COMPLETED") "ACTIVE" else "COMPLETED"
            dao.updateStatus(itemId, next)
            return true
        }

        /** 逐条目 PendingIntent（通知动作等直接持有一份的场景用）。 */
        fun togglePendingIntent(context: Context, itemId: Long): PendingIntent {
            val intent = Intent(context, TodoToggleReceiver::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_ITEM_ID, itemId)
            return PendingIntent.getBroadcast(
                context,
                (WidgetDeepLink.SEG_TODO_TOGGLE + itemId).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * 集合模板：只带 action 与组件，**条目 id 一律由行内的 fill-in intent 提供**。
         * 模板里绝不能预置 [EXTRA_ITEM_ID]：fill-in 只补模板里「空着」的字段
         * （Intent#fillIn 对 extras 是 `newb.putAll(mExtras)`，模板的值覆盖 fill-in 的值），
         * 模板自带一个 0 会把行的真实 id 顶掉，点哪一行都变成空动作。
         */
        fun toggleTemplatePendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                WidgetDeepLink.SEG_TODO_TOGGLE,
                Intent(context, TodoToggleReceiver::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
