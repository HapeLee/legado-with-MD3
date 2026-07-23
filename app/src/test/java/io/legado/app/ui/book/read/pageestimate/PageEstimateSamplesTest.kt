package io.legado.app.ui.book.read.pageestimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.roundToInt

class PageEstimateSamplesTest {

    private fun samplesOf(vararg points: Pair<Double, Double>) =
        points.fold(PageEstimateSamples()) { acc, (x, y) -> acc.plus(x, y) }

    @Test
    fun `recovers a known affine relation`() {
        // y = 1.4x + 0.5
        val samples = samplesOf(
            1.0 to 1.9, 2.0 to 3.3, 4.0 to 6.1, 8.0 to 11.7, 12.0 to 17.3,
        )

        val calibration = samples.fit()

        assertTrue(calibration.fitted)
        assertEquals(1.4f, calibration.slope, 1e-4f)
        assertEquals(0.5f, calibration.intercept, 1e-4f)
        assertEquals(5, calibration.sampleCount)
    }

    @Test
    fun `too few samples stay unfitted`() {
        val samples = samplesOf(1.0 to 2.0, 2.0 to 3.0, 3.0 to 4.0)

        assertFalse(samples.fit().fitted)
        assertEquals(3, samples.fit().sampleCount)
    }

    /** 所有章节估算值相同（行列式退化）时斜率无定义，必须退回冷启动而不是产出 NaN。 */
    @Test
    fun `identical estimates cannot produce a fit`() {
        val samples = samplesOf(5.0 to 6.0, 5.0 to 7.0, 5.0 to 5.0, 5.0 to 8.0, 5.0 to 6.0)

        val calibration = samples.fit()

        assertFalse(calibration.fitted)
        assertTrue(calibration.slope.isFinite())
    }

    @Test
    fun `implausible fits are rejected`() {
        // 斜率约 12，远超 MAX_SLOPE
        val steep = samplesOf(1.0 to 12.0, 2.0 to 24.0, 3.0 to 36.0, 4.0 to 48.0)
        assertFalse(steep.fit().fitted)

        // 截距约 -20，低于 MIN_INTERCEPT
        val shifted = samplesOf(10.0 to 1.0, 11.0 to 2.0, 12.0 to 3.0, 13.0 to 4.0)
        assertFalse(shifted.fit().fitted)
    }

    @Test
    fun `unfitted calibration is exactly the old ceil behaviour`() {
        val cold = PageEstimateCalibration()

        listOf(0.2f, 1f, 2.1f, 6.99f, 12f).forEach { estimate ->
            assertEquals(
                ceil(estimate).toInt().coerceAtLeast(1),
                cold.apply(estimate),
            )
        }
    }

    @Test
    fun `fitted calibration rounds instead of ceiling`() {
        val fitted = PageEstimateCalibration(
            slope = 1.5f,
            intercept = 0.4f,
            sampleCount = 8,
            fitted = true,
        )

        listOf(2f, 3.3f, 7.8f).forEach { estimate ->
            assertEquals((1.5f * estimate + 0.4f).roundToInt(), fitted.apply(estimate))
        }
        assertEquals(1, fitted.apply(0f))
    }

    @Test
    fun `non finite input never escapes`() {
        assertEquals(1, PageEstimateCalibration().apply(Float.NaN))
        assertEquals(1, PageEstimateCalibration().apply(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `effective sample window is capped while fit follows recent data`() {
        var samples = PageEstimateSamples()
        repeat(400) { index ->
            val x = (index % 20 + 1).toDouble()
            samples = samples.plus(x, 1.2 * x + 0.5)
        }

        val calibration = samples.fit()

        assertEquals(256, samples.count)
        assertTrue(calibration.fitted)
        assertEquals(1.2f, calibration.slope, 1e-3f)
        assertEquals(0.5f, calibration.intercept, 1e-3f)
    }
}
