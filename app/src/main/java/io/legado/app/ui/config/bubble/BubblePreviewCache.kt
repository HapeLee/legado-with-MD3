package io.legado.app.ui.config.bubble

import android.graphics.Bitmap
import android.util.LruCache
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.PackageSvgResourceResolver
import io.legado.app.utils.SvgUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 气泡包预览位图缓存。
 *
 * key 里带上模板内容哈希、资源哈希、配色与像素边长：编辑页每次改一个字符都要重画，
 * 但只要这四项没变就必然命中。
 */
internal class BubblePreviewCache(
    maxBytes: Int = DEFAULT_MAX_BYTES
) {
    private data class Key(
        val dirName: String,
        val updatedAt: Long,
        val svgHash: Int,
        val svgLength: Int,
        val resourcesHash: Int,
        val color: String,
        val sidePx: Int
    )

    private val renderMutex = Mutex()
    private val cache = object : LruCache<Key, Bitmap>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun load(
        entry: BubblePackageManager.Entry,
        sidePx: Int,
        isNightTheme: Boolean
    ): Bitmap? {
        val config = entry.config
        val color = if (isNightTheme) {
            config.nightNormalColor
        } else {
            config.dayNormalColor
        }?.takeIf { it.isNotBlank() } ?: BubblePackageManager.DEFAULT_NORMAL_COLOR
        return load(
            dirName = entry.dirName,
            config = config,
            root = entry.localDir,
            sidePx = sidePx,
            color = color
        )
    }

    suspend fun load(
        dirName: String,
        config: BubblePackageManager.Config,
        root: File?,
        sidePx: Int,
        color: String
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (sidePx <= 0) return@withContext null
        val key = Key(
            dirName = dirName,
            updatedAt = config.updatedAt,
            svgHash = config.svgTemplate.hashCode(),
            svgLength = config.svgTemplate.length,
            resourcesHash = config.resources.hashCode(),
            color = color,
            sidePx = sidePx
        )
        cache.get(key) ?: renderMutex.withLock {
            cache.get(key) ?: render(config, root, color, sidePx)?.also {
                cache.put(key, it)
            }
        }
    }

    private fun render(
        config: BubblePackageManager.Config,
        root: File?,
        color: String,
        sidePx: Int
    ): Bitmap? {
        val svg = config.svgTemplate
            .replace("\${color}", color)
            .replace("\${displayColor}", color)
            .replace("\${num}", "12")
            .replace("\${displayText}", "12")
        return ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)).use { input ->
            if (root == null) {
                SvgUtils.createBitmap(input, sidePx, sidePx)
            } else {
                SvgUtils.createBitmap(
                    input,
                    sidePx,
                    sidePx,
                    PackageSvgResourceResolver(root, config.resources, sidePx, sidePx)
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 8 * 1024 * 1024
    }
}
