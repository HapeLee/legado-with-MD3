package io.legado.app.help.glide.progress

import io.legado.app.help.glide.progress.ProgressManager.addListener
import io.legado.app.help.glide.progress.ProgressManager.progress
import io.legado.app.help.glide.progress.ProgressManager.removeListener
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 进度监听器管理类
 * 加入图片加载进度监听，加入Https支持
 *
 * 两条消费路径并存：
 * 1. [progress] 全局事件流，供阅读页 ViewModel 订阅（无需注册/注销，生命周期安全）；
 * 2. [addListener] / [removeListener] 单个 URL 的回调，兼容旧的 View 侧用法。
 *
 * 只有当至少一方在监听时才分发事件；主线程投递由响应体按时间节流。
 */
object ProgressManager {
    private val listenersMap = ConcurrentHashMap<String, CopyOnWriteArrayList<OnProgressListener>>()

    private val _progress = MutableSharedFlow<DownloadProgress>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val progress: SharedFlow<DownloadProgress> = _progress.asSharedFlow()

    val LISTENER = object : ProgressResponseBody.InternalProgressListener {
        override fun onProgress(url: String, bytesRead: Long, totalBytes: Long) {
            if (!hasConsumer()) return
            // Content-Length 缺失（分块传输/服务端不返回）时不计算百分比，
            // 也不判完成，否则旧实现会把 percentage 算成负数并立刻注销监听。
            if (totalBytes <= 0L) return
            val key = getUrlNoOption(url)
            val listeners = listenersMap[key]
            val percentage = (bytesRead * 100f / totalBytes).toInt().coerceIn(0, 100)
            val isComplete = bytesRead >= totalBytes
            listeners?.forEach { it.invoke(isComplete, percentage, bytesRead, totalBytes) }
            _progress.tryEmit(
                DownloadProgress(
                    // Flow 按完整请求身份匹配页面，不能丢掉书源 header 等选项。
                    // 只有旧 View 监听器使用上面的规范化 key。
                    url = url,
                    percentage = percentage,
                    bytesRead = bytesRead,
                    totalBytes = totalBytes,
                    isComplete = isComplete,
                )
            )
            if (isComplete) {
                removeListener(url)
            }
        }
    }

    private fun hasConsumer(): Boolean =
        _progress.subscriptionCount.value > 0 || listenersMap.isNotEmpty()

    fun addListener(url: String, listener: OnProgressListener) {
        if (url.isNotEmpty()) {
            val url = getUrlNoOption(url)
            listenersMap.getOrPut(url) { CopyOnWriteArrayList() }.apply {
                remove(listener)
                add(listener)
            }
            listener.invoke(false, 1, 0, 0)
        }
    }

    fun removeListener(url: String) {
        if (url.isNotEmpty()) {
            val url = getUrlNoOption(url)
            listenersMap.remove(url)
        }
    }

    fun removeListener(url: String, listener: OnProgressListener) {
        if (url.isEmpty()) return
        val key = getUrlNoOption(url)
        val listeners = listenersMap[key] ?: return
        listeners.remove(listener)
        if (listeners.isEmpty()) listenersMap.remove(key, listeners)
    }

    fun getProgressListener(url: String): OnProgressListener? {
        return if (url.isEmpty() || listenersMap.isEmpty()) {
            null
        } else {
            listenersMap[getUrlNoOption(url)]?.firstOrNull()
        }
    }

    private fun getUrlNoOption(url: String): String {
        val urlMatch = AnalyzeUrl.paramPattern.find(url)
        return if (urlMatch != null) {
            url.take(urlMatch.range.first)
        } else {
            url
        }
    }

}
