package com.necoarc.ityou.ui.screens.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.necoarc.ityou.data.repository.AuthRepository
import com.necoarc.ityou.ui.theme.ShapeCache
import kotlinx.coroutines.launch

/**
 * IT之家 WebView 统一登录页。
 *
 * 通过加载处于 `https://www.ithome.com/` 上下文中的软媒 `login.min.js` embed 模式，
 * 完整支持账号密码、短信验证码、图形/滑块风控验证以及 `.ithome.com` 跨域 Cookie 同步。
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

    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryHex = remember(primaryColor) {
        String.format("#%06X", 0xFFFFFF and primaryColor.toArgb())
    }
    val bgColor = MaterialTheme.colorScheme.background
    val bgHex = remember(bgColor) {
        String.format("#%06X", 0xFFFFFF and bgColor.toArgb())
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

    val loginBridgeHtml = remember(isDark, primaryHex, bgHex) {
        buildEmbedLoginHtml(isDark = isDark, primaryHex = primaryHex, bgHex = bgHex)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                removeJavascriptInterface("ITYouAuth")
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
                        onClick = {
                            webViewRef?.loadDataWithBaseURL(
                                "https://www.ithome.com/user-login-bridge",
                                loginBridgeHtml,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        },
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
                            loginHtml = loginBridgeHtml,
                            onPageStarted = { isPageLoading = true },
                            onPageFinished = { url ->
                                isPageLoading = false
                                val cookies = CookieManager.getInstance()
                                    .getCookie("https://www.ithome.com/")
                                    .orEmpty()
                                // 检测到 ithome 域已经出现用户会话标志或回到 ithome 主页时，静默探测校验
                                if (cookies.contains("user=", ignoreCase = true) ||
                                    (url?.startsWith("https://www.ithome.com/") == true &&
                                        !url.contains("user-login-bridge"))
                                ) {
                                    triggerSessionSync(silentIfUnauthenticated = true)
                                }
                            },
                            onJsLoginSuccess = {
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
    loginHtml: String,
    onPageStarted: () -> Unit,
    onPageFinished: (String?) -> Unit,
    onJsLoginSuccess: () -> Unit
): WebView {
    return WebView(context).apply {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
        }

        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onLoginSuccess() {
                    post { onJsLoginSuccess() }
                }
            },
            "ITYouAuth"
        )

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
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase().orEmpty()
                // 仅允许 http/https 在 WebView 内跳转，防止外部 App Scheme 导致报错页面
                return scheme != "http" && scheme != "https"
            }
        }

        loadDataWithBaseURL(
            "https://www.ithome.com/user-login-bridge",
            loginHtml,
            "text/html",
            "UTF-8",
            null
        )
    }
}

private fun buildEmbedLoginHtml(
    isDark: Boolean,
    primaryHex: String,
    bgHex: String
): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link href="https://dat.ruanmei.com/lib/login.min.css" type="text/css" rel="stylesheet" />
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background-color: $bgHex;
                    overflow-x: hidden;
                }
                #login-root {
                    width: 100%;
                    height: 100%;
                    min-height: 520px;
                }
            </style>
        </head>
        <body>
            <div id="login-root"></div>
            <script src="https://dat.ruanmei.com/lib/login.min.js"></script>
            <script>
                (function() {
                    if (typeof window.loginHelper === 'undefined') {
                        window.location.href = 'https://my.ruanmei.com/user-login/index.htm';
                        return;
                    }
                    window.loginHelper.requestLogin({
                        mode: 'embed',
                        embedIn: document.getElementById('login-root'),
                        darkMode: $isDark,
                        themeColor: '$primaryHex',
                        onLogin: function() {
                            // 给 hidden iframe 种下 .ithome.com cookie 留出 600ms 缓冲
                            setTimeout(function() {
                                if (window.ITYouAuth && window.ITYouAuth.onLoginSuccess) {
                                    window.ITYouAuth.onLoginSuccess();
                                }
                            }, 600);
                        }
                    });
                })();
            </script>
        </body>
        </html>
    """.trimIndent()
}
