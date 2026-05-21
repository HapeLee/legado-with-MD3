package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddBookUseCase(
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository
) {
    suspend fun execute(
        bookUrls: String,
        onProgress: suspend (Int) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        val urls = bookUrls.split("\n")
        val hasBookUrlPattern = bookSourceRepository.getHasBookUrlPattern()

        for (url in urls) {
            val bookUrl = url.trim()
            if (bookUrl.isEmpty()) continue
            if (bookRepository.getBook(bookUrl) != null) {
                successCount++
                onProgress(successCount)
                continue
            }
            val baseUrl = NetworkUtils.getBaseUrl(bookUrl) ?: continue
            var source = bookSourceRepository.getBookSourceAddBook(baseUrl)
            if (source == null) {
                for (bookSourcePart in hasBookUrlPattern) {
                    try {
                        val bs = bookSourcePart.getBookSource()!!
                        if (bookUrl.matches(bs.bookUrlPattern!!.toRegex())) {
                            source = bs
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            val bookSource = source ?: continue
            val book = Book(
                bookUrl = bookUrl,
                origin = bookSource.bookSourceUrl,
                originName = bookSource.bookSourceName
            )

            kotlin.runCatching {
                WebBook.getBookInfoAwait(bookSource, book)
            }.onSuccess {
                val dbBook = bookRepository.getBook(it.name, it.author)
                if (dbBook != null) {
                    val toc = WebBook.getChapterListAwait(bookSource, it).getOrThrow()
                    dbBook.migrateTo(it, toc)
                    bookRepository.insert(it)
                    bookRepository.insertChapters(*toc.toTypedArray())
                } else {
                    it.order = bookRepository.getMinOrder() - 1
                    bookRepository.insert(it)
                }
                successCount++
                onProgress(successCount)
            }
        }
        successCount
    }
}
