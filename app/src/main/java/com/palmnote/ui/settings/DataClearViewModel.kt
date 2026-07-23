package com.palmnote.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.AppDatabase
import kotlinx.coroutines.launch


class DataClearViewModel(
    private val db: AppDatabase
) : ViewModel() {

    fun clearAssets() { viewModelScope.launch { try { db.assetDao().deleteAll() } catch (e: Exception) { Log.w("DataClear", "clearAssets failed", e) } } }

    fun clearBills() { viewModelScope.launch { try { db.billDao().deleteAll(); db.budgetDao().deleteAll(); db.walletDao().deleteAll(); db.recurringTemplateDao().deleteAll() } catch (e: Exception) { Log.w("DataClear", "clearBills failed", e) } } }

    fun clearLife() {
        viewModelScope.launch {
            try {
                // Legacy tables
                db.goalDao().deleteAll()
                db.goalCheckInDao().deleteAll()
                db.anniversaryDao().deleteAll()
                db.momentDao().deleteAll()
                // Life module tables
                db.lifeItemDao().deleteAll()
                db.lifeTemplateDao().deleteAll()
                db.crossLinkDao().deleteAll()
                db.achievementDao().deleteAll()
                db.focusRecordDao().deleteAll()
                db.todoItemDao().deleteAll()
                db.moodDiaryDao().deleteAll()
                db.lifeReportDao().deleteAll()
                db.lifeMomentDao().deleteAll()
            } catch (e: Exception) { Log.w("DataClear", "clearLife failed", e) }
        }
    }

    fun clearAll() { viewModelScope.launch { try { db.clearAllTables() } catch (e: Exception) { Log.w("DataClear", "clearAll failed", e) } } }
}
