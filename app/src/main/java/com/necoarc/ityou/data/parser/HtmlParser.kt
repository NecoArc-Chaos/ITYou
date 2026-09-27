package com.necoarc.ityou.data.parser

import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HtmlParser {

    /**
     * 解析 RSS XML 获取文章列表
     */
    fun parseRss(xmlContent: String): List<Article> {
        val articles = mutableListOf<Article>()
        try {
            val doc: Document = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser())
            val items = doc.select("item")
            for (item in items) {
                val title = item.selectFirst("title")?.text().orEmpty()
                val link = item.selectFirst("link")?.text().orEmpty()
                val pubDate = item.selectFirst("pubDate")?.text().orEmpty()
                val description = item.selectFirst("description")?.text().orEmpty()

                // 从 description 中抽取首张图片与摘要纯文本
                val descDoc = Jsoup.parse(description)
                val imageUrl = descDoc.selectFirst("img")?.attr("src")
                val textSummary = descDoc.text()

                val articleId = link.substringAfterLast("/").substringBefore(".htm").ifEmpty {
                    link.hashCode().toString()
                }

                if (title.isNotEmpty()) {
                    articles.add(
                        Article(
                            id = articleId,
                            title = title,
                            summary = textSummary,
                            coverImageUrl = imageUrl,
                            author = "IT之家",
                            publishTime = pubDate,
                            category = ArticleCategory.ALL,
                            url = link
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // 解析容错
        }
        return articles
    }

    /**
     * 解析 IT 之家网页正文 HTML 为结构化 ContentBlock
     */
    fun parseArticleDetail(html: String, id: String, fallbackUrl: String = ""): ArticleDetail {
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.post-title, h1.title, .post_title")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: "无标题"

        val author = doc.selectFirst("#author_baidu, .author, .post-author")?.text() ?: "IT之家"
        val pubTime = doc.selectFirst("#pubtime_baidu, .pubtime, .time")?.text() ?: ""

        val blocks = mutableListOf<ContentBlock>()
        val contentElement = doc.selectFirst("#paragraph, .post_content, .content")

        if (contentElement != null) {
            for (child in contentElement.children()) {
                when (child.tagName().lowercase()) {
                    "p" -> {
                        val img = child.selectFirst("img")
                        if (img != null) {
                            val src = img.attr("data-original").ifEmpty { img.attr("src") }
                            if (src.isNotEmpty()) {
                                blocks.add(ContentBlock.Image(url = src))
                            }
                        } else {
                            val text = child.text().trim()
                            if (text.isNotEmpty()) {
                                blocks.add(ContentBlock.Paragraph(text = text))
                            }
                        }
                    }
                    "h2", "h3" -> {
                        val text = child.text().trim()
                        if (text.isNotEmpty()) {
                            blocks.add(ContentBlock.Heading(text = text, level = if (child.tagName() == "h2") 2 else 3))
                        }
                    }
                    "blockquote" -> {
                        val text = child.text().trim()
                        if (text.isNotEmpty()) {
                            blocks.add(ContentBlock.BlockQuote(text = text))
                        }
                    }
                    "img" -> {
                        val src = child.attr("data-original").ifEmpty { child.attr("src") }
                        if (src.isNotEmpty()) {
                            blocks.add(ContentBlock.Image(url = src))
                        }
                    }
                }
            }
        }

        return ArticleDetail(
            id = id,
            title = title,
            author = author,
            publishTime = pubTime,
            source = "IT之家",
            contentBlocks = blocks,
            originalUrl = fallbackUrl
        )
    }
}
