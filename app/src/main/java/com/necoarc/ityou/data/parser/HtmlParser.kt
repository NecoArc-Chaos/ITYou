package com.necoarc.ityou.data.parser

import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object HtmlParser {

    /**
     * 根据文章标题与内容自动推断分类
     */
    fun inferCategory(title: String, description: String = ""): ArticleCategory {
        val text = (title + " " + description).lowercase()
        return when {
            text.contains("手机") || text.contains("iphone") || text.contains("android") ||
                text.contains("骁龙") || text.contains("天玑") || text.contains("ios") ||
                text.contains("华为") || text.contains("小米") || text.contains("vivo") || text.contains("oppo") -> ArticleCategory.SMARTPHONE

            text.contains("显卡") || text.contains("cpu") || text.contains("笔记本") ||
                text.contains("intel") || text.contains("amd") || text.contains("rtx") ||
                text.contains("windows") || text.contains("电脑") || text.contains("主机") -> ArticleCategory.PC

            text.contains("ai") || text.contains("大模型") || text.contains("人工智能") ||
                text.contains("gpt") || text.contains("deepseek") || text.contains("算法") -> ArticleCategory.AI

            text.contains("车") || text.contains("特斯拉") || text.contains("智驾") ||
                text.contains("新能源") || text.contains("su7") || text.contains("比亚迪") -> ArticleCategory.AUTOMOTIVE

            text.contains("游戏") || text.contains("steam") || text.contains("ps5") ||
                text.contains("switch") || text.contains("xbox") || text.contains("悟空") -> ArticleCategory.GAME

            text.contains("数码") || text.contains("耳机") || text.contains("相机") ||
                text.contains("手表") || text.contains("平板") -> ArticleCategory.DIGITAL

            else -> ArticleCategory.DIGITAL
        }
    }

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

                val category = inferCategory(title, textSummary)

                if (title.isNotEmpty()) {
                    articles.add(
                        Article(
                            id = articleId,
                            title = title,
                            summary = textSummary,
                            coverImageUrl = imageUrl,
                            author = "IT之家",
                            publishTime = pubDate,
                            category = category,
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
