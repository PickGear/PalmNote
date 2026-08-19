package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.util.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VaultWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun vaultDao(): com.palmnote.feature.vault.VaultDao
    }

    private var scope: CoroutineScope? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope?.cancel()
        scope = null
    }

    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val coroutineScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

        coroutineScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val vaultDao = entryPoint.vaultDao()

                val entries = vaultDao.getAllEntries().first().take(1)
                val firstEntry = entries.firstOrNull()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_vault)

                    val totalCount = vaultDao.countEntriesFlow().first() ?: 0
                    views.setTextViewText(R.id.widget_vault_count, "$totalCount")

                    if (firstEntry != null) {
                        views.setTextViewText(R.id.widget_entry_title, firstEntry.title)
                        views.setTextViewText(R.id.widget_entry_category, firstEntry.category)
                        views.setViewVisibility(R.id.widget_first_entry, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_empty_state, View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_first_entry, View.GONE)
                        views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
                    }

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, appWidgetId, "vault")
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                AppLogger.e("VaultWidgetProvider", "Widget update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
