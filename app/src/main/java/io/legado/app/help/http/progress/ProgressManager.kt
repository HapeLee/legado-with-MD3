package io.legado.app.help.http.progress

import io.legado.app.model.analyzeRule.AnalyzeUrl
import java.util.concurrent.ConcurrentHashMap

/** Tracks OkHttp response progress independently of any image-loading library. */
object ProgressManager {
    private val listenersMap = ConcurrentHashMap<String, OnProgressListener>()

    val LISTENER = object : ProgressResponseBody.InternalProgressListener {
        override fun onProgress(url: String, bytesRead: Long, totalBytes: Long) {
            getProgressListener(url)?.let { listener ->
                var percentage = (bytesRead * 1f / totalBytes * 100f).toInt()
                var isComplete = percentage >= 100
                if (percentage <= -100) {
                    percentage = 0
                    isComplete = true
                }
                listener(isComplete, percentage, bytesRead, totalBytes)
                if (isComplete) removeListener(url)
            }
        }
    }

    fun addListener(url: String, listener: OnProgressListener) {
        if (url.isNotEmpty()) {
            listenersMap[getUrlNoOption(url)] = listener
            listener(false, 1, 0, 0)
        }
    }

    fun removeListener(url: String) {
        if (url.isNotEmpty()) listenersMap.remove(getUrlNoOption(url))
    }

    fun getProgressListener(url: String): OnProgressListener? =
        if (url.isEmpty() || listenersMap.isEmpty()) null else listenersMap[url]

    private fun getUrlNoOption(url: String): String =
        AnalyzeUrl.paramPattern.find(url)?.let { url.take(it.range.first) } ?: url
}
