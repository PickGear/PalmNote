package com.palmnote.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import com.palmnote.ui.dashboard.DashboardCardConfig
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "palmnote_preferences")

class PreferencesManager(
    private val context: Context
) {
    /** DataStore 读取统一兜底：损坏/IO 异常时回退空偏好，避免崩溃 */
    private val prefsFlow: Flow<Preferences> = context.dataStore.data.catch { emit(emptyPreferences()) }
    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_BILL_TYPE = stringPreferencesKey("default_bill_type")
        val BUDGET_REMINDER_ENABLED = booleanPreferencesKey("budget_reminder_enabled")
        val ASSET_VIEW_MODE = booleanPreferencesKey("asset_view_mode")
        val DASHBOARD_CARD_CONFIGS = stringPreferencesKey("dashboard_card_configs")
        val CALENDAR_SYNC_ENABLED = booleanPreferencesKey("calendar_sync_enabled")
        val SWITCH_COLOR = stringPreferencesKey("switch_color")
        val DEFAULT_START_PAGE = stringPreferencesKey("default_start_page")
        val LANGUAGE = stringPreferencesKey("language")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val ENCRYPTED_PIN = stringPreferencesKey("encrypted_pin")
        val BIRTHDAY_REMINDER_ADVANCE_DAYS = intPreferencesKey("birthday_reminder_advance_days")
        val ANNIVERSARY_REMINDER_ADVANCE_DAYS = intPreferencesKey("anniversary_reminder_advance_days")
        val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val BILL_REMINDER_ENABLED = booleanPreferencesKey("bill_reminder_enabled")
        val DAILY_REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
        val DAILY_REMINDER_MINUTE = intPreferencesKey("daily_reminder_minute")
        val BILL_REMINDER_HOUR = intPreferencesKey("bill_reminder_hour")
        val BILL_REMINDER_MINUTE = intPreferencesKey("bill_reminder_minute")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PRIVACY_AGREED = booleanPreferencesKey("privacy_agreed")
        val LIFE_PLAN_EXPANDED = booleanPreferencesKey("life_plan_expanded")
        val LIFE_TIME_EXPANDED = booleanPreferencesKey("life_time_expanded")
        val LIFE_RECORD_EXPANDED = booleanPreferencesKey("life_record_expanded")
        val PROFILE_NICKNAME = stringPreferencesKey("profile_nickname")
        val PROFILE_SIGNATURE = stringPreferencesKey("profile_signature")
        val PROFILE_AVATAR = stringPreferencesKey("profile_avatar")
        val PROFILE_AVATAR_PATH = stringPreferencesKey("profile_avatar_path")
        val CATEGORY_COLOR_OVERRIDES = stringPreferencesKey("category_color_overrides")
        val PRESET_CATEGORY_OVERRIDES = stringPreferencesKey("preset_category_overrides")
        val VAULT_SALT = stringPreferencesKey("vault_salt")
        val VAULT_KEY_WRAP = stringPreferencesKey("vault_key_wrap")
        val VAULT_REQUIRE_AUTH = booleanPreferencesKey("vault_require_auth")
        val VAULT_CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("vault_clipboard_clear_seconds")
    }

    val themeMode: Flow<String> = prefsFlow.map { it[THEME_MODE] ?: "SYSTEM" }
    val defaultBillType: Flow<String> = prefsFlow.map { it[DEFAULT_BILL_TYPE] ?: "EXPENSE" }
    val budgetReminderEnabled: Flow<Boolean> = prefsFlow.map { it[BUDGET_REMINDER_ENABLED] ?: true }
    val assetViewMode: Flow<Boolean> = prefsFlow.map { it[ASSET_VIEW_MODE] ?: false }

    suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[THEME_MODE] = mode } }
    suspend fun setDefaultBillType(type: String) { context.dataStore.edit { it[DEFAULT_BILL_TYPE] = type } }
    suspend fun setBudgetReminderEnabled(enabled: Boolean) { context.dataStore.edit { it[BUDGET_REMINDER_ENABLED] = enabled } }
    suspend fun setAssetViewMode(isGrid: Boolean) { context.dataStore.edit { it[ASSET_VIEW_MODE] = isGrid } }

    val calendarSyncEnabled: Flow<Boolean> = prefsFlow.map { it[CALENDAR_SYNC_ENABLED] ?: false }

    suspend fun setCalendarSyncEnabled(enabled: Boolean) { context.dataStore.edit { it[CALENDAR_SYNC_ENABLED] = enabled } }

    val switchColor: Flow<String> = prefsFlow.map { it[SWITCH_COLOR] ?: "#2D4A3E" }

    suspend fun setSwitchColor(color: String) { context.dataStore.edit { it[SWITCH_COLOR] = color } }

    val defaultStartPage: Flow<String> = prefsFlow.map { it[DEFAULT_START_PAGE] ?: "dashboard" }

    suspend fun setDefaultStartPage(route: String) { context.dataStore.edit { it[DEFAULT_START_PAGE] = route } }

    val language: Flow<String> = prefsFlow.map { it[LANGUAGE] ?: "SYSTEM" }

    suspend fun setLanguage(lang: String) { context.dataStore.edit { it[LANGUAGE] = lang } }

    val dashboardCardConfigs: Flow<List<DashboardCardConfig>> = prefsFlow.map { prefs ->
        val json = prefs[DASHBOARD_CARD_CONFIGS]
        val stored = if (json != null) DashboardCardConfig.fromJson(json) else DashboardCardConfig.defaults
        // 新版本新增的卡片（如 VAULT）不在旧存储配置中，需要与 defaults 合并，避免用户丢失新卡片
        val storedTypes = stored.map { it.type }.toSet()
        DashboardCardConfig.defaults.mapNotNull { def ->
            stored.find { it.type == def.type } ?: def.takeIf { it.type !in storedTypes }
        }
    }

    suspend fun saveDashboardCardConfigs(configs: List<DashboardCardConfig>) {
        context.dataStore.edit { it[DASHBOARD_CARD_CONFIGS] = DashboardCardConfig.toJson(configs) }
    }

    fun isAppLockEnabled(): Boolean = runBlocking(Dispatchers.IO) { prefsFlow.first()[APP_LOCK_ENABLED] ?: false }

    val appLockEnabledFlow: Flow<Boolean> = prefsFlow.map { it[APP_LOCK_ENABLED] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    fun setAppLockEnabledSync(enabled: Boolean) = runBlocking(Dispatchers.IO) { setAppLockEnabled(enabled) }

    fun getEncryptedPin(): String = runBlocking(Dispatchers.IO) { prefsFlow.first()[ENCRYPTED_PIN] ?: "" }

    suspend fun setEncryptedPin(pin: String) {
        context.dataStore.edit { it[ENCRYPTED_PIN] = pin }
    }

    suspend fun setAppLockCredentials(pin: String, enabled: Boolean) {
        context.dataStore.edit {
            it[ENCRYPTED_PIN] = pin
            it[APP_LOCK_ENABLED] = enabled
        }
    }

    val birthdayReminderAdvanceDays: Flow<Int> = prefsFlow.map { it[BIRTHDAY_REMINDER_ADVANCE_DAYS] ?: 3 }

    suspend fun setBirthdayReminderAdvanceDays(days: Int) { context.dataStore.edit { it[BIRTHDAY_REMINDER_ADVANCE_DAYS] = days } }

    val anniversaryReminderAdvanceDays: Flow<Int> = prefsFlow.map { it[ANNIVERSARY_REMINDER_ADVANCE_DAYS] ?: 3 }

    suspend fun setAnniversaryReminderAdvanceDays(days: Int) { context.dataStore.edit { it[ANNIVERSARY_REMINDER_ADVANCE_DAYS] = days } }

    val dailyReminderEnabled: Flow<Boolean> = prefsFlow.map { it[DAILY_REMINDER_ENABLED] ?: true }

    suspend fun setDailyReminderEnabled(enabled: Boolean) { context.dataStore.edit { it[DAILY_REMINDER_ENABLED] = enabled } }

    val billReminderEnabled: Flow<Boolean> = prefsFlow.map { it[BILL_REMINDER_ENABLED] ?: true }

    suspend fun setBillReminderEnabled(enabled: Boolean) { context.dataStore.edit { it[BILL_REMINDER_ENABLED] = enabled } }

    val dailyReminderHour: Flow<Int> = prefsFlow.map { it[DAILY_REMINDER_HOUR] ?: 9 }

    suspend fun setDailyReminderHour(hour: Int) { context.dataStore.edit { it[DAILY_REMINDER_HOUR] = hour } }

    val dailyReminderMinute: Flow<Int> = prefsFlow.map { it[DAILY_REMINDER_MINUTE] ?: 0 }

    suspend fun setDailyReminderMinute(minute: Int) { context.dataStore.edit { it[DAILY_REMINDER_MINUTE] = minute } }

    val billReminderHour: Flow<Int> = prefsFlow.map { it[BILL_REMINDER_HOUR] ?: 21 }

    suspend fun setBillReminderHour(hour: Int) { context.dataStore.edit { it[BILL_REMINDER_HOUR] = hour } }

    val billReminderMinute: Flow<Int> = prefsFlow.map { it[BILL_REMINDER_MINUTE] ?: 0 }

    suspend fun setBillReminderMinute(minute: Int) { context.dataStore.edit { it[BILL_REMINDER_MINUTE] = minute } }

    val biometricEnabled: Flow<Boolean> = prefsFlow.map { it[BIOMETRIC_ENABLED] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) { context.dataStore.edit { it[BIOMETRIC_ENABLED] = enabled } }

    val privacyAgreed: Flow<Boolean> = prefsFlow.map { it[PRIVACY_AGREED] ?: false }

    suspend fun setPrivacyAgreed(agreed: Boolean) { context.dataStore.edit { it[PRIVACY_AGREED] = agreed } }

    val lifePlanExpanded: Flow<Boolean> = prefsFlow.map { it[LIFE_PLAN_EXPANDED] ?: true }
    val lifeTimeExpanded: Flow<Boolean> = prefsFlow.map { it[LIFE_TIME_EXPANDED] ?: true }
    val lifeRecordExpanded: Flow<Boolean> = prefsFlow.map { it[LIFE_RECORD_EXPANDED] ?: true }

    suspend fun setLifePlanExpanded(v: Boolean) { context.dataStore.edit { it[LIFE_PLAN_EXPANDED] = v } }
    suspend fun setLifeTimeExpanded(v: Boolean) { context.dataStore.edit { it[LIFE_TIME_EXPANDED] = v } }
    suspend fun setLifeRecordExpanded(v: Boolean) { context.dataStore.edit { it[LIFE_RECORD_EXPANDED] = v } }

    val profileNickname: Flow<String> = prefsFlow.map { it[PROFILE_NICKNAME] ?: "" }
    val profileSignature: Flow<String> = prefsFlow.map { it[PROFILE_SIGNATURE] ?: "" }
    val profileAvatar: Flow<String> = prefsFlow.map { it[PROFILE_AVATAR] ?: "Spa" }
    val profileAvatarPath: Flow<String> = prefsFlow.map { it[PROFILE_AVATAR_PATH] ?: "" }

    suspend fun setProfileNickname(name: String) { context.dataStore.edit { it[PROFILE_NICKNAME] = name } }
    suspend fun setProfileSignature(sig: String) { context.dataStore.edit { it[PROFILE_SIGNATURE] = sig } }
    suspend fun setProfileAvatar(avatar: String) { context.dataStore.edit { it[PROFILE_AVATAR] = avatar } }
    suspend fun setProfileAvatarPath(path: String) { context.dataStore.edit { it[PROFILE_AVATAR_PATH] = path } }

    val categoryColorOverrides: Flow<Map<String, String>> = prefsFlow.map { prefs ->
        val json = prefs[CATEGORY_COLOR_OVERRIDES]
        if (json != null) {
            try {
                val obj = org.json.JSONObject(json)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } catch (_: Exception) { emptyMap() }
        } else emptyMap()
    }

    suspend fun saveCategoryColorOverrides(overrides: Map<String, String>) {
        context.dataStore.edit { it[CATEGORY_COLOR_OVERRIDES] = org.json.JSONObject(overrides.toMap()).toString() }
    }

    val presetCategoryOverrides: Flow<Map<String, String>> = prefsFlow.map { prefs ->
        val json = prefs[PRESET_CATEGORY_OVERRIDES]
        if (json != null) {
            try {
                val obj = org.json.JSONObject(json)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } catch (_: Exception) { emptyMap() }
        } else emptyMap()
    }

    suspend fun savePresetCategoryOverrides(overrides: Map<String, String>) {
        context.dataStore.edit { it[PRESET_CATEGORY_OVERRIDES] = org.json.JSONObject(overrides.toMap()).toString() }
    }

    fun getVaultSalt(): String = runBlocking(Dispatchers.IO) { prefsFlow.first()[VAULT_SALT] ?: "" }

    fun getVaultKeyWrap(): String = runBlocking(Dispatchers.IO) { prefsFlow.first()[VAULT_KEY_WRAP] ?: "" }

    suspend fun setVaultCredentials(salt: String, keyWrap: String) {
        context.dataStore.edit {
            it[VAULT_SALT] = salt
            it[VAULT_KEY_WRAP] = keyWrap
        }
    }

    suspend fun clearVaultCredentials() {
        context.dataStore.edit {
            it.remove(VAULT_SALT)
            it.remove(VAULT_KEY_WRAP)
        }
    }

    val vaultRequireAuth: Flow<Boolean> = prefsFlow.map { it[VAULT_REQUIRE_AUTH] ?: true }

    suspend fun setVaultRequireAuth(enabled: Boolean) {
        context.dataStore.edit { it[VAULT_REQUIRE_AUTH] = enabled }
    }

    val vaultClipboardClearSeconds: Flow<Int> = prefsFlow.map { it[VAULT_CLIPBOARD_CLEAR_SECONDS] ?: 30 }

    suspend fun setVaultClipboardClearSeconds(seconds: Int) {
        context.dataStore.edit { it[VAULT_CLIPBOARD_CLEAR_SECONDS] = seconds }
    }
}
