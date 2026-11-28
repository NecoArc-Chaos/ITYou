package com.necoarc.ityou.data.auth

import com.necoarc.ityou.data.repository.AuthRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AuthCookieAndUserInfoTest {

    private val testCipher = object : StringCipher {
        override fun encrypt(plainText: String): String =
            Base64.getEncoder().encodeToString(plainText.toByteArray())

        override fun decrypt(cipherText: String): String =
            String(Base64.getDecoder().decode(cipherText))
    }

    @Test
    fun `PersistentCookieJar 导入 WebView Cookie 后能够匹配 ithome 子域并过滤过期项`() {
        var now = 1_700_000_000_000L
        val jar = PersistentCookieJar(
            store = SecureCookieStore.inMemory(testCipher),
            clock = { now }
        )

        jar.importWebViewCookies(
            rootDomain = "ithome.com",
            rawCookieHeader = "user=hash%3Dabc123; rm_session=xyz; ignored_empty="
        )

        assertTrue(jar.hasCookiesForDomain("ithome.com"))
        assertFalse(jar.hasCookiesForDomain("example.com"))

        // 验证 cmt.ithome.com 与 napi.ithome.com 均能匹配到 Cookie
        val cmtCookies = jar.loadForRequest("https://cmt.ithome.com/apiv2/comment/submit".toHttpUrl())
        assertEquals(2, cmtCookies.size)
        assertTrue(cmtCookies.any { it.name == "user" && it.value == "hash%3Dabc123" })

        val napiCookies = jar.loadForRequest("https://napi.ithome.com/api/user/getuserinfo".toHttpUrl())
        assertEquals(2, napiCookies.size)

        // 推进超过 30 天有效期，Cookie 应自动过期被清除
        now += 31L * 24 * 60 * 60 * 1000L
        val expiredCookies = jar.loadForRequest("https://cmt.ithome.com/apiv2/comment/submit".toHttpUrl())
        assertTrue(expiredCookies.isEmpty())
        assertFalse(jar.hasCookiesForDomain("ithome.com"))
    }

    @Test
    fun `SecureCookieStore 序列化编码与解码保持完整字段`() {
        val store = SecureCookieStore.inMemory(testCipher)
        val cookies = listOf(
            StoredCookie(
                name = "user",
                value = "hash=123",
                expiresAt = 9_999_999_999_999L,
                domain = "ithome.com",
                path = "/",
                secure = true,
                httpOnly = true,
                hostOnly = false
            )
        )

        val encoded = store.encodeCookies(cookies)
        val decoded = store.decodeCookies(encoded)

        assertEquals(1, decoded.size)
        assertEquals(cookies[0], decoded[0])
    }

    @Test
    fun `AuthRepository_parseUserInfoJson 兼容多格式响应与失败校验`() {
        val standard = AuthRepository.parseUserInfoJson(
            """{"success":true,"data":{"userId":"42","userNick":"软媒极客","userAvatar":"//avatar.ithome.com/42.jpg","level":8}}"""
        )
        assertNotNull(standard)
        assertTrue(standard!!.isLoggedIn)
        assertEquals("42", standard.userId)
        assertEquals("软媒极客", standard.userNick)
        assertEquals("https://avatar.ithome.com/42.jpg", standard.avatarUrl)
        assertEquals(8, standard.level)

        // 兼容 PascalCase 服务端返回字段（UserNick / UserAvatarHeadImg / UserID / Level）
        val pascal = AuthRepository.parseUserInfoJson(
            """{"success":true,"data":{"UserID":1024,"UserNick":"尾巴","UserAvatarHeadImg":"https://avatar.ithome.com/1024.jpg","Level":4}}"""
        )
        assertNotNull(pascal)
        assertEquals("1024", pascal!!.userId)
        assertEquals("尾巴", pascal.userNick)
        assertEquals("https://avatar.ithome.com/1024.jpg", pascal.avatarUrl)
        assertEquals(4, pascal.level)

        val failure = AuthRepository.parseUserInfoJson("""{"success":false,"data":null}""")
        assertNull(failure)
        assertNull(AuthRepository.parseUserInfoJson(""))
    }
}
