package com.necoarc.ityou.data.cache

import com.necoarc.ityou.data.model.ArticleDetail
import java.util.LinkedHashMap

/**
 * 文章详情的短期内存 LRU 缓存。
 *
 * 背景与目标：
 * 移动端阅读场景下，用户经常在列表与详情之间往复切换，或通过底部「相关文章」连续阅读后退回前文。
 * 若无缓存，每次返回或重复打开同一文章都会经历：
 * 1. 发起完整 HTTP GET 抓取 HTML；
 * 2. 耗时的 Jsoup DOM 树构建与递归解析；
 * 3. 产生闪烁的骨架屏等待。
 *
 * 本缓存机制提供：
 * - **LRU 淘汰**：默认上限 50 篇详情，超出自动淘汰最久未访问的内容，严格控制内存开销；
 * - **TTL 生存期控制**：默认 10 分钟短期有效，既能保障阅读即开即看的极速体验，又能避免长期驻留旧资讯；
 * - **线程安全**：通过内部互斥保护并发读写；
 * - **时钟可注入**：便于单元测试确定性验证过期行为。
 */
class ArticleDetailCache(
    private val maxCapacity: Int = DEFAULT_MAX_CAPACITY,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private data class CacheEntry(
        val detail: ArticleDetail,
        val timestamp: Long
    )

    // accessOrder = true 使其成为严格 LRU 缓存
    private val cacheMap = object : LinkedHashMap<String, CacheEntry>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > maxCapacity
        }
    }

    private val lock = Any()

    /**
     * 根据文章 ID 获取缓存详情。
     *
     * @param articleId 文章唯一 ID
     * @return 存在且未过期的 [ArticleDetail]，否则返回 null 并移除过期项
     */
    fun get(articleId: String): ArticleDetail? {
        if (articleId.isBlank()) return null
        synchronized(lock) {
            val entry = cacheMap[articleId] ?: return null
            val now = clock()
            if (now - entry.timestamp > ttlMillis) {
                // 已过期，主动淘汰
                cacheMap.remove(articleId)
                return null
            }
            return entry.detail
        }
    }

    /**
     * 写入文章详情到缓存。
     *
     * @param articleId 文章 ID
     * @param detail 文章详情对象
     */
    fun put(articleId: String, detail: ArticleDetail) {
        if (articleId.isBlank()) return
        synchronized(lock) {
            cacheMap[articleId] = CacheEntry(
                detail = detail,
                timestamp = clock()
            )
        }
    }

    /** 移除单篇文章缓存 */
    fun remove(articleId: String) {
        synchronized(lock) {
            cacheMap.remove(articleId)
        }
    }

    /** 清空全部缓存 */
    fun clear() {
        synchronized(lock) {
            cacheMap.clear()
        }
    }

    /** 当前缓存条目数（包含可能尚未过期的项） */
    val size: Int
        get() = synchronized(lock) { cacheMap.size }

    companion object {
        /** 默认最大缓存容量（篇） */
        const val DEFAULT_MAX_CAPACITY = 50

        /** 默认缓存有效期：10 分钟 */
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L

        /** 全局共享单例 */
        val shared: ArticleDetailCache by lazy { ArticleDetailCache() }
    }
}
