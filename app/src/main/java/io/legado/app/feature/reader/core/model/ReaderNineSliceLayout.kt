package io.legado.app.feature.reader.core.model

import kotlin.math.roundToInt

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
        val borderPx = if (image.hasNinePatchBorder) 1 else 0
        val sourceLeft = borderPx
        val sourceTop = borderPx
        val sourceRight = (bitmapWidth - borderPx).coerceAtLeast(sourceLeft)
        val sourceBottom = (bitmapHeight - borderPx).coerceAtLeast(sourceTop)
        val sourceWidth = sourceRight - sourceLeft
        val sourceHeight = sourceBottom - sourceTop
        val sx = intArrayOf(
            sourceLeft,
            sourceLeft + (sourceWidth * image.ninePatchLeft.coerceIn(0f, 1f)).roundToInt(),
            sourceRight - (sourceWidth * image.ninePatchRight.coerceIn(0f, 1f)).roundToInt(),
            sourceRight,
        )
        val sy = intArrayOf(
            sourceTop,
            sourceTop + (sourceHeight * image.ninePatchTop.coerceIn(0f, 1f)).roundToInt(),
            sourceBottom - (sourceHeight * image.ninePatchBottom.coerceIn(0f, 1f)).roundToInt(),
            sourceBottom,
        )
        if (sx[1] > sx[2] || sy[1] > sy[2]) return emptyList()
        // 中心格落在文字框上，八个边框格落在外扩出来的 `frame` 上（对照旧 View
        // `drawNineSliceCenter` 的中心 + `drawNineSliceFrames` 画在行框外的上下边/行框两侧的
        // 左右边）。退化情形不需要特判：上下边被「半行距」压成 0 时，上下两行的目标高度为 0，
        // 下面的循环直接跳过，只剩「中心 + 左右两条边」——正是旧 View 在无行距时的画法。
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
