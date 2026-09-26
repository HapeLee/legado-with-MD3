package io.legado.app.help.coil

import android.app.Application
import coil3.ColorImage
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
class MangaPrefetchDataTest {
    private class StreamPage

    @Test
    fun `stream prefetch consumes to EOF without decoding caching a bitmap or calling display listener`() =
        runBlocking {
            val size = 70_000L
            var consumed = 0L
            var eof = false
            var closed = false
            var ready = 0
            val stream = object : ForwardingSource(Buffer().write(ByteArray(size.toInt()))) {
                override fun read(sink: Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also {
                        if (it == -1L) eof = true else consumed += it
                    }

                override fun close() {
                    closed = true; super.close()
                }
            }.buffer()
            val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()
            try {
                val request = ImageRequest.Builder(RuntimeEnvironment.getApplication())
                    .data(StreamPage()).size(20, 30)
                    .fetcherFactory<StreamPage> { _, options, _ ->
                        Fetcher {
                            SourceFetchResult(
                                ImageSource(stream, options.fileSystem),
                                null,
                                DataSource.NETWORK
                            )
                        }
                    }
                    .listener(onSuccess = { _, _ -> ready++ })
                    .build().asMangaPrefetch()
                val result = loader.execute(request)
                if (result is ErrorResult) throw result.throwable
                assertTrue(result.image is ColorImage)
                assertNull((result as SuccessResult).memoryCacheKey)
                assertEquals(size, consumed)
                assertTrue(eof)
                assertTrue(closed)
                assertEquals(0, ready)
            } finally {
                loader.shutdown()
            }
        }
}
