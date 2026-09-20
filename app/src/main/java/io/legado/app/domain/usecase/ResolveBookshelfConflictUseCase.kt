package io.legado.app.domain.usecase

import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.help.book.removeType
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 处理「加入书架时发现疑似同一作品」的两个方向。
 *
 * 与换源共用 [ChangeBookSourceUseCase]，保证同一组携带选项在换源、批量换源、
 * 加入书架冲突三个入口下含义一致。
 *
 * 阅读时长的处理是这个用例最容易被写错的地方：
 * - 共存：两个副本都留在书架，阅读会话按各自的 bookUrl 记账，互不相干，
 *   因此这里只搬运用户在选项里勾选的书籍字段，**不搬运任何阅读记录**；
 * - 迁移：等价于换源，旧书被删除，它的阅读会话整体平移到新副本（见
 *   [ChangeBookSourceUseCase.changeTo]），是平移而不是累加，不会产生双倍时长。
 */
/**
 * 迁移所需的目录拿不到。
 *
 * 迁移是破坏性操作（删旧书 + 重建目录 + 可能重置进度），因此目录不可用时必须中止而不是降级，
 * 由调用方向用户提示后让用户重试。
 */
class BookTocUnavailableException(
    val bookName: String,
    cause: Throwable? = null,
) : IllegalStateException("获取目录失败：$bookName", cause)

class ResolveBookshelfConflictUseCase(
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val changeBookSourceUseCase: ChangeBookSourceUseCase,
) {

    /**
     * 共存：新书入架，按 [options] 从已有作品复制勾选的数据。
     *
     * @param existingBookUrl 书架已有作品的 bookUrl
     * @param newBook 待加入的书（尚未入库）
     * @param chapters 调用方已加载的目录；为空时由本用例按书源拉取
     */
    suspend fun coexist(
        existingBookUrl: String,
        newBook: Book,
        options: ChangeSourceMigrationOptions,
        chapters: List<BookChapter>? = null,
    ): Book = withContext(Dispatchers.IO) {
        val existing = bookRepository.getBook(existingBookUrl)
        // 共存既不删旧书、也不重置进度，目录只是「新书暂时没有章节」；拉不到就降级继续，
        // 不要因为一次网络抖动挡住用户本来就很轻的共存选择。
        val resolvedChapters = chapters ?: runCatching { loadChapters(newBook) }
            .onFailure { AppLog.put("共存时获取目录失败，暂不写入目录", it, true) }
            .getOrDefault(emptyList())
        prepareNewBook(newBook)
        if (existing == null) {
            insertBook(newBook, resolvedChapters)
            return@withContext newBook
        }
        existing.copyMigratableFieldsTo(newBook, options)
        insertBook(newBook, resolvedChapters)
        newBook
    }

    /**
     * 迁移：用新书替换书架已有作品，语义等同于换源。
     *
     * @param chapters 调用方已加载的目录；为空时由本用例按书源拉取
     * @return 替换后留在书架的那本书
     */
    suspend fun migrate(
        existingBookUrl: String,
        newBook: Book,
        options: ChangeSourceMigrationOptions,
        chapters: List<BookChapter>? = null,
    ): Book = withContext(Dispatchers.IO) {
        val existing = bookRepository.getBook(existingBookUrl)
        val resolvedChapters = chapters ?: loadChapters(newBook)
        // 没有目录就替换：changeTo 会删掉旧书、又不插入任何章节，且进度会被重置到第一章。
        // 宁可中止让用户重试，也不能用一次网络抖动换掉整本书的进度。
        if (resolvedChapters.isEmpty()) {
            throw BookTocUnavailableException(newBook.name)
        }
        prepareNewBook(newBook)
        if (existing == null) {
            insertBook(newBook, resolvedChapters)
            return@withContext newBook
        }
        changeBookSourceUseCase.changeTo(
            oldBook = existing,
            newBook = newBook,
            chapters = resolvedChapters,
            options = options,
        )
        newBook
    }

    private fun prepareNewBook(newBook: Book) {
        newBook.removeType(BookType.notShelf)
    }

    /**
     * 载入待加入书籍的目录。
     *
     * 目录是迁移的必要条件而不是可选条件：`migrateInto` 在目录为空时会按「未勾选迁移进度」
     * 处理，把阅读进度重置到第一章；而迁移又会先删掉旧书。目录静默失败就等于用户丢了进度。
     * 因此这里失败即抛异常，由调用方提示并中止本次迁移；本地书没有书源，返回空目录。
     */
    private suspend fun loadChapters(newBook: Book): List<BookChapter> {
        val source = bookSourceRepository.getBookSource(newBook.origin) ?: return emptyList()
        if (newBook.tocUrl.isBlank()) {
            runCatching { WebBook.getBookInfoAwait(source, newBook) }
                .onFailure { AppLog.put("获取书籍详情出错\n${it.localizedMessage}", it, true) }
        }
        return WebBook.getChapterListAwait(source, newBook)
            .onFailure {
                AppLog.put("获取目录出错\n${it.localizedMessage}", it, true)
                throw BookTocUnavailableException(newBook.name, it)
            }
            .getOrThrow()
    }

    private suspend fun insertBook(newBook: Book, chapters: List<BookChapter>) {
        if (newBook.order == 0) {
            newBook.order = bookRepository.getMinOrder() - 1
        }
        bookRepository.insert(newBook)
        if (chapters.isNotEmpty()) {
            bookRepository.insertChapters(*chapters.toTypedArray())
        }
    }
}
