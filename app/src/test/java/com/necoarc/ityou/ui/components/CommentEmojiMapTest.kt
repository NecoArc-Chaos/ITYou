package com.necoarc.ityou.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentEmojiMapTest {

    @Test
    fun urlFor_returnsCdnSvgUrlForKnownEmoji() {
        assertEquals(
            "https://img.ithome.com/app/emotion/svg/rm_e_huaixiao.svg",
            CommentEmojiMap.urlFor("坏笑")
        )
        assertEquals(
            "https://img.ithome.com/app/emotion/svg/rm_e_wulianxiaoku.svg",
            CommentEmojiMap.urlFor("捂脸笑哭")
        )
        assertEquals(
            "https://img.ithome.com/app/emotion/svg/rm_e_buzhengjinghuaji.svg",
            CommentEmojiMap.urlFor("不正经滑稽")
        )
    }

    @Test
    fun urlFor_returnsNullForUnknownName() {
        assertNull(CommentEmojiMap.urlFor("这不存在的表情"))
        assertNull(CommentEmojiMap.urlFor(""))
        assertNull(CommentEmojiMap.urlFor("0"))
    }

    @Test
    fun isKnown_distinguishesEmojiFromPlainBrackets() {
        assertTrue(CommentEmojiMap.isKnown("晕"))
        assertTrue(CommentEmojiMap.isKnown("狗头"))
        // 普通方括号内容不应被当成表情
        assertFalse(CommentEmojiMap.isKnown("0"))
        assertFalse(CommentEmojiMap.isKnown("索引"))
    }

    @Test
    fun tokenRegex_extractsEmojiNamesFromText() {
        val text = "这个东西比黑卡 7 还大还沉[晕]"
        val names = CommentEmojiMap.TOKEN_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("晕"), names)
    }

    @Test
    fun tokenRegex_extractsMultipleEmoji() {
        val text = "带[坏笑]和[捂脸笑哭]的内容"
        val names = CommentEmojiMap.TOKEN_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("坏笑", "捂脸笑哭"), names)
    }

    @Test
    fun tokenRegex_ignoresOverlongBracketContent() {
        // 超过 10 个字符的方括号内容不视作表情（正则上限）
        val text = "这是一段[非常长的普通文本内容用来测试上限]的说明"
        val names = CommentEmojiMap.TOKEN_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("超长方括号内容不应被匹配为表情", names.isEmpty())
    }

    @Test
    fun tokenRegex_matchesPlainBracketsThatCallerMustFilter() {
        // 正则本身会匹配 arr[0]，因此调用方必须用 isKnown 过滤
        val text = "数组 arr[0] 不是表情"
        val names = CommentEmojiMap.TOKEN_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("0"), names)
        assertFalse(
            "过滤后不应残留可渲染的表情",
            names.any { CommentEmojiMap.isKnown(it) }
        )
    }
}
