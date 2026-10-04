package com.necoarc.ityou.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.necoarc.ityou.ui.theme.ShapeCache

/**
 * 中心提示弹窗（成功 / 失败通用）。
 *
 * 为什么用 [BasicAlertDialog]：
 * 它是 Material 3 提供的「裸容器」弹窗 —— 只负责 Dialog 窗口、焦点收敛、
 * 返回键与外部点击关闭，**不预设任何视觉样式**，因此可以完全套用本应用的
 * M3E 容器语言（SurfaceContainer 色阶 + Expressive 圆角），而不会与
 * 系统默认样式打架。
 *
 * 视觉规格：
 * - 容器：`[ShapeCache.smooth28]`（28dp，Hero 级圆角），
 *   比常规 AlertDialog 的 28dp 保持一致的「大圆角」取向；
 * - 颜色：`surfaceContainerHigh`，符合本项目「无分割线、仅靠容器色差分层」的约定；
 * - 内边距：24dp（与 Material 官方 AlertDialog 默认值一致）；
 * - 宽度：官方 [BasicAlertDialog] 已约束 280dp ~ 560dp，此处仅补
 *   `fillMaxWidth` 让容器在「较宽屏幕上」也贴合该约束。
 *
 * @param title 弹窗标题
 * @param message 弹窗正文
 * @param confirmText 确认按钮文案
 * @param icon 标题上方的图标（可选）。用于区分成功 / 失败语义。
 * @param onDismissRequest 点击按钮、点击外部或按返回键时触发
 */
@Composable
fun CenterNoticeDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "知道了",
    icon: (@Composable () -> Unit)? = null
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = DialogProperties(
            // 允许点击外部与返回键关闭：这是一条「通知」，不应强制用户寻找按钮
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = ShapeCache.smooth28,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NoticeDialogPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NoticeDialogItemSpacing)
            ) {
                icon?.invoke()

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = confirmText)
                }
            }
        }
    }
}

/** 弹窗内边距。与 Material 官方 AlertDialog 默认值一致。 */
private val NoticeDialogPadding = PaddingValues(
    start = 24.dp,
    end = 24.dp,
    top = 24.dp,
    bottom = 16.dp
)

/** 弹窗内元素纵向间距。 */
private val NoticeDialogItemSpacing = 12.dp
