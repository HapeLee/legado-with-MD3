package io.legado.app.feature.reader.platform

import android.app.Application
import io.legado.app.feature.reader.core.layout.GlyphClusters
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidReaderTextShaperContractTest {
    @Test
    fun exposesTextPaintFontMetricsUsingTheSharedPositiveMetricConvention() {
        val paint = ReaderAndroidPaintFactory.createTextPaint(ReaderTextStyle(0, 28f))
        val metrics = paint.fontMetrics
        val shaper = AndroidReaderTextShaper(paint)
        val bounds = shaper.fontBounds
        val lineMetrics = shaper.fontLineMetrics

        assertEquals(metrics.top, bounds.topPx, 0.001f)
        assertEquals(metrics.bottom, bounds.bottomPx, 0.001f)
        assertEquals(metrics.descent, bounds.descentPx, 0.001f)
        assertEquals(metrics.descent - metrics.ascent + metrics.leading, lineMetrics.heightPx, 0.001f)
        assertEquals(-metrics.ascent, lineMetrics.baselineOffsetPx, 0.001f)
        assertEquals(-metrics.ascent, lineMetrics.ascentPx, 0.001f)
        assertEquals(metrics.descent, lineMetrics.descentPx, 0.001f)
    }

    @Test
    fun preservesSharedClusterRulesAndTreatsEmptyTextAsEmptyOutput() {
        val shaper = AndroidReaderTextShaper(ReaderAndroidPaintFactory.createTextPaint(ReaderTextStyle(0, 24f)))

        assertEquals(GlyphClusters(emptyList(), emptyList()), shaper.shape(""))
        val source = "A\u0301B"
        val shaped = shaper.shape(source)
        assertEquals(source, shaped.text.joinToString(separator = ""))
        assertEquals(2, shaped.text.size)
        assertEquals(2, shaped.widthsPx.size)
    }
}
