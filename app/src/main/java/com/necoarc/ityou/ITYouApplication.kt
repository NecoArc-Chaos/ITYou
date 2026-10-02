package com.necoarc.ityou

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.necoarc.ityou.data.remote.NetworkClient

object ITYouApplicationSingleton {
    @SuppressLint("StaticFieldLeak")
    var appContext: Context? = null
}

/**
 * 应用级基础设施初始化。
 */
class ITYouApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ITYouApplicationSingleton.appContext = this.applicationContext
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
