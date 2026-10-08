package com.palmnote.ui.asset

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **出图用的诊断类，不是断言测试**（同 `DetailRowHeightSpecTest` 的性质：只打印/出图，不断言）。
 *
 * 用途：改表单布局时把那一屏画成 PNG 自己看一眼——「占不占地方、好不好看」没法用断言判断，
 * 而真机上又要靠点击才能走到这一屏。图落在 `app/build/reports/form-*.png`，不进版本库。
 *
 * 注：`captureToImage()` 在 Robolectric 下会超时，所以走 `decorView.draw(Canvas)`；
 * `@GraphicsMode(NATIVE)` 是让文字真的画出来（否则是空图）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class AssetFormLayoutDumpTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel(form: AddAssetFormState): AssetViewModel = mockk(relaxed = true) {
        every { formState } returns MutableStateFlow(form)
        every { customCategories } returns MutableStateFlow<List<CategoryItem>>(emptyList())
        every { presetCategoryOverrides } returns MutableStateFlow<Map<String, String>>(emptyMap())
    }

    private fun dump(name: String, form: AddAssetFormState, openUnitMenu: Boolean = false) {
        compose.setContent {
            PalmNoteTheme { AddAssetScreen(assetId = null, viewModel = viewModel(form)) }
        }
        // 表单靠下，先滚到保质期那一栏
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("保质期限"))
        compose.waitForIdle()
        if (openUnitMenu) {
            // 点框内右侧的单位文字，把下拉展开——配色问题只有展开时才看得见
            compose.onNodeWithText("月").performClick()
            compose.waitForIdle()
        }

        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val out = File("build/reports/form-$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("DIAG wrote ${out.absolutePath} (${view.width}x${view.height})")
    }

    @Test
    fun `到期日录法`() = dump("date", AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_DATE))

    @Test
    fun `保质期限录法`() = dump(
        "period",
        AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_PERIOD)
            .withShelfLifePeriod(
                java.time.LocalDate.of(2026, 5, 10).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                "12",
                com.palmnote.domain.model.ShelfLifeUnit.MONTH.value
            )
    )

    @Test
    fun `单位下拉展开`() = dump(
        "period-menu",
        AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_PERIOD)
            .withShelfLifePeriod(
                java.time.LocalDate.of(2026, 5, 10).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                "12",
                com.palmnote.domain.model.ShelfLifeUnit.MONTH.value
            ),
        openUnitMenu = true
    )
}
