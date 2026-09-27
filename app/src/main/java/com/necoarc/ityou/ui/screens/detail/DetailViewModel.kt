package com.necoarc.ityou.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = false,
    val detail: ArticleDetail? = null,
    val errorMessage: String? = null
)

class DetailViewModel(
    private val repository: ArticleRepository = ArticleRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState(isLoading = true))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadArticleDetail(
        articleId: String,
        url: String,
        previewTitle: String = "",
        previewAuthor: String = "",
        previewPubTime: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.getArticleDetail(articleId, url, previewTitle, previewAuthor, previewPubTime)
            result.onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        detail = detail
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "加载详情失败"
                    )
                }
            }
        }
    }
}
