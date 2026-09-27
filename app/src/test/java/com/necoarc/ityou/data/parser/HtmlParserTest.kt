package com.necoarc.ityou.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserTest {

    @Test
    fun parseRss_extractsArticlesCorrectly() {
        val sampleRss = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0">
                <channel>
                    <title>IT之家</title>
                    <item>
                        <title>测试新闻：谷歌发布 Android 新版本</title>
                        <link>https://www.ithome.com/0/800/123.htm</link>
                        <pubDate>Sun, 27 Sep 2026 10:00:00 GMT</pubDate>
                        <description><![CDATA[<p><img src="https://img.ithome.com/test.jpg" />这是一篇关于 Android 的测试新闻摘要内容。</p>]]></description>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val articles = HtmlParser.parseRss(sampleRss)

        assertEquals(1, articles.size)
        val article = articles[0]
        assertEquals("测试新闻：谷歌发布 Android 新版本", article.title)
        assertEquals("https://img.ithome.com/test.jpg", article.coverImageUrl)
        assertTrue(article.summary.contains("测试新闻摘要内容"))
        assertEquals("123", article.id)
    }

    @Test
    fun parseArticleDetail_extractsBlocksCorrectly() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <h1 class="post-title">详细评测：高性能处理器深度解析</h1>
                <div class="author">测试作者</div>
                <div class="time">2026-09-27 12:00</div>
                <div id="paragraph">
                    <p>这是正文的第一段文字介绍。</p>
                    <h2>性能基准测试</h2>
                    <p><img src="https://img.ithome.com/chart.png" /></p>
                    <blockquote>注意：本测试数据在室温 25 度下测得。</blockquote>
                </div>
            </body>
            </html>
        """.trimIndent()

        val detail = HtmlParser.parseArticleDetail(sampleHtml, "800123")

        assertEquals("详细评测：高性能处理器深度解析", detail.title)
        assertEquals("测试作者", detail.author)
        assertEquals(4, detail.contentBlocks.size)
    }
}
