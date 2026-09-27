package com.necoarc.ityou.data.parser

import com.necoarc.ityou.data.model.ArticleCategory
import org.junit.Assert.assertEquals
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
                        <title>测试新闻：苹果发布全新 iPhone 手机</title>
                        <link>https://www.ithome.com/0/800/123.htm</link>
                        <pubDate>Sun, 27 Sep 2026 10:00:00 GMT</pubDate>
                        <description><![CDATA[<p><img src="https://img.ithome.com/test.jpg" />这是一篇关于新手机的测试新闻摘要内容。</p>]]></description>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val articles = HtmlParser.parseRss(sampleRss)

        assertEquals(1, articles.size)
        val article = articles[0]
        assertEquals("测试新闻：苹果发布全新 iPhone 手机", article.title)
        assertEquals("https://img.ithome.com/test.jpg", article.coverImageUrl)
        assertTrue(article.summary.contains("测试新闻摘要内容"))
        assertEquals("123", article.id)
        assertEquals(ArticleCategory.SMARTPHONE, article.category)
    }

    @Test
    fun inferCategory_matchesKeywordsAccurately() {
        assertEquals(ArticleCategory.SMARTPHONE, HtmlParser.inferCategory("苹果发布全新 iPhone 旗舰手机"))
        assertEquals(ArticleCategory.PC, HtmlParser.inferCategory("英伟达发布 RTX 5090 显卡与新架构处理器"))
        assertEquals(ArticleCategory.AI, HtmlParser.inferCategory("DeepSeek 大模型新算法解析"))
        assertEquals(ArticleCategory.AUTOMOTIVE, HtmlParser.inferCategory("小米汽车 SU7 Ultra 交付进度更新"))
        assertEquals(ArticleCategory.GAME, HtmlParser.inferCategory("Steam 新品节与国产 3A 游戏公布"))
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
