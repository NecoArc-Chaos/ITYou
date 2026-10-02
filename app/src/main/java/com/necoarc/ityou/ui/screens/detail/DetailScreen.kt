package com.necoarc.ityou.ui.screens.detail

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
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
                    windowInsetsPadding = innerPadding
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
    windowInsetsPadding: PaddingValues
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
        //
        // 性能说明：SelectionContainer 只包裹「文本类」块，而不是整个 LazyColumn。
        // 用 SelectionContainer 包住长列表是官方文档明确提示的性能陷阱：
        // 它需要为整棵子树注册选区，会削弱 LazyColumn 的按需组合与回收能力。
        // 代价是跨段落选择文本不再可行（在单个段落内选择不受影响）。
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
                                // IntrinsicSize.Min：让左侧 4dp 引用竖线始终与文字等高
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

        item(key = "detail_bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(48.dp))
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

/**
 * 正文图片块。
 *
 * 关键优化：IT之家 正文 `<img>` 带 `w` / `h` 属性，解析后可以**提前确定宽高比**，
 * 于是图片在加载前就占好位，滚动时不会出现「图片加载完成后整篇正文下移」的回流；
 * 对无法确定宽高比的图片，则给出高度上限，避免 Coil 按原图尺寸解码超大图
 * （这是详情页大量图片滚动时卡顿与内存抖动的主要来源）。
 */
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
