package com.palmnote.ui.life

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.palmnote.app.R
import com.palmnote.data.db.entity.BuiltinFieldText
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.ui.components.AppDialog

/**
 * 详情页「**点值即改**」。
 *
 * ## 其他 app 是怎么做的
 *
 * todo / 计划类 app 里，**详情页的值本身就是入口**：点标题就地改文字、点日期弹日期选择器、
 * 点数字弹数字键盘、点分类弹选项 —— 只有「新建」才进表单页。
 * 原实现唯一的入口是「⋯ → 编辑记录」，改一个字要四步，而详情页上所有值都只是文字。
 *
 * ## 本轮范围（都写死在 [isInlineEditable] 一处）
 *
 * 覆盖：文本类、数值类、日期 / 时间 / 日期时间（复用系统选择器）、布尔（就地开关）。
 * **仍未覆盖**：`SELECT` / `TAG` / `MULTI_SELECT`（要弹选项 chips）、媒体 / 地图 / 表格 /
 * 区间 / 派生字段 —— 这些仍走编辑页。
 */

/** 哪些类型在详情页可以就地编辑。 */
internal fun isInlineEditable(type: FieldType): Boolean = when (type) {
    FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT,
    FieldType.URL, FieldType.EMAIL, FieldType.PHONE, FieldType.LOCATION,
    FieldType.NUMBER, FieldType.CURRENCY, FieldType.PERCENT, FieldType.PERCENTAGE,
    FieldType.RATING, FieldType.SLIDER, FieldType.DURATION,
    FieldType.SELECT, FieldType.TAG, FieldType.MULTI_SELECT -> true
    else -> false
}

/** 数值类：就地编辑用小数点键盘，免得用户先切键盘。 */
internal fun isNumericType(type: FieldType): Boolean = when (type) {
    FieldType.NUMBER, FieldType.CURRENCY, FieldType.PERCENT, FieldType.PERCENTAGE,
    FieldType.RATING, FieldType.SLIDER, FieldType.DURATION -> true
    else -> false
}

/** 逗号分隔的多选值（与 `LifeCreateFieldKit.MultiSelectSection` 同一形态）。 */
internal fun csvValues(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotBlank() }

/** 勾 / 取消一个选项，仍以逗号分隔串返回 —— 与多选控件、`fieldsData` 的形态一致。 */
internal fun toggleCsv(raw: String, option: String): String {
    val cur = csvValues(raw).toSet()
    return (if (option in cur) cur - option else cur + option).joinToString(",")
}

/**
 * 单字段就地编辑弹窗（文本 / 数值 / 选项三类）。
 *
 * 日期 / 时间 / 日期时间不走这里 —— 它们直接用系统选择器（[LifeDatePickerDialog] /
 * [LifeTimePickerDialog]），少一层弹窗。布尔也不走这里，点一下就翻，不需要确认。
 */
@Composable
internal fun QuickEditDialog(
    config: FieldConfig,
    initial: String,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(config.key) { mutableStateOf(initial) }
    val hasOptions = config.options.isNotEmpty()
    val context = LocalContext.current
    // 选项**既是显示也是存库的值**（SELECT / TAG / MULTI_SELECT 都没有独立的 key），
    // 显示与值必须同源：只翻显示、值留中文的话，英文界面里改出来的值会存成中文，
    // 详情页再原样渲染出中文，而且 chip 的选中态也对不上。
    val options = remember(config.options, context) {
        config.options.map { BuiltinFieldText.localize(context, it) }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.label.ifBlank { config.key }, fontWeight = FontWeight.Bold) },
        text = {
            when {
                // 有选项就点选（todo / 计划类 app 的选择器都是 chips，不用打字）
                config.type == FieldType.SELECT && hasOptions -> FlowChips(
                    labels = options, values = options, selected = text
                ) { text = it }
                config.type == FieldType.TAG && hasOptions -> FlowChips(
                    labels = options, values = options, selected = text
                ) { text = it }
                config.type == FieldType.MULTI_SELECT && hasOptions -> FlowChips(
                    labels = options,
                    values = options,
                    selectedList = csvValues(text),
                    onToggle = { option -> text = toggleCsv(text, option) }
                )
                config.type == FieldType.RICH_TEXT -> FormTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = config.placeholder.ifBlank { config.label },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 10
                )
                else -> FormTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = config.placeholder.ifBlank {
                        if (isNumericType(config.type)) "0" else config.label
                    },
                    keyboardType = if (isNumericType(config.type)) KeyboardType.Decimal else KeyboardType.Text
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCommit(text)
                onDismiss()
            }) {
                Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
