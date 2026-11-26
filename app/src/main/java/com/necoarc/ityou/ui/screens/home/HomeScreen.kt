package com.necoarc.ityou.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.ui.components.ArticleCard
import com.necoarc.ityou.ui.components.BackToTopRefreshButton
import com.necoarc.ityou.ui.components.FeedItemType
import com.necoarc.ityou.ui.components.HeroArticleCard
import com.necoarc.ityou.ui.components.HomeSkeletonScreen
import com.necoarc.ityou.ui.theme.Dimens
import com.necoarc.ityou.ui.theme.ShapeCache
import com.necoarc.ityou.ui.util.AppToast
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 距离列表末尾还有多少项时触发预加载。 */
private const val PREFETCH_THRESHOLD = 3

/** 滚过多少个列表项后显示「回到顶部」按钮。 */
private const val BACK_TO_TOP_THRESHOLD = 4

/** 按钮距离右下角的间距。 */
private val BACK_TO_TOP_BUTTON_PADDING = 16.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onArticleClick: (articleId: String, url: String, title: String, author: String, pubTime: String) -> Unit,
    onSettingsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val categories = remember { ArticleCategory.entries }
    val initialPageIndex = remember { categories.indexOf(uiState.selectedCategory).coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { categories.size }
    )

    // 滑动 Pager 联动选择分类
    LaunchedEffect(pagerState.currentPage) {
        val targetCategory = categories[pagerState.currentPage]
        if (uiState.selectedCategory != targetCategory) {
            viewModel.selectCategory(targetCategory)
        }
    }

    // 外部或代码改变分类时联动 Pager 平滑切页
    LaunchedEffect(uiState.selectedCategory) {
        val targetIndex = categories.indexOf(uiState.selectedCategory)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    // 刷新完成提示
    val refreshResult = uiState.refreshResult
    LaunchedEffect(refreshResult) {
        if (refreshResult != null) {
            val message = if (refreshResult.newCount > 0) {
                "已更新 ${refreshResult.newCount} 篇文章"
            } else {
                "已同步最新文章"
            }
            AppToast.show(context, message)
            viewModel.consumeRefreshResult()
        }
    }

    // 刷新失败提示
    val currentFeed = uiState.currentFeed
    val errorMessage = currentFeed.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && currentFeed.articles.isNotEmpty()) {
            AppToast.show(context, errorMessage, long = true)
            viewModel.consumeErrorMessage()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            HomeTopBar(
                scrollBehavior = scrollBehavior,
                onSettingsClick = onSettingsClick,
                onFavoritesClick = onFavoritesClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. 顶部常驻 MD3E 分类导航胶囊条（与 Pager 双向联动）
            CategoryRow(
                categories = categories,
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { category ->
                    val index = categories.indexOf(category)
                    if (index >= 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimens.listHorizontalPadding,
                        vertical = 6.dp
                    )
            )

            // 2. 下方内容区域：支持真一手势左右横滑切换分区的 HorizontalPager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val category = categories[pageIndex]
                val feed = uiState.categoryFeeds[category] ?: CategoryFeedState()

                // 懒加载：进入或滑近时，若该分类尚未加载则自动拉取
                LaunchedEffect(category) {
                    viewModel.ensureCategoryLoaded(category)
                }

                // 每个分类拥有独立持久化的 LazyListState，切走再切回精确保留滚动高度
                val listState = rememberSaveable(category, saver = LazyListState.Saver) {
                    LazyListState()
                }

                // 触底预加载
                LaunchedEffect(listState) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .distinctUntilChanged()
                        .collect { lastVisibleIndex ->
                            val totalCount = listState.layoutInfo.totalItemsCount
                            if (totalCount > 0 && lastVisibleIndex >= totalCount - PREFETCH_THRESHOLD) {
                                viewModel.loadMore(category)
                            }
                        }
                }

                val showBackToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex >= BACK_TO_TOP_THRESHOLD }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        feed.showFullScreenSkeleton -> {
                            HomeSkeletonScreen(includeCategoryRow = false)
                        }

                        feed.errorMessage != null && feed.articles.isEmpty() -> {
                            HomeErrorState(
                                message = feed.errorMessage.orEmpty(),
                                onRetry = { viewModel.refresh(category) }
                            )
                        }

                        else -> {
                            val pullState = rememberPullToRefreshState()
                            PullToRefreshBox(
                                isRefreshing = feed.isRefreshing,
                                onRefresh = { viewModel.refresh(category) },
                                state = pullState,
                                indicator = {
                                    Md3ePullToRefreshIndicator(
                                        state = pullState,
                                        isRefreshing = feed.isRefreshing,
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                HomeArticleList(
                                    articles = feed.articles,
                                    isLoadingMore = feed.isLoadingMore,
                                    loadMoreFailed = feed.loadMoreFailed,
                                    hasMore = feed.hasMore,
                                    isEmpty = feed.isEmpty,
                                    listState = listState,
                                    windowInsetsPadding = PaddingValues(0.dp),
                                    onArticleClick = onArticleClick,
                                    onRetryLoadMore = { viewModel.retryLoadMore(category) }
                                )

                                // 回到顶部 + 刷新。浮于列表右下角，滚过一段距离后才出现
                                BackToTopRefreshButton(
                                    visible = showBackToTop,
                                    onClick = {
                                        coroutineScope.launch {
                                            runCatching { listState.animateScrollToItem(0) }
                                            viewModel.refresh(category)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(BACK_TO_TOP_BUTTON_PADDING)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * MD3E 下拉刷新指示器。
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

/**
 * MD3E 表现力顶栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSettingsClick: () -> Unit,
    onFavoritesClick: () -> Unit
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ITYou",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = ShapeCache.smoothPill,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "资讯流",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        actions = {
            FilledTonalIconButton(
                onClick = onFavoritesClick,
                shape = ShapeCache.smoothPill,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = "我的收藏",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = onSettingsClick,
                shape = ShapeCache.smoothPill,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

@Composable
private fun HomeArticleList(
    articles: List<Article>,
    isLoadingMore: Boolean,
    loadMoreFailed: Boolean,
    hasMore: Boolean,
    isEmpty: Boolean,
    listState: LazyListState,
    windowInsetsPadding: PaddingValues,
    onArticleClick: (articleId: String, url: String, title: String, author: String, pubTime: String) -> Unit,
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
        // 1. 头条大卡
        val heroArticle = articles.firstOrNull()
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

        // 2. 文章卡片流
        val streamArticles = articles.drop(1)
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

        // 3. 底部状态
        when {
            isLoadingMore -> {
                item(key = "footer_loading", contentType = FeedItemType.LOADING_FOOTER) {
                    LoadingFooter()
                }
            }

            loadMoreFailed -> {
                item(key = "footer_error", contentType = FeedItemType.ERROR) {
                    LoadMoreErrorFooter(onRetry = onRetryLoadMore)
                }
            }

            isEmpty -> {
                item(key = "footer_empty", contentType = FeedItemType.EMPTY) {
                    EmptyFooter()
                }
            }

            !hasMore && articles.isNotEmpty() -> {
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
    categories: List<ArticleCategory>,
    selectedCategory: ArticleCategory,
    onCategorySelected: (ArticleCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val rowState = rememberLazyListState()

    // 选中分类变动时，平滑滚动使目标分类 Chip 保持可见居中
    LaunchedEffect(selectedCategory) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) {
            val targetScroll = (index - 1).coerceAtLeast(0)
            rowState.animateScrollToItem(targetScroll)
        }
    }

    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(
            items = categories,
            key = { category -> "category_${category.name}" },
            contentType = { FeedItemType.CATEGORY_ROW }
        ) { category ->
            val isSelected = selectedCategory == category

            // MD3E 弹性微交互：统一读取主题 MotionScheme 的 fastSpatialSpec
            val chipScale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1.0f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "CategoryChipScale"
            )

            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = ShapeCache.smoothPill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null,
                modifier = Modifier.graphicsLayer {
                    scaleX = chipScale
                    scaleY = chipScale
                }
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
    windowInsetsPadding: PaddingValues = PaddingValues(0.dp)
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
