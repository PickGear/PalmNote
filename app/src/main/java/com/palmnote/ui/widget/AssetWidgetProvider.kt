package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.model.AssetStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class AssetWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun assetDao(): com.palmnote.data.db.dao.AssetDao
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val heldCount = entryPoint.assetDao().getAssetCountByStatus(AssetStatus.HELD).first()
        // 估值口径：先取当前估值，无人填过估值则回退购买价（与 App 内口径一致）
        val totalValue = entryPoint.assetDao().getTotalCurrentValue().first()
            ?: entryPoint.assetDao().getHeldAssetValue().first()
            ?: 0L

        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                appWidgetId,
                bindViews(context, appWidgetId, heldCount, totalValue)
            )
        }
    }

    internal fun bindViews(context: Context, appWidgetId: Int, heldCount: Int, totalValue: Long): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_asset_unified)

        if (heldCount > 0) {
            views.setTextViewText(R.id.widget_asset_count, "$heldCount")
            views.setTextViewText(R.id.widget_asset_value, WidgetData.formatMoneyCompact(context, totalValue))
            views.setViewVisibility(R.id.widget_first_asset, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_state, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_first_asset, View.GONE)
            views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
        }

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_ASSET + appWidgetId, WidgetDeepLink.TAB_ASSET)
        )
        return views
    }
}
