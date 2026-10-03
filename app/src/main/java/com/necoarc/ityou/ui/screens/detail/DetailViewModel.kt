package com.necoarc.ityou.ui.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.data.model.ArticleComment
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
    val errorMessage: String? = null,
    val comments: List<ArticleComment> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = false,
    val commentsError: String? = null,
    /** 正在展开剩余回复的父评论 id 集合（用于展示行内加载态）。 */
    val expandingCommentIds: Set<String> = emptySet()
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
    private var commentsJob: Job? = null
    private var loadMoreJob: Job? = null

    /** 评论分页游标；"0" 表示尚未加载首页。 */
    private var commentsCursor: String = "0"

    /** 评论接口 sn 令牌缓存（避免每次翻页重复抓取 PC 页面）。 */
    private var commentSn: String? = null

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

        // 文章切换：清空评论相关缓存
        commentSn = null
        runLoad(request)
        loadComments(url, reset = true)
    }

    fun retry() {
        lastRequest?.let {
            commentSn = null
            runLoad(it)
            loadComments(it.url, reset = true)
        }
    }

    /** 重新加载评论首页（供评论区错误重试使用）。 */
    fun retryComments() {
        lastRequest?.let { loadComments(it.url, reset = true) }
    }

    /** 加载下一页（更旧的）评论。 */
    fun loadMoreComments() {
        val state = _uiState.value
        if (state.isLoadingMoreComments || !state.hasMoreComments) return
        lastRequest?.let { loadComments(it.url, reset = false) }
    }

    /**
     * 展开某条评论下剩余的楼中楼回复。
     *
     * 已内联的回复保持不变，仅追加服务端返回的剩余回复；重复调用会被忽略。
     */
    fun expandCommentReplies(commentId: String) {
        val state = _uiState.value
        if (commentId in state.expandingCommentIds) return

        val request = lastRequest ?: return
        _uiState.update { it.copy(expandingCommentIds = it.expandingCommentIds + commentId) }

        viewModelScope.launch {
            val result = repository.getCommentReplies(
                commentId = commentId,
                url = request.url,
                sn = commentSn
            )

            result.onSuccess { replies ->
                _uiState.update { current ->
                    current.copy(
                        comments = current.comments.map { comment ->
                            if (comment.id != commentId) {
                                comment
                            } else {
                                val existingIds = comment.replies.mapTo(HashSet()) { it.id }
                                comment.copy(
                                    replies = comment.replies + replies.filter { it.id !in existingIds },
                                    remainingReplyCount = 0
                                )
                            }
                        },
                        expandingCommentIds = current.expandingCommentIds - commentId
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(expandingCommentIds = current.expandingCommentIds - commentId)
                }
            }
        }
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

    /**
     * 加载评论。
     *
     * @param reset true 表示重载首页（清空已有评论并重置游标）；
     *              false 表示追加下一页。
     */
    private fun loadComments(url: String, reset: Boolean) {
        val isFirstPage = reset || commentsCursor == "0"

        if (reset) {
            commentsJob?.cancel()
            commentsCursor = "0"
            _uiState.update {
                it.copy(
                    isCommentsLoading = true,
                    isLoadingMoreComments = false,
                    comments = emptyList(),
                    hasMoreComments = false,
                    commentsError = null,
                    expandingCommentIds = emptySet()
                )
            }
        } else {
            if (loadMoreJob?.isActive == true) return
            _uiState.update { it.copy(isLoadingMoreComments = true) }
        }

        val cursorToUse = if (reset) "0" else commentsCursor

        val job = viewModelScope.launch {
            val result = repository.getArticleComments(
                url = url,
                cursor = cursorToUse,
                sn = commentSn
            )

            result.onSuccess { page ->
                commentsCursor = page.nextCursor
                if (page.sn.isNotBlank()) commentSn = page.sn
                val hasMore = page.nextCursor.isNotEmpty() && page.comments.isNotEmpty()

                _uiState.update { state ->
                    val merged = if (isFirstPage) {
                        page.comments
                    } else {
                        val existingIds = state.comments.mapTo(HashSet()) { it.id }
                        state.comments + page.comments.filter { it.id !in existingIds }
                    }
                    state.copy(
                        comments = merged,
                        isCommentsLoading = false,
                        isLoadingMoreComments = false,
                        hasMoreComments = hasMore,
                        commentsError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCommentsLoading = false,
                        isLoadingMoreComments = false,
                        commentsError = error.localizedMessage ?: "评论加载失败"
                    )
                }
            }
        }

        if (reset) {
            commentsJob = job
        } else {
            loadMoreJob = job
        }
    }
}
