package com.necoarc.ityou.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.necoarc.ityou.data.model.ContentBlock
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
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()

    var isStarred by remember { mutableStateOf(false) }

    // 仿 ReadYou 的沉浸式底栏跟随滚动隐藏/显示
    val isScrollingUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || !listState.isScrollInProgress
        }
    }

    LaunchedEffect(articleId) {
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
                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
        bottomBar = {
            // 仿 PixelPlayer 与 ReadYou 的悬浮胶囊底栏 (Floating Pill Toolbar)
            AnimatedVisibility(
                visible = isScrollingUp,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = ShapeCache.smooth32,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "纯净阅读模式",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(MaterialTheme.colorScheme.outline, ShapeCache.smoothPill)
                            )
                            Text(
                                text = "IT之家原创",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val detail = uiState.detail
            if (detail != null) {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // 1. 文章大标题 (仿 ReadYou headline 样式)
                        item {
                            Text(
                                text = detail.title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // 2. 元数据栏（作者、时间、来源）
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = ShapeCache.smooth8,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(end = 4.dp)
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

                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }

                        // 3. 原生富文本排版流
                        items(detail.contentBlocks) { block ->
                            when (block) {
                                is ContentBlock.Paragraph -> {
                                    Text(
                                        text = block.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                is ContentBlock.Heading -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = block.text,
                                        style = if (block.level == 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is ContentBlock.Image -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = block.url,
                                            contentDescription = block.caption,
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(ShapeCache.smooth24)
                                        )
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
                                is ContentBlock.BlockQuote -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = ShapeCache.smooth16,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(modifier = Modifier.padding(14.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        ShapeCache.smooth8
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = block.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}
