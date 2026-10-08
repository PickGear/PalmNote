package com.palmnote.ui.asset

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.palmnote.data.db.entity.Asset
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
 * 详情页**头部**那排 chip 的离屏渲染核对。
 *
 * 它以前只看质保，所以「只填了保质期」的物品在头部一个到期 chip 都没有。现在改成取
 * 质保/保质期里更紧迫的那个（与列表卡、网格卡同一套 `nearestExpiryDate` + `expiryAccentColor`），
 * 这条测试钉的就是那个行为。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class AssetDetailHeaderExpiryChipRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun millisFromToday(days: Long): Long =
        LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun renderDetail(asset: Asset) {
        val vm = mockk<AssetViewModel>(relaxed = true) {
            every { detailState } returns MutableStateFlow(AssetDetailState(asset = asset, isLoading = false))
            every { showAwayDialog } returns MutableStateFlow(false)
            every { showClearDialog } returns MutableStateFlow(false)
            every { showDeleteDialog } returns MutableStateFlow(false)
            every { presetCategoryOverrides } returns MutableStateFlow<Map<String, String>>(emptyMap())
            every { customCategories } returns MutableStateFlow<List<CategoryItem>>(emptyList())
        }
        compose.setContent { PalmNoteTheme { AssetDetailScreen(assetId = 1L, viewModel = vm) } }
    }

    @Test
    fun `只填保质期的物品，头部也有到期 chip`() {
        renderDetail(Asset(name = "牛奶", category = "食品", shelfLifeExpireDate = millisFromToday(5)))
        compose.onNodeWithText("保质期5天").assertExists()
    }

    @Test
    fun `质保更紧迫时头部显示质保`() {
        renderDetail(
            Asset(
                name = "相机",
                category = "数码",
                warrantyExpireDate = millisFromToday(2),
                shelfLifeExpireDate = millisFromToday(300)
            )
        )
        compose.onNodeWithText("质保2天").assertExists()
    }
}
