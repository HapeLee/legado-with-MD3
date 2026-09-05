package io.legado.app.feature.reader.core.pageestimate

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class PageEstimateMetricsTest {

    @BeforeTest
    fun clearMetrics() {
        LocalPageEstimateMetrics.clear()
    }

    @Test
    fun `keeps only recent diagnostics and exports no chapter id`() {
        repeat(205) { index ->
            LocalPageEstimateMetrics.record(
                PageEstimateMetric.ChapterCorrected(
                    generation = index.toLong(),
                    layoutSignature = 10L,
                    chapterId = "private://chapter-$index",
                    chapterIndex = index,
                    contentLength = 1_000,
                    rawEstimate = 5f,
                    estimatedPages = 5,
                    realPages = 6,
                    absoluteError = 1,
                    relativeError = 1f / 6f,
                    calibrationBefore = PageEstimateCalibration(),
                    calibrationAfter = PageEstimateCalibration(),
                )
            )
        }

        val exported = LocalPageEstimateMetrics.export()

        assertEquals(200, LocalPageEstimateMetrics.size())
        assertTrue(exported.contains("entryCount=200"))
        assertTrue(exported.contains("chapterIndex=204"))
        assertFalse(exported.contains("chapterIndex=0,"))
        assertFalse(exported.contains("private://"))
    }
}
