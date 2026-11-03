package io.legado.app.feature.reader.core.model

object ReaderImageCachePolicy {
    fun key(element: ReaderElement.Image): String =
        "${element.source}|${element.bounds.width.toInt()}x${element.bounds.height.toInt()}"

    fun belongsToSource(key: String, source: String): Boolean = key.startsWith("$source|")

    fun withGeneration(key: String, generation: Long): String = "$key|generation=$generation"

    /**
     * 单张图解码后的字节**上界**估计，按显示像素算。
     *
     * `BitmapFactory` 的 `inSampleSize` 只能取 2 的幂，请求的宽高只是"不小于显示尺寸"，
     * 最坏会按显示尺寸的 2 倍边长（4 倍面积）解码；`ARGB_8888` 又是 4 字节/像素。
     */
    fun estimatedDecodedBytes(bounds: ReaderRect): Long =
        bounds.width.coerceAtLeast(0f).toLong() *
                bounds.height.coerceAtLeast(0f).toLong() *
                UPPER_BYTES_PER_DISPLAY_PIXEL

    /**
     * 图片缓存的目标容量。
     *
     * 滚动模式一次绘制记录会画完窗口内四页，任何一张图被淘汰就只能画回 18% 灰占位，
     * 所以容量至少要覆盖窗口内所有图片；上限由调用方按堆预算给出。
     */
    fun targetCapacityBytes(
        windowImageBounds: List<ReaderRect>,
        baseBytes: Int,
        limitBytes: Int,
    ): Int {
        val ceiling = limitBytes.coerceAtLeast(baseBytes).toLong()
        if (windowImageBounds.isEmpty()) return baseBytes
        val needed = windowImageBounds.sumOf(::estimatedDecodedBytes)
        return needed.coerceIn(baseBytes.toLong(), ceiling).toInt()
    }

    /** 4 字节/像素 × 最坏 4 倍面积（见 [estimatedDecodedBytes]）。 */
    private const val UPPER_BYTES_PER_DISPLAY_PIXEL = 16L
}
