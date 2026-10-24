package io.legado.app.ui.widget.components

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose 中承载 WebView 的互操作容器。
 *
 * WebView 不直接作为 AndroidView 的子 View，而是先包一层 FrameLayout：
 * 裸 WebView 直接挂进互操作容器时，其 relayout/resize 会触发 Vulkan functor
 * 重绘路径，在 Android 16 HWUI Vulkan 后端上可能引发 drawVk SIGTRAP 原生崩溃
 * （issue #2468）。FrameLayout 间接层是社区与本仓库浏览器/RSS 页已验证的承载方式。
 *
 * @param createWebView 在 AndroidView factory 中调用，完成 WebView 构造与
 *   实例级配置（settings、JavascriptInterface、WebChromeClient 等），每个容器
 *   生命周期只执行一次。
 * @param update 每次重组调用，与 AndroidView.update 语义一致；用于随状态变化
 *   驱动加载或更新 client。
 * @param onDestroyed 容器销毁、WebView destroy 前回调。
 */
@Composable
fun WebViewContainerCompose(
    modifier: Modifier = Modifier,
    createWebView: (Context) -> WebView,
    update: (WebView) -> Unit = {},
    onDestroyed: (() -> Unit)? = null,
) {
    val webViewHolder = remember { WebViewHolder() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                val webView = createWebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                addView(webView)
                webViewHolder.webView = webView
            }
        },
        update = { container ->
            val webView = webViewHolder.webView
                ?: container.getChildAt(0) as? WebView
            if (webView != null) {
                webViewHolder.webView = webView
                update(webView)
            }
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            onDestroyed?.invoke()
            webViewHolder.webView?.let { webView ->
                webView.stopLoading()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
            webViewHolder.webView = null
        }
    }
}

private class WebViewHolder {
    var webView: WebView? = null
}
