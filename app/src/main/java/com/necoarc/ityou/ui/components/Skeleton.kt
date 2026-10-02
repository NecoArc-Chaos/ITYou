package com.necoarc.ityou.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.ui.theme.Dimens
import com.necoarc.ityou.ui.theme.ShapeCache

// ======================================================================
// 微光流光 (Shimmer) 基础设施
// ======================================================================

/**
 * 全屏共享的单一动画驱动。
 *
 * 旧实现里 `Modifier.shimmerEffect()` 每调用一次就创建一个
 * `rememberInfiniteTransition`，首屏骨架屏大约有 50 个占位块 ——
 * 也就是 50 个独立动画时钟 + 每帧 50 次渐变对象构造。
 *
 * 现在整屏只保留 **一个** 动画时钟，占位块通过 [value] 在 **绘制阶段** 读取进度，
 * 因此每帧只触发重绘，不触发任何重组与重新测量。
 */
@Stable
class ShimmerState internal constructor(private val progress: State<Float>) {
    internal val value: Float get() = progress.value
}

@Composable
fun rememberShimmerState(): ShimmerState {
    val transition = rememberInfiniteTransition(label = "ITYouShimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ITYouShimmerProgress"
    )
    return remember(progress) { ShimmerState(progress) }
}

/**
 * 在自身范围内绘制一块「圆角 + 流光」的占位。
 *
 * 相比 `clip(shape).background(brush)` 的写法：
 * - 不需要 `clip`，因此**不产生额外的 RenderLayer**；
 * - 渐变的起点在绘制阶段计算，进度值也在绘制阶段读取，不触发重组；
 * - 圆角半径由调用方从 [ShapeCache] 的同一份常量传入，避免与真实控件错位。
 */
@Composable
fun Modifier.shimmerSurface(
    shimmer: ShimmerState,
    cornerRadius: Dp = ShapeCache.radiusMedium
): Modifier {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    return this.drawWithCache {
        val radiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
        val colors = listOf(baseColor, highlightColor, baseColor)
        val width = size.width
        val height = size.height.coerceAtLeast(1f)
        val sweep = (width * 0.9f).coerceAtLeast(96f)

        onDrawBehind {
            // 绘制阶段读取动画进度：只触发重绘，不触发重组
            val progress = shimmer.value
            val startX = -sweep + progress * (width + sweep * 2f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(startX, 0f),
                    end = Offset(startX + sweep, height)
                ),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )
        }
    }
}

// ======================================================================
// 骨架屏基础图元
// ======================================================================

/** 单条骨架线的高度：保证 N 条线 + (N-1) 个间距 恰好铺满 `lineHeight * N`。 */
internal fun skeletonBarHeight(lineHeight: Dp, lines: Int, gapRatio: Float = 0.25f): Dp {
    if (lines <= 0) return lineHeight
    val gap = lineHeight * gapRatio
    return (lineHeight * lines - gap * (lines - 1)) / lines
}

internal fun skeletonBarGap(lineHeight: Dp, gapRatio: Float = 0.25f): Dp = lineHeight * gapRatio

/**
 * 多行文本占位：总高度严格等于 `lineHeight * lines`，
 * 因此放在 [ArticleCardFrame] 的固定高度块中不会产生任何偏差。
 */
@Composable
private fun SkeletonLines(
    shimmer: ShimmerState,
    lines: Int,
    barHeight: Dp,
    barGap: Dp,
    widths: List<Float>,
    cornerRadius: Dp
) {
    // 注意：这里只能 fillMaxWidth。若使用 fillMaxSize，
    // 在纵向约束不受限的父容器中会被撑满整屏（骨架屏块高度必须严格由线条累加得出）。
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(lines) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widths.getOrElse(index) { 1f })
                    .height(barHeight)
                    .shimmerSurface(shimmer, cornerRadius)
            )
            if (index < lines - 1) {
                Spacer(modifier = Modifier.height(barGap))
            }
        }
    }
}

/** 当前主题下某排版样式的行高（dp，已包含系统字号缩放）。 */
@Composable
internal fun rememberLineHeight(style: TextStyle): Dp {
    val density = LocalDensity.current
    return remember(style, density) { lineHeightToDp(density, style) ?: 0.dp }
}

// ======================================================================
// 首页骨架屏
// ======================================================================

private const val SKELETON_CARD_COUNT = 5

/**
 * 首页专属骨架屏。
 *
 * 与真实首页的**结构完全一致**：
 * 分类胶囊行 → 头条大卡 → 文章卡片流；
 * 并且文章卡片使用与真实卡片相同的 [ArticleCardFrame] 与 [ArticleCardMetrics]，
 * 因此行数、右侧缩略图位置、元数据栏高度在加载前后完全对齐。
 *
 * @param windowInsetsPadding 由 Scaffold 的 innerPadding 传入，用于避让系统栏/顶栏，
 * 与真实列表的处理方式保持一致（而不是当成 contentPadding 使用）。
 */
