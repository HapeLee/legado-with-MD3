package io.legado.app.data.repository.manga.translation

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaPaddleDbPostprocessorTest {
    @Test fun `user thresholds change candidate admission while unclip changes only recognition padding`() {
        val probability = Array(80) { FloatArray(120) }
        for (y in 10..30) for (x in 10..80) probability[y][x] = .8f
        fun extract(options: io.legado.app.domain.model.manga.MangaOcrOptions): List<MangaPaddleDbPostprocessor.Quad> {
            val values = options.detectionThresholds(io.legado.app.domain.model.manga.MangaLocalOcrEngine.PADDLE_V5)
            return MangaPaddleDbPostprocessor.extractQuads(probability, 1f, 1f, values.probability, values.score, values.unclip, retainSupport = true)
        }
        val base = io.legado.app.domain.model.manga.MangaOcrOptions()
        assertEquals(1, extract(base).size)
        assertTrue(extract(base.copy(probabilityThreshold = .9f)).isEmpty())
        assertTrue(extract(base.copy(boxScoreThreshold = .9f)).isEmpty())
        val narrow = extract(base.copy(unclipRatio = .5f)).single()
        val wide = extract(base.copy(unclipRatio = 3f)).single()
        assertTrue(wide.width > narrow.width)
        assertEquals(narrow.support, wide.support)
    }
    @Test fun `separate rows keep tight source support while recognition receives padded crops`() {
        val probability = Array(80) { FloatArray(120) }
        for (top in listOf(12, 48)) for (y in top until top + 10) for (x in 20 until 90) probability[y][x] = .95f
        val quads = MangaPaddleDbPostprocessor.extractQuads(probability, 2f, 2f, .3f, .6f, 1.5f, retainSupport = true)
        assertEquals(2, quads.size)
        val first = quads.first()
        val support = checkNotNull(first.support)
        assertTrue(first.width > support.width)
        assertTrue(first.height > support.height)
        assertEquals(40f, support.p0.x, .1f)
        assertEquals(24f, support.p0.y, .1f)
        val fitted = checkNotNull(MangaPaddleDbPostprocessor.fitRecognitionCropQuad(first, 240, 160))
        assertTrue(fitted.width > 0 && fitted.height > 0)
    }

    @Test fun `weak detector output and isolated noise do not become text regions`() {
        val probability = Array(80) { FloatArray(120) }
        for (y in 10..25) for (x in 10..60) probability[y][x] = .4f
        probability[60][80] = .99f
        assertTrue(MangaPaddleDbPostprocessor.extractQuads(probability, 1f, 1f, .3f, .6f, 1.5f).isEmpty())
    }
}
