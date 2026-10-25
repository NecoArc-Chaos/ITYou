package com.necoarc.ityou.data.cache

import com.necoarc.ityou.data.model.ArticleDetail
import com.necoarc.ityou.data.model.ContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleDetailCacheTest {

    private fun sampleDetail(id: String, title: String = "标题-$id") = ArticleDetail(
        id = id,
        title = title,
        author = "测试作者",
        publishTime = "2026-10-25 12:00",
        source = "IT之家",
        contentBlocks = listOf(ContentBlock.Paragraph(text = "正文内容 $id")),
        originalUrl = "https://www.ithome.com/html/$id.htm"
    )

    @Test
    fun `基础存取：put 后能正常读取，未存取的 id 返回 null`() {
        val cache = ArticleDetailCache()
        val detail = sampleDetail("1001")

        cache.put("1001", detail)

        val retrieved = cache.get("1001")
        assertNotNull(retrieved)
        assertEquals("1001", retrieved?.id)
        assertEquals("标题-1001", retrieved?.title)

        assertNull("未缓存的 id 必须返回 null", cache.get("non_existent"))
        assertNull("空白 id 必须返回 null", cache.get(""))
        assertEquals(1, cache.size)
    }

    @Test
    fun `TTL 过期机制：超过生存时间后读取返回 null 并主动淘汰`() {
        var currentTime = 1_000_000L
        val ttl = 5_000L // 5秒
        val cache = ArticleDetailCache(
            maxCapacity = 10,
            ttlMillis = ttl,
            clock = { currentTime }
        )

        val detail = sampleDetail("2001")
        cache.put("2001", detail)

        // 时间推进 3 秒（未过期）
        currentTime += 3_000L
        assertNotNull("未超过 TTL 时应该命中缓存", cache.get("2001"))

        // 时间再推进 3 秒（累计 6 秒，已超过 5 秒 TTL）
        currentTime += 3_000L
        assertNull("超过 TTL 时必须返回 null", cache.get("2001"))
        assertEquals("过期条目在被访问时应被淘汰", 0, cache.size)
    }

    @Test
    fun `LRU 淘汰机制：超出最大容量时淘汰最久未访问的条目`() {
        var time = 1_000_000L
        val cache = ArticleDetailCache(
            maxCapacity = 3,
            ttlMillis = 60_000L,
            clock = { time++ }
        )

        cache.put("a", sampleDetail("a"))
        cache.put("b", sampleDetail("b"))
        cache.put("c", sampleDetail("c"))
        assertEquals(3, cache.size)

        // 访问 "a"，使其变为最近访问（此时访问顺序由旧到新：b, c, a）
        assertNotNull(cache.get("a"))

        // 插入第 4 条 "d"，应该淘汰最旧未访问的 "b"
        cache.put("d", sampleDetail("d"))

        assertEquals(3, cache.size)
        assertNull("最久未被访问的 'b' 必须被淘汰", cache.get("b"))
        assertNotNull("'a' 最近被访问过，必须保留", cache.get("a"))
        assertNotNull("'c' 必须保留", cache.get("c"))
        assertNotNull("'d' 作为最新插入项必须保留", cache.get("d"))
    }

    @Test
    fun `覆盖写入：相同 id 写入时应更新数据与时间戳`() {
        var currentTime = 1_000_000L
        val cache = ArticleDetailCache(
            maxCapacity = 10,
            ttlMillis = 5_000L,
            clock = { currentTime }
        )

        cache.put("x", sampleDetail("x", "初版标题"))
        assertEquals("初版标题", cache.get("x")?.title)

        // 在 4 秒后覆盖写入新标题
        currentTime += 4_000L
        cache.put("x", sampleDetail("x", "更新后标题"))

        // 再过 3 秒（距初次写入 7 秒，但距更新写入仅 3 秒，仍在 TTL 内）
        currentTime += 3_000L
        val updated = cache.get("x")
        assertNotNull("覆盖写入后时间戳已刷新，不应过期", updated)
        assertEquals("更新后标题", updated?.title)
    }

    @Test
    fun `手动移除与清空缓存`() {
        val cache = ArticleDetailCache()
        cache.put("1", sampleDetail("1"))
        cache.put("2", sampleDetail("2"))

        cache.remove("1")
        assertNull(cache.get("1"))
        assertNotNull(cache.get("2"))
        assertEquals(1, cache.size)

        cache.clear()
        assertNull(cache.get("2"))
        assertEquals(0, cache.size)
    }
}
