package com.palmnote.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.ui.components.CompactTopAppBar

/**
 * 可复用的长文本文档屏（隐私政策 / 用户协议 / 开源许可等）。
 *
 * 视觉严格对齐 [PrivacyPolicyScreen] / [TermsOfServiceScreen]：
 * Scaffold + CompactTopAppBar + LazyColumn(16.dp / spacedBy(8.dp)) + 末尾 Spacer(32.dp)。
 *
 * @param title 顶部标题（调用方自行 stringResource 解析）。
 * @param lines 正文，逐行渲染（行内不含换行）。
 * @param isHeading 判断某行是否为小节标题（决定加粗标题样式）。
 * @param onNavigateBack 返回回调；同时接管系统返回手势，避免穿透到宿主 Activity。
 * @param verbatimText 可选的原样文本（等宽字体整体渲染，不做分行 / 加粗）；为 null 时不显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    title: String,
    lines: List<String>,
    isHeading: (String) -> Boolean,
    onNavigateBack: () -> Unit = {},
    verbatimText: String? = null
) {
    // 协议覆盖层等宿主依赖返回手势回到上一级，缺失会让返回穿透到 Activity。
    BackHandler(onBack = onNavigateBack)
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    style = if (isHeading(line)) {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (verbatimText != null) {
                item {
                    Text(
                        text = verbatimText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