@Composable
fun HomeSkeletonScreen(
    modifier: Modifier = Modifier,
    windowInsetsPadding: PaddingValues = PaddingValues(0.dp)
) {
    val shimmer = rememberShimmerState()
    val metrics = rememberArticleCardMetrics()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(windowInsetsPadding),
        contentPadding = PaddingValues(
            horizontal = Dimens.listHorizontalPadding,
            vertical = Dimens.listVerticalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.listItemSpacing),
        userScrollEnabled = false
    ) {
        item(key = "skeleton_category_row", contentType = FeedItemType.CATEGORY_ROW) {
            SkeletonCategoryRow()
        }

        item(key = "skeleton_hero", contentType = FeedItemType.HERO) {
            HeroCardShell {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmerSurface(shimmer, ShapeCache.radiusHero)
                )
            }
        }

        items(
            count = SKELETON_CARD_COUNT,
            key = { index -> "skeleton_card_$index" },
            contentType = { FeedItemType.ARTICLE }
        ) {
            ArticleCardFrame(
                title = {
                    SkeletonLines(
                        shimmer = shimmer,
                        lines = metrics.titleLines,
                        barHeight = metrics.titleBarHeight,
                        barGap = metrics.titleBarGap,
                        widths = listOf(0.92f, 0.62f),
                        cornerRadius = ShapeCache.radiusTiny
                    )
                },
                summary = {
                    SkeletonLines(
                        shimmer = shimmer,
                        lines = metrics.summaryLines,
                        barHeight = metrics.summaryBarHeight,
                        barGap = metrics.summaryBarGap,
                        widths = listOf(0.98f, 0.55f),
                        cornerRadius = ShapeCache.radiusTiny
                    )
                },
                meta = { SkeletonMetaRow(shimmer = shimmer, metrics = metrics) },
                thumbnail = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shimmerSurface(shimmer, ShapeCache.radiusMedium)
                    )
                }
            )
        }
    }
}

/**
 * 分类胶囊占位。
 *
 * 这里直接渲染**真实禁用状态**的 FilterChip：
 * 分类名在骨架阶段就是已知的，使用真实控件意味着宽度、高度、圆角与字体
 * 与真实分类行完全一致（旧的「固定 64x32 圆角块」永远无法对齐真实胶囊宽度）。
 * 同时采用默认的禁用配色，天然呈现柔和的占位观感，无需额外覆盖颜色。
 */
@Composable
private fun SkeletonCategoryRow() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(ArticleCategory.entries) { category ->
            FilterChip(
                selected = false,
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                shape = ShapeCache.smooth20
            )
        }
    }
}

@Composable
private fun SkeletonMetaRow(
    shimmer: ShimmerState,
    metrics: ArticleCardMetrics
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(metrics.metaBarHeight)
                .shimmerSurface(shimmer, ShapeCache.radiusTiny)
        )
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(metrics.metaBarHeight)
                .shimmerSurface(shimmer, ShapeCache.radiusTiny)
        )
    }
}

// ======================================================================
// 文章详情骨架屏
// ======================================================================

/**
 * 文章详情阅读页骨架屏。
 *
 * 行高全部来自真实排版样式（`headlineLarge` / `bodyLarge` / `labelMedium`），
 * 内边距与块间距来自 [Dimens]，与详情页真实列表保持一致。
 */
@Composable
fun DetailSkeletonScreen(
    modifier: Modifier = Modifier,
    windowInsetsPadding: PaddingValues = PaddingValues(0.dp)
) {
    val shimmer = rememberShimmerState()

    val headlineLine = rememberLineHeight(MaterialTheme.typography.headlineLarge)
    val bodyLine = rememberLineHeight(MaterialTheme.typography.bodyLarge)
    val labelLine = rememberLineHeight(MaterialTheme.typography.labelMedium)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(windowInsetsPadding)
            .padding(
                horizontal = Dimens.detailHorizontalPadding,
                vertical = Dimens.detailVerticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(Dimens.detailBlockSpacing)
    ) {
        // 1. 文章大标题（固定 2 行）
        SkeletonLines(
            shimmer = shimmer,
            lines = 2,
            barHeight = skeletonBarHeight(headlineLine, 2),
            barGap = skeletonBarGap(headlineLine),
            widths = listOf(0.92f, 0.60f),
            cornerRadius = ShapeCache.radiusSmall
        )

        // 2. 作者徽章 + 发布时间
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    // 真实徽章 = labelMedium 行高 + 上下各 2dp 内边距
                    .height(labelLine + 4.dp)
                    .shimmerSurface(shimmer, ShapeCache.radiusTiny)
            )
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(labelLine)
                    .shimmerSurface(shimmer, ShapeCache.radiusTiny)
            )
        }

        // 3. 分割线
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        // 4. 正文段落（与 bodyLarge 行高严格对齐）
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(skeletonBarGap(bodyLine))) {
                SkeletonParagraphLines(shimmer = shimmer, bodyLine = bodyLine)
            }
        }

        // 5. 正文大插图
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .shimmerSurface(shimmer, ShapeCache.radiusXLarge)
        )
    }
}

private const val PARAGRAPH_LINES = 4
private val PARAGRAPH_WIDTHS = listOf(1f, 0.96f, 0.88f, 0.45f)

@Composable
private fun SkeletonParagraphLines(shimmer: ShimmerState, bodyLine: Dp) {
    val barHeight = skeletonBarHeight(bodyLine, PARAGRAPH_LINES)
    repeat(PARAGRAPH_LINES) { index ->
        Box(
            modifier = Modifier
                .fillMaxWidth(PARAGRAPH_WIDTHS.getOrElse(index) { 1f })
                .height(barHeight)
                .shimmerSurface(shimmer, ShapeCache.radiusTiny)
        )
    }
}
