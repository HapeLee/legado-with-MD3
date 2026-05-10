package io.legado.app.ui.rss.article

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.data.entities.RssSource
import io.legado.app.help.WebCacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.CookieManager
import io.legado.app.help.webView.WebJsExtensions
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RssStartPage(
    rssSource: RssSource?,
    onNavigateToArticles: (sortUrl: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val startHtml = rssSource?.startHtml
    val startStyle = rssSource?.startStyle ?: rssSource?.style
    val startJs = rssSource?.startJs
    val preloadJs = rssSource?.preloadJs
    val hasPreloadJs = !preloadJs.isNullOrBlank()

    LaunchedEffect(startHtml) {
        if (startHtml.isNullOrBlank()) {
            onNavigateToArticles(null)
        }
    }

    if (startHtml.isNullOrBlank()) {
        return
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (isLoading && progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = AppConfig.userAgent
                        cacheMode = if (rssSource?.cacheFirst == true) {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        } else {
                            WebSettings.LOAD_DEFAULT
                        }
                    }

                    CookieManager.applyToWebView(rssSource?.sourceUrl.orEmpty())

                    if (hasPreloadJs && activity != null && rssSource != null) {
                        val webJsExtensions = WebJsExtensions(
                            rssSource,
                            activity,
                            this,
                            callback = object : WebJsExtensions.Callback {
                                override fun upConfig(config: String) {}
                                override fun onNavigateToArticles(sortUrl: String?) {
                                    activity.runOnUiThread {
                                        onNavigateToArticles(sortUrl)
                                    }
                                }
                            }
                        )
                        addJavascriptInterface(webJsExtensions, WebJsExtensions.nameJava)
                        addJavascriptInterface(rssSource, WebJsExtensions.nameSource)
                        addJavascriptInterface(WebCacheManager, WebJsExtensions.nameCache)
                    }

                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun navigateToArticles() {
                            activity?.runOnUiThread {
                                onNavigateToArticles(null)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun getSourceUrl(): String {
                            return rssSource?.sourceUrl.orEmpty()
                        }

                        @android.webkit.JavascriptInterface
                        fun getSourceName(): String {
                            return rssSource?.sourceName.orEmpty()
                        }
                    }, "AndroidBridge")

                    var jsInjected = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url = request.url.toString()
                            if (!jsInjected && url == WebJsExtensions.nameUrl) {
                                jsInjected = true
                                val jsContent = buildString {
                                    append("(() => {")
                                    append(WebJsExtensions.JS_INJECTION)
                                    if (!preloadJs.isNullOrBlank()) {
                                        append("\n")
                                        append(preloadJs)
                                    }
                                    append("\n})();")
                                }
                                return WebResourceResponse(
                                    "text/javascript",
                                    "utf-8",
                                    ByteArrayInputStream(jsContent.toByteArray())
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (rssSource?.showWebLog == true) {
                                consoleMessage?.message()?.let { message ->
                                    io.legado.app.constant.AppLog.put("StartPage: $message")
                                }
                            }
                            return true
                        }
                    }

                    val htmlContent = buildStartPageHtml(startHtml, startStyle, startJs, hasPreloadJs)
                    loadDataWithBaseURL(
                        rssSource?.sourceUrl,
                        htmlContent,
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun buildStartPageHtml(
    html: String,
    style: String?,
    startJs: String?,
    hasPreloadJs: Boolean
): String {
    val styleContent = style?.takeIf { it.isNotBlank() }?.let {
        "<style>$it</style>"
    } ?: "<style>img{max-width:100% !important; width:auto; height:auto;}body{word-wrap:break-word; height:auto;max-width: 100%; width:auto;}</style>"

    val jsScript = startJs?.takeIf { it.isNotBlank() }?.let {
        "<script>$it</script>"
    } ?: ""

    val preloadScript = if (hasPreloadJs) {
        WebJsExtensions.JS_URL
    } else {
        ""
    }

    val htmlBuilder = StringBuilder()

    val headIndex = html.indexOf("<head>")
    if (headIndex >= 0) {
        htmlBuilder.append(html, 0, headIndex + 6)
        htmlBuilder.append(preloadScript)
        htmlBuilder.append(html, headIndex + 6, html.length)
    } else {
        htmlBuilder.append("<head>")
        htmlBuilder.append(preloadScript)
        htmlBuilder.append("</head>")
        htmlBuilder.append(html)
    }

    val result = htmlBuilder.toString()

    val styleEndIndex = result.indexOf("</style>")
    return if (styleEndIndex >= 0) {
        val insertIndex = styleEndIndex + 8
        buildString {
            append(result, 0, insertIndex)
            append(styleContent)
            append(result, insertIndex, result.length)
            append(jsScript)
        }
    } else {
        val headEndIndex = result.indexOf("</head>")
        if (headEndIndex >= 0) {
            buildString {
                append(result, 0, headEndIndex)
                append(styleContent)
                append(result, headEndIndex, result.length)
                append(jsScript)
            }
        } else {
            buildString {
                append(styleContent)
                append(result)
                append(jsScript)
            }
        }
    }
}
