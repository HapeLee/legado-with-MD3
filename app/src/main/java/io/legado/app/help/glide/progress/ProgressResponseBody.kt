package io.legado.app.help.glide.progress

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

class ProgressResponseBody internal constructor(private val url: String, private val internalProgressListener: InternalProgressListener?, private val responseBody: ResponseBody) : ResponseBody() {
    private var bufferedSource: BufferedSource? = null
    override fun contentType(): MediaType? {
        return responseBody.contentType()
    }

    override fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override fun source(): BufferedSource {
        if (bufferedSource == null) {
            bufferedSource = source(responseBody.source()).buffer()
        }
        return bufferedSource!!
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead: Long = 0
            var lastTotalBytesRead: Long = 0
            var lastEmitAt: Long = 0

            @Throws(IOException::class)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                totalBytesRead += if (bytesRead == -1L) 0 else bytesRead
                if (internalProgressListener != null && lastTotalBytesRead != totalBytesRead) {
                    // 每 8KB 一次主线程 post，在“预取若干页 + 高画质大图”时会把主线程消息队列
                    // 打满，直接表现为阅读页滑动卡顿。这里按时间节流，收尾时补发一次终值。
                    val now = android.os.SystemClock.uptimeMillis()
                    val isFinished = bytesRead == -1L
                    if (isFinished || now - lastEmitAt >= EMIT_INTERVAL_MS) {
                        lastEmitAt = now
                        lastTotalBytesRead = totalBytesRead
                        mainThreadHandler.post {
                            internalProgressListener.onProgress(
                                url, totalBytesRead, contentLength()
                            )
                        }
                    }
                }
                return bytesRead
            }
        }
    }

    interface InternalProgressListener {
        fun onProgress(url: String, bytesRead: Long, totalBytes: Long)
    }

    companion object {
        private val mainThreadHandler = Handler(Looper.getMainLooper())
        private const val EMIT_INTERVAL_MS = 100L
    }

}