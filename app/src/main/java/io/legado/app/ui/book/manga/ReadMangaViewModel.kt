package io.legado.app.ui.book.manga

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Book.ReadConfig
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.mapParallelSafe
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.ByteArrayOutputStream

class ReadMangaViewModel(application: Application) : BaseViewModel(application) {

    private var changeSourceCoroutine: Coroutine<*>? = null

    /**
     * 初始化
     */
    fun initData(intent: Intent, success: (() -> Unit)? = null) {
        execute {
            ReadManga.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            ReadManga.chapterChanged = intent.getBooleanExtra("chapterChanged", false)
            val bookUrl = intent.getStringExtra("bookUrl")
            val book = when {
                bookUrl.isNullOrEmpty() -> appDb.bookDao.lastReadBook
                else -> appDb.bookDao.getBook(bookUrl)
            } ?: ReadManga.book
            when {
                book != null -> initManga(book)
                else -> context.getString(R.string.no_book)//没有找到书
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            val msg = "初始化数据失败\n${it.localizedMessage}"
            AppLog.put(msg, it)
        }.onFinally {
            ReadManga.saveRead()
        }
    }

    private suspend fun initManga(book: Book) {
        val isSameBook = ReadManga.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            ReadManga.upData(book)
        } else {
            ReadManga.resetData(book)
        }
        if (!book.isLocal && book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            return
        }

        if (book.isLocal && !checkLocalBookFileExist(book)) {
            return
        }

        if ((ReadManga.chapterSize == 0 || book.isLocalModified()) && !loadChapterListAwait(book)) {
            return
        }

        //开始加载内容
        if (!isSameBook) {
            ReadManga.loadContent()
        } else {
            ReadManga.loadOrUpContent()
        }

        if (ReadManga.chapterChanged) {
            // 有章节跳转不同步阅读进度
            ReadManga.chapterChanged = false
        } else if (!isSameBook) {
            if (AppConfig.syncBookProgressPlus) {
                ReadManga.syncProgress(
                    { progress -> ReadManga.mCallback?.sureNewProgress(progress) })
            } else {
                syncBookProgress(book)
            }
        }

        //自动换源
        if (!book.isLocal && ReadManga.bookSource == null) {
            autoChangeSource(book.name, book.author)
            return
        }
    }

