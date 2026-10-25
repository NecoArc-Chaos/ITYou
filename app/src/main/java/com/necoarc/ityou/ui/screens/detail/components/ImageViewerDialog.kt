package com.necoarc.ityou.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.necoarc.ityou.ui.theme.ShapeCache

/**
 * 沉浸式手势大图查看器。
 *
 * 功能点：
 * 1. 铺满全屏暗色背景，阻断底层列表手势干扰；
 * 2. 支持双指 Pinch-to-zoom 放大缩放（1.0x ~ 4.5x）与放大后的自由平移；
 * 3. 支持双击快捷复位/放大；
 * 4. 单击空白区域或点击右上角关闭按钮退出；
 * 5. 加载期间呈现居中指示器。
 */
@Composable
fun ImageViewerDialog(
    imageUrl: String?,
    caption: String? = null,
    onDismissRequest: () -> Unit
) {
    if (imageUrl.isNullOrBlank()) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = { onDismissRequest() }
                    )
                }
        ) {
            // 图片主体
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = caption,
                contentScale = ContentScale.Fit,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4.5f)
                            if (scale > 1f) {
                                val maxOffset = 600f * (scale - 1f)
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            )

            // 顶栏关闭按钮
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Surface(
                    shape = ShapeCache.smoothPill,
                    color = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭预览",
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }
            }

            // 底部标题展示
            if (!caption.isNullOrBlank()) {
                Surface(
                    shape = ShapeCache.smooth16,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
