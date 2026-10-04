package com.necoarc.ityou.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
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
import com.necoarc.ityou.data.model.ArticleComment
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import com.necoarc.ityou.data.model.FavoriteArticle
import com.necoarc.ityou.data.model.RelatedArticle
import com.necoarc.ityou.data.repository.FavoriteRepository
import com.necoarc.ityou.data.share.ArticleSharer
import com.necoarc.ityou.ui.components.CommentEmojiText
import com.necoarc.ityou.ui.components.DetailSkeletonScreen
import com.necoarc.ityou.ui.theme.Dimens
import com.necoarc.ityou.ui.theme.Motion
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
    val context = LocalContext.current
    val favoriteRepository = remember(context) { FavoriteRepository.getInstance(context) }
    val listState = rememberLazyListState()

    // 收藏状态以仓库为**唯一数据源**：不再用本地 remember 变量，
    // 否则退出页面即丢失，且列表页与详情页会显示不一致。
    val favorites by favoriteRepository.favorites.collectAsStateWithLifecycle()
    val isStarred = favorites.any { it.id == articleId }

    // 收藏/取消的反馈提示。用 Snackbar 而非 Toast：
    // Snackbar 跟随主题着色与形状语言，且在本页已经有 Scaffold 承载。
    val snackbarHostState = remember { SnackbarHostState() }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(articleId, articleUrl) {
        viewModel.loadArticleDetail(
            articleId = articleId,
            url = articleUrl,
            previewTitle = previewTitle,
            previewAuthor = previewAuthor,
            previewPubTime = previewPubTime
        )
    }

    // 分享 / 收藏优先使用详情加载后的真实数据，未加载完时回退到列表页传入的预览数据，
    // 这样即使详情还在加载，顶栏按钮也是可用的。
    val detail = uiState.detail
    val shareTitle = detail?.title?.takeIf { it.isNotBlank() } ?: previewTitle
    val shareUrl = detail?.originalUrl?.takeIf { it.isNotBlank() } ?: articleUrl

    // 先赋值再消费，避免 LaunchedEffect 因状态被提前清空而以 null 重启
    LaunchedEffect(toastMessage) {
        toastMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            toastMessage = null
        }
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
                    IconButton(
                        onClick = {
                            // 收藏项以 id 为主键，id 为空时不写入，避免产生无法取消的幽灵数据
                            if (articleId.isNotBlank()) {
                                val nowFavorited = favoriteRepository.toggleFavorite(
                                    FavoriteArticle(
                                        id = articleId,
                                        title = shareTitle,
                                        author = detail?.author ?: previewAuthor,
                                        pubTime = detail?.publishTime ?: previewPubTime,
                                        url = shareUrl,
                                        favoritedAt = System.currentTimeMillis()
                                    )
                                )
                                toastMessage = if (nowFavorited) "已加入收藏" else "已取消收藏"
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isStarred) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isStarred) "取消收藏" else "收藏",
                            tint = if (isStarred) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            if (shareUrl.isNotBlank()) {
                                ArticleSharer.share(
                                    context = context,
                                    title = shareTitle,
                                    url = shareUrl
                                )
                            } else {
                                toastMessage = "链接尚未就绪，请稍后再试"
                            }
                        }
                    ) {
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                    comments = uiState.comments,
                    isCommentsLoading = uiState.isCommentsLoading,
                    commentsError = uiState.commentsError,
                    expandingCommentIds = uiState.expandingCommentIds,
                    onRetryComments = viewModel::retryComments,
                    onExpandReplies = viewModel::expandCommentReplies,
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
    comments: List<ArticleComment>,
    isCommentsLoading: Boolean,
    commentsError: String?,
    expandingCommentIds: Set<String>,
    onRetryComments: () -> Unit,
    onExpandReplies: (String) -> Unit,
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

        // 5. 评论区
        item(key = "comments_header", contentType = "comments_header") {
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
                        onExpandReplies = { onExpandReplies(comment.id) }
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
private fun CommentItem(
    comment: ArticleComment,
    isExpandingReplies: Boolean = false,
    onExpandReplies: () -> Unit = {}
) {
    // 楼中楼默认折叠，点击「展开 N 条回复」才渲染，避免长评论串一次性铺满
    var repliesExpanded by remember(comment.id) { mutableStateOf(false) }

    // 剩余回复加载完成后（remainingReplyCount 由 >0 变为 0）自动展开，
    // 避免用户加载完还要再点一次。用 previousRemaining 记录上一次的值，
    // 保证「首次进入就无剩余回复」的场景仍保持默认折叠。
    var previousRemaining by remember(comment.id) { mutableStateOf(comment.remainingReplyCount) }
    LaunchedEffect(comment.id, comment.remainingReplyCount) {
        val current = comment.remainingReplyCount
        if (previousRemaining > 0 && current == 0 && comment.replies.isNotEmpty()) {
            repliesExpanded = true
        }
        previousRemaining = current
    }

    Surface(
        shape = ShapeCache.smooth16,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            CommentRow(comment = comment)

            // 楼中楼：有回复时展示折叠入口
            if (comment.replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp)
                ) {
                    ReplyToggleButton(
                        expanded = repliesExpanded,
                        count = comment.replies.size,
                        onClick = { repliesExpanded = !repliesExpanded }
                    )

                    AnimatedVisibility(
                        visible = repliesExpanded,
                        enter = expandVertically(animationSpec = Motion.expandSize) +
                            fadeIn(animationSpec = Motion.fadeInSpec),
                        exit = shrinkVertically(animationSpec = Motion.expandSize) +
                            fadeOut(animationSpec = Motion.fadeInSpec)
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            comment.replies.forEach { reply ->
                                Surface(
                                    shape = ShapeCache.smooth12,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Box(modifier = Modifier.padding(10.dp)) {
                                        CommentRow(comment = reply, compact = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 服务端提示仍有更多未内联回复：点击按需加载
            if (comment.remainingReplyCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.padding(start = 12.dp)) {
                    if (isExpandingReplies) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "加载回复中…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        Surface(
                            shape = ShapeCache.smoothPill,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable(
                                enabled = !isExpandingReplies,
                                onClick = onExpandReplies
                            )
                        ) {
                            Text(
                                text = "展开另外 ${comment.remainingReplyCount} 条回复",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: ArticleComment, compact: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        // 头像
        val avatarSize = if (compact) 26.dp else 36.dp
        if (comment.avatarUrl != null) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = comment.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(ShapeCache.smoothPill)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(ShapeCache.smoothPill)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.author.take(1),
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            // 昵称 + 楼层
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.author,
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (comment.floor.isNotEmpty()) {
                    Text(
                        text = comment.floor,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 正文（可选中复制）
            if (comment.content.isNotEmpty() || comment.replyToAuthor != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val bodyStyle = if (compact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                }
                SelectionContainer {
                    if (comment.replyToAuthor != null) {
                        // 含「回复 @某人」前缀：前缀用纯文本，正文单独渲染以支持表情内联
                        Row {
                            Text(
                                text = "回复 @${comment.replyToAuthor}：",
                                style = bodyStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CommentEmojiText(
                                text = comment.content,
                                style = bodyStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        CommentEmojiText(
                            text = comment.content,
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 元信息：时间 / 点赞 / 反对
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (comment.location.isNotEmpty() && !compact) {
                    Text(
                        text = comment.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (comment.publishTime.isNotEmpty()) {
                    Text(
                        text = comment.publishTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (comment.supportCount > 0) {
                    Text(
                        text = "赞 ${comment.supportCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (comment.againstCount > 0 && !compact) {
                    Text(
                        text = "踩 ${comment.againstCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyToggleButton(
    expanded: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        // 文字在「展开 N 条回复 / 收起回复」间做淡入淡出切换，
        // 避免文字长度突变造成的视觉跳变
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(animationSpec = Motion.fadeInSpec) togetherWith
                    fadeOut(animationSpec = Motion.fadeInSpec)
            },
            label = "replyToggleLabel"
        ) { isExpanded ->
            Text(
                text = if (isExpanded) "收起回复" else "展开 $count 条回复",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun CommentLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "评论加载中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun CommentErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "评论加载失败",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onRetry,
            shape = ShapeCache.smoothPill
        ) {
            Text(text = "重试", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CommentEmptyState() {
    Text(
        text = "暂无评论",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        textAlign = TextAlign.Center
    )
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
