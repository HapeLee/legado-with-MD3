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
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
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

data class BookInfoEditUiState(
    val name: String = "",
    val author: String = "",
    val coverUrl: String? = null,
    val intro: String? = null,
    val remark: String? = null,
    val selectedType: String = "文本",
    val bookTypes: List<String> = listOf("文本", "音频", "图片"),
    val book: Book? = null,
)

class BookInfoEditViewModel(application: Application) : BaseViewModel(application) {
    var book: Book? = null
    private val _uiState = MutableStateFlow(BookInfoEditUiState())
    val uiState: StateFlow<BookInfoEditUiState> = _uiState.asStateFlow()

    fun loadBook(bookUrl: String) {
        execute {
            book = appDb.bookDao.getBook(bookUrl)
            book?.let {
                val selectedTypeIndex = when {
                    it.isImage -> 2
                    it.isAudio -> 1
                    else -> 0
                }
                _uiState.value = BookInfoEditUiState(
                    name = it.name,
                    author = it.author,
                    coverUrl = it.getDisplayCover(),
                    intro = it.getDisplayIntro(),
                    remark = it.remark,
                    selectedType = _uiState.value.bookTypes[selectedTypeIndex],
                    book = it
                )
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onAuthorChange(author: String) {
        _uiState.value = _uiState.value.copy(author = author)
    }

    fun onCoverUrlChange(coverUrl: String) {
        _uiState.value = _uiState.value.copy(coverUrl = coverUrl)
    }

    fun onIntroChange(intro: String) {
        _uiState.value = _uiState.value.copy(intro = intro)
    }

    fun onRemarkChange(remark: String) {
        _uiState.value = _uiState.value.copy(remark = remark)
    }

    fun onBookTypeChange(bookType: String) {
        _uiState.value = _uiState.value.copy(selectedType = bookType)
    }

    fun resetCover() {
        _uiState.value = _uiState.value.copy(coverUrl = book?.coverUrl ?: "")
    }

    fun save(onSuccess: () -> Unit) {
        execute {
            val currentState = _uiState.value
            book?.let { book ->
                val oldBook = book.copy()
                book.name = currentState.name
                book.author = currentState.author
                book.remark = currentState.remark
                val local = if (book.isLocal) BookType.local else 0
                val bookType = when (currentState.selectedType) {
                    currentState.bookTypes[2] -> BookType.image or local
                    currentState.bookTypes[1] -> BookType.audio or local
                    else -> BookType.text or local
                }
                book.removeType(BookType.local, BookType.image, BookType.audio, BookType.text)
                book.addType(bookType)
                book.customCoverUrl = if (currentState.coverUrl == book.coverUrl) null else currentState.coverUrl
                book.customIntro = if (currentState.intro == book.intro) null else currentState.intro
                BookHelp.updateCacheFolder(oldBook, book)

                if (ReadBook.book?.bookUrl == book.bookUrl) {
                    ReadBook.book = book
                }
                appDb.bookDao.update(book)
            }
        }.onSuccess {
            onSuccess.invoke()
        }.onError {
            if (it is SQLiteConstraintException) {
                AppLog.put("书籍信息保存失败，存在相同书名作者书籍\n$it", it, true)
            } else {
                AppLog.put("书籍信息保存失败\n$it", it, true)
            }
        }
    }

    fun coverChangeTo(context: Context, uri: Uri) {
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
                    _uiState.value = _uiState.value.copy(coverUrl = coverFile.absolutePath)
                } ?: run {
                    AppLog.put("External cache directory is null", Throwable("Null directory"), true)
                }
            }.onFailure {
                AppLog.put("书籍封面保存失败\n$it", it, true)
            }
        }
    }
}
