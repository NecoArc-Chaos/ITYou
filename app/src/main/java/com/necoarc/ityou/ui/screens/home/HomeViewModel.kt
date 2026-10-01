package com.necoarc.ityou.ui.screens.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val articles: List<Article> = emptyList(),
    val selectedCategory: ArticleCategory = ArticleCategory.ALL,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: ArticleRepository = ArticleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastOrderTimestamp: Long = 0L

    init {
        loadArticles()
    }

    /**
     * 下拉/初次刷新文章流
     */
    fun loadArticles() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    hasMore = true,
                    errorMessage = null
                )
            }
            lastOrderTimestamp = 0L

            val result = repository.getArticles(
                category = _uiState.value.selectedCategory,
                lastOrderTimestamp = 0L
            )

            result.onSuccess { articles ->
                lastOrderTimestamp = articles.lastOrNull()?.orderTimestamp ?: 0L
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        articles = articles,
                        hasMore = articles.isNotEmpty()
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "加载失败"
                    )
                }
            }
        }
    }

    /**
     * 瀑布流触底自动上拉加载更多
     */
    fun loadMoreArticles() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.hasMore) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val result = repository.getArticles(
                category = currentState.selectedCategory,
                lastOrderTimestamp = lastOrderTimestamp
            )

            result.onSuccess { newArticles ->
                if (newArticles.isEmpty()) {
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                } else {
                    // 去重合并
                    val existingIds = currentState.articles.map { it.id }.toSet()
                    val distinctNew = newArticles.filterNot { existingIds.contains(it.id) }

                    if (distinctNew.isNotEmpty()) {
                        lastOrderTimestamp = distinctNew.lastOrNull()?.orderTimestamp ?: lastOrderTimestamp
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                articles = it.articles + distinctNew,
                                hasMore = true
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                    }
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * 切换分类
     */
    fun selectCategory(category: ArticleCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        loadArticles()
    }
}
