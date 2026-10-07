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
        // 左右（上下）两条切分线各自可以拉到 100%，两条在源里交叉时不能返回空列表——
        // 气泡整块消失就是「线一过中间图就没了」。图案不在正中间的图恰恰需要这个：
        // 把拉伸带整个推到一侧，另一侧那条切片按原样画满，图案才不会被切断。
        // 交叉只说明两条切片把源挤到了一起，给中间那一格至少留一个像素可拉即可——
        // 零宽的源什么也拉不出来（那一格被跳过），字底下会留一个洞。
        sx[2] = sx[2].coerceAtLeast((sx[1] + 1).coerceAtMost(sourceRight))
        sy[2] = sy[2].coerceAtLeast((sy[1] + 1).coerceAtMost(sourceBottom))
        // 纵向：任何一格都不拉伸。上下两条切线之间就是文字的显示区域，中间那一行盖住的是
        // 「行盒 × 图片大小」这一截（图片大小 1 倍时正好是行盒），上下两条边各占剩下的——
        // 四条边都是分页期按同一个等比倍率换算出来的（[ReaderTextBackgroundImage.frameTopPx]），
        // 所以每一格的 目标/源 倍率完全一致：整张图只是等比变大小，字号大了气泡跟着长高，
        // 绝不会出现「中间拉长、上下不动」或者「尺寸不变地对着字上下挪」。
        //
        // 这条一致性靠的是**两处读同一份切线**：[withBitmapSize] 把夹过的上下切线写回
        // [ReaderTextBackgroundImage.ninePatchTop] / [ninePatchBottom]，倍率与这里的 sy 都从它
        // 换算。哪一处另算一遍，中间那一格就会与上下两条边用不同的倍率——那正是「纵向还是会拉伸」。
        //
        // 横向只有左右两条线之间那一格被拉到文字宽度（再各加左/右偏移），四周一圈按**与纵向同一个
        // 等比倍率**换算出来的宽度画（[ReaderTextBackgroundImage.frameLeftPx]）——四边若仍按原图像素
        // 宽画，图就只缩了纵向，右边那块图案会被压扁。偏移可以为负（气泡比字短），但不许把中间那一格
        // 挤成反向：夹紧（[ReaderTextBackgroundImage.stretchLeftPx] / [stretchRightPx]，与外框同一份口径）。
        val textWidthPx = content.right - content.left
        // 中间那一行的上下界是**带子**的两条边，不是文字框：图片大小 ≠ 1 时整张图（含带子）
        // 按那一个倍率放大/缩小，多出来的一截上下对称分给带子，字留在带子正中。
        // 外框与这里读的是同一份 [bandOverhangPx]，所以三行的 目标/源 倍率仍然完全相同。
        val overhangPx = image.bandOverhangPx(content.height)
        val dx = floatArrayOf(
            frame.left,
            content.left - image.stretchLeftPx(textWidthPx),
            content.right + image.stretchRightPx(textWidthPx),
            frame.right,
        )
        val dy = floatArrayOf(
            frame.top,
            content.top - overhangPx,
            content.bottom + overhangPx,
            frame.bottom,
        )
        // 目标坐标本来不会交叉（`stretchLeftPx/stretchRightPx` 已把两侧各夹到 -文字宽/2，
        // 中间那一格最窄归零），这里同样只夹平、不清空，理由与上面一致。
        if (dx[1] > dx[2]) dx[2] = dx[1]
        if (dy[1] > dy[2]) dy[2] = dy[1]
        // 中心格落在「文字宽 + 长度偏移」上，八个边框格落在外扩出来的 `frame` 上（对照旧 View
        // `drawNineSliceCenter` 的中心 + `drawNineSliceFrames` 画在行框外的上下边/行框两侧的
        // 左右边）。退化情形不需要特判：某条边厚度为 0 时它在源里也是 0，下面的循环直接跳过。
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
