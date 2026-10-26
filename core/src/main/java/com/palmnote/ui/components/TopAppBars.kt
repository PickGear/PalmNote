package com.palmnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    CompactTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        backgroundColor = backgroundColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    windowInsets: WindowInsets = WindowInsets.statusBars
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        // 底色自己画（Modifier.background 立即生效），容器色恒为透明。
        // M3 的 TopAppBar 内部对 containerColor 做**无条件** spring 动画；壁纸模式下
        // colorScheme.background 是 Color.Transparent（透明黑），从 BackgroundLight 插值过去
        // 会经过「低亮度 + 半透明」的中间色 —— 换壁纸 / 开关壁纸那一刻顶栏（含状态栏区域）
        // 会压过一层半透明灰，看起来就是「闪一下暗色」。
        modifier = Modifier.background(backgroundColor),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        windowInsets = windowInsets
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedParameter")
@Composable
fun SecondaryTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        // 同 CompactTopAppBar：容器色恒透明、底色自己画，绕开 M3 对 containerColor 的
        // 无条件 spring 动画（壁纸开关时会经过半透明灰）。
        modifier = Modifier.background(colors.containerColor),
        colors = colors.copy(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedParameter")
@Composable
fun SecondaryTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        modifier = Modifier.background(colors.containerColor),
        colors = colors.copy(containerColor = Color.Transparent),
    )
}
