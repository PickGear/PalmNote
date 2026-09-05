package com.palmnote.ui.settings
import kotlin.jvm.JvmSuppressWildcards
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.repository.CategoryConfigRepository
import com.palmnote.ui.asset.assetCategoryItems
import com.palmnote.ui.bills.expenseCategoryItems
import com.palmnote.ui.bills.incomeCategoryItems
import com.palmnote.ui.theme.ColorResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

@Stable
data class CategoryType(
    val key: String,
    @StringRes val labelResId: Int
)

@Stable
data class CategoryEntry(
    val key: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val isPreset: Boolean,
    val isEnabled: Boolean,
    val configId: Long? = null,
    val configIcon: com.palmnote.ui.theme.AppIcon? = null
)

@Stable
data class CategoryState(
    val categoryTypes: List<CategoryType> = listOf(
        CategoryType("ASSET", R.string.recycle_tab_assets),
        CategoryType("BILL_EXPENSE", R.string.bill_expense),
        CategoryType("BILL_INCOME", R.string.bill_income),
        CategoryType("GOAL", R.string.recycle_tab_goals),
        CategoryType("ANNIVERSARY", R.string.recycle_tab_anniversaries),
        CategoryType("MOMENT", R.string.recycle_tab_moments),
        CategoryType("TAG", R.string.bill_tags)
    ),
    val selectedTypeIndex: Int = 0,
    val presetEntries: List<CategoryEntry> = emptyList(),
    val customEntries: List<CategoryEntry> = emptyList()
) {
    val currentType: String
        get() = categoryTypes.getOrNull(selectedTypeIndex)?.key ?: "ASSET"
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val cachedCategoryConfigs: @JvmSuppressWildcards StateFlow<List<CategoryConfig>>,
    private val repository: CategoryConfigRepository,
    private val preferencesManager: PreferencesManager,
    private val assetRepository: com.palmnote.domain.repository.AssetRepository,
    private val billRepository: com.palmnote.domain.repository.BillRepository
) : ViewModel() {

    private val _selectedTypeIndex = MutableStateFlow(0)

    val state: StateFlow<CategoryState> = combine(
        _selectedTypeIndex,
        cachedCategoryConfigs,
        preferencesManager.presetCategoryOverrides
    ) { index, configs, presetOverrides ->
        val types = CategoryState().categoryTypes
        val currentType = types.getOrNull(index)?.key ?: "ASSET"

        val customEntries = configs
            .filter { it.type == currentType }
            .map { config ->
                val catColor = try {
                    config.color.toComposeColor()
                } catch (_: Exception) { com.palmnote.ui.theme.AccentOrange }
                CategoryEntry(
                    key = "custom_${config.id}",
                    name = config.name,
                    icon = config.icon.imageVector,
                    color = catColor,
                    isPreset = false,
                    isEnabled = config.isEnabled,
                    configId = config.id,
                    configIcon = config.icon
                )
            }

        val presetEntries = loadPresetCategories(currentType, presetOverrides)

        CategoryState(
            selectedTypeIndex = index,
            presetEntries = presetEntries,
            customEntries = customEntries
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryState())

    private fun loadPresetCategories(type: String, overrides: Map<String, String>): List<CategoryEntry> {
        val source = when (type) {
            "ASSET" -> assetCategoryItems.map { it.name to it }
            "BILL_EXPENSE" -> expenseCategoryItems.map { "EXPENSE_${it.name}" to it }
            "BILL_INCOME" -> incomeCategoryItems.map { "INCOME_${it.name}" to it }
            else -> return emptyList()
        }
        return source.map { (keySuffix, item) ->
            val key = "preset_$keySuffix"
            val overrideJson = overrides[key]
            val overridesMap = if (overrideJson != null) {
                try {
                    val obj = org.json.JSONObject(overrideJson)
                    mutableMapOf<String, String>().apply {
                        if (obj.has("name")) put("name", obj.getString("name"))
                        if (obj.has("color")) put("color", obj.getString("color"))
                        if (obj.has("enabled")) put("enabled", obj.getString("enabled"))
                    }
                } catch (_: Exception) { emptyMap() }
            } else emptyMap()

            val enabled = overridesMap["enabled"]?.toBooleanStrictOrNull() ?: true
            val name = overridesMap["name"] ?: item.name
            val colorHex = overridesMap["color"]
            val color = if (colorHex != null) {
                try { Color(android.graphics.Color.parseColor(colorHex)) }
                catch (_: Exception) { item.color }
            } else item.color

            CategoryEntry(
                key = key,
                name = name,
                icon = item.icon,
                color = color,
                isPreset = true,
                isEnabled = enabled
            )
        }
    }

    private fun parsePresetOverride(json: String?): Map<String, String> {
        if (json == null) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            mutableMapOf<String, String>().apply {
                if (obj.has("name")) put("name", obj.getString("name"))
                if (obj.has("color")) put("color", obj.getString("color"))
                if (obj.has("enabled")) put("enabled", obj.getString("enabled"))
            }
        } catch (_: Exception) { emptyMap() }
    }

    fun savePresetOverride(key: String, name: String, colorHex: String, enabled: Boolean) {
        viewModelScope.launch {
            val json = org.json.JSONObject().apply {
                put("name", name)
                put("color", colorHex)
                put("enabled", enabled)
            }
            val current = preferencesManager.presetCategoryOverrides.first().toMutableMap()
            current[key] = json.toString()
            ColorResolver.loadPresetColorOverrides(current)
            preferencesManager.savePresetCategoryOverrides(current)
        }
    }

    fun resetPresetOverride(key: String) {
        viewModelScope.launch {
            val current = preferencesManager.presetCategoryOverrides.first().toMutableMap()
            current.remove(key)
            val colors = preferencesManager.categoryColorOverrides.first()
            ColorResolver.loadOverrides(colors)
            ColorResolver.loadPresetColorOverrides(current)
            preferencesManager.savePresetCategoryOverrides(current)
        }
    }

    fun selectType(index: Int) {
        _selectedTypeIndex.value = index
    }

    fun togglePresetEnabled(key: String) {
        viewModelScope.launch {
            val current = preferencesManager.presetCategoryOverrides.first().toMutableMap()
            val existing = parsePresetOverride(current[key])
            val enabled = !(existing["enabled"]?.toBooleanStrictOrNull() ?: true)
            val json = org.json.JSONObject().apply {
                existing.forEach { (k, v) -> if (k != "enabled") put(k, v) }
                put("enabled", enabled)
            }
            current[key] = json.toString()
            preferencesManager.savePresetCategoryOverrides(current)
            ColorResolver.loadPresetColorOverrides(current)
        }
    }

    fun addCategory(category: CategoryConfig) {
        viewModelScope.launch {
            repository.insertCategory(category)
        }
    }

    fun updateCategory(category: CategoryConfig) {
        viewModelScope.launch {
            val old = repository.getCategoryById(category.id)
            if (old != null && old.name != category.name) {
                billRepository.updateCategoryNameInBills(old.name, category.name)
                assetRepository.updateCategoryNameInAssets(old.name, category.name)
            }
            repository.updateCategory(category.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategoryById(id)
        }
    }

    suspend fun getCategoryUsageCount(name: String): Pair<Int, Int> {
        val bills = billRepository.countByCategory(name)
        val assets = assetRepository.countByCategory(name)
        return bills to assets
    }

    fun deleteCategoryWithData(categoryName: String, categoryId: Long) {
        viewModelScope.launch {
            billRepository.deleteByCategory(categoryName)
            assetRepository.deleteByCategory(categoryName)
            repository.deleteCategoryById(categoryId)
        }
    }

    fun toggleCategoryEnabled(id: Long) {
        viewModelScope.launch {
            val category = repository.getCategoryById(id) ?: return@launch
            repository.setCategoryEnabled(id, !category.isEnabled)
        }
    }
}

private fun String.toComposeColor(): Color = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: Exception) { com.palmnote.ui.theme.AccentOrange }
