package io.legado.app.feature.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderTextDrawRunsNativeTest {
    @Test
    fun fractionalAndOddSpacingRetainMergedDrawingWithoutMovingGlyphs() {
        for (linear in listOf(false, true)) {
            for (gap in listOf(0f, .6f, 1f, 2f, 3f, -1f, -3f, -20f)) {
                assertMergedMatchesElements(gap, linear)
            }
        }
    }

    @Test
    fun changedFontAdvancesFallBackWithoutMutatingTheStylePaint() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { textSize = 60f }
        val style = ReaderTextStyle(Color.BLACK, 60f)
        val width = paint.measureText("H")
        val elements = List(3) { index ->
            val left = 40f + index * (width + 2f)
            ReaderElement.Text(
                bounds = ReaderRect(left, 0f, left + width, 120f),
                baselinePx = 100f,
                value = "H",
                style = style,
                selected = false,
                emphasized = false,
                chapterPosition = index,
            )
        }
        val changed = elements.dropLast(1) + elements.last().copy(
            bounds = elements.last().bounds.copy(right = elements.last().bounds.right + 5f),
        )
        assertTrue(buildMergedTextRuns(changed, mapOf(style to paint)).isEmpty())
        assertTrue(paint.letterSpacing == 0f)
        paint.flags = paint.flags or Paint.TEXT_RUN_FLAG_LEFT_EDGE or Paint.TEXT_RUN_FLAG_RIGHT_EDGE
        val flags = paint.flags
        val run = buildMergedTextRuns(elements, mapOf(style to paint)).getValue(elements[0])
        val bitmap = Bitmap.createBitmap(240, 140, Bitmap.Config.ARGB_8888)
        try {
            drawMergedTextRun(Canvas(bitmap), run, paint, Color.BLACK, Paint())
            assertTrue(paint.letterSpacing == 0f && paint.flags == flags)
        } finally {
            bitmap.recycle()
        }
    }
    private fun assertMergedMatchesElements(gap: Float, linear: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = 60f
            color = Color.BLACK
            isLinearText = linear
        }
        val style = ReaderTextStyle(Color.BLACK, 60f, linearText = linear)
        val value = "H".repeat(20)
        val widths = FloatArray(value.length)
        paint.getTextWidths(value, widths)
        var left = 40f
        val elements = value.mapIndexed { index, char ->
            ReaderElement.Text(
                bounds = ReaderRect(left, 0f, left + widths[index], 120f),
                baselinePx = 100f,
                value = char.toString(),
                style = style,
                selected = false,
                emphasized = false,
                chapterPosition = index,
            ).also { left += widths[index] + gap }
        }
        val runs = buildMergedTextRuns(elements, mapOf(style to paint))
        assertTrue("gap=$gap linear=$linear should stay merged", runs.size == elements.size)
        val expected = Bitmap.createBitmap(1800, 140, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(1800, 140, Bitmap.Config.ARGB_8888)
        try {
            val reference = Canvas(expected)
            elements.forEach { reference.drawText(it.value, it.bounds.left, it.baselinePx, paint) }
            drawMergedTextRun(Canvas(actual), runs.getValue(elements.first()), paint, Color.BLACK, Paint())
            assertTrue("gap=$gap linear=$linear must preserve glyph pixels", expected.sameAs(actual))
        } finally {
            expected.recycle()
            actual.recycle()
        }
    }
}
