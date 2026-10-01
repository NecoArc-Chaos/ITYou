package com.necoarc.ityou.data.model

import java.util.Date

/**
 * 首页文章流模型
 */
data class Article(
    val id: String,
    val title: String,
    val summary: String = "",
    val coverImageUrl: String? = null,
    val author: String = "IT之家",
    val publishTime: String = "",
    val publishedDate: Date = Date(),
    val category: ArticleCategory = ArticleCategory.ALL,
    val commentCount: Int = 0,
    val url: String = "",
    val isStarred: Boolean = false,
    val isRead: Boolean = false,
    val orderTimestamp: Long = 0L // 用于分页瀑布流游标加载
)

/**
 * 文章分类枚举
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
 * 仿 ReadYou 的原生 Compose 结构化排版块
 */
sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Heading(val text: String, val level: Int = 2) : ContentBlock
    data class Image(val url: String, val caption: String? = null) : ContentBlock
    data class BlockQuote(val text: String) : ContentBlock
    data class CodeBlock(val code: String, val language: String = "") : ContentBlock
    data class Divider(val text: String = "") : ContentBlock
}

/**
 * 文章详情完整模型
 */
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
