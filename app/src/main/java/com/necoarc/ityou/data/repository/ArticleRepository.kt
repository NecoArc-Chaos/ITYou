package com.necoarc.ityou.data.repository

import androidx.compose.runtime.Immutable
import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticleComment
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ArticlePage
import com.necoarc.ityou.data.parser.CommentPageResult
import com.necoarc.ityou.data.parser.CommentParser
import com.necoarc.ityou.data.parser.HtmlParser
import com.necoarc.ityou.data.remote.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 服务端「一页原始数据」（尚未做分类过滤）。
 *
 * @param articles 原始文章（新 → 旧）
 * @param nextCursor 下一页游标 = 本页最旧一条的时间戳
 * @param hasMore 服务端是否还有更多
 */
@Immutable
data class RawNewsPage(
    val articles: List<Article> = emptyList(),
    val nextCursor: Long = 0L,
    val hasMore: Boolean = true
)

open class ArticleRepository(
    /**
     * 时钟。抽成可注入的依赖以便单元测试断言 URL 中的游标，
     * 而不是让 `System.currentTimeMillis()` 直接把行为变成不可验证的。
     */
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val client get() = NetworkClient.client

    /**
     * 拉取「某个分类的下一个页面」。
     *
     * 关键设计（修复历史缺陷）：
     * 分类过滤是在客户端完成的。早期实现把游标取自 *过滤后* 的最后一条，
     * 一旦某一整页都不属于当前分类，游标就不会前进 ——
     * 表现为「某些分类只能刷出很少文章，而且再也翻不动」。
     *
     * 现在游标永远取自 **服务端原始页**，并在单次调用内部连续翻页，
     * 直到收集到足够多的匹配项或数据源耗尽。
     *
     * @param cursor 0 表示从最新开始；否则为上一页返回的 [ArticlePage.nextCursor]
     * @param forceRefresh 为 true 时绕过 HTTP 缓存强制回源。
     *   下拉刷新与「切换分类」这类用户明确要求「拿最新」的场景应传 true，
     *   否则会命中资讯接口的 60 秒短缓存，看起来像刷新没生效。
     */
    open suspend fun getArticlePage(
        category: ArticleCategory = ArticleCategory.ALL,
        cursor: Long = 0L,
        forceRefresh: Boolean = false
    ): Result<ArticlePage> {
        val isFirstPage = cursor <= 0L
        val maxPages = if (category == ArticleCategory.ALL) 1 else MAX_SUB_PAGES
        val minMatches = if (category == ArticleCategory.ALL) 0 else MIN_CATEGORY_MATCHES

        val apiResult: Result<ArticlePage> = try {
            collectCategoryPage(
                category = category,
                startCursor = cursor,
                maxPages = maxPages,
                minMatches = minMatches,
                fetchPage = { pageCursor ->
                    fetchRawPageFromApi(cursor = pageCursor, forceRefresh = forceRefresh)
                }
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }

        apiResult.getOrNull()?.let { page ->
            // 后续页即使为空也是「正常的流末尾」，直接返回
            if (page.articles.isNotEmpty() || !isFirstPage) return Result.success(page)
        }

        if (!isFirstPage) return apiResult

        // 首页且接口无内容 → 回退到 RSS（RSS 不具备分页能力）
        val rssPage = try {
            fetchRawPageFromRss(category)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        if (rssPage != null && rssPage.articles.isNotEmpty()) return Result.success(rssPage)

        val apiFailure = apiResult.exceptionOrNull()
        return if (apiFailure != null) {
            Result.failure(apiFailure)
        } else {
            Result.failure(IOException("数据源暂无可用内容，请稍后重试"))
        }
    }

    /**
     * 拉取文章详情。
     *
     * 注意：早期实现在网络失败时会返回一段**硬编码的示例正文**并当作真实内容展示，
     * 这会让「加载失败」伪装成「加载成功」，也会掩盖真实的解析问题。
     * 现在统一返回失败，由 UI 呈现可重试的错误状态。
     */
    suspend fun getArticleDetail(
        articleId: String,
        url: String,
        previewTitle: String = "",
        previewAuthor: String = "",
        previewPubTime: String = ""
    ): Result<ArticleDetail> = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext Result.failure(IOException("缺少文章链接"))
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val html = response.body?.string().orEmpty()
                val detail = HtmlParser.parseArticleDetail(html, articleId, url)

                if (detail.contentBlocks.isEmpty()) {
                    // 抓取成功但正文解析为空：多半是页面结构变更，显式报错而不是伪造正文
                    return@withContext Result.failure(IOException("正文解析为空，页面结构可能已变更"))
                }

                Result.success(
                    detail.copy(
                        title = detail.title.takeIf { it.isNotBlank() && it != "无标题" }
                            ?: previewTitle.ifBlank { detail.title },
                        author = previewAuthor.ifBlank { detail.author },
                        publishTime = previewPubTime.ifBlank { detail.publishTime }
                    )
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------
    // 数据抓取（全部在 IO 线程执行）
    // ------------------------------------------------------------------

    /**
     * 拉取文章评论列表。
     *
     * 采用 PC 评论接口 `cmt.ithome.com/api/webcomment/getnewscomment`：
     * 1. 抓取移动端详情页，取 canonical 的 PC 地址；
     * 2. 抓取 PC 页面提取 `sn` 令牌；
     * 3. 调评论接口拉取全部评论（含内联楼中楼）。
     *
     * 实测该接口一次性返回全部评论（`cid` 游标不会产生第二页），因此不做分页；
     * 之所以仍用 PC 接口而非移动端：PC 接口提供 `sn` 令牌体系，
     * 并支持 `expandCount` 展开剩余回复。
     *
     * 任一步骤令牌缺失（页面结构变更）时返回空结果而非抛错，避免阻塞详情页。
     *
     * @param url 文章详情页 URL（移动端 m.ithome.com 地址）
     * @param sn 已缓存的 sn 令牌；为空时本方法会自行抓取解析
     */
    suspend fun getArticleComments(
        url: String,
        sn: String? = null
    ): Result<CommentPageResult> =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) {
                return@withContext Result.success(CommentPageResult())
            }
            try {
                val token = (sn?.takeIf { it.isNotBlank() } ?: resolveCommentSn(url))
                    ?.also { resolved -> cachedCommentSn = resolved }
                if (token.isNullOrBlank()) {
                    return@withContext Result.success(CommentPageResult())
                }

                val commentRequest = Request.Builder()
                    .url(buildCommentListUrl(token))
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("Referer", "https://www.ithome.com/")
                    .build()

                val pageResult = client.newCall(commentRequest).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val json = response.body?.string().orEmpty()
                    CommentParser.parseComments(json)
                }

                Result.success(pageResult.copy(sn = token))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 最近一次成功解析的 sn 令牌（进程内缓存，减少重复抓取 PC 页面）。 */
    @Volatile
    var cachedCommentSn: String? = null
        private set

    /**
     * 展开某条评论下剩余的楼中楼回复（`getcommentcontent`）。
     *
     * @param commentId 目标父评论 id
     * @param url 文章详情页 URL（用于在 sn 未缓存时兜底解析）
     * @param sn 已缓存的 sn 令牌
     */
    suspend fun getCommentReplies(
        commentId: String,
        url: String,
        sn: String? = null
    ): Result<List<ArticleComment>> =
        withContext(Dispatchers.IO) {
            if (commentId.isBlank()) return@withContext Result.success(emptyList())
            try {
                val token = sn?.takeIf { it.isNotBlank() } ?: resolveCommentSn(url)
                if (token.isNullOrBlank()) return@withContext Result.success(emptyList())

                val request = Request.Builder()
                    .url(buildCommentContentUrl(commentId, token))
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("Referer", "https://www.ithome.com/")
                    .build()

                val replies = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val json = response.body?.string().orEmpty()
                    CommentParser.parseCommentContent(json)
                }

                Result.success(replies)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 解析评论 `sn` 令牌：移动端详情页 → canonical PC 地址 → PC 页面 `sn`。
     */
    private fun resolveCommentSn(url: String): String? {
        val mobileHtml = fetchHtml(url) ?: return null
        val pcUrl = HtmlParser.extractCanonicalUrl(mobileHtml) ?: return null
        val pcHtml = fetchHtml(pcUrl) ?: return null
        return HtmlParser.extractCommentSn(pcHtml)
    }

    private fun fetchHtml(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun buildCommentListUrl(sn: String): String =
        "https://cmt.ithome.com/api/webcomment/getnewscomment" +
            "?sn=$sn&cid=0&isInit=true&appver=900"

    private fun buildCommentContentUrl(commentId: String, sn: String): String =
        "https://cmt.ithome.com/api/webcomment/getcommentcontent" +
            "?commentId=$commentId&sn=$sn&appver=900"


    /**
     * 从资讯接口拉取一页原始数据。
     *
     * **关于 `ot` 参数（重要，修复历史缺陷）：**
     * 该接口把 `ot` 当作「由此时间点往回取一页」的游标，
     * 同时服务端（腾讯云 BLB）的 CDN **以完整 URL 作为缓存键**。
     *
     * 早期实现在首页请求时**省略** `ot`，请求裸地址，
     * 于是命中 CDN 缓存、固定返回约 20 分钟前的旧列表 ——
     * 表现为「下拉刷新了，但拿不到最新文章」，且每次领先/滞后
     * 的篇数不定（实测滞后 5~6 篇）。
     * 仅靠客户端缓存策略（OkHttp）无法解决，因为问题出在上游 CDN。
     *
     * 因此这里**始终带上 `ot`**：首页用当前时间戳，
     * 既保证语义正确（从此刻往回取），又让 URL 唯一从而绕过 CDN 缓存。
     *
     * @param cursor 0 表示首页；否则为上一页返回的 [ArticlePage.nextCursor]
     */
    private suspend fun fetchRawPageFromApi(
        cursor: Long,
        forceRefresh: Boolean = false
    ): RawNewsPage = withContext(Dispatchers.IO) {
        val url = buildNewsListUrl(cursor = cursor, now = clock())
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Referer", "https://m.ithome.com/")

        // 刷新场景要求回源：给拦截器一个显式标记，
        // 避免命中客户端 OkHttp 缓存（上游 CDN 已由上面的 ot 参数处理）。
        if (forceRefresh) {
            requestBuilder.header(NetworkClient.HEADER_FORCE_REFRESH, "1")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = response.body?.string().orEmpty()
            val articles = HtmlParser.parseJsonNews(json)
            rawPageOf(articles)
        }
    }

    private suspend fun fetchRawPageFromRss(category: ArticleCategory): ArticlePage =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(RSS_FEED)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val xml = response.body?.string().orEmpty()
                val all = HtmlParser.parseRss(xml)
                val filtered = if (category == ArticleCategory.ALL) {
                    all
                } else {
                    all.filter { it.category == category }
                }
                // RSS 无分页能力
                ArticlePage(articles = filtered, nextCursor = 0L, hasMore = false)
            }
        }

    private fun rawPageOf(articles: List<Article>): RawNewsPage {
        if (articles.isEmpty()) {
            return RawNewsPage(articles = emptyList(), nextCursor = 0L, hasMore = false)
        }
        // 接口按时间倒序返回，最后一条即本页最旧
        val oldest = articles.last().orderTimestamp
        return RawNewsPage(articles = articles, nextCursor = oldest, hasMore = true)
    }

    companion object {
        private const val NEWS_LIST_API = "https://m.ithome.com/api/news/newslistpageget"
        private const val RSS_FEED = "https://www.ithome.com/rss/"

        /**
         * 构造资讯列表请求 URL（纯函数，便于单元测试）。
         *
         * **必须始终带 `ot`**：上游 CDN 以完整 URL 为缓存键，
         * 首屏若请求裸地址会被缓存固定住约 20 分钟，导致刷新拿不到新文章。
         * 首页（`cursor <= 0`）用「当前时间」作为游标，
         * 既语义正确（从此刻往回取）又让 URL 唯一从而回源。
         *
         * @param cursor 非正数表示首页
         * @param now 当前时间戳；仅在首页时使用。由调用方注入以便测试。
         */
        internal fun buildNewsListUrl(cursor: Long, now: Long): String {
            val effectiveCursor = cursor.takeIf { it > 0L } ?: now
            return "$NEWS_LIST_API?ot=$effectiveCursor"
        }

        /** 单次「加载更多」最多连续请求的原始页数（防止分类过于稀疏时长时间空转）。 */
        internal const val MAX_SUB_PAGES = 4

        /** 单次「加载更多」希望收集到的分类匹配条目数。 */
        internal const val MIN_CATEGORY_MATCHES = 12
    }
}

/**
 * 分类感知的分页收集器（纯函数，便于单元测试）。
 *
 * 行为约定：
 * 1. 游标始终取自服务端原始页的 [RawNewsPage.nextCursor]，
 *    因此「整页都不匹配当前分类」时游标依旧前进，不会卡住。
 * 2. 达到 [maxPages]、收集到 [minMatches] 条匹配项，或数据源耗尽即停止。
 * 3. 首页抓取失败 → [Result.failure]；后续页失败 → 返回已成功收集的部分结果。
 */
internal suspend fun collectCategoryPage(
    category: ArticleCategory,
    startCursor: Long,
    maxPages: Int,
    minMatches: Int,
    fetchPage: suspend (cursor: Long) -> RawNewsPage
): Result<ArticlePage> {
    val matched = LinkedHashMap<String, Article>()
    var cursor = startCursor
    var pages = 0
    var hasMore = true
    var anySuccess = false

    while (pages < maxPages) {
        val raw = try {
            fetchPage(cursor)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (!anySuccess) return Result.failure(throwable)
            // 已有部分结果：返回已收集内容，游标保持在上次成功处以便重试
            break
        }
        anySuccess = true
        pages++

        if (raw.articles.isEmpty()) {
            hasMore = false
            break
        }

        val nextCursor = raw.nextCursor.takeIf { it > 0L } ?: raw.articles.last().orderTimestamp
        // 游标没有前进说明服务端数据异常，停止以避免死循环
        if (nextCursor == cursor) {
            hasMore = raw.hasMore
            break
        }
        cursor = nextCursor
        hasMore = raw.hasMore

        raw.articles.forEach { article ->
            if (category == ArticleCategory.ALL || article.category == category) {
                matched[article.id] = article
            }
        }

        if (!hasMore) break
        if (category == ArticleCategory.ALL) break
        if (minMatches > 0 && matched.size >= minMatches) break
    }

    return Result.success(
        ArticlePage(
            articles = matched.values.toList(),
            nextCursor = cursor,
            hasMore = hasMore
        )
    )
}
