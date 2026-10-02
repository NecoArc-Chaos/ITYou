package com.necoarc.ityou.ui.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.repository.ArticleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DetailUiState(
    val articleId: String = "",
    val isLoading: Boolean = true,
    val detail: ArticleDetail? = null,
    val errorMessage: String? = null
) {
    val showSkeleton: Boolean get() = isLoading && detail == null
    val showError: Boolean get() = !isLoading && detail == null && errorMessage != null
}

/** 详情请求参数（用于失败重试，避免重试时丢失上下文）。 */
private data class DetailRequest(
    val articleId: String,
    val url: String,
    val previewTitle: String,
    val previewAuthor: String,
    val previewPubTime: String
)

class DetailViewModel(
    private val repository: ArticleRepository = ArticleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var lastRequest: DetailRequest? = null
    private var loadJob: Job? = null

    /**
     * 加载文章详情。对「同一篇文章正在加载」的重复调用会被忽略，
     * 避免导航重组导致的重复网络请求。
     */
    fun loadArticleDetail(
        articleId: String,
        url: String,
        previewTitle: String = "",
        previewAuthor: String = "",
        previewPubTime: String = ""
    ) {
        val request = DetailRequest(articleId, url, previewTitle, previewAuthor, previewPubTime)
        lastRequest = request

        val current = _uiState.value
        if (current.articleId == articleId && (current.isLoading || current.detail != null)) return

        runLoad(request)
    }

    fun retry() {
        lastRequest?.let(::runLoad)
    }

    private fun runLoad(request: DetailRequest) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    articleId = request.articleId,
                    isLoading = true,
                    errorMessage = null
                )
            }

            val result = repository.getArticleDetail(
                articleId = request.articleId,
                url = request.url,
                previewTitle = request.previewTitle,
                previewAuthor = request.previewAuthor,
                previewPubTime = request.previewPubTime
            )

            result.onSuccess { detail ->
                _uiState.update {
                    it.copy(isLoading = false, detail = detail, errorMessage = null)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        detail = null,
                        errorMessage = error.localizedMessage ?: "加载失败，请稍后重试"
                    )
                }
            }
        }
    }
}
