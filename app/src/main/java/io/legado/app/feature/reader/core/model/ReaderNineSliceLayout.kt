package io.legado.app.feature.reader.core.model

import kotlin.math.roundToInt

data class ReaderIntRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class ReaderNineSliceCell(
    val source: ReaderIntRect,
    val destination: ReaderRect,
    /**
     * 真正画出去的那一块：[destination] 的四条边里，只跟相邻格共享的那两条各让出半像素，
     * 外框那两条保持原位。相邻格因此在拼缝上互相叠压，谁也不会留下一条半透明的切线。
     */
    val painted: ReaderRect,
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
        // 纵向一条边都不拉伸：整张图按 scale 原样高，`frame` 的上下边就是图自己的上下边
        // （由 [ReaderTextBackgroundImage.centerBandPx] 定死），所以中间那一行的目标高度恒等于
        // 源里那条带的高度。上下两条切分线因此只有一件事可做——把字框在图的哪一段里，
        // 拖它们就是上下对齐。
        //
        // 横向只有左右两条线之间那一格被拉到文字宽度（再各加左/右偏移），四周一圈按原图宽度画，
        // 四角原样。偏移可以为负（气泡比字短），但不许把中间那一格挤成反向：夹紧
        // （[ReaderTextBackgroundImage.stretchLeftPx] / [stretchRightPx]，与外框同一份口径）。
        val textWidthPx = content.right - content.left
        val dx = floatArrayOf(
            frame.left,
            content.left - image.stretchLeftPx(textWidthPx),
            content.right + image.stretchRightPx(textWidthPx),
            frame.right,
        )
        val dy = floatArrayOf(
            frame.top,
            frame.top + image.contentInsetTopPx,
            frame.bottom - image.contentInsetBottomPx,
            frame.bottom,
        )
        if (dx[1] > dx[2] || dy[1] > dy[2]) return emptyList()
        // 中心格落在「文字宽 + 长度偏移」上，八个边框格落在外扩出来的 `frame` 上（对照旧 View
        // `drawNineSliceCenter` 的中心 + `drawNineSliceFrames` 画在行框外的上下边/行框两侧的
        // 左右边）。退化情形不需要特判：中间那条带高度为 0 时，上下两行的目标高度为 0，
        // 下面的循环直接跳过，只剩「中心 + 左右两条边」。
        return buildList(9) {
            for (row in 0..2) for (column in 0..2) {
                if (sx[column] == sx[column + 1] || sy[row] == sy[row + 1]) continue
                val destination = ReaderRect(dx[column], dy[row], dx[column + 1], dy[row + 1])
                if (destination.width <= 0f || destination.height <= 0f) continue
                add(ReaderNineSliceCell(
                    source = ReaderIntRect(sx[column], sy[row], sx[column + 1], sy[row + 1]),
                    destination = destination,
                    painted = ReaderRect(
                        left = if (dx[column] > dx[0]) dx[column] - seamOverlapPx else dx[column],
                        top = if (dy[row] > dy[0]) dy[row] - seamOverlapPx else dy[row],
                        right = if (dx[column + 1] < dx[3]) dx[column + 1] + seamOverlapPx
                        else dx[column + 1],
                        bottom = if (dy[row + 1] < dy[3]) dy[row + 1] + seamOverlapPx
                        else dy[row + 1],
                    ),
                ))
            }
        }
    }

    /**
     * 切片只是恰好贴合时，两侧各自抗锯齿会在拼缝上留下两条半覆盖的边：source-over 不是相加，
     * 两条半覆盖合不成满覆盖，底下的页面背景就从缝里透出来，气泡上是一道笔直「切割线」。
     * 内部边界各向外让半像素，相邻格互相叠压，缝永远被完整盖住（对照参考实现
     * `TextLine.drawNineSlice` 的 `seamOverlap`）。外框边缘保持原位不动。
     */
    private const val seamOverlapPx = 0.5f
}