    fun upBookSource(success: (() -> Unit)?) {
        execute {
            ReadManga.book?.let { book ->
                ReadManga.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun loadChapterList(book: Book) {
        execute {
            loadChapterListAwait(book)
        }
    }

    fun disableSource() {
        execute {
            ReadManga.bookSource?.let {
                it.enabled = false
                appDb.bookSourceDao.update(it)
            }
        }
    }

    private suspend fun loadChapterListAwait(book: Book): Boolean {
        ReadManga.bookSource?.let {
            val oldBook = book.copy()
            WebBook.getChapterListAwait(it, book, true).onSuccess { cList ->
                if (oldBook.bookUrl == book.bookUrl) {
                    appDb.bookDao.update(book)
                } else {
                    appDb.bookDao.replace(oldBook, book)
                    BookHelp.updateCacheFolder(oldBook, book)
                }
                appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                appDb.bookChapterDao.insert(*cList.toTypedArray())
                ReadManga.onChapterListUpdated(book)
                return true
            }.onFailure {
                //加载章节出错
                ReadManga.mCallback?.loadFail(appCtx.getString(R.string.error_load_toc))
                return false
            }
        }

        return true

    }

    /**
     * 加载详情页
     */
    private suspend fun loadBookInfo(book: Book): Boolean {
        val source = ReadManga.bookSource ?: return true
        try {
            WebBook.getBookInfoAwait(source, book, canReName = false)
            return true
        } catch (e: Throwable) {
            ReadManga.mCallback?.loadFail("详情页出错: ${e.localizedMessage}")
            return false
        }
    }

    /**
     * 自动换源
     */
    private fun autoChangeSource(name: String, author: String) {
        if (!AppConfig.autoChangeSource) return
        execute {
            val sources = appDb.bookSourceDao.allTextEnabledPart
            flow {
                for (source in sources) {
                    source.getBookSource()?.let {
                        emit(it)
                    }
                }
            }.onStart {
                // 自动换源

            }.mapParallelSafe(AppConfig.threadCount) { source ->
                val book = WebBook.preciseSearchAwait(source, name, author).getOrThrow()
                if (book.tocUrl.isEmpty()) {
                    WebBook.getBookInfoAwait(source, book)
                }
                val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
                val chapter = toc.getOrElse(book.durChapterIndex) {
                    toc.last()
                }
                val nextChapter = toc.getOrElse(chapter.index) {
                    toc.first()
                }
                WebBook.getContentAwait(
                    bookSource = source,
                    book = book,
                    bookChapter = chapter,
                    nextChapterUrl = nextChapter.url
                )
                book to toc
            }.take(1).onEach { (book, toc) ->
                changeTo(book, toc)
            }.onEmpty {
                throw NoStackTraceException("没有合适书源")
            }.onCompletion {
                // 换源完成
            }.catch {
                AppLog.put("自动换源失败\n${it.localizedMessage}", it)
                context.toastOnUi("自动换源失败\n${it.localizedMessage}")
            }.collect()
        }
    }

    /**
     * 同步进度
     */
    fun syncBookProgress(
        book: Book,
        alertSync: ((progress: BookProgress) -> Unit)? = null
    ) {
        if (!AppConfig.syncBookProgress) return
        execute {
            AppWebDav.getBookProgress(book)
        }.onError {
            AppLog.put("拉取阅读进度失败《${book.name}》\n${it.localizedMessage}", it)
        }.onSuccess { progress ->
            progress ?: return@onSuccess
            if (progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                alertSync?.invoke(progress)
            } else if (progress.durChapterIndex < book.simulatedTotalChapterNum()) {
                ReadManga.setProgress(progress)
                AppLog.put("自动同步阅读进度成功《${book.name}》 ${progress.durChapterTitle}")
            }
        }
    }

    /**
     * 换源
     */
    fun changeTo(book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            //换源中
            ReadManga.book?.migrateTo(book, toc)
            book.removeType(BookType.updateError)
            ReadManga.book?.delete()
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            ReadManga.resetData(book)
            ReadManga.loadContent()
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    private fun checkLocalBookFileExist(book: Book): Boolean {
        try {
            LocalBook.getBookInputStream(book)
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    fun openChapter(index: Int, durChapterPos: Int = 0) {
        if (index < ReadManga.chapterSize) {
            ReadManga.showLoading()
            ReadManga.durChapterIndex = index
            ReadManga.durChapterPos = durChapterPos
            ReadManga.saveRead()
            ReadManga.loadContent()
        }
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        val book = ReadManga.book
        Coroutine.async {
            book?.delete()
        }.onSuccess {
            success?.invoke()
        }
    }


    fun getEffectiveScrollMode(): Int {
        return ReadManga.book?.readConfig?.mangaScrollMode ?: AppConfig.mangaScrollMode
    }

    fun getEffectiveWebtoonSidePadding(): Int {
        return ReadManga.book?.readConfig?.webtoonSidePaddingDp ?: AppConfig.webtoonSidePaddingDp
    }

    fun saveImageToGallery(url: String, folderName: String = "Legado") {
        viewModelScope.launch {
            try {
                val byteArray = withContext(Dispatchers.IO) {
                    // 用 Glide 下载 Bitmap 转换成 ByteArray
                    val bitmap = Glide.with(getApplication<Application>().applicationContext)
                        .asBitmap()
                        .load(url)
                        .submit()
                        .get()
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                    baos.toByteArray()
                }

                val context = getApplication<Application>().applicationContext
                val success = ImageSaveUtils.saveImageToGallery(
                    context,
                    byteArray,
                    folderName = folderName
                )

                if (success) {
                    context.toastOnUi("图片已保存到相册")
                } else {
                    context.toastOnUi("保存失败")
                }
            } catch (e: Exception) {
                context.toastOnUi("保存出错: ${e.localizedMessage}")
            }
        }
    }


    fun updateReadConfig(update: ReadConfig.() -> Unit) {
        val book = ReadManga.book ?: return
        val config = book.readConfig ?: ReadConfig()
        config.update()
        book.readConfig = config

        execute {
            appDb.bookDao.update(book)
        }.onError {
            AppLog.put("更新阅读配置失败\n${it.localizedMessage}", it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        changeSourceCoroutine?.cancel()
    }

    fun refreshContentDur(book: Book) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadManga.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.delContent(book, chapter)
                    openChapter(ReadManga.durChapterIndex, ReadManga.durChapterPos)
                }
        }
    }

    fun addToBookshelf(book: Book, toc: List<BookChapter>, success: (() -> Unit)? = null) {
        execute {
            book.removeType(BookType.notShelf)
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }

            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
        }.onSuccess {
            success?.invoke()
        }.onError {
            AppLog.put("添加书籍到书架失败", it)
            context.toastOnUi("添加书籍失败")
        }
    }
}