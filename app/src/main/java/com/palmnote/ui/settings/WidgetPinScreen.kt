package com.palmnote.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.palmnote.app.R
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.widget.WidgetPin

/**
 * 桌面小组件目录：应用内直接发起「添加到桌面」（系统确认框），
 * 并显示各组件当前在桌面上的绑定数量。数量在系统确认框返回后（ON_RESUME）自动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPinScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isMiui = remember { WidgetPin.isMiuiHome(context) }

    var snapshot by remember { mutableStateOf(WidgetPin.snapshot(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) snapshot = WidgetPin.snapshot(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_widget_title),
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                WidgetPinCard(snapshot)
                WidgetPinFooter(snapshot, isMiui)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 目录卡片：9 个组件逐行（预览缩略图 + 名称/说明 + 绑定数 + 添加按钮），行间细分隔线。 */
@Composable
private fun WidgetPinCard(snapshot: WidgetPin.Snapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            WidgetPin.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                WidgetPinRow(entry = entry, snapshot = snapshot)
            }
        }
    }
}

/** 目录里的一行：预览缩略图 + 名称/说明 + 当前绑定数 + 「添加到桌面」按钮。 */
@Composable
private fun WidgetPinRow(entry: WidgetPin.Entry, snapshot: WidgetPin.Snapshot) {
    val bound = snapshot.boundCount(entry)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(entry.previewImageRes),
            contentDescription = null,
            modifier = Modifier
                .width(84.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillBounds
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(entry.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(entry.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        WidgetPinActions(entry, snapshot, bound)
    }
}

/** 行尾操作列：当前绑定数 + 「添加到桌面」按钮（成功/失败都有 Toast 反馈）。 */
@Composable
private fun WidgetPinActions(entry: WidgetPin.Entry, snapshot: WidgetPin.Snapshot, bound: Int) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (bound == 0) {
                stringResource(R.string.widget_pin_none)
            } else {
                stringResource(R.string.widget_pin_count, bound)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            enabled = snapshot.pinSupported,
            onClick = {
                // 成功/失败都给反馈：MIUI 授权后是直接落桌（无确认框），
                // 原生桌面则还要在确认框里点一下——文案用中性措辞两头都通
                if (WidgetPin.requestPin(context, entry)) {
                    android.widget.Toast.makeText(
                        context,
                        R.string.widget_pin_requested,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        R.string.widget_pin_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ) {
            Text(stringResource(R.string.widget_pin_add))
        }
    }
}

/** 页脚说明：通道提示 + MIUI 的权限引导（pin 确认框被「后台弹出界面」拦掉的已知坑）。 */
@Composable
private fun WidgetPinFooter(snapshot: WidgetPin.Snapshot, isMiui: Boolean) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            if (snapshot.pinSupported) {
                stringResource(R.string.widget_pin_tip)
            } else {
                stringResource(R.string.widget_pin_unsupported_tip)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isMiui) {
            Text(
                stringResource(R.string.widget_pin_miui_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val context = LocalContext.current
            TextButton(
                contentPadding = PaddingValues(horizontal = 8.dp),
                onClick = {
                    // MIUI 的「桌面快捷方式」开关不在任何公开 API 里，只能带用户去应用详情页手动开
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    )
                }
            ) {
                Text(stringResource(R.string.widget_pin_open_settings))
            }
        }
    }
}
