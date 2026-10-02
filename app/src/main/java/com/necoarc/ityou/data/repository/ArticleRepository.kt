package com.necoarc.ityou.data.repository

import androidx.compose.runtime.Immutable
import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ArticlePage
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

class ArticleRepository {

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
     */
    suspend fun getArticlePage(
        category: ArticleCategory = ArticleCategory.ALL,
        cursor: Long = 0L
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
                fetchPage = ::fetchRawPageFromApi
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

    private suspend fun fetchRawPageFromApi(cursor: Long): RawNewsPage = withContext(Dispatchers.IO) {
        val url = if (cursor > 0L) "$NEWS_LIST_API?ot=$cursor" else NEWS_LIST_API
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Referer", "https://m.ithome.com/")
            .build()

        client.newCall(request).execute().use { response ->
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
