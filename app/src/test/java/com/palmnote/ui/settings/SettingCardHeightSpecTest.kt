package com.palmnote.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.components.SettingRow
import com.palmnote.ui.components.SettingRowContent
import com.palmnote.ui.components.SettingsCard
import com.palmnote.ui.components.SettingsMenuItem
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 设置页「单行卡」高度的结构断言（design-spec §6.1 + §6.15）。
 *
 * **不钉绝对 dp 值**：文字实际高度受字体度量影响（Robolectric 实测双行内容约 70dp，
 * 而不是 bodyLarge 24 + bodySmall 16 = 40dp——字体自带上下留白会再加一圈）。
 * 钉死 64dp 这类数字只会在换环境时假红，且掩盖真正该守的东西。这里钉三条结构：
 *
 * 1. [SettingsCard]（卡片不带垂直内边距）比默认 `ModuleCard` **矮 24dp**
 *    （正好是卡片上下各 12dp 那一圈），这就是「单行卡虚高」的可量化差值；
 * 2. 该差值对 [SettingRow] 与 [SettingsMenuItem] 都成立（两种行组件的上下内边距同值）；
 *
 * `application = Application::class` 不能省：用真 App 类会去初始化 SQLCipher 的 AndroidKeyStore，
 * Robolectric 没有该 Provider，用例会全挂在 KeyStoreException 上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class SettingCardHeightSpecTest {

    @get:Rule
    val compose = createComposeRule()

    private fun heightOf(tag: String): Float {
        val b = compose.onNodeWithTag(tag).getBoundsInRoot()
        return (b.bottom - b.top).value
    }

    @Test
    fun `SettingRow 单行卡去掉卡片上下内边距后矮 24dp`() {
        compose.setContent {
            PalmNoteTheme {
                Column {
                    ModuleCard(modifier = Modifier.testTag("default")) {
                        SettingRow { SettingRowContent(title = "标题", subtitle = "副标题") }
                    }
                    SettingsCard(modifier = Modifier.testTag("tight")) {
                        SettingRow { SettingRowContent(title = "标题", subtitle = "副标题") }
                    }
                }
            }
        }
        assertEquals(
            "去掉卡片上下内边距应正好少 24dp（12dp×2）",
            24f,
            heightOf("default") - heightOf("tight"),
            1.0f
        )
    }

    @Test
    fun `SettingsMenuItem 单行卡同样矮 24dp`() {
        compose.setContent {
            PalmNoteTheme {
                Column {
                    ModuleCard(modifier = Modifier.testTag("default")) {
                        SettingsMenuItem(icon = Icons.Outlined.Settings, title = "标题", subtitle = "副标题")
                    }
                    SettingsCard(modifier = Modifier.testTag("tight")) {
                        SettingsMenuItem(icon = Icons.Outlined.Settings, title = "标题", subtitle = "副标题")
                    }
                }
            }
        }
        assertEquals(
            "SettingsMenuItem 去掉卡片上下内边距应正好少 24dp",
            24f,
            heightOf("default") - heightOf("tight"),
            1.0f
        )
    }

}
