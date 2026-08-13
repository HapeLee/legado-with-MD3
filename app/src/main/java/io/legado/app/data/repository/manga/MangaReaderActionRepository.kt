package io.legado.app.data.repository.manga

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Book.ReadConfig
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.NoStackTraceException
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.help.coil.CoverExtras
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.removeType
import io.legado.app.help.source.getSourceType
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.postEvent
import kotlinx.coroutines.currentCoroutineContext
import java.io.ByteArrayOutputStream

sealed interface MangaReaderActionPaymentResult {
    data class OpenUrl(
        val url: String,
        val sourceOrigin: String?,
        val sourceName: String?,
        val sourceType: Int?,
    ) : MangaReaderActionPaymentResult

    data object Refreshed : MangaReaderActionPaymentResult
}

/** One-shot reader operations. Every operation receives explicit session identity. */
class MangaReaderActionRepository(
    private val application: Application,
    private val database: AppDatabase,
    private val imageLoader: ImageLoader,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
) {
    suspend fun getBook(bookUrl: String): Book? = database.bookDao.getBook(bookUrl)

    suspend fun refreshSource(sourceOrigin: String?) =
        sourceOrigin?.let { database.bookSourceDao.getBookSource(it) }

    suspend fun disableSource(sourceOrigin: String?) {
        val source = sourceOrigin?.let { database.bookSourceDao.getBookSource(it) } ?: return
        source.enabled = false
        database.bookSourceDao.update(source)
    }

    suspend fun changeSource(currentBookUrl: String, book: Book, toc: List<BookChapter>) {
        database.bookDao.getBook(currentBookUrl)?.migrateTo(
            newBook = book,
            toc = toc,
            defaultReplaceEnabled = otherSettingsGateway.currentSettings.replaceEnableDefault,
            chineseConverterType = readSettingsGateway.currentSettings.chineseConverterType,
        )
        book.removeType(BookType.updateError)
        database.bookDao.getBook(currentBookUrl)?.delete()
        database.bookDao.insert(book)
        database.bookChapterDao.insert(*toc.toTypedArray())
        postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
    }

    suspend fun removeTemporaryBook(bookUrl: String) {
        database.bookDao.getBook(bookUrl)?.delete()
    }

    suspend fun addCurrentBookToShelf(bookUrl: String) {
        val book = database.bookDao.getBook(bookUrl)
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        persistOnShelf(book, database.bookChapterDao.getChapterList(book.bookUrl))
    }

    suspend fun addToShelf(book: Book, toc: List<BookChapter>) = persistOnShelf(book, toc)

    private suspend fun persistOnShelf(book: Book, toc: List<BookChapter>) {
        book.removeType(BookType.notShelf)
        if (book.order == 0) book.order = database.bookDao.minOrder - 1
        database.bookDao.insert(book)
        database.bookChapterDao.insert(*toc.toTypedArray())
    }

    suspend fun updateReadConfig(bookUrl: String, update: ReadConfig.() -> Unit) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        book.readConfig = (book.readConfig ?: ReadConfig()).apply(update)
        database.bookDao.update(book)
    }

    suspend fun invalidateChapter(bookUrl: String, chapterIndex: Int) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        database.bookChapterDao.getChapter(bookUrl, chapterIndex)?.let {
            BookHelp.delContent(book, it)
        }
    }

    suspend fun saveImage(
        url: String,
        bookUrl: String,
        sourceOrigin: String?,
        folderName: String = "Legado",
    ): Boolean {
        val result = imageLoader.execute(
            ImageRequest.Builder(application)
                .data(url)
                .allowHardware(false)
                .apply {
                    extras[CoverExtras.Manga] = true
                    extras[CoverExtras.MangaBookUrl] = bookUrl
                    extras[CoverExtras.SourceOrigin] = sourceOrigin
                }
                .build()
        )
        val bitmap = requireNotNull(result.image).toBitmap()
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            output.toByteArray()
        }
        return ImageSaveUtils.saveImageToGallery(application, bytes, folderName = folderName)
    }

    suspend fun payCurrentChapter(
        bookUrl: String,
        chapterIndex: Int,
    ): MangaReaderActionPaymentResult {
        val book = database.bookDao.getBook(bookUrl)
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        if (book.isLocal) throw NoStackTraceException("local book")
        val chapter = database.bookChapterDao.getChapter(bookUrl, chapterIndex)
            ?: throw NoStackTraceException("no chapter")
        val source = database.bookSourceDao.getBookSource(book.origin)
            ?: throw NoStackTraceException("no book source")
        val payAction = source.getContentRule().payAction
        if (payAction.isNullOrBlank()) throw NoStackTraceException("no pay action")
        val analyzeRule = AnalyzeRule(book, source).apply {
            setCoroutineContext(currentCoroutineContext())
            setBaseUrl(chapter.url)
            setChapter(chapter)
        }
        val result = analyzeRule.evalJS(payAction).toString()
        return when {
            result.isAbsUrl() -> MangaReaderActionPaymentResult.OpenUrl(
                result, source.bookSourceUrl, source.bookSourceName, source.getSourceType(),
            )
            result.isTrue() -> {
                BookHelp.delContent(book, chapter)
                val chapters = WebBook.getChapterListAwait(source, book, true).getOrThrow()
                database.bookChapterDao.delByBook(book.bookUrl)
                database.bookChapterDao.insert(*chapters.toTypedArray())
                MangaReaderActionPaymentResult.Refreshed
            }
            else -> throw NoStackTraceException("pay action returned $result")
        }
    }
}
