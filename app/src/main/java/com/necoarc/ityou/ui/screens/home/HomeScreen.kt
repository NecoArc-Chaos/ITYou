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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
                "已同步最新文章"
            }
            viewModel.consumeRefreshResult()
            snackbarHostState.showSnackbar(message = text)
        }
    }

    // 刷新失败提示：列表非空时错误页不会展示（错误页只用于「首屏为空」的场景），
    // 若不在这里兜底，用户只会看到转圈突然停止、没有任何反馈 —— 属于静默失败。
    val errorMessage = uiState.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && uiState.articles.isNotEmpty()) {
            snackbarHostState.showSnackbar(message = errorMessage)
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
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
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    state = pullState,
                    // 自定义指示器：与本应用的胶囊形状 / primaryContainer 配色对齐
                    indicator = {
                        Md3ePullToRefreshIndicator(
                            state = pullState,
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
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
 * MD3E 下拉刷新指示器。
 *
 * 使用 [PullToRefreshDefaults.LoadingIndicator] —— 这是 Material 官方为下拉刷新
 * 场景提供的 M3E 指示器，其内部渲染的就是 [ContainedLoadingIndicator]
 * （形状在容器内持续形变 morph），因此无需自己拼装：
 * - 刷新中：容器化加载指示器持续形变；
 * - 下拉中：指示器形状随 `distanceFraction` 从 0→1 推进；
 * - 超过阈值后继续下拉：整体旋转，给出"松手即可刷新"的连续反馈。
 *
 * 配色遵循 MD3E 的成对使用约束：
 * 容器 `primaryContainer` + 指示器 `onPrimaryContainer`（保证对比度）。
 *
 * 实现说明：此处**只需传颜色**，尺寸 / 位移 / 裁剪 / 容器绘制
 * 全部由官方实现负责，不要自行外包 `Surface` 或 `.size()`。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Md3ePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    PullToRefreshDefaults.LoadingIndicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = PullToRefreshDefaults.LoadingIndicatorElevation
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
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
