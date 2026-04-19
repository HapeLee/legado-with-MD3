@file:Suppress("DEPRECATION")

package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.Download
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.utils.isTrue
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.setDarkeningAllowed
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import java.net.URLDecoder

internal data class RssReadWebControllerCallbacks(
    val onProgressChanged: (Int) -> Unit,
    val onPageTitleResolved: (String) -> Unit
)

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
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            callbacks.onProgressChanged(newProgress)
        }
    }

    webView.webViewClient = object : WebViewClient() {
        private var lastUrl: String? = null

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
            AlertDialog.Builder(context)
                .setTitle("重定向请求")
                .setMessage("是否允许页面跳转？\n\n来源：${fromUrl ?: "未知"}\n目标：$toUrl")
                .setPositiveButton("允许") { _, _ -> onResult(true) }
                .setNegativeButton("拒绝") { _, _ -> onResult(false) }
                .setCancelable(true)
                .show()
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

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
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
        allowContentAccess = true
        builtInZoomControls = true
        displayZoomControls = false
        setDarkeningAllowed(AppConfig.isNightTheme)
        if (viewModel.rssSource?.enableJs == true) {
            javaScriptEnabled = true
        }
    }

    webView.addJavascriptInterface(object {
        @JavascriptInterface
        fun isNightTheme(): Boolean = AppConfig.isNightTheme
    }, "thisActivity")

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
