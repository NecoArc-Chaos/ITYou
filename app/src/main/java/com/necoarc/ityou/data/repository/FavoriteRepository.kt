package com.necoarc.ityou.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.necoarc.ityou.data.model.FavoriteArticle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏仓库：负责收藏状态的持久化与查询。
 *
 * 设计要点：
 * - **单一数据源**：全部收藏项以 JSON 数组形式存于 [SharedPreferences]，
 *   内存中维护一份 [StateFlow] 供 UI 观察。收藏量级（个人阅读场景）远达不到
 *   需要数据库的程度，用 SharedPreferences 可避免引入 Room 这类重依赖。
 * - **同步读写**：单条收藏的记录体积很小（仅元数据、不含正文），
 *   `commit()` 的开销可接受，且能保证「点一下就真的存下了」，
 *   不会在进程被杀时丢数据。
 * - **写后即广播**：任何写操作都会更新 StateFlow，UI 自动重组。
 */
class FavoriteRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(loadFavorites())

    /** 收藏列表，按收藏时间倒序（最近收藏的在前）。 */
    val favorites: StateFlow<List<FavoriteArticle>> = _favorites.asStateFlow()

    /** 判断某篇文章是否已收藏。 */
    fun isFavorite(articleId: String): Boolean =
        _favorites.value.any { it.id == articleId }

    /**
     * 切换收藏状态。
     *
     * @return 切换后是否处于「已收藏」状态，便于调用方给出对应提示。
     */
    fun toggleFavorite(article: FavoriteArticle): Boolean {
        val current = _favorites.value
        val alreadyExists = current.any { it.id == article.id }
        val updated = if (alreadyExists) {
            current.filterNot { it.id == article.id }
        } else {
            // 新收藏置顶，保证列表顺序与「收藏时间倒序」一致
            listOf(article) + current
        }
        persist(updated)
        _favorites.value = updated
        return !alreadyExists
    }

    /** 移除收藏。 */
    fun removeFavorite(articleId: String) {
        val updated = _favorites.value.filterNot { it.id == articleId }
        if (updated.size != _favorites.value.size) {
            persist(updated)
            _favorites.value = updated
        }
    }

    /** 清空全部收藏。 */
    fun clearAll() {
        persist(emptyList())
        _favorites.value = emptyList()
    }

    private fun persist(items: List<FavoriteArticle>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(KEY_ID, item.id)
                    put(KEY_TITLE, item.title)
                    put(KEY_AUTHOR, item.author)
                    put(KEY_PUB_TIME, item.pubTime)
                    put(KEY_URL, item.url)
                    put(KEY_COVER, item.coverUrl ?: JSONObject.NULL)
                    put(KEY_FAVORITED_AT, item.favoritedAt)
                }
            )
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).commit()
    }

    private fun loadFavorites(): List<FavoriteArticle> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString(KEY_ID)
                    // id 是主键，缺失则视为脏数据丢弃，避免出现无法取消的幽灵收藏
                    if (id.isEmpty()) continue
                    add(
                        FavoriteArticle(
                            id = id,
                            title = obj.optString(KEY_TITLE),
                            author = obj.optString(KEY_AUTHOR),
                            pubTime = obj.optString(KEY_PUB_TIME),
                            url = obj.optString(KEY_URL),
                            coverUrl = obj.optString(KEY_COVER).takeIf {
                                it.isNotEmpty() && it != "null"
                            },
                            favoritedAt = obj.optLong(KEY_FAVORITED_AT)
                        )
                    )
                }
            }
        }.getOrElse {
            // 解析失败（例如旧版本格式）时回退为空列表，
            // 而不是让整个应用崩溃在启动阶段。
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "ityou_favorites"
        private const val KEY_FAVORITES = "favorites_json"

        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_AUTHOR = "author"
        private const val KEY_PUB_TIME = "pubTime"
        private const val KEY_URL = "url"
        private const val KEY_COVER = "coverUrl"
        private const val KEY_FAVORITED_AT = "favoritedAt"

        @Volatile
        private var instance: FavoriteRepository? = null

        /** 获取单例。Application 级上下文，避免持有 Activity 造成泄漏。 */
        fun getInstance(context: Context): FavoriteRepository =
            instance ?: synchronized(this) {
                instance ?: FavoriteRepository(context).also { instance = it }
            }
    }
}
