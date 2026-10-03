package com.necoarc.ityou.ui.screens.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.ui.components.ArticleCard
import com.necoarc.ityou.ui.components.FeedItemType
import com.necoarc.ityou.ui.components.HeroArticleCard
import com.necoarc.ityou.ui.components.HomeSkeletonScreen
import com.necoarc.ityou.ui.theme.Dimens
import com.necoarc.ityou.ui.theme.ShapeCache
import kotlinx.coroutines.flow.distinctUntilChanged

/** 距离列表末尾还有多少项时触发预加载。 */
private const val PREFETCH_THRESHOLD = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onArticleClick: (articleId: String, url: String, title: String, author: String, pubTime: String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 触底预加载：使用 snapshotFlow 收集尾部可见项，避免每帧读取 layoutInfo 触发额外重组
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val totalCount = listState.layoutInfo.totalItemsCount
                if (totalCount > 0 && lastVisibleIndex >= totalCount - PREFETCH_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
    }

    // 刷新完成提示：消费一次性事件，展示「更新了 N 篇」后清空，避免旋转/重组时重复弹出
    val refreshResult = uiState.refreshResult
    LaunchedEffect(refreshResult) {
        if (refreshResult != null) {
            val text = if (refreshResult.newCount > 0) {
                "已更新 ${refreshResult.newCount} 篇文章"
            } else {
                "已是最新内容"
            }
            viewModel.consumeRefreshResult()
            snackbarHostState.showSnackbar(message = text)
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                isRefreshing = uiState.isRefreshing,
                onRefreshClick = { viewModel.refresh() },
                onSettingsClick = onSettingsClick
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        when {
            uiState.showFullScreenSkeleton -> {
                HomeSkeletonScreen(windowInsetsPadding = innerPadding)
            }

            uiState.errorMessage != null && uiState.articles.isEmpty() -> {
                HomeErrorState(
                    message = uiState.errorMessage.orEmpty(),
                    onRetry = { viewModel.refresh() },
                    windowInsetsPadding = innerPadding
                )
            }

            else -> {
                // 下拉刷新：仅包裹列表区域（骨架屏/错误页无需下拉）
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    HomeArticleList(
                        uiState = uiState,
                        listState = listState,
                        // 内边距已由外层 PullToRefreshBox 消费，这里不再重复应用
                        windowInsetsPadding = PaddingValues(0.dp),
                        onArticleClick = onArticleClick,
                        onCategorySelected = viewModel::selectCategory,
                        onRetryLoadMore = viewModel::retryLoadMore
                    )
                }
            }
        }
    }
}

/**
 * 旋转刷新的刷新图标。
 *
 * MD3E 动效约定：
 * - 刷新中：**匀速无限旋转**（线性、无弹簧），表示「持续进行中」的确定性状态；
 * - 空闲时：停在当前角度，不做回摆，避免停止瞬间的方向反转。
 *
 * 实现说明（为什么不用 rememberInfiniteTransition）：
 * 无限动画的值在每圈结束时从 360 跳回 0，无论怎么组合（直接使用、
 * 或乘一个进度系数）都会在循环边界产生一次可见的**反向跳变**。
 * 因此这里改用 [withFrameNanos] 按帧累加角度：数值单调递增、永不回绕，
 * 从根源上消除回摆；停止时保留当前角度即可平滑收尾。
 */
@Composable
private fun AnimatedRefreshIcon(isRefreshing: Boolean) {
    // 累加角度：仅在刷新中按帧推进；停止后保持不变（不回摆）
    val rotation = remember { mutableFloatStateOf(0f) }
    // 记录上一帧时间，用于按真实时间差换算角度，保证不同帧率下转速一致
    var lastFrameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            lastFrameNanos = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { now ->
                if (lastFrameNanos != 0L) {
                    val deltaSeconds = (now - lastFrameNanos) / 1_000_000_000f
                    // 每秒旋转 400°（约 1.11 圈/秒），接近 MD3E 的「进行中」节奏。
                    // 注意：这里**不做 % 360 取模**——取模会在数值回绕时造成反向跳变，
                    // 而 Modifier.rotate 本身能正确处理任意大的角度值。
                    rotation.floatValue += deltaSeconds * DEGREES_PER_SECOND
                }
                lastFrameNanos = now
            }
        }
    }

    Icon(
        imageVector = Icons.Outlined.Refresh,
        contentDescription = if (isRefreshing) "刷新中" else "刷新",
        modifier = Modifier.rotate(rotation.floatValue)
    )
}

