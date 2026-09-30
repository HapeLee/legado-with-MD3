package io.legado.app.help.coil

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.IntSize
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MangaWebtoonTileWindowTest {
    private fun plan(y: Float = 0f, height: Int = 20_000, width: Int = 800) = mangaWebtoonTiles(
        IntSize(800, height), IntSize(width, height * width / 800), Offset(0f, -y),
        Rect(0f, 0f, 1080f, 2400f),
    )

    @Test
    fun `very tall images retain source width without making full height bitmaps`() {
        for (height in listOf(20_000, 200_000)) {
            val tiles = plan(10_000f, height)
            assertTrue(tiles.size <= 6)
            assertTrue(tiles.all { it.sampleSize == 1 && it.bounds.width == 800 })
            assertTrue(tiles.all { it.decodeBounds.width <= 1026 && it.decodeBounds.height <= 1026 })
            val rows = tiles.sortedBy { it.bounds.top }
            assertTrue(rows.first().bounds.top <= 10_000)
            assertTrue(rows.last().bounds.bottom >= 12_400)
            rows.zipWithNext().forEach { (first, second) ->
                assertEquals(first.bounds.bottom, second.bounds.top)
                assertTrue(first.decodeBounds.bottom > second.decodeBounds.top)
            }
        }
    }

    @Test
    fun `page edges are clamped and offscreen pages do not decode`() {
        assertEquals(0, plan().minOf { it.bounds.top })
        assertEquals(20_000, plan(19_000f).maxOf { it.bounds.bottom })
        assertTrue(plan(20_001f).isEmpty())
        assertTrue(plan(-2401f).isEmpty())
        assertTrue(mangaWebtoonTiles(IntSize.Zero, IntSize.Zero, Offset.Zero, Rect.Zero).isEmpty())
    }

    @Test
    fun `zoom uses a smaller sample and horizontally clipped viewports only load visible columns`() {
        val source = IntSize(8000, 200_000)
        val viewport = Rect(0f, 0f, 1080f, 2400f)
        val normal = mangaWebtoonTiles(source, IntSize(1000, 25_000), Offset.Zero, viewport)
        val zoomed =
            mangaWebtoonTiles(source, IntSize(4000, 100_000), Offset(-1500f, -20_000f), viewport)
        assertTrue(normal.all { it.sampleSize == 8 })
        assertTrue(zoomed.all { it.sampleSize == 2 })
        assertTrue(zoomed.all { it.bounds.left >= 2048 && it.bounds.right <= 6144 })
        assertTrue(zoomed.all { it.decodeBounds.width / it.sampleSize <= 1026 })
    }

    @Test
    fun `scrolling reuses overlap and releases tiles outside the viewport window`() = runTest {
        val plans = MutableStateFlow(plan())
        val decoded = mutableListOf<MangaWebtoonTile>()
        var published = emptySet<MangaWebtoonTile>()
        val job = launch {
            loadMangaWebtoonTiles(
                plans,
                { decoded += it; ColorPainter(Color.Red) },
                { published = it.keys })
        }
        runCurrent()
        val first = plans.value.toSet()
        val second = plan(1024f)
        plans.value = second
        runCurrent()
        assertEquals(first.size + (second.toSet() - first).size, decoded.size)
        assertEquals(second.toSet(), published)
        plans.value = persistentListOf()
        runCurrent()
        assertTrue(published.isEmpty())
        job.cancelAndJoin()
    }

    @Test
    fun `cancellation propagates and does not publish a late native decode`() = runTest {
        var delivered = false
        val started = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        val job = launch {
            loadMangaWebtoonTiles(flowOf(plan()), {
                // Native decoders can return a bitmap after the calling job has been cancelled.
                withContext(NonCancellable) {
                    started.complete(Unit)
                    returned.await()
                    ColorPainter(Color.Red)
                }
            }, { if (it.isNotEmpty()) delivered = true })
        }
        started.await()
        job.cancel()
        returned.complete(Unit)
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(delivered)
    }

    @Test
    fun `disposing an in flight decode cancels work`() = runTest {
        var cancelled = false
        val job = launch {
            loadMangaWebtoonTiles(flowOf(plan()), {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }, {})
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(cancelled)
    }
}
