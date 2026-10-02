package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * 首页文章流模型。
 *
 * 性能约定（非常重要）：
 * 1. 必须保持 **全部字段为不可变类型**，并标注 [Immutable]，
 *    否则 Compose 会把 [Article] 推断为 unstable，导致 `ArticleCard` 失去
 *    "跳过重组" 能力（父级每次重组都会全量重跑所有可见卡片，包含中文文本测量）。
 * 2. 因此这里刻意 **不持有 `java.util.Date`**（Java 日期是可变的第三方类型，
 *    会让整个数据类被判定为 unstable）。时间统一用 [orderTimestamp]（毫秒）。
 */
@Immutable
data class Article(
    val id: String,
    val title: String,
    val summary: String = "",
    val coverImageUrl: String? = null,
    val author: String = "IT之家",
    val publishTime: String = "",
    val category: ArticleCategory = ArticleCategory.ALL,
    val commentCount: Int = 0,
    val url: String = "",
    val isStarred: Boolean = false,
    val isRead: Boolean = false,
    /**
     * 服务端排序时间戳（毫秒）。同时用于无限分页的游标（cursor）。
     */
    val orderTimestamp: Long = 0L
)

/**
 * 文章分类枚举。
 */
enum class ArticleCategory(val title: String) {
    ALL("全部"),
    DIGITAL("数码"),
    SMARTPHONE("手机"),
    PC("电脑"),
    AI("人工智能"),
    AUTOMOTIVE("汽车"),
    GAME("游戏")
}

/**
 * 仿 ReadYou 的原生 Compose 结构化排版块。
 */
@Immutable
sealed interface ContentBlock {
    @Immutable
    data class Paragraph(val text: String) : ContentBlock

    @Immutable
    data class Heading(val text: String, val level: Int = 2) : ContentBlock

    /**
     * @param aspectRatio 图片宽高比（width / height），来自 IT之家正文 `<img w h>` 属性。
     * 提前预留高度可彻底消除正文图片加载完成后的列表回流与跳动；
     * 为 null 时代表未知，UI 侧会做高度上限保护。
     */
    @Immutable
    data class Image(
        val url: String,
        val caption: String? = null,
        val aspectRatio: Float? = null
    ) : ContentBlock

    @Immutable
    data class BlockQuote(val text: String) : ContentBlock

    @Immutable
    data class CodeBlock(val code: String, val language: String = "") : ContentBlock

    @Immutable
    data class Divider(val text: String = "") : ContentBlock
}

/**
 * 文章详情完整模型。
 */
@Immutable
data class ArticleDetail(
    val id: String,
    val title: String,
    val author: String,
    val publishTime: String,
    val source: String = "IT之家",
    val contentBlocks: List<ContentBlock> = emptyList(),
    val commentCount: Int = 0,
    val originalUrl: String = "",
    val isStarred: Boolean = false,
    val readingProgress: Float = 0f
)

/**
 * 一页文章数据（分页结果）。
 *
 * @param articles 本页（已完成分类过滤）的文章
 * @param nextCursor 下一页游标，直接来自 **服务端原始页** 的最旧时间戳，
 * 因此即使整页内容都被分类过滤掉，游标依旧会前进，不会卡在同一页。
 * @param hasMore 数据源是否还有更多内容
 */
@Immutable
data class ArticlePage(
    val articles: List<Article> = emptyList(),
    val nextCursor: Long = 0L,
    val hasMore: Boolean = false
)
