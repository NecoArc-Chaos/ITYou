package com.necoarc.ityou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class DetailRouteTest {

    @Test
    fun `DetailRoute_create 能够正确转义特殊字符与空格`() {
        val articleId = "1009170"
        val url = "https://www.ithome.com/html/1009170.htm?from=feed&ref=app"
        val title = "华为 & 苹果：2026 年新机对比（深度解析）"
        val author = "IT之家 / 评测组"
        val pubTime = "2026-10-25 10:30"

        val route = DetailRoute.create(
            articleId = articleId,
            url = url,
            title = title,
            author = author,
            pubTime = pubTime
        )

        assertTrue(route.startsWith("detail/$articleId?url="))
        // 验证 & 与 ? 被正确编码，不会破坏 Navigation URL 参数解析
        assertTrue(!route.contains("&ref=app"))
        assertTrue(!route.contains("?from=feed"))

        // 反向解码后必须能够完美还原原始文本
        val urlQuery = route.substringAfter("url=").substringBefore("&title=")
        val decodedUrl = URLDecoder.decode(urlQuery, StandardCharsets.UTF_8.toString())
        assertEquals(url, decodedUrl)

        val titleQuery = route.substringAfter("title=").substringBefore("&author=")
        val decodedTitle = URLDecoder.decode(titleQuery, StandardCharsets.UTF_8.toString())
        assertEquals(title, decodedTitle)
    }

    @Test
    fun `DetailRoute_create 能够容忍空字符串默认值`() {
        val route = DetailRoute.create(
            articleId = "123",
            url = "https://www.ithome.com/123.htm"
        )
        assertTrue(route.contains("title=&author=&pubTime="))
    }
}
