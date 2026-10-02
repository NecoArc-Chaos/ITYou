package com.necoarc.ityou.data.repository

import com.necoarc.ityou.data.model.Article
import com.necoarc.ityou.data.model.ArticleCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类分页游标契约测试。
 *
 * 背景：分类过滤是在客户端做的。旧实现把「下一页游标」取自 **过滤后** 的最后一条，
 * 于是只要某一整页内容都不属于当前分类，游标就永远停在原地 ——
 * 用户看到的现象就是「切到某个分类后文章很少，而且再也翻不动」。
 *
 * 现在游标永远来自服务端原始页，并由下面的测试锁定该行为。
 */
class CategoryPagingTest {

    private fun article(id: String, category: ArticleCategory, timestamp: Long) = Article(
        id = id,
        title = "标题 $id",
        category = category,
        orderTimestamp = timestamp
    )

    private fun rawPage(
        articles: List<Article>,
        nextCursor: Long,
        hasMore: Boolean = true
    ) = RawNewsPage(articles = articles, nextCursor = nextCursor, hasMore = hasMore)

    @Test
    fun cursorAdvances_evenWhenPageHasNoMatchingCategory() = runTest {
        val pages = mapOf(
            0L to rawPage(
                articles = listOf(
                    article("d1", ArticleCategory.DIGITAL, 100L),
                    article("d2", ArticleCategory.DIGITAL, 90L)
                ),
                nextCursor = 90L
            ),
            90L to rawPage(
                articles = listOf(article("c1", ArticleCategory.AUTOMOTIVE, 80L)),
                nextCursor = 80L,
                hasMore = false
            )
        )
        var requestCount = 0

        val page = collectCategoryPage(
            category = ArticleCategory.AUTOMOTIVE,
            startCursor = 0L,
            maxPages = 4,
            minMatches = 12
        ) { cursor ->
            requestCount++
            pages.getValue(cursor)
        }.getOrThrow()

        // 只应返回命中分类的条目
        assertEquals(listOf("c1"), page.articles.map { it.id })
        // 关键：游标推进到了服务端原始页的最旧时间戳，而不是停在 0
        assertEquals(80L, page.nextCursor)
        // 两页都被请求过（第二页才是有效内容）
        assertEquals(2, requestCount)
        assertFalse(page.hasMore)
    }

    @Test
    fun stopsAtMaxPages_whenCategoryIsSparse() = runTest {
        var cursorSeen = 0L
        var requestCount = 0

        val page = collectCategoryPage(
            category = ArticleCategory.AI,
            startCursor = 0L,
            maxPages = 2,
            minMatches = 12
        ) { cursor ->
            requestCount++
            cursorSeen = cursor
            rawPage(
                articles = listOf(article("dig_$requestCount", ArticleCategory.DIGITAL, 100L - requestCount * 10)),
                nextCursor = 100L - requestCount * 10
            )
        }.getOrThrow()

        assertEquals(2, requestCount)
        assertTrue("没有任何匹配项时不应伪造数据", page.articles.isEmpty())
        // 游标仍在推进，下一次加载更多不会重复请求同一页
        assertTrue(cursorSeen > 0L)
        assertEquals(80L, page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun returnsPartialResult_whenLaterPageFails() = runTest {
        val page = collectCategoryPage(
            category = ArticleCategory.PC,
            startCursor = 0L,
            maxPages = 3,
            minMatches = 12
        ) { cursor ->
            if (cursor == 0L) {
                rawPage(
                    articles = listOf(article("pc1", ArticleCategory.PC, 200L)),
                    nextCursor = 200L
                )
            } else {
                throw IllegalStateException("网络中断")
            }
        }.getOrThrow()

        // 已经拿到的内容必须保留，游标停在最后一次成功处以便重试
        assertEquals(listOf("pc1"), page.articles.map { it.id })
        assertEquals(200L, page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun firstPageFailure_isReportedAsFailure() = runTest {
        val result = collectCategoryPage(
            category = ArticleCategory.PC,
            startCursor = 0L,
            maxPages = 2,
            minMatches = 12
        ) { throw IllegalStateException("无法连接服务器") }

        assertTrue(result.isFailure)
        assertEquals("无法连接服务器", result.exceptionOrNull()?.message)
    }

    @Test
    fun allCategory_returnsWholePageWithoutExtraRequests() = runTest {
        var requestCount = 0
        val page = collectCategoryPage(
            category = ArticleCategory.ALL,
            startCursor = 0L,
            maxPages = 1,
            minMatches = 0
        ) {
            requestCount++
            rawPage(
                articles = listOf(
                    article("a", ArticleCategory.DIGITAL, 300L),
                    article("b", ArticleCategory.PC, 200L),
                    article("c", ArticleCategory.AI, 100L)
                ),
                nextCursor = 100L,
                hasMore = false
            )
        }.getOrThrow()

        assertEquals(1, requestCount)
        assertEquals(3, page.articles.size)
        assertEquals(100L, page.nextCursor)
        assertFalse(page.hasMore)
    }

    @Test
    fun duplicatedArticleIds_areDeduplicated() = runTest {
        val page = collectCategoryPage(
            category = ArticleCategory.ALL,
            startCursor = 0L,
            maxPages = 1,
            minMatches = 0
        ) {
            rawPage(
                articles = listOf(
                    article("dup", ArticleCategory.DIGITAL, 300L),
                    article("dup", ArticleCategory.DIGITAL, 250L)
                ),
                nextCursor = 250L,
                hasMore = false
            )
        }.getOrThrow()

        assertEquals(1, page.articles.size)
    }
}
