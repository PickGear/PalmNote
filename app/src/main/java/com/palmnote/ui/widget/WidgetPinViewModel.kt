package com.palmnote.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 一个已落桌实例的当前配置，编辑面板打开时回填用。
 * 四项都取**覆盖值**：null = 跟随默认（与面板「不写 = 跟随默认」同一口径）。
 */
data class WidgetInstanceConfig(
    val opacity: Float? = null,
    val background: String? = null,
    val blur: Float? = null,
    val book: Long? = null,
    val accent: String? = null
)

/**
 * 添加页的落桌收尾：把面板里挑好的**不透明度**写到刚落桌的那个实例上。
 *
 * 为什么只能等落桌之后再写：我们的配置一律按 `appWidgetId` 存（键就是实例 id），
 * 而 id 要等桌面确认落桌才存在 —— 所以面板里选的值要等到核对到新 id 才落得下去。
 * 带系统配置页的组件（账单 / 概览 / 接下来）同样在这里落设置：系统配置页只管账本与配色，
 * 不透明度与背景一律由面板写 —— 面板按实例 id 写，落桌确认后立即生效。
 */
@HiltViewModel
class WidgetPinViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val accountBookDao: com.palmnote.data.db.dao.AccountBookDao
) : ViewModel() {

    /** 账本列表（隐藏账本不参与，与配置页一致）：面板「编辑账本与配色」层用。 */
    val books = accountBookDao.getAllBooks()

    /**
     * 覆盖某个实例的不透明度；[opacity] 传 null = 恢复跟随全局默认。
     * 写完立刻整轮刷新一次（只改外观，按 provider 细分不值得），否则要等下一次刷新才看得到。
     */
    fun applyOpacity(appWidgetId: Int, opacity: Float?) {
        viewModelScope.launch {
            preferencesManager.setWidgetOpacityFor(appWidgetId, opacity)
            WidgetUpdateHelper.refreshAllWidgets()
        }
    }

    /** 面板/配置页选的背景色落到该实例；null = 回到默认卡片底色。 */
    fun applyBackground(appWidgetId: Int, colorHex: String?) {
        viewModelScope.launch {
            preferencesManager.setWidgetBackgroundFor(appWidgetId, colorHex)
            WidgetUpdateHelper.refreshAllWidgets()
        }
    }

    /** 图片背景的模糊度落到该实例；null/0 = 不模糊。 */
    fun applyBlur(appWidgetId: Int, blur: Float?) {
        viewModelScope.launch {
            preferencesManager.setWidgetBlurFor(appWidgetId, blur)
            WidgetUpdateHelper.refreshAllWidgets()
        }
    }

    /** 面板「编辑账本与配色」层的选择落到该实例；bookId 传 null = 全账本。 */
    fun applyBook(appWidgetId: Int, bookId: Long?) {
        viewModelScope.launch {
            preferencesManager.setWidgetBook(appWidgetId, bookId)
            WidgetUpdateHelper.refreshBillWidgets()
        }
    }

    /**
     * 面板「编辑账本与配色」层的配色落到该实例；accentId 传 null = 跟随应用主题包。
     *
     * 刷新范围用全量：配色卡的组件是「账单 / 概览 / 接下来」三族，只刷账单族会让
     * 另外两族改完不重绘（看起来像设置没生效）。
     */
    fun applyAccent(appWidgetId: Int, accentId: String?) {
        viewModelScope.launch {
            preferencesManager.setWidgetAccentThemeFor(appWidgetId, accentId)
            WidgetUpdateHelper.refreshAllWidgets()
        }
    }

    /**
     * 读某个已落桌实例的当前配置（编辑面板回填）。
     * 取**覆盖值**而不是实际值：面板里「没动过」就等于跟随默认，不能因为读了一次就写成覆盖。
     */
    suspend fun loadInstance(appWidgetId: Int): WidgetInstanceConfig = WidgetInstanceConfig(
        opacity = preferencesManager.widgetOpacityOverride(appWidgetId).first(),
        background = preferencesManager.widgetBackgroundFor(appWidgetId).first(),
        blur = preferencesManager.widgetBlurFor(appWidgetId).first().takeIf { it > 0f },
        book = preferencesManager.widgetBook(appWidgetId).first(),
        accent = preferencesManager.widgetAccentThemeFor(appWidgetId).first()
    )

    /**
     * 保存编辑结果：**四项都写**（包括 null）。
     *
     * 与添加时的草稿相反：添加态里 null 表示「这次没选、别写覆盖」，
     * 编辑态里 null 是用户的明确选择「回到默认」（背景白底 / 不透明度跟随全局 / 全账本）。
     * 写完只刷新一轮，不逐项各刷一次。
     */
    fun saveInstance(appWidgetId: Int, config: WidgetInstanceConfig) {
        viewModelScope.launch {
            preferencesManager.setWidgetOpacityFor(appWidgetId, config.opacity)
            preferencesManager.setWidgetBackgroundFor(appWidgetId, config.background)
            preferencesManager.setWidgetBlurFor(appWidgetId, config.blur)
            preferencesManager.setWidgetBook(appWidgetId, config.book)
            preferencesManager.setWidgetAccentThemeFor(appWidgetId, config.accent)
            WidgetUpdateHelper.refreshAllWidgets()
        }
    }
}
