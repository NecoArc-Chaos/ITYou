package com.necoarc.ityou

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.necoarc.ityou.data.remote.NetworkClient

/**
 * 应用级基础设施初始化。
 *
 * 这里做两件对滑动流畅度影响很大的事情：
 * 1. 建立**唯一的** OkHttpClient（同时供 API 与 Coil 复用，共享连接池与线程池）；
 * 2. 定制 Coil 的 ImageLoader：
 *    - 关闭 crossfade：列表滚动时淡入动画会额外增加图层动画与逐帧重绘；
 *    - `respectCacheHeaders(false)`：IT之家 缩略图 URL 带时间戳，忽略协商缓存可显著提升命中率；
 *    - 提高内存缓存占比：避免快速上下滑动时图片被反复回收再解码。
 */
class ITYouApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        NetworkClient.initialize(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(false)
        .respectCacheHeaders(false)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.30)
                .build()
        }
        .okHttpClient { NetworkClient.client }
        .build()
}
