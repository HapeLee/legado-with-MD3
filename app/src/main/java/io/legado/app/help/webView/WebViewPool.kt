package io.legado.app.help.webView

import android.content.Context
import android.webkit.WebView
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 池化的 WebView 包装，用于复用 WebView 实例。
 */
class PooledWebView(val realWebView: WebView) {
    fun release() {
        realWebView.stopLoading()
        realWebView.webChromeClient = null
        realWebView.webViewClient = WebViewClient()
        realWebView.removeJavascriptInterface(nameCache)
        realWebView.removeJavascriptInterface(nameSource)
        realWebView.removeJavascriptInterface(nameJava)
        (realWebView.parent as? android.view.ViewGroup)?.removeView(realWebView)
        realWebView.destroy()
    }
}

/**
 * WebView 对象池，避免频繁创建/销毁 WebView。
 */
object WebViewPool {
    private val pool = ConcurrentLinkedQueue<PooledWebView>()

    fun acquire(context: Context): PooledWebView {
        val pooled = pool.poll()
        return pooled ?: PooledWebView(WebView(context))
    }

    fun release(pooledWebView: PooledWebView) {
        pooledWebView.release()
    }
}
