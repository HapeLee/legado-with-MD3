package io.legado.app.feature.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 真机原生 Canvas：覆盖不同系统的字距量化和 drawText 行首半字距差异。 */
@RunWith(AndroidJUnit4::class)
class ReaderTextDrawRunsDeviceTest {
    @Test
    fun ordinaryFractionalAndNegativeSpacingKeepMergedGlyphPositions() {
        for (linear in listOf(false, true)) {
            for (gap in listOf(0f, .6f, 1f, 2f, 3f, -1f, -3f, -20f)) {
                assertMergedPixels("国".repeat(20), gap, linear)
            }
        }
    }

    @Test
    fun mixedPunctuationAndLatinTextStayMergedWhenMetricsMatch() {
        for (value in listOf("天地玄黄，宇宙洪荒。日月盈昃！", "HNXHNXHNXHNX")) {
            assertMergedPixels(value, .6f, false)
        }
    }

    private fun assertMergedPixels(value: String, gap: Float, linear: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = 60f
            color = Color.BLACK
            isLinearText = linear
        }
        val style = ReaderTextStyle(Color.BLACK, 60f, linearText = linear)
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
        val expectsMerge = android.os.Build.VERSION.SDK_INT >= 30 || linear || gap % 1f == 0f
        assertTrue(
            "gap=$gap linear=$linear merge=$expectsMerge",
            if (expectsMerge) runs.size == elements.size else runs.isEmpty(),
        )
        if (!expectsMerge) return
        val expected = Bitmap.createBitmap(1800, 140, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(1800, 140, Bitmap.Config.ARGB_8888)
        try {
            val reference = Canvas(expected)
            elements.forEach { reference.drawText(it.value, it.bounds.left, it.baselinePx, paint) }
            drawMergedTextRun(Canvas(actual), runs.getValue(elements.first()), paint, Color.BLACK, Paint())
            assertTrue("gap=$gap linear=$linear text=$value preserve all glyph pixels", expected.sameAs(actual))
            // 使用排版内容边界裁剪，尤其覆盖 Android 17 负字距把末字推到右侧的反例。
            val right = elements.last().bounds.right
            for (y in 0 until actual.height) {
                for (x in kotlin.math.ceil(right).toInt().coerceAtLeast(0) until actual.width) {
                    assertTrue("text must fit layout right=$right x=$x", Color.alpha(actual.getPixel(x, y)) == 0)
                }
            }
        } finally {
            expected.recycle()
            actual.recycle()
        }
    }
}
