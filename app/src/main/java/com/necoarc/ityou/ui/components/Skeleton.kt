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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
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
 * 首页专属骨架屏布局：与真实 HomeScreen 保持 1:1 精确对齐
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
        // 1. 分类胶囊占位 (FilterChip 高度与 32dp 药丸对齐)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                items(6) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 32.dp)
                            .clip(ShapeCache.smooth20)
                            .shimmerEffect()
                    )
                }
            }
        }

        // 2. 今日焦点大卡片占位 (真实尺寸：fillMaxWidth, height: 220dp, shape: smooth28)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(ShapeCache.smooth28)
                    .shimmerEffect()
            )
        }

        // 3. 文章流卡片占位 (与 ArticleCard 完全一致的 Card 容器与内外间距)
        items(5) {
            Card(
                shape = ShapeCache.smooth24,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        // 标题占位：2 行 (对应 titleMedium 24sp line height)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(18.dp)
                                .clip(ShapeCache.smooth8)
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(18.dp)
                                .clip(ShapeCache.smooth8)
                                .shimmerEffect()
                        )

                        // 摘要占位：2 行 (对应 bodySmall 22sp line height)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.98f)
                                .height(14.dp)
                                .clip(ShapeCache.smooth8)
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(14.dp)
                                .clip(ShapeCache.smooth8)
                                .shimmerEffect()
                        )

                        // 元数据底部栏 (作者 + 时间)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 14.dp)
                                    .clip(ShapeCache.smooth8)
                                    .shimmerEffect()
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 14.dp)
                                    .clip(ShapeCache.smooth8)
                                    .shimmerEffect()
                            )
                        }
                    }

                    // 右侧封面图占位 (真实尺寸：96dp x 72dp, shape: smooth16)
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
 * 文章详情阅读页专属骨架屏布局：与真实 DetailScreen 保持 1:1 对齐
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
        // 1. 文章大标题 (对应 headlineLarge 36sp line height, 两行)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(28.dp)
                    .clip(ShapeCache.smooth12)
                    .shimmerEffect()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(28.dp)
                    .clip(ShapeCache.smooth12)
                    .shimmerEffect()
            )
        }

        // 2. 元数据作者徽章与发布时间 (高度与 DetailScreen 徽章对齐)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 22.dp)
                    .clip(ShapeCache.smooth8)
                    .shimmerEffect()
            )
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 16.dp)
                    .clip(ShapeCache.smooth8)
                    .shimmerEffect()
            )
        }

        // 3. 分割线
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        // 4. 正文段落文字骨架 (对应 bodyLarge 28sp line height)
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(ShapeCache.smooth8)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .height(18.dp)
                        .clip(ShapeCache.smooth8)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(18.dp)
                        .clip(ShapeCache.smooth8)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(18.dp)
                        .clip(ShapeCache.smooth8)
                        .shimmerEffect()
                )
            }
        }

        // 5. 大插图骨架 (大圆角 24dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(ShapeCache.smooth24)
                .shimmerEffect()
        )
    }
}
