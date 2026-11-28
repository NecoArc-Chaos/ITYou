package com.necoarc.ityou.ui.screens.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.necoarc.ityou.data.repository.AuthRepository
import com.necoarc.ityou.ui.theme.ShapeCache
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * IT之家 WebView 统一登录页。
 *
 * 直接加载软媒官方统一登录页 `https://my.ruanmei.com/user-login/index.htm`（附带 `forsid` 与主题配色），
 * 避免 `loadDataWithBaseURL` 在嵌套 iframe 场景下因安全源或容器高度坍缩导致的白屏问题。
 * 登录成功后官方页会自动执行跨站种 Cookie 并跳转回 `https://m.ithome.com/`，
 * 客户端拦截该回调跳转并同步加密持久化 `.ithome.com` 会话 Cookie。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authRepository = remember(context) { AuthRepository.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryHex = remember(primaryColor) {
        String.format("%06X", 0xFFFFFF and primaryColor.toArgb())
    }
    val loginUrl = remember(isDark, primaryHex) {
        buildDirectLoginUrl(isDark = isDark, primaryHex = primaryHex)
    }

    var isPageLoading by remember { mutableStateOf(true) }
    var isVerifyingSession by remember { mutableStateOf(false) }
    var completedAlready by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun triggerSessionSync(silentIfUnauthenticated: Boolean) {
        if (completedAlready || isVerifyingSession) return
        coroutineScope.launch {
            isVerifyingSession = true
            val result = authRepository.completeWebViewLogin()
            isVerifyingSession = false
            result.onSuccess {
                completedAlready = true
                onLoginSuccess()
            }.onFailure { err ->
                if (!silentIfUnauthenticated) {
                    snackbarHostState.showSnackbar(
                        err.localizedMessage ?: "尚未检测到有效登录状态，请先完成登录"
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "登录 IT之家",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        shape = ShapeCache.smoothPill,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { triggerSessionSync(silentIfUnauthenticated = false) },
                        enabled = !isVerifyingSession,
                        shape = ShapeCache.smoothPill,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (isVerifyingSession) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("校验中", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("我已登录", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    FilledTonalIconButton(
                        onClick = { webViewRef?.loadUrl(loginUrl) },
                        shape = ShapeCache.smoothPill,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新登录页",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isPageLoading || isVerifyingSession) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Surface(
                shape = ShapeCache.smooth16,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "建议使用手机号短信验证或账号密码登录。登录成功后会自动同步会话；若页面未自动返回，请点击右上角「我已登录」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        createConfiguredLoginWebView(
                            context = ctx,
                            loginUrl = loginUrl,
                            onPageStarted = { isPageLoading = true },
                            onPageFinished = { url ->
                                isPageLoading = false
                                val cookies = CookieManager.getInstance()
                                    .getCookie(ITHOME_CALLBACK_TARGET)
                                    .orEmpty()
                                val reachedCallbackHost = url != null &&
                                    (url.startsWith(ITHOME_CALLBACK_TARGET) ||
                                        url.startsWith("https://www.ithome.com"))
                                if (reachedCallbackHost || cookies.contains("user=", ignoreCase = true)) {
                                    triggerSessionSync(silentIfUnauthenticated = !reachedCallbackHost)
                                }
                            },
                            onReachedCallback = {
                                triggerSessionSync(silentIfUnauthenticated = false)
                            }
                        ).also { webViewRef = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredLoginWebView(
    context: android.content.Context,
    loginUrl: String,
    onPageStarted: () -> Unit,
    onPageFinished: (String?) -> Unit,
    onReachedCallback: () -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
        }

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onPageStarted()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinished(url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                val scheme = request?.url?.scheme?.lowercase().orEmpty()
                if (scheme != "http" && scheme != "https") {
                    return true
                }
                // 登录成功后 official script 会将顶层跳转到 forsid 指定的 m.ithome.com / www.ithome.com
                if (url.startsWith(ITHOME_CALLBACK_TARGET) || url.startsWith("https://www.ithome.com")) {
                    post { onReachedCallback() }
                    return true
                }
                return false
            }
        }

        loadUrl(loginUrl)
    }
}

private const val ITHOME_CALLBACK_TARGET = "https://m.ithome.com/"

private fun buildDirectLoginUrl(isDark: Boolean, primaryHex: String): String {
    val reversedTarget = ITHOME_CALLBACK_TARGET.reversed()
    val encodedForsid = URLEncoder.encode(reversedTarget, StandardCharsets.UTF_8.toString())
    val darkParam = if (isDark) "&dark=1" else ""
    return "https://my.ruanmei.com/user-login/index.htm?forsid=$encodedForsid&tc=c$primaryHex$darkParam"
}
