package com.necoarc.ityou.data.parser

import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object HtmlParser {

    /**
     * 根据文章标题与内容自动推断分类。
     * 优先级从高到低：手机 > PC > 汽车 > 游戏 > AI > 数码 > 默认数码
     */
    fun inferCategory(title: String, description: String = ""): ArticleCategory {
        val text = (title + " " + description).lowercase()
        return when {
            text.contains("手机") || text.contains("iphone") ||
                text.contains("骁龙") || text.contains("天玑") ||
                text.contains("华为") || text.contains("vivo") || text.contains("oppo") -> ArticleCategory.SMARTPHONE

            text.contains("显卡") || text.contains("cpu") || text.contains("笔记本") ||
                text.contains("intel") || text.contains("amd") || text.contains("rtx") ||
                text.contains("windows") || text.contains("电脑") || text.contains("主机") -> ArticleCategory.PC

            text.contains("汽车") || text.contains("特斯拉") || text.contains("智驾") ||
                text.contains("新能源") || text.contains("su7") || text.contains("比亚迪") -> ArticleCategory.AUTOMOTIVE

            text.contains("游戏") || text.contains("steam") || text.contains("ps5") ||
                text.contains("xbox") || text.contains("悟空") -> ArticleCategory.GAME

            text.contains("人工智能") || text.contains("大模型") || text.contains("gpt") ||
                text.contains("deepseek") || text.contains("算法") -> ArticleCategory.AI

            text.contains("数码") || text.contains("耳机") || text.contains("相机") ||
                text.contains("手表") || text.contains("平板") -> ArticleCategory.DIGITAL

            else -> ArticleCategory.DIGITAL
        }
    }

    /**
     * 解析移动端 API 返回的 JSON 列表数据（支持流式分页）
     */
    fun parseJsonNews(jsonContent: String): List<Article> {
        val articles = mutableListOf<Article>()
        try {
            val root = JSONObject(jsonContent)
            if (root.optInt("Success") != 1) return emptyList()
            val list = root.optJSONArray("Result") ?: return emptyList()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("GMT+8")
            }

            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val newsId = item.optLong("newsid").toString()
                val title = item.optString("title").trim()
                val description = item.optString("description").trim()
                val image = item.optString("image").ifEmpty { null }
                val orderDateStr = item.optString("orderdate")
                val postDateStr = item.optString("PostDateStr").ifEmpty { "刚刚" }
                val commentCount = item.optInt("commentcount", 0)
                val rawUrl = item.optString("url")
                val wapUrl = item.optString("WapNewsUrl")

                // 过滤导购与纯广告流条目
                val tips = item.optJSONArray("NewsTips")
                var isAd = item.optBoolean("isad", false)
                if (tips != null) {
                    for (t in 0 until tips.length()) {
                        val tipObj = tips.optJSONObject(t)
                        if (tipObj?.optString("TipName") == "广告") {
                            isAd = true
                            break
                        }
                    }
                }
                if (isAd || rawUrl.contains("lapin.ithome.com")) {
                    continue
                }

                val fullUrl = when {
                    rawUrl.startsWith("http") -> rawUrl
                    rawUrl.isNotEmpty() -> "https://www.ithome.com$rawUrl"
                    wapUrl.isNotEmpty() -> wapUrl
                    else -> "https://www.ithome.com/0/${newsId.take(3)}/${newsId.takeLast(3)}.htm"
                }

                val orderTimestamp = try {
                    val cleanDate = orderDateStr.substringBefore(".")
                    dateFormat.parse(cleanDate)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }

                if (title.isNotEmpty()) {
                    articles.add(
                        Article(
                            id = newsId,
                            title = title,
                            summary = description,
                            coverImageUrl = image,
                            author = "IT之家",
                            publishTime = postDateStr,
                            category = inferCategory(title, description),
                            commentCount = commentCount,
                            url = fullUrl,
                            orderTimestamp = orderTimestamp
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

                val descDoc = Jsoup.parse(description)
                val imageUrl = descDoc.selectFirst("img")?.attr("src")
                val textSummary = descDoc.text().replace("\\s+".toRegex(), " ").trim()

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
                            category = inferCategory(title, textSummary),
                            url = link,
                            orderTimestamp = System.currentTimeMillis()
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
     * 仿照 ReadYou 的 DOM 递归解析算法
     * 将 HTML 文章正文精准转为 ContentBlock 原生树
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
            parseElementRecursive(contentElement, blocks)
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

    private fun parseElementRecursive(parent: Element, blocks: MutableList<ContentBlock>) {
        for (child in parent.children()) {
            when (child.tagName().lowercase()) {
                "p" -> {
                    val img = child.selectFirst("img")
                    if (img != null) {
                        val src = img.attr("data-original").ifEmpty { img.attr("src") }
                        if (src.isNotEmpty()) {
                            blocks.add(ContentBlock.Image(url = src, caption = img.attr("alt").ifEmpty { null }))
                        }
                    } else {
                        val text = child.text().trim()
                        if (text.isNotEmpty()) blocks.add(ContentBlock.Paragraph(text = text))
                    }
                }
                "h1", "h2" -> {
                    val text = child.text().trim()
                    if (text.isNotEmpty()) blocks.add(ContentBlock.Heading(text = text, level = 2))
                }
                "h3", "h4", "h5", "h6" -> {
                    val text = child.text().trim()
                    if (text.isNotEmpty()) blocks.add(ContentBlock.Heading(text = text, level = 3))
                }
                "blockquote" -> {
                    val text = child.text().trim()
                    if (text.isNotEmpty()) blocks.add(ContentBlock.BlockQuote(text = text))
                }
                "pre", "code" -> {
                    val code = child.text().trim()
                    if (code.isNotEmpty()) blocks.add(ContentBlock.CodeBlock(code = code))
                }
                "hr" -> blocks.add(ContentBlock.Divider())
                "img" -> {
                    val src = child.attr("data-original").ifEmpty { child.attr("src") }
                    if (src.isNotEmpty()) {
                        blocks.add(ContentBlock.Image(url = src, caption = child.attr("alt").ifEmpty { null }))
                    }
                }
                "div", "section", "article" -> parseElementRecursive(child, blocks)
            }
        }
    }
}
