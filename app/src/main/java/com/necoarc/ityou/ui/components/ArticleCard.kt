package com.necoarc.ityou.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.ui.theme.ShapeCache

/**
 * 文章卡片的**共享布局骨架**。
 *
 * 真实卡片（[ArticleCard]）与骨架屏（`HomeSkeletonScreen`）都通过它渲染，
 * 并且几何度量全部来自 [rememberArticleCardMetrics] ——
 * 因此「内边距 / 标题行数 / 摘要块高度 / 元数据栏高度 / 右侧缩略图尺寸与位置」
 * 在两处必然是同一份值，结构上不可能再出现错位。
 *
 * @param onClick 为 null 时表示占位状态：不注册点击与缩放动效，也不创建额外图层。
 */
@Composable
internal fun ArticleCardFrame(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    title: @Composable () -> Unit,
    summary: @Composable () -> Unit,
    meta: @Composable () -> Unit,
    thumbnail: @Composable () -> Unit
) {
    val metrics = rememberArticleCardMetrics()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "ArticleCardScale"
    )

    val pressModifier = if (onClick != null) {
        Modifier
            // 缩放读取发生在图层阶段（lambda 内），不会触发逐帧重组
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else {
        Modifier
    }

    Card(
        shape = ShapeCache.smooth24,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(pressModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = metrics.textToThumbGap)
            ) {
                // 标题块：固定 2 行高度
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.titleBlockHeight)
                ) {
                    title()
                }

                Spacer(modifier = Modifier.height(metrics.titleToSummaryGap))

                // 摘要块：固定 2 行高度（即使没有摘要也保留空间，保证列表等高）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.summaryBlockHeight)
                ) {
                    summary()
                }

                Spacer(modifier = Modifier.height(metrics.summaryToMetaGap))

                // 元数据块：固定高度，内容垂直居中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.metaHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    meta()
                }
            }

            Box(
                modifier = Modifier
                    .size(metrics.thumbWidth, metrics.thumbHeight)
                    .clip(ShapeCache.smooth16)
            ) {
                thumbnail()
            }
        }
    }
}

/**
 * 借鉴 PixelPlayer 与 ReadYou 风格的 Expressive 文章流卡片。
 */
@Composable
fun ArticleCard(
    article: Article,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArticleCardFrame(
        modifier = modifier,
        onClick = onClick,
        title = { ArticleTitleText(text = article.title) },
        summary = { ArticleSummaryText(text = article.summary) },
        meta = { ArticleMetaRow(article = article) },
        thumbnail = { ArticleThumbnail(article = article) }
    )
}

@Composable
private fun ArticleTitleText(text: String) {
    val metrics = rememberArticleCardMetrics()
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        // 固定行数：既是等高的前提，也避免短标题与长标题造成卡片高度抖动
        minLines = metrics.titleLines,
        maxLines = metrics.titleLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ArticleSummaryText(text: String) {
    if (text.isEmpty()) return
    val metrics = rememberArticleCardMetrics()
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = metrics.summaryLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ArticleMetaRow(article: Article) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = article.author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (article.publishTime.isNotEmpty()) {
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = article.publishTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }

        if (article.commentCount > 0) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                contentDescription = "评论数",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = "${article.commentCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ArticleThumbnail(article: Article) {
    // 始终铺一层容器色作为底：图片加载中 / 失败 / 缺失时都不会出现空洞，
    // 也不会因为「有图 / 无图」改变卡片高度。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val cover = article.coverImageUrl
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = article.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = article.category.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
