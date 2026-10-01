package com.necoarc.ityou.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.necoarc.ityou.ui.theme.ShapeCache

/**
 * MD3E 微光流光骨架屏修饰符 (Shimmer Modifier)
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
        MaterialTheme.colorScheme.surfaceContainerLow,
    )

    return this.background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 400f, translateAnim - 400f),
            end = Offset(translateAnim, translateAnim)
        )
    )
}

/**
 * 首页专属骨架屏布局
 */
@Composable
fun HomeSkeletonScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false
    ) {
        // 分类胶囊占位
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                items(5) {
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 36.dp)
                            .clip(ShapeCache.smooth20)
                            .shimmerEffect()
                    )
                }
            }
        }

        // 今日焦点大卡片占位
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(ShapeCache.smooth28)
                    .shimmerEffect()
            )
        }

        // 列表条目卡片占位
        items(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth24)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(18.dp)
                                .clip(ShapeCache.smooth8)
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(14.dp)
                                .clip(ShapeCache.smooth8)
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 12.dp)
                                    .clip(ShapeCache.smooth8)
                                    .shimmerEffect()
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 12.dp)
                                    .clip(ShapeCache.smooth8)
                                    .shimmerEffect()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 72.dp)
                            .clip(ShapeCache.smooth16)
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

/**
 * 文章详情阅读页专属骨架屏布局
 */
@Composable
fun DetailSkeletonScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 标题骨架
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(30.dp)
                .clip(ShapeCache.smooth12)
                .shimmerEffect()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(26.dp)
                .clip(ShapeCache.smooth12)
                .shimmerEffect()
        )

        // 作者元数据骨架
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 22.dp)
                    .clip(ShapeCache.smooth8)
                    .shimmerEffect()
            )
            Box(
                modifier = Modifier
                    .size(width = 90.dp, height = 16.dp)
                    .clip(ShapeCache.smooth8)
                    .shimmerEffect()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 段落文字骨架
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(ShapeCache.smooth8)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(16.dp)
                        .clip(ShapeCache.smooth8)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(ShapeCache.smooth8)
                        .shimmerEffect()
                )
            }
        }

        // 大图骨架
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(ShapeCache.smooth24)
                .shimmerEffect()
        )
    }
}
