package com.palmnote.ui.asset

import android.app.Application
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.palmnote.data.db.entity.Asset
import com.palmnote.domain.model.ShelfLifeUnit
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 保质期倒计时的**离屏渲染**核对：卡片真的渲染出标题、到期日与天数，而不是只"代码写了"。
 *
 * 断言用 `assertExists` 而非 `assertIsDisplayed`：Robolectric 的窗口尺寸与文字度量都不等于真机
 * （同 DetailRowHeightSpecTest 的边界），`isDisplayed` 在这里恒为假。能证明的是"这一坨渲染出来了、
 * 资源解析到了、文案对"，真机高度仍要看截图。
 *
 * 注意 `qualifiers = "zh"`：Robolectric 默认按英文取资源，不带这个限定符断言的中文会全部落空。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class AssetShelfLifeRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun millisFromToday(days: Long): Long = LocalDate.now()
        .plusDays(days)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun assetExpiringIn(days: Long): Asset =
        Asset(name = "牛奶", category = "食品", shelfLifeExpireDate = millisFromToday(days))

    private fun renderCard(asset: Asset) {
        compose.setContent { PalmNoteTheme { ShelfLifeCard(asset) } }
        val bounds = compose.onRoot().getBoundsInRoot()
        println("DIAG shelfLifeCardHeight=${bounds.bottom - bounds.top}")
    }

    private fun renderGrid(asset: Asset) {
        compose.setContent { PalmNoteTheme { GridAssetCard(asset = asset, onClick = {}) } }
        val bounds = compose.onRoot().getBoundsInRoot()
        println("DIAG gridCardHeight=${bounds.bottom - bounds.top}")
    }

    @Test
    fun `详情卡未过期时渲染标题、到期日与剩余天数`() {
        renderCard(assetExpiringIn(10))
        compose.onNodeWithText("保质期").assertExists()
        compose.onNodeWithText("到期", substring = true).assertExists()
        compose.onNodeWithText("剩余10天").assertExists()
    }

    @Test
    fun `详情卡到期当天算剩余 0 天而不是已过期`() {
        renderCard(assetExpiringIn(0))
        compose.onNodeWithText("剩余0天").assertExists()
    }

    @Test
    fun `详情卡已过期时渲染已过期天数`() {
        renderCard(assetExpiringIn(-5))
        compose.onNodeWithText("保质期").assertExists()
        compose.onNodeWithText("已过期5天").assertExists()
    }

    @Test
    fun `详情卡按期限录法时显示生产日期与保质期`() {
        renderCard(
            Asset(
                name = "牛奶",
                category = "食品",
                shelfLifeExpireDate = millisFromToday(200),
                shelfLifeProducedDate = millisFromToday(-100),
                shelfLifeDurationValue = 12,
                shelfLifeDurationUnit = ShelfLifeUnit.MONTH.value
            )
        )
        compose.onNodeWithText("生产日期", substring = true).assertExists()
        compose.onNodeWithText("保质期: 12 个月").assertExists()
    }

    @Test
    fun `详情卡直接填到期日时不显示生产日期与保质期`() {
        renderCard(assetExpiringIn(10))
        compose.onNodeWithText("生产日期", substring = true).assertDoesNotExist()
        compose.onNodeWithText("保质期: ", substring = true).assertDoesNotExist()
    }

    @Test
    fun `网格卡放一个到期徽标并显示天数`() {
        renderGrid(assetExpiringIn(10))
        compose.onNodeWithText("保质期10天").assertExists()
    }

    @Test
    fun `网格卡已过期时显示已过期`() {
        renderGrid(assetExpiringIn(-5))
        compose.onNodeWithText("已过期").assertExists()
    }

    @Test
    fun `网格卡质保与保质期同时存在时取更紧迫的那个`() {
        renderGrid(
            Asset(
                name = "相机",
                category = "数码",
                warrantyExpireDate = millisFromToday(400),
                shelfLifeExpireDate = millisFromToday(3)
            )
        )
        compose.onNodeWithText("保质期3天").assertExists()
    }
}
