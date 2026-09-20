package com.palmnote.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val BottomSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val DialogShape = RoundedCornerShape(20.dp)
val ButtonShape = RoundedCornerShape(12.dp)

/**
 * §13.3 B3：卡片圆角只允许两个显式常量 —— 列表卡 12dp / 大卡 16dp。
 * 验收（§13.6）：卡片圆角只出现 1 个值（按卡型各一）。
 */
val ListCardShape = RoundedCornerShape(12.dp)
val BigCardShape = RoundedCornerShape(16.dp)
