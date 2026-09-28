package io.legado.app.help.coil

import android.graphics.Bitmap
import coil3.disk.DiskCache
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import okio.Path.Companion.toOkioPath
import splitties.init.appCtx
import java.io.File

/** 条漫的可再生成、无损预览缓存，不参与下载保留或分页区域解码。 */
internal object MangaPreviewCache {
    private val cache by lazy {
        DiskCache.Builder().directory(File(appCtx.cacheDir, "manga_previews").toOkioPath())
            .maxSizeBytes(256L * 1024 * 1024).build()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val encoding = Semaphore(1)

    fun open(key: String): DiskCache.Snapshot? = try {
        cache.openSnapshot(key)
    } catch (_: Exception) {
        null
    }

    fun remove(key: String) {
        try {
            cache.remove(key)
        } catch (_: Exception) {
        }
    }

    suspend fun key(memoryKey: String, request: ImageRequest): String =
        "manga-preview-v1:$memoryKey:${request.sizeResolver.size()}:${request.scale}:" +
                request.transformations.joinToString { it.cacheKey }

    // 仅后台编码限为一个，忙时跳过可再生成的缓存；不限制网络或可见页解码，不排队持有大量长图。
    fun save(key: String, bitmap: Bitmap) {
        if (!encoding.tryAcquire()) return
        scope.launch {
            try {
                cache.openSnapshot(key)?.use { return@launch }
                if (bitmap.isRecycled) return@launch
                val editor = cache.openEditor(key) ?: return@launch
                try {
                    editor.data.toFile().outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    editor.metadata.toFile().writeText("manga-preview-v1")
                    editor.commit()
                } catch (_: Exception) {
                    editor.abort()
                }
            } catch (_: Exception) {
                // 预览可重建；磁盘不足或缓存目录被清理不影响原图展示。
            } finally {
                encoding.release()
            }
        }
    }
}
