package com.palmnote.ui.settings
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import com.palmnote.domain.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


@HiltViewModel
class DataClearViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) : ViewModel() {

    fun clearAssets() { viewModelScope.launch { try { db.assetDao().deleteAll(); clearImagesDir() } catch (e: Exception) { AppLogger.w("DataClear", "clearAssets failed", e) } } }

    fun clearBills() { viewModelScope.launch { try { db.billDao().deleteAll(); db.budgetDao().deleteAll(); db.walletDao().deleteAll(); db.recurringTemplateDao().deleteAll(); clearImagesDir() } catch (e: Exception) { AppLogger.w("DataClear", "clearBills failed", e) } } }

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
                clearImagesDir()
            } catch (e: Exception) { AppLogger.w("DataClear", "clearLife failed", e) }
        }
    }

    fun clearAll() { viewModelScope.launch { try { db.clearAllTables(); clearImagesDir() } catch (e: Exception) { AppLogger.w("DataClear", "clearAll failed", e) } } }

    /** 清理文件存储中的图片孤儿文件（DB 已清，文件不再引用） */
    private suspend fun clearImagesDir() = withContext(Dispatchers.IO) {
        File(context.filesDir, "images").listFiles()?.forEach { it.delete() }
    }
}
