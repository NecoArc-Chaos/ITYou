package com.necoarc.ityou.ui.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.necoarc.ityou.data.model.ArticleComment
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.UploadedCommentImage
import com.necoarc.ityou.data.parser.CommentParser
import com.necoarc.ityou.data.parser.RichNotice
import com.necoarc.ityou.data.repository.ArticleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 待上传的评论附图字节载荷。
 */
data class CommentImagePayload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String = "image/jpeg"
)

@Immutable
data class DetailUiState(
    val articleId: String = "",
    val isLoading: Boolean = true,
    val detail: ArticleDetail? = null,
    val errorMessage: String? = null,
    val comments: List<ArticleComment> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val commentsError: String? = null,
    /** 正在展开剩余回复的父评论 id 集合（用于展示行内加载态）。 */
    val expandingCommentIds: Set<String> = emptySet(),
    /** 正在进行点赞/踩操作的评论 id 集合。 */
    val votingCommentIds: Set<String> = emptySet(),
    /** 评论编辑弹窗是否可见 */
    val isComposerOpen: Boolean = false,
    /** 当前回复的目标评论（null 表示发表主楼评论） */
    val replyTarget: ArticleComment? = null,
    /** 正在提交评论/上传图片中 */
    val isSubmittingComment: Boolean = false,
    /** 提交阶段状态文案（如「正在上传第 1/2 张图片…」） */
    val submittingStatus: String? = null,
    /** 评论编辑弹窗内部的错误或等级限制提示（支持可点击富文本链接，避免被底部抽屉遮挡） */
    val composerNotice: RichNotice? = null,
    /** 服务端返回的当前账号评论权限及限制提示 */
    val canComment: Boolean = true,
    val cannotCommentTip: RichNotice? = null,
    /** 一次性提示消息（提交成功、投票报错等） */
    val actionMessage: String? = null
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

    /** 评论接口 sn 令牌缓存（避免重复抓取 PC 页面）。 */
    private var commentSn: String? = null

    fun loadArticleDetail(
        articleId: String,
        url: String,
        previewTitle: String = "",
        previewAuthor: String = "",
        previewPubTime: String = "",
        forceRefresh: Boolean = false
    ) {
        val request = DetailRequest(articleId, url, previewTitle, previewAuthor, previewPubTime)
        lastRequest = request

        val current = _uiState.value
        if (!forceRefresh && current.articleId == articleId && (current.isLoading || current.detail != null)) return

        if (!forceRefresh) {
            val cached = repository.getArticleDetailFromCache(articleId)
            if (cached != null) {
                _uiState.update {
                    it.copy(
                        articleId = articleId,
                        isLoading = false,
                        detail = cached,
                        errorMessage = null
                    )
                }
                loadComments(url)
                return
            }
        }

        commentSn = null
        runLoad(request, forceRefresh = forceRefresh)
        loadComments(url)
    }

    fun retry() {
        lastRequest?.let {
            commentSn = null
            runLoad(it, forceRefresh = true)
            loadComments(it.url)
        }
    }

    /** 重新加载评论（供评论区错误重试使用）。 */
    fun retryComments() {
        lastRequest?.let { loadComments(it.url) }
    }

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun clearComposerError() {
        _uiState.update { it.copy(composerNotice = null) }
    }

    fun openComposer(replyTarget: ArticleComment? = null) {
        _uiState.update { current ->
            current.copy(
                isComposerOpen = true,
                replyTarget = replyTarget,
                composerNotice = if (!current.canComment) current.cannotCommentTip else null
            )
        }
    }

    fun closeComposer() {
        if (_uiState.value.isSubmittingComment) return
        _uiState.update {
            it.copy(
                isComposerOpen = false,
                replyTarget = null,
                composerNotice = null
            )
        }
    }

    /**
     * 提交评论或回复（支持上传多张附图 + 文本 + `[表情]`）。
     */
    fun submitComment(
        text: String,
        images: List<CommentImagePayload> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        if (_uiState.value.isSubmittingComment) return
        val request = lastRequest ?: return
        val targetReply = _uiState.value.replyTarget
        val replyCommentId = targetReply?.id?.toLongOrNull() ?: 0L

        val newsId = repository.resolveNumericNewsId(
            articleId = request.articleId,
            articleUrl = _uiState.value.detail?.originalUrl?.ifBlank { request.url } ?: request.url
        )
        if (newsId == null) {
            _uiState.update {
                it.copy(composerNotice = RichNotice("暂不支持对此页面发表评论（无法解析文章 ID）"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmittingComment = true,
                    composerNotice = null,
                    submittingStatus = if (images.isNotEmpty()) "准备上传图片…" else "正在发布评论…"
                )
            }

            // 1. 顺序上传附图
            val uploaded = mutableListOf<UploadedCommentImage>()
            for ((index, imgPayload) in images.withIndex()) {
                _uiState.update {
                    it.copy(submittingStatus = "正在上传图片 (${index + 1}/${images.size})…")
                }
                val uploadResult = repository.uploadCommentImage(
                    bytes = imgPayload.bytes,
                    fileName = imgPayload.fileName,
                    mimeType = imgPayload.mimeType
                )
                val imageMeta = uploadResult.getOrElse { err ->
                    _uiState.update {
                        it.copy(
                            isSubmittingComment = false,
                            submittingStatus = null,
                            composerNotice = CommentParser.parseRichNoticeHtml(
                                err.localizedMessage ?: "第 ${index + 1} 张图片上传失败"
                            )
                        )
                    }
                    return@launch
                }
                uploaded.add(imageMeta)
            }

            // 2. 提交评论主体
            _uiState.update { it.copy(submittingStatus = "正在发布评论…") }
            val submitResult = repository.submitComment(
                newsId = newsId,
                text = text,
                images = uploaded,
                replyCommentId = replyCommentId
            )

            submitResult.onSuccess { res ->
                val newComment = res.comment
                if (newComment != null) {
                    _uiState.update { current ->
                        val updatedComments = if (replyCommentId == 0L) {
                            listOf(newComment) + current.comments.filterNot { it.id == newComment.id }
                        } else {
                            insertReplyIntoComments(
                                comments = current.comments,
                                parentOrTargetId = res.parentCommentId.ifBlank { replyCommentId.toString() },
                                fallbackTargetId = replyCommentId.toString(),
                                newReply = newComment
                            )
                        }
                        current.copy(
                            comments = updatedComments,
                            isSubmittingComment = false,
                            submittingStatus = null,
                            composerNotice = null,
                            isComposerOpen = false,
                            replyTarget = null,
                            actionMessage = res.message
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSubmittingComment = false,
                            submittingStatus = null,
                            composerNotice = null,
                            isComposerOpen = false,
                            replyTarget = null,
                            actionMessage = res.message
                        )
                    }
                    loadComments(request.url)
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingComment = false,
                        submittingStatus = null,
                        composerNotice = CommentParser.parseRichNoticeHtml(
                            err.localizedMessage ?: "发布失败，请稍后重试"
                        )
                    )
                }
            }
        }
    }

    /**
     * 切换评论的赞/踩状态（支持取消赞/取消踩及真实票数同步）。
     */
    fun toggleVote(commentId: String, isSupport: Boolean) {
        val state = _uiState.value
        if (commentId in state.votingCommentIds) return
        val targetComment = findCommentById(state.comments, commentId) ?: return

        val alreadyVotedThis = if (isSupport) targetComment.isSupported else targetComment.isAgainst
        val cancel = alreadyVotedThis

        _uiState.update { it.copy(votingCommentIds = it.votingCommentIds + commentId) }

        viewModelScope.launch {
            val result = repository.voteComment(
                commentId = commentId,
                isSupport = isSupport,
                cancel = cancel
            )

            result.onSuccess { serverCount ->
                _uiState.update { current ->
                    current.copy(
                        comments = updateCommentInTree(current.comments, commentId) { c ->
                            if (isSupport) {
                                val nextSupported = !cancel
                                val nextCount = serverCount ?: (c.supportCount + if (nextSupported) 1 else -1).coerceAtLeast(0)
                                c.copy(isSupported = nextSupported, supportCount = nextCount)
                            } else {
                                val nextAgainst = !cancel
                                val nextCount = serverCount ?: (c.againstCount + if (nextAgainst) 1 else -1).coerceAtLeast(0)
                                c.copy(isAgainst = nextAgainst, againstCount = nextCount)
                            }
                        },
                        votingCommentIds = current.votingCommentIds - commentId
                    )
                }
            }.onFailure { err ->
                _uiState.update { current ->
                    current.copy(
                        votingCommentIds = current.votingCommentIds - commentId,
                        actionMessage = err.localizedMessage ?: "操作失败"
                    )
                }
            }
        }
    }

    /**
     * 展开某条评论下剩余的楼中楼回复。
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

    private fun runLoad(request: DetailRequest, forceRefresh: Boolean = false) {
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
                previewPubTime = request.previewPubTime,
                forceRefresh = forceRefresh
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

    private fun loadComments(url: String) {
        commentsJob?.cancel()
        _uiState.update {
            it.copy(
                isCommentsLoading = true,
                comments = emptyList(),
                commentsError = null,
                expandingCommentIds = emptySet()
            )
        }

        commentsJob = viewModelScope.launch {
            val result = repository.getArticleComments(url = url, sn = commentSn)

            result.onSuccess { page ->
                if (page.sn.isNotBlank()) commentSn = page.sn

                _uiState.update { state ->
                    state.copy(
                        comments = page.comments,
                        isCommentsLoading = false,
                        commentsError = null,
                        canComment = page.canComment,
                        cannotCommentTip = page.cannotCommentTip
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCommentsLoading = false,
                        commentsError = error.localizedMessage ?: "评论加载失败"
                    )
                }
            }
        }
    }

    private fun insertReplyIntoComments(
        comments: List<ArticleComment>,
        parentOrTargetId: String,
        fallbackTargetId: String,
        newReply: ArticleComment
    ): List<ArticleComment> {
        return comments.map { comment ->
            val matchesTop = comment.id == parentOrTargetId || comment.id == fallbackTargetId
            val matchesChild = comment.replies.any { it.id == parentOrTargetId || it.id == fallbackTargetId }
            if (matchesTop || matchesChild) {
                val deduped = comment.replies.filterNot { it.id == newReply.id }
                comment.copy(replies = deduped + newReply)
            } else {
                comment
            }
        }
    }

    private fun findCommentById(comments: List<ArticleComment>, id: String): ArticleComment? {
        comments.forEach { comment ->
            if (comment.id == id) return comment
            comment.replies.firstOrNull { it.id == id }?.let { return it }
        }
        return null
    }

    private fun updateCommentInTree(
        comments: List<ArticleComment>,
        targetId: String,
        transform: (ArticleComment) -> ArticleComment
    ): List<ArticleComment> {
        return comments.map { comment ->
            when {
                comment.id == targetId -> transform(comment)
                comment.replies.any { it.id == targetId } -> {
                    comment.copy(
                        replies = comment.replies.map { reply ->
                            if (reply.id == targetId) transform(reply) else reply
                        }
                    )
                }
                else -> comment
            }
        }
    }
}
