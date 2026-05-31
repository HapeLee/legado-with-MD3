package io.legado.app.api.controller

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.CacheManager
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.cnCompare
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.File
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

object BookController {

    private lateinit var book: Book
    private var bookSource: BookSource? = null
    private var bookUrl: String = ""
    private val defaultCoverCache by lazy { WeakHashMap<Drawable, Bitmap>() }

    /**
     * 书架所有书籍
     */
    val bookshelf: ReturnData
        get() {
            val books = appDb.bookDao.all
            val returnData = ReturnData()
            return if (books.isEmpty()) {
                returnData.setErrorMsg("No books added yet")
            } else {
                val data = when (AppConfig.bookshelfSort) {
                    1 -> books.sortedByDescending { it.latestChapterTime }
                    2 -> books.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> books.sortedBy { it.order }
                    else -> books.sortedByDescending { it.durChapterTime }
                }
                returnData.setData(data)
            }
        }

    /**
     * 获取封面
     */
    fun getCover(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val coverPath = parameters["path"]?.firstOrNull()
        val ftBitmap = ImageLoader.loadBitmap(appCtx, coverPath)
            .override(84, 112)
            .centerCrop()
            .submit()
        return try {
            returnData.setData(ftBitmap.get(3, TimeUnit.SECONDS))
        } catch (e: Exception) {
            try {
                val defaultBitmap = defaultCoverCache.getOrPut(BookCover.defaultDrawable) {
                    Glide.with(appCtx)
                        .asBitmap()
                        .load(BookCover.defaultDrawable.toBitmap())
                        .override(84, 112)
                        .centerCrop()
                        .submit()
                        .get()
                }
                returnData.setData(defaultBitmap)
            } catch (e: Exception) {
                returnData.setErrorMsg(e.localizedMessage ?: "getCover error")
            }
        }
    }

    /**
     * 获取正文图片
     */
    fun getImg(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val bookUrl = parameters["url"]?.firstOrNull()
            ?: return returnData.setErrorMsg("bookUrl is empty")
        val src = parameters["path"]?.firstOrNull()
            ?: return returnData.setErrorMsg("Image link is empty")
        val width = parameters["width"]?.firstOrNull()?.toInt() ?: 640
        if (this.bookUrl != bookUrl) {
            this.book = appDb.bookDao.getBook(bookUrl)
                ?: return returnData.setErrorMsg("bookUrl is incorrect")
            this.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
        }
        this.bookUrl = bookUrl
        val bitmap = runBlocking {
            ImageProvider.cacheImage(book, src, bookSource)
            ImageProvider.getImage(book, src, width)
        }
        return returnData.setData(bitmap)
    }

    /**
     * 更新目录
     */
    fun refreshToc(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        try {
            val bookUrl = parameters["url"]?.firstOrNull()
            if (bookUrl.isNullOrEmpty()) {
                return returnData.setErrorMsg("Parameter url cannot be empty, please specify book URL")
            }
            val book = appDb.bookDao.getBook(bookUrl)
                ?: return returnData.setErrorMsg("Book not found in database, please add it first")
            if (book.isLocal) {
                val toc = LocalBook.getChapterList(book)
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                appDb.bookDao.update(book)
                return returnData.setData(toc)
            } else {
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: return returnData.setErrorMsg("No matching source found, please change source")
                val toc = runBlocking {
                    if (book.tocUrl.isBlank()) {
                        WebBook.getBookInfoAwait(bookSource, book)
                    }
                    WebBook.getChapterListAwait(bookSource, book).getOrThrow()
                }
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                appDb.bookDao.update(book)
                return returnData.setData(toc)
            }
        } catch (e: Exception) {
            return returnData.setErrorMsg(e.localizedMessage ?: "refresh toc error")
        }
    }

