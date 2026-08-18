package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.ReadingProgress
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.mapParallelSafe
import io.legado.app.utils.postEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import java.io.FileNotFoundException
import kotlin.coroutines.coroutineContext

/**
 * 开书 / 目录加载 / 换源 / 进度同步域（R2.2 续批）。
 *
 * 从导航请求解析出书，装载目录与正文，处理本地文件缺失、换源（手动与自动）、
 * 以及与云端阅读进度的双向同步。
 *
 * **无自持状态**：唯一的状态是 `isInitFinish`（ReadView 首帧靠它放行前后章排版，
 * 见 `prepareCachedChapterFallback` 的说明），必须留在 [ReadBookUiState]。
 * 故与 [ReadConfigUpdateDelegate] 同形，读写经 [Host]。
 */
class ReadBookLoadDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val readSettingsRepository: ReadSettingsRepository,
    private val backupSettingsGateway: BackupSettingsGateway,
    private val changeSourceSettingsGateway: ChangeSourceSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val changeBookSourceUseCase: ChangeBookSourceUseCase,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val uploadReadingProgressUseCase: UploadReadingProgressUseCase,
) {

    interface Host {
        /** 刚 initData 完的一次性标记：此时不再弹进度冲突确认。 */
        var justInitData: Boolean

        fun setInitFinish()

        fun emitEffect(effect: ReadBookEffect)

        /** 云端进度比本地新，弹确认框。 */
        fun sureNewProgress(progress: BookProgress)

        /** 本地书文件读不到，去要书籍目录权限。 */
        fun requestBooksDirPicker(reloadChapterList: Boolean)

        /** 把 ReadPreferences 快照刷成最新，开书流程依赖它。 */
        suspend fun syncReadPreferencesSnapshot()

        fun openChapter(index: Int, durChapterPos: Int)

        suspend fun checkReadRecordAlias(book: Book)
    }

    private var changeSourceCoroutine: Coroutine<*>? = null

    suspend fun initReadBookConfig(request: ReadBookInitRequest) {
        val bookUrl = request.bookUrl
        val book = when {
            bookUrl.isNullOrEmpty() -> bookRepository.getLastReadBook()
            else -> bookRepository.getBook(bookUrl)
        } ?: return
        ReadBook.upReadBookConfig(book)
    }

    fun initData(request: ReadBookInitRequest, success: (() -> Unit)? = null) {
        Coroutine.async(scope, Dispatchers.IO) {
            host.syncReadPreferencesSnapshot()
            ReadBook.inBookshelf = request.inBookshelf
            ReadBook.chapterChanged = request.chapterChanged
            val bookUrl = request.bookUrl
            val book = when {
                bookUrl.isNullOrEmpty() -> bookRepository.getLastReadBook()
                else -> bookRepository.getBook(bookUrl)
            } ?: ReadBook.book
            when {
                book != null -> initBook(book)
                else -> {
                    ReadBook.upMsg(context.getString(R.string.no_book))
                    AppLog.put("未找到书籍\nbookUrl:$bookUrl")
                }
            }
            val index = request.chapterIndex
            val chapterPos = request.chapterPos
            if (index >= 0 && chapterPos >= 0) {
                ReadBook.saveCurrentBookProgress()
                host.openChapter(index, chapterPos)
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            val msg = "初始化数据失败\n${it.localizedMessage}"
            ReadBook.upMsg(msg)
            AppLog.put(msg, it)
        }.onFinally {
            ReadBook.saveRead()
        }
    }

    /** 换书/重装目录后重走开书流程。VM 的「模拟阅读切换」和目录权限回来后也调它。 */
    suspend fun initBook(book: Book) {
        val isSameBook = ReadBook.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            ReadBook.upData(book)
        } else {
            ReadBook.resetData(book)
        }
        host.setInitFinish()
        if (!book.isLocal && book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            return
        }
        if (book.isLocal && !checkLocalBookFileExist(book)) {
            return
        }
        if ((ReadBook.chapterSize == 0 || book.isLocalModified()) && !loadChapterListAwait(book)) {
            return
        }
        ReadBook.upMsg(null)
        host.checkReadRecordAlias(book)

        if (!isSameBook) {
            ReadBook.loadContent(resetPageOffset = true) {
                ReadBook.bookSource?.let {
                    SourceCallBack.callBackBook(
                        SourceCallBack.START_READ,
                        it,
                        book,
                        ReadBook.curTextChapter?.chapter
                    )
                }
            }
        } else {
            ReadBook.loadOrUpContent {
                ReadBook.bookSource?.let {
                    SourceCallBack.callBackBook(
                        SourceCallBack.START_READ,
                        it,
                        book,
                        ReadBook.curTextChapter?.chapter
                    )
                }
            }
        }
        if (ReadBook.chapterChanged) {
            ReadBook.chapterChanged = false
        } else if (!(isSameBook && BaseReadAloudService.isRun) && ReadBook.inBookshelf) {
            if (backupSettingsGateway.currentSettings.syncBookProgressPlus) {
                ReadBook.syncProgress({ progress -> host.sureNewProgress(progress) })
            } else {
                syncBookProgress(book)
            }
        }
        if (!book.isLocal && ReadBook.bookSource == null) {
            autoChangeSource(book.name, book.author)
            return
        }
    }

    private fun checkLocalBookFileExist(book: Book): Boolean {
        try {
            LocalBook.getBookInputStream(book)
            return true
        } catch (e: Throwable) {
            ReadBook.upMsg("打开本地书籍出错: ${e.localizedMessage}")
            if (e is SecurityException || e is FileNotFoundException) {
                host.requestBooksDirPicker(reloadChapterList = false)
            }
            return false
        }
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val source = ReadBook.bookSource ?: return true
        try {
            WebBook.getBookInfoAwait(source, book, canReName = false)
            return true
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
            ReadBook.upMsg("详情页出错: ${e.localizedMessage}")
            return false
        }
    }

    /** 重新拉目录。会话侧 `loadChapterList` 回调和 TOC 正则改动都走这里。 */
    fun doLoadChapterList(book: Book) {
        Coroutine.async(scope, Dispatchers.IO) {
            if (loadChapterListAwait(book)) {
                ReadBook.upMsg(null)
            }
        }
    }

    private suspend fun loadChapterListAwait(book: Book): Boolean {
        if (book.isLocal) {
            kotlin.runCatching {
                LocalBook.getChapterList(book).let {
                    bookRepository.replaceChaptersAndUpdateBook(book, it)
                    ReadBook.onChapterListUpdated(book)
                }
                return true
            }.onFailure {
                when (it) {
                    is SecurityException, is FileNotFoundException -> {
                        host.requestBooksDirPicker(reloadChapterList = true)
                    }
                    else -> {
                        AppLog.put("LoadTocError:${it.localizedMessage}", it)
                        ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                    }
                }
                return false
            }
        } else {
            ReadBook.bookSource?.let {
                val oldBook = book.copy()
                WebBook.getChapterListAwait(it, book, true)
                    .onSuccess { cList ->
                        if (oldBook.bookUrl == book.bookUrl) {
                            bookRepository.update(book)
                        } else {
                            bookRepository.replace(oldBook, book)
                            BookHelp.updateCacheFolder(oldBook, book)
                        }
                        bookRepository.deleteChaptersByBook(oldBook.bookUrl)
                        bookRepository.insertChapters(*cList.toTypedArray())
                        ReadBook.onChapterListUpdated(book)
                        return true
                    }.onFailure {
                        coroutineContext.ensureActive()
                        ReadBook.upMsg(context.getString(R.string.error_load_toc))
                        return false
                    }
            }
        }
        return true
    }

    fun syncBookProgress(
        book: Book,
        alertSync: ((progress: BookProgress) -> Unit)? = null
    ) {
        if (!backupSettingsGateway.currentSettings.syncBookProgress) return
        Coroutine.async(scope, Dispatchers.IO) {
            getReadingProgressUseCase.execute(book.name, book.author)?.toBookProgress()
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
                ReadBook.setProgress(progress)
                AppLog.put("自动同步阅读进度成功《${book.name}》 ${progress.durChapterTitle}")
            }
        }
    }

    fun isReadingProgressSyncConfigured(): Boolean {
        return getReadingProgressUseCase.isConfigured
    }

    suspend fun uploadBookProgress(book: Book) {
        uploadReadingProgressUseCase.execute(book.toReadingProgress())?.let { uploadTime ->
            book.syncTime = uploadTime
            bookRepository.update(book)
        }
    }

    private fun Book.toReadingProgress() = ReadingProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle
    )

    private fun ReadingProgress.toBookProgress() = BookProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle
    )

    fun changeTo(book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = Coroutine.async(scope, Dispatchers.IO) {
            ReadBook.upMsg(context.getString(R.string.loading))
            applyChangeSource(book, toc)
        }.onSuccess {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
            ReadBook.upMsg(null)
        }
    }

    fun changeTo(book: Book) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = Coroutine.async(scope, Dispatchers.IO) {
            ReadBook.upMsg(context.getString(R.string.loading))
            val source = bookSourceRepository.getBookSource(book.origin)
                ?: throw NoStackTraceException("书源不存在")
            if (book.tocUrl.isEmpty()) {
                WebBook.getBookInfoAwait(source, book)
            }
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
            applyChangeSource(book, toc)
        }.onSuccess {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
            ReadBook.upMsg(null)
        }
    }

    private suspend fun applyChangeSource(book: Book, toc: List<BookChapter>) {
        if (toc.isEmpty()) {
            throw NoStackTraceException("换源目录为空")
        }
        val oldBook = ReadBook.book ?: throw NoStackTraceException("书籍不存在")
        changeBookSourceUseCase.changeTo(
            oldBook = oldBook,
            newBook = book,
            chapters = toc,
            options = changeSourceSettingsGateway.currentSettings.migrationOptions(),
        )
        ReadBook.resetData(book)
        ReadBook.upMsg(null)
        ReadBook.loadContent(resetPageOffset = true)
    }

    private fun autoChangeSource(name: String, author: String) {
        if (!readSettingsRepository.currentSettings.autoChangeSource) return
        Coroutine.async(scope, Dispatchers.IO) {
            val sources = bookSourceRepository.getAllTextEnabledPart()
            flow {
                for (source in sources) {
                    source.getBookSource()?.let {
                        emit(it)
                    }
                }
            }.onStart {
                ReadBook.upMsg(context.getString(R.string.source_auto_changing))
            }.mapParallelSafe(downloadCacheSettingsGateway.currentSettings.threadCount) { source ->
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
                ReadBook.upMsg(null)
            }.catch {
                AppLog.put("自动换源失败\n${it.localizedMessage}", it)
                host.emitEffect(ReadBookEffect.ShowToast("自动换源失败\n${it.localizedMessage}"))
            }.collect()
        }
    }

}
