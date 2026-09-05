package com.palmnote.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
}
