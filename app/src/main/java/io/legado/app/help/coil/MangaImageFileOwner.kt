package io.legado.app.help.coil

import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import io.legado.app.constant.AppLog
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** 一个渲染请求的文件所有权。仅管理平台资源生命周期，不持有 UI 或阅读会话。 */
@Stable
class MangaImageFileOwner : Closeable, RememberObserver {
    private val regionFailure = MutableStateFlow(false)
    val regionDecodeFailed = regionFailure.asStateFlow()

    @Synchronized
    fun onRegionDecodeFailed(error: Exception) {
        if (!closed && regionFailure.compareAndSet(false, true)) {
            AppLog.put("漫画区域解码失败，回退整图", error)
        }
    }

    private var closed = false
    private var file: File? = null
    private var lease: Closeable? = null
    private val acquisition = Mutex()
    private var requestData: String? = null
    private var fileLength = 0L
    private var fileModified = 0L
    @Volatile
    private var originalRatio: Float? = null
    internal fun aspectRatio(): Float? = originalRatio
    internal fun setAspectRatio(ratio: Float?) {
        originalRatio = ratio
    }

    /** 同一显示请求的预览、背景取色与回退共享已校验的文件，避免反复查库和获取。 */
    internal suspend fun acquire(data: String, fetch: suspend () -> Pair<File, Closeable>): File =
        acquisition.withLock {
            val cached = synchronized(this) {
                if (closed) throw CancellationException("Image request disposed")
                file?.takeIf {
                    requestData == data && it.isFile && it.length() == fileLength &&
                            it.lastModified() == fileModified
                }
            }
            cached ?: fetch().let { (file, lease) ->
                attach(file, lease)
                synchronized(this) { requestData = data }
                file
            }
        }

    @Synchronized
    fun attach(file: File, lease: Closeable) {
        if (closed) {
            lease.close()
            throw CancellationException("Image request disposed")
        }
        if (this.file != null && this.file != file) {
            lease.close()
            error("Image request changed its original file")
        }
        if (this.lease != null) lease.close()
        else {
            this.file = file
            this.lease = lease
        }
        fileLength = file.length()
        fileModified = file.lastModified()
    }

    /** 瓦片具有独立租约；释放 Compose 请求不提前释放解码器正在使用的文件。 */
    @Synchronized
    fun borrowForTiles(): Pair<File, Closeable>? =
        file?.takeUnless { closed }?.let { it to BookHelp.pinImageFile(it) }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        lease?.close()
        lease = null
    }

    override fun onRemembered() = Unit
    override fun onForgotten() = close()
    override fun onAbandoned() = close()
}
