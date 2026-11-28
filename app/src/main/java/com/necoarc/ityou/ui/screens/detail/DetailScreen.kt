package com.necoarc.ityou.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necoarc.ityou.data.model.FavoriteArticle
import com.necoarc.ityou.data.repository.AuthRepository
import com.necoarc.ityou.data.repository.FavoriteRepository
import com.necoarc.ityou.data.repository.SettingsRepository
import com.necoarc.ityou.data.share.ArticleSharer
import com.necoarc.ityou.ui.components.DetailSkeletonScreen
import com.necoarc.ityou.ui.screens.detail.components.CommentComposerSheet
import com.necoarc.ityou.ui.screens.detail.components.DetailContent
import com.necoarc.ityou.ui.screens.detail.components.ImageViewerDialog
import com.necoarc.ityou.ui.screens.detail.components.commentsHeaderIndex
import com.necoarc.ityou.ui.theme.ShapeCache
import kotlinx.coroutines.launch

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
    onLoginClick: () -> Unit = {},
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val favoriteRepository = remember(context) { FavoriteRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val authRepository = remember(context) { AuthRepository.getInstance(context) }
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    val authState by authRepository.authState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val favorites by favoriteRepository.favorites.collectAsStateWithLifecycle()
    val isStarred = favorites.any { it.id == articleId }

    val snackbarHostState = remember { SnackbarHostState() }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showFontControls by remember { mutableStateOf(false) }

    // 未登录操作引导与登录完成后的自动续接动作
    var showLoginPromptDialog by remember { mutableStateOf(false) }
    var pendingAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 正文及评论附图手势全屏查看状态
    var previewingImage by remember { mutableStateOf<Pair<String, String?>?>(null) }

    fun requireAuth(action: () -> Unit) {
        if (authState.isLoggedIn) {
            action()
        } else {
            pendingAuthAction = action
            showLoginPromptDialog = true
        }
    }

    // 从登录页成功登录返回后，自动执行刚刚被拦截的写评论/回复/点赞操作
    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            showLoginPromptDialog = false
            pendingAuthAction?.let { action ->
                pendingAuthAction = null
                action()
            }
        }
    }

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

    val actionMessage = uiState.actionMessage
    LaunchedEffect(actionMessage) {
        actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message)
            viewModel.consumeActionMessage()
        }
    }

    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val toolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )
    val showScrolledTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 1 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    scrollBehavior = topAppBarScrollBehavior,
                    title = {
                        AnimatedVisibility(
                            visible = showScrolledTitle && shareTitle.isNotBlank(),
                            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())
                        ) {
                            Text(
                                text = shareTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBackClick,
                            shape = ShapeCache.smoothPill,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        if (articleUrl.isNotEmpty()) {
                            FilledTonalIconButton(
                                onClick = { uriHandler.openUri(articleUrl) },
                                shape = ShapeCache.smoothPill,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInBrowser,
                                    contentDescription = "在浏览器中打开",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                if (detail == null) return@Scaffold
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 快捷字号调节胶囊浮层
                    AnimatedVisibility(
                        visible = showFontControls,
                        enter = scaleIn(
                            initialScale = 0.9f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                        ) + fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = scaleOut(
                            targetScale = 0.9f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                        ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())
                    ) {
                        Surface(
                            shape = ShapeCache.smoothPill,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        settingsRepository.setArticleFontScale(settings.articleFontScale - 0.10f)
                                    },
                                    enabled = settings.articleFontScale > SettingsRepository.MIN_FONT_SCALE
                                ) {
                                    Text("A-", fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { settingsRepository.setArticleFontScale(1.0f) }
                                ) {
                                    Text("${(settings.articleFontScale * 100).toInt()}%")
                                }
                                TextButton(
                                    onClick = {
                                        settingsRepository.setArticleFontScale(settings.articleFontScale + 0.10f)
                                    },
                                    enabled = settings.articleFontScale < SettingsRepository.MAX_FONT_SCALE
                                ) {
                                    Text("A+", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // MD3E 底部悬浮工具栏：收藏、写评论、跳评论、字号、分享
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                            toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            toolbarContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        scrollBehavior = toolbarScrollBehavior
                    ) {
                        IconButton(
                            onClick = {
                                if (articleId.isNotBlank()) {
                                    val nowFavorited = favoriteRepository.toggleFavorite(
                                        FavoriteArticle(
                                            id = articleId,
                                            title = shareTitle,
                                            author = detail.author.ifBlank { previewAuthor },
                                            pubTime = detail.publishTime.ifBlank { previewPubTime },
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
                                contentDescription = if (isStarred) "取消收藏" else "收藏"
                            )
                        }

                        IconButton(
                            onClick = {
                                requireAuth { viewModel.openComposer(replyTarget = null) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "发表评论"
                            )
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    runCatching {
                                        listState.animateScrollToItem(commentsHeaderIndex(detail))
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Chat,
                                contentDescription = "跳转评论区"
                            )
                        }

                        IconButton(
                            onClick = { showFontControls = !showFontControls }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatSize,
                                contentDescription = "调节字号"
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
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            modifier = Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .nestedScroll(toolbarScrollBehavior)
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
                        votingCommentIds = uiState.votingCommentIds,
                        onRetryComments = viewModel::retryComments,
                        onExpandReplies = viewModel::expandCommentReplies,
                        onWriteCommentClick = {
                            requireAuth { viewModel.openComposer(replyTarget = null) }
                        },
                        onReplyComment = { target ->
                            requireAuth { viewModel.openComposer(replyTarget = target) }
                        },
                        onVoteComment = { commentId, isSupport ->
                            requireAuth { viewModel.toggleVote(commentId, isSupport) }
                        },
                        onRelatedArticleClick = onRelatedArticleClick,
                        articleFontScale = settings.articleFontScale,
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

        // 评论编辑器底部抽屉（支持图文混排 + 表情选择）
        if (uiState.isComposerOpen) {
            CommentComposerSheet(
                replyTarget = uiState.replyTarget,
                isSubmitting = uiState.isSubmittingComment,
                submittingStatus = uiState.submittingStatus,
                onDismissRequest = viewModel::closeComposer,
                onSubmit = { text, images ->
                    viewModel.submitComment(
                        text = text,
                        images = images
                    )
                }
            )
        }

        // 未登录引导弹窗（MD3E SurfaceContainerHigh + 大圆角）
        if (showLoginPromptDialog) {
            BasicAlertDialog(
                onDismissRequest = {
                    showLoginPromptDialog = false
                    pendingAuthAction = null
                }
            ) {
                Surface(
                    shape = ShapeCache.smooth28,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = ShapeCache.smoothPill,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Text(
                            text = "需要登录 IT之家账号",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "登录后即可发表评论、回复楼中楼、上传附图及进行赞/踩互动，登录成功将自动返回当前页面。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    showLoginPromptDialog = false
                                    pendingAuthAction = null
                                }
                            ) {
                                Text("暂不")
                            }
                            Button(
                                onClick = {
                                    showLoginPromptDialog = false
                                    onLoginClick()
                                },
                                shape = ShapeCache.smoothPill
                            ) {
                                Text("去登录")
                            }
                        }
                    }
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
