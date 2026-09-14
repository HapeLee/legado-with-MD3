package io.legado.app.feature.reader.platform

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.ReaderUnderline
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPageDecorationDrawCacheTest {

    @After fun clearSvgCache() = ReaderSvgPathCache.clear()

    @Test fun separatesContentAndOverlayRulesAroundMergedStyledUnderlines() {
        val underline = ReaderUnderline(1, 0xff112233.toInt(), 1f, 2f)
        val style = ReaderTextStyle(0xff000000.toInt(), 16f, underline = underline)
        val page = page(
            ReaderElement.Text(ReaderRect(0f, 0f, 5f, 10f), 8f, "甲", style, false, false, chapterPosition = 0),
            ReaderElement.Text(ReaderRect(5f, 0f, 10f, 10f), 8f, "乙", style, false, false, chapterPosition = 1),
            ReaderElement.Rule(ReaderRect(0f, 4f, 10f, 4f), 0xff000000.toInt(), 1f, false),
            ReaderElement.Rule(
                ReaderRect(0f, 12f, 10f, 12f),
                0xff000000.toInt(),
                1f,
                false,
                overlayStyledUnderline = true,
            ),
        )

        val cache = ReaderPageDecorationDrawCache.create(page)

        assertEquals(1, cache.contentRules.size)
        assertEquals(1, cache.styledUnderlines.size)
        assertEquals(1, cache.overlayRules.size)
    }

    @Test fun svgParserCachesSuccessfulPathsAndIgnoresBlankData() {
        val data = "M0 50 L100 50"
        val first = ReaderSvgPathCache.parse(data)

        assertSame(first, ReaderSvgPathCache.parse(data))
        assertNull(ReaderSvgPathCache.parse("  "))

        ReaderSvgPathCache.clear()
        assertNotSame(first, ReaderSvgPathCache.parse(data))
    }

    @Test
    fun halfHighlightIsSeparatedSoItCanBeDrawnBehindText() {
        val style = ReaderTextStyle(
            colorArgb = 0xff000000.toInt(),
            fontSizePx = 16f,
            underline = ReaderUnderline(7, 0x66ffd54f, 1f, 0f),
        )
        val cache = ReaderPageDecorationDrawCache.create(
            page(
                ReaderElement.Text(
                    ReaderRect(0f, 0f, 10f, 10f),
                    8f,
                    "甲",
                    style,
                    false,
                    false,
                    chapterPosition = 0
                )
            )
        )

        assertEquals(1, cache.halfHighlights.size)
        assertEquals(0, cache.styledUnderlines.size)
    }

    @Test(timeout = 1_000) fun zeroDashAndWaveLengthsCannotStallDrawing() {
        val canvas = Canvas(Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888))
        val bounds = ReaderRect(0f, 0f, 10f, 10f)

        ReaderUnderlineDrawCommand(
            bounds,
            ReaderUnderline(2, 0xff000000.toInt(), 1f, 0f, dashOnPx = 0f, dashOffPx = 0f),
        ).draw(canvas)
        ReaderUnderlineDrawCommand(
            bounds,
            ReaderUnderline(3, 0xff000000.toInt(), 1f, 0f, waveLengthPx = 0f),
        ).draw(canvas)
    }

    private fun page(vararg elements: ReaderElement) = ReaderPage(
        id = ReaderPageId(0, 0),
        chapterTitle = "",
        text = "甲乙",
        widthPx = 20,
        heightPx = 20,
        contentTopPx = 0f,
        contentBottomPx = 20f,
        elements = elements.toList(),
        revision = 1L,
    )
}
