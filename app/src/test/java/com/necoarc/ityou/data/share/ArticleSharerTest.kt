package com.necoarc.ityou.data.share

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ArticleSharer] 的单元测试。
 *
 * 只覆盖纯逻辑部分（分享文本的拼接）；
 * 实际拉起系统面板需要 Android 运行时，由 Instrumented Test 覆盖。
 */
class ArticleSharerTest {

    @Test
    fun `有标题时标题与链接分为两行`() {
        val text = ArticleSharer.buildShareText(
            title = "IT之家文章标题",
            url = "https://www.ithome.com/0/000/000.htm"
        )
        assertEquals("IT之家文章标题\nhttps://www.ithome.com/0/000/000.htm", text)
    }

    @Test
    fun `标题为空时只分享链接`() {
        val text = ArticleSharer.buildShareText(
            title = "",
            url = "https://www.ithome.com/0/000/000.htm"
        )
        assertEquals("https://www.ithome.com/0/000/000.htm", text)
    }

    @Test
    fun `标题为空白字符时视同无标题`() {
        val text = ArticleSharer.buildShareText(
            title = "   \n  ",
            url = "https://www.ithome.com/0/000/000.htm"
        )
        assertEquals("https://www.ithome.com/0/000/000.htm", text)
    }

    @Test
    fun `标题首尾空白会被去除`() {
        val text = ArticleSharer.buildShareText(
            title = "  标题  ",
            url = "https://example.com/a"
        )
        assertEquals("标题\nhttps://example.com/a", text)
    }
}
