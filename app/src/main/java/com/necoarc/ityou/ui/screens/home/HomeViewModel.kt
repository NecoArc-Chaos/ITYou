package com.necoarc.ityou.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.repository.ArticleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 单个分类的文章流状态。
 *
 * 每个分类拥有独立的状态、文章列表与分页游标，
 * 支持左右滑动分区时各页面独立渲染、独立预加载与独立滚动。
 */
@Immutable
data class CategoryFeedState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = true,
    val articles: List<Article> = emptyList(),
    val errorMessage: String? = null,
    val loadMoreFailed: Boolean = false,
    val cursor: Long = 0L,
    val isInitialized: Boolean = false
) {
    val showFullScreenSkeleton: Boolean
        get() = isLoading && articles.isEmpty() && errorMessage == null

    val isEmpty: Boolean
        get() = !isLoading && articles.isEmpty() && errorMessage == null
}

/**
 * 首页全局状态契约。
 *
 * 标注 [Immutable]，提供对当前选中分类状态的透明代理，
 * 兼具全局响应性与向后兼容性。
 */
@Immutable
data class HomeUiState(
    val selectedCategory: ArticleCategory = ArticleCategory.ALL,
    val categoryFeeds: Map<ArticleCategory, CategoryFeedState> = emptyMap(),
    val refreshResult: RefreshResult? = null
) {
    val currentFeed: CategoryFeedState
        get() = categoryFeeds[selectedCategory] ?: CategoryFeedState()

    // 兼容旧属性访问，指向当前选中分类
    val isLoading: Boolean get() = currentFeed.isLoading
    val isLoadingMore: Boolean get() = currentFeed.isLoadingMore
    val isRefreshing: Boolean get() = currentFeed.isRefreshing
    val hasMore: Boolean get() = currentFeed.hasMore
    val articles: List<Article> get() = currentFeed.articles
    val errorMessage: String? get() = currentFeed.errorMessage
    val loadMoreFailed: Boolean get() = currentFeed.loadMoreFailed
    val showFullScreenSkeleton: Boolean get() = currentFeed.showFullScreenSkeleton
    val isEmpty: Boolean get() = currentFeed.isEmpty
}

/**
 * 一次刷新的结果，用于向用户展示「更新了几篇文章」。
 */
@Immutable
data class RefreshResult(val newCount: Int)

