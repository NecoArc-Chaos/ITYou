package com.necoarc.ityou.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.necoarc.ityou.ui.theme.Motion
import com.necoarc.ityou.ui.theme.ShapeCache

/**
 * 「回到顶部并刷新」浮动按钮。
 *
 * 交互设计：单击一次完成两步 —— 先平滑滚回列表顶部，**到达后再触发刷新**。
 * 之所以要在滚动结束后才刷新，而不是两个动作同时发起：
 * 刷新会替换列表内容，若同时滚动会让用户看到内容在滚动途中突然跳变，
 * 且 `animateScrollToItem` 的目标位置在数据替换后可能失效。
 *
 * 视觉：使用 M3E 的 [MediumFloatingActionButton]，配色取
 * `primaryContainer` / `onPrimaryContainer`（成对使用以保证对比度），
 * 与项目内其他浮层元素保持一致的容器色语言。
 *
 * 显隐：由调用方通过 [visible] 控制（滚动超过阈值才出现），
 * 这里统一用 [Motion.scaleInSpec] / [Motion.fadeInSpec] 做弹簧进出场，
 * 与全应用的动效规格保持一致。
 */
@Composable
fun BackToTopRefreshButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = Motion.scaleInSpec) +
            fadeIn(animationSpec = Motion.fadeInSpec),
        exit = scaleOut(animationSpec = Motion.scaleInSpec) +
            fadeOut(animationSpec = Motion.fadeInSpec),
        modifier = modifier
    ) {
        MediumFloatingActionButton(
            onClick = onClick,
            shape = ShapeCache.smooth16,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Outlined.VerticalAlignTop,
                contentDescription = "回到顶部并刷新",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
