package com.necoarc.ityou.data.auth

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 [SecureCookieStore] 的持久化 OkHttp [CookieJar]。
 *
 * 设计要点：
 * 1. 同时管理服务端 `Set-Cookie` 响应与 WebView 登录页导入的会话 Cookie；
 * 2. 以 `domain|path|name` 为唯一键，避免同名 Cookie 在子域间覆盖错乱；
 * 3. 保留会话 Cookie（如未显式过期），确保用户冷启动应用后依然保持登录状态。
 */
class PersistentCookieJar(
    private val store: SecureCookieStore? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : CookieJar {

    private val cookieCache = ConcurrentHashMap<String, StoredCookie>()

    init {
        val now = clock()
        store?.loadAll()
            ?.filter { it.expiresAt > now }
            ?.forEach { stored ->
                cookieCache[cacheKey(stored)] = stored
            }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = clock()
        var changed = false

        cookies.forEach { cookie ->
            val stored = cookie.toStoredCookie()
            val key = cacheKey(stored)
            if (cookie.expiresAt <= now) {
                if (cookieCache.remove(key) != null) changed = true
            } else {
                cookieCache[key] = stored
                changed = true
            }
        }

        if (changed) persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = clock()
        var removedExpired = false
        val matched = mutableListOf<Cookie>()

        val iterator = cookieCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val stored = entry.value
            if (stored.expiresAt <= now) {
                iterator.remove()
                removedExpired = true
                continue
            }
            val okCookie = stored.toOkHttpCookie() ?: continue
            if (okCookie.matches(url)) {
                matched.add(okCookie)
            }
        }

        if (removedExpired) persist()
        return matched
    }

    /**
     * 从 Android WebView CookieManager 读取 `"k1=v1; k2=v2"` 字符串并注入对应根域。
     *
     * @param rootDomain 例如 `"ithome.com"` 或 `"ruanmei.com"`（以通配子域方式存储，使 `cmt.ithome.com` 与 `napi.ithome.com` 均能携带）
     * @param rawCookieHeader `CookieManager.getCookie(...)` 返回的原始分号分隔键值对
     */
    fun importWebViewCookies(rootDomain: String, rawCookieHeader: String?) {
        if (rawCookieHeader.isNullOrBlank()) return
        val cleanDomain = rootDomain.trim().trimStart('.')
        if (cleanDomain.isBlank()) return

        // 默认赋予 30 天持久化寿命（服务端若返回更短过期时间会在后续请求中自然更新或返回 401）
        val sessionExpiresAt = clock() + THIRTY_DAYS_MILLIS
        var changed = false

        rawCookieHeader.split(';').forEach { segment ->
            val trimmed = segment.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val name = trimmed.substring(0, eqIdx).trim()
            val value = trimmed.substring(eqIdx + 1).trim()
            if (name.isEmpty() || value.isEmpty()) return@forEach

            val stored = StoredCookie(
                name = name,
                value = value,
                expiresAt = sessionExpiresAt,
                domain = cleanDomain,
                path = "/",
                secure = false,
                httpOnly = false,
                hostOnly = false
            )
            cookieCache[cacheKey(stored)] = stored
            changed = true
        }

        if (changed) persist()
    }

    fun hasCookiesForDomain(domainSuffix: String): Boolean {
        val now = clock()
        val target = domainSuffix.trimStart('.').lowercase()
        return cookieCache.values.any { stored ->
            stored.expiresAt > now &&
                (stored.domain.equals(target, ignoreCase = true) ||
                    stored.domain.lowercase().endsWith(".$target"))
        }
    }

    fun clearAll() {
        cookieCache.clear()
        store?.clear()
    }

    private fun persist() {
        store?.saveAll(cookieCache.values.toList())
    }

    private fun cacheKey(c: StoredCookie): String = "${c.domain.lowercase()}|${c.path}|${c.name}"

    private fun Cookie.toStoredCookie(): StoredCookie = StoredCookie(
        name = name,
        value = value,
        expiresAt = expiresAt,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly
    )

    private fun StoredCookie.toOkHttpCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            }
            .path(path)
            .apply {
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    companion object {
        private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000L
    }
}
