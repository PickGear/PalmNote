package com.palmnote.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.palmnote.data.datastore.PreferencesManager

object AppIconManager {

    private val aliases = mapOf(
        PreferencesManager.APP_ICON_GREEN_WHITE to "com.palmnote.app.IconGreenWhite",
        PreferencesManager.APP_ICON_BLACK_WHITE to "com.palmnote.app.IconBlackWhite",
        PreferencesManager.APP_ICON_WHITE_BLACK to "com.palmnote.app.IconWhiteBlack",
        PreferencesManager.APP_ICON_CYAN_WHITE to "com.palmnote.app.IconCyanWhite",
    )

    fun apply(context: Context, style: String): Boolean = runCatching {
        val pm = context.packageManager
        val selectedAlias = aliases[style] ?: return@runCatching false
        val selected = ComponentName(context.packageName, selectedAlias)
        setState(pm, selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        aliases.values.filter { it != selectedAlias }.forEach { alias ->
            setState(pm, ComponentName(context.packageName, alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }.isSuccess

    fun current(context: Context): String {
        val pkgName = context.applicationContext.packageName
        val pm = context.applicationContext.packageManager
        return aliases.entries.firstOrNull { (_, alias) ->
            pm.getComponentEnabledSetting(ComponentName(pkgName, alias)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }?.key ?: PreferencesManager.DEFAULT_APP_ICON_STYLE
    }

    private fun setState(pm: PackageManager, component: ComponentName, state: Int) {
        if (pm.getComponentEnabledSetting(component) == state) return
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
