package com.necoarc.ityou.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.ui.components.ArticleCard
import com.necoarc.ityou.ui.components.BackToTopRefreshButton
import com.necoarc.ityou.ui.components.FeedItemType
import com.necoarc.ityou.ui.components.HeroArticleCard
import com.necoarc.ityou.ui.components.HomeSkeletonScreen
import com.necoarc.ityou.ui.theme.Dimens
import com.necoarc.ityou.ui.util.AppToast
import com.necoarc.ityou.ui.theme.ShapeCache
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 距离列表末尾还有多少项时触发预加载。 */
private const val PREFETCH_THRESHOLD = 3

/**
 * 滚过多少个列表项后显示「回到顶部」按钮。
 *
 * 取 4 而非 1：仅仅滑动一两屏（首页还有分类栏与头条卡）就弹出按钮，
 * 会显得过于敏感、也容易误触；4 项约对应一屏半以上的真实下翻距离。
 */
private const val BACK_TO_TOP_THRESHOLD = 4

/**
 * 按钮距离右下角的间距。
 *
 * 用 16dp 与列表内边距保持一致；未额外叠加导航栏 inset，
 * 因为该按钮位于已消费 `innerPadding` 的容器内，
 * 底部安全区已由 Scaffold 处理，重复叠加会把按钮顶得过高。
 */
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
    // 为每个分类独立保存与恢复滚动位置，切换分类来回浏览时位置不丢失
    val listState = rememberSaveable(uiState.selectedCategory, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 是否已滚过「一段距离」——决定回到顶部按钮的显隐。
    //
    // 用 derivedStateOf 而非直接读 listState：滚动时 firstVisibleItemIndex
    // 每帧都在变，直接读取会让整个 HomeScreen 每帧重组。
    // derivedStateOf 只在**布尔结果翻转时**才通知下游，把重组压到两次。
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= BACK_TO_TOP_THRESHOLD }
    }

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

    // 刷新完成提示。
    //
    // 改用系统 Toast 后不再需要本地 UI 状态：提示的生命周期完全由系统托管，
    // 因此这里只需「读出事件 → 弹 Toast → 消费事件」。
    // 仍需遵循「先处理、后消费」的顺序 —— 若先消费，uiState 变化会让本
    // LaunchedEffect 以 null 重启，这次提示就丢了。
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

    // 刷新失败提示：列表非空时错误页不会展示（错误页只用于「首屏为空」的场景），
    // 若不在这里兜底，用户只会看到转圈突然停止、没有任何反馈 —— 属于静默失败。
    // 用较长时长，让错误信息有足够时间被看清。
    val errorMessage = uiState.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && uiState.articles.isNotEmpty()) {
            AppToast.show(context, errorMessage, long = true)
            viewModel.consumeErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                onSettingsClick = onSettingsClick,
                onFavoritesClick = onFavoritesClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. 顶部常驻分类导航胶囊条（带微弹簧缩放与自动居中滚动）
            CategoryRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = viewModel::selectCategory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimens.listHorizontalPadding,
                        vertical = 6.dp
                    )
            )

            // 2. 下方内容区域：带分类切换过渡动画
            AnimatedContent(
                targetState = uiState.selectedCategory,
                transitionSpec = {
                    val isForward = targetState.ordinal > initialState.ordinal
                    val direction = if (isForward) {
                        AnimatedContentTransitionScope.SlideDirection.Start
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.End
                    }
                    val navSpring = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    (slideIntoContainer(direction, navSpring) + fadeIn(animationSpec = tween(220)))
                        .togetherWith(slideOutOfContainer(direction, navSpring) + fadeOut(animationSpec = tween(180)))
                },
                label = "CategoryContentTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.showFullScreenSkeleton -> {
                            HomeSkeletonScreen(
                                includeCategoryRow = false
                            )
                        }

                        uiState.errorMessage != null && uiState.articles.isEmpty() -> {
                            HomeErrorState(
                                message = uiState.errorMessage.orEmpty(),
                                onRetry = { viewModel.refresh() }
                            )
                        }

                        else -> {
                            // 下拉刷新：仅包裹列表区域
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
                                modifier = Modifier.fillMaxSize()
                            ) {
                                HomeArticleList(
                                    uiState = uiState,
                                    listState = listState,
                                    windowInsetsPadding = PaddingValues(0.dp),
                                    onArticleClick = onArticleClick,
                                    onRetryLoadMore = viewModel::retryLoadMore
                                )

                                // 回到顶部 + 刷新。浮于列表右下角，滚过一段距离后才出现。
                                BackToTopRefreshButton(
                                    visible = showBackToTop,
                                    onClick = {
                                        coroutineScope.launch {
                                            runCatching { listState.animateScrollToItem(0) }
                                            viewModel.refresh()
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
    onSettingsClick: () -> Unit,
    onFavoritesClick: () -> Unit
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
            IconButton(onClick = onFavoritesClick) {
                Icon(imageVector = Icons.Outlined.BookmarkBorder, contentDescription = "我的收藏")
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

        // 2. 文章卡片流
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

        // 3. 底部状态
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
    onCategorySelected: (ArticleCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val rowState = rememberLazyListState()

    // 选中分类变动时，平滑滚动使目标分类 Chip 保持可见
    LaunchedEffect(selectedCategory) {
        val index = ArticleCategory.entries.indexOf(selectedCategory)
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
            items = ArticleCategory.entries,
            key = { category -> "category_${category.name}" },
            contentType = { FeedItemType.CATEGORY_ROW }
        ) { category ->
            val isSelected = selectedCategory == category

            // MD3E 弹性微交互：选中项具有平滑的缩放动效
            val chipScale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
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
                shape = ShapeCache.smooth20,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
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
