package io.legado.app.domain.usecase

import android.content.Context
import android.net.Uri
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ImportBookshelfUseCase(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository
) {
    suspend fun import(
        str: String,
        groupId: Long,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Unit> = kotlin.runCatching {
        val text = str.trim()
        when {
            text.isAbsUrl() -> {
                val downloadedText = okHttpClient.newCallResponseBody {
                    url(text)
                }.decompressed().text()
                import(downloadedText, groupId, onProgress).getOrThrow()
            }

            text.isJsonArray() -> {
                importByJson(text, groupId, onProgress)
            }

            else -> {
                throw NoStackTraceException("格式不对")
            }
        }
    }

    suspend fun import(
        uri: Uri,
        groupId: Long,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Unit> = kotlin.runCatching {
        val text = uri.readText(context)
        import(text, groupId, onProgress).getOrThrow()
    }

    private suspend fun importByJson(
        json: String,
        groupId: Long,
        onProgress: suspend (String) -> Unit
    ) {
        onProgress("导入中...")
        val bookSourceParts = bookSourceRepository.getAllEnabledPart()
        val semaphore = Semaphore(AppConfig.threadCount)
        val books = GSON.fromJsonArray<Map<String, String?>>(json).getOrThrow()

        withContext(Dispatchers.IO) {
            books.forEach { bookInfo ->
                val name = bookInfo["name"] ?: ""
                val author = bookInfo["author"] ?: ""
                if (name.isEmpty() || bookRepository.getBook(name, author) != null) {
                    return@forEach
                }
                semaphore.withPermit {
                    var foundBook: Book? = null
                    for (s in bookSourceParts) {
                        ensureActive()
                        val source = s.getBookSource() ?: continue
                        foundBook = WebBook.preciseSearchAwait(source, name, author).getOrNull()
                        if (foundBook != null) break
                    }
                    if (foundBook != null) {
                        val book = foundBook
                        if (groupId > 0) {
                            book.group = groupId
                        }
                        if (bookRepository.getBook(book.bookUrl) != null) {
                            bookRepository.update(book)
                        } else {
                            bookRepository.insert(book)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            context.toastOnUi("没有搜索到<$name>$author")
                        }
                    }
                }
            }
        }
    }
}
