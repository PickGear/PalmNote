package com.palmnote.ui.asset

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.palmnote.domain.model.ShelfLifeUnit
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.theme.PalmNoteTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 新增/编辑物品表单里保质期一栏的**离屏渲染**核对：两种录法各自的控件都在。
 *
 * 该栏在表单靠下（Section 4），首屏不在视口内，所以先把列表滚到它再断言。
 * ViewModel 用 mockk 顶掉（配方同 [[palmnote-test-device-limits]] 记的离屏渲染做法）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class AssetFormShelfLifeRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun mockViewModel(form: AddAssetFormState = AddAssetFormState()): AssetViewModel =
        mockk(relaxed = true) {
            every { formState } returns MutableStateFlow(form)
            every { customCategories } returns MutableStateFlow<List<CategoryItem>>(emptyList())
            every { presetCategoryOverrides } returns MutableStateFlow<Map<String, String>>(emptyMap())
        }

    private fun renderForm(form: AddAssetFormState = AddAssetFormState()) {
        compose.setContent {
            PalmNoteTheme { AddAssetScreen(assetId = null, viewModel = mockViewModel(form)) }
        }
        // 表单里有两个可滚动容器（外层 LazyColumn + 分类行的 LazyRow）。语义树前序里
        // 第一个就是最外层那个，即表单列表本身。「保质期限」这个模式 chip 两种录法下都在。
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("保质期限"))
    }

    @Test
    fun `表单里有保质期一栏与两种录法`() {
        renderForm()
        compose.onNodeWithText("保质期").assertExists()
        compose.onNodeWithText("到期日").assertExists()
        compose.onNodeWithText("保质期限").assertExists()
    }

    @Test
    fun `保质期限录法渲染生产日期、时长单位与算出的到期日`() {
        val produced = LocalDate.of(2026, 5, 10)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 走真实路径构造（withShelfLifePeriod 会把到期日同步进来），别手搓一个半截状态
        val form = AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_PERIOD)
            .withShelfLifePeriod(produced, "12", ShelfLifeUnit.MONTH.value)
        renderForm(form)
        compose.onNodeWithText("生产日期").assertExists()
        compose.onNodeWithText("月").assertExists()
        // 生产日期 2026/05/10 + 12 个月 = 2027/05/10（formatDisplayYearDate 为 yyyy/MM/dd）
        compose.onNodeWithText("到期: 2027/05/10").assertExists()
    }
}
