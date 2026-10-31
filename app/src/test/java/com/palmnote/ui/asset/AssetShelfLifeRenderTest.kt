package com.palmnote.ui.asset

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.palmnote.data.db.entity.Asset
import com.palmnote.domain.model.ShelfLifeUnit
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertTrue
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

    /**
     * 网格卡的徽标行已升到**卡片全宽**（原先挤在图片右侧约 66dp 的内容列里，英文
     * "Under warranty" / "%d days to expiry" 会被 Clip 成 "Under" / "2 days to"）。
     *
     * 这里把容器收窄到 120dp 钉两条底线：两个徽标都保持正宽（不被压扁），
     * 且都落在卡片右缘之内（不被无声 Clip 裁掉）。放不下时 FlowRow 会换行兜底。
     */
    @Test
    fun `网格卡徽标升到卡片全宽后不压扁不截断`() {
        compose.setContent {
            PalmNoteTheme {
                Box(Modifier.width(120.dp)) {
                    GridAssetCard(asset = assetExpiringIn(21), onClick = {})
                }
            }
        }
        val expiry = compose.onNodeWithText("保质期21天", useUnmergedTree = true).getBoundsInRoot()
        val status = compose.onNodeWithText("持有中", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("到期徽标被压扁", expiry.right - expiry.left > 0.dp)
        assertTrue("状态徽标被压扁", status.right - status.left > 0.dp)
        assertTrue("到期徽标不应超出卡片左缘", expiry.left >= 0.dp)
        assertTrue("状态徽标不应被挤出卡片右缘", status.right <= 120.dp)
    }

    /**
     * 大图卡是**同一份信息的另一种排版**（不在测试里比高度：Robolectric 的文字度量不等于真机，
     * 高度差看着对也不代表真机对）。这里钉的是"信息没丢"——名称、到期徽标、状态徽标都在。
     */
    @Test
    fun `大图卡渲染与小图卡同一组信息`() {
        compose.setContent { PalmNoteTheme { LargeGridAssetCard(asset = assetExpiringIn(10), onClick = {}) } }
        compose.onNodeWithText("牛奶").assertExists()
        compose.onNodeWithText("保质期10天").assertExists()
        compose.onNodeWithText("持有中").assertExists()
    }

    /** 大图卡复用 [AssetCardBadgeRow]：窄容器下同样不许压扁或裁掉（与小图卡同一条底线）。 */
    @Test
    fun `大图卡窄容器下徽标不压扁不截断`() {
        compose.setContent {
            PalmNoteTheme {
                Box(Modifier.width(120.dp)) {
                    LargeGridAssetCard(asset = assetExpiringIn(21), onClick = {})
                }
            }
        }
        val expiry = compose.onNodeWithText("保质期21天", useUnmergedTree = true).getBoundsInRoot()
        val status = compose.onNodeWithText("持有中", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("到期徽标被压扁", expiry.right - expiry.left > 0.dp)
        assertTrue("状态徽标被压扁", status.right - status.left > 0.dp)
        assertTrue("到期徽标不应超出卡片左缘", expiry.left >= 0.dp)
        assertTrue("状态徽标不应被挤出卡片右缘", status.right <= 120.dp)
    }

    /**
     * 无照片的物品：大图卡回落成 120dp 分类色块 + 分类图标。
     *
     * 钉的是"没图也不塌"——图片块仍占满 120dp（否则无图卡片会比有图的矮一截，瀑布流里参差不齐），
     * 名称与分类照常渲染。有图路径由上面几条用例覆盖（[assetExpiringIn] 造的就是无图资产）。
     */
    @Test
    fun `大图卡无照片时图片块仍占 120dp 且信息照常渲染`() {
        compose.setContent { PalmNoteTheme { LargeGridAssetCard(asset = assetExpiringIn(10), onClick = {}) } }
        compose.onNodeWithText("牛奶").assertExists()
        compose.onNodeWithText("食品").assertExists()
        val card = compose.onRoot().getBoundsInRoot()
        assertTrue(
            "无图时卡片高度应 ≥ 图片块 120dp，实际 ${card.bottom - card.top}",
            card.bottom - card.top >= 120.dp
        )
    }
}
