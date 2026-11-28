package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * 评论内嵌图片（如用户附图发表的评论图片）。
 */
@Immutable
data class CommentImage(
    val url: String,
    val width: Int = 0,
    val height: Int = 0
)

/**
 * 已上传到 IT之家 图床、待随评论正文一起提交的图片元数据。
 */
@Immutable
data class UploadedCommentImage(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val flag: Int = 0
)

/**
 * 提交评论结果。
 *
 * @param comment 服务端直接返回的已落库评论节点；部分情况下可能为 null（此时客户端回退刷新评论流）
 * @param message 服务端提示文案（例如「发布成功」）
 * @param parentCommentId 所属主楼评论 id（0 或空表示为主楼评论）
 */
@Immutable
data class CommentSubmitResult(
    val comment: ArticleComment? = null,
    val message: String = "发布成功",
    val parentCommentId: String = ""
)

/**
 * IT之家文章评论模型。
 *
 * 性能约定（非常重要）：
 * 1. 必须保持 **全部字段为不可变类型**，并标注 [Immutable]，
 *    否则 Compose 会把 [ArticleComment] 推断为 unstable，导致评论流
 *    每次重组都重新渲染所有可见评论。
 * 2. 避免引入可变类型。
 * 3. [replies] 为不可变列表，仅包含已获取到的楼中楼回复。
 */
@Immutable
data class ArticleComment(
    val id: String,
    val author: String,
    val avatarUrl: String?,
    val location: String,
    val publishTime: String,
    val floor: String,
    val content: String,
    val supportCount: Int,
    val againstCount: Int,
    /** 评论附图列表 */
    val images: List<CommentImage> = emptyList(),
    /** 当前用户是否已点「支持」 */
    val isSupported: Boolean = false,
    /** 当前用户是否已点「反对」 */
    val isAgainst: Boolean = false,
    /** 楼中楼回复（列表接口会内联返回大部分；剩余部分可按需加载）。 */
    val replies: List<ArticleComment> = emptyList(),
    /**
     * 服务端报告「尚未内联」的回复数量。
     */
    val remainingReplyCount: Int = 0,
    /** 被回复者昵称（楼中楼专用，"回复 @某人"）。 */
    val replyToAuthor: String? = null
) {
    /** 回复总数（已获取 + 未获取）。 */
    val totalReplyCount: Int get() = replies.size + remainingReplyCount
}
