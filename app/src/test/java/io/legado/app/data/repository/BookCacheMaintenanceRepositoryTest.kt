package io.legado.app.data.repository

import android.app.Application
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.OrphanDeleteRequest
import io.legado.app.help.book.BookHelp
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File
import java.lang.reflect.Proxy

/**
 * Repository 层集成回归：用真实 Room（内存库）+ 真实缓存目录，覆盖 FakeGateway 绕过的部分。
 *
 * 重点是两条安全不变量：合法章节缓存绝不能进孤儿名单；整书字数绝不能比章节字数先落库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class BookCacheMaintenanceRepositoryTest {

    @Test
    fun `scan lists current and legacy orphans while keeping legal chapters and image directory`() {
        withRepository { repository, db ->
            val book = onlineBook("orphan")
            val chapter = chapterOf(book, 0, "Chapter One")
            db.bookDao.insert(book)
            db.bookChapterDao.insert(chapter)

            val bookDir = bookDir(book)
            listOf(
                chapter.getFileName().substringBeforeLast('.'),
                "images",
                ORPHAN_DIR,
                LEGACY_ORPHAN_DIR,
            ).forEach { File(bookDir, it).mkdirs() }
            File(bookDir, LEGACY_ORPHAN_FILE).writeText("legacy content")

            val groups = runBlocking { repository.scanOrphanedChapters { _, _ -> } }

            assertEquals(1, groups.size)
            assertEquals(
                setOf(ORPHAN_DIR, LEGACY_ORPHAN_DIR, LEGACY_ORPHAN_FILE),
                groups.single().entryNames.toSet(),
            )
            assertEquals(book.bookUrl, groups.single().bookUrl)
        }
    }

    /**
     * 第一本处理完、第二本开始前取消。若整书字数先落库而章节字数还攒在 pending 里，
     * 取消后该书 wordCount 非空却永远不会再进 scanRecountTargets 的候选，章节字数永久补不上。
     */
    @Test
    fun `cancelling mid-run never leaves a book with total word count but missing chapter counts`() {
        withRepository { repository, db ->
            val books = listOf(onlineBook("first"), onlineBook("second"))
            val chapters = books.map { chapterOf(it, 0, "Chapter One") }
            db.bookDao.insert(*books.toTypedArray())
            db.bookChapterDao.insert(*chapters.toTypedArray())
            books.zip(chapters).forEach { (book, chapter) ->
                val dir = bookDir(book).apply { mkdirs() }
                File(dir, chapter.getFileName()).writeText("缓存正文内容")
            }

            var progressCalls = 0
            try {
                runBlocking {
                    repository.recountWordCounts(
                        LinkedHashSet(books.map { it.bookUrl })
                    ) { _, _ ->
                        progressCalls++
                        if (progressCalls == 2) throw CancellationException("stop mid-run")
                    }
                }
            } catch (_: CancellationException) {
                // 期望的中断点
            } catch (_: Throwable) {
                // runBlocking 可能把取消包装成 JobCancellationException
            }

            val firstBook = db.bookDao.getBook(books[0].bookUrl)!!
            val firstChapter = db.bookChapterDao.getChapterList(books[0].bookUrl).single()
            val secondBook = db.bookDao.getBook(books[1].bookUrl)!!
            val secondChapter = db.bookChapterDao.getChapterList(books[1].bookUrl).single()

            assertNotNull("第一本已处理完，整书字数应当已写", firstBook.wordCount)
            assertNotNull("章节字数必须先于整书字数提交", firstChapter.wordCount)
            assertEquals(firstChapter.wordCount, firstBook.wordCount)
            assertNull("第二本尚未处理，整书字数不应写入", secondBook.wordCount)
            assertNull(secondChapter.wordCount)
            assertTrue(progressCalls >= 2)
        }
    }

    /**
     * 整书字数已有值（常见于书源详情，含未缓存章节）时，仍要能补缺失的章节字数，
     * 但绝不能用「已缓存章节之和」覆盖整书字数。
     */
    @Test
    fun `existing total word count is kept while missing chapter counts are filled`() {
        withRepository { repository, db ->
            val book = onlineBook("has-total").apply { wordCount = "100万字" }
            val chapter = chapterOf(book, 0, "Chapter One")
            db.bookDao.insert(book)
            db.bookChapterDao.insert(chapter)
            val dir = bookDir(book).apply { mkdirs() }
            File(dir, chapter.getFileName()).writeText("缓存正文内容")

            val targets = runBlocking { repository.scanRecountTargets { _, _ -> } }
            assertEquals(1, targets.size)
            assertEquals(book.bookUrl, targets.single().bookUrl)

            runBlocking { repository.recountWordCounts(setOf(book.bookUrl)) { _, _ -> } }

            assertEquals("100万字", db.bookDao.getBook(book.bookUrl)!!.wordCount)
            assertNotNull(db.bookChapterDao.getChapterList(book.bookUrl).single().wordCount)
        }
    }

    /** 没有任何章节被重算时，不做「只补整书字数」的动作，整书字数字段保持为空。 */
    @Test
    fun `no chapter recount means no book total write`() {
        withRepository { repository, db ->
            val book = onlineBook("no-total")
            val chapter = chapterOf(book, 0, "Chapter One").apply { wordCount = "6字" }
            db.bookDao.insert(book)
            db.bookChapterDao.insert(chapter)
            val dir = bookDir(book).apply { mkdirs() }
            File(dir, chapter.getFileName()).writeText("缓存正文内容")

            assertTrue(
                runBlocking { repository.scanRecountTargets { _, _ -> } }.isEmpty()
            )
            runBlocking { repository.recountWordCounts(setOf(book.bookUrl)) { _, _ -> } }

            assertNull(db.bookDao.getBook(book.bookUrl)!!.wordCount)
            assertEquals("6字", db.bookChapterDao.getChapterList(book.bookUrl).single().wordCount)
        }
    }

    /** 整书字数在所有章节重算完成后一次汇总，而不是每算完一章就重写。 */
    @Test
    fun `book total is summed once after all chapter recounts complete`() {
        withRepository { repository, db ->
            val book = onlineBook("multi")
            val chapters = (0..2).map { chapterOf(book, it, "Chapter $it") }
            db.bookDao.insert(book)
            db.bookChapterDao.insert(*chapters.toTypedArray())
            val dir = bookDir(book).apply { mkdirs() }
            chapters.forEach { File(dir, it.getFileName()).writeText("缓存正文内容") }

            val outcome = runBlocking {
                repository.recountWordCounts(setOf(book.bookUrl)) { _, _ -> }
            }
            assertEquals(3, outcome.updatedChapters)
            assertEquals(1, outcome.updatedBooks)

            val stored = db.bookChapterDao.getChapterList(book.bookUrl)
            assertEquals(3, stored.size)
            assertTrue(stored.all { !it.wordCount.isNullOrBlank() })
            val chapterCounts = stored.map { it.wordCount }.toSet()
            assertEquals(1, chapterCounts.size)
            // 若逐章覆盖整书字数，最终值会等于某一章的字数；一次汇总则应明显更大。
            assertNotEquals(
                chapterCounts.single(),
                db.bookDao.getBook(book.bookUrl)!!.wordCount,
            )
        }
    }

    /** 整书字数的 UPDATE 自带「当前为空」条件：书源期间写入的值不能被覆盖。 */
    @Test
    fun `book total update only writes when the field is still empty`() {
        withRepository { _, db ->
            val empty = onlineBook("empty-total")
            val filled = onlineBook("filled-total").apply { wordCount = "100万字" }
            db.bookDao.insert(empty, filled)

            assertEquals(1, db.bookDao.upWordCount(empty.bookUrl, "6字"))
            assertEquals("6字", db.bookDao.getBook(empty.bookUrl)!!.wordCount)
            assertEquals(0, db.bookDao.upWordCount(filled.bookUrl, "999字"))
            assertEquals("100万字", db.bookDao.getBook(filled.bookUrl)!!.wordCount)
        }
    }

    /** 章节查询失败时，删除侧和重算侧都必须把书留下供重试。 */
    @Test
    fun `chapter query failure keeps books for retry on both paths`() {
        withRepository(chapterDaoFactory = { throwingChapterDao() }) { repository, db ->
            val book = onlineBook("query-failed")
            db.bookDao.insert(book)
            db.bookChapterDao.insert(chapterOf(book, 0, "Chapter One"))
            File(bookDir(book).apply { mkdirs() }, ORPHAN_DIR).mkdirs()

            val deleted = runBlocking {
                repository.deleteOrphanedChapters(
                    listOf(OrphanDeleteRequest(book.bookUrl, setOf(ORPHAN_DIR)))
                ) { }
            }
            assertEquals(0, deleted.deletedEntries)
            assertEquals(1, deleted.skippedBooks)
            assertEquals(0L, deleted.freedBytes)

            val recount = runBlocking {
                repository.recountWordCounts(setOf(book.bookUrl)) { _, _ -> }
            }
            assertTrue("重算侧不应把书算作已处理", recount.handledBookUrls.isEmpty())
            assertNull("查询失败不应写整书字数", db.bookDao.getBook(book.bookUrl)!!.wordCount)
        }
    }

    private fun throwingChapterDao(): BookChapterDao = Proxy.newProxyInstance(
        BookChapterDao::class.java.classLoader,
        arrayOf(BookChapterDao::class.java),
    ) { _, _, _ -> throw IllegalStateException("simulated chapter query failure") } as BookChapterDao

    private fun onlineBook(suffix: String) = Book(
        bookUrl = "https://cache.example/$suffix",
        name = "Book $suffix",
        type = BookType.text,
    )

    private fun chapterOf(book: Book, index: Int, title: String) = BookChapter(
        bookUrl = book.bookUrl,
        url = "${book.bookUrl}/chapter/$index",
        title = title,
        index = index,
    )

    private fun bookDir(book: Book) = File(BookHelp.cachePath, book.getFolderName())

    private fun withRepository(
        chapterDaoFactory: (AppDatabase) -> BookChapterDao = { it.bookChapterDao },
        block: (BookCacheMaintenanceRepository, AppDatabase) -> Unit,
    ) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        File(BookHelp.cachePath).deleteRecursively()
        InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            try {
                block(
                    BookCacheMaintenanceRepository(
                        fixture.database.bookDao,
                        chapterDaoFactory(fixture.database),
                    ),
                    fixture.database,
                )
            } finally {
                File(BookHelp.cachePath).deleteRecursively()
            }
        }
    }

    private companion object {
        const val ORPHAN_DIR = "00009-9999999999999999"
        const val LEGACY_ORPHAN_DIR = "legacy-chapter-9"
        const val LEGACY_ORPHAN_FILE = "deadbeef.nb"
    }
}
