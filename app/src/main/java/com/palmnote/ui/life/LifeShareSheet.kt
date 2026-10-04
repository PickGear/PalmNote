package com.palmnote.ui.life

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记录分享图卡（详情页顶栏「分享」）。
 *
 * ## 设计取舍：**卡片本身就是截图对象**
 *
 * 弹出的是"将要分享的那张图"的**真实预览**，而不是先问"要分享吗"再生成 ——
 * 用户点分享前就能看清会发出什么（这也是本模块预览三态的一贯做法）。
 * 截图直接录这张卡片的绘制内容（[recordInto]），不存在"预览与产物不一致"的可能。
 *
 * ## 卡面内容：复用首页卡片
 *
 * 卡面用 [LifeRecordCard]（与首页卡**同一个组件、同一套取值**），所以：
 * 卡片上显示哪些字段（`showInCard`）、方向措辞（还有 / 已过）、进度条、
 * 「每年重复」的滚动口径 —— 全都与用户在主界面看到的一致，不需要第二套规则。
 * 外面只加一条身份色条与一行落款。
 */
@Composable
internal fun LifeShareSheet(ui: DetailUi, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.life_share_sheet_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.md))
            Box(modifier = Modifier.recordInto(layer)) { LifeShareCard(ui) }
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = {
                    scope.launch {
                        val ok = runCatching {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            val fileName = lifeShareFileName("item", System.currentTimeMillis())
                            val file = withContext(Dispatchers.IO) { saveSharePng(context, bitmap, fileName) }
                            sharePng(context, file)
                        }.isSuccess
                        Toast.makeText(
                            context,
                            context.getString(
                                if (ok) R.string.life_share_ready else R.string.life_share_failed
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(stringResource(R.string.life_share_action))
            }
        }
    }
}

/** 分享卡面：身份色条 + 首页同款卡片 + 落款。宽度固定，避免不同内容产生不同尺寸的图。 */
@Composable
private fun LifeShareCard(ui: DetailUi) {
    val ctx = ui.ctx
    val progressCfg = ctx.configs.firstOrNull { it.showAsProgress }
    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                MaterialTheme.shapes.large
            )
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ctx.accent)
        )
        Spacer(Modifier.height(Spacing.sm))
        LifeRecordCard(
            iconKey = ctx.template.icon,
            colorHex = ctx.template.color,
            title = ctx.item.title,
            fields = cardFieldValues(ctx.configs, ctx.obj, ctx.today),
            progress = ctx.progressOf(progressCfg)?.fraction,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.life_share_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
