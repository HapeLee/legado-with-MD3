package io.legado.app.utils.canvasrecorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import io.legado.app.utils.canvasrecorder.pools.CanvasPool
import android.util.LruCache

class CanvasRecorderImpl : BaseCanvasRecorder() {

    var bitmap: Bitmap? = null
    var canvas: Canvas? = null

    override val width get() = bitmap?.width ?: -1
    override val height get() = bitmap?.height ?: -1

    private fun init(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        if (bitmap == null) {
            bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
        }
        if (bitmap!!.width != width || bitmap!!.height != height) {
            if (bitmap!!.isMutable && canReconfigure(width, height)) {
                bitmap!!.reconfigure(width, height, Bitmap.Config.ARGB_8888)
            } else {
                bitmapPool.put(bitmap!!)
                bitmap = bitmapPool.get(width, height, Bitmap.Config.ARGB_8888)
            }
        }
    }

    private fun canReconfigure(width: Int, height: Int): Boolean {
        return bitmap!!.allocationByteCount >= width * height * 4
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        init(width, height)
        bitmap?.eraseColor(Color.TRANSPARENT)
        canvas = canvasPool.obtain().apply { setBitmap(bitmap) }
        return canvas!!
    }

    override fun endRecording() {
        bitmap?.prepareToDraw()
        super.endRecording()
        canvasPool.recycle(canvas!!)
        canvas = null
    }

    override fun draw(canvas: Canvas) {
        val b = bitmap ?: return
        if (!b.isRecycled) {
            canvas.drawBitmap(b, 0f, 0f, null)
        }
    }

    override fun recycle() {
        super.recycle()
        val b = bitmap ?: return
        bitmap = null
        bitmapPool.put(b)
    }

    companion object {
        private val canvasPool = CanvasPool(2)
        private val bitmapPool = SimpleBitmapPool()
    }

}

/**
 * 轻量位图池，替代 Glide 的 BitmapPool。
 * 使用 LruCache 按 (width × height × config) 键回收可复用的 Bitmap，
 * 避免 Glide 依赖。
 */
private class SimpleBitmapPool {

    private val cache = LruCache<String, Bitmap>(MAX_POOL_SIZE_BYTES)

    private fun key(width: Int, height: Int, config: Bitmap.Config): String {
        val bytesPerPixel = if (config == Bitmap.Config.ARGB_8888) 4 else 1
        return "${width}x${height}_${bytesPerPixel}"
    }

    fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val k = key(width, height, config)
        val cached = cache.remove(k)
        if (cached != null && !cached.isRecycled && cached.width == width && cached.height == height) {
            return cached
        }
        if (cached != null && !cached.isRecycled) {
            // Size mismatch — recycle the old one
            cached.recycle()
        }
        return Bitmap.createBitmap(width, height, config)
    }

    fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (bitmap.isMutable) {
            val k = key(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            cache.put(k, bitmap)
        } else {
            bitmap.recycle()
        }
    }

    companion object {
        private const val MAX_POOL_SIZE_BYTES = 16 * 1024 * 1024 // 16 MB
    }
}
