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
 * 首页状态契约。
 *
 * 标注 [Immutable] 并保证全部字段为不可变类型，
 * 这样 `HomeScreen` 在状态未变化时不会产生无意义的整屏重组。
 */
@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = true,
    val articles: List<Article> = emptyList(),
    val selectedCategory: ArticleCategory = ArticleCategory.ALL,
    val errorMessage: String? = null,
    val loadMoreFailed: Boolean = false,
    /** 最近一次刷新新增的文章数；null 表示本次刷新没有可提示的结果。 */
    val refreshResult: RefreshResult? = null
) {
    /** 是否展示整屏骨架：仅在「首次加载 / 切换分类」这种列表为空的情况下展示，刷新时不闪屏。 */
    val showFullScreenSkeleton: Boolean get() = isLoading && articles.isEmpty() && errorMessage == null

    val isEmpty: Boolean get() = !isLoading && articles.isEmpty() && errorMessage == null
}

/**
 * 一次刷新的结果，用于向用户展示「更新了几篇文章」。
 *
 * 作为一次性事件由 UI 消费后清空；[newCount] 为 0 表示没有新内容。
 */
@Immutable
data class RefreshResult(val newCount: Int)

/**
 * 分类文章流状态快照。
 *
 * 用于在分类切换时保存并快速恢复该分类的文章列表与分页游标，
 * 避免在不同分类间来回选择时反复发起网络请求与展示骨架屏。
 */
@Immutable
private data class CategoryFeedSnapshot(
    val articles: List<Article> = emptyList(),
    val cursor: Long = 0L,
    val hasMore: Boolean = true
)

