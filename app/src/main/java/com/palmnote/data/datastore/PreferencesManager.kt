package com.palmnote.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.palmnote.domain.model.AutoLockMode
import com.palmnote.ui.dashboard.DashboardCardConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "palmnote_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @com.palmnote.di.ApplicationScope private val scope: CoroutineScope
) {
    /** DataStore 内存缓存：Eagerly 启动，进程活着就常驻 */
    private val prefsState: StateFlow<Preferences> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyPreferences())

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
        val VAULT_KDF_ITERATIONS = intPreferencesKey("vault_kdf_iterations")
        val VAULT_REQUIRE_AUTH = booleanPreferencesKey("vault_require_auth")
        val VAULT_CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("vault_clipboard_clear_seconds")
        val VAULT_BIO_ENABLED = booleanPreferencesKey("vault_bio_enabled")
        val VAULT_BIO_KEY_WRAP = stringPreferencesKey("vault_bio_key_wrap")
        val VAULT_NO_LOCK = booleanPreferencesKey("vault_no_lock")
        val VAULT_NO_LOCK_BANNER_DISMISSED = booleanPreferencesKey("vault_no_lock_banner_dismissed")
        val AUTO_LOCK_MODE = stringPreferencesKey("auto_lock_mode")
        val AUTO_LOCK_TIMEOUT_MINUTES = intPreferencesKey("auto_lock_timeout_minutes")
        val VAULT_CARD_IDENTITY = stringPreferencesKey("vault_card_identity")

        const val AUTO_LOCK_MODE_IMMEDIATE = "immediate"
        const val AUTO_LOCK_MODE_SYSTEM = "system"
        const val AUTO_LOCK_MODE_TIMEOUT = "timeout"
        const val DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES = 5
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
        if (json == null) {
            DashboardCardConfig.defaults
        } else {
            val stored = DashboardCardConfig.fromJson(json)
            // 保留用户自定义的存储顺序（拖拽排序依赖该顺序持久化），
            // 仅把新版本新增的卡片（如 VAULT）追加到末尾，避免用户丢失新卡片
            val storedTypes = stored.map { it.type }.toSet()
            stored + DashboardCardConfig.defaults.filter { it.type !in storedTypes }
        }
    }

    suspend fun saveDashboardCardConfigs(configs: List<DashboardCardConfig>) {
        context.dataStore.edit { it[DASHBOARD_CARD_CONFIGS] = DashboardCardConfig.toJson(configs) }
    }

    fun isAppLockEnabled(): Boolean = prefsState.value[APP_LOCK_ENABLED] ?: false

    val appLockEnabledFlow: Flow<Boolean> = prefsFlow.map { it[APP_LOCK_ENABLED] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    fun setAppLockEnabledSync(enabled: Boolean) { scope.launch { setAppLockEnabled(enabled) } }

    fun getEncryptedPin(): String = prefsState.value[ENCRYPTED_PIN] ?: ""

    /** 加密 PIN 的 DataStore flow（供冷启动等待首次加载后做锁定决策）。 */
    val encryptedPinFlow: Flow<String> = prefsFlow.map { it[ENCRYPTED_PIN] ?: "" }

    fun getLanguage(): String = prefsState.value[LANGUAGE] ?: "SYSTEM"

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

    fun getVaultSalt(): String = prefsState.value[VAULT_SALT] ?: ""

    /** vault_salt 的 DataStore flow（用于冷启动后重校验密码本初始化状态）。 */
    val vaultSalt: Flow<String> = prefsFlow.map { it[VAULT_SALT] ?: "" }

    fun getVaultKeyWrap(): String = prefsState.value[VAULT_KEY_WRAP] ?: ""

    /** 创建 PIN 包裹所用的 PBKDF2 迭代次数；0 = 历史包裹（无参数记录，需回退探测）。 */
    fun getVaultKdfIterations(): Int = prefsState.value[VAULT_KDF_ITERATIONS] ?: 0

    /** 原子写入 salt + 包裹 + 派生参数（单次 DataStore edit）。 */
    suspend fun setVaultCredentials(salt: String, keyWrap: String, kdfIterations: Int) {
        context.dataStore.edit {
            it[VAULT_SALT] = salt
            it[VAULT_KEY_WRAP] = keyWrap
            if (kdfIterations > 0) it[VAULT_KDF_ITERATIONS] = kdfIterations
        }
    }

    suspend fun clearVaultCredentials() {
        context.dataStore.edit {
            it.remove(VAULT_SALT)
            it.remove(VAULT_KEY_WRAP)
            it.remove(VAULT_KDF_ITERATIONS)
        }
    }

    val vaultRequireAuth: Flow<Boolean> = prefsFlow.map { it[VAULT_REQUIRE_AUTH] ?: true }

    suspend fun setVaultRequireAuth(enabled: Boolean) {
        context.dataStore.edit { it[VAULT_REQUIRE_AUTH] = enabled }
    }

    val vaultBiometricEnabled: Flow<Boolean> = prefsFlow.map { it[VAULT_BIO_ENABLED] ?: false }

    fun getVaultBioKeyWrap(): String = prefsState.value[VAULT_BIO_KEY_WRAP] ?: ""

    /** 原子写入生物识别包裹 + 启用标志（单次 DataStore edit，避免双数据源不一致窗口）。 */
    suspend fun setVaultBioCredentials(wrap: String) {
        context.dataStore.edit {
            it[VAULT_BIO_KEY_WRAP] = wrap
            it[VAULT_BIO_ENABLED] = true
        }
    }

    suspend fun setVaultBioKeyWrap(wrap: String) {
        context.dataStore.edit { it[VAULT_BIO_KEY_WRAP] = wrap }
    }

    suspend fun clearVaultBio() {
        context.dataStore.edit {
            it.remove(VAULT_BIO_ENABLED)
            it.remove(VAULT_BIO_KEY_WRAP)
        }
    }

    /** 密码本是否为无锁模式（DK 用无认证 Keystore 密钥包裹，打开即用）。 */
    val vaultNoLock: Flow<Boolean> = prefsFlow.map { it[VAULT_NO_LOCK] ?: false }

    suspend fun setVaultNoLock(enabled: Boolean) {
        context.dataStore.edit { it[VAULT_NO_LOCK] = enabled }
    }

    /** 无锁模式引导横幅是否已被用户关闭（持久化，跨导航保持隐藏）。 */
    val vaultNoLockBannerDismissed: Flow<Boolean> =
        prefsFlow.map { it[VAULT_NO_LOCK_BANNER_DISMISSED] ?: false }

    suspend fun setVaultNoLockBannerDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[VAULT_NO_LOCK_BANNER_DISMISSED] = dismissed }
    }

    suspend fun clearVaultNoLock() {
        context.dataStore.edit { it.remove(VAULT_NO_LOCK) }
    }

    /** 自动锁定模式：immediate=切后台立即锁 / system=跟随系统锁屏（默认） / timeout=锁屏+超时。 */
    val autoLockMode: Flow<String> = prefsFlow.map { it[AUTO_LOCK_MODE] ?: AUTO_LOCK_MODE_SYSTEM }

    suspend fun setAutoLockMode(mode: String) {
        context.dataStore.edit { it[AUTO_LOCK_MODE] = mode }
    }

    /** 自动锁定超时（分钟），timeout 模式下切后台超过该时长才回锁。 */
    val autoLockTimeoutMinutes: Flow<Int> =
        prefsFlow.map { it[AUTO_LOCK_TIMEOUT_MINUTES] ?: DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES }

    suspend fun setAutoLockTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { it[AUTO_LOCK_TIMEOUT_MINUTES] = minutes }
    }

    val vaultClipboardClearSeconds: Flow<Int> = prefsFlow.map { it[VAULT_CLIPBOARD_CLEAR_SECONDS] ?: 30 }

    suspend fun setVaultClipboardClearSeconds(seconds: Int) {
        context.dataStore.edit { it[VAULT_CLIPBOARD_CLEAR_SECONDS] = seconds }
    }

    val vaultCardIdentity: Flow<String> = prefsFlow.map { it[VAULT_CARD_IDENTITY] ?: "email_first" }

    suspend fun setVaultCardIdentity(identity: String) {
        context.dataStore.edit { it[VAULT_CARD_IDENTITY] = identity }
    }
}
