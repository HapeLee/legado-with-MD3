package io.legado.app.feature.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundRun
import io.legado.app.feature.reader.core.model.frameBottomPx
import io.legado.app.feature.reader.core.model.frameLeftPx
import io.legado.app.feature.reader.core.model.frameRightPx
import io.legado.app.feature.reader.core.model.frameTopPx
import io.legado.app.feature.reader.core.model.withBitmapSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderTextBackgroundDrawTest {
    @Test
    fun translucentSlicesKeepUniformAlphaAtSeamsAndIntersections() {
        assertUniformFrame(sourceColor = 0x804080c0.toInt())
    }

    @Test
    fun translucentSlicesCompositeOnceOverThePageAfterScalingAndTranslation() {
        assertUniformFrame(
            sourceColor = 0x804080c0.toInt(),
            pageColor = 0xffeddbbb.toInt(),
            paintAlpha = 192,
            scale = 1.25f,
        )
    }

    @Test
    fun opaqueSlicesCoverFractionalSeamsWithoutGaps() {
        assertUniformFrame(sourceColor = 0xff4080c0.toInt(), scale = 0.75f)
    }

    @Test
    fun rawNinePatchSlicesCompositeOnceAtTheirInternalSeams() {
        assertUniformFrame(sourceColor = 0x804080c0.toInt(), rawNinePatch = true)
    }

    private fun assertUniformFrame(
        sourceColor: Int,
        pageColor: Int = Color.TRANSPARENT,
        paintAlpha: Int = 255,
        scale: Float = 1f,
        rawNinePatch: Boolean = false,
    ) {
        val border = if (rawNinePatch) 1 else 0
        val source = Bitmap.createBitmap(12 + border * 2, 12 + border * 2, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(sourceColor)
            if (rawNinePatch) {
                for (i in 0 until source.width) {
                    source.setPixel(i, 0, Color.BLACK)
                    source.setPixel(i, source.height - 1, Color.BLACK)
                    source.setPixel(0, i, Color.BLACK)
                    source.setPixel(source.width - 1, i, Color.BLACK)
                }
            }
            actual.eraseColor(pageColor)
            expected.eraseColor(pageColor)
            val image = ReaderTextBackgroundImage(
                source = if (rawNinePatch) "bubble.9.png" else "bubble.png",
                fit = 3,
                scale = 1f,
                ninePatchLeft = 0.25f,
                ninePatchRight = 0.25f,
                ninePatchTop = 0.25f,
                ninePatchBottom = 0.25f,
            ).withBitmapSize(source.width, source.height)
            val content = ReaderRect(20.25f, 20.5f, 60.25f, 32.5f)
            val frame = ReaderRect(
                content.left - image.frameLeftPx(content.height),
                content.top - image.frameTopPx(content.height),
                content.right + image.frameRightPx(content.height),
                content.bottom + image.frameBottomPx(content.height),
            )
            fun canvas(bitmap: Bitmap) = Canvas(bitmap).apply {
                translate(0.25f, 0.5f)
                scale(scale, scale)
            }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = paintAlpha }
            val actualCanvas = canvas(actual)
            val saveCount = actualCanvas.saveCount
            val clip = actualCanvas.clipBounds
            drawTextBackground(actualCanvas, source, ReaderTextBackgroundRun(frame, content, image), paint)
            assertEquals(saveCount, actualCanvas.saveCount)
            assertEquals(clip, actualCanvas.clipBounds)
            // A uniform source must look exactly like one image draw, including all outer pixels.
            canvas(expected).drawBitmap(
                source,
                Rect(border, border, source.width - border, source.height - border),
                RectF(frame.left, frame.top, frame.right, frame.bottom),
                Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = paintAlpha },
            )
            // The .9 guide border is nonuniform: scaling the whole image and scaling each
            // outer cell sample it differently. Compare its uniform interior, including both
            // split axes and their intersections; ordinary images above also check every edge.
            val comparisonBounds = if (rawNinePatch) RectF(
                (frame.left + 3f) * scale + 0.25f,
                (frame.top + 3f) * scale + 0.5f,
                (frame.right - 3f) * scale + 0.25f,
                (frame.bottom - 3f) * scale + 0.5f,
            ) else RectF(0f, 0f, actual.width.toFloat(), actual.height.toFloat())
            for (y in 0 until actual.height) for (x in 0 until actual.width) {
                if (!comparisonBounds.contains(x + 0.5f, y + 0.5f)) continue
                assertEquals("pixel ($x, $y), scale=$scale", expected.getPixel(x, y), actual.getPixel(x, y))
            }
        } finally {
            source.recycle()
            actual.recycle()
            expected.recycle()
        }
    }
}
