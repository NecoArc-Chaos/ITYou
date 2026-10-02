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

    /** IT之家 移动端「惠惠 / 导购推广」频道 id，直接过滤以保证信息流纯净。 */
    private const val CID_PROMOTION = 166

    /**
     * 服务端频道 id 到本地分类的高置信度映射。
     *
     * 说明：早期版本完全依赖标题关键词推断分类（例如标题里必须出现 "手机"），
     * 这会造成大量误判与漏判（"奇瑞集团 9 月汽车销售..." 能命中，但
     * "华为 Pura 70 获 HarmonyOS 升级" 只能落到默认分类）。
     * 现在优先使用服务端频道 id，仅当 id 未知时才回退到关键词推断。
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

    /**
     * 依据服务端频道 id 推断分类，未知频道返回 null（交由关键词推断兜底）。
     */
    fun categoryFromCid(cid: Int): ArticleCategory? = CID_CATEGORY_MAP[cid]

    /**
     * 清洗图片地址。
     *
     * IT之家 缩略图形如 `.../1009170_240.jpg?r=1790870763077`，
     * 其中 `r` 是每次请求都变化的时间戳缓存破坏参数。
     * 如果原样交给 Coil，内存/磁盘缓存键每页都不同 → 反复解码、反复下载，
     * 这是滚动卡顿的隐蔽来源之一。这里统一剔除该参数。
     */
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

    /**
     * 根据文章标题与内容推断分类（仅在服务端频道 id 未知时使用）。
     * 优先级：手机 > PC > 汽车 > 游戏 > AI > 数码 > 默认数码
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
     * 解析移动端 API 返回的 JSON 列表数据（支持流式分页）。
     *
     * 返回的列表保持服务端顺序（新 → 旧），并已剔除导购与插播推广内容。
     */
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

                // ---- 纯净度过滤：导购频道 / lapin 推广域名 / 广告标记 ----
                if (cid == CID_PROMOTION) continue
                if (rawUrl.contains("lapin.ithome.com")) continue
                if (item.optBoolean("isad", false)) continue
                if (hasAdTip(item)) continue

                // 部分条目 url 字段为空（例如直播/专题），此时从 WapNewsUrl 或 newsid 兜底构造
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
        } catch (_: Exception) {
            // 解析容错：单条数据异常不应导致整页失败
        }
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
            // orderdate 形如 2026-10-01T23:47:33.417，需去掉毫秒
            format.parse(rawOrderDate.substringBefore('.'))?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * 解析 RSS XML 获取文章列表（接口不可用时的备用数据源）。
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
        } catch (_: Exception) {
            // 解析容错
        }
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
            } catch (_: Exception) {
                // 尝试下一种格式
            }
        }
        return System.currentTimeMillis()
    }

    /**
     * 仿照 ReadYou 的 DOM 递归解析算法，将 HTML 正文精准转为 ContentBlock 原生树。
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
                        imageBlockOf(img)?.let(blocks::add)
                    } else {
                        // 递归处理 <p> 内的嵌套元素（如 <strong>/<a>/<br>）
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

    /**
     * 将 `<img>` 转为 Image 块，并尽力解析宽高以提前预留布局空间。
     * IT之家 正文图片普遍带 `w` / `h` 属性。若缺少宽高，则 aspectRatio 为 null，
     * 由 UI 侧做高度上限保护（避免 Coil 按原图尺寸解码超大图）。
     */
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