class HomeViewModel(
    private val repository: ArticleRepository = ArticleRepository()
) : ViewModel() {

    private val categoryJobs = mutableMapOf<ArticleCategory, Job>()
    private val loadMoreJobs = mutableMapOf<ArticleCategory, Job>()

    private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(
        HomeUiState(
            selectedCategory = ArticleCategory.ALL,
            categoryFeeds = ArticleCategory.entries.associateWith { category ->
                CategoryFeedState(
                    isLoading = category == ArticleCategory.ALL,
                    isInitialized = false
                )
            }
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 默认启动首页「全部」分类的数据加载
        loadCategoryPage(category = ArticleCategory.ALL, isRefresh = false)
    }

    /**
     * 确保某个分类的数据已被拉取。
     * 左右滑动到新分区时调用：若该分类已缓存，则立即秒开、不产生任何网络请求。
     */
    fun ensureCategoryLoaded(category: ArticleCategory) {
        val feed = _uiState.value.categoryFeeds[category] ?: return
        if (feed.isInitialized || feed.isLoading) return
        loadCategoryPage(category = category, isRefresh = false)
    }

    /**
     * 用户点击切换或滑动切换至目标分类。
     */
    fun selectCategory(category: ArticleCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        ensureCategoryLoaded(category)
    }

    /**
     * 下拉刷新指定分类（默认当前分类）。
     */
    fun refresh(category: ArticleCategory = _uiState.value.selectedCategory) {
        loadCategoryPage(category = category, isRefresh = true)
    }

    /** 消费掉刷新结果 */
    fun consumeRefreshResult() {
        _uiState.update { it.copy(refreshResult = null) }
    }

    /** 消费掉错误提示 */
    fun consumeErrorMessage(category: ArticleCategory = _uiState.value.selectedCategory) {
        updateFeed(category) { it.copy(errorMessage = null) }
    }

    /**
     * 触底加载更多指定分类。
     */
    fun loadMore(category: ArticleCategory = _uiState.value.selectedCategory) {
        val feed = _uiState.value.categoryFeeds[category] ?: return
        if (feed.isLoading || feed.isRefreshing || feed.isLoadingMore || !feed.hasMore) return
        if (loadMoreJobs[category]?.isActive == true) return

        val requestCursor = feed.cursor

        loadMoreJobs[category] = viewModelScope.launch {
            updateFeed(category) { it.copy(isLoadingMore = true, loadMoreFailed = false) }

            val result = repository.getArticlePage(
                category = category,
                cursor = requestCursor
            )

            result.onSuccess { page ->
                val currentFeed = _uiState.value.categoryFeeds[category] ?: return@onSuccess
                if (currentFeed.isRefreshing) {
                    updateFeed(category) { it.copy(isLoadingMore = false) }
                    return@onSuccess
                }

                val existingIds = currentFeed.articles.mapTo(HashSet()) { it.id }
                val appended = page.articles.filterNot { it.id in existingIds }
                updateFeed(category) {
                    it.copy(
                        isLoadingMore = false,
                        articles = it.articles + appended,
                        cursor = page.nextCursor,
                        hasMore = page.hasMore
                    )
                }
            }.onFailure {
                updateFeed(category) { it.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    /** 重试加载更多 */
    fun retryLoadMore(category: ArticleCategory = _uiState.value.selectedCategory) {
        updateFeed(category) { it.copy(loadMoreFailed = false) }
        loadMore(category)
    }

    private fun loadCategoryPage(category: ArticleCategory, isRefresh: Boolean) {
        val feed = _uiState.value.categoryFeeds[category] ?: CategoryFeedState()

        // 刷新在途时避免重复
        if (isRefresh && feed.isRefreshing) return

        val previousIds = if (isRefresh) {
            feed.articles.mapTo(HashSet()) { it.id }
        } else {
            null
        }

        // 同步置位刷新 / 加载标志
        updateFeed(category) { current ->
            current.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                isLoadingMore = false,
                loadMoreFailed = false,
                errorMessage = null,
                hasMore = true,
                cursor = 0L,
                articles = if (isRefresh) current.articles else emptyList()
            )
        }
        if (isRefresh) {
            _uiState.update { it.copy(refreshResult = null) }
        }

        categoryJobs[category]?.cancel()
        loadMoreJobs[category]?.cancel()

        categoryJobs[category] = viewModelScope.launch {
            val result = repository.getArticlePage(
                category = category,
                cursor = 0L,
                forceRefresh = true
            )

            result.onSuccess { page ->
                val newCount = previousIds?.let { old ->
                    page.articles.count { it.id !in old }
                }

                updateFeed(category) {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        articles = page.articles,
                        cursor = page.nextCursor,
                        hasMore = page.hasMore,
                        isInitialized = true,
                        errorMessage = null
                    )
                }

                if (isRefresh && newCount != null) {
                    _uiState.update { it.copy(refreshResult = RefreshResult(newCount)) }
                }
            }.onFailure { error ->
                updateFeed(category) {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = false,
                        isInitialized = true,
                        errorMessage = error.localizedMessage ?: "加载失败，请稍后重试"
                    )
                }
            }
        }
    }

    private fun updateFeed(
        category: ArticleCategory,
        transform: (CategoryFeedState) -> CategoryFeedState
    ) {
        _uiState.update { state ->
            val feeds = state.categoryFeeds.toMutableMap()
            val current = feeds[category] ?: CategoryFeedState()
            feeds[category] = transform(current)
            state.copy(categoryFeeds = feeds)
        }
    }
}
