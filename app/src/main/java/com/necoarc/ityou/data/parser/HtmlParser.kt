package com.necoarc.ityou.data.parser

import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import com.necoarc.ityou.data.model.RelatedArticle
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object HtmlParser {

    /** IT之家 移动端「惠惠 / 导购推广」频道 id，直接过滤以保证信息流纯净。 */
    private const val CID_PROMOTION = 166

    /**
     * 服务端频道 id 到本地分类的高置信度映射。
     */
    private val CID_CATEGORY_MAP: Map<Int, ArticleCategory> = mapOf(
        185 to ArticleCategory.SMARTPHONE,
        186 to ArticleCategory.SMARTPHONE,
        100 to ArticleCategory.PC,
        91 to ArticleCategory.PC,
        183 to ArticleCategory.PC,
        192 to ArticleCategory.AUTOMOTIVE,
        160 to ArticleCategory.AUTOMOTIVE,
        76 to ArticleCategory.GAME,
        149 to ArticleCategory.GAME,
        32 to ArticleCategory.GAME,
        200 to ArticleCategory.AI,
        177 to ArticleCategory.DIGITAL,
        56 to ArticleCategory.DIGITAL,
        130 to ArticleCategory.DIGITAL
    )

    fun categoryFromCid(cid: Int): ArticleCategory? = CID_CATEGORY_MAP[cid]

    fun cleanImageUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val url = rawUrl.trim()
        if (!url.startsWith("http")) return null
        val queryIndex = url.indexOf('?')
        if (queryIndex < 0) return url

        val base = url.substring(0, queryIndex)
        val query = url.substring(queryIndex + 1)
        val kept = query
            .split('&')
            .filter { it.isNotEmpty() && !it.startsWith("r=") }
            .joinToString("&")

        return if (kept.isEmpty()) base else "$base?$kept"
    }

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

    fun parseJsonNews(jsonContent: String): List<Article> {
        val articles = mutableListOf<Article>()
        try {
            val root = JSONObject(jsonContent)
            if (root.optInt("Success") != 1) return emptyList()
            val list = root.optJSONArray("Result") ?: return emptyList()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("GMT+08:00")
            }

            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue

                val newsId = item.optLong("newsid").toString()
                val title = item.optString("title").trim()
                if (title.isEmpty() || newsId == "0") continue

                val description = item.optString("description").trim()
                val coverImage = cleanImageUrl(item.optString("image"))
                val orderDateStr = item.optString("orderdate")
                val postDateStr = item.optString("PostDateStr").ifEmpty { "刚刚" }
                val commentCount = item.optInt("commentcount", 0)
                val rawUrl = item.optString("url")
                val wapUrl = item.optString("WapNewsUrl")
                val cid = item.optInt("cid", -1)

                // 过滤纯推广与广告
                if (cid == CID_PROMOTION) continue
                if (rawUrl.contains("lapin.ithome.com")) continue
                if (item.optBoolean("isad", false)) continue
                if (hasAdTip(item)) continue

                val fullUrl = when {
                    rawUrl.startsWith("http") -> rawUrl
                    rawUrl.startsWith("/") -> "https://www.ithome.com$rawUrl"
                    wapUrl.startsWith("http") -> wapUrl
                    else -> "https://m.ithome.com/html/$newsId.htm"
                }

                val orderTimestamp = parseOrderTimestamp(orderDateStr, dateFormat)

                articles.add(
                    Article(
                        id = newsId,
                        title = title,
                        summary = description,
                        coverImageUrl = coverImage,
                        author = "IT之家",
                        publishTime = postDateStr,
                        category = categoryFromCid(cid) ?: inferCategory(title, description),
                        commentCount = commentCount,
                        url = fullUrl,
                        orderTimestamp = orderTimestamp
                    )
                )
            }
        } catch (_: Exception) {}
        return articles
    }

    private fun hasAdTip(item: JSONObject): Boolean {
        val tips = item.optJSONArray("NewsTips") ?: return false
        for (t in 0 until tips.length()) {
            val tipName = tips.optJSONObject(t)?.optString("TipName").orEmpty()
            if (tipName == "广告") return true
        }
        return false
    }

    private fun parseOrderTimestamp(rawOrderDate: String, format: SimpleDateFormat): Long {
        if (rawOrderDate.isBlank()) return System.currentTimeMillis()
        return try {
            format.parse(rawOrderDate.substringBefore('.'))?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

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
                val imageUrl = cleanImageUrl(descDoc.selectFirst("img")?.attr("src"))
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
                            orderTimestamp = rssDateToTimestamp(pubDate)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return articles
    }

    private fun rssDateToTimestamp(pubDate: String): Long {
        if (pubDate.isBlank()) return System.currentTimeMillis()
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                val parsed = format.parse(pubDate)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    /**
     * 仿照 ReadYou 的 DOM 递归解析算法，将 HTML 正文精准转为 ContentBlock 原生树，
     * 并解析底部「相关文章」列表。
     */
    fun parseArticleDetail(html: String, id: String, fallbackUrl: String = ""): ArticleDetail {
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.post-title, h1.title, .post_title")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: "无标题"

        val author = doc.selectFirst("#author_baidu, .author, .post-author")?.text() ?: "IT之家"
        val pubTime = doc.selectFirst("#pubtime_baidu, .pubtime, .time")?.text() ?: ""

        val blocks = mutableListOf<ContentBlock>()
        val contentElement = resolveContentElement(doc)

        if (contentElement != null) {
            parseElementRecursive(contentElement, blocks)
        }

        // 解析移动端与 PC 端的相关文章列表
        val relatedArticles = parseRelatedArticles(doc)

        return ArticleDetail(
            id = id,
            title = title,
            author = author,
            publishTime = pubTime,
            source = "IT之家",
            contentBlocks = blocks,
            relatedArticles = relatedArticles,
            originalUrl = fallbackUrl
        )
    }

    /**
     * 精准定位正文容器。
     *
     * 背景：IT之家 PC 端正文 HTML 结构为
     * `<div class="fl content">` 外层包裹 `<h1>标题</h1>`、`#paragraph/.post_content` 正文、
     * 以及 `.related_post`（内含 `<h2>相关文章</h2>`）等多个兄弟节点。
     * 若直接用 `.content` 命中外层容器再递归解析，会把「标题 h1」与「相关文章 h2」
     * 一并当作正文 Heading 混入 [blocks]，导致详情页标题 / 「相关文章」标题重复出现。
     *
     * 因此这里：
     * 1. 优先命中精确正文容器（PC 端 `#paragraph` / `.post_content`，移动端 `.news-content`）。
     * 2. 仅当以上都缺失时，才回退到 `.content`，并主动剔除导航/元信息/相关文章等噪声节点。
     */
    private fun resolveContentElement(doc: Document): Element? {
        doc.selectFirst("#paragraph, .post_content, .news-content")?.let { return it }

        return doc.selectFirst(".content")?.also { wrapper ->
            wrapper.select(
                ".related_post, #related_post, .relevant-news, .newserror, " +
                    ".newsgrade, .shareto, .bdsharebuttonbox, .cv, .info, .down_app, h1"
            ).remove()
        }
    }

    /**
     * 从详情页 HTML 中提取 PC 评论接口所需的 `sn` 令牌。
     *
     * PC 评论接口（`cmt.ithome.com/api/webcomment/*`）依赖 PC 页面内嵌的 `sn`：
     * ```html
     * <div id="post_comm" data-id="628f56baadfd8115"></div>
     * ```
     * 该令牌同时用于：评论分页（`getnewscomment?cid=`）与楼中楼展开（`getcommentcontent`）。
     *
     * @return sn 令牌；页面结构变更导致提取失败时返回 null
     */
    fun extractCommentSn(html: String): String? =
        PC_SN_PATTERN.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /**
     * 从移动端详情页 HTML 中提取 canonical 的 PC 版 URL。
     *
     * 移动端页面会通过 `<link rel="canonical" href="https://www.ithome.com/...">`
     * 声明对应的 PC 地址，据此可直接抓取 PC 页面以获取 `sn`。
     *
     * @return PC 版 URL；未声明时返回 null
     */
    fun extractCanonicalUrl(html: String): String? =
        CANONICAL_PATTERN.find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }

    private val PC_SN_PATTERN = Regex("""id="post_comm"[^>]*data-id="([^"]+)"""")

    private val CANONICAL_PATTERN = Regex("""<link[^>]*rel="canonical"[^>]*href="([^"]+)"""")

    /**
     * 解析页面中的「相关文章」区块。
     * 兼容移动端 (`.relevant-news, .relevant-news-box, .placeholder`)
     * 与 PC 网页端 (`.related_post, .list_3, #related_post`)。
     */
    private fun parseRelatedArticles(doc: Document): List<RelatedArticle> {
        val results = mutableListOf<RelatedArticle>()

        // 1. 移动版 m.ithome.com 样式
        val mItems = doc.select(".relevant-news-box .placeholder, .relevant-news .placeholder")
        for (item in mItems) {
            val a = item.selectFirst("a") ?: continue
            val href = a.attr("href").trim()
            val title = item.selectFirst(".plc-title, p.title")?.text()?.trim().orEmpty()
            if (title.isEmpty() || href.isEmpty()) continue

            val rawImg = item.selectFirst("img")?.let { img ->
                img.attr("data-original").ifEmpty { img.attr("src") }
            }
            val coverImg = cleanImageUrl(rawImg)
            val pubTime = item.selectFirst(".plc-footer span, .plc-time")?.text()?.trim().orEmpty()

            val relId = item.attr("data-order-newsId").ifEmpty {
                href.substringAfterLast("/").substringBefore(".htm")
            }

            val fullUrl = if (href.startsWith("http")) href else "https://m.ithome.com$href"

            results.add(
                RelatedArticle(
                    id = relId.ifEmpty { fullUrl.hashCode().toString() },
                    title = title,
                    url = fullUrl,
                    coverImageUrl = coverImg,
                    publishTime = pubTime
                )
            )
        }

        if (results.isNotEmpty()) return results

        // 2. PC版 ithome.com 样式
        val pcItems = doc.select(".related_post ul li, #related_post ul li")
        for (item in pcItems) {
            val a = item.selectFirst("a") ?: continue
            val href = a.attr("href").trim()
            val title = a.text().trim()
            if (title.isEmpty() || href.isEmpty()) continue

            val relId = href.substringAfterLast("/").substringBefore(".htm")
            val fullUrl = if (href.startsWith("http")) href else "https://www.ithome.com$href"

            results.add(
                RelatedArticle(
                    id = relId.ifEmpty { fullUrl.hashCode().toString() },
                    title = title,
                    url = fullUrl,
                    coverImageUrl = null,
                    publishTime = ""
                )
            )
        }

        return results
    }

    private fun parseElementRecursive(parent: Element, blocks: MutableList<ContentBlock>) {
        for (child in parent.children()) {
            when (child.tagName().lowercase()) {
                "p" -> {
                    val img = child.selectFirst("img")
                    if (img != null) {
                        imageBlockOf(img)?.let(blocks::add)
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
                "img" -> imageBlockOf(child)?.let(blocks::add)
                "div", "section", "article" -> parseElementRecursive(child, blocks)
            }
        }
    }

    private fun imageBlockOf(img: Element): ContentBlock.Image? {
        val src = cleanImageUrl(
            img.attr("data-original").ifEmpty { img.attr("src") }
        ) ?: return null

        val width = img.attr("w").toFloatOrNull()
            ?: img.attr("width").toFloatOrNull()
            ?: img.attr("data-w").toFloatOrNull()
        val height = img.attr("h").toFloatOrNull()
            ?: img.attr("height").toFloatOrNull()
            ?: img.attr("data-h").toFloatOrNull()

        val aspectRatio = if (width != null && height != null && width > 0f && height > 0f) {
            width / height
        } else {
            null
        }

        return ContentBlock.Image(
            url = src,
            caption = img.attr("alt").ifEmpty { null },
            aspectRatio = aspectRatio
        )
    }
}
