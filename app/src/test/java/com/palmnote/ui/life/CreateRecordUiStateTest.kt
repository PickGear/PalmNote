package com.palmnote.ui.life

import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `CreateRecordUiState.canSave`：**"能不能保存"是业务规则，不该写在 UI 的 `enabled` 表达式里**
 * （那既不可测，又占 `LifeCreateRecordScreen` 的圈复杂度额度）。
 *
 * 关键一条：**"没有可填字段"也不允许保存** —— 那意味着模板加载失败或模板本身没有字段，
 * 而 `save()` 内部同样会 return。此前按钮是可点的，用户点了**什么都不发生**：
 * 看起来就是"点了没反应"。
 */
class CreateRecordUiStateTest {

    private fun field(key: String = "a") = FieldConfig(key = key, label = key, type = FieldType.TEXT)

    @Test
    fun `没有字段时不允许保存（模板加载失败或空模板）`() {
        assertFalse(CreateRecordUiState().canSave)
    }

    @Test
    fun `有字段且不在保存中就可以保存`() {
        assertTrue(CreateRecordUiState(fields = listOf(field())).canSave)
    }

    @Test
    fun `保存进行中不允许重复提交`() {
        assertFalse(CreateRecordUiState(fields = listOf(field()), saving = true).canSave)
    }
}