/** 刷新图标的旋转速度（度/秒）。 */
private const val DEGREES_PER_SECOND = 400f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "ITYou",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            IconButton(onClick = onRefreshClick) {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "刷新")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(imageVector = Icons.Outlined.Settings, contentDescription = "设置")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

@Composable
private fun HomeArticleList(
    uiState: HomeUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    windowInsetsPadding: PaddingValues,
    onArticleClick: (articleId: String, url: String, title: String, author: String, pubTime: String) -> Unit,
    onCategorySelected: (ArticleCategory) -> Unit,
    onRetryLoadMore: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(windowInsetsPadding),
        contentPadding = PaddingValues(
            horizontal = Dimens.listHorizontalPadding,
            vertical = Dimens.listVerticalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.listItemSpacing)
    ) {
        // 1. 分类导航胶囊条
        item(key = "category_row", contentType = FeedItemType.CATEGORY_ROW) {
            CategoryRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = onCategorySelected
            )
        }

        // 2. 头条大卡
        val heroArticle = uiState.articles.firstOrNull()
        if (heroArticle != null) {
            item(key = "hero_${heroArticle.id}", contentType = FeedItemType.HERO) {
                HeroArticleCard(
                    article = heroArticle,
                    onClick = {
                        onArticleClick(
                            heroArticle.id,
                            heroArticle.url,
                            heroArticle.title,
                            heroArticle.author,
                            heroArticle.publishTime
                        )
                    }
                )
            }
        }

        // 3. 文章卡片流
        val streamArticles = uiState.articles.drop(1)
        items(
            items = streamArticles,
            key = { article -> article.id },
            contentType = { FeedItemType.ARTICLE }
        ) { article ->
            ArticleCard(
                article = article,
                onClick = {
                    onArticleClick(
                        article.id,
                        article.url,
                        article.title,
                        article.author,
                        article.publishTime
                    )
                }
            )
        }

        // 4. 底部状态
        when {
            uiState.isLoadingMore -> {
                item(key = "footer_loading", contentType = FeedItemType.LOADING_FOOTER) {
                    LoadingFooter()
                }
            }

            uiState.loadMoreFailed -> {
                item(key = "footer_error", contentType = FeedItemType.ERROR) {
                    LoadMoreErrorFooter(onRetry = onRetryLoadMore)
                }
            }

            uiState.isEmpty -> {
                item(key = "footer_empty", contentType = FeedItemType.EMPTY) {
                    EmptyFooter()
                }
            }

            !uiState.hasMore && uiState.articles.isNotEmpty() -> {
                item(key = "footer_end", contentType = FeedItemType.END_FOOTER) {
                    EndFooter()
                }
            }
        }

        item(key = "bottom_spacer", contentType = FeedItemType.END_FOOTER) {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryRow(
    selectedCategory: ArticleCategory,
    onCategorySelected: (ArticleCategory) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = ArticleCategory.entries,
            key = { category -> "category_${category.name}" },
            contentType = { FeedItemType.CATEGORY_ROW }
        ) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                shape = ShapeCache.smooth20,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun LoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = ShapeCache.smoothPill,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "正在探索更多前沿科技...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoadMoreErrorFooter(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onRetry) {
            Text(
                text = "加载失败，点击重试",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun EndFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "已为你呈现全部最新资讯",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun EmptyFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无该分类下的文章",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * 首屏加载失败状态。
 */
@Composable
private fun HomeErrorState(
    message: String,
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
        Card(
            shape = ShapeCache.smooth24,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "内容加载失败",
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
                Button(
                    onClick = onRetry,
                    shape = ShapeCache.smoothPill
                ) {
                    Text(text = "重新加载", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
