package io.legado.app.ui.widget.components.privacy

import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import coil3.transform.Transformation

/**
 * 私密内容的封面脱敏。
 *
 * 为什么不用 Modifier.blur：项目 minSdk 26，而 androidx.compose.ui.draw.blur 只在
 * API 31+ 生效，26~30 上是一个 no-op——单独依赖它等于在低版本设备上直接泄漏真实封面。
 *
 * 这里走 Coil Transformation：先强降采样（顺带让模糊更彻底、更便宜），再做两轮
 * 可分离 box blur，最后放大回原尺寸。全 API 行为一致，且模糊结果只存在于内存。
 */
class PrivateCoverBlurTransformation : Transformation() {

    override val cacheKey: String = "private-cover-blur-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width <= 0 || height <= 0) return input

        val smallWidth = (width / DOWNSCALE_FACTOR).coerceAtLeast(MIN_DOWNSCALED_EDGE)
        val smallHeight = (height / DOWNSCALE_FACTOR).coerceAtLeast(MIN_DOWNSCALED_EDGE)
        val small = Bitmap.createScaledBitmap(input, smallWidth, smallHeight, true)

        var blurred = small
        repeat(BLUR_PASSES) {
            blurred = boxBlur(blurred, BLUR_RADIUS)
        }
        if (blurred === small) return input

        return Bitmap.createScaledBitmap(blurred, width, height, true)
    }

    companion object {
        private const val DOWNSCALE_FACTOR = 6
        private const val MIN_DOWNSCALED_EDGE = 6
        private const val BLUR_RADIUS = 3
        private const val BLUR_PASSES = 2
        private const val CHANNEL_MAX = 255

        /** 可分离 box blur：先横向再纵向，窗口越界按边缘像素钳制 */
        private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
            val width = source.width
            val height = source.height
            if (width <= 0 || height <= 0) return source

            val sourcePixels = IntArray(width * height)
            source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
            val horizontal = IntArray(width * height)
            val vertical = IntArray(width * height)
            val window = radius * 2 + 1

            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    var alpha = 0
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (dx in -radius..radius) {
                        val sx = (x + dx).coerceIn(0, width - 1)
                        val pixel = sourcePixels[rowOffset + sx]
                        alpha += Color.alpha(pixel)
                        red += Color.red(pixel)
                        green += Color.green(pixel)
                        blue += Color.blue(pixel)
                    }
                    horizontal[rowOffset + x] = argb(
                        alpha / window, red / window, green / window, blue / window
                    )
                }
            }

            for (x in 0 until width) {
                for (y in 0 until height) {
                    var alpha = 0
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (dy in -radius..radius) {
                        val sy = (y + dy).coerceIn(0, height - 1)
                        val pixel = horizontal[sy * width + x]
                        alpha += Color.alpha(pixel)
                        red += Color.red(pixel)
                        green += Color.green(pixel)
                        blue += Color.blue(pixel)
                    }
                    vertical[y * width + x] = argb(
                        alpha / window, red / window, green / window, blue / window
                    )
                }
            }

            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            output.setPixels(vertical, 0, width, 0, 0, width, height)
            return output
        }

        private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = Color.argb(
            alpha.coerceIn(0, CHANNEL_MAX),
            red.coerceIn(0, CHANNEL_MAX),
            green.coerceIn(0, CHANNEL_MAX),
            blue.coerceIn(0, CHANNEL_MAX)
        )
    }
}
