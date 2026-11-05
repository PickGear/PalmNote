package com.palmnote.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.ui.theme.TypeScale

/**
 * 预算预警卡的三行部件（[BudgetAlertBody] 的拆分）：行一主数字、行二金额/遮罩说明、
 * 行三进度条。拆出来除了让主体可读，更是为了四态**定高**——
 *
 * 卡高恒定（2026-11-06 定案）的支点都在这几个部件里：
 * 1. 四段文字的行框全部显式钉死（lineHeight + [BudgetCardLineHeightStyle]），
 *    再加 dp 下限兜底——CJK 字形会回退到更高行框的字体（¥ / 万 / 中文），
 *    不钉的话同一行框随文案内容漂 2px，且部分环境 lineHeight 不生效。
 * 2. 行二隐私态说明与金额对比共占同一个进度胶囊（2026-11-06 真机定案）。
 * 3. 进度胶囊在隐私态只画轨道不画填充——填充比例本身也是信息，遮罩下不能漏。
 */

/** 预算卡四段文字共用的行框裁剪：行框精确等于 lineHeight，不被 CJK 回退字体撑高。 */
internal val BudgetCardLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

/**
 * 行二：金额胶囊**本身就是进度条**（2026-11-06 真机定案——3dp 细条太细不可读，
 * 合并进胶囊）。整宽胶囊 = 轨道（accent 10%），左侧按已用比例填充 accent 25%
 * 色带（超支封顶满格），金额文字叠在其上；隐私态只留轨道 + 遮罩说明（填充
 * 比例本身也是信息，遮罩下不能漏）。
 */
@Composable
internal fun BudgetProgressChip(status: BudgetProgress, accent: Color, amountsHidden: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 「细」的口径：只比内部文字行高（16sp）高一点，比通用分类标签矮一档，
            // 肉眼可辨是进度不是标签（2026-11-06 真机反馈两轮「再细一点」）。
            // 下限 20dp = 隐私态最坏情况——中文说明 CJK 回退行框 ~18px + 上下 1dp，
            // 再低隐私态胶囊会反过来比数据态高、卡高又漂
            .heightIn(min = 20.dp)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.10f))
    ) {
        if (!amountsHidden) {
            // 填充层用 matchParentSize：fillMaxHeight 在纵向滚动（无界高度）里会退化，
            // matchParentSize 等父 Box 按内容定完尺寸后再撑满，不参与父尺寸决定
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((status.usedPercent / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.25f))
                )
            }
        }
        Text(
            text = if (amountsHidden) {
                stringResource(R.string.budget_alert_masked_hint)
            } else {
                stringResource(
                    R.string.budget_alert_amounts,
                    status.spent / 100.0, status.limit / 100.0
                )
            },
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 1.dp)
                .align(Alignment.CenterStart),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = TypeScale.chip,
                lineHeight = 16.sp,
                lineHeightStyle = BudgetCardLineHeightStyle,
                fontFeatureSettings = "tnum"
            ),
            // 隐私态保持安静：灰字压在信息蓝底上；数据态金额用 accent 与主数字同色
            color = if (amountsHidden) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                accent
            },
            maxLines = 1
        )
    }
}
