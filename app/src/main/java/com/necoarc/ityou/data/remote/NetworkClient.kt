package com.necoarc.ityou.data.remote

import android.content.Context
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 全局唯一的网络传输层。
 *
 * 之前的实现里 `HomeViewModel` 与 `DetailViewModel` 各自 `ArticleRepository()`，
 * 而 Repository 的默认参数会各建一个 `OkHttpClient`
 * —— 意味着两套连接池 + 两套线程池 + 两份 socket 资源，且完全没有 HTTP 磁盘缓存。
 * 这里收敛为单例：API 与图片加载（Coil）共用同一个连接池。
 */
object NetworkClient {

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) ITYou-App/1.4"

    private const val HTTP_CACHE_DIR = "http_cache"
    private const val HTTP_CACHE_SIZE_BYTES = 24L * 1024 * 1024

    @Volatile
    private var instance: OkHttpClient? = null

    /**
     * 由 `ITYouApplication` 在进程启动时调用（需要 Context 才能建立磁盘缓存）。
     */
    fun initialize(context: Context) {
        if (instance != null) return
        synchronized(this) {
            if (instance != null) return
            val appContext = context.applicationContext
            val cache = Cache(
                directory = File(appContext.cacheDir, HTTP_CACHE_DIR),
                maxSize = HTTP_CACHE_SIZE_BYTES
            )
            instance = baseBuilder()
                .cache(cache)
                .addInterceptor(newsApiCacheInterceptor)
                .build()
        }
    }

    /** 供 API 与 Coil 共用的客户端；未初始化时（例如单元测试）退化为无磁盘缓存版本。 */
    val client: OkHttpClient
        get() = instance ?: fallback

    private val fallback: OkHttpClient by lazy { baseBuilder().build() }

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    /**
     * 资讯接口本身不带任何缓存响应头，导致每次进入 App / 来回切换分类都要
     * 重新请求整页 JSON。这里为 `/api/` 响应补一个 60 秒的短缓存：
     * 分类来回切换时可直接命中磁盘缓存，首屏秒开。
     */
    private val newsApiCacheInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.encodedPath.startsWith("/api/") && response.isSuccessful) {
            response.newBuilder()
                .removeHeader("Pragma")
                .header("Cache-Control", "public, max-age=60")
                .build()
        } else {
            response
        }
    }
}
