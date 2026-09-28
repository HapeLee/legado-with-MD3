package io.legado.app.help.coil

import android.app.Application
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.runBlocking
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
class LocalMangaRegionRecoveryTest {
    @Before
    fun setUp() { RuntimeEnvironment.getApplication().injectAsAppCtx() }

    private val params = object : ImageRegionDecoder.FactoryParams {
        override val imageOptions = ImageBitmapOptions()
    }

    @Test
    fun sourceWithoutOnlineFileLeaseStillFallsBackAndClosesOriginal() = runBlocking {
        val owner = MangaImageFileOwner()
        var closes = 0
        val original = object : SubSamplingImageSource {
            override val preview = null
            override suspend fun decoder() = ImageRegionDecoder.Factory { throw IOException("local decoder failed") }
            override fun close() { closes++ }
        }
        val recovered = recoveringMangaTileSource(original, owner)
        recovered.decoder().create(params)
        assertTrue(owner.regionDecodeFailed.value)
        recovered.close()
        assertEquals(1, closes)
        owner.close()
    }

    @Test
    fun localTileCancellationDoesNotRequestFallback() = runBlocking {
        val owner = MangaImageFileOwner()
        val original = object : SubSamplingImageSource {
            override val preview = null
            override suspend fun decoder() = ImageRegionDecoder.Factory {
                object : ImageRegionDecoder {
                    override val imageSize = IntSize(40, 60)
                    override suspend fun decodeRegion(region: IntRect, sampleSize: Int): ImageRegionDecoder.DecodeResult =
                        throw CancellationException("disposed")
                }
            }
        }
        val recovered = recoveringMangaTileSource(original, owner)
        try {
            recovered.decoder().create(params).decodeRegion(IntRect(0, 0, 10, 10), 1)
            error("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertFalse(owner.regionDecodeFailed.value)
        } finally {
            recovered.close()
            owner.close()
        }
    }
}
