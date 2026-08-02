package com.palmnote.ui.settings
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import coil3.imageLoader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.export.CsvDataExporter
import com.palmnote.data.lock.AppLockManager
import com.palmnote.domain.repository.*
import com.palmnote.data.sync.CalendarSyncManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.palmnote.data.worker.LifeDailyCheckWorker
import android.util.Log
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


@Stable
data class SettingsState(
    val themeMode: String = "SYSTEM",
    val defaultBillType: String = "EXPENSE",
    val budgetReminderEnabled: Boolean = true,
    val calendarSyncEnabled: Boolean = false,
    val switchColor: String = "#2D4A3E",
    val defaultStartPage: String = "dashboard",
    val language: String = "SYSTEM",
    val assetCount: Int = 0,
    val goalCount: Int = 0,
    val momentCount: Int = 0,
    val anniversaryCount: Int = 0,

    val birthdayReminderAdvanceDays: Int = 3,
    val anniversaryReminderAdvanceDays: Int = 3,
    val dailyReminderEnabled: Boolean = true,
    val billReminderEnabled: Boolean = true,
    val dailyReminderHour: Int = 9,
    val dailyReminderMinute: Int = 0,
    val billReminderHour: Int = 21,
    val billReminderMinute: Int = 0,
    val biometricEnabled: Boolean = false,
    val resultMessage: String? = null,
    val profileNickname: String = "",
    val profileSignature: String = "",
    val profileAvatar: String = "Spa",
    val profileAvatarPath: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val csvDataExporter: CsvDataExporter,
    private val calendarSyncManager: CalendarSyncManager,
    private val assetRepository: AssetRepository,
    private val goalRepository: GoalRepository,
    private val momentRepository: MomentRepository,
    private val anniversaryRepository: AnniversaryRepository,
    val appLockManager: AppLockManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // Merge all three combines into one to prevent concurrent overwrites
        viewModelScope.launch {
            combine(
                preferencesManager.themeMode,
                preferencesManager.defaultBillType,
                preferencesManager.budgetReminderEnabled,
                preferencesManager.calendarSyncEnabled,
                preferencesManager.switchColor,
                preferencesManager.defaultStartPage,
                preferencesManager.language,
                preferencesManager.birthdayReminderAdvanceDays,
                preferencesManager.anniversaryReminderAdvanceDays,
                preferencesManager.dailyReminderEnabled,
                preferencesManager.billReminderEnabled,
                preferencesManager.dailyReminderHour,
                preferencesManager.dailyReminderMinute,
                preferencesManager.billReminderHour,
                preferencesManager.billReminderMinute,
                preferencesManager.biometricEnabled,
                preferencesManager.profileNickname,
                preferencesManager.profileSignature,
                preferencesManager.profileAvatar,
                preferencesManager.profileAvatarPath,
                assetRepository.getTotalAssetCount(),
                goalRepository.getGoalCount(),
                momentRepository.getMomentCount(),
                anniversaryRepository.getAnniversaryCount()
            ) { args ->
                val i = { idx: Int -> args.getOrNull(idx) }
                _state.update { current ->
                    current.copy(
                        themeMode = (i(0) as? String) ?: "SYSTEM",
                        defaultBillType = (i(1) as? String) ?: "EXPENSE",
                        budgetReminderEnabled = (i(2) as? Boolean) ?: true,
                        calendarSyncEnabled = (i(3) as? Boolean) ?: false,
                        switchColor = (i(4) as? String) ?: "#2D4A3E",
                        defaultStartPage = (i(5) as? String) ?: "dashboard",
                        language = (i(6) as? String) ?: "SYSTEM",
                        birthdayReminderAdvanceDays = (i(7) as? Int) ?: 3,
                        anniversaryReminderAdvanceDays = (i(8) as? Int) ?: 3,
                        dailyReminderEnabled = (i(9) as? Boolean) ?: true,
                        billReminderEnabled = (i(10) as? Boolean) ?: true,
                        dailyReminderHour = (i(11) as? Int) ?: 9,
                        dailyReminderMinute = (i(12) as? Int) ?: 0,
                        billReminderHour = (i(13) as? Int) ?: 21,
                        billReminderMinute = (i(14) as? Int) ?: 0,
                        biometricEnabled = (i(15) as? Boolean) ?: false,
                        profileNickname = (i(16) as? String) ?: "",
                        profileSignature = (i(17) as? String) ?: "",
                        profileAvatar = (i(18) as? String) ?: "Spa",
                        profileAvatarPath = (i(19) as? String) ?: "",
                        assetCount = (i(20) as? Int) ?: 0,
                        goalCount = (i(21) as? Int) ?: 0,
                        momentCount = (i(22) as? Int) ?: 0,
                        anniversaryCount = (i(23) as? Int) ?: 0
                    )
                }
            }.catch { Log.w("SettingsVM", "Settings flow failed", it) }.collect()
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setDefaultBillType(type: String) {
        viewModelScope.launch { preferencesManager.setDefaultBillType(type) }
    }

    fun setBudgetReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBudgetReminderEnabled(enabled) }
    }

    fun setCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCalendarSyncEnabled(enabled)
            if (enabled) {
                calendarSyncManager.syncAnniversaries().onSuccess { count ->
                    _state.value = _state.value.copy(resultMessage = context.getString(R.string.settings_sync_success, count))
                }.onFailure { e ->
                    _state.value = _state.value.copy(resultMessage = context.getString(R.string.settings_sync_failed, e.message ?: ""))
                }
            } else {
                calendarSyncManager.clearSync()
                _state.value = _state.value.copy(resultMessage = context.getString(R.string.settings_sync_cleared))
            }
        }
    }

    fun setSwitchColor(color: String) {
        viewModelScope.launch { preferencesManager.setSwitchColor(color) }
    }

    fun setDefaultStartPage(route: String) {
        viewModelScope.launch { preferencesManager.setDefaultStartPage(route) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            preferencesManager.setLanguage(lang)
            LanguageHelper.applyLanguage(lang)
        }
    }

    fun setBirthdayReminderAdvanceDays(days: Int) {
        viewModelScope.launch { preferencesManager.setBirthdayReminderAdvanceDays(days) }
    }

    fun setAnniversaryReminderAdvanceDays(days: Int) {
        viewModelScope.launch { preferencesManager.setAnniversaryReminderAdvanceDays(days) }
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDailyReminderEnabled(enabled)
            val hour = preferencesManager.dailyReminderHour.first()
            val minute = preferencesManager.dailyReminderMinute.first()
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            val delay = target.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<LifeDailyCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("life_daily_check", ExistingPeriodicWorkPolicy.REPLACE, request)
        }
    }

    fun setBillReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBillReminderEnabled(enabled) }
    }

    fun setDailyReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesManager.setDailyReminderHour(hour)
            preferencesManager.setDailyReminderMinute(minute)
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            val delay = target.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<LifeDailyCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("life_daily_check", ExistingPeriodicWorkPolicy.REPLACE, request)
        }
    }

    fun setBillReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesManager.setBillReminderHour(hour)
            preferencesManager.setBillReminderMinute(minute)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBiometricEnabled(enabled) }
    }

    fun setProfileNickname(name: String) { viewModelScope.launch { preferencesManager.setProfileNickname(name) } }
    fun setProfileSignature(sig: String) { viewModelScope.launch { preferencesManager.setProfileSignature(sig) } }
    fun setProfileAvatar(avatar: String) { viewModelScope.launch { preferencesManager.setProfileAvatar(avatar) } }
    fun setProfileAvatarPath(path: String) { viewModelScope.launch { preferencesManager.setProfileAvatarPath(path) } }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            csvDataExporter.exportToUri(uri).onSuccess { msg ->
                _state.value = _state.value.copy(resultMessage = msg)
            }.onFailure { e ->
                _state.value = _state.value.copy(resultMessage = context.getString(R.string.settings_export_failed, e.message ?: ""))
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            csvDataExporter.importFromUri(uri).onSuccess { count ->
                val msg = if (count > 0) context.getString(R.string.settings_import_success, count) else context.getString(R.string.settings_import_no_data)
                _state.value = _state.value.copy(resultMessage = msg)
            }.onFailure { e ->
                _state.value = _state.value.copy(resultMessage = context.getString(R.string.settings_import_failed, e.message ?: ""))
            }
        }
    }

    fun clearCache(@ApplicationContext context: Context) {
        viewModelScope.launch {
            try {
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("csvimport") || file.name.startsWith("coil"))) {
                        file.delete()
                    }
                }
                _state.value = _state.value.copy(resultMessage = context.getString(R.string.settings_cache_cleared))
            } catch (e: Exception) {
                _state.value = _state.value.copy(resultMessage = context.getString(R.string.settings_cache_clear_failed, e.message ?: ""))
            }
        }
    }

    fun clearResult() {
        _state.value = _state.value.copy(resultMessage = null)
    }
}
