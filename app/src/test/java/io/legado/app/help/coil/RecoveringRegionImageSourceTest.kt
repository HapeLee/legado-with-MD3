package io.legado.app.help.coil

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.test.runTest
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class RecoveringRegionImageSourceTest {
    private val params = object : ImageRegionDecoder.FactoryParams {
        override val imageOptions = ImageBitmapOptions()
    }

    private fun source(factory: ImageRegionDecoder.Factory) = object : SubSamplingImageSource {
        override val preview = null
        override suspend fun decoder() = factory
        override fun close() = Unit
    }

    @Test
    fun initializationFailureFallsBackOnce() = runTest {
        var failures = 0
        val wrapped = RecoveringRegionImageSource(source { throw IOException("unsupported") }) { failures++ }
        val decoder = wrapped.decoder().create(params)
        decoder.decodeRegion(IntRect(0, 0, 1, 1), 1)
        assertEquals(1, failures)
    }

    @Test
    fun tileFailurePreservesDimensionsAndClosesDecoder() = runTest {
        var failures = 0
        var closed = false
        val original = object : ImageRegionDecoder {
            override val imageSize = IntSize(200, 300)
            override suspend fun decodeRegion(region: IntRect, sampleSize: Int): ImageRegionDecoder.DecodeResult =
                throw RuntimeException("native region decoding failed")
            override fun close() { closed = true }
        }
        val wrapped = RecoveringRegionImageSource(source { original }) { failures++ }
        val decoder = wrapped.decoder().create(params)
        repeat(2) { decoder.decodeRegion(IntRect(0, 0, 10, 10), 1) }
        assertEquals(original.imageSize, decoder.imageSize)
        assertEquals(1, failures)
        decoder.close()
        assertTrue(closed)
    }

    @Test
    fun cancellationDoesNotBecomeDecodeFailure() = runTest {
        var failed = false
        val wrapped = RecoveringRegionImageSource(source { throw CancellationException("disposed") }) { failed = true }
        try {
            wrapped.decoder().create(params)
            error("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertFalse(failed)
        }
    }
}
