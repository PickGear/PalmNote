package com.palmnote.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 删分类时预算的级联清理。
 *
 * 分类预算存的是**分类名**不是 id，所以分类一删，这条预算的 `spentOf` 就永远过滤不到
 * 任何账单 —— 它会一直挂在预算页停在 0%、永远不提醒。这里钉住两件事：
 * 1. 删支出分类会连带清掉它的预算；
 * 2. 分类名在表里不唯一（资产 / 收入分类可能同名），只删同名资产分类时不能误伤预算。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CategoryBudgetCascadeTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CategoryConfigRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CategoryConfigRepositoryImpl(
            context,
            db.categoryConfigDao(),
            db.customTagDao(),
            db,
            // 关演示：级联清理按「真实模式只清 NONE 来源」的口径走，测试种的是真实预算
            demoOffPrefs()
        )
    }

    @After
    fun tearDown() = db.close()

    private fun category(type: String, name: String) = CategoryConfig(type = type, name = name)

    private fun categoryBudget(name: String) = Budget(
        period = BudgetPeriod.MONTH.name,
        scope = BudgetScope.CATEGORY.name,
        category = name,
        totalBudget = 10_000L
    )

    @Test
    fun `删除支出分类会连带清掉该分类预算`() = runBlocking {
        val id = db.categoryConfigDao().insert(category("BILL_EXPENSE", "餐饮"))
        db.budgetDao().insertBudget(categoryBudget("餐饮"))

        repository.deleteCategoryById(id)

        assertEquals(0, db.budgetDao().getAllBudgetsOnce().size)
    }

    @Test
    fun `只删同名资产分类时支出预算不受影响`() = runBlocking {
        val assetId = db.categoryConfigDao().insert(category("ASSET", "餐饮"))
        db.budgetDao().insertBudget(categoryBudget("餐饮"))

        repository.deleteCategoryById(assetId)

        assertEquals(1, db.budgetDao().getAllBudgetsOnce().size)
    }

    @Test
    fun `删其他分类时账本预算不受影响`() = runBlocking {
        val id = db.categoryConfigDao().insert(category("BILL_EXPENSE", "餐饮"))
        db.budgetDao().insertBudget(
            Budget(
                period = BudgetPeriod.MONTH.name,
                scope = BudgetScope.BOOK.name,
                bookId = 7L,
                totalBudget = 50_000L
            )
        )

        repository.deleteCategoryById(id)

        assertEquals(1, db.budgetDao().getAllBudgetsOnce().size)
    }
}
