// 「Kit」文件按惯例：本文件只装一类东西（记录预览），文件名带 Kit 后缀，
// 而里面唯一的类叫 RecordPreviewInput —— detekt 的 MatchingDeclarationName 会要求两者同名，
// 与 LifeCreateFieldKit / LifeEditKit 等文件一样用文件级抑制（项目既有惯例）。
@file:Suppress("MatchingDeclarationName")

package com.palmnote.ui.life

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.ui.theme.Spacing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 记录预览（卡片 / 详情 / 填写）：**模板编辑器与记录填写页共用同一份**。
 *
 * ## 为什么抽出来
 *
 * 模板编辑器拿「字段默认值」预览，填写页拿「用户刚填的值」预览 ——
 * 两者回答的其实是同一个问题（**这条记录会变成什么样**），所以必须走同一条渲染路径。
 * 此前「卡片」态是编辑器自己画的一套，与首页真实卡片无关、没有进度字段时还退化成
 * 一句「N 个字段」；现在卡片态调 [LifeRecordCard]、详情态调 [DetailAssembler]，
 * 与真实页面**同源同码**。
 *
 * 填写页只给两态（卡片 / 详情）：它本身就是「填写」态，再预览一遍没有意义。
 */

/** 预览所需的全部输入（打成一包，免得 `RecordPreviewSheet` 参数列超过 detekt 阈值）。 */
internal data class RecordPreviewInput(
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val fields: List<FieldConfig>,
    val values: Map<String, String>,
    val category: String = "",
    val templateId: Long = 0L,
    val description: String = "",
    val isBuiltin: Boolean = false,
    /** 模板的「每年重复」开关：预览也要按同一次序滚动，否则预览与真实读数会不一致。 */
    val repeatYearly: Boolean = false,
    /** 是否提供「填写」态（模板编辑器要，填写页不要）。 */
    val withFillTab: Boolean = true
)

/**
 * 用「字段配置 + 值」合成一次**真实详情页渲染**所需的 [DetailUi]。
 *
 * 走的是生产用的 [DetailAssembler]（不是另写一套拼装），因此预览与详情页不会漂移。
 */
internal fun previewDetailUiOf(
    input: RecordPreviewInput,
    displayName: String,
    context: Context? = null
): DetailUi {
    val fields = input.fields
        .filter { !it.disabled }
        .mapIndexed { index, config -> config.copy(sortOrder = index) }
    val template = LifeTemplate(
        id = input.templateId,
        name = displayName,
        category = input.category,
        icon = input.iconKey,
        color = input.colorHex,
        description = input.description,
        fieldsConfig = Json.encodeToString(fields),
        layoutType = "card",
        availableLayouts = "[]",
        statusFlowConfig = "",
        linkConfig = "",
        isBuiltin = input.isBuiltin,
        repeatYearly = input.repeatYearly
    )
    val fieldsData = buildJsonObject {
        fields.forEach { c ->
            input.values[c.key]?.takeIf { it.isNotBlank() }?.let { put(c.key, JsonPrimitive(it)) }
        }
    }
    val item = LifeItem(templateId = template.id, title = displayName, fieldsData = fieldsData.toString())
    return DetailAssembler.assemble(item = item, template = template, context = context)
}

@Composable
internal fun RecordPreviewSheet(input: RecordPreviewInput, onDismiss: () -> Unit) {
    val displayName = input.name.ifBlank { stringResource(R.string.life_template_untitled) }
    val tabs = buildList {
        add(stringResource(R.string.life_template_preview_card))
        add(stringResource(R.string.life_template_preview_detail))
        if (input.withFillTab) add(stringResource(R.string.life_template_preview_fill))
    }
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    AppSheet(onDismiss = onDismiss) {
        PreviewTabStrip(tabs = tabs, selected = tab, accentHex = input.colorHex) { tab = it }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> PreviewCardBody(input, displayName)
            1 -> PreviewDetailBody(previewDetailUiOf(input, displayName, context))
            else -> PreviewFill(input.fields, identityColor(input.colorHex), context)
        }
    }
}

/** 预览的三段切换条（选中侧用模板身份色）。 */
@Composable
private fun PreviewTabStrip(
    tabs: List<String>,
    selected: Int,
    accentHex: String,
    onSelect: (Int) -> Unit
) {
    val accent = identityColor(accentHex)
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 卡片态：**与首页真实卡片同一条取值路径**（同一个 `LifeRecordCard` + 同一对取值函数）。 */
@Composable
private fun PreviewCardBody(input: RecordPreviewInput, displayName: String) {
    val context = LocalContext.current
    val fields = remember(input.fields) { input.fields.filter { !it.disabled } }
    val configJson = remember(fields) { Json.encodeToString(fields) }
    val dataJson = remember(fields, input.values) {
        buildJsonObject {
            fields.forEach { c ->
                input.values[c.key]?.takeIf { it.isNotBlank() }?.let { put(c.key, JsonPrimitive(it)) }
            }
        }.toString()
    }
    LifeRecordCard(
        iconKey = input.iconKey,
        colorHex = input.colorHex,
        title = displayName,
        subtitle = input.category.takeIf { it.isNotBlank() },
        fields = remember(configJson, dataJson, context) {
            localizedCardFieldValues(context, configJson, dataJson, repeatYearly = input.repeatYearly)
        },
        progress = remember(configJson, dataJson) { cardProgressFraction(configJson, dataJson) }
    )
}

/** 详情态：hero + 指标行 + 结构区分组（全部是详情页的真实组件）。 */
@Composable
private fun PreviewDetailBody(ui: DetailUi) {
    val ctx = ui.ctx
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GroupCard(contentPadding = PaddingValues(0.dp)) {
            LifeHero(ctx = ctx, onToggleChecklist = { _, _ -> })
        }
        MetricRow(ui.metrics)
        ui.groups.forEach { group -> StructureGroupBlock(group = group, accent = ctx.accent) }
    }
}
