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

    @Test
    fun `nine slice frame keeps the image border thickness on both ends`() {
        val framed = image.copy(contentInsetLeftPx = 3f, contentInsetRightPx = 4f)
        val framedStyle = style.copy(backgroundImage = framed)
        val page = page(
            text(3f, 0f, 13f, 20f, framedStyle),
            text(13f, 0f, 23f, 20f, framedStyle),
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
        // 上下两条切线之间那条带就是字要待的地方，高度按原图锁死：40×(1−0.1−0.2)=28。
        assertEquals(28f, resolved.centerBandPx, 0.001f)
        // 三条加起来 = 位图高，纵向一分都不拉伸。
        assertEquals(
            40f,
            resolved.contentInsetTopPx + resolved.centerBandPx + resolved.contentInsetBottomPx,
            0.001f,
        )
    }

    @Test
    fun `the locked band is centred on the text row and the frame height never changes`() {
        val sized = image.copy(
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(50, 40)

        // 行盒 20：带子 28 比字高，上下各外扩一半多余 → 图高仍是 40。
        assertEquals(8f, sized.frameTopPx(20f), 0.001f)
        assertEquals(12f, sized.frameBottomPx(20f), 0.001f)
        assertEquals(40f, 20f + sized.frameTopPx(20f) + sized.frameBottomPx(20f), 0.001f)
        // 行盒 30：带子比字矮 2px，上下各少让 1px，图高依旧 40。
        assertEquals(3f, sized.frameTopPx(30f), 0.001f)
        assertEquals(7f, sized.frameBottomPx(30f), 0.001f)
        assertEquals(40f, 30f + sized.frameTopPx(30f) + sized.frameBottomPx(30f), 0.001f)
    }

    /** 左偏移只管左沿、右偏移只管右沿：两端能分别对齐，这才是要拆成两项的原因。 */
    @Test
    fun `left and right offset each move only their own end of the frame`() {
        val framed = image.copy(
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            lengthOffsetLeftPx = 10f,
            lengthOffsetRightPx = 6f,
        )

        val frame = framed.nineSliceFrame(ReaderRect(10f, 0f, 20f, 20f))

        // 中间那一格左沿 = 10 − 左边条 3 − 左偏移 10，右沿 = 20 + 右边条 4 + 右偏移 6。
        assertEquals(-3f, frame.left, 0.001f)
        assertEquals(30f, frame.right, 0.001f)
        assertEquals(0f, frame.top, 0f)
        assertEquals(20f, frame.bottom, 0f)
    }

    @Test
    fun `a negative offset cannot push the frame past the text edges`() {
        fun frame(left: Float, right: Float) = image.copy(
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            lengthOffsetLeftPx = left,
            lengthOffsetRightPx = right,
        ).nineSliceFrame(ReaderRect(10f, 0f, 20f, 20f))

        // 夹紧到把中间那一格缩到零为止：两侧各退回文字宽的一半（5px），外框不许反过来跨过文字。
        val both = frame(-100f, -100f)
        assertEquals(12f, both.left, 0.001f)
        assertEquals(19f, both.right, 0.001f)

        // 只收左边：右边一条边都不退。
        val onlyLeft = frame(-100f, 0f)
        assertEquals(12f, onlyLeft.left, 0.001f)
        assertEquals(24f, onlyLeft.right, 0.001f)
    }

    @Test
    fun `non nine slice fits keep the text rect untouched`() {
        val tiled = image.copy(
            fit = 0,
            contentInsetLeftPx = 3f,
            lengthOffsetLeftPx = 10f,
            lengthOffsetRightPx = 10f,
        )
        val content = ReaderRect(10f, 0f, 20f, 20f)

        assertEquals(content, tiled.nineSliceFrame(content))
        assertEquals(0f, tiled.frameTopPx(20f), 0f)
        assertEquals(0f, tiled.frameBottomPx(20f), 0f)
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
        // 贴着内容框右下角的一截气泡：左右多出边条，上下多出「图比行盒高出来的那一截」。
        val page = page(text(90f, 80f, 100f, 100f, framedStyle, frameTop = 5f, frameBottom = 6f))

        val clip = page.contentClipRect(page.textBackgroundRuns())

        assertEquals(0f, clip.left, 0.001f)
        assertEquals(0f, clip.top, 0.001f)
        assertEquals(104f, clip.right, 0.001f)
        assertEquals(106f, clip.bottom, 0.001f)
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
