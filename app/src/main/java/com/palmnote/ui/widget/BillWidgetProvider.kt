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
import com.palmnote.data.db.migration.MIGRATION_1_2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val yearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val db = getDatabase(context)
                val expense = db.billDao().getMonthlyExpense(yearMonth).first()
                val income = db.billDao().getMonthlyIncome(yearMonth).first()
                val fmt = NumberFormat.getCurrencyInstance(Locale.CHINA)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.widget_expense, fmt.format(expense ?: 0.0))
                    views.setTextViewText(R.id.widget_income, fmt.format(income ?: 0.0))

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
                pendingResult.finish()
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppDatabase.DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
