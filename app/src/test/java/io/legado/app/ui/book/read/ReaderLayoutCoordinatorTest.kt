package io.legado.app.ui.book.read

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderLayoutCoordinatorTest {

    @Test
    fun `View 与 Compose 上报相同正文尺寸时只更新一次分页引擎`() {
        val appliedSizes = mutableListOf<Pair<Int, Int>>()
        val coordinator = ReaderLayoutCoordinator(
            updateLayoutSize = { width, height -> appliedSizes += width to height },
            relayoutContent = {},
        )

        coordinator.updateViewport(
            ReaderViewport(widthPx = 800, heightPx = 1200, density = 2f)
        )
        coordinator.updateViewport(
            ReaderViewport(
                widthPx = 1000,
                heightPx = 1500,
                density = 2f,
                contentPadding = ReaderPadding(left = 100, top = 100, right = 100, bottom = 200),
            )
        )

        assertEquals(listOf(800 to 1200), appliedSizes)
        assertEquals(100, coordinator.viewport.value?.contentPadding?.left)
    }

    @Test
    fun `无效测量不覆盖 viewport 且显式重排版经过接缝`() {
        var relayoutCount = 0
        val coordinator = ReaderLayoutCoordinator(
            updateLayoutSize = { _, _ -> error("无效尺寸不应进入分页引擎") },
            relayoutContent = { relayoutCount++ },
        )

        coordinator.updateViewport(ReaderViewport(0, 1200, 2f))
        coordinator.requestRelayout()

        assertEquals(null, coordinator.viewport.value)
        assertEquals(1, relayoutCount)
    }

    @Test
    fun `排版结果携带当前 viewport 与单调 revision`() = runBlocking {
        val coordinator = ReaderLayoutCoordinator(
            updateLayoutSize = { _, _ -> },
            relayoutContent = {},
        )
        val viewport = ReaderViewport(800, 1200, 2f, mode = ReaderLayoutMode.PAGED)
        coordinator.updateViewport(viewport)
        val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.layoutResults.first() }

        coordinator.publishPageLayout(pageIndex = 3)
        val result = first.await()

        assertEquals(1L, result.revision)
        assertEquals(3, result.pageIndex)
        assertSame(viewport, result.viewport)
        assertSame(viewport, coordinator.awaitViewport())
    }

    @Test
    fun `没有 View 测量时 Compose viewport 仍驱动分页尺寸`() = runBlocking {
        val appliedSizes = mutableListOf<Pair<Int, Int>>()
        val coordinator = ReaderLayoutCoordinator(
            updateLayoutSize = { width, height -> appliedSizes += width to height },
            relayoutContent = {},
        )

        coordinator.updateViewport(ReaderViewport(1080, 2340, density = 3f))

        assertEquals(listOf(1080 to 2340), appliedSizes)
        assertEquals(1080, coordinator.awaitViewport()?.contentWidthPx)
    }

    @Test
    fun `viewport 未测量时有界等待后返回 null`() = runBlocking {
        val coordinator = ReaderLayoutCoordinator(
            updateLayoutSize = { _, _ -> },
            relayoutContent = {},
        )

        assertNull(coordinator.awaitViewport(timeoutMillis = 20))
    }
}
