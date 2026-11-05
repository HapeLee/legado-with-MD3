package io.legado.app.help.http

import android.webkit.WebView

/**
 * WebView 渲染进程被系统回收（OOM、渲染崩溃）时的统一收尾。
 *
 * 不覆盖 `WebViewClient.onRenderProcessGone` 时，系统默认把宿主进程一起杀掉；覆盖并返回
 * true 才表示事件已消费、应用可以继续运行。
 *
 * 这里刻意不调用 `destroy()`：UI 侧的实例归 `WebViewContainerCompose` 与各页面在销毁时
 * 释放，提前销毁会让随后 `update`／导航触发的 `loadUrl` 打在已销毁的实例上。渲染进程已经
 * 不在了，这块区域会保持空白，用户退出重进即可恢复。
 */
internal fun WebView.handleRenderProcessGone(): Boolean {
    stopLoading()
    return true
}
