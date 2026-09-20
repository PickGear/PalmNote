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
}
