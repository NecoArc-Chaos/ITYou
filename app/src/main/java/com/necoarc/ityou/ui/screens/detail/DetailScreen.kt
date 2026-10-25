package com.necoarc.ityou.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necoarc.ityou.data.model.FavoriteArticle
import com.necoarc.ityou.data.repository.FavoriteRepository
import com.necoarc.ityou.data.share.ArticleSharer
import com.necoarc.ityou.ui.components.DetailSkeletonScreen
import com.necoarc.ityou.ui.screens.detail.components.DetailContent
import com.necoarc.ityou.ui.screens.detail.components.ImageViewerDialog
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

    // 收藏状态以仓库为唯一数据源
    val favorites by favoriteRepository.favorites.collectAsStateWithLifecycle()
    val isStarred = favorites.any { it.id == articleId }

    val snackbarHostState = remember { SnackbarHostState() }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // 正文大图手势全屏查看状态
    var previewingImage by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(articleId, articleUrl) {
        viewModel.loadArticleDetail(
            articleId = articleId,
            url = articleUrl,
            previewTitle = previewTitle,
            previewAuthor = previewAuthor,
            previewPubTime = previewPubTime
        )
    }

    val detail = uiState.detail
    val shareTitle = detail?.title?.takeIf { it.isNotBlank() } ?: previewTitle
    val shareUrl = detail?.originalUrl?.takeIf { it.isNotBlank() } ?: articleUrl

    LaunchedEffect(toastMessage) {
        toastMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            toastMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
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
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
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
                        onRelatedArticleClick = onRelatedArticleClick,
                        onImageClick = { url, caption ->
                            previewingImage = url to caption
                        }
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

        // 沉浸式大图手势查看器弹窗
        previewingImage?.let { (url, caption) ->
            ImageViewerDialog(
                imageUrl = url,
                caption = caption,
                onDismissRequest = { previewingImage = null }
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
