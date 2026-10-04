package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldContract
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.TableColumn
import com.palmnote.domain.model.TableModel
import com.palmnote.domain.model.compoundRawOf
import com.palmnote.domain.model.encodeTable
import com.palmnote.domain.model.parseTable
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.Success
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * TABLE 字段（明细表）的列定义、行编辑器与**单元格勾选**。
 *
 * 为什么单独一个文件：`LifeCreateFieldKit.kt` 已经 1300+ 行，而这段是**自成一体的**一块
 * （列从字段配置来、行在表单里编、勾选态有自己的存储约定），与其它控件的分派没有耦合。
 */

/**
 * TABLE 的列定义来自 `FieldConfig.options`，每项形如 `key:label:TYPE`
 * （内置模板里就是 `"name:名目:TEXT"`、`"amount:金额:CURRENCY"`）。
 *
 * ⚠️ 此前**没有任何代码读过它**：详情页用的是载荷自带的 `columns`，而载荷由 [encodeTable]
 * 生成、[encodeTable] 又没有调用方 —— 于是表格字段从**填写到展示**整条链都是死的。
 * 存钱「明细」/ 购物「购物明细」/ 旅行「行程明细」/ 订阅「扣费历史」四个内置模板都声明了
 * TABLE 字段，却一条都填不进去、也画不出来。
 *
 * 未知类型经 [FieldContract.normalize] 一律回落 `TEXT`，绝不抛。
 */
internal fun tableColumnsOf(cfg: FieldConfig): List<TableColumn> =
    cfg.options.mapNotNull { spec ->
        val parts = spec.split(":")
        val key = parts.getOrNull(0)?.trim().orEmpty()
        if (key.isBlank()) return@mapNotNull null
        val label = parts.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() } ?: key
        TableColumn(key, label, FieldContract.normalize(parts.getOrNull(2)?.trim().orEmpty()))
    }

// ───────────────────────── BOOLEAN 单元格的存储约定 ─────────────────────────

/**
 * 单元格「是否已勾选」。**这是全仓唯一一处判定**：
 * `LifeDetailViewModel.boughtText`（购物「已买 N / M」指标）也调它，
 * 免得「勾选框写的值」与「统计读的值」再次分叉（R2 家族）。
 *
 * 认 `true`（大小写不敏感）与含 `✓` 的旧手输写法 —— 演示数据用的是 `"true"`/`"false"`。
 */
internal fun isTickCell(cell: String?): Boolean =
    cell != null && (cell.equals("true", ignoreCase = true) || "✓" in cell)

/** 勾选态 → 落库值。与演示数据同款的 `"true"` / `"false"`。 */
internal fun tickCellValue(checked: Boolean): String = if (checked) "true" else "false"

/**
 * 就地翻转明细表某一格，返回新的 `fieldsData`；**解析不出载荷或行列越界时返回 null**
 * （调用方跳过写入，而不是写一份被改坏的载荷）。
 *
 * 列定义沿用**载荷里已有的那份**：它才是这条记录真正用过的列序，
 * 拿 `options` 的顺序当第二真源会在两者不一致时静默换列。
 * 载荷里没有列定义（从没填过表）时不写 —— 那种记录本来也没有行。
 */
internal fun toggleTableCellValue(
    fieldsData: String,
    key: String,
    rowIndex: Int,
    colIndex: Int
): String? {
    val obj = runCatching { Json.decodeFromString<JsonObject>(fieldsData) }.getOrNull() ?: return null
    // compoundRawOf 两种形态都认：生产写入是 JSON 字符串原语，演示/历史数据是嵌套对象。
    val model = parseTable(compoundRawOf(obj[key]))
    val row = model.rows.getOrNull(rowIndex)
    // 载荷解析不出 / 行列越界 / 没有列定义 ⇒ 不写（宁可什么都不做，也不写一份变形载荷）。
    if (row == null || colIndex !in row.indices || model.columns.isEmpty()) return null
    val nextRow = row.toMutableList().also {
        it[colIndex] = tickCellValue(!isTickCell(row[colIndex]))
    }
    val nextRows = model.rows.toMutableList().also { it[rowIndex] = nextRow }
    val encoded = encodeTable(TableModel(model.columns, nextRows))
    return JsonObject(obj.toMutableMap().apply { put(key, JsonPrimitive(encoded)) }).toString()
}

/**
 * 勾选圆圈（明细表的 BOOLEAN 单元格）。样式与清单行的勾圈**逐项对齐**
 * （16dp / 1.5dp 描边 / `Success` 填充 / ✓），免得同屏出现两种勾。
 */
