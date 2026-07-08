package com.palmnote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataClearViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {

    fun clearAssets() { viewModelScope.launch { try { db.assetDao().deleteAll() } catch (_: Exception) {} } }

    fun clearBills() { viewModelScope.launch { try { db.billDao().deleteAll(); db.budgetDao().deleteAll(); db.walletDao().deleteAll(); db.recurringTemplateDao().deleteAll() } catch (_: Exception) {} } }

    fun clearLife() { viewModelScope.launch { try { db.goalDao().deleteAll(); db.goalCheckInDao().deleteAll(); db.anniversaryDao().deleteAll(); db.momentDao().deleteAll() } catch (_: Exception) {} } }

    fun clearAll() { viewModelScope.launch { try { db.clearAllTables() } catch (_: Exception) {} } }
}
