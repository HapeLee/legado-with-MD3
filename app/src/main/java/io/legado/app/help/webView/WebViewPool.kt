package io.legado.app.help.webView

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 池化的 WebView 包装，用于复用 WebView 实例。
 */
class PooledWebView(val realWebView: WebView) {
    /**
     * 重置 WebView 状态以便放回池中复用。
     * 不销毁 WebView，仅清理上一次使用的状态。
     */
    fun reset() {
        realWebView.stopLoading()
        realWebView.webChromeClient = null
        realWebView.webViewClient = WebViewClient()
        realWebView.removeJavascriptInterface("searchRule")
        realWebView.removeJavascriptInterface("java")
        realWebView.removeJavascriptInterface("source")
        realWebView.removeJavascriptInterface("cache")
        (realWebView.parent as? android.view.ViewGroup)?.removeView(realWebView)
    }

    /**
     * 真正销毁 WebView，释放资源。仅在不再需要时调用。
     */
    fun destroy() {
        reset()
        realWebView.destroy()
    }
}

/**
 * WebView 对象池，避免频繁创建/销毁 WebView。
 */
object WebViewPool {
    private val pool = ConcurrentLinkedQueue<PooledWebView>()

    fun acquire(context: Context): PooledWebView {
        return pool.poll() ?: PooledWebView(WebView(context))
    }

    /**
     * 将 WebView 重置后归还到池中以便复用。
     */
    fun release(pooledWebView: PooledWebView) {
        pooledWebView.reset()
        pool.offer(pooledWebView)
    }

    /**
     * 销毁池中所有 WebView。可在 Application onTrimMemory 时调用。
     */
    fun clear() {
        while (true) {
            val pooled = pool.poll() ?: break
            pooled.destroy()
        }
    }
}
