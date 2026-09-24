package com.palmnote.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * §11.3 五级字号唯一真源（P0.5 / §13.3 B1）。
 * display 34/28/24 · headline 22/20/18 · title 18/16/14 · body 16/14/12 · label 14/12/10
 * 允许值全集 = {10, 12, 14, 16, 18, 20, 22, 24, 28, 34}sp。
 * 验收（§13.6）：改动文件里 `fontSize =` 字面量 0 新增 —— 新代码一律引这里。
 */
object TypeScale {
    val displayL: TextUnit = 34.sp
    val displayM: TextUnit = 28.sp
    val displayS: TextUnit = 24.sp
    val headlineL: TextUnit = 22.sp
    val headlineM: TextUnit = 20.sp
    val headlineS: TextUnit = 18.sp
    val titleL: TextUnit = 18.sp
    val titleM: TextUnit = 16.sp
    val titleS: TextUnit = 14.sp
    val bodyL: TextUnit = 16.sp
    val bodyM: TextUnit = 14.sp
    val bodyS: TextUnit = 12.sp
    val labelL: TextUnit = 14.sp
    val labelM: TextUnit = 12.sp
    val labelS: TextUnit = 10.sp

    // ───────── 设计稿实测档（2026-09-22）─────────
    // 上面的五级是全 App 通用档；下面这些是**详情页设计稿里逐张量出来的值**，
    // 不在五级全集里（15 / 13 / 12.5 / 11 / 10.5 / 9.5）。
    // 依据「规范文字与设计稿冲突时**以设计稿为准**」，这里按实测值补档，
    // 仍然走令牌（不写 `fontSize =` 字面量），避免各处各写一套。
    /** 天数巨字（P2 天数型英雄区，设计稿 dtl_07「87」实测）——**全 App 唯一一处 44sp**。 */
    val dayHero: TextUnit = 44.sp
    /** ② 指标行的值（15sp / 700）。 */
    val metricValue: TextUnit = 15.sp
    /** 巨字右侧的单位（「/ ¥300,000」13sp）。 */
    val heroUnit: TextUnit = 13.sp
    /** ③ 结构区的正文与表格右值（12.5sp）。 */
    val bodyRead: TextUnit = 12.5.sp
    /** 英雄区左右小注（11sp）。 */
    val note: TextUnit = 11.sp
    /** 端标 chip 文字（10.5sp）。 */
    val chip: TextUnit = 10.5.sp
    /** ④ 页脚（9.5sp，最小一档）。 */
    val footer: TextUnit = 9.5.sp
}
