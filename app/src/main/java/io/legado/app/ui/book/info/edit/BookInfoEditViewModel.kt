package io.legado.app.ui.book.info.edit

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.removeType
import io.legado.app.model.ReadBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.inputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class BookInfoEditViewModel(application: Application) : BaseViewModel(application) {
    var book: Book? = null
    private val _bookData = MutableStateFlow<Book?>(null)
    val bookData: StateFlow<Book?> = _bookData.asStateFlow()

    fun loadBook(bookUrl: String) {
        execute {
            book = appDb.bookDao.getBook(bookUrl)
            book?.let {
                _bookData.value = it
            }
        }
    }

    fun saveBook(success: (() -> Unit)?) {
        execute {
            book?.let { book ->
                if (ReadBook.book?.bookUrl == book.bookUrl) {
                    ReadBook.book = book
                }
                appDb.bookDao.update(book)
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            if (it is SQLiteConstraintException) {
                AppLog.put("书籍信息保存失败，存在相同书名作者书籍\n$it", it, true)
            } else {
                AppLog.put("书籍信息保存失败\n$it", it, true)
            }
        }
    }

    fun updateBookState(
        name: String,
        author: String,
        selectedType: String,
        bookTypes: Array<String>,
        coverUrl: String?,
        intro: String?,
        remark: String?
    ) {
        book?.let { book ->
            val oldBook = book.copy()
            book.name = name
            book.author = author
            book.remark = remark
            val local = if (book.isLocal) BookType.local else 0
            val bookType = when (selectedType) {
                bookTypes[2] -> BookType.image or local
                bookTypes[1] -> BookType.audio or local
                else -> BookType.text or local
            }
            book.removeType(BookType.local, BookType.image, BookType.audio, BookType.text)
            book.addType(bookType)
            book.customCoverUrl = if (coverUrl == book.coverUrl) null else coverUrl
            book.customIntro = if (intro == book.intro) null else intro
            BookHelp.updateCacheFolder(oldBook, book)
        }
    }

    fun coverChangeTo(context: Context, uri: Uri, onFinally: (coverUrl: String) -> Unit) {
        execute {
            runCatching {
                context.externalCacheDir?.let { externalCacheDir ->
                    val file = File(externalCacheDir, "covers")
                    val suffix = context.contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
                    val fileName = uri.inputStream(context).getOrThrow().use { MD5Utils.md5Encode(it) } + ".$suffix"
                    val coverFile = FileUtils.createFileIfNotExist(file, fileName)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(coverFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    onFinally(coverFile.absolutePath)
                } ?: run {
                    AppLog.put("External cache directory is null", Throwable("Null directory"), true)
                }
            }.onFailure {
                AppLog.put("书籍封面保存失败\n$it", it, true)
            }
        }
    }


    fun updateCoverUrl(coverUrl: String) {
        book?.let {
            val updatedBook = it.copy(customCoverUrl = coverUrl)
            _bookData.value = updatedBook
        }
    }

}
