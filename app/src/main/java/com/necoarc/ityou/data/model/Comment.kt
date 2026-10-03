package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * IT之家文章评论模型。
 *
 * 性能约定（非常重要）：
 * 1. 必须保持 **全部字段为不可变类型**，并标注 [Immutable]，
 *    否则 Compose 会把 [ArticleComment] 推断为 unstable，导致评论流
 *    每次重组都重新渲染所有可见评论。
 * 2. 避免引入可变类型。
 * 3. [replies] 为不可变列表，仅包含已获取到的楼中楼回复。
 *
 * 数据来源：
 * - 列表：移动端 `/api/comment/newscommentlistget`
 * - 剩余回复按需加载：PC 端 `/api/webcomment/getcommentcontent`
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
    /** 楼中楼回复（列表接口会内联返回大部分；剩余部分可按需加载）。 */
    val replies: List<ArticleComment> = emptyList(),
    /**
     * 服务端报告「尚未内联」的回复数量。
     *
     * 实测：IT之家两个接口目前都一次性内联全部回复，该值通常为 0。
     * 保留该字段是为了**防御性兼容**——一旦服务端改为分页返回，
     * 评论区仍能通过 [ArticleComment] 上的「加载剩余回复」按需补齐。
     */
    val remainingReplyCount: Int = 0,
    /** 被回复者昵称（楼中楼专用，"回复 @某人"）。 */
    val replyToAuthor: String? = null
) {
    /** 回复总数（已获取 + 未获取）。 */
    val totalReplyCount: Int get() = replies.size + remainingReplyCount
}
