package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.LocalBookMetricInvalidation
import io.legado.app.help.book.BookHelp
import androidx.room.invalidationTrackerFlow
import io.legado.app.help.book.canSafelyRebindTo
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.FileDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BookImportRepository(
    private val appDb: AppDatabase,
) {

    /** 大小缓存的单书失效由 [LocalBook.importMangaDirectory] 统一负责。 */
    fun importMangaDirectory(directory: FileDoc): Book =
        LocalBook.importMangaDirectory(directory)

    // 查询前防抖：debounce 挂在 books 表失效信号上（而非 DAO 查询结果之后），
    // 批量导入期间不再执行中间查询、不产生中间结果分配；首个信号立即放行，
    // 首屏加载无延迟
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun flowLocalBooks(): Flow<List<Book>> = appDb.invalidationTrackerFlow("books")
        .onStart { emit(emptySet()) }
        .debounceSkippingFirst(SHELF_INVALIDATE_DEBOUNCE_MS)
        .mapLatest { appDb.bookDao.getLocal() }
        .flowOn(Dispatchers.IO)

    suspend fun findByFileName(fileName: String): Book? = withContext(Dispatchers.IO) {
        appDb.bookDao.getBookByFileName(fileName)
    }

    suspend fun findAndRebind(fileName: String, filePath: String): Book? =
        withContext(Dispatchers.IO) {
            val book = appDb.bookDao.getBookByFileName(fileName) ?: return@withContext null
            if (book.bookUrl == filePath || !book.canSafelyRebindTo(filePath)) {
                return@withContext book
            }

            // 迁主键后旧目录的正文偏移对新文件没有意义：清掉解析时间让 isLocalModified()
            // 为真，打开这本书时会按新文件重新解析目录，而不是拿旧偏移读取。
            val reboundBook = book.copy(bookUrl = filePath, latestChapterTime = 0)
            appDb.runInTransaction {
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookDao.replace(book, reboundBook)
                BookHelp.updateCacheFolder(book, reboundBook)
            }
            // 新路径之前可能被测过大小或记为测不到，接回后按书籍失效
            LocalBookMetricInvalidation.notifyChanged(filePath)
            reboundBook
        }
}
