package io.legado.app.ui.book.read.pageestimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PageEstimateConfigTest {

    private val config = PageEstimateConfig(
        readerType = 0,
        textSizePx = 32f,
        textHeightPx = 38f,
        lineSpacingPx = 8f,
        paragraphSpacingPx = 4f,
        titleTextSizePx = 42f,
        titleTextHeightPx = 48f,
        titleLineSpacingPx = 8f,
        titleTopSpacingPx = 16f,
        titleBottomSpacingPx = 12f,
        endPaddingPx = 20f,
        contentWidthPx = 1080,
        contentHeightPx = 1920,
        fontKey = "font-a",
        contentKey = "rule-a",
    )

    @Test
    fun `layout signature is stable for equal configuration`() {
        assertEquals(config.layoutSignature, config.copy().layoutSignature)
        assertEquals(config.calibrationBucket, config.copy().calibrationBucket)
    }

    @Test
    fun `content processing affects strict signature but not calibration bucket`() {
        val changed = config.copy(contentKey = "rule-b")

        assertNotEquals(config.layoutSignature, changed.layoutSignature)
        assertEquals(config.calibrationBucket, changed.calibrationBucket)
    }


    @Test
    fun `layout dimensions affect signature and calibration bucket`() {
        val changed = config.copy(contentWidthPx = 900)

        assertNotEquals(config.layoutSignature, changed.layoutSignature)
        assertNotEquals(config.calibrationBucket, changed.calibrationBucket)
    }
}
