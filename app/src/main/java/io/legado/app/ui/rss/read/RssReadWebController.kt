@file:Suppress("DEPRECATION")

package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.JsExtProvider
import io.legado.app.help.WebCacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.Download
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.utils.applyDayNight
import io.legado.app.utils.isTrue
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.setDarkeningAllowed
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.regex.PatternSyntaxException

internal data class RssReadWebControllerCallbacks(
    val onProgressChanged: (Int) -> Unit,
    val onPageTitleResolved: (String) -> Unit,
    val onShowCustomView: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    val onHideCustomView: () -> Unit,
    val onShowPhoto: (src: String, sourceOrigin: String?) -> Unit,
    val navigateToArticles: (sortUrl: String?, origin: String?) -> Unit,
    val onAskRedirect: (String?, String, (Boolean) -> Unit) -> Unit,
    val onCloseRequested: () -> Unit,
    val isFullscreenProvider: () -> Boolean,
) : WebJsExtensions.Callback {
    override fun upConfig(config: String) = Unit

    override fun onNavigateToArticles(sortUrl: String?, origin: String?) {
        navigateToArticles(sortUrl, origin)
    }
}

private val webCookieManager: CookieManager by lazy { CookieManager.getInstance() }

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
internal fun configureRssReadWebView(
    webView: VisibleWebView,
    context: Context,
    activity: Activity?,
    appCompatActivity: AppCompatActivity?,
    viewModel: ReadRssViewModel,
    initialTitle: String?,
    redirectPolicyProvider: () -> RedirectPolicy,
    callbacks: RssReadWebControllerCallbacks
) {
    webView.webChromeClient = object : WebChromeClient() {
        override fun getDefaultVideoPoster(): Bitmap {
            return super.getDefaultVideoPoster() ?: createBitmap(100, 100)
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            callbacks.onProgressChanged(newProgress)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            callbacks.onShowCustomView(view, callback)
        }

        override fun onHideCustomView() {
            callbacks.onHideCustomView()
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            val source = viewModel.rssSource
            if (source?.showWebLog == true) {
                val messageLevel = consoleMessage.messageLevel().name
                val message = consoleMessage.message()
                AppLog.put(
                    "${source.getTag()}${messageLevel}: $message",
                    NoStackTraceException(
                        "\n${message}\n- Line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                    )
                )
                return true
            }
            return false
        }

        override fun onCloseWindow(window: WebView?) {
            callbacks.onCloseRequested()
        }
    }

    webView.webViewClient = object : WebViewClient() {
        private var lastUrl: String? = null
        private var preloadJsInjected = false

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val targetUri = request.url
            if (targetUri.scheme == "legado" || targetUri.scheme == "yuedu") {
                return handleCustomScheme(targetUri)
            }
            val currentUrl = lastUrl ?: view.url
            val targetUrl = request.url.toString()
            lastUrl = targetUrl
            if (!request.isForMainFrame) return false
            if (handleRedirect(view, currentUrl, targetUrl)) return true
            return handleCustomScheme(request.url)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            val targetUri = url.toUri()
            if (targetUri.scheme == "legado" || targetUri.scheme == "yuedu") {
                return handleCustomScheme(targetUri)
            }
            val currentUrl = lastUrl ?: view.url
            val targetUrl = url
            lastUrl = targetUrl
            if (handleRedirect(view, currentUrl, targetUrl)) return true
            return handleCustomScheme(targetUri)
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val source = viewModel.rssSource ?: return super.shouldInterceptRequest(view, request)
            val url = request.url.toString()
            if (request.isForMainFrame) {
                if (viewModel.hasPreloadJs) {
                    preloadJsInjected = false
                    if (url.startsWith("data:text/html;") || request.method == "POST") {
                        return super.shouldInterceptRequest(view, request)
                    }
                    return runBlocking(IO) {
                        getModifiedContentWithJs(url, request) ?: super.shouldInterceptRequest(
                            view,
                            request
                        )
                    }
                }
            } else if (!preloadJsInjected && url == WebJsExtensions.nameUrl) {
                preloadJsInjected = true
                val preloadJs = source.preloadJs ?: ""
                return WebResourceResponse(
                    "text/javascript",
                    "utf-8",
                    ByteArrayInputStream("(() => {${WebJsExtensions.JS_INJECTION}\n$preloadJs\n})();".toByteArray())
                )
            }
            val blacklist = source.contentBlacklist?.splitNotBlank(",")
            if (!blacklist.isNullOrEmpty()) {
                blacklist.forEach {
                    try {
                        if (url.startsWith(it) || url.matches(it.toRegex())) {
                            return createEmptyResource()
                        }
                    } catch (e: PatternSyntaxException) {
                        AppLog.put("黑名单规则正则语法错误 源名称:${source.sourceName} 正则:$it", e)
                    }
                }
            } else {
                val whitelist = source.contentWhitelist?.splitNotBlank(",")
                if (!whitelist.isNullOrEmpty()) {
                    whitelist.forEach {
                        try {
                            if (url.startsWith(it) || url.matches(it.toRegex())) {
                                return super.shouldInterceptRequest(view, request)
                            }
                        } catch (e: PatternSyntaxException) {
                            AppLog.put(
                                "白名单规则正则语法错误 源名称:${source.sourceName} 正则:$it",
                                e
                            )
                        }
                    }
                    return createEmptyResource()
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        private suspend fun getModifiedContentWithJs(
            url: String,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return try {
                val cookie = webCookieManager.getCookie(url)
                val res = okHttpClient.newCallResponse {
                    url(url)
                    method(request.method, null)
                    if (!cookie.isNullOrEmpty()) {
                        addHeader("Cookie", cookie)
                    }
                    request.requestHeaders?.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                res.headers("Set-Cookie").forEach { setCookie ->
                    webCookieManager.setCookie(url, setCookie)
                }
                val body = res.body
                val contentType = body.contentType()
                if (!shouldInjectPreloadJs(contentType, res.header("Content-Disposition"))) {
                    res.close()
                    return null
                }
                val mimeType = contentType?.toString()?.substringBefore(";") ?: "text/html"
                val charset = contentType?.charset() ?: Charsets.UTF_8
                val charsetName = charset.name()
                val bodyText = body.text().let { originalText ->
                    val headIndex = originalText.indexOf("<head", ignoreCase = true)
                    if (headIndex >= 0) {
                        val closingHeadIndex = originalText.indexOf('>', startIndex = headIndex)
                        if (closingHeadIndex >= 0) {
                            val insertPos = closingHeadIndex + 1
                            StringBuilder(originalText).insert(insertPos, WebJsExtensions.JS_URL)
                                .toString()
                        } else {
                            originalText
                        }
                    } else {
                        originalText
                    }
                }
                WebResourceResponse(
                    mimeType,
                    charsetName,
                    ByteArrayInputStream(bodyText.toByteArray(charset))
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun createEmptyResource(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain", "utf-8", ByteArrayInputStream("".toByteArray())
            )
        }

        private fun handleRedirect(view: WebView, fromUrl: String?, toUrl: String): Boolean {
            val fromHost = fromUrl?.toUri()?.host
            val toHost = toUrl.toUri().host
            val crossOrigin = fromHost != null && toHost != null && fromHost != toHost

            return when (redirectPolicyProvider()) {
                RedirectPolicy.ALLOW_ALL -> false
                RedirectPolicy.BLOCK_ALL -> {
                    context.toastOnUi("已阻止重定向")
                    true
                }

                RedirectPolicy.ASK_ALWAYS -> {
                    askUser(fromUrl, toUrl) { if (it) view.loadUrl(toUrl) }
                    true
                }

                RedirectPolicy.ASK_CROSS_ORIGIN -> {
                    if (crossOrigin) {
                        askUser(fromUrl, toUrl) { if (it) view.loadUrl(toUrl) }
                        true
                    } else false
                }

                RedirectPolicy.BLOCK_CROSS_ORIGIN -> {
                    if (crossOrigin) {
                        context.toastOnUi("已阻止跨域重定向")
                        true
                    } else false
                }

                RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS -> {
                    if (crossOrigin) {
                        context.toastOnUi("已阻止域外跳转")
                        true
                    } else {
                        askUser(fromUrl, toUrl) { if (it) view.loadUrl(toUrl) }
                        true
                    }
                }
            }
        }

        private fun askUser(fromUrl: String?, toUrl: String, onResult: (Boolean) -> Unit) {
            callbacks.onAskRedirect(fromUrl, toUrl, onResult)
        }

        private fun handleCustomScheme(url: Uri): Boolean {
            val source = viewModel.rssSource
            val js = source?.shouldOverrideUrlLoading
            if (!js.isNullOrBlank() && appCompatActivity != null) {
                val t = SystemClock.uptimeMillis()
                val result = kotlin.runCatching {
                    runScriptWithContext(appCompatActivity.lifecycleScope.coroutineContext) {
                        source.evalJS(js) {
                            put("java", RssJsExtensions(appCompatActivity, source))
                            put("url", url.toString())
                        }.toString()
                    }
                }.onFailure {
                    AppLog.put("${source.getTag()}: url跳转拦截js出错", it)
                }.getOrNull()
                if (SystemClock.uptimeMillis() - t > 30) {
                    AppLog.put("${source.getTag()}: url跳转拦截js执行耗时过长")
                }
                if (result.isTrue()) return true
            }

            return when (url.scheme) {
                "http", "https", "jsbridge" -> false
                "legado", "yuedu" -> {
                    context.startActivity<OnLineImportActivity> {
                        data = url
                    }
                    true
                }

                else -> {
                    val root = activity?.findViewById<View>(android.R.id.content)
                    if (root != null) {
                        root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            context.openUrl(url)
                        }
                    } else {
                        context.openUrl(url)
                    }
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            view?.evaluateJavascript(WebJsExtensions.basicJs, null)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            val finishedUrl = url ?: return
            if (URLUtil.isNetworkUrl(finishedUrl)) {
                webCookieManager.getCookie(finishedUrl)?.takeIf { it.isNotBlank() }?.let { cookie ->
                    CookieStore.setCookie(finishedUrl, cookie)
                }
            }
            view.applyDayNight(AppConfig.isNightTheme)
            view.title?.let { webTitle ->
                if (
                    webTitle != url &&
                    webTitle != view.url &&
                    webTitle.isNotBlank() &&
                    url != "about:blank"
                ) {
                    callbacks.onPageTitleResolved(webTitle)
                } else {
                    callbacks.onPageTitleResolved(initialTitle.orEmpty())
                }
            }
            viewModel.rssSource?.injectJs?.let {
                if (it.isNotBlank()) {
                    view.evaluateJavascript(it, null)
                }
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.proceed()
        }
    }

    webView.settings.apply {
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        allowContentAccess = true
        builtInZoomControls = true
        displayZoomControls = false
        textZoom = 100
        setDarkeningAllowed(AppConfig.isNightTheme)
        userAgentString = viewModel.headerMap[AppConst.UA_NAME] ?: AppConfig.userAgent
        viewModel.rssSource?.let { source ->
            javaScriptEnabled = source.enableJs
            cacheMode = if (source.cacheFirst == true) {
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            } else {
                WebSettings.LOAD_DEFAULT
            }
        }
    }

    webView.addJavascriptInterface(object {
        @JavascriptInterface
        fun isNightTheme(): Boolean = AppConfig.isNightTheme
    }, "thisActivity")

    webView.addJavascriptInterface(object {
        @JavascriptInterface
        fun lockOrientation(orientation: String) {
            val ctx = activity
            if (ctx != null && callbacks.isFullscreenProvider() && !ctx.isFinishing && !ctx.isDestroyed) {
                ctx.runOnUiThread {
                    ctx.requestedOrientation = when (orientation) {
                        "portrait", "portrait-primary" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "portrait-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        "landscape-primary" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        "landscape-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        "any", "unspecified" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }

        @JavascriptInterface
        fun onCloseRequested() {
            val ctx = activity
            if (ctx != null && !ctx.isFinishing && !ctx.isDestroyed) {
                ctx.runOnUiThread {
                    callbacks.onCloseRequested()
                }
            }
        }
    }, WebJsExtensions.nameBasic)

    webView.setOnLongClickListener {
        val hitTestResult = webView.hitTestResult
        if (
            hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
            hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        ) {
            hitTestResult.extra?.let { webPic ->
                appCompatActivity?.selector(
                    arrayListOf(
                        SelectItem(context.getString(R.string.action_save), "save"),
                    )
                ) { _, charSequence, _ ->
                    if (charSequence.value == "save") {
                        viewModel.saveImage(webPic)
                    }
                }
                return@setOnLongClickListener true
            }
        }
        false
    }

    webView.setDownloadListener { url, _, contentDisposition, _, _ ->
        var fileName = URLUtil.guessFileName(url, contentDisposition, null)
        fileName = URLDecoder.decode(fileName, "UTF-8")
        val root = appCompatActivity?.findViewById<View>(android.R.id.content)
        if (root != null) {
            root.longSnackbar(fileName, context.getString(R.string.action_download)) {
                Download.start(context, url, fileName)
            }
        }
    }
}

private fun shouldInjectPreloadJs(
    contentType: MediaType?,
    contentDisposition: String?
): Boolean {
    if (contentDisposition
            ?.substringBefore(';')
            ?.trim()
            ?.equals("attachment", ignoreCase = true) == true
    ) return false
    return contentType == null ||
            contentType.type == "text" && contentType.subtype == "html" ||
            contentType.type == "application" && contentType.subtype == "xhtml+xml"
}

/**
 * 在页面内容加载前注入 preloadJs 所需的 JS 接口（java/source/cache）。
 * 必须在 [ReadRssViewModel.hasPreloadJs] 确定后调用，否则接口会因时序缺失而
 * 导致 startJs 首行（依赖 window.cache 等）抛错，进而在 let 声明前中断脚本。
 */
@SuppressLint("JavascriptInterface")
internal fun injectRssReadJsInterfaces(
    webView: WebView,
    viewModel: ReadRssViewModel,
    appCompatActivity: AppCompatActivity?,
    callbacks: RssReadWebControllerCallbacks,
    injectedSourceUrl: String? = null,
    onInjected: (String) -> Unit = {}
) {
    val source = viewModel.rssSource ?: return
    if (injectedSourceUrl == source.sourceUrl) return
    if (!viewModel.hasPreloadJs || source.preloadJs.isNullOrBlank()) return
    if (appCompatActivity == null) return
    val webJsExtensions = WebJsExtensions(
        source,
        appCompatActivity,
        webView,
        callback = callbacks,
        onShowPhoto = callbacks.onShowPhoto,
    )
    webView.addJavascriptInterface(webJsExtensions, WebJsExtensions.nameJava)
    webView.addJavascriptInterface(JsExtProvider.wrap(source), WebJsExtensions.nameSource)
    webView.addJavascriptInterface(WebCacheManager, WebJsExtensions.nameCache)
    onInjected(source.sourceUrl)
}
