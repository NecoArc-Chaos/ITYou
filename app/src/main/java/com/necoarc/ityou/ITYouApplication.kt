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
 * 1. 建立**唯一的** OkHttpClient（供资讯接口使用，含 HTTP 磁盘缓存）；
 * 2. 定制 Coil 的 ImageLoader：
 *    - `crossfade(false)`：关闭淡入动画。列表滚动时淡入会额外引入图层动画与逐帧重绘，
 *      在快速滑动（fling）场景下是最廉价也最有效的优化之一；
 *    - `respectCacheHeaders(false)`：IT之家 缩略图不带稳定的缓存头，
 *      忽略协商缓存可显著提升磁盘缓存命中率；
 *    - 扩大内存缓存占比，减少快速上下滑动时图片被回收后重新解码的次数。
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
        .build()
}
