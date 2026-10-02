package com.necoarc.ityou.data.parser

import com.necoarc.ityou.data.model.ArticleCategory
import com.necoarc.ityou.data.model.ContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserTest {

    @Test
    fun parseJsonNews_extractsArticlesAndPagingTimestampCorrectly() {
        val sampleJson = """
            {
                "Success": 1,
                "Result": [
                    {
                        "newsid": 1009170,
                        "title": "长安汽车 9 月交付 22.89 万辆，海外大增 73.8%",
                        "description": "长安汽车公布 9 月交付数据，新能源交付超 11.3 万辆。",
                        "image": "https://img.ithome.com/test_car.jpg?r=1790869653417",
                        "orderdate": "2026-10-01T23:47:33.417",
                        "PostDateStr": "昨日 23:47",
                        "commentcount": 10,
                        "cid": 192,
                        "url": "/1/009/170.htm",
                        "isad": false,
                        "NewsTips": []
                    },
                    {
                        "newsid": 1009171,
                        "title": "24 支仅需 9.8 元：某品牌水彩笔特惠",
                        "description": "促销打折活动",
                        "image": "https://img.ithome.com/ad.jpg",
                        "orderdate": "2026-10-01T23:45:00",
                        "PostDateStr": "昨日 23:45",
                        "commentcount": 2,
                        "cid": 166,
                        "url": "https://lapin.ithome.com/html/digi/1009171.htm",
                        "isad": true,
                        "NewsTips": [{"TipClass": "tip-gray", "TipName": "广告"}]
                    },
                    {
                        "newsid": 1009172,
                        "title": "某品牌跑鞋清仓促销",
                        "description": "",
                        "image": "https://img.ithome.com/ad2.jpg",
                        "orderdate": "2026-10-01T23:44:00",
                        "PostDateStr": "昨日 23:44",
                        "commentcount": 1,
                        "cid": 166,
                        "url": "/1/009/172.htm",
                        "isad": false,
                        "NewsTips": []
                    }
                ]
            }
        """.trimIndent()

        val articles = HtmlParser.parseJsonNews(sampleJson)

        // 导购频道 (cid=166) 的条目必须被整类过滤，即使没有广告标记
        assertEquals(1, articles.size)
        val article = articles[0]
        assertEquals("1009170", article.id)
        assertEquals("长安汽车 9 月交付 22.89 万辆，海外大增 73.8%", article.title)
        assertEquals(ArticleCategory.AUTOMOTIVE, article.category)
        assertTrue(article.orderTimestamp > 0L)
        assertEquals("https://www.ithome.com/1/009/170.htm", article.url)
        // 缩略图上的 ?r=<时间戳> 缓存破坏参数必须被剔除，否则 Coil 缓存永远无法命中
        assertEquals("https://img.ithome.com/test_car.jpg", article.coverImageUrl)
    }

    @Test
    fun categoryFromCid_mapsServerChannelsWithHighConfidence() {
        assertEquals(ArticleCategory.AUTOMOTIVE, HtmlParser.categoryFromCid(192))
        assertEquals(ArticleCategory.SMARTPHONE, HtmlParser.categoryFromCid(185))
        assertEquals(ArticleCategory.PC, HtmlParser.categoryFromCid(100))
        assertEquals(ArticleCategory.GAME, HtmlParser.categoryFromCid(76))
        assertEquals(ArticleCategory.AI, HtmlParser.categoryFromCid(200))
        assertNull(HtmlParser.categoryFromCid(9999))
    }

    @Test
    fun cleanImageUrl_stripsOnlyCacheBuster() {
        assertEquals(
            "https://img.ithome.com/a.jpg",
            HtmlParser.cleanImageUrl("https://img.ithome.com/a.jpg?r=1790870763077")
        )
        assertEquals(
            "https://img.ithome.com/a.jpg?x-bce-process=image/format,f_auto",
            HtmlParser.cleanImageUrl("https://img.ithome.com/a.jpg?r=123&x-bce-process=image/format,f_auto")
        )
        assertNull(HtmlParser.cleanImageUrl("data:image/png;base64,AAAA"))
        assertNull(HtmlParser.cleanImageUrl(""))
        assertNull(HtmlParser.cleanImageUrl(null))
    }

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
                        <description><![CDATA[<p><img src="https://img.ithome.com/test.jpg?r=123" />这是一篇关于新手机的测试新闻摘要内容。</p>]]></description>
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
        assertTrue(article.orderTimestamp > 1_700_000_000_000L)
    }

    @Test
    fun inferCategory_matchesKeywordsAccurately() {
        assertEquals(ArticleCategory.SMARTPHONE, HtmlParser.inferCategory("苹果发布全新 iPhone 旗舰手机"))
        assertEquals(ArticleCategory.PC, HtmlParser.inferCategory("英伟达发布 RTX 5090 显卡与新架构处理器"))
        assertEquals(ArticleCategory.AI, HtmlParser.inferCategory("DeepSeek 大模型新算法技术解析"))
        assertEquals(ArticleCategory.AUTOMOTIVE, HtmlParser.inferCategory("小米汽车 SU7 Ultra 交付进度更新"))
        assertEquals(ArticleCategory.GAME, HtmlParser.inferCategory("Steam 新品节与国产 3A 游戏公布"))
        assertEquals(ArticleCategory.DIGITAL, HtmlParser.inferCategory("索尼发布全新无线降噪耳机"))
    }

    @Test
    fun parseArticleDetail_extractsBlocksAndRelatedArticlesCorrectly() {
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
                    <p><img src="https://img.ithome.com/chart.png" w="989" h="793" /></p>
                    <blockquote>注意：本测试数据在室温 25 度下测得。</blockquote>
                </div>
                <div class="relevant-news">
                    <div class="title">相关文章</div>
                    <div class="relevant-news-box">
                        <div class="placeholder one-img-plc" data-order-newsId="1008253">
                            <a href="https://m.ithome.com/html/1008253.htm">
                                <div class="plc-image">
                                    <img data-original="https://img.ithome.com/rel1.jpg?r=999" />
                                </div>
                                <div class="plc-con">
                                    <p class="plc-title">相关推荐：第一代架构演进之路</p>
                                    <p class="plc-footer"><span>2026.09.29</span></p>
                                </div>
                            </a>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val detail = HtmlParser.parseArticleDetail(sampleHtml, "800123")

        assertEquals("详细评测：高性能处理器深度解析", detail.title)
        assertEquals("测试作者", detail.author)
        assertEquals(4, detail.contentBlocks.size)

        val image = detail.contentBlocks.filterIsInstance<ContentBlock.Image>().firstOrNull()
        assertTrue("正文中的图片应被解析为 Image 块", image != null)
        val ratio = image!!.aspectRatio
        assertTrue("图片宽高比应被解析", ratio != null)
        assertEquals(989f / 793f, ratio!!, 0.001f)

        // 验证相关文章成功解析
        assertEquals(1, detail.relatedArticles.size)
        val rel = detail.relatedArticles[0]
        assertEquals("1008253", rel.id)
        assertEquals("相关推荐：第一代架构演进之路", rel.title)
        assertEquals("https://m.ithome.com/html/1008253.htm", rel.url)
        assertEquals("https://img.ithome.com/rel1.jpg", rel.coverImageUrl)
        assertEquals("2026.09.29", rel.publishTime)
    }
}
