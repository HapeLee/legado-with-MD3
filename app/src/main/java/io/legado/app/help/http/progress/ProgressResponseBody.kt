package io.legado.app.help.http.progress

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

class ProgressResponseBody internal constructor(
    private val url: String,
    private val internalProgressListener: InternalProgressListener?,
    private val responseBody: ResponseBody,
) : ResponseBody() {
    private var bufferedSource: BufferedSource? = null

    override fun contentType(): MediaType? = responseBody.contentType()

    override fun contentLength(): Long = responseBody.contentLength()

    override fun source(): BufferedSource = bufferedSource ?: source(responseBody.source())
        .buffer()
        .also { bufferedSource = it }

    private fun source(source: Source): Source = object : ForwardingSource(source) {
        var totalBytesRead = 0L
        var lastTotalBytesRead = 0L

        @Throws(IOException::class)
        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesRead = super.read(sink, byteCount)
            totalBytesRead += if (bytesRead == -1L) 0 else bytesRead
            if (internalProgressListener != null && lastTotalBytesRead != totalBytesRead) {
                lastTotalBytesRead = totalBytesRead
                mainThreadHandler.post {
                    internalProgressListener.onProgress(url, totalBytesRead, contentLength())
                }
            }
            return bytesRead
        }
    }

    interface InternalProgressListener {
        fun onProgress(url: String, bytesRead: Long, totalBytes: Long)
    }

    private companion object {
        val mainThreadHandler = Handler(Looper.getMainLooper())
    }
}
