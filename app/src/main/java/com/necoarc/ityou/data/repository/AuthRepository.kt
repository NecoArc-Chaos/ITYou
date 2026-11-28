package com.necoarc.ityou.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.compose.runtime.Immutable
import com.necoarc.ityou.data.remote.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

@Immutable
data class AuthState(
    val isLoggedIn: Boolean = false,
    val userNick: String = "",
    val avatarUrl: String? = null,
    val userId: String = "",
    val level: Int? = null,
    val exp: Int? = null,
    val expHint: String? = null,
    val isChecking: Boolean = false
)

/**
 * 用户账号与登录会话仓库。
 *
 * 登录方式：
 * 通过内嵌 WebView 在 IT之家真实上下文中完成软媒通行证登录与风控验证，
 * 随后把 WebView `CookieManager` 中 `.ithome.com` / `.ruanmei.com` 域下的会话 Cookie
 * 导入到加密持久化 [NetworkClient.cookieJar]，并通过 `napi.ithome.com/api/user/getuserinfo`
 * 校验并同步账号信息。
 */
class AuthRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(loadCachedAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        if (_authState.value.isLoggedIn) {
            scope.launch { refreshLoginState() }
        }
    }

    private fun loadCachedAuthState(): AuthState {
        val hasCookies = NetworkClient.cookieJar.hasCookiesForDomain("ithome.com")
        val cachedNick = prefs.getString(KEY_USER_NICK, "").orEmpty()
        val cachedAvatar = prefs.getString(KEY_AVATAR_URL, null)
        val cachedId = prefs.getString(KEY_USER_ID, "").orEmpty()
        val cachedLevel = prefs.getInt(KEY_USER_LEVEL, -1).takeIf { it >= 0 }
        val cachedExp = prefs.getInt(KEY_USER_EXP, -1).takeIf { it >= 0 }
        val cachedExpHint = prefs.getString(KEY_EXP_HINT, null)
        return if (hasCookies) {
            AuthState(
                isLoggedIn = true,
                userNick = cachedNick.ifBlank { "IT之家用户" },
                avatarUrl = cachedAvatar,
                userId = cachedId,
                level = cachedLevel,
                exp = cachedExp,
                expHint = cachedExpHint
            )
        } else {
            AuthState()
        }
    }

    /**
     * 从 Android WebView CookieManager 同步 Cookie 并向服务端校验登录状态。
     */
    suspend fun completeWebViewLogin(): Result<AuthState> = withContext(Dispatchers.IO) {
        _authState.value = _authState.value.copy(isChecking = true)
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()

            val jar = NetworkClient.cookieJar
            jar.importWebViewCookies("ithome.com", cookieManager.getCookie("https://www.ithome.com/"))
            jar.importWebViewCookies("ithome.com", cookieManager.getCookie("https://cmt.ithome.com/"))
            jar.importWebViewCookies("ithome.com", cookieManager.getCookie("https://napi.ithome.com/"))
            jar.importWebViewCookies("ruanmei.com", cookieManager.getCookie("https://my.ruanmei.com/"))

            if (!jar.hasCookiesForDomain("ithome.com")) {
                _authState.value = AuthState(isLoggedIn = false, isChecking = false)
                return@withContext Result.failure(IOException("未获取到有效登录会话，请完成登录后重试"))
            }

            val profileResult = fetchUserInfoFromServer()
            profileResult.fold(
                onSuccess = { profile ->
                    saveProfile(profile)
                    _authState.value = profile.copy(isChecking = false)
                    Result.success(_authState.value)
                },
                onFailure = { error ->
                    // 如果 cookie 已存在但服务器明确 401，说明未完成跨站同步
                    _authState.value = _authState.value.copy(isChecking = false)
                    Result.failure(error)
                }
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isChecking = false)
            Result.failure(e)
        }
    }

    /**
     * 刷新服务端会话有效性。若服务端返回 401，则自动清理过期会话。
     */
    suspend fun refreshLoginState(): Result<AuthState> = withContext(Dispatchers.IO) {
        if (!NetworkClient.cookieJar.hasCookiesForDomain("ithome.com")) {
            clearLocalSession()
            return@withContext Result.success(_authState.value)
        }

        _authState.value = _authState.value.copy(isChecking = true)
        return@withContext try {
            val result = fetchUserInfoFromServer()
            result.fold(
                onSuccess = { profile ->
                    saveProfile(profile)
                    _authState.value = profile.copy(isChecking = false)
                    Result.success(_authState.value)
                },
                onFailure = { err ->
                    if (err is UnauthorizedException) {
                        clearLocalSession()
                    } else {
                        _authState.value = _authState.value.copy(isChecking = false)
                    }
                    Result.failure(err)
                }
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isChecking = false)
            Result.failure(e)
        }
    }

    /**
     * 在评论列表接口或发评成功响应中顺带更新当前登录昵称/头像（兜底补充）。
     */
    fun updateProfileHint(userNick: String?, avatarUrl: String?) {
        val cleanNick = userNick?.trim().orEmpty()
        if (cleanNick.isEmpty() || !_authState.value.isLoggedIn) return
        val current = _authState.value
        if (current.userNick == "IT之家用户" || current.userNick.isBlank()) {
            val updated = current.copy(
                userNick = cleanNick,
                avatarUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: current.avatarUrl
            )
            saveProfile(updated)
            _authState.value = updated
        }
    }

    /**
     * 从评论禁言/等级限制文案中提取距离下一可评论门槛的经验值提示，供账号卡片展示。
     */
    fun updateExpHintFromTip(tipText: String) {
        if (!_authState.value.isLoggedIn) return
        val hint = extractExpHint(tipText) ?: return
        val current = _authState.value
        if (current.expHint != hint) {
            val updated = current.copy(expHint = hint)
            saveProfile(updated)
            _authState.value = updated
        }
    }

    /**
     * 退出登录：通知服务端注销并清空本地加密 Cookie 与 WebView Cookie。
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LOGOUT_URL)
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Referer", "https://www.ithome.com/")
                .build()
            NetworkClient.client.newCall(request).execute().close()
        }
        withContext(Dispatchers.Main) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        clearLocalSession()
    }

    private fun clearLocalSession() {
        NetworkClient.cookieJar.clearAll()
        prefs.edit().clear().apply()
        _authState.value = AuthState(isLoggedIn = false, isChecking = false)
    }

    private fun saveProfile(state: AuthState) {
        prefs.edit()
            .putString(KEY_USER_NICK, state.userNick)
            .putString(KEY_AVATAR_URL, state.avatarUrl)
            .putString(KEY_USER_ID, state.userId)
            .apply {
                if (state.level != null) putInt(KEY_USER_LEVEL, state.level)
                else remove(KEY_USER_LEVEL)
                if (state.exp != null) putInt(KEY_USER_EXP, state.exp)
                else remove(KEY_USER_EXP)
                if (state.expHint != null) putString(KEY_EXP_HINT, state.expHint)
                else remove(KEY_EXP_HINT)
            }
            .apply()
    }

    private fun fetchUserInfoFromServer(): Result<AuthState> {
        val napiRequest = Request.Builder()
            .url(USER_INFO_URL)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Origin", "https://www.ithome.com")
            .header("Referer", "https://www.ithome.com/")
            .build()

        var primaryState: AuthState? = null
        var unauthorized = false
        var lastError: IOException? = null

        NetworkClient.client.newCall(napiRequest).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 -> unauthorized = true
                !response.isSuccessful -> lastError = IOException("HTTP ${response.code}")
                else -> {
                    val body = response.body?.string().orEmpty()
                    primaryState = parseUserInfoJson(body)
                }
            }
        }

        // 同时尝试从移动站 userinfoget 补全 Level / Exp / UserNick（user-menu.js 使用该接口获取 Lv.X）
        val wapState = runCatching { fetchMobileUserInfo() }.getOrNull()

        val merged = mergeAuthStates(primaryState, wapState, existingHint = _authState.value.expHint)
        if (merged != null) {
            return Result.success(merged)
        }
        return if (unauthorized) {
            Result.failure(UnauthorizedException())
        } else {
            Result.failure(lastError ?: UnauthorizedException("登录状态尚未生效"))
        }
    }

    private fun fetchMobileUserInfo(): AuthState? {
        val request = Request.Builder()
            .url(WAP_USER_INFO_URL)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Origin", "https://m.ithome.com")
            .header("Referer", "https://m.ithome.com/")
            .build()
        return NetworkClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            parseUserInfoJson(body)
        }
    }

    private class UnauthorizedException(message: String = "登录已过期，请重新登录") : IOException(message)

    companion object {
        private const val USER_INFO_URL = "https://napi.ithome.com/api/user/getuserinfo"
        private const val WAP_USER_INFO_URL = "https://m.ithome.com/api/user/userinfoget"
        private const val LOGOUT_URL = "https://cmt.ithome.com/api/webcomment/logout"

        private const val PREFS_NAME = "ityou_auth_profile"
        private const val KEY_USER_NICK = "user_nick"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_LEVEL = "user_level"
        private const val KEY_USER_EXP = "user_exp"
        private const val KEY_EXP_HINT = "user_exp_hint"

        private val LEVEL_TARGET_REGEX = Regex("""【(\d+级)】""")
        private val REMAIN_EXP_REGEX = Regex("""还需\s*(\d+)\s*经验值""")

        internal fun extractExpHint(tipText: String): String? {
            val remain = REMAIN_EXP_REGEX.find(tipText)?.groupValues?.getOrNull(1) ?: return null
            val target = LEVEL_TARGET_REGEX.find(tipText)?.groupValues?.getOrNull(1)
            return if (target != null) {
                "距 $target 还需 $remain 经验值"
            } else {
                "还需 $remain 经验值"
            }
        }

        internal fun mergeAuthStates(
            primary: AuthState?,
            secondary: AuthState?,
            existingHint: String? = null
        ): AuthState? {
            if (primary == null && secondary == null) return null
            val base = primary ?: secondary!!
            val aux = if (primary != null) secondary else null
            val resolvedNick = base.userNick.takeIf { it.isNotBlank() && it != "IT之家用户" }
                ?: aux?.userNick?.takeIf { it.isNotBlank() && it != "IT之家用户" }
                ?: "IT之家用户"
            return AuthState(
                isLoggedIn = true,
                userNick = resolvedNick,
                avatarUrl = base.avatarUrl ?: aux?.avatarUrl,
                userId = base.userId.ifBlank { aux?.userId.orEmpty() },
                level = base.level ?: aux?.level,
                exp = base.exp ?: aux?.exp,
                expHint = existingHint
            )
        }

        /**
         * 纯函数：解析 `getuserinfo` 响应 JSON（兼容 PascalCase 与 camelCase 多种字段，便于单元测试）。
         */
        internal fun parseUserInfoJson(json: String): AuthState? {
            if (json.isBlank()) return null
            return runCatching {
                val root = JSONObject(json)
                if (root.has("success") && !root.optBoolean("success")) return null
                if (root.has("Success") && root.optInt("Success") != 1) return null

                val data = root.optJSONObject("data")
                    ?: root.optJSONObject("content")
                    ?: root.optJSONObject("Result")
                    ?: root.optJSONObject("userInfo")
                    ?: root

                val nick = data.optString("UserNick")
                    .ifBlank { data.optString("userNick") }
                    .ifBlank { data.optString("nickName") }
                    .ifBlank { data.optString("nickname") }
                    .ifBlank { data.optString("userName") }
                    .ifBlank { data.optString("name") }
                    .trim()

                val userId = data.optString("UserID")
                    .ifBlank { data.optString("userId") }
                    .ifBlank { data.optString("userid") }
                    .ifBlank { data.optString("id") }
                    .ifBlank {
                        val numId = data.optLong("UserID", 0L).takeIf { it > 0L }
                            ?: data.optLong("uid", 0L).takeIf { it > 0L }
                        numId?.toString().orEmpty()
                    }

                val rawAvatar = data.optString("UserAvatarHeadImg")
                    .ifBlank { data.optString("userAvatar") }
                    .ifBlank { data.optString("avatar") }
                    .ifBlank { data.optString("headImg") }
                    .ifBlank { data.optString("headimg") }
                    .trim()

                val normalizedAvatar = when {
                    rawAvatar.isEmpty() || rawAvatar == "null" -> null
                    rawAvatar.startsWith("//") -> "https:$rawAvatar"
                    else -> rawAvatar
                }

                val level = listOf("Level", "level", "UserLevel", "userLevel", "grade")
                    .firstNotNullOfOrNull { key ->
                        if (data.has(key) && !data.isNull(key)) {
                            data.optInt(key, -1).takeIf { it >= 0 }
                                ?: data.optString(key).trim().toIntOrNull()?.takeIf { it >= 0 }
                        } else {
                            null
                        }
                    }

                val exp = listOf("Exp", "exp", "UserExp", "userExp", "Score", "score")
                    .firstNotNullOfOrNull { key ->
                        if (data.has(key) && !data.isNull(key)) {
                            data.optInt(key, -1).takeIf { it >= 0 }
                                ?: data.optString(key).trim().toIntOrNull()?.takeIf { it >= 0 }
                        } else {
                            null
                        }
                    }

                // 服务端明确成功（200 OK 且 success == true）但未返回 nick 时保留兜底昵称
                if (nick.isEmpty() && userId.isEmpty() && !root.optBoolean("success", false)) {
                    return null
                }

                AuthState(
                    isLoggedIn = true,
                    userNick = nick.ifBlank { "IT之家用户" },
                    avatarUrl = normalizedAvatar,
                    userId = userId,
                    level = level,
                    exp = exp
                )
            }.getOrNull()
        }

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository(context).also { instance = it }
            }
    }
}
