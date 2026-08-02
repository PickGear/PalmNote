package com.palmnote.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.room.Room
import com.palmnote.MainActivity
import com.palmnote.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import com.palmnote.data.db.EncryptedOpenHelperFactory
import com.palmnote.data.db.migration.MIGRATION_1_2
import com.palmnote.data.db.migration.MIGRATION_2_3
import com.palmnote.data.db.migration.MIGRATION_3_4
import com.palmnote.data.db.migration.MIGRATION_4_5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        runBlocking(Dispatchers.IO) {
            var db: AppDatabase? = null
            try {
                val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
                val appContext = context.applicationContext
                db = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    AppDatabase.DATABASE_NAME
                ).openHelperFactory(
                    EncryptedOpenHelperFactory(appContext, DbKeyStore(appContext))
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                val expense = db.billDao().getMonthlyExpense(yearMonth).first()
                val income = db.billDao().getMonthlyIncome(yearMonth).first()
                val fmt = java.text.NumberFormat.getCurrencyInstance(Locale.getDefault())

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.widget_expense, fmt.format((expense ?: 0L) / 100.0))
                    views.setTextViewText(R.id.widget_income, fmt.format((income ?: 0L) / 100.0))
                    val intent = Intent(context, MainActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(
                        context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("BillWidgetProvider", "Widget update failed", e)
            } finally {
                db?.close()
                pendingResult.finish()
            }
        }
    }
}
