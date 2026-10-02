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
    val hasMore: Boolean = true,
    val articles: List<Article> = emptyList(),
    val selectedCategory: ArticleCategory = ArticleCategory.ALL,
    val errorMessage: String? = null,
    val loadMoreFailed: Boolean = false
) {
    /** 是否展示整屏骨架：仅在「首次加载 / 切换分类」这种列表为空的情况下展示，刷新时不闪屏。 */
    val showFullScreenSkeleton: Boolean get() = isLoading && articles.isEmpty() && errorMessage == null

    val isEmpty: Boolean get() = !isLoading && articles.isEmpty() && errorMessage == null
}

class HomeViewModel(
    private val repository: ArticleRepository = ArticleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 分页游标：服务端原始页的最旧时间戳 */
    private var cursor: Long = 0L

    private var firstPageJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadFirstPage(clearExisting = true)
    }

    /** 顶栏刷新：保留当前列表，避免整屏闪骨架。 */
    fun refresh() {
        loadFirstPage(clearExisting = false)
    }

    /** 触底加载更多。重复调用是安全的（内部有状态守卫）。 */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (loadMoreJob?.isActive == true) return

        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }

            val result = repository.getArticlePage(
                category = state.selectedCategory,
                cursor = cursor
            )

            result.onSuccess { page ->
                cursor = page.nextCursor
                val existingIds = _uiState.value.articles.mapTo(HashSet()) { it.id }
                val appended = page.articles.filterNot { it.id in existingIds }
                _uiState.update { current ->
                    current.copy(
                        isLoadingMore = false,
                        articles = current.articles + appended,
                        hasMore = page.hasMore
                    )
                }
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
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        loadFirstPage(clearExisting = true)
    }

    private fun loadFirstPage(clearExisting: Boolean) {
        firstPageJob?.cancel()
        loadMoreJob?.cancel()

        firstPageJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    loadMoreFailed = false,
                    errorMessage = null,
                    hasMore = true,
                    articles = if (clearExisting) emptyList() else current.articles
                )
            }
            cursor = 0L

            val category = _uiState.value.selectedCategory
            val result = repository.getArticlePage(category = category, cursor = 0L)

            result.onSuccess { page ->
                cursor = page.nextCursor
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        articles = page.articles,
                        hasMore = page.hasMore
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        hasMore = false,
                        errorMessage = error.localizedMessage ?: "加载失败，请稍后重试"
                    )
                }
            }
        }
    }
}
