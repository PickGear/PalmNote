package com.palmnote.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 关闭所在 Dialog 窗口的系统层进入/退出动画。
 *
 * Compose 的 Dialog 窗口会继承主题解析出的 windowAnimations（MIUI 上为自底向上滑入），
 * 与 Compose 自身的内容动画叠加后，弹窗会偶发"从底部滑出"的观感。
 * 必须在 Dialog / AlertDialog / DatePickerDialog 等窗口内容的组合内调用；
 * ModalBottomSheet 从底部滑入是设计行为，不要调用本函数。
 */
@Composable
fun NoDialogWindowAnimation() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.setWindowAnimations(0)
        onDispose { }
    }
}
