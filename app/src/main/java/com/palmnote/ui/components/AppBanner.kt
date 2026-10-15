package com.palmnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.ui.theme.TypeScale

/**
 * **状态条**：当前处于某种模式时的常驻指示（目前只有「示例模式」）。
 *
 * ## 与「一次性提示」的分工（统一框架）
 *
 * | 性质 | 组件 | 位置 | 可关闭性 |
 * |---|---|---|---|
 * | 反馈 | Snackbar | 底部 | 自动消失、可撤销 |
 * | **状态** | **[AppBanner]（本组件）** | 壳层固定槽位（布局流里） | 可关闭，但只记**本次会话**（进程内存态，重启复现） |
 * | 一次性教学 | 页内提示卡（各页自己渲染） | 相关内容流里 | 可永久关闭，按 id 持久化 |
 * | 阻塞决策 | Dialog | 居中 | 必须选择 |
 *
 * 两条纪律来自竞品（Google Docs 的「查看模式」、Stripe 的「测试模式」）：
 * 1. **状态可收起，但不持久化**（2026-10-15 用户决定：横幅要能关）——关闭叉只是
 *    「本次眼不见」，不是「永久同意」：示例模式还开着，下次冷启动 app 再提醒一次；
 * 2. **不浮动**——浮动会压住页面标题（真机截图上压过），固定槽位只会占薄薄一行。
 *
 * @param text 状态文案（如「示例模式 · 数据为预置示例」）
 * @param accent 状态色（演示模式用品牌青；调用方决定，避免与模块色混淆）
 * @param onClose 关闭叉回调；传 null 则不渲染叉（保持不可关闭形态）
 */
@Composable
fun AppBanner(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(lerp(accent, MaterialTheme.colorScheme.surface, 0.9f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = TypeScale.labelS,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (onClose != null) {
            // 关闭叉：弱化色 + 小圆角点击区（~26dp；不用 IconButton 的 48dp
            // 最小触摸区，保持横幅极薄）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClose)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = lerp(accent, Color.Transparent, 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
