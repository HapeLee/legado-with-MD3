package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTextBackgroundRunTest {

    private val image = ReaderTextBackgroundImage("background.png", fit = 3, scale = 1f)
    private val style = ReaderTextStyle(0xFF000000.toInt(), 20f, backgroundImage = image)
    private val plainStyle = ReaderTextStyle(0xFF000000.toInt(), 20f)

    @Test
    fun `merges adjacent text with the same background on one line`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(10f, 0f, 25f, 20f, style),
        )

        assertEquals(listOf(ReaderRect(0f, 0f, 25f, 20f)), page.textBackgroundRuns().map { it.bounds })
    }

    @Test
    fun `letter spacing gap continues the run when pagination marks it`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(12f, 0f, 22f, 20f, style, continues = true),
        )

        assertEquals(
            listOf(ReaderRect(0f, 0f, 22f, 20f)),
            page.textBackgroundRuns().map { it.bounds })
    }

    @Test
    fun `unmatched glyph between same image ranges breaks the run`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(10f, 0f, 20f, 20f, plainStyle),
            text(20f, 0f, 30f, 20f, style, continues = true),
        )

        assertEquals(2, page.textBackgroundRuns().size)
    }

    @Test
    fun `flagged continuation does not cross rows`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(12f, 20f, 22f, 40f, style, continues = true),
        )

        assertEquals(2, page.textBackgroundRuns().size)
    }

    @Test
    fun `does not merge across lines gaps or different images`() {
        val other = style.copy(backgroundImage = image.copy(source = "other.png"))
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(12f, 0f, 22f, 20f, style),
            text(0f, 20f, 10f, 40f, style),
            text(10f, 20f, 20f, 40f, other),
        )

        assertEquals(4, page.textBackgroundRuns().size)
    }

    /** 外框两端让出的都是**等比换算后**的边条厚度（这里倍率 1，所以就是边条本身）。 */
    @Test
    fun `nine slice frame grows both ends by the scaled border thickness`() {
        val framed = image.copy(contentInsetLeftPx = 3f, contentInsetRightPx = 4f)
        val framedStyle = style.copy(backgroundImage = framed)
        val page = page(
            text(3f, 0f, 13f, 20f, framedStyle, frameLeft = 3f, frameRight = 4f),
            text(13f, 0f, 23f, 20f, framedStyle, frameLeft = 3f, frameRight = 4f),
        )

        assertEquals(ReaderRect(0f, 0f, 27f, 20f), page.textBackgroundRuns().single().bounds)
    }

    @Test
    fun `bitmap width resolves legacy nine slice horizontal margins`() {
        val resolved = image.copy(
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(50, 40)

        assertEquals(10f, resolved.contentInsetLeftPx, 0f)
        assertEquals(15f, resolved.contentInsetRightPx, 0.001f)
        assertEquals(4f, resolved.contentInsetTopPx, 0f)
        assertEquals(8f, resolved.contentInsetBottomPx, 0f)
    }

    /**
     * 上下两条切线之间就是文字的显示区域：中间带对上行盒高，整张图按同一个倍率变大小
     * （等比缩放，没有哪一格被单独拉伸）。行盒 28 = 中间带自然高时倍率为 1，上下边条是 4/8；
     * 行盒翻到 56 时三条一起翻倍，气泡高从 40 长到 80。
     */
    @Test
    fun `the whole image scales to the text row by one uniform factor`() {
        val sized = image.copy(
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(50, 40)

        assertEquals(28f, sized.contentBandHeightPx, 0.001f)
        assertEquals(4f, sized.frameTopPx(28f), 0.001f)
        assertEquals(8f, sized.frameBottomPx(28f), 0.001f)
        assertEquals(40f, 28f + sized.frameTopPx(28f) + sized.frameBottomPx(28f), 0.001f)
        // 行盒 28→56：倍率 1→2，上下边条跟着翻倍。
        assertEquals(8f, sized.frameTopPx(56f), 0.001f)
        assertEquals(16f, sized.frameBottomPx(56f), 0.001f)
        assertEquals(80f, 56f + sized.frameTopPx(56f) + sized.frameBottomPx(56f), 0.001f)
        // 行盒缩到中间带的一半：整张图等比缩到一半。
        assertEquals(2f, sized.frameTopPx(14f), 0.001f)
        assertEquals(4f, sized.frameBottomPx(14f), 0.001f)
        assertEquals(20f, 14f + sized.frameTopPx(14f) + sized.frameBottomPx(14f), 0.001f)
    }

    /**
     * 上下两条切线挤到一起时中间带趋近 0，按行盒算等比倍率会趋于无穷。所以中间带一律留整张图
     * 高的 25%（整像素），两条边按各自比例缩回——整张图最高也就是行盒的 4 倍，气泡不会高过屏幕。
     *
     * 注意夹的是**切线位置**而不是「边条厚度」：只夹厚度的话中间那一格就独享另一个倍率，
     * 纵向拉伸又回来了（用户否掉过的那种）。
     */
    @Test
    fun `squeezed split lines cannot blow the bubble up`() {
        val squeezed = image.copy(
            ninePatchTop = 0.49f,
            ninePatchBottom = 0.5f,
        ).withBitmapSize(50, 40)

        // 整张图高 40：中间带留 10 像素，上下两条边各 15（合计正好 3 倍中间带）。
        assertEquals(10f, squeezed.contentBandHeightPx, 0.001f)
        assertEquals(15f, squeezed.contentInsetTopPx, 0.001f)
        assertEquals(15f, squeezed.contentInsetBottomPx, 0.001f)
        // 写回的切线就是绘制期切片读的那一份，倍率与切线不会各算各的。
        assertEquals(0.375f, squeezed.ninePatchTop, 0.001f)
        assertEquals(0.375f, squeezed.ninePatchBottom, 0.001f)
        // 行盒 28 → 倍率 2.8，上下各 42：整张图 112 = 行盒的 4 倍。
        assertEquals(112f, 28f + squeezed.frameTopPx(28f) + squeezed.frameBottomPx(28f), 0.01f)
    }

    /**
     * 左偏移只管左沿、右偏移只管右沿：两端能分别对齐，这才是要拆成两项的原因。
     * 四周一圈的厚度走分页期算好的 [frameLeftPx]（与上下同一条等比倍率），这里只补偏移。
     */
    @Test
    fun `left and right offset each move only their own end of the frame`() {
        val framed = image.copy(
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            contentBandHeightPx = 20f,
            lengthOffsetLeftPx = 10f,
            lengthOffsetRightPx = 6f,
        )
        val text = ReaderRect(10f, 0f, 20f, 20f)
        val frame = framed.nineSliceFrame(
            text.copy(
                left = text.left - framed.frameLeftPx(text.height),
                right = text.right + framed.frameRightPx(text.height),
            ),
            text.width,
        )

        // 行盒 20 = 中间带 20 → 倍率 1，左右边条各 3 / 4 像素。
        assertEquals(3f, framed.frameLeftPx(20f), 0.001f)
        assertEquals(4f, framed.frameRightPx(20f), 0.001f)
        // 中间那一格左沿 = 10 − 左边条 3 − 左偏移 10，右沿 = 20 + 右边条 4 + 右偏移 6。
        assertEquals(-3f, frame.left, 0.001f)
        assertEquals(30f, frame.right, 0.001f)
        assertEquals(0f, frame.top, 0f)
        assertEquals(20f, frame.bottom, 0f)
    }

    /** 纵向缩了一半，左右边条也必须跟着缩一半，否则整张图只缩一个方向——图案就被压扁了。 */
    @Test
    fun `all four edges share one scale so the art never deforms`() {
        val framed = image.copy(
            contentInsetLeftPx = 20f,
            contentInsetRightPx = 20f,
            contentInsetTopPx = 10f,
            contentInsetBottomPx = 10f,
            contentBandHeightPx = 40f,
        )

        // 行盒 20 是中间带 40 的一半 → 四边都按 0.5 换算。
        assertEquals(0.5f, framed.verticalScalePx(20f), 0.001f)
        assertEquals(10f, framed.frameLeftPx(20f), 0.001f)
        assertEquals(10f, framed.frameRightPx(20f), 0.001f)
        assertEquals(5f, framed.frameTopPx(20f), 0.001f)
        assertEquals(5f, framed.frameBottomPx(20f), 0.001f)
        // 非九宫格不参与这套换算。
        assertEquals(0f, framed.copy(fit = 0).frameLeftPx(20f), 0f)
    }

    @Test
    fun `a negative offset cannot push the frame past the text edges`() {
        fun frame(left: Float, right: Float) = image.copy(
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            lengthOffsetLeftPx = left,
            lengthOffsetRightPx = right,
        ).nineSliceFrame(ReaderRect(10f, 0f, 20f, 20f), 10f)

        // 夹紧到把中间那一格缩到零为止：两侧各退回文字宽的一半（5px），外框不许反过来跨过文字。
        val both = frame(-100f, -100f)
        assertEquals(15f, both.left, 0.001f)
        assertEquals(15f, both.right, 0.001f)

        // 只收左边：右边一条边都不退。
        val onlyLeft = frame(-100f, 0f)
        assertEquals(15f, onlyLeft.left, 0.001f)
        assertEquals(20f, onlyLeft.right, 0.001f)
    }

    @Test
    fun `non nine slice fits keep the text rect untouched`() {
        val tiled = image.copy(
            fit = 0,
            contentInsetLeftPx = 3f,
            contentBandHeightPx = 20f,
            lengthOffsetLeftPx = 10f,
            lengthOffsetRightPx = 10f,
        )
        val content = ReaderRect(10f, 0f, 20f, 20f)

        assertEquals(content, tiled.nineSliceFrame(content, content.width))
        assertEquals(0f, tiled.frameTopPx(20f), 0f)
        assertEquals(0f, tiled.frameBottomPx(20f), 0f)
        assertEquals(0f, tiled.frameLeftPx(20f), 0f)
        assertEquals(0f, tiled.frameRightPx(20f), 0f)
    }

    @Test
    fun `raw nine patch border is excluded when resolving fixed margins`() {
        val resolved = image.copy(
            source = "background.9.png",
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(52, 42)

        assertEquals(10f, resolved.contentInsetLeftPx, 0f)
        assertEquals(15f, resolved.contentInsetRightPx, 0.001f)
        assertEquals(4f, resolved.contentInsetTopPx, 0f)
        assertEquals(8f, resolved.contentInsetBottomPx, 0f)
    }

    /**
     * 胶囊对背景完全透明：分页期会把胶囊之后的字标成同一 run 的延续（见
     * [io.legado.app.feature.reader.core.layout.ReaderPaginator] 的 previousItemBackground），
     * 绘制期只认这个标记，几何上被胶囊撑开的间隙不再断链——一句对白只有一个包住胶囊的气泡。
     */
    @Test
    fun `role capsule does not cut the bubble in half`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            capsule(10f, 0f, 40f, 20f),
            text(40f, 0f, 55f, 20f, style, continues = true),
        )

        assertEquals(listOf(ReaderRect(0f, 0f, 55f, 20f)), page.textBackgroundRuns().map { it.bounds })
    }

    @Test
    fun `bgm capsule does not cut the bubble in half`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            ReaderElement.BgmScene(
                bounds = ReaderRect(10f, 0f, 30f, 20f),
                paragraphIndex = 0,
                poolName = "池",
                trackName = "曲",
                chapterPosition = 0,
            ),
            text(30f, 0f, 45f, 20f, style, continues = true),
        )

        assertEquals(1, page.textBackgroundRuns().size)
    }

    /** 行内图是真内容，不是我们插进去的按钮：它照常切断一条气泡。 */
    @Test
    fun `inline image still breaks the run`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            ReaderElement.Image(
                bounds = ReaderRect(10f, 0f, 40f, 20f),
                source = "pic.png",
                action = null,
                inline = true,
            ),
            text(40f, 0f, 55f, 20f, style, continues = true),
        )

        assertEquals(2, page.textBackgroundRuns().size)
    }

    @Test
    fun `capsule never bridges two different rows`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            capsule(10f, 0f, 40f, 20f),
            text(40f, 20f, 55f, 40f, style, continues = true),
        )

        assertEquals(2, page.textBackgroundRuns().size)
    }

    /** 九宫格气泡的外圈天生超出内容框，裁剪框必须跟着背景走，否则四周被切成平口。 */
    @Test
    fun `content clip grows to the drawn bubble`() {
        val framed = image.copy(contentInsetLeftPx = 3f, contentInsetRightPx = 4f)
        val framedStyle = style.copy(backgroundImage = framed)
        // 贴着内容框右下角的一截气泡：四条边都是分页期按同一倍率换算出来的边条厚度。
        val page = page(
            text(
                90f, 80f, 100f, 100f, framedStyle,
                frameTop = 5f, frameBottom = 6f, frameLeft = 3f, frameRight = 4f,
            ),
        )

        val clip = page.contentClipRect(page.textBackgroundRuns())

        assertEquals(0f, clip.left, 0.001f)
        assertEquals(0f, clip.top, 0.001f)
        assertEquals(104f, clip.right, 0.001f)
        assertEquals(106f, clip.bottom, 0.001f)
    }

    /** 一段气泡的框 = 文字段四边各让出一条等比换算后的边条；合并时左右各取两端。 */
    @Test
    fun `run frame grows outward on all four edges by the per-glyph insets`() {
        val page = page(
            text(10f, 20f, 20f, 40f, style, frameTop = 5f, frameBottom = 6f, frameLeft = 3f, frameRight = 4f),
            text(20f, 20f, 35f, 40f, style, frameTop = 5f, frameBottom = 6f, frameLeft = 3f, frameRight = 4f),
        )

        assertEquals(
            listOf(ReaderRect(7f, 15f, 39f, 46f)),
            page.textBackgroundRuns().map { it.bounds },
        )
    }

    @Test
    fun `content clip stays on the content box without backgrounds`() {
        val page = page(text(0f, 0f, 10f, 20f, plainStyle))

        assertEquals(ReaderRect(0f, 0f, 100f, 100f), page.contentClipRect(emptyList()))
    }

    private fun capsule(left: Float, top: Float, right: Float, bottom: Float) =
        ReaderElement.RoleCast(
            bounds = ReaderRect(left, top, right, bottom),
            name = "丹妃",
            voicePoolLabel = "女中年",
            avatarUri = "",
            assigned = true,
            voiceEffectMark = false,
            quoteOrdinal = 0,
            characterId = "c",
            chapterPosition = 0,
        )

    private fun text(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        textStyle: ReaderTextStyle,
        continues: Boolean = false,
        frameTop: Float = 0f,
        frameBottom: Float = 0f,
        frameLeft: Float = 0f,
        frameRight: Float = 0f,
    ) = ReaderElement.Text(
        bounds = ReaderRect(left, top, right, bottom),
        baselinePx = bottom - 4f,
        value = "字",
        style = textStyle,
        selected = false,
        emphasized = false,
        chapterPosition = 0,
        continuesBackgroundRun = continues,
        backgroundFrameTopPx = frameTop,
        backgroundFrameBottomPx = frameBottom,
        backgroundFrameLeftPx = frameLeft,
        backgroundFrameRightPx = frameRight,
    )

    private fun page(vararg elements: ReaderElement) = ReaderPage(
        id = ReaderPageId(0, 0),
        chapterTitle = "chapter",
        text = "",
        widthPx = 100,
        heightPx = 100,
        elements = elements.toList(),
        contentTopPx = 0f,
        contentBottomPx = 100f,
        revision = 1L,
    )
}
