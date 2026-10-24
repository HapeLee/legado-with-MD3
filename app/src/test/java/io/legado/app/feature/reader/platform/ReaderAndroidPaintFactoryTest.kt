package io.legado.app.feature.reader.platform

import android.app.Application
import io.legado.app.feature.reader.core.model.ReaderTextShadow
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderAndroidPaintFactoryTest {
    @Test fun nativeUnderlineAndStrikeUseTheSamePaintAsGlyphDrawing() {
        val paint = ReaderAndroidPaintFactory.create(
            ReaderTextStyle(0, 24f, nativeUnderline = true, strikeThrough = true),
        )

        assertTrue(paint.isUnderlineText)
        assertTrue(paint.isStrikeThruText)
    }

    @Test
    fun shaperExcludesLetterSpacingWithoutMutatingCapturedPaint() {
        val paint = ReaderAndroidPaintFactory.createTextPaint(ReaderTextStyle(0, 24f))
        val expected = AndroidReaderTextShaper(paint).shape("甲乙")
        paint.letterSpacing = 0.2f
        val reshaped = AndroidReaderTextShaper(paint).shape("甲乙")
        assertEquals(expected.text, reshaped.text)
        assertArrayEquals(expected.widthsPx, reshaped.widthsPx, 0f)
        assertEquals(0.2f, paint.letterSpacing, 0f)
    }

    @Test
    fun baselineMatchesViewLineBottomMinusDescent() {
        for (size in listOf(12f, 24f, 37f)) {
            val paint = ReaderAndroidPaintFactory.createTextPaint(ReaderTextStyle(0, size))
            val metrics = paint.fontMetrics
            val height = metrics.descent - metrics.ascent + metrics.leading
            assertEquals(height - metrics.descent, ReaderAndroidPaintFactory.baselineOffset(paint), 0.0001f)
        }
    }

    @Test
    fun measurementAndDrawingPreserveWeightItalicColorAndShadow() {
        val style = ReaderTextStyle(
            colorArgb = 0xff123456.toInt(), fontSizePx = 28f, fontWeight = 550,
            italic = true, fontFamily = "serif", linearText = true,
            shadow = ReaderTextShadow(0xff654321.toInt(), 3f, 2f, 1f),
        )
        val draw = ReaderAndroidPaintFactory.create(style)
        val measure = ReaderAndroidPaintFactory.createTextPaint(style)
        assertEquals(style.colorArgb, draw.color)
        assertEquals(28f, draw.textSize, 0f)
        assertEquals(-0.25f, draw.textSkewX, 0f)
        assertEquals(550, draw.typeface.weight)
        assertTrue(draw.isLinearText)
        assertEquals(3f, draw.shadowLayerRadius, 0f)
        assertEquals(2f, draw.shadowLayerDx, 0f)
        assertEquals(1f, draw.shadowLayerDy, 0f)
        assertEquals(style.shadow!!.colorArgb, draw.shadowLayerColor)
        // Variation axes can create distinct Typeface objects with identical font metrics.
        assertEquals(draw.typeface.weight, measure.typeface.weight)
        assertEquals(draw.fontMetrics.top, measure.fontMetrics.top, 0.001f)
        assertEquals(draw.fontMetrics.bottom, measure.fontMetrics.bottom, 0.001f)
        assertEquals(draw.textSize, measure.textSize, 0f)
        assertEquals(draw.textSkewX, measure.textSkewX, 0f)
        assertEquals(draw.measureText("Reader字"), measure.measureText("Reader字"), 0.001f)
        assertTrue(draw.measureText("Reader字") > 0f)
    }

    @Test
    fun missingFileUsesRequestedSystemFamilyAndDoesNotLoseWeight() {
        val missing = ReaderAndroidPaintFactory.loadTypeface("missing-reader-font.ttf", 300, false, "monospace")
        val fallback = ReaderAndroidPaintFactory.loadTypeface("", 300, false, "monospace")
        assertEquals(fallback, missing)
        assertEquals(300, missing.weight)
    }
}
