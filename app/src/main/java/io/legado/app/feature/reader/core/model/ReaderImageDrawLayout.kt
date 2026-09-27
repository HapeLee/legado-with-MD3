package io.legado.app.feature.reader.core.model

import io.legado.app.feature.reader.core.model.ReaderImageDrawLayout.Companion.fitCenter
import io.legado.app.feature.reader.core.model.ReaderImageDrawLayout.Companion.inlineCell


/**
 * 阅读器图片与错误占位图的绘制几何。整图（standalone）按比例内接居中（[fitCenter]）；
 * 文字嵌入的行内图按旧 `ImageColumn` 占满一个字符格宽、高随实际位图长宽比（[inlineCell]）。
 */
data class ReaderImageDrawLayout(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
) {
    companion object {
        fun fitCenter(
            container: ReaderRect,
            imageWidthPx: Int,
            imageHeightPx: Int,
        ): ReaderImageDrawLayout? {
            if (
                container.width <= 0f || container.height <= 0f ||
                imageWidthPx <= 0 || imageHeightPx <= 0
            ) return null
            val scale = minOf(
                container.width / imageWidthPx,
                container.height / imageHeightPx,
            )
            val width = imageWidthPx * scale
            val height = imageHeightPx * scale
            return ReaderImageDrawLayout(
                leftPx = container.left + (container.width - width) / 2f,
                topPx = container.top + (container.height - height) / 2f,
                widthPx = width,
                heightPx = height,
            )
        }

        /**
         * 文字嵌入的行内图几何，对照旧 View `ImageColumn.draw`：
         *
         * ```kotlin
         * val h = (end - start) / bitmap.width * bitmap.height // 宽 = 一个字符格
         * val div = (height - h) / 2                         // height = 行盒高
         * RectF(start, div, end, height - div)               // div < 0 时允许高于当前行
         * ```
         *
         * 宽恒等于 [container]（即一个字符格），高按**实际加载到的位图**长宽比换算，`div` 为负
         * 时向上下对称溢出。绝不内接留白——旧实现里图片永远精确占满一个字符格宽，用
         * [fitCenter] 代替会让它在测量期长宽比与位图不一致时被画小。
         */
        fun inlineCell(
            container: ReaderRect,
            imageWidthPx: Int,
            imageHeightPx: Int,
        ): ReaderImageDrawLayout? {
            if (container.width <= 0f || imageWidthPx <= 0 || imageHeightPx <= 0) return null
            val height = container.width * imageHeightPx / imageWidthPx
            return ReaderImageDrawLayout(
                leftPx = container.left,
                topPx = container.top + (container.height - height) / 2f,
                widthPx = container.width,
                heightPx = height,
            )
        }

        /** 行内图走旧 `ImageColumn` 的字符格几何；整图保持按比例内接居中。 */
        fun forElement(
            element: ReaderElement.Image,
            imageWidthPx: Int,
            imageHeightPx: Int,
        ): ReaderImageDrawLayout? = if (element.inline) {
            inlineCell(element.bounds, imageWidthPx, imageHeightPx)
        } else {
            fitCenter(element.bounds, imageWidthPx, imageHeightPx)
        }
    }
}
