package com.palmnote.data.repository

import android.content.Context
import com.palmnote.R
import com.palmnote.data.db.dao.CategoryConfigDao
import com.palmnote.data.db.dao.CustomTagDao
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.CustomTag
import com.palmnote.ui.theme.AppIcon
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.CategoryConfigRepository

class CategoryConfigRepository(
    private val context: Context,
    private val categoryConfigDao: CategoryConfigDao,
    private val customTagDao: CustomTagDao
) : CategoryConfigRepository {
    // ========== 分类管理 ==========
    override fun getCategoriesByType(type: String): Flow<List<CategoryConfig>> =
        categoryConfigDao.getCategoriesByType(type)

    override fun getAllCategoriesByType(type: String): Flow<List<CategoryConfig>> =
        categoryConfigDao.getAllCategoriesByType(type)

    override fun getAllCategories(): Flow<List<CategoryConfig>> =
        categoryConfigDao.getAllCategories()

    override suspend fun getCategoryById(id: Long): CategoryConfig? =
        categoryConfigDao.getCategoryById(id)

    override suspend fun getCategoryByName(type: String, name: String): CategoryConfig? =
        categoryConfigDao.getCategoryByName(type, name)

    override fun getSubCategories(parentId: Long): Flow<List<CategoryConfig>> =
        categoryConfigDao.getSubCategories(parentId)

    override suspend fun insertCategory(category: CategoryConfig): Long =
        categoryConfigDao.insert(category)

    override suspend fun updateCategory(category: CategoryConfig) =
        categoryConfigDao.update(category)

    override suspend fun deleteCategory(category: CategoryConfig) =
        categoryConfigDao.delete(category)

    override suspend fun deleteCategoryById(id: Long) =
        categoryConfigDao.deleteById(id)

    override suspend fun setCategoryEnabled(id: Long, enabled: Boolean) =
        categoryConfigDao.setEnabled(id, enabled)

    // ========== 自定义标签 ==========
    override fun getAllTags(): Flow<List<CustomTag>> =
        customTagDao.getAllTags()

    override fun getTagsByType(type: String): Flow<List<CustomTag>> =
        customTagDao.getTagsByType(type)

    override fun searchTags(query: String): Flow<List<CustomTag>> =
        customTagDao.searchTags(query)

    override suspend fun getTagById(id: Long): CustomTag? =
        customTagDao.getTagById(id)

    override suspend fun getTagByName(name: String): CustomTag? =
        customTagDao.getTagByName(name)

    override suspend fun insertTag(tag: CustomTag): Long =
        customTagDao.insert(tag)

    override suspend fun updateTag(tag: CustomTag) =
        customTagDao.update(tag)

    override suspend fun incrementTagUsage(id: Long) =
        customTagDao.incrementUsage(id)

    override suspend fun deleteTag(id: Long) =
        customTagDao.softDelete(id)

    // ========== 初始化默认分类 ==========
    override suspend fun initDefaultCategories() {
        val existing = categoryConfigDao.getCategoryByName("ASSET", context.getString(R.string.asset_category_digital))
        if (existing != null) return

        // 资产分类
        val assetCategories = listOf(
            Triple(context.getString(R.string.asset_category_digital), AppIcon.Devices, "#4285F4"),
            Triple(context.getString(R.string.asset_category_appliance), AppIcon.Kitchen, "#34A853"),
            Triple(context.getString(R.string.asset_category_furniture), AppIcon.Chair, "#FBBC04"),
            Triple(context.getString(R.string.asset_category_clothing), AppIcon.Checkroom, "#EA4335"),
            Triple(context.getString(R.string.asset_category_sports), AppIcon.SportsBasketball, "#FF6D00"),
            Triple(context.getString(R.string.asset_category_books), AppIcon.MenuBook, "#9C27B0"),
            Triple(context.getString(R.string.asset_category_cosmetics), AppIcon.Face, "#E91E63"),
            Triple(context.getString(R.string.asset_category_food), AppIcon.Restaurant, "#795548"),
            Triple(context.getString(R.string.asset_category_tools), AppIcon.Build, "#607D8B"),
            Triple(context.getString(R.string.asset_category_other), AppIcon.Inventory2, "#9E9E9E")
        )
        assetCategories.forEachIndexed { index, (name, icon, color) ->
            categoryConfigDao.insert(CategoryConfig(
                type = "ASSET", name = name, icon = icon,
                color = color, sortOrder = index, isDefault = true
            ))
        }

        // 支出分类
        val expenseCategories = listOf(
            context.getString(R.string.category_food) to AppIcon.Restaurant,
            context.getString(R.string.category_transport) to AppIcon.Flight,
            context.getString(R.string.category_shopping) to AppIcon.ShoppingCart,
            context.getString(R.string.category_entertainment) to AppIcon.SportsEsports,
            context.getString(R.string.category_housing) to AppIcon.Home,
            context.getString(R.string.category_medical) to AppIcon.LocalHospital,
            context.getString(R.string.category_education) to AppIcon.School,
            context.getString(R.string.category_communication) to AppIcon.PhoneAndroid,
            context.getString(R.string.asset_category_clothing) to AppIcon.Checkroom,
            context.getString(R.string.asset_category_sports) to AppIcon.SportsBasketball,
            context.getString(R.string.asset_category_other) to AppIcon.MoreHoriz
        )
        expenseCategories.forEachIndexed { index, (name, icon) ->
            categoryConfigDao.insert(CategoryConfig(
                type = "BILL_EXPENSE", name = name, icon = icon,
                sortOrder = index, isDefault = true
            ))
        }

        // 收入分类
        val incomeCategories = listOf(
            context.getString(R.string.category_salary) to AppIcon.AccountBalance,
            context.getString(R.string.category_bonus) to AppIcon.EmojiEvents,
            context.getString(R.string.category_investment) to AppIcon.TrendingUp,
            context.getString(R.string.category_part_time) to AppIcon.Work,
            context.getString(R.string.category_red_packet) to AppIcon.CardGiftcard,
            context.getString(R.string.category_refund) to AppIcon.AutoMirrored_Outlined_Undo,
            context.getString(R.string.asset_category_other) to AppIcon.MoreHoriz
        )
        incomeCategories.forEachIndexed { index, (name, icon) ->
            categoryConfigDao.insert(CategoryConfig(
                type = "BILL_INCOME", name = name, icon = icon,
                sortOrder = index, isDefault = true
            ))
        }

        // 目标分类
        val goalCategories = listOf(
            Triple(context.getString(R.string.goal_category_fitness), AppIcon.FitnessCenter, "#FF6D00"),
            Triple(context.getString(R.string.goal_category_reading), AppIcon.MenuBook, "#4285F4"),
            Triple(context.getString(R.string.goal_category_skill), AppIcon.School, "#9C27B0"),
            Triple(context.getString(R.string.goal_category_habit), AppIcon.Today, "#34A853"),
            Triple(context.getString(R.string.goal_category_project), AppIcon.Assignment, "#FBBC04"),
            Triple(context.getString(R.string.goal_category_savings), AppIcon.Savings, "#E91E63"),
            Triple(context.getString(R.string.goal_category_custom), AppIcon.Build, "#607D8B")
        )
        goalCategories.forEachIndexed { index, (name, icon, color) ->
            categoryConfigDao.insert(CategoryConfig(
                type = "GOAL", name = name, icon = icon,
                color = color, sortOrder = index, isDefault = true
            ))
        }

        // 纪念日类型
        val anniversaryTypes = listOf(
            Triple(context.getString(R.string.anniversary_type_birthday), AppIcon.Celebration, "#E91E63"),
            Triple(context.getString(R.string.anniversary_type_wedding), AppIcon.Favorite, "#FF6D00"),
            Triple(context.getString(R.string.anniversary_type_meeting), AppIcon.Handshake, "#4285F4"),
            Triple(context.getString(R.string.anniversary_type_graduation), AppIcon.School, "#9C27B0"),
            Triple(context.getString(R.string.anniversary_type_work), AppIcon.Work, "#607D8B"),
            Triple(context.getString(R.string.anniversary_type_travel), AppIcon.Flight, "#00BCD4"),
            Triple(context.getString(R.string.anniversary_type_baby), AppIcon.ChildCare, "#FF9800"),
            Triple(context.getString(R.string.anniversary_type_pet), AppIcon.Pets, "#795548"),
            Triple(context.getString(R.string.anniversary_type_custom), AppIcon.EditCalendar, "#9E9E9E")
        )
        anniversaryTypes.forEachIndexed { index, (name, icon, color) ->
            categoryConfigDao.insert(CategoryConfig(
                type = "ANNIVERSARY", name = name, icon = icon,
                color = color, sortOrder = index, isDefault = true
            ))
        }

        // 瞬间分类
        val momentCategories = listOf(
            Triple(context.getString(R.string.moment_category_travel), AppIcon.Flight, "#00BCD4"),
            Triple(context.getString(R.string.moment_category_food), AppIcon.Restaurant, "#FF9800"),
            Triple(context.getString(R.string.moment_category_family), AppIcon.Group, "#E91E63"),
            Triple(context.getString(R.string.moment_category_work), AppIcon.Work, "#607D8B"),
            Triple(context.getString(R.string.moment_category_friends), AppIcon.Group, "#4285F4"),
            Triple(context.getString(R.string.moment_category_hobby), AppIcon.Palette, "#9C27B0"),
            Triple(context.getString(R.string.moment_category_nature), AppIcon.Eco, "#4CAF50"),
            Triple(context.getString(R.string.moment_category_pet), AppIcon.Pets, "#795548"),
            Triple(context.getString(R.string.moment_category_custom), AppIcon.Edit, "#9E9E9E")
        )
        momentCategories.forEachIndexed { index, (name, icon, color) ->
            categoryConfigDao.insert(CategoryConfig(
                type = "MOMENT", name = name, icon = icon,
                color = color, sortOrder = index, isDefault = true
            ))
        }

        // 默认标签
        val defaultTags = listOf(
            context.getString(R.string.tag_default_important),
            context.getString(R.string.tag_default_daily),
            context.getString(R.string.tag_default_work),
            context.getString(R.string.tag_default_life),
            context.getString(R.string.tag_default_study),
            context.getString(R.string.tag_default_entertainment),
            context.getString(R.string.tag_default_travel),
            context.getString(R.string.tag_default_food),
            context.getString(R.string.tag_default_family),
            context.getString(R.string.tag_default_friends),
            context.getString(R.string.tag_default_health),
            context.getString(R.string.tag_default_finance),
            context.getString(R.string.tag_default_shopping),
            context.getString(R.string.tag_default_gift)
        )
        defaultTags.forEach { name ->
            customTagDao.insert(CustomTag(
                name = name,
                applicableTypes = "ASSET,BILL,GOAL,MOMENT"
            ))
        }
    }
}
