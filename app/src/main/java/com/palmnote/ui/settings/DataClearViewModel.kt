package com.palmnote.ui.settings
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import com.palmnote.domain.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 清除数据的结果反馈。
 * 此前四个清除操作全部静默（fire-and-forget，失败只写日志）——
 * 「点了没反应」和「清了没提示」都会让用户怀疑操作是否生效。
 */
data class DataClearUiState(
    val clearing: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DataClearViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(DataClearUiState())
    val state: StateFlow<DataClearUiState> = _state.asStateFlow()

    fun consumeResult() {
        if (!_state.value.clearing) _state.value = DataClearUiState()
    }

    fun clearAssets() = launchClear { db.assetDao().deleteAll(); clearImagesDir() }

    fun clearBills() = launchClear {
        db.billDao().deleteAll(); db.budgetDao().deleteAll(); db.walletDao().deleteAll()
        db.recurringTemplateDao().deleteAll(); clearImagesDir()
    }

    fun clearLife() = launchClear {
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
        resetLifeDemoSeed()
    }

    fun clearAll() = launchClear {
        db.clearAllTables(); clearImagesDir(); clearVaultAvatarsDir()
        resetLifeDemoSeed()
    }

    /**
     * 清掉演示播种标记。
     *
     * 标记在 DataStore、示例行在 Room——清表不会带走标记。不归零的话，演示模式下
     * [com.palmnote.data.LifeDemoSeeder.ensureSeeded] 会认为示例已就绪而拒绝重建，
     * 生活页从此永久空白。这是「清完数据生活页变空壳」的根因。
     */
    private suspend fun resetLifeDemoSeed() {
        preferences.setLifeDemoSeeded(false)
        preferences.setLifeDemoSeedVersion(0)
    }

    private fun launchClear(block: suspend () -> Unit) {
        if (_state.value.clearing) return
        _state.value = DataClearUiState(clearing = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                _state.value = DataClearUiState(success = true)
            } catch (e: Exception) {
                AppLogger.w("DataClear", "clear failed", e)
                _state.value = DataClearUiState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** 清理文件存储中的图片孤儿文件（DB 已清，文件不再引用） */
    private suspend fun clearImagesDir() = withContext(Dispatchers.IO) {
        File(context.filesDir, "images").listFiles()?.forEach { it.delete() }
    }

    /** 清理密码本头像目录（仅「全部清空」时调用） */
    private suspend fun clearVaultAvatarsDir() = withContext(Dispatchers.IO) {
        File(context.filesDir, "vault_avatars").listFiles()?.forEach { it.delete() }
    }
}
