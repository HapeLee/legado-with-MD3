package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.CacheDownloadStateStore
import io.legado.app.model.cache.ChapterSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * 复现：整书暂停后点某一章下载，不应解冻失败重试章/其余章，也不应只 toast 不真正可启动。
 */
class CacheBookModelResumeTest {

    @Test
    fun bookPause_movesAllChaptersOutOfScheduleQueue() {
        val model = newModel()
        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Range(0, 35),
            )
        )
        assertTrue(model.pause())
        assertTrue(model.isPaused())
        assertFalse(model.hasLaunchableChapters())
        assertEquals(0, model.diagnostics().waitingChapterCount)
        assertTrue(model.pausedIndices().containsAll((0..35).toList()))
    }

    @Test
    fun resumeSingleChapterFromBookPause_onlyThatChapterBecomesLaunchable() {
        val model = newModel()
        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Range(0, 35),
            )
        )
        // 失败重试章会进 indices，优先级高于 range
        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Single(10),
            )
        )
        assertTrue(model.pause())
        assertTrue(model.isPaused())
        assertTrue(model.isPaused(13))

        assertTrue(model.resumeDownload(13))

        assertFalse(model.isPaused())
        assertTrue(model.hasLaunchableChapters())
        assertFalse(model.isPaused(13))
        assertTrue(model.isWaiting(13))
        // 其余章保持单章暂停，避免点 13 却先跑失败的 10
        assertTrue(model.pausedIndices().contains(10))
        assertTrue(model.pausedIndices().contains(0))
        assertTrue(model.pausedIndices().contains(16))
        assertFalse(model.pausedIndices().contains(13))
        assertEquals(1, model.diagnostics().waitingChapterCount)
    }

    @Test
    fun addRequestWhileBookPaused_doesNotResumeUnrelatedChapters() {
        val model = newModel()
        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Range(0, 35),
            )
        )
        assertTrue(model.pause())

        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Single(16),
            )
        )

        assertFalse(model.isPaused())
        assertTrue(model.hasLaunchableChapters())
        assertTrue(model.isWaiting(16))
        assertTrue(model.pausedIndices().contains(10))
        assertFalse(model.pausedIndices().contains(16))
        assertEquals(1, model.diagnostics().waitingChapterCount)
    }

    @Test
    fun readerPreloadWhileBookPaused_keepsTheBookPaused() {
        val model = newModel()
        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Range(0, 5),
            )
        )
        assertTrue(model.pause())

        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Single(6),
                source = CacheDownloadSource.ReadPreload,
            )
        )

        assertTrue(model.isPaused())
        assertFalse(model.hasLaunchableChapters())
    }

    @Test
    fun resumeBook_restoresAllPausedChaptersOnlyForThatModel() {
        val model = newModel()
        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Range(0, 5),
            )
        )
        assertTrue(model.pause())
        assertTrue(model.pausedIndices().containsAll((0..5).toList()))

        assertTrue(model.resume())
        assertFalse(model.isPaused())
        assertTrue(model.pausedIndices().isEmpty())
        assertEquals(6, model.diagnostics().waitingChapterCount)
        assertTrue(model.hasLaunchableChapters())
    }

    @Test
    fun resumeAllModels_unpausesEveryBookIndependently() {
        val host = FakeHost()
        val a = newModel(host, "https://example.com/a")
        val b = newModel(host, "https://example.com/b")
        val c = newModel(host, "https://example.com/c")
        a.addRequest(CacheDownloadRequest(a.book.bookUrl, ChapterSelection.Range(0, 2)))
        b.addRequest(CacheDownloadRequest(b.book.bookUrl, ChapterSelection.Range(0, 2)))
        c.addRequest(CacheDownloadRequest(c.book.bookUrl, ChapterSelection.Range(0, 2)))
        assertTrue(a.pause())
        assertTrue(b.pause())
        assertTrue(c.pause())

        // 模拟 FAB 全局恢复：对 map 内每一本 resume
        host.cacheBookMap.values.forEach { it.resume() }

        assertTrue(a.hasLaunchableChapters())
        assertTrue(b.hasLaunchableChapters())
        assertTrue(c.hasLaunchableChapters())
        assertFalse(a.isPaused())
        assertFalse(b.isPaused())
        assertFalse(c.isPaused())
    }

    @Test
    fun resumeDownload_returnsTrueWhenChapterOnlyBookPaused() {
        val model = newModel()
        model.addRequest(
            CacheDownloadRequest(
                bookUrl = BOOK_URL,
                selection = ChapterSelection.Range(0, 5),
            )
        )
        assertTrue(model.pause())
        assertTrue(model.isPaused(3))
        assertTrue(model.resumeDownload(3))
        assertTrue(model.hasLaunchableChapters())
    }

    private fun newModel(
        host: FakeHost = FakeHost(),
        bookUrl: String = BOOK_URL,
    ): CacheBookModel {
        val model = CacheBookModel(
            bookSource = BookSource(bookSourceUrl = "https://example.com", bookSourceName = "t"),
            book = Book(bookUrl = bookUrl, name = "漫画", origin = "https://example.com"),
            host = host,
        )
        host.cacheBookMap[bookUrl] = model
        return model
    }

    private class FakeHost : CacheBookModel.Host {
        override val stateStore = CacheDownloadStateStore()
        override val cacheBookMap = ConcurrentHashMap<String, CacheBookModel>()
        override fun incrementSuccessCount(): Int = 0
        override fun onTaskQueuesChanged(bookUrl: String) = Unit
        override fun onTaskRemoved(bookUrl: String, clearState: Boolean) = Unit
        override fun onExplicitBookQueued(bookUrl: String) = Unit
        override fun emitDownloadingIndices(bookUrl: String, indices: Set<Int>) = Unit
        override fun emitDownloadError(bookUrl: String, indices: Set<Int>) = Unit
        override fun emitChapterCached(chapter: BookChapter) = Unit
        override fun errorIndices(bookUrl: String): Set<Int> = emptySet()
    }

    companion object {
        private const val BOOK_URL = "https://example.com/book/1"
    }
}
