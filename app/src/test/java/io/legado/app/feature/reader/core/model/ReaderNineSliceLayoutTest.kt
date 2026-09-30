package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNineSliceLayoutTest {
    // 50×40 的图，切线 20%/30%/10%/20%，scale 1：上边条 4 / 中间带 28 / 下边条 8。
    // 纵向一律等比缩放：中间带对上行盒高，整张图就按同一个倍率变大小，谁也不被单独拉长。
    private val locked = ReaderTextBackgroundImage(
        source = "frame.png",
        fit = 3,
        scale = 1f,
        ninePatchLeft = 0.2f,
        ninePatchRight = 0.3f,
        ninePatchTop = 0.1f,
        ninePatchBottom = 0.2f,
    ).withBitmapSize(50, 40)

    /** 行盒高（= 中间带要盖住的那一截）与中间带自然高 28 之比，就是整张图的等比倍率。 */
    private val lockedBandHeightPx = 28f

    /** 分页给这一行算出的外框：上下各让出一条边按行盒高等比换算后的厚度，横向只有中间格被拉长。 */
    private fun frameOf(image: ReaderTextBackgroundImage, content: ReaderRect): ReaderRect =
        image.nineSliceFrame(
            content.copy(
                top = content.top - image.frameTopPx(content.height),
                bottom = content.bottom + image.frameBottomPx(content.height),
            ),
        )

    private fun sourceWidth(cell: ReaderNineSliceCell) = cell.source.right - cell.source.left

    private fun sourceHeight(cell: ReaderNineSliceCell) = cell.source.bottom - cell.source.top

    /** 每一格的目标高 = 源高 × 同一个等比倍率；中间那一行因此正好是行盒高。 */
    private fun expectedHeight(cell: ReaderNineSliceCell, lineHeight: Float) =
        sourceHeight(cell) * (lineHeight / lockedBandHeightPx)

    /**
     * 左右两条切分线现在都能拉到 100%（图案不在正中间的图需要把拉伸带整个推到一侧）。
     * 两条线在源里交叉时，以前这里直接 `return emptyList()`，表现为「线一过中间，整块气泡消失」；
     * 现在给中间那一格留一个像素可拉，气泡不断，字底下也不会留洞。
     */
    @Test
    fun crossingSplitLinesStillLeaveSomethingToStretch() {
        val image = ReaderTextBackgroundImage(
            source = "frame.png",
            fit = 3,
            scale = 1f,
            ninePatchLeft = 0.8f,
            ninePatchRight = 0.8f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(50, 40)
        val content = ReaderRect(10f, 20f, 40f, 50f)
        val frame = frameOf(image, content)

        val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, image)

        assertEquals(9, cells.size)
        assertTrue(cells.none { it.destination.width <= 0f || it.destination.height <= 0f })
        // 外框仍然首尾相接铺满，不会缺一条边
        assertEquals(frame.left, cells.minOf { it.destination.left }, 0.01f)
        assertEquals(frame.right, cells.maxOf { it.destination.right }, 0.01f)
        // 左切片吃掉整张图的左 80%（源宽 40），图案因此不会被从中间切断
        assertEquals(40, cells.maxOf { sourceWidth(it) })
        // 交叉之后中间那一格只剩 1 个源像素，仍被拉到整段文字宽
        val center = cells[4]
        assertEquals(1, sourceWidth(center))
        assertEquals(30f, center.destination.width, 0.01f)
    }

    @Test
    fun theWholeImageScalesToTheTextRowWithoutStretchingAnyCell() {
        // 行盒高 28 = 中间带自然高，等比倍率正好是 1：整张图就是它自己的原始尺寸。
        val content = ReaderRect(10f, 20f, 40f, 48f)
        val frame = frameOf(locked, content)

        // 纵向：图高 = 上边条 4 + 行盒 28 + 下边条 8。
        assertEquals(40f, frame.height, 0.01f)

        val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, locked)

        assertEquals(9, cells.size)
        cells.forEach { cell ->
            assertEquals(expectedHeight(cell, content.height), cell.destination.height, 0.01f)
        }
        // 中间那一格被横向拉到文字宽度，纵向正好盖住行盒；倍率和上下两条边完全一致。
        val center = cells[4]
        assertEquals(ReaderIntRect(10, 4, 35, 32), center.source)
        assertEquals(content, center.destination)
        // 左右两条边按原图厚度画，且落在文字框外侧（不压在字上）。
        assertEquals(10f, cells.first().destination.width, 0.01f)
        assertEquals(15f, cells.last().destination.width, 0.01f)
    }

    /** 等比缩放：行盒长一分，整张图（含上下两条边）按同一个倍率长一分，没有哪一格被单独拉伸。 */
    @Test
    fun theBubbleGrowsAndShrinksWithTheTextRow() {
        listOf(14f, 28f, 56f).forEach { lineHeight ->
            val content = ReaderRect(10f, 100f, 40f, 100f + lineHeight)
            val frame = frameOf(locked, content)

            // 倍率 = 行盒 / 中间带 28：整张图高 40 也跟着同一个倍率走。
            assertEquals(40f * lineHeight / lockedBandHeightPx, frame.height, 0.01f)
            val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, locked)
            cells.forEach { cell ->
                assertEquals(expectedHeight(cell, lineHeight), cell.destination.height, 0.01f)
            }
            val center = cells.single { it.source == ReaderIntRect(10, 4, 35, 32) }
            assertEquals(lineHeight, center.destination.height, 0.01f)
            assertEquals(100f, center.destination.top, 0.01f)
        }
    }

    /** 左/右偏移各自只挪自己那一端：中间格 = 文字宽 + 左 + 右，两条边原厚、跟着平移。 */
    @Test
    fun lengthOffsetWidensOnlyTheMiddleCellAndSlidesTheEdgesWithIt() {
        val content = ReaderRect(10f, 20f, 40f, 48f)
        val stretched = locked.copy(lengthOffsetLeftPx = 6f, lengthOffsetRightPx = 10f)
        val frame = frameOf(stretched, content)

        val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, stretched)

        // 30 文字宽 + 6 左偏移 + 10 右偏移 = 46，加上 10/15 两条原厚边 → 外框 71。
        assertEquals(71f, frame.width, 0.01f)
        assertEquals(40f, frame.height, 0.01f)
        assertEquals(9, cells.size)
        assertEquals(46f, cells[4].destination.width, 0.01f)
        // 上下两截带子跟着中间格一起变宽；这一行行盒等于中间带，所以倍率为 1、厚度按原图。
        assertEquals(46f, cells[1].destination.width, 0.01f)
        assertEquals(sourceHeight(cells[1]).toFloat(), cells[1].destination.height, 0.01f)
        assertEquals(10f, cells[3].destination.width, 0.01f)
        assertEquals(15f, cells[5].destination.width, 0.01f)
        // 左端只被左偏移带走（10-10-6），右端只被右偏移带走（40+15+10）。
        assertEquals(-6f, cells[3].destination.left, 0.01f)
        assertEquals(65f, cells[5].destination.right, 0.01f)
    }

    /** 只调一边就够了也不许越界：负偏移把中间格缩到零，两条边谁也不越过谁。 */
    @Test
    fun aShorterLengthOffsetStopsAtTheTextEdgesInsteadOfInvertingTheSlice() {
        val content = ReaderRect(10f, 20f, 40f, 50f)
        val shrunk = locked.copy(lengthOffsetLeftPx = -100f, lengthOffsetRightPx = -100f)
        val frame = frameOf(shrunk, content)

        val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, shrunk)

        // 偏移再负也只是把中间那一格缩到零：两条边各自保住原厚，谁也不越过谁。
        assertEquals(6, cells.size)
        cells.forEach { cell ->
            assertTrue(cell.destination.width > 0f)
            assertEquals(sourceWidth(cell).toFloat(), cell.destination.width, 0.01f)
            assertEquals(expectedHeight(cell, content.height), cell.destination.height, 0.01f)
        }
        assertEquals(10f, cells.first().destination.width, 0.01f)
        assertEquals(15f, cells.last().destination.width, 0.01f)
    }

    @Test
    fun withoutVerticalEdgesOnlyTheCenterAndTheSideCellsSurvive() {
        // 上下两条边厚度为 0（外框的上下边就是文字的上下边）时，上下两行的目标高度为 0，
        // 连同四角一起被跳过，只剩「中心 + 左右两条边」。
        val image = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 7f,
            contentInsetRightPx = 5f,
        )
        val content = ReaderRect(10f, 20f, 40f, 50f)
        val frame = ReaderRect(3f, 20f, 45f, 50f)

        val cells = ReaderNineSliceLayout.cells(10, 10, content, frame, image)

        assertEquals(3, cells.size)
        // 左右边保持原图厚度，且落在文字框外侧（不压在字上）。
        assertEquals(ReaderRect(3f, 20f, 10f, 50f), cells.first().destination)
        assertEquals(ReaderRect(40f, 20f, 45f, 50f), cells.last().destination)
        assertEquals(content, cells[1].destination)
        assertEquals(ReaderIntRect(1, 1, 9, 9), cells[1].source)
    }

    @Test
    fun rawNinePatchGuideBorderIsExcludedFromEverySourceCell() {
        val image = ReaderTextBackgroundImage(
            "frame.9.png", 3, 1f,
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(52, 42)
        val content = ReaderRect(10f, 4f, 35f, 32f)

        val cells = ReaderNineSliceLayout.cells(
            bitmapWidth = 52,
            bitmapHeight = 42,
            content = content,
            frame = frameOf(image, content),
            image = image,
        )

        assertEquals(9, cells.size)
        assertEquals(ReaderIntRect(1, 1, 11, 5), cells.first().source)
        assertEquals(ReaderIntRect(36, 33, 51, 41), cells.last().source)
    }

    @Test
    fun fixedCornersKeepOneUniformConfiguredScale() {
        val image = ReaderTextBackgroundImage(
            "frame.png", 3, 0.5f,
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(50, 40)
        val content = ReaderRect(5f, 2f, 30f, 30f)
        val frame = frameOf(image, content)

        val topLeft = ReaderNineSliceLayout.cells(50, 40, content, frame, image).first()

        // scale 0.5 之后：上边条 2、中间带 14、下边条 4。行盒 28 是中间带的两倍，于是整张图
        // 再放大两倍，每一格共用同一个倍率（0.5 × 2 = 1）：源 10×4 → 目标 5×4。
        assertEquals(10, sourceWidth(topLeft))
        assertEquals(4, sourceHeight(topLeft))
        assertEquals(5f, topLeft.destination.width, 0f)
        assertEquals(4f, topLeft.destination.height, 0f)
        // 图高 40 = 上边条 4 + 行盒 28 + 下边条 8：自然高 20 的整张图被等比放大两倍。
        assertEquals(40f, frame.height, 0.01f)
    }

    /** 拼缝：内部边界各让出半像素让相邻格叠压，外框那两条边保持原位，否则缝上会透出页面背景。 */
    @Test
    fun internalSeamsOverlapWhileTheOuterFrameEdgesStayPut() {
        val content = ReaderRect(10f, 20f, 40f, 50f)
        val cells = ReaderNineSliceLayout.cells(50, 40, content, frameOf(locked, content), locked)

        val topLeft = cells.first()
        val topCenter = cells[1]
        assertEquals(topLeft.destination.left, topLeft.painted.left, 0f)
        assertEquals(topLeft.destination.top, topLeft.painted.top, 0f)
        assertEquals(topLeft.destination.right + .5f, topLeft.painted.right, 0f)
        assertEquals(topLeft.destination.bottom + .5f, topLeft.painted.bottom, 0f)
        assertEquals(topCenter.destination.left - .5f, topCenter.painted.left, 0f)
        assertEquals(topCenter.destination.right + .5f, topCenter.painted.right, 0f)
        assertEquals(topCenter.destination.top, topCenter.painted.top, 0f)
        val bottomRight = cells.last()
        assertEquals(bottomRight.destination.right, bottomRight.painted.right, 0f)
        assertEquals(bottomRight.destination.bottom, bottomRight.painted.bottom, 0f)
        // 相邻两格在缝上真正叠压，而不是恰好贴合。
        assertTrue(topLeft.painted.right > topCenter.painted.left)
        assertTrue(topCenter.painted.bottom > cells[4].painted.top)
    }

    /**
     * 用户口径：上下两条线只决定文字的显示范围，动它们等于整张图**等比例**放大/缩小，
     * 纵向一条边都不许拉伸。所以遍历两条线的全部组合，要求每一格的 目标高/源高 都等于
     * 同一个倍率——只要有一格不是，画出来就是「纵向还是会拉伸」。
     *
     * 上一轮的实现把上限夹在「上下两条边的厚度」上，夹住的那一档里中间那一格仍然被单独拉长，
     * 这个用例正是当时漏掉的那一类。
     */
    @Test
    fun verticalRatiosStayUniformForEveryPairOfCutLines() {
        val lineHeight = 60f
        val content = ReaderRect(10f, 200f, 70f, 200f + lineHeight)
        var topStep = 0
        while (topStep <= 20) {
            var bottomStep = 0
            while (bottomStep <= 20) {
                val image = ReaderTextBackgroundImage(
                    source = "frame.png",
                    fit = 3,
                    scale = 1f,
                    ninePatchTop = topStep / 20f,
                    ninePatchBottom = bottomStep / 20f,
                ).withBitmapSize(100, 100)
                val ratio = image.verticalScalePx(lineHeight)
                val cells = ReaderNineSliceLayout.cells(100, 100, content, frameOf(image, content), image)
                assertTrue("切线 $topStep/$bottomStep 把气泡挤没了", cells.isNotEmpty())
                cells.forEach { cell ->
                    assertEquals(
                        "切线 ${topStep / 20f}/${bottomStep / 20f} 那一格被单独纵向拉伸",
                        sourceHeight(cell) * ratio,
                        cell.destination.height,
                        0.01f,
                    )
                }
                // 中间那一格永远正好盖住行盒，整块气泡不超过行盒的 4 倍（分母夹在整图高 25%）。
                val center = cells.single { it.destination == content }
                assertEquals(lineHeight, center.destination.height, 0.01f)
                assertTrue(
                    "气泡高 ${frameOf(image, content).height} 超出上限",
                    frameOf(image, content).height <= lineHeight * 4f + 0.01f,
                )
                bottomStep++
            }
            topStep++
        }
    }

    @Test
    fun fractionalMarginsRoundBackToTheirOriginalPixelBoundaries() {
        val image = ReaderTextBackgroundImage(
            "frame.9.png", 3, 1f,
            ninePatchLeft = 7f / 31f,
            ninePatchRight = 9f / 31f,
            ninePatchTop = 5f / 29f,
            ninePatchBottom = 8f / 29f,
        ).withBitmapSize(33, 31)
        val content = ReaderRect(7f, 5f, 22f, 21f)

        val cells = ReaderNineSliceLayout.cells(
            bitmapWidth = 33,
            bitmapHeight = 31,
            content = content,
            frame = frameOf(image, content),
            image = image,
        )

        // 分数切线换算回整像素：外框正好落在原图的像素边界上，不出现半像素缝。
        assertEquals(9, cells.size)
        assertEquals(ReaderIntRect(1, 1, 8, 6), cells.first().source)
        assertEquals(ReaderIntRect(23, 22, 32, 30), cells.last().source)
    }
}
