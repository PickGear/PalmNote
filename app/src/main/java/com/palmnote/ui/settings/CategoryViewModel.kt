package com.palmnote.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.R
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.domain.repository.CategoryConfigRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


@Stable
data class CategoryType(
    val key: String,
    @StringRes val labelResId: Int
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
    val categories: List<CategoryConfig> = emptyList()
) {
    val currentType: String
        get() = categoryTypes.getOrNull(selectedTypeIndex)?.key ?: "ASSET"
}

class CategoryViewModel(
    private val repository: CategoryConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryState())
    val state: StateFlow<CategoryState> = _state.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            repository.getAllCategories().collect { allCategories ->
                _state.update { state ->
                    val filtered = allCategories.filter { it.type == state.currentType }
                    state.copy(categories = filtered)
                }
            }
        }
    }

    fun selectType(index: Int) {
        _state.update { it.copy(selectedTypeIndex = index) }
        loadCategories()
    }

    fun addCategory(category: CategoryConfig) {
        viewModelScope.launch {
            repository.insertCategory(category)
        }
    }

    fun updateCategory(category: CategoryConfig) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategoryById(id)
        }
    }

    fun toggleCategoryEnabled(id: Long) {
        viewModelScope.launch {
            val category = repository.getCategoryById(id) ?: return@launch
            repository.setCategoryEnabled(id, !category.isEnabled)
        }
    }
}
