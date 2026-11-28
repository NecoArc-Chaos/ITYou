package com.necoarc.ityou.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.necoarc.ityou.data.model.ArticleComment
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import com.necoarc.ityou.data.model.InlineLink
import com.necoarc.ityou.data.model.RelatedArticle
import com.necoarc.ityou.data.remote.ImageQualityHelper
import com.necoarc.ityou.ui.theme.Dimens
import com.necoarc.ityou.ui.theme.LocalHighQualityImage
import com.necoarc.ityou.ui.theme.ShapeCache

@Composable
fun DetailContent(
    detail: ArticleDetail,
    listState: LazyListState,
    windowInsetsPadding: PaddingValues,
    comments: List<ArticleComment>,
    isCommentsLoading: Boolean,
    commentsError: String?,
    expandingCommentIds: Set<String>,
    votingCommentIds: Set<String> = emptySet(),
    onRetryComments: () -> Unit,
    onExpandReplies: (String) -> Unit,
    onWriteCommentClick: () -> Unit = {},
    onReplyComment: (ArticleComment) -> Unit = {},
    onVoteComment: (commentId: String, isSupport: Boolean) -> Unit = { _, _ -> },
    onRelatedArticleClick: (id: String, url: String, title: String, pubTime: String) -> Unit,
    articleFontScale: Float = 1.0f,
    onImageClick: (url: String, caption: String?) -> Unit = { _, _ -> }
) {
    val bodyLargeScaled = rememberScaledTextStyle(MaterialTheme.typography.bodyLarge, articleFontScale)
    val bodyMediumScaled = rememberScaledTextStyle(MaterialTheme.typography.bodyMedium, articleFontScale)
    val heading2Scaled = rememberScaledTextStyle(MaterialTheme.typography.titleLarge, articleFontScale)
    val heading3Scaled = rememberScaledTextStyle(MaterialTheme.typography.titleMedium, articleFontScale)
    val linkColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(windowInsetsPadding),
        contentPadding = PaddingValues(
            horizontal = Dimens.detailHorizontalPadding,
            vertical = Dimens.detailVerticalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.detailBlockSpacing)
    ) {
        // 1. 文章大标题
        item(key = "detail_title", contentType = "title") {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 2. 元数据栏（作者、时间，纯容器胶囊表达，无硬质分割线）
        item(key = "detail_meta", contentType = "meta") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = ShapeCache.smoothPill,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = detail.author,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                if (detail.publishTime.isNotEmpty()) {
                    Surface(
                        shape = ShapeCache.smoothPill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = detail.publishTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        item(key = "detail_meta_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 3. 原生富文本排版流
        itemsIndexed(
            items = detail.contentBlocks,
            key = { index, _ -> "block_$index" },
            contentType = { _, block -> blockContentType(block) }
        ) { _, block ->
            when (block) {
                is ContentBlock.Paragraph -> {
                    val annotated = remember(block.text, block.links, linkColor) {
                        buildLinkedText(block.text, block.links, linkColor)
                    }
                    SelectableText {
                        Text(
                            text = annotated,
                            style = bodyLargeScaled,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is ContentBlock.Heading -> {
                    SelectableText {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = block.text,
                            style = if (block.level == 2) heading2Scaled else heading3Scaled,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is ContentBlock.Image -> {
                    ArticleImageBlock(
                        block = block,
                        onImageClick = {
                            val fullResUrl = ImageQualityHelper.resolveUrl(
                                rawUrl = block.url,
                                highQuality = true,
                                isThumbnail = false
                            ) ?: block.url
                            onImageClick(fullResUrl, block.caption)
                        }
                    )
                }

                is ContentBlock.BlockQuote -> {
                    val annotated = remember(block.text, block.links, linkColor) {
                        buildLinkedText(block.text, block.links, linkColor)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = ShapeCache.smooth16,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        ShapeCache.smoothPill
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            SelectableText {
                                Text(
                                    text = annotated,
                                    style = bodyMediumScaled,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is ContentBlock.ListItemBlock -> {
                    val annotated = remember(block.text, block.links, linkColor) {
                        buildLinkedText(block.text, block.links, linkColor)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (block.orderedIndex != null) {
                            Surface(
                                shape = ShapeCache.smoothPill,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(top = 3.dp)
                            ) {
                                Text(
                                    text = "${block.orderedIndex}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, ShapeCache.smoothPill)
                            )
                        }
                        SelectableText {
                            Text(
                                text = annotated,
                                style = bodyLargeScaled,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                is ContentBlock.CodeBlock -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = ShapeCache.smooth16,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (13f * articleFontScale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(14.dp)
                        )
                    }
                }

                is ContentBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    ShapeCache.smoothPill
                                )
                        )
                    }
                }
            }
        }

        // 4. 相关文章区块
        if (detail.relatedArticles.isNotEmpty()) {
            item(key = "related_header", contentType = "related_header") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.primary, ShapeCache.smooth8)
                    )
                    Text(
                        text = "相关文章",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            itemsIndexed(
                items = detail.relatedArticles,
                key = { _, item -> "related_${item.id}" },
                contentType = { _, _ -> "related_item" }
            ) { _, related ->
                RelatedArticleItem(
                    item = related,
                    onClick = {
                        onRelatedArticleClick(
                            related.id,
                            related.url,
                            related.title,
                            related.publishTime
                        )
                    }
                )
            }
        }

        // 5. 评论区
        item(key = "comments_header", contentType = "comments_header") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.primary, ShapeCache.smooth8)
                    )
                    Text(
                        text = "评论",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (comments.isNotEmpty()) {
                        Text(
                            text = "(${comments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onWriteCommentClick,
                    shape = ShapeCache.smoothPill,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "写评论",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        when {
            isCommentsLoading && comments.isEmpty() -> {
                item(key = "comments_loading", contentType = "comments_loading") {
                    CommentLoadingState()
                }
            }

            commentsError != null && comments.isEmpty() -> {
                item(key = "comments_error", contentType = "comments_error") {
                    CommentErrorState(
                        message = commentsError,
                        onRetry = onRetryComments
                    )
                }
            }

            comments.isNotEmpty() -> {
                itemsIndexed(
                    items = comments,
                    key = { _, comment -> "comment_${comment.id}" },
                    contentType = { _, _ -> "comment_item" }
                ) { _, comment ->
                    CommentItem(
                        comment = comment,
                        isExpandingReplies = comment.id in expandingCommentIds,
                        votingCommentIds = votingCommentIds,
                        onExpandReplies = { onExpandReplies(comment.id) },
                        onReplyClick = onReplyComment,
                        onVoteClick = onVoteComment,
                        onImageClick = { imgUrl -> onImageClick(imgUrl, null) }
                    )
                }
            }

            else -> {
                item(key = "comments_empty", contentType = "comments_empty") {
                    CommentEmptyState()
                }
            }
        }

        item(key = "detail_bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/** 计算评论区标题在 LazyColumn 中的精确索引，供底部浮动工具栏一键定位评论。 */
fun commentsHeaderIndex(detail: ArticleDetail): Int {
    var index = 3 + detail.contentBlocks.size
    if (detail.relatedArticles.isNotEmpty()) {
        index += 1 + detail.relatedArticles.size
    }
    return index
}

@Composable
private fun rememberScaledTextStyle(style: TextStyle, scale: Float): TextStyle =
    remember(style, scale) {
        if (kotlin.math.abs(scale - 1f) < 0.01f) {
            style
        } else {
            style.copy(
                fontSize = style.fontSize * scale,
                lineHeight = style.lineHeight * scale
            )
        }
    }

internal fun buildLinkedText(
    text: String,
    links: List<InlineLink>,
    linkColor: Color
): AnnotatedString {
    if (links.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var searchCursor = 0
        links.forEach { link ->
            val matchIndex = text.indexOf(link.text, startIndex = searchCursor)
            if (matchIndex >= 0) {
                val endIndex = matchIndex + link.text.length
                addLink(
                    LinkAnnotation.Url(
                        url = link.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    ),
                    start = matchIndex,
                    end = endIndex
                )
                searchCursor = endIndex
            }
        }
    }
}

@Composable
fun RelatedArticleItem(
    item: RelatedArticle,
    onClick: () -> Unit
) {
    Card(
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (item.coverImageUrl != null) 10.dp else 0.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.publishTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.publishTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            item.coverImageUrl?.let { rawImg ->
                val highQuality = LocalHighQualityImage.current
                val imageUrl = remember(rawImg, highQuality) {
                    ImageQualityHelper.resolveUrl(rawImg, highQuality = highQuality, isThumbnail = true)
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 72.dp, height = 54.dp)
                        .clip(ShapeCache.smooth12)
                )
            }
        }
    }
}

@Composable
fun ArticleImageBlock(
    block: ContentBlock.Image,
    onImageClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeCache.smooth24)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onImageClick)
        ) {
            val ratio = block.aspectRatio
            val imageModifier = if (ratio != null && ratio > 0.05f && ratio < 20f) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = Dimens.detailImageMinHeight,
                        max = Dimens.detailImageMaxHeight
                    )
            }

            val highQuality = LocalHighQualityImage.current
            val imageUrl = remember(block.url, highQuality) {
                ImageQualityHelper.resolveUrl(
                    rawUrl = block.url,
                    highQuality = highQuality,
                    isThumbnail = false
                )
            }
            AsyncImage(
                model = imageUrl,
                contentDescription = block.caption,
                contentScale = ContentScale.FillWidth,
                modifier = imageModifier
            )
        }

        block.caption?.let { caption ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun SelectableText(content: @Composable () -> Unit) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

fun blockContentType(block: ContentBlock): String = when (block) {
    is ContentBlock.Paragraph -> "paragraph"
    is ContentBlock.Heading -> "heading"
    is ContentBlock.Image -> "image"
    is ContentBlock.BlockQuote -> "quote"
    is ContentBlock.ListItemBlock -> "list_item"
    is ContentBlock.CodeBlock -> "code"
    is ContentBlock.Divider -> "divider"
}
