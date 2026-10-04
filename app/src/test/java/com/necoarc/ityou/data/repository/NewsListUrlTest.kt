package com.necoarc.ityou.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资讯列表 URL 构造的契约测试。
 *
 * 背景（真实线上缺陷）：
 * 该接口把 `ot` 当作「由此时间点往回取一页」的游标，同时上游 CDN
 * **以完整 URL 作为缓存键**。早期实现首页请求时**省略** `ot`、请求裸地址，
 * 于是命中 CDN 缓存，固定返回约 20 分钟前的旧列表 —— 表现为
 * 「下拉刷新了，却拿不到最新文章」，实测滞后 5~6 篇。
 *
 * 由于问题出在上游 CDN，仅调整客户端 OkHttp 缓存策略无法修复，
 * 必须在 URL 上体现差异。下面的测试锁定这一行为。
 */
class NewsListUrlTest {

    private val now = 1_791_126_000_000L

    @Test
    fun `首页必须携带 ot 参数`() {
        val url = ArticleRepository.buildNewsListUrl(cursor = 0L, now = now)
        assertTrue(
            "首页 URL 必须带 ot，否则会命中 CDN 缓存拿到旧列表：$url",
            url.contains("?ot=")
        )
        assertTrue("ot 应为当前时间戳", url.endsWith("ot=$now"))
    }

    @Test
    fun `不同时刻的首页请求 URL 不同，以绕过 CDN 缓存`() {
        val first = ArticleRepository.buildNewsListUrl(cursor = 0L, now = now)
        val second = ArticleRepository.buildNewsListUrl(cursor = 0L, now = now + 1_000)
        assertNotEquals(
            "时间推进后 URL 必须变化，否则刷新会反复命中同一份 CDN 缓存",
            first,
            second
        )
    }

    @Test
    fun `翻页时使用传入的游标而不是当前时间`() {
        val cursor = 1_791_100_000_000L
        val url = ArticleRepository.buildNewsListUrl(cursor = cursor, now = now)
        assertTrue("翻页应使用传入游标：$url", url.endsWith("ot=$cursor"))
    }

    @Test
    fun `负数与零游标都按首页处理`() {
        assertEquals(
            ArticleRepository.buildNewsListUrl(cursor = 0L, now = now),
            ArticleRepository.buildNewsListUrl(cursor = -1L, now = now)
        )
    }
}
