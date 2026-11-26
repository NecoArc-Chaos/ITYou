package com.necoarc.ityou.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityHelperTest {

    @Test
    fun `高清模式剥离 IT之家 图床压缩参数并拉取原图`() {
        val raw = "https://img.ithome.com/newspic/2026/11/test.jpg?x-bce-process=image/resize,w_320"
        val resolved = ImageQualityHelper.resolveUrl(raw, highQuality = true, isThumbnail = false)
        assertEquals("https://img.ithome.com/newspic/2026/11/test.jpg", resolved)
    }

    @Test
    fun `省流模式为 IT之家 图床追加缩略图或详情页压缩参数`() {
        val raw = "https://img.ithome.com/newspic/2026/11/test.jpg"
        val thumb = ImageQualityHelper.resolveUrl(raw, highQuality = false, isThumbnail = true)
        val detail = ImageQualityHelper.resolveUrl(raw, highQuality = false, isThumbnail = false)

        assertTrue(thumb!!.contains("x-bce-process=image/resize,w_360"))
        assertTrue(detail!!.contains("x-bce-process=image/resize,w_720"))
    }

    @Test
    fun `第三方图床或空链接保持原样`() {
        assertNull(ImageQualityHelper.resolveUrl(null, highQuality = false))
        assertEquals(
            "https://cdn.example.com/pic.png",
            ImageQualityHelper.resolveUrl("https://cdn.example.com/pic.png", highQuality = false)
        )
    }
}
