package io.legado.app.feature.reader.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import splitties.init.appCtx
import java.io.File
import java.io.InputStream

/** Android resource boundary shared by background measurement and Canvas drawing. */
object ReaderTextBackgroundLoader {
    data class NineSliceFractions(
        val left: Float,
        val right: Float,
        val top: Float,
        val bottom: Float,
    )
    /**
     * 气泡图缓存的上限：堆的八分之一（Android 做图片缓存的常规口径），最少 16 MB。
     *
     * 下限必须容得下最大的一张气泡图：解出来的气泡图能到整屏大小（`sampleSizeFor` 就按
     * 屏幕尺寸降采样），一张 1440×3200 的 ARGB_8888 接近 18 MB；上限小于一张图的尺寸时
     * 条目一进缓存就被挤出去，每页每帧都是未命中，翻页首帧气泡是空的、下一帧才补上。
     */
    private val bitmaps = object : LruCache<String, Bitmap>(
        maxOf(16 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 8).toInt()),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /**
     * 这张图**真正解出来**有多大（与 [load] 给绘制侧的那张位图一致）。
     *
     * 九宫格的两条切分线、锁定高度都是按这个尺寸换算出来的，报成文件原尺寸就会和位图对不上：
     * 大图降采样过一半时，四角按半尺寸画、中间那条带却按全尺寸留高，气泡接缝就错开了。
     */
    fun dimensions(source: String): Pair<Int, Int> {
        val (width, height) = fileDimensions(source)
        if (width <= 0 || height <= 0) return 0 to 0
        val sample = sampleSizeFor(source, width, height)
        return (width / sample) to (height / sample)
    }

    private fun fileDimensions(source: String): Pair<Int, Int> = runCatching {
        open(source)?.use { input ->
            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeStream(input, null, this)
                outWidth.coerceAtLeast(0) to outHeight.coerceAtLeast(0)
            }
        }
    }.getOrNull() ?: (0 to 0)

    fun load(source: String): Bitmap? {
        if (source.isBlank()) return null
        val key = cacheKey(source)
        cached(source)?.let { return it }
        val (width, height) = fileDimensions(source)
        if (width <= 0 || height <= 0) return null
        val sampleSize = sampleSizeFor(source, width, height)
        return runCatching {
            open(source)?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                )
            }
        }.getOrNull()?.takeUnless(Bitmap::isRecycled)?.also { bitmaps.put(key, it) }
    }

    fun cached(source: String): Bitmap? = source.takeIf(String::isNotBlank)
        ?.let(::cacheKey)
        ?.let(bitmaps::get)
        ?.takeUnless(Bitmap::isRecycled)

    /** Reads the first stretch run from a raw .9.png guide border. */
    fun nineSliceFractions(source: String): NineSliceFractions? {
        if (!isRawNinePatch(source)) return null
        val bitmap = load(source) ?: return null
        if (bitmap.width < 3 || bitmap.height < 3) return null
        fun marked(color: Int): Boolean = android.graphics.Color.alpha(color) > 0 &&
                android.graphics.Color.red(color) < 32 &&
                android.graphics.Color.green(color) < 32 &&
                android.graphics.Color.blue(color) < 32

        fun run(length: Int, colorAt: (Int) -> Int): IntRange? {
            val start = (1 until length - 1).firstOrNull { marked(colorAt(it)) } ?: return null
            val end = (start until length - 1).takeWhile { marked(colorAt(it)) }.last()
            return start..end
        }

        val horizontal = run(bitmap.width) { bitmap.getPixel(it, 0) } ?: return null
        val vertical = run(bitmap.height) { bitmap.getPixel(0, it) } ?: return null
        val width = (bitmap.width - 2).toFloat()
        val height = (bitmap.height - 2).toFloat()
        return NineSliceFractions(
            left = (horizontal.first - 1) / width,
            right = (bitmap.width - 2 - horizontal.last) / width,
            top = (vertical.first - 1) / height,
            bottom = (bitmap.height - 2 - vertical.last) / height,
        )
    }

    internal fun assetCandidates(source: String): List<String> = when {
        source.startsWith("assets://") -> listOf(source.removePrefix("assets://"))
        source.startsWith("content://") || File(source).exists() -> emptyList()
        source.startsWith("bg/") -> listOf(source)
        else -> listOf("bg/$source")
    }

    private fun open(source: String): InputStream? {
        if (source.isBlank()) return null
        return when {
            source.startsWith("assets://") -> appCtx.assets.open(source.removePrefix("assets://"))
            source.startsWith("content://") -> appCtx.contentResolver.openInputStream(Uri.parse(source))
            File(source).exists() -> File(source).inputStream()
            else -> assetCandidates(source).firstNotNullOfOrNull { asset ->
                runCatching { appCtx.assets.open(asset) }.getOrNull()
            }
        }
    }

    private fun cacheKey(source: String): String {
        val file = File(source)
        return if (file.isFile) "$source:${file.length()}:${file.lastModified()}" else source
    }

    private fun isRawNinePatch(source: String): Boolean =
        source.substringBefore('?').substringBefore('#').endsWith(".9.png", ignoreCase = true)

    /** `.9.png` 的引导边只有一像素，降采样会把切线吃掉，所以它一律原尺寸解。 */
    private fun sampleSizeFor(source: String, width: Int, height: Int): Int =
        if (isRawNinePatch(source)) 1 else calculateInSampleSize(
            width = width,
            height = height,
            requestedWidth = appCtx.resources.displayMetrics.widthPixels,
            requestedHeight = appCtx.resources.displayMetrics.heightPixels,
        )

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        var sample = 1
        if (height > requestedHeight || width > requestedWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sample >= requestedHeight && halfWidth / sample >= requestedWidth) sample *= 2
        }
        return sample
    }
}
