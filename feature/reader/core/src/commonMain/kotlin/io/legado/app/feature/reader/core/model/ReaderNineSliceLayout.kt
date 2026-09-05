package io.legado.app.feature.reader.core.model

data class ReaderIntRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class ReaderNineSliceCell(
    val source: ReaderIntRect,
    val destination: ReaderRect,
)

object ReaderNineSliceLayout {
    fun cells(
        bitmapWidth: Int,
        bitmapHeight: Int,
        content: ReaderRect,
        frame: ReaderRect,
        image: ReaderTextBackgroundImage,
    ): List<ReaderNineSliceCell> {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()
        val sx = intArrayOf(
            0,
            (bitmapWidth * image.ninePatchLeft.coerceIn(0f, 1f)).toInt(),
            (bitmapWidth * (1f - image.ninePatchRight.coerceIn(0f, 1f))).toInt(),
            bitmapWidth,
        )
        val sy = intArrayOf(
            0,
            (bitmapHeight * image.ninePatchTop.coerceIn(0f, 1f)).toInt(),
            (bitmapHeight * (1f - image.ninePatchBottom.coerceIn(0f, 1f))).toInt(),
            bitmapHeight,
        )
        if (sx[1] > sx[2] || sy[1] > sy[2]) return emptyList()
        val dx = floatArrayOf(frame.left, content.left, content.right, frame.right)
        val dy = floatArrayOf(frame.top, content.top, content.bottom, frame.bottom)
        return buildList(9) {
            for (row in 0..2) for (column in 0..2) {
                if (sx[column] == sx[column + 1] || sy[row] == sy[row + 1]) continue
                val destination = ReaderRect(dx[column], dy[row], dx[column + 1], dy[row + 1])
                if (destination.width <= 0f || destination.height <= 0f) continue
                add(ReaderNineSliceCell(
                    ReaderIntRect(sx[column], sy[row], sx[column + 1], sy[row + 1]),
                    destination,
                ))
            }
        }
    }
}
