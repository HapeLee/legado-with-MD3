package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNineSliceLayoutTest {
    // 50×40 的图，切线 20%/30%/10%/20%，scale 1：四条边的原图厚度 10/15/4/8。
    // 上下两条切线之间那一格按行盒高画，所以整张图的高 = 4 + 行盒 + 8，随字号自适应。
    private val locked = ReaderTextBackgroundImage(
        source = "frame.png",
        fit = 3,
        scale = 1f,
        ninePatchLeft = 0.2f,
        ninePatchRight = 0.3f,
        ninePatchTop = 0.1f,
        ninePatchBottom = 0.2f,
    ).withBitmapSize(50, 40)

    /** 分页给这一行算出的外框：上下各让出一条边自己的厚度，横向只有中间格被拉长。 */
    private fun frameOf(image: ReaderTextBackgroundImage, content: ReaderRect): ReaderRect =
        image.nineSliceFrame(
            content.copy(
                top = content.top - image.frameTopPx(),
                bottom = content.bottom + image.frameBottomPx(),
            ),
        )

    private fun sourceWidth(cell: ReaderNineSliceCell) = cell.source.right - cell.source.left

    private fun sourceHeight(cell: ReaderNineSliceCell) = cell.source.bottom - cell.source.top

    /** 中间那一行（源里从第 4 像素起）按行盒高画，上下两条边按源厚原样画。 */
    private fun expectedHeight(cell: ReaderNineSliceCell, lineHeight: Float) =
        if (cell.source.top == 4) lineHeight else (cell.source.bottom - cell.source.top).toFloat()

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
    fun theMiddleCellAdaptsToTheTextRowWhileEveryEdgeKeepsItsThickness() {
        val content = ReaderRect(10f, 20f, 40f, 50f)
        val frame = frameOf(locked, content)

        // 纵向：图高 = 上边条 4 + 行盒 30 + 下边条 8。
        assertEquals(42f, frame.height, 0.01f)

        val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, locked)

        assertEquals(9, cells.size)
        cells.forEach { cell ->
            assertEquals(expectedHeight(cell, content.height), cell.destination.height, 0.01f)
        }
        // 中间那一格既被横向拉到文字宽度，也被纵向拉成行盒高：字底下正好铺满。
        val center = cells[4]
        assertEquals(ReaderIntRect(10, 4, 35, 32), center.source)
        assertEquals(content, center.destination)
        // 左右两条边按原图厚度画，且落在文字框外侧（不压在字上）。
        assertEquals(10f, cells.first().destination.width, 0.01f)
        assertEquals(15f, cells.last().destination.width, 0.01f)
    }

    /** 自适应大小：行盒长一分，气泡就高一分，切出来的字永远在上下两条线之间。 */
    @Test
    fun theBubbleGrowsAndShrinksWithTheTextRow() {
        listOf(12f, 30f, 64f).forEach { lineHeight ->
            val content = ReaderRect(10f, 100f, 40f, 100f + lineHeight)
            val frame = frameOf(locked, content)

            assertEquals(lineHeight + 12f, frame.height, 0.01f)
            val center = ReaderNineSliceLayout.cells(50, 40, content, frame, locked)
                .single { it.source == ReaderIntRect(10, 4, 35, 32) }
            assertEquals(lineHeight, center.destination.height, 0.01f)
            assertEquals(100f, center.destination.top, 0.01f)
        }
    }

    /** 左/右偏移各自只挪自己那一端：中间格 = 文字宽 + 左 + 右，两条边原厚、跟着平移。 */
    @Test
    fun lengthOffsetWidensOnlyTheMiddleCellAndSlidesTheEdgesWithIt() {
        val content = ReaderRect(10f, 20f, 40f, 50f)
        val stretched = locked.copy(lengthOffsetLeftPx = 6f, lengthOffsetRightPx = 10f)
        val frame = frameOf(stretched, content)

        val cells = ReaderNineSliceLayout.cells(50, 40, content, frame, stretched)

        // 30 文字宽 + 6 左偏移 + 10 右偏移 = 46，加上 10/15 两条原厚边 → 外框 71。
        assertEquals(71f, frame.width, 0.01f)
        assertEquals(42f, frame.height, 0.01f)
        assertEquals(9, cells.size)
        assertEquals(46f, cells[4].destination.width, 0.01f)
        // 上下两截带子跟着中间格一起变宽，厚度仍按原图。
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

        // 源 10×4 → 目标 5×2：scale 0.5 一份不少地作用在四条边上。
        assertEquals(10, sourceWidth(topLeft))
        assertEquals(4, sourceHeight(topLeft))
        assertEquals(5f, topLeft.destination.width, 0f)
        assertEquals(2f, topLeft.destination.height, 0f)
        // 图高 34 = 上边条 2 + 行盒 28 + 下边条 4：两条边按 scale，中间那一格按字。
        assertEquals(34f, frame.height, 0.01f)
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
