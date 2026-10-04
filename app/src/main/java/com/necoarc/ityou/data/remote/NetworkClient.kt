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

    /**
     * 请求头标记：置为 `"1"` 时强制跳过资讯接口缓存。
     *
     * 为什么需要它：资讯接口本身不带缓存响应头，[newsApiCacheInterceptor]
     * 会为其补上 60 秒的 `max-age`，以便分类来回切换时秒开。
     * 但下拉刷新请求的是**完全相同的 URL**（同样不带 `ot` 参数），
     * 因此会命中这份缓存 —— 表现为「刷新了，但看到的还是旧列表」。
     * 刷新场景必须在请求头显式声明「不要缓存」。
     */
    const val HEADER_FORCE_REFRESH = "X-ITYou-Force-Refresh"

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
     * 资讯接口缓存策略。
     *
     * 1. 请求头带 [HEADER_FORCE_REFRESH]（下拉刷新）：
     *    改写为 `no-cache` 并**移除请求头**后转发，强制走网络校验，
     *    保证用户拿到的就是最新列表。
     * 2. 其余 `/api/` 请求（首屏、切分类、加载更多）：
     *    补充 60 秒 `max-age`，使其可命中磁盘缓存，减少重复整页请求。
     *
     * 注意：该标记必须在转发前删除，否则会被一并发送到服务端。
     */
    private val newsApiCacheInterceptor = Interceptor { chain ->
        val original = chain.request()
        val isApi = original.url.encodedPath.startsWith("/api/")
        val forceRefresh = original.header(HEADER_FORCE_REFRESH) == "1"

        if (!isApi) return@Interceptor chain.proceed(original)

        val request = if (forceRefresh) {
            original.newBuilder().removeHeader(HEADER_FORCE_REFRESH).build()
        } else {
            original
        }

        val response = chain.proceed(request)
        if (!response.isSuccessful) return@Interceptor response

        val cacheControl = if (forceRefresh) CACHE_CONTROL_NO_CACHE else CACHE_CONTROL_SHORT_LIVED
        response.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", cacheControl)
            .build()
    }

    /** 强制刷新：不使用任何已存副本，必须回源校验。 */
    private const val CACHE_CONTROL_NO_CACHE = "no-cache, no-store, must-revalidate"

    /** 常规加载：允许 60 秒的短缓存，兼顾首屏速度与内容新鲜度。 */
    private const val CACHE_CONTROL_SHORT_LIVED = "public, max-age=60"
}
