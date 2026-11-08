package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import androidx.room.withTransaction
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.GroupBookCount
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ShelfBookSummary
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/**
 * 书架/导入页列表 Flow 的失效防抖窗口：批量写入（整目录导入、恢复）期间每次书表
 * 失效都会触发订阅方对整架全量重查重排，数千本规模下分配速率可压垮 GC；
 * 200ms 合并突发失效，单本更新的刷新延迟无感。
 */
internal const val SHELF_INVALIDATE_DEBOUNCE_MS = 200L

/**
 * 首个信号立即放行（首查不延迟），后续信号合并到 [debounceMillis] 窗口内。
 * 配合失效信号流（invalidationTrackerFlow）实现「查询前防抖」：debounce 在
 * 查询执行之前，批量写入期间不再产生中间查询结果与分配。
 */
@OptIn(FlowPreview::class)
internal fun <T> Flow<T>.debounceSkippingFirst(debounceMillis: Long): Flow<T> = flow {
    var first = true
    emitAll(
        this@debounceSkippingFirst.debounce {
            if (first) {
                first = false
                0L
            } else {
                debounceMillis
            }
        }
    )
}

class BookRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val appDb: AppDatabase,
) {

    private companion object {
        const val SQLITE_MAX_BIND_PARAMETERS = 900
    }

    fun flowBook(bookUrl: String): Flow<Book?> {
        return bookDao.flowGetBook(bookUrl)
    }

    fun flowChapters(bookUrl: String): Flow<List<BookChapter>> {
        return bookChapterDao.getChapterListFlow(bookUrl)
    }

    fun getAllBooks(): Flow<List<Book>> {
        return bookDao.flowAll()
    }

    suspend fun getBookCoverByNameAndAuthor(bookName: String, bookAuthor: String): String? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookName, bookAuthor)?.getDisplayCover()
        }
    }

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            val book = bookDao.getBook(bookName, bookAuthor)
            val bookUrl = book?.bookUrl
            if (bookUrl.isNullOrEmpty()) return@withContext null

            bookChapterDao.getChapterTitleByUrlAndIndex(bookUrl, chapterIndex)
        }
    }

    suspend fun getBook(bookUrl: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookUrl)
        }
    }

    suspend fun getBook(name: String, author: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(name, author)
        }
    }

    /**
     * 批量取书，用于把跨分组选中的 bookUrl 解析成实体。
     * 分批是因为 `IN (:bookUrls)` 会展开成绑定参数，单次上限由 SQLite 决定（默认 999）。
     */
    suspend fun getBooksByUrls(bookUrls: Set<String>): List<Book> = withContext(Dispatchers.IO) {
        if (bookUrls.isEmpty()) return@withContext emptyList()
        if (bookUrls.size <= SQLITE_MAX_BIND_PARAMETERS) {
            return@withContext bookDao.getBooksByUrls(bookUrls)
        }
        bookUrls.chunked(SQLITE_MAX_BIND_PARAMETERS).flatMap { chunk ->
            bookDao.getBooksByUrls(chunk.toSet())
        }
    }

    suspend fun getShelfBookSummaries(): List<ShelfBookSummary> {
        return withContext(Dispatchers.IO) {
            bookDao.getShelfBookSummaries()
        }
    }

    // 该 DAO 方法按 groupId 分发到多个 @Query 流（各组各自追踪自己的表集），
    // 在失效信号上做查询前防抖需要手工维护每查询表集，SQL 演进时极易漏订阅
    // （错过的失效 = 静默脏书架）。故此入口保留结果级防抖：批量期间的重查询
    // 仍会发生，但下游排序/重组/分配已被合并——那是分配风暴的实际来源。
    @OptIn(FlowPreview::class)
    fun flowBookShelfByGroup(groupId: Long): Flow<List<BookShelfItem>> {
        return bookDao.flowBookShelfByGroup(groupId)
            .debounce(SHELF_INVALIDATE_DEBOUNCE_MS)
    }

    fun flowSystemGroupCounts(): Flow<List<GroupBookCount>> {
        return bookDao.flowSystemGroupCounts()
    }

    fun flowAllBookShelfCount(): Flow<Int> {
        return bookDao.flowAllBookShelfCount()
    }

    fun flowUserGroupBookCount(groupId: Long): Flow<Int> {
        return bookDao.flowUserGroupBookCount(groupId)
    }

    fun flowGroupPreview(groupId: Long): Flow<List<BookShelfItem>> {
        return bookDao.flowGroupPreview(groupId)
    }

    suspend fun getChapter(bookUrl: String, index: Int): BookChapter? {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapter(bookUrl, index)
        }
    }

    suspend fun getChapterCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterCount(bookUrl)
        }
    }

    suspend fun getVolumeCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getVolumeCount(bookUrl)
        }
    }

    suspend fun getChapters(bookUrl: String): List<BookChapter> {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterList(bookUrl)
        }
    }

    suspend fun getChapters(bookUrl: String, start: Int, end: Int): List<BookChapter> {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterList(bookUrl, start, end)
        }
    }

    suspend fun update(vararg book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.update(*book)
        }
    }

    suspend fun upVideoPlayProgress(
        bookUrl: String,
        index: Int,
        position: Int,
        chapterTitle: String?,
        time: Long,
    ) {
        withContext(Dispatchers.IO) {
            bookDao.upVideoPlayProgress(bookUrl, index, position, chapterTitle, time)
        }
    }

    /** 只改本书的记录开关，保留阅读器并发保存的进度和其他字段。 */
    suspend fun setReadRecordDisabled(bookUrl: String, disabled: Boolean): Book? =
        withContext(Dispatchers.IO) {
            appDb.withTransaction {
                bookDao.setReadRecordDisabled(bookUrl, disabled)
                bookDao.getBook(bookUrl)
            }
        }

    suspend fun getMinOrder(): Int {
        return withContext(Dispatchers.IO) {
            bookDao.minOrder
        }
    }

    suspend fun insert(book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.insert(book)
        }
    }

    suspend fun insertChapters(vararg chapters: BookChapter) {
        withContext(Dispatchers.IO) {
            bookChapterDao.insert(*chapters)
        }
    }

    suspend fun getHasUpdateBooks(): List<Book> {
        return withContext(Dispatchers.IO) {
            bookDao.hasUpdateBooks
        }
    }

    suspend fun getAll(): List<Book> {
        return withContext(Dispatchers.IO) {
            bookDao.getAll()
        }
    }

    suspend fun getLastReadBook(): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.lastReadBook
        }
    }

    suspend fun getLastReadBookForReader(): Book? = withContext(Dispatchers.IO) {
        bookDao.lastReadBookForReader
    }

    suspend fun replace(oldBook: Book, newBook: Book) {
        withContext(Dispatchers.IO) {
            bookDao.replace(oldBook, newBook)
        }
    }

    suspend fun delete(vararg book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.delete(*book)
        }
    }

    suspend fun deleteChaptersByBook(bookUrl: String) {
        withContext(Dispatchers.IO) {
            bookChapterDao.delByBook(bookUrl)
        }
    }

    /**
     * 原子替换整本书的目录并落库书籍信息。
     *
     * @param oldBookUrl 书籍详情刷新后 bookUrl 可能变化，此时要顺带清掉旧 URL 的章节，
     * 否则它们会成为没有书籍行的孤儿数据。
     */
    suspend fun replaceChaptersAndUpdateBook(
        book: Book,
        chapters: List<BookChapter>,
        oldBookUrl: String = book.bookUrl,
    ) {
        withContext(Dispatchers.IO) {
            appDb.runInTransaction {
                if (oldBookUrl != book.bookUrl) {
                    bookChapterDao.delByBook(oldBookUrl)
                }
                bookChapterDao.delByBook(book.bookUrl)
                bookChapterDao.insert(*chapters.toTypedArray())
                bookDao.update(book)
            }
        }
    }

}
