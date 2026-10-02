package com.necoarc.ityou.ui.screens.detail

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import com.necoarc.ityou.data.model.RelatedArticle
import com.necoarc.ityou.ui.components.DetailSkeletonScreen
import com.necoarc.ityou.ui.theme.Dimens
import com.necoarc.ityou.ui.theme.ShapeCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    articleId: String,
    articleUrl: String,
    previewTitle: String = "",
    previewAuthor: String = "",
    previewPubTime: String = "",
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRelatedArticleClick: (id: String, url: String, title: String, pubTime: String) -> Unit = { _, _, _, _ -> },
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()

    var isStarred by remember { mutableStateOf(false) }

    LaunchedEffect(articleId, articleUrl) {
        viewModel.loadArticleDetail(
            articleId = articleId,
            url = articleUrl,
            previewTitle = previewTitle,
            previewAuthor = previewAuthor,
            previewPubTime = previewPubTime
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (articleUrl.isNotEmpty()) {
                        IconButton(onClick = { uriHandler.openUri(articleUrl) }) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = "在浏览器中打开"
                            )
                        }
                    }
                    IconButton(onClick = { isStarred = !isStarred }) {
                        Icon(
                            imageVector = if (isStarred) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (isStarred) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "分享"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        val detail = uiState.detail
        when {
            uiState.showSkeleton -> {
                DetailSkeletonScreen(windowInsetsPadding = innerPadding)
            }

            detail != null -> {
                DetailContent(
                    detail = detail,
                    listState = listState,
                    windowInsetsPadding = innerPadding,
                    onRelatedArticleClick = onRelatedArticleClick
                )
            }

            uiState.showError -> {
                DetailErrorState(
                    message = uiState.errorMessage.orEmpty(),
                    canOpenInBrowser = articleUrl.isNotEmpty(),
                    onOpenInBrowser = { uriHandler.openUri(articleUrl) },
                    onRetry = viewModel::retry,
                    windowInsetsPadding = innerPadding
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: ArticleDetail,
    listState: androidx.compose.foundation.lazy.LazyListState,
    windowInsetsPadding: PaddingValues,
    onRelatedArticleClick: (id: String, url: String, title: String, pubTime: String) -> Unit
) {
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

        // 2. 元数据栏（作者、时间）
        item(key = "detail_meta", contentType = "meta") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = ShapeCache.smooth8,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = detail.author,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (detail.publishTime.isNotEmpty()) {
                    Text(
                        text = detail.publishTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item(key = "detail_divider", contentType = "divider") {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )
        }

        // 3. 原生富文本排版流
        itemsIndexed(
            items = detail.contentBlocks,
            key = { index, _ -> "block_$index" },
            contentType = { _, block -> blockContentType(block) }
        ) { _, block ->
            when (block) {
                is ContentBlock.Paragraph -> {
                    SelectableText {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is ContentBlock.Heading -> {
                    SelectableText {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = block.text,
                            style = if (block.level == 2) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is ContentBlock.Image -> {
                    ArticleImageBlock(block = block)
                }

                is ContentBlock.BlockQuote -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
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
                                        ShapeCache.smooth8
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            SelectableText {
                                Text(
                                    text = block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(14.dp)
                        )
                    }
                }

                is ContentBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
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

        item(key = "detail_bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun RelatedArticleItem(
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

            item.coverImageUrl?.let { imageUrl ->
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
private fun SelectableText(content: @Composable () -> Unit) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun ArticleImageBlock(block: ContentBlock.Image) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeCache.smooth24)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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

            AsyncImage(
                model = block.url,
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
private fun DetailErrorState(
    message: String,
    canOpenInBrowser: Boolean,
    onOpenInBrowser: () -> Unit,
    onRetry: () -> Unit,
    windowInsetsPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(windowInsetsPadding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "正文加载失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry, shape = ShapeCache.smoothPill) {
                    Text(text = "重试", style = MaterialTheme.typography.labelLarge)
                }
                if (canOpenInBrowser) {
                    Button(onClick = onOpenInBrowser, shape = ShapeCache.smoothPill) {
                        Text(text = "浏览器打开", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun blockContentType(block: ContentBlock): String = when (block) {
    is ContentBlock.Paragraph -> "paragraph"
    is ContentBlock.Heading -> "heading"
    is ContentBlock.Image -> "image"
    is ContentBlock.BlockQuote -> "quote"
    is ContentBlock.CodeBlock -> "code"
    is ContentBlock.Divider -> "divider"
}
