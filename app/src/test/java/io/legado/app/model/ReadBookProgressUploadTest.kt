package io.legado.app.model

import android.app.Application
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.service.BaseReadAloudService
import io.legado.app.support.InMemoryAppDatabaseFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/** A late upload must not write its old Book snapshot over newer Room reading progress. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadBookProgressUploadTest {

    private lateinit var uploadingBook: Book
    private lateinit var currentBook: Book
    private lateinit var databaseFixture: InMemoryAppDatabaseFixture

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        databaseFixture = InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication())
        BaseReadAloudService.requestStop()
        uploadingBook = Book(
            bookUrl = "https://example.test/upload-progress",
            name = "Upload progress",
            durChapterIndex = 5,
            durChapterPos = 100,
            durChapterTime = 1_000,
            syncTime = 500,
        )
        currentBook = uploadingBook.copy(
            durChapterIndex = 8,
            durChapterPos = 850,
            durChapterTime = 2_000,
            durChapterTitle = "Chapter 8",
        )
        appDb.bookDao.insert(currentBook)
        ReadBook.replaceCurrentBook(currentBook)
    }

    @After
    fun tearDown() {
        try {
            ReadBook.clearCurrentBook()
        } finally {
            if (::databaseFixture.isInitialized) databaseFixture.close()
        }
    }

    @Test
    fun lateUploadOfOldChapterOnlySavesItsSyncTime() {
        // The network completes after the reader has replaced the original Book instance.
        uploadingBook.syncTime = 3_000

        ReadBook.onProgressUploaded(uploadingBook)

        assertEquals(currentBook.copy(syncTime = 3_000), appDb.bookDao.getBook(currentBook.bookUrl))
        assertSame(currentBook, ReadBook.book)
        assertEquals(8, currentBook.durChapterIndex)
        assertEquals(850, currentBook.durChapterPos)
        assertEquals(3_000L, currentBook.syncTime)
    }

    @Test
    fun olderUploadCompletionDoesNotDecreaseSyncTimeOrReadingProgress() {
        uploadingBook.syncTime = 3_000
        ReadBook.onProgressUploaded(uploadingBook)

        ReadBook.onProgressUploaded(uploadingBook.copy(syncTime = 2_500))

        val saved = appDb.bookDao.getBook(currentBook.bookUrl)!!
        assertEquals(3_000L, saved.syncTime)
        assertEquals(8, saved.durChapterIndex)
        assertEquals(850, saved.durChapterPos)
        assertEquals(3_000L, currentBook.syncTime)
    }

    @Test
    fun uploadForPreviousBookDoesNotChangeTheActiveBook() {
        val nextBook = currentBook.copy(bookUrl = "https://example.test/another-book")
        ReadBook.replaceCurrentBook(nextBook)
        uploadingBook.syncTime = 3_000

        ReadBook.onProgressUploaded(uploadingBook)

        assertSame(nextBook, ReadBook.book)
        assertEquals(500L, nextBook.syncTime)
        assertEquals(8, nextBook.durChapterIndex)
        assertEquals(850, nextBook.durChapterPos)
        assertEquals(3_000L, appDb.bookDao.getBook(currentBook.bookUrl)?.syncTime)
    }
}