class HomeViewModel(
    private val repository: ArticleRepository = ArticleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 分页游标：服务端原始页的最旧时间戳 */
    private var cursor: Long = 0L

    /**
     * 按分类划分的首页文章流缓存映射表。
     *
     * 解决核心痛点：
     * 用户在不同分类之间来回选择时，只要该分类已经加载过，直接从内存缓存恢复，
     * 做到「每个分类只加载一次」，来回切换秒切秒开，无需每次重复网络请求与等待转圈。
     */
    private val categoryCacheMap = mutableMapOf<ArticleCategory, CategoryFeedSnapshot>()

    private var firstPageJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadFirstPage(clearExisting = true)
    }

    /** 顶栏刷新：保留当前列表，避免整屏闪骨架；完成后报告新增文章数。 */
    fun refresh() {
        loadFirstPage(clearExisting = false, isRefresh = true)
    }

    /** 消费掉刷新结果（提示展示完毕后调用，避免重复弹提示）。 */
    fun consumeRefreshResult() {
        _uiState.update { it.copy(refreshResult = null) }
    }

    /**
     * 消费掉错误提示（弹窗展示完毕后调用）。
     *
     * 必要性：`errorMessage` 原本只在「发起新的加载」时才被清空，
     * 刷新失败后会长期保持非 null。改用模态弹窗后，任何一次重组都可能
     * 让它重新弹出，因此必须由 UI 显式消费，与 [consumeRefreshResult] 对称。
     */
    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 触底加载更多。重复调用是安全的（内部有状态守卫）。
     *
     * 注意：[cursor] 在**协程外**同步读取，再作为参数传入协程。
     * 如果改到协程体内读取，刷新把 cursor 归零的时机就会与本方法竞争，
     * 导致"用旧游标请求下一页、又叠加到即将被替换的新列表上"，
     * 出现重复或错乱的条目。
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return
        if (loadMoreJob?.isActive == true) return

        // 同步快照游标与分类，避免协程调度期间被刷新改写
        val requestCursor = cursor
        val requestCategory = state.selectedCategory

        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }

            val result = repository.getArticlePage(
                category = requestCategory,
                cursor = requestCursor
            )

            result.onSuccess { page ->
                // 若期间发生了刷新/切分类，本次结果已过期，直接丢弃，
                // 否则会把旧页数据追加到新列表上。
                val current = _uiState.value
                if (current.selectedCategory != requestCategory || current.isRefreshing) {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    return@onSuccess
                }

                cursor = page.nextCursor
                val existingIds = current.articles.mapTo(HashSet()) { it.id }
                val appended = page.articles.filterNot { it.id in existingIds }
                val updatedArticles = current.articles + appended
                _uiState.update { it.copy(
                    isLoadingMore = false,
                    articles = updatedArticles,
                    hasMore = page.hasMore
                ) }

                // 同步更新该分类的缓存映射
                categoryCacheMap[requestCategory] = CategoryFeedSnapshot(
                    articles = updatedArticles,
                    cursor = page.nextCursor,
                    hasMore = page.hasMore
                )
            }.onFailure {
                _uiState.update { current -> current.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    /** 重试「加载更多」失败的分页请求。 */
    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    fun selectCategory(category: ArticleCategory) {
        val currentCategory = _uiState.value.selectedCategory
        if (currentCategory == category) return

        // 1. 保存离开前的当前分类状态快照
        val currentArticles = _uiState.value.articles
        if (currentArticles.isNotEmpty()) {
            categoryCacheMap[currentCategory] = CategoryFeedSnapshot(
                articles = currentArticles,
                cursor = cursor,
                hasMore = _uiState.value.hasMore
            )
        }

        firstPageJob?.cancel()
        loadMoreJob?.cancel()

        // 2. 检查目标分类是否已有缓存
        val cached = categoryCacheMap[category]
        if (cached != null && cached.articles.isNotEmpty()) {
            // 命中缓存：直接同步恢复该分类的文章列表、游标与状态，无需重新加载网络！
            cursor = cached.cursor
            _uiState.update {
                it.copy(
                    selectedCategory = category,
                    articles = cached.articles,
                    hasMore = cached.hasMore,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    loadMoreFailed = false,
                    errorMessage = null,
                    refreshResult = null
                )
            }
        } else {
            // 首次访问该分类：置空列表并向网络发起首屏请求
            cursor = 0L
            _uiState.update {
                it.copy(
                    selectedCategory = category,
                    articles = emptyList(),
                    hasMore = true,
                    isLoading = true,
                    loadMoreFailed = false,
                    errorMessage = null,
                    refreshResult = null
                )
            }
            loadFirstPage(clearExisting = true, isRefresh = false)
        }
    }

    private fun loadFirstPage(clearExisting: Boolean, isRefresh: Boolean = false) {
        // 已有刷新在途时直接忽略，避免快速连点导致重复请求与计数错乱
        if (isRefresh && _uiState.value.isRefreshing) return

        // 在切换协程之前同步快照，保证「更新了几篇」的基准是用户当前看到的列表
        val previousIds = if (isRefresh) {
            _uiState.value.articles.mapTo(HashSet()) { it.id }
        } else {
            null
        }
        val category = _uiState.value.selectedCategory

        // 同步置位刷新标志（而不是等协程体执行）：
        // 否则 loadMore() 的守卫会读到尚未更新的 isRefreshing，
        // 在「刷新刚启动、协程体还没跑」的窗口里放行一次过期的加载更多。
        if (isRefresh) {
            _uiState.update { it.copy(isRefreshing = true, refreshResult = null) }
        }

        firstPageJob?.cancel()
        loadMoreJob?.cancel()

        firstPageJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    isLoadingMore = false,
                    loadMoreFailed = false,
                    errorMessage = null,
                    hasMore = true,
                    refreshResult = null,
                    articles = if (clearExisting) emptyList() else current.articles
                )
            }
            cursor = 0L

            // 首屏/刷新/切分类都必须回源：
            // 这三种场景用户期待的都是「此刻最新的内容」，
            // 若命中资讯接口的 60 秒短缓存，下拉刷新会看起来毫无效果。
            val result = repository.getArticlePage(
                category = category,
                cursor = 0L,
                forceRefresh = true
            )

            result.onSuccess { page ->
                cursor = page.nextCursor
                // 仅在刷新场景计算新增数量：本次返回里不属于上一份快照的条目
                val newCount = previousIds?.let { old ->
                    page.articles.count { it.id !in old }
                }
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        articles = page.articles,
                        hasMore = page.hasMore,
                        refreshResult = if (isRefresh && newCount != null) {
                            RefreshResult(newCount = newCount)
                        } else {
                            null
                        }
                    )
                }

                // 同步记录到该分类的缓存映射表中
                categoryCacheMap[category] = CategoryFeedSnapshot(
                    articles = page.articles,
                    cursor = page.nextCursor,
                    hasMore = page.hasMore
                )
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = false,
                        errorMessage = error.localizedMessage ?: "加载失败，请稍后重试"
                    )
                }
            }
        }
    }
}
