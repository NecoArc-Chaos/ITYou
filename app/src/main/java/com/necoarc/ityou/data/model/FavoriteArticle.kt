package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * 本地收藏的文章。
 *
 * 仅保存列表与跳转所需的**最小字段**，不缓存正文：
 * 正文可能随时更新，且体积大；收藏的意义是「记住这篇」，
 * 重新打开时仍走正常的详情加载流程。
 *
 * 标注 [Immutable] 以便 Compose 跳过不必要的重组。
 */
@Immutable
data class FavoriteArticle(
    /** 文章唯一 id，作为收藏项的主键。 */
    val id: String,
    /** 文章标题。 */
    val title: String,
    /** 文章作者，可能为空。 */
    val author: String,
    /** 发布时间（展示用原文，可能是相对时间或日期串）。 */
    val pubTime: String,
    /** 文章链接，用于重新打开详情与分享。 */
    val url: String,
    /** 封面图，可能为空。 */
    val coverUrl: String? = null,
    /** 收藏时间戳（毫秒），用于按收藏时间倒序排列。 */
    val favoritedAt: Long
)
