package io.legado.app.feature.reader.core.model

/** Aspect-preserving destination geometry for reader images and error placeholders. */
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
    }
}
