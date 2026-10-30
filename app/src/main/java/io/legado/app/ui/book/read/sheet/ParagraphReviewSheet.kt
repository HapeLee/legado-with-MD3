package io.legado.app.ui.book.read.sheet

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.BookType
import io.legado.app.help.WebCacheManager
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.JS_INJECTION
import io.legado.app.help.webView.WebJsExtensions.Companion.basicJs
import io.legado.app.help.webView.WebJsExtensions.Companion.nameBasic
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.WebViewContainerCompose
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.applyDayNight
import kotlinx.coroutines.currentCoroutineContext

/**
 * 段评弹层。
 *
 * 书源在段评图标的 click 里写 `java.showBrowser(url, html)`：
 * - 有 html（书源已经把段评内容抓下来）→ 直接渲染；
 * - 只有 url（段评要按规则再请求一次）→ 走 [AnalyzeUrl] 带上书源请求头/Cookie 取回正文。
 *
 * 页面里的 `java.*` / `source.*` / `cache.*` 与旧的半屏 WebView 是同一套桥，
 * 所以点赞、回复这类交互书源不需要改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParagraphReviewSheet(
    show: Boolean,
    request: ReadBookSheet.ParagraphReview?,
    onDismissRequest: () -> Unit,
) {
    val isDark = LegadoTheme.isDark
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // WebView 只在首次组合时构造，preloadJs 却随每次请求变化——用持有者让回调读最新值
    val preloadJsHolder = remember { PreloadJsHolder() }
    preloadJsHolder.value = request?.preloadJs

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        animateContentSize = false,
        contentPaddingEnabled = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight * 0.72f),
        ) {
            WebViewContainerCompose(
                modifier = Modifier.fillMaxSize(),
                createWebView = { context ->
                    buildReviewWebView(
                        context = context,
                        isDark = isDark,
                        preloadJsHolder = preloadJsHolder,
                        onLoadingChange = { loading = it },
                        onCloseRequest = onDismissRequest,
                    )
                },
                update = { created -> webView = created },
                onDestroyed = { webView = null },
            )
            if (loading) {
                AppCircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
            errorMessage?.let { message ->
                AppText(
                    text = stringResource(R.string.error_load_msg, message),
                    color = LegadoTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
        }
    }

    LaunchedEffect(webView, request) {
        val target = webView ?: return@LaunchedEffect
        val sheet = request ?: return@LaunchedEffect
        errorMessage = null
        loading = true
        runCatching { loadReviewPage(target, sheet) }
            .onFailure {
                errorMessage = it.localizedMessage
                    ?: it.javaClass.simpleName
                loading = false
            }
    }

    LaunchedEffect(webView, isDark) {
        webView?.applyDayNight(isDark)
    }
}

private suspend fun loadReviewPage(
    webView: WebView,
    request: ReadBookSheet.ParagraphReview,
) {
    val inlineHtml = request.html
    val baseUrl = request.url.ifBlank { "about:blank" }
    if (!inlineHtml.isNullOrBlank()) {
        webView.loadDataWithBaseURL(baseUrl, inlineHtml, "text/html", "utf-8", baseUrl)
        return
    }
    if (request.url.isBlank()) error("缺少段评地址")
    val analyzeUrl = AnalyzeUrl(
        request.url,
        source = ReadBook.bookSource,
        coroutineContext = currentCoroutineContext(),
    )
    val body = analyzeUrl.getStrResponseAwait().body
    if (body.isNullOrBlank()) error("段评内容为空")
    analyzeUrl.headerMap[AppConst.UA_NAME]?.let { webView.settings.userAgentString = it }
    webView.loadDataWithBaseURL(analyzeUrl.url, body, "text/html", "utf-8", analyzeUrl.url)
}

@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
private fun buildReviewWebView(
    context: Context,
    isDark: Boolean,
    preloadJsHolder: PreloadJsHolder,
    onLoadingChange: (Boolean) -> Unit,
    onCloseRequest: () -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(0)
    overScrollMode = View.OVER_SCROLL_NEVER
    settings.apply {
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        javaScriptEnabled = true
        builtInZoomControls = true
        displayZoomControls = false
    }
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onLoadingChange(true)
            view?.evaluateJavascript(basicJs, null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onLoadingChange(false)
            view?.applyDayNight(isDark)
            preloadJsHolder.value?.takeIf { it.isNotBlank() }?.let { preloadJs ->
                view?.evaluateJavascript("(() => {\n$JS_INJECTION\n$preloadJs\n})();", null)
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val uri = request?.url ?: return false
            return when (uri.scheme) {
                "http", "https" -> false
                else -> {
                    runCatching {
                        view?.context?.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        )
                    }
                    true
                }
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?,
        ) {
            handler?.proceed()
        }
    }
    ReadBook.bookSource?.let { source ->
        addJavascriptInterface(source, nameSource)
        addJavascriptInterface(WebCacheManager, nameCache)
        addJavascriptInterface(ReviewBasicBridge(onCloseRequest), nameBasic)
        context.findActivity()?.let { activity ->
            addJavascriptInterface(
                WebJsExtensions(source, activity, this, BookType.text),
                nameJava,
            )
        }
    }
}

/** 段评页面里 `window.close()` / `screen.orientation.lock()` 的落点。 */
private class ReviewBasicBridge(
    private val onCloseRequest: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onCloseRequested() {
        mainHandler.post(onCloseRequest)
    }

    @JavascriptInterface
    fun lockOrientation(orientation: String) = Unit
}

/** WebView 只构造一次，[WebJsExtensions.Companion.JS_INJECTION] 需要读每次请求最新的 preloadJs。 */
private class PreloadJsHolder {
    var value: String? = null
}

private tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
