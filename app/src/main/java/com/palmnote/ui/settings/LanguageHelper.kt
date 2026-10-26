package com.palmnote.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.palmnote.data.datastore.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

object LanguageHelper {

    private val SUPPORTED_LOCALES = mapOf(
        "SYSTEM" to LocaleListCompat.getEmptyLocaleList(),
        "zh" to LocaleListCompat.create(Locale.SIMPLIFIED_CHINESE),
        "en" to LocaleListCompat.create(Locale.ENGLISH)
    )

    fun applyLanguage(language: String) {
        val localeList = SUPPORTED_LOCALES[language] ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getCurrentLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) return "SYSTEM"
        val locale = appLocales[0] ?: return "SYSTEM"
        return when (locale.language) {
            Locale.SIMPLIFIED_CHINESE.language -> "zh"
            Locale.ENGLISH.language -> "en"
            else -> "SYSTEM"
        }
    }

    /**
     * 启动时把**系统真源**（per-app language）单向同步回 DataStore。
     *
     * 语言的持久化真源在系统侧：Android 13+ 由 `LocaleManager` 持久化 per-app language，
     * Android 12 及以下由 AppCompat 经 manifest 的 `autoStoreLocales` 自动存储/恢复
     * （见 AndroidManifest 的 `AppLocalesMetadataHolderService`）。App 内切换走
     * [applyLanguage] + `setLanguage`，两处一致。这是官方标准路径
     * （developer.android.com「Per-app language preferences」）。
     *
     * 因此启动时**绝不能无脑反向覆盖系统**：旧实现 `applySavedLanguage()` 在 Application
     * onCreate 里同步读 DataStore——此时 StateFlow（`stateIn(Eagerly, emptyPreferences())`）
     * 尚未载入，读到的恒是默认值 "SYSTEM"，随即 `setApplicationLocales(空列表)`
     * 把用户在系统设置里为掌记配置的语言**清掉**。这正是用户反馈
     * 「realme 改了应用语言、再打开还是英文」的根因。
     *
     * 例外是**一次性迁移**（官方文档点名场景：「从自定义存储切换到 autoStoreLocales 时，
     * 应从自定义存储检索语言区域并调 setApplicationLocales 传给系统」）：v1.4.1 前
     * 旧版把语言存 DataStore 且启动自管恢复，Android 12- 设备上系统侧从没存过它；
     * 直接切换真源会让这批用户在 App 内选过的语言丢一次。首次启动若 DataStore 有
     * 非跟随值，回灌进系统真源再打标记；此后永远以系统为准。
     *
     * 这里只做显示层的归位：设置页选中态读 DataStore（SettingsViewModel），
     * 与系统真源不一致时写回，保证 UI 与实际语言一致。
     *
     * @return 归一化后的当前语言码（"SYSTEM"/"zh"/"en"）
     */
    suspend fun syncStoredLanguageFromSystem(preferences: PreferencesManager): String {
        val system = getCurrentLanguage()
        if (!preferences.languageMigrated.first()) {
            val legacy = preferences.language.first()
            val effective = if (legacy != "SYSTEM") {
                // 迁移语义 = 升级前后所见一致：旧版的实际显示语言由 DataStore 决定，回灌它。
                // setApplicationLocales 要求主线程（同 SettingsViewModel.setLanguage 的约束），
                // 本函数跑在 applicationScope(IO)，必须显式切回。
                withContext(Dispatchers.Main.immediate) { applyLanguage(legacy) }
                legacy
            } else {
                system
            }
            preferences.setLanguageMigrated()
            if (preferences.language.first() != effective) {
                preferences.setLanguage(effective)
            }
            return effective
        }
        if (preferences.language.first() != system) {
            preferences.setLanguage(system)
        }
        return system
    }
}
