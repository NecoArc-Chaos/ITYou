package com.necoarc.ityou.data.model

import androidx.compose.runtime.Immutable

/**
 * 首页文章流模型。
 *
 * 性能约定（非常重要）：
 * 1. 必须保持 **全部字段为不可变类型**，并标注 [Immutable]，
 *    否则 Compose 会把 [Article] 推断为 unstable，导致 `ArticleCard` 失去
 *    "跳过重组" 能力（父级每次重组都会全量重跑所有可见卡片，包含中文文本测量）。
 * 2. 避免引入 `java.util.Date` 等可变第三方类型。
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
 * 详情页相关文章条目模型
 */
@Immutable
data class RelatedArticle(
    val id: String,
    val title: String,
    val url: String,
    val coverImageUrl: String? = null,
    val publishTime: String = ""
)

/**
 * 仿 ReadYou 的原生 Compose 结构化排版块。
 */
@Immutable
sealed interface ContentBlock {
    @Immutable
    data class Paragraph(val text: String) : ContentBlock

    @Immutable
    data class Heading(val text: String, val level: Int = 2) : ContentBlock

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
    val relatedArticles: List<RelatedArticle> = emptyList(),
    val commentCount: Int = 0,
    val originalUrl: String = "",
    val isStarred: Boolean = false,
    val readingProgress: Float = 0f
)

/**
 * 一页文章数据（分页结果）。
 */
@Immutable
data class ArticlePage(
    val articles: List<Article> = emptyList(),
    val nextCursor: Long = 0L,
    val hasMore: Boolean = false
)
