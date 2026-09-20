package com.palmnote.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 全屏阻断进度对话框：用于**不可中断的写操作**（恢复备份、全量导入、清除数据）执行期间。
 *
 * 与普通 loading 的区别在于语义：这类操作进行到一半被并发访问打断会损坏数据，
 * 所以对话框不可点击关闭、遮罩不可穿透——恢复期间用户唯一能做的事就是等待。
 */
@Composable
fun BlockingProgressDialog(
    message: String,
    detail: String? = null,
) {
    Dialog(
        onDismissRequest = { /* 不可取消：写操作中途停下 = 数据损坏 */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        NoDialogWindowAnimation()
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(18.dp))
                Text(message, fontWeight = FontWeight.Bold)
                if (detail != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
