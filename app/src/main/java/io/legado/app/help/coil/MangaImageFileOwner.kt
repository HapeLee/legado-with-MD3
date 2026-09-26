package io.legado.app.help.coil

import androidx.compose.runtime.Stable
import androidx.compose.runtime.RememberObserver
import io.legado.app.help.book.BookHelp
import java.io.Closeable
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** 一个渲染请求的文件所有权。仅管理平台资源生命周期，不持有 UI 或阅读会话。 */
@Stable
class MangaImageFileOwner : Closeable, RememberObserver {
    private var closed = false
    private var file: File? = null
    private var lease: Closeable? = null

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