    /**
     * 获取目录
     */
    fun getChapterList(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("Parameter url cannot be empty, please specify book URL")
        }
        val chapterList = appDb.bookChapterDao.getChapterList(bookUrl)
        if (chapterList.isEmpty()) {
            return refreshToc(parameters)
        }
        return returnData.setData(chapterList)
    }

    /**
     * 获取正文
     */
    fun getBookContent(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val index = parameters["index"]?.firstOrNull()?.toInt()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("Parameter url cannot be empty, please specify book URL")
        }
        if (index == null) {
            return returnData.setErrorMsg("Parameter index cannot be empty, please specify chapter index")
        }
        val book = appDb.bookDao.getBook(bookUrl)
        val chapter = runBlocking {
            var chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
            var wait = 0
            while (chapter == null && wait < 30) {
                delay(1000)
                chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
                wait++
            }
            chapter
        }
        if (book == null || chapter == null) {
            return returnData.setErrorMsg("Not found")
        }
        var content: String? = BookHelp.getContent(book, chapter)
        if (content != null) {
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            content = runBlocking {
                contentProcessor.getContent(book, chapter, content, includeTitle = false)
                    .toString()
            }
            return returnData.setData(content)
        }
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            ?: return returnData.setErrorMsg("No source found")
        try {
            content = runBlocking {
                WebBook.getContentAwait(bookSource, book, chapter).let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    contentProcessor.getContent(book, chapter, it, includeTitle = false)
                        .toString()
                }
            }
            returnData.setData(content)
        } catch (e: Exception) {
            returnData.setErrorMsg(e.stackTraceStr)
        }
        return returnData
    }

    /**
     * 保存书籍
     */
    suspend fun saveBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            AppWebDav.uploadBookProgress(book)
            book.save()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("Invalid format")
    }

    /**
     * 删除书籍
     */
    fun deleteBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            book.delete()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("Invalid format")
    }

    /**
     * 保存进度
     */
    suspend fun saveBookProgress(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<BookProgress>(postData)
            .onFailure { it.printOnDebug() }
            .getOrNull()?.let { bookProgress ->
                appDb.bookDao.getBook(bookProgress.name, bookProgress.author)?.let { book ->
                    book.durChapterIndex = bookProgress.durChapterIndex
                    book.durChapterPos = bookProgress.durChapterPos
                    book.durChapterTitle = bookProgress.durChapterTitle
                    book.durChapterTime = bookProgress.durChapterTime
                    AppWebDav.uploadBookProgress(bookProgress) {
                        book.syncTime = System.currentTimeMillis()
                    }
                    appDb.bookDao.update(book)
                    ReadBook.book?.let {
                        if (it.name == bookProgress.name &&
                            it.author == bookProgress.author
                        ) {
                            ReadBook.webBookProgress = bookProgress
                        }
                    }
                    return returnData.setData("")
                }
            }
        return returnData.setErrorMsg("Invalid format")
    }

    /**
     * 添加本地书籍
     */
    fun addLocalBook(
        parameters: Map<String, List<String>>,
        files: Map<String, String>
    ): ReturnData {
        val returnData = ReturnData()
        val fileName = parameters["fileName"]?.firstOrNull()
            ?: return returnData.setErrorMsg("fileName cannot be empty")
        val fileData = files["fileData"]
            ?: return returnData.setErrorMsg("fileData cannot be empty")
        kotlin.runCatching {
            val uri = LocalBook.saveBookFile(File(fileData).inputStream(), fileName)
            LocalBook.importFile(uri)
        }.onFailure {
            return when (it) {
                is SecurityException -> returnData.setErrorMsg("Need to reset book save location!")
                else -> returnData.setErrorMsg("Error saving book\n${it.localizedMessage}")
            }
        }
        return returnData.setData(true)
    }

    /**
     * 保存web阅读界面配置
     */
    fun saveWebReadConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData?.let {
            CacheManager.put("webReadConfig", postData)
        } ?: CacheManager.delete("webReadConfig")
        return returnData.setData("")
    }

    /**
     * 获取web阅读界面配置
     */
    fun getWebReadConfig(): ReturnData {
        val returnData = ReturnData()
        val data = CacheManager.get("webReadConfig")
            ?: return returnData.setErrorMsg("No configuration")
        return returnData.setData(data)
    }

}
