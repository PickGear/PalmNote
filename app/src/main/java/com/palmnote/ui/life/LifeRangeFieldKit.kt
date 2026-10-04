package com.palmnote.ui.life

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.RangeModel
import com.palmnote.domain.model.encodeRange
import com.palmnote.domain.model.parseRange
import com.palmnote.domain.util.DateUtils
import java.time.LocalDate

/**
 * RANGE（区间）字段的编辑器：**起止两枚日期胶囊**。
 *
 * ## 为什么是日期，而不是「两个通用输入框」
 *
 * 详情页对 RANGE 唯一的渲染方式，就是把两个端点按 `DateUtils.parseDateValueOrNull` 当**日期**
 * 格式化（`LifeDetailModel` 的 `FieldType.RANGE` 分支）—— 数值型端点在那里本来就渲染成空。
 * 这里跟渲染口径保持一致，而不是另发明一套语义。
 * （已知局限：数值区间在详情页依旧显示为空，属既有渲染口径，留给单独一轮。）
 *
 * ## 为什么必须修
 *
 * 此前 RANGE 落在 `ComplexFieldCard` 的 `else -> FormTextField`，而 `decodeValue` 对它是
 * `else -> raw` —— 于是 `{"v":1,"start":…,"end":…}` 这段**载荷 JSON 被原样塞进了文本框**。
 * 用户看到的是内部格式，一改就把载荷改坏。所以这不算「没有控件」，
 * 而是「控件把内部格式暴露给了用户」。
 */
@Composable
internal fun RangeSection(
    cfg: FieldConfig,
    state: CreateRecordUiState,
    host: FieldFormHost
) {
    val parsed = parseRange(state.values[cfg.key])
    var showStart by remember(cfg.key) { mutableStateOf(false) }
    var showEnd by remember(cfg.key) { mutableStateOf(false) }
    val start = parsed?.start?.let { DateUtils.parseDateValueOrNull(it) }?.let { DateUtils.millisToLocalDate(it) }
    val end = parsed?.end?.let { DateUtils.parseDateValueOrNull(it) }?.let { DateUtils.millisToLocalDate(it) }

    fun commit(from: LocalDate?, to: LocalDate?) {
        // 两端都空 = 这个字段就是没填。写空串让 buildFieldsData 跳过它，
        // 而不是留一个 {"v":1,"start":"","end":""} 的空载荷。
        host.updateValue(
            cfg.key,
            if (from == null && to == null) {
                ""
            } else {
                encodeRange(RangeModel(from?.toString().orEmpty(), to?.toString().orEmpty()))
            }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PillValue(
            text = start?.format(dateTimePattern()) ?: stringResource(R.string.life_record_today),
            onClick = { showStart = true },
            muted = start == null
        )
        Text(
            "–",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PillValue(
            text = end?.format(dateTimePattern()) ?: stringResource(R.string.life_record_today),
            onClick = { showEnd = true },
            muted = end == null
        )
        if (start != null || end != null) {
            Text(
                text = stringResource(R.string.life_form_clear),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { commit(null, null) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
    if (showStart) {
        LifeDatePickerDialog(
            initial = start,
            onPick = { commit(it, end); showStart = false },
            onDismiss = { showStart = false }
        )
    }
    if (showEnd) {
        LifeDatePickerDialog(
            initial = end,
            onPick = { commit(start, it); showEnd = false },
            onDismiss = { showEnd = false }
        )
    }
}
