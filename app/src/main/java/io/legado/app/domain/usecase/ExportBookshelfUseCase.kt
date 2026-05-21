package io.legado.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.google.gson.stream.JsonWriter
import io.legado.app.data.repository.BookRepository
import io.legado.app.ui.main.bookshelf.BookUiItem
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class ExportBookshelfUseCase(
    private val context: Context,
    private val bookRepository: BookRepository
) {
    suspend fun exportToUri(uri: Uri, items: List<BookUiItem>): Result<Unit> =
        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
                    writer.setIndent("  ")
                    writer.beginArray()
                    items.forEach {
                        val bookMap = hashMapOf<String, String?>()
                        bookMap["name"] = it.book.name
                        bookMap["author"] = it.book.author
                        val fullBook = bookRepository.getBook(it.book.bookUrl)
                        bookMap["intro"] = fullBook?.getDisplayIntro()
                        GSON.toJson(bookMap, bookMap::class.java, writer)
                    }
                    writer.endArray()
                    writer.close()
                } ?: throw Exception("Failed to open output stream")
            }
        }

    suspend fun exportToFile(items: List<BookUiItem>): Result<File> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val path = "${context.filesDir}/books.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            FileOutputStream(file).use { out ->
                val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
                writer.setIndent("  ")
                writer.beginArray()
                items.forEach {
                    val bookMap = hashMapOf<String, String?>()
                    bookMap["name"] = it.book.name
                    bookMap["author"] = it.book.author
                    val fullBook = bookRepository.getBook(it.book.bookUrl)
                    bookMap["intro"] = fullBook?.getDisplayIntro()
                    GSON.toJson(bookMap, bookMap::class.java, writer)
                }
                writer.endArray()
                writer.close()
            }
            file
        }
    }

    suspend fun exportToJson(items: List<BookUiItem>): Result<String> =
        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val list = items.map {
                    val bookMap = hashMapOf<String, String?>()
                    bookMap["name"] = it.book.name
                    bookMap["author"] = it.book.author
                    val fullBook = bookRepository.getBook(it.book.bookUrl)
                    bookMap["intro"] = fullBook?.getDisplayIntro()
                    bookMap
                }
                GSON.toJson(list)
            }
        }
}