@Composable
internal fun TickCircle(checked: Boolean, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(16.dp)
            .clip(CircleShape)
            .background(if (checked) Success else Color.Transparent)
            .border(1.5.dp, if (checked) Success else MaterialTheme.colorScheme.outline, CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/**
 * 明细表编辑器：**列**由 [tableColumnsOf] 决定（增删列在字段编辑器里做），**行**可增可删；
 * `BOOLEAN` 列渲染成 [TickCircle] 而不是文本框（购物「已买」就是这一列）。
 *
 * 非布尔单元格一律用文本框：载荷里的单元格本来就是**自由文本** —— `FieldValueExtractor`
 * 按行原样存 `json`、详情页也原样显示。做「按列类型的全套专用控件」反而会和这个存储口径
 * 分叉，所以留给单独一轮，不在这里发明第二套语义。
 *
 * 每次改动都整表 [encodeTable] 回写，与 CHECKLIST / MAP 一样以**载荷字符串**存在
 * `fieldsData` 里（`LifeCreateRecordViewModel.buildFieldsData` 的 `else` 分支原样落库），
 * 因此 ViewModel 侧只需要把它从 `EXCLUDED_FROM_FORM` 里移出去。
 */
@Composable
internal fun TableSection(
    cfg: FieldConfig,
    accent: Color,
    state: CreateRecordUiState,
    host: FieldFormHost
) {
    val columns = tableColumnsOf(cfg)
    val rows = parseTable(state.values[cfg.key]).rows
    fun commit(next: List<List<String>>) =
        host.updateValue(cfg.key, encodeTable(TableModel(columns, next)))

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (columns.isEmpty()) {
            // 字段配置没给列定义（options 为空）时，画一张连列名都没有的表没有意义。
            Text(
                stringResource(R.string.life_field_hint_table),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        TableHeaderRow(columns, accent)
        rows.forEachIndexed { rowIndex, row ->
            TableRowEditor(
                row = row,
                columns = columns,
                onCellChange = { colIndex, cell ->
                    val next = rows.toMutableList()
                    val line = next[rowIndex].toMutableList()
                    while (line.size <= colIndex) line += ""
                    line[colIndex] = cell
                    next[rowIndex] = line
                    commit(next)
                },
                onRemove = { commit(rows.filterIndexed { i, _ -> i != rowIndex }) }
            )
        }
        OutlinedButton(
            onClick = { commit(rows + listOf(List(columns.size) { "" })) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.life_row_add), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 表头：列名一行，末列留出删除按钮的宽度以对齐。 */
@Composable
private fun TableHeaderRow(columns: List<TableColumn>, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        columns.forEach { col ->
            Text(
                col.label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.width(28.dp))
    }
}

/** 一行单元格：文本列用文本框、布尔列用勾圈、日期列用日期胶囊 + 行尾删除。 */
@Composable
private fun TableRowEditor(
    row: List<String>,
    columns: List<TableColumn>,
    onCellChange: (colIndex: Int, cell: String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        columns.forEachIndexed { colIndex, col ->
            val cell = row.getOrNull(colIndex)
            when (col.type) {
                FieldType.BOOLEAN -> TickCircle(checked = isTickCell(cell)) {
                    onCellChange(colIndex, tickCellValue(!isTickCell(cell)))
                }
                FieldType.DATE -> TableDateCell(
                    value = cell.orEmpty(),
                    placeholder = col.label,
                    modifier = Modifier.weight(1f)
                ) { onCellChange(colIndex, it) }
                else -> FormTextField(
                    value = cell.orEmpty(),
                    onValueChange = { onCellChange(colIndex, it) },
                    placeholder = col.label,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }
        // **不要**给 IconButton 传 `size(28.dp)`：那会压掉 M3 内部的最小触控尺寸，
        // 让热区缩到 28dp —— 而这是**破坏性**操作（删行）且紧邻可编辑单元格，误触就是丢数据。
        // 用默认形态：视觉 40dp、热区 48dp；图标仍保持 14dp。
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                stringResource(R.string.life_row_remove),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 明细表的日期格：胶囊 + 系统日期选择器。
 *
 * 存 **ISO 文本**（`LocalDate.toString()`）—— 与 DATE 字段的表单形态、演示数据
 * （`"startDate":"2026-10-04"`）同一口径，`DateUtils.parseDateValueOrNull` 也认，
 * 所以它同时还能被 `FieldValueExtractor` / 统计正常读走。
 */
@Composable
private fun TableDateCell(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    PillValue(
        text = date?.format(dateTimePattern()) ?: placeholder,
        onClick = { show = true },
        muted = date == null,
        modifier = modifier
    )
    if (show) {
        LifeDatePickerDialog(
            initial = date,
            onPick = {
                onValueChange(it.toString())
                show = false
            },
            onDismiss = { show = false }
        )
    }
}
