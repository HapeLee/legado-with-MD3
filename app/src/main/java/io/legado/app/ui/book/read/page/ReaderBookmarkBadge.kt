package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.caverock.androidsvg.SVG
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.SvgUtils
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 书签角标图源：把用户自定义的角标图片（PNG/JPEG/WebP/SVG 等）解码为可绘制对象并缓存。
 *
 * 角标是 10×20dp 的小图，且 `upBookmarkBadge` 位于翻页渲染热路径上，所以按
 * `path + lastModified` 单条缓存——路径或文件内容变了才重新解码，避免每页读盘。
 * 返回 null 表示未自定义（或文件失效），由调用方回退到默认黄色书签。
 */
object ReaderBookmarkBadge {

    private class Cache(val key: String, val drawable: Drawable?)

    @Volatile
    private var cache: Cache? = null

    fun drawable(context: Context): Drawable? {
        val path = ReadConfig.bookmarkBadgeImage
        if (path.isBlank()) {
            cache = null
            return null
        }
        val file = File(path)
        if (!file.isFile) {
            cache = null
            return null
        }
        // 角标为 1:2 的丝带，宽度来自 bookmarkBadgeSize（dp）。尺寸参与缓存键，
        // 调大小后同一路径也会重新解码到新分辨率。
        val density = context.resources.displayMetrics.density
        val sizeDp = ReadConfig.bookmarkBadgeSize.coerceAtLeast(1)
        val targetW = (sizeDp * density).toInt().coerceAtLeast(1)
        val targetH = (sizeDp * 2 * density).toInt().coerceAtLeast(1)
        val key = "$path|${file.lastModified()}|${targetW}x$targetH"
        cache?.takeIf { it.key == key }?.let { return it.drawable }
        val bitmap = decode(file, targetW, targetH) ?: run {
            cache = Cache(key, null)
            return null
        }
        return BitmapDrawable(context.resources, bitmap).also { cache = Cache(key, it) }
    }

    private fun decode(file: File, targetW: Int, targetH: Int): Bitmap? =
        decodeRaster(file.path, targetW, targetH) ?: decodeSvg(file, targetW, targetH)

    /** 位图解码：先读边界，按目标大小的两倍做采样，避免用户选了大图撑爆内存。 */
    private fun decodeRaster(path: String, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW * 2 &&
            bounds.outHeight / (sample * 2) >= targetH * 2
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, options)
    }

    /**
     * SVG 解码：缩放到角标实际像素尺寸（小图需要放大到目标大小才清晰，
     * [SvgUtils.createBitmap] 只缩不放，不适用）。位图解码失败时才走到这里。
     */
    private fun decodeSvg(file: File, targetW: Int, targetH: Int): Bitmap? = runCatching {
        val svg = SVG.getFromInputStream(FileInputStream(file))
        val size = SvgUtils.getSize(file.path) ?: return@runCatching null
        val scale = min(targetW / size.width.toFloat(), targetH / size.height.toFloat())
        val width = max(1, (size.width * scale).toInt())
        val height = max(1, (size.height * scale).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        svg.renderToCanvas(Canvas(bitmap))
        bitmap
    }.getOrNull()
}
