package com.palmnote.ui.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.ui.theme.Spacing

/**
 * 模板编辑器「预览 → 填写」态：**直接复用填写页的真实控件**。
 *
 * ## 为什么不再画示意图
 *
 * 旧实现对所有类型都只画一条灰条；后来改进成"按类型画形状"的示意控件 ——
 * 但那仍然是自己画的一套：**分组规则、空态文案、控件尺寸都可能与真实表单不一致**，
 * 正是卡片态预览当初踩过的坑（自绘一套 → 与真实卡漂移）。
 *
 * 现在这里做三件与填写页**完全相同**的事：
 * 1. 同一套字段筛选（`EXCLUDED_FROM_FORM`：派生字段不进表单）；
 * 2. 同一套分组（`buildFieldGroups`：连续 inline 字段合并成一张卡）；
 * 3. 同一批控件（`SimpleRowsCard` / `ComplexFieldCard`）——
 *    靠 [FieldFormHost] 把"写入"换成只读宿主，所以预览里点得动但改不了。
 *
 * @param fields 模板的全部字段（停用与派生字段在内部按真实口径过滤）
 */
@Composable
internal fun PreviewFill(
    fields: List<FieldConfig>,
    accent: androidx.compose.ui.graphics.Color,
    context: android.content.Context
) {
    val visible = remember(fields) {
        fields.filter { !it.disabled && it.type !in LifeCreateRecordViewModel.EXCLUDED_FROM_FORM }
    }
    // 预览没有"用户输入"：用字段默认值当初始值，与字段级预览、详情态预览同源
    val state = remember(visible) {
        CreateRecordUiState(fields = visible, values = visible.associate { it.key to it.defaultValue })
    }
    val host = remember { PreviewFormHost() }
    val groups = remember(visible) { buildFieldGroups(visible) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.life_template_no_fields),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        groups.forEach { group ->
            val single = group.singleOrNull()
            if (single != null && single.type !in INLINE_FORM_TYPES) {
                ComplexFieldCard(single, accent, state, host, context)
            } else {
                SimpleRowsCard(group, accent, state, host)
            }
        }
    }
}

/**
 * 预览用的**只读宿主**：写入被吞掉。
 *
 * 于是预览里所有控件都是"真的、但不能用"—— 这正是预览该有的语义
 * （既不能污染模板默认值，也不能让用户以为在预览里改的就是记录）。
 */
private class PreviewFormHost : FieldFormHost {
    override fun updateValue(key: String, value: String) = Unit
    override fun importTrack(context: android.content.Context, uri: android.net.Uri, fieldKey: String) = Unit
}
