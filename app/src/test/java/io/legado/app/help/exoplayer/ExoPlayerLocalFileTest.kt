package io.legado.app.help.exoplayer

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.settings.DownloadCacheSettings
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ExoPlayerLocalFileTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        startKoin {
            modules(module {
                single<DownloadCacheSettingsGateway> {
                    object : DownloadCacheSettingsGateway {
                        override val currentSettings = DownloadCacheSettings()
                        override val settings = flowOf(currentSettings)
                        override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
                            error("Playback must not change download settings")
                        }
                    }
                }
            })
        }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `HTTP media still reads through the upstream with its request headers`() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val headers = CompletableFuture<String>()
        val bytes = byteArrayOf(10, 20, 30)
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val request = buildString {
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            appendLine(line)
                        }
                    }
                    headers.complete(request)
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    output.write(bytes)
                    output.flush()
                }
            } catch (failure: Exception) {
                headers.completeExceptionally(failure)
            }
        }
        val item = ExoPlayerHelper.createMediaItem(
            "http://127.0.0.1:${server.localPort}/video.mp4",
            mapOf("User-Agent" to "local-video-test", "X-NovelHub-Test" to "header-preserved"),
        )
        val source = ExoPlayerHelper.resolvingDataSource.createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(item.localConfiguration!!.uri).build())
            val actual = ByteArray(bytes.size)
            var offset = 0
            while (offset < actual.size) {
                val count = source.read(actual, offset, actual.size - offset)
                assertTrue("HTTP body ended early", count > 0)
                offset += count
            }
            assertArrayEquals(bytes, actual)
            val request = headers.get(5, TimeUnit.SECONDS)
            assertTrue(request.contains("User-Agent: local-video-test", ignoreCase = true))
            assertTrue(request.contains("X-NovelHub-Test: header-preserved", ignoreCase = true))
        } finally {
            source.close()
            server.close()
            worker.join(5000)
        }
    }

    @Test
    fun `production media source reads and seeks a local URI with encoded headers`() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        val file = File.createTempFile("local-video-", ".mp4", app.cacheDir)
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        file.writeBytes(bytes)
        try {
            val item = ExoPlayerHelper.createMediaItem(
                Uri.fromFile(file).toString(),
                mapOf("User-Agent" to "local-video-test"),
            )
            val source = ExoPlayerHelper.resolvingDataSource.createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(item.localConfiguration!!.uri).build())
                val actual = ByteArray(bytes.size)
                assertEquals(bytes.size, source.read(actual, 0, actual.size))
                assertArrayEquals(bytes, actual)
                assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
                source.close()

                source.open(
                    DataSpec.Builder().setUri(item.localConfiguration!!.uri).setPosition(3).build(),
                )
                val tail = ByteArray(3)
                assertEquals(3, source.read(tail, 0, tail.size))
                assertArrayEquals(byteArrayOf(4, 5, 6), tail)
            } finally {
                source.close()
            }
        } finally {
            file.delete()
        }
    }
}
