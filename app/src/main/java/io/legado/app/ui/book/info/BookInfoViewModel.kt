package io.legado.app.ui.book.info

import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.RemoteBookRepository
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.exception.NoBooksDirException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.book.updateTo
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.SourceCallBack
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookInfoViewModel(
    application: Application,
    private val remoteBookRepository: RemoteBookRepository,
    private val readRecordRepository: ReadRecordRepository,
    private val changeBookSourceUseCase: ChangeBookSourceUseCase,
    private val clearBookCacheUseCase: ClearBookCacheUseCase
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(BookInfoUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookInfoEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var currentBook: Book? = null
        set(value) {
            field = value
            observeReadRecordIfNeeded(value)
        }
    private var currentChapterList: List<BookChapter> = emptyList()
    private var currentWebFiles: List<BookInfoWebFile> = emptyList()
    private var currentKindLabels: List<String> = emptyList()
    private var currentGroupNames: String? = null
    private var currentHasCustomGroup = false
    private var currentReadRecordTotalTime = 0L
    private var currentReadRecordTimelineDays: List<ReadRecordTimelineDay> = emptyList()
    private var observingReadRecordKey: String? = null
    private var chapterChanged = false

    var inBookshelf = false
        private set
    var bookSource: BookSource? = null
        private set

    private var changeSourceCoroutine: Coroutine<*>? = null
    private var readRecordObserveJob: Job? = null

    fun initData(intent: Intent) {
        initData(intent.getStringExtra("bookUrl") ?: "")
    }

    fun initData(bookUrl: String) {
        if (currentBook?.bookUrl == bookUrl) return
        currentBook = null
        currentChapterList = emptyList()
        currentWebFiles = emptyList()
        currentKindLabels = emptyList()
        currentGroupNames = null
        currentHasCustomGroup = false
        inBookshelf = false
        bookSource = null
        chapterChanged = false
        clearReadRecordObserve()
        _uiState.value = BookInfoUiState()
        execute {
            appDb.bookDao.getBook(bookUrl)?.let {
                inBookshelf = !it.isNotShelf
                return@execute it
            }
            appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()?.let {
                inBookshelf = false
                return@execute it
            }
            throw NoStackTraceException("未找到书籍")
        }.onSuccess {
            upBook(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "未找到书籍")
            emitEffect(BookInfoEffect.Finish(afterTransition = true))
        }
    }

    fun onIntent(intent: BookInfoIntent) {
        when (intent) {
            BookInfoIntent.BackPressed -> onBackPressed()
            BookInfoIntent.DismissSheet -> dismissSheet()
            BookInfoIntent.DismissDialog -> dismissDialog()
            is BookInfoIntent.MenuAction -> handleMenuAction(intent.action)
            is BookInfoIntent.AuthorClick -> onAuthorClick(intent.longClick)
            is BookInfoIntent.BookNameClick -> onBookNameClick(intent.longClick)
            BookInfoIntent.OriginClick -> onOriginClick()
            BookInfoIntent.DismissAppLogSheet -> {
                _uiState.update { it.copy(showAppLogSheet = false) }
            }

            BookInfoIntent.ReadClick -> onReadClick()
            BookInfoIntent.ShelfClick -> onShelfClick()
            BookInfoIntent.TocClick -> onTocClick()
            BookInfoIntent.CoverClick -> setSheet(BookInfoSheet.CoverPicker)
            BookInfoIntent.CoverLongClick -> currentBook?.getDisplayCover()?.takeIf { it.isNotBlank() }
                ?.let { showDialog(BookInfoDialog.PhotoPreview(it)) }

            BookInfoIntent.GroupClick -> setSheet(BookInfoSheet.GroupPicker)
            BookInfoIntent.ChangeSourceClick -> setSheet(BookInfoSheet.SourcePicker)
            BookInfoIntent.ReadRecordClick -> setSheet(BookInfoSheet.ReadRecord)
            BookInfoIntent.RemarkClick -> showDialog(BookInfoDialog.EditRemark(currentBook?.remark))
            BookInfoIntent.ConfirmBackAddToShelf -> {
                dismissDialog()
                addToBookshelf {
                    emitEffect(BookInfoEffect.Finish(afterTransition = true))
                }
            }

            is BookInfoIntent.ConfirmDelete -> {
                dismissDialog()
                deleteBook(intent.deleteOriginal)
            }

            is BookInfoIntent.UpdateRemark -> {
                dismissDialog()
                saveRemark(intent.remark)
            }

            is BookInfoIntent.SelectGroup -> {
                dismissSheet()
                updateGroup(intent.groupId)
            }

            is BookInfoIntent.SelectCover -> {
                dismissSheet()
                updateCover(intent.coverUrl)
            }

            is BookInfoIntent.ReplaceWithSource -> {
                dismissSheet()
                changeTo(intent.source, intent.book, intent.toc, intent.options)
            }

            is BookInfoIntent.AddSourceAsNewBook -> {
                addToBookshelf(intent.book, intent.toc) {
                    context.toastOnUi("已添加到书架")
                }
            }

            is BookInfoIntent.SelectWebFile -> handleWebFileSelection(
                intent.webFile,
                intent.openAfterImport
            )

            is BookInfoIntent.OpenUnsupportedWebFile -> {
                dismissDialog()
                importOrDownloadWebFile<Uri>(intent.webFile) { uri ->
                    emitEffect(BookInfoEffect.OpenFile(uri, "*/*"))
                }
            }

            is BookInfoIntent.SelectArchiveEntry -> {
                dismissSheet()
                importArchiveBook(intent.archiveUri, intent.entryName) { book ->
                    if (intent.openAfterImport) {
                        openReader(book)
                    }
                }
            }
        }
    }

    fun openEdit() {
        currentBook?.let {
            emitEffect(BookInfoEffect.OpenBookInfoEdit(it.bookUrl))
        }
    }

    fun showAppLog() {
        _uiState.update { it.copy(showAppLogSheet = true) }
    }

    fun refreshCurrentBook() {
        currentBook?.let {
            refreshBook(it)
        }
    }

    fun onSourceEdited() {
        currentBook?.let { book ->
            bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            syncUiState()
            refreshBook(book)
        }
    }

    fun onInfoEdited() {
        currentBook?.bookUrl?.let { bookUrl ->
            appDb.bookDao.getBook(bookUrl)?.let { upBook(it) }
        }
    }

    fun onTocResult(result: Triple<Int, Int, Boolean>?) {
        if (result == null) {
            if (!inBookshelf) {
                delBook()
            }
            return
        }
        chapterChanged = result.third
        val book = currentBook ?: return
        execute {
            book.durChapterIndex = result.first
            book.durChapterPos = result.second
            appDb.bookDao.update(book)
            book
        }.onSuccess {
            currentBook = it
            syncUiState(isTocLoading = false)
            openReader(it)
        }
    }

    fun onReaderResult(resultCode: Int) {
        when (resultCode) {
            RESULT_OK -> {
                inBookshelf = true
                syncUiState()
            }

            io.legado.app.ui.book.read.ReadBookActivity.RESULT_DELETED -> {
                emitEffect(BookInfoEffect.Finish(resultCode = RESULT_OK))
            }
        }
    }

    fun toggleCanUpdate() {
        currentBook?.let { book ->
            book.canUpdate = !book.canUpdate
            if (inBookshelf) {
                if (!book.canUpdate) {
                    book.removeType(BookType.updateError)
                }
                saveBook(book)
            }
            syncUiState()
        }
    }

    fun toggleSplitLongChapter() {
        currentBook?.takeIf { it.isLocal && it.type and BookType.text > 0 }?.let { book ->
            book.setSplitLongChapter(!book.getSplitLongChapter())
            syncUiState(isTocLoading = true)
            loadBookInfo(book, canReName = false)
            if (!book.getSplitLongChapter()) {
                context.toastOnUi(context.getString(R.string.need_more_time_load_content))
            }
        }
    }

    fun toggleDeleteAlert() {
        LocalConfig.bookInfoDeleteAlert = !LocalConfig.bookInfoDeleteAlert
        syncUiState()
    }

    fun requestSourceVariableDialog() {
        execute {
            val source = bookSource ?: throw NoStackTraceException("书源不存在")
            val comment = source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = source.getVariable()
            BookInfoEffect.ShowVariableDialog(
                title = context.getString(R.string.set_source_variable),
                key = source.getKey(),
                variable = variable,
                comment = comment,
            )
        }.onSuccess {
            emitEffect(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "书源不存在")
        }
    }

    fun requestBookVariableDialog() {
        execute {
            val source = bookSource ?: throw NoStackTraceException("书源不存在")
            val book = currentBook ?: throw NoStackTraceException("book is null")
            val variable = book.getCustomVariable()
            val comment = source.getDisplayVariableComment(
                "书籍变量可在js中通过book.getVariable(\"custom\")获取"
            )
            BookInfoEffect.ShowVariableDialog(
                title = context.getString(R.string.set_book_variable),
                key = book.bookUrl,
                variable = variable,
                comment = comment,
            )
        }.onSuccess {
            emitEffect(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "书源不存在")
        }
    }

    fun setVariable(key: String, variable: String?) {
        when (key) {
            bookSource?.getKey() -> bookSource?.setVariable(variable)
            currentBook?.bookUrl -> currentBook?.let {
                it.putCustomVariable(variable)
                if (inBookshelf) {
                    saveBook(it)
                }
            }
        }
    }

    fun topBook() {
        currentBook?.let { book ->
            execute {
                val minOrder = appDb.bookDao.minOrder
                book.order = minOrder - 1
                book.durChapterTime = System.currentTimeMillis()
                appDb.bookDao.update(book)
                book
            }.onSuccess {
                currentBook = it
                syncUiState()
            }
        }
    }
    fun syncFromRemote() {
        val book = currentBook ?: return
        if (!book.isLocal) return

        execute {
            setBusy(true)
            val newBook = remoteBookRepository.syncBookFromRemote(book)
            appDb.bookDao.delete(book)
            appDb.bookDao.insert(newBook)
            newBook
        }.onSuccess { newBook ->
            currentBook = newBook
            inBookshelf = true
            syncUiState(isTocLoading = true)
            loadChapter(newBook)
            context.toastOnUi("同步完成")
        }.onFinally {
            setBusy(false)
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun uploadBook(success: () -> Unit) {
        val book = currentBook ?: return
        execute {
            setBusy(true)
            remoteBookRepository.uploadBook(book)
            saveBook(book)
        }.onSuccess {
            success.invoke()
        }.onFinally {
            setBusy(false)
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun clearCache() {
        currentBook?.let { book ->
            execute {
                clearBookCacheUseCase.execute(book.bookUrl)
                if (ReadBook.book?.bookUrl == book.bookUrl) {
                    ReadBook.clearTextChapter()
                }
                if (ReadManga.book?.bookUrl == book.bookUrl) {
                    ReadManga.clearMangaChapter()
                }
            }.onSuccess {
                context.toastOnUi(R.string.clear_cache_success)
            }.onError {
                context.toastOnUi("清理缓存出错\n${it.localizedMessage}")
            }
        }
    }

    fun saveRemark(remark: String, success: (() -> Unit)? = null) {
        currentBook?.let { book ->
            execute {
                book.remark = remark
                book.save()
                book
            }.onSuccess {
                currentBook = it
                syncUiState()
                success?.invoke()
            }
        }
    }

    fun saveBook(book: Book?, success: (() -> Unit)? = null) {
        book ?: return
        execute {
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.getBook(book.name, book.author)?.let {
                book.durChapterIndex = it.durChapterIndex
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            book.save()
            if (ReadBook.book?.isSameNameAuthor(book) == true) {
                ReadBook.book = book
            } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                AudioPlay.book = book
            }
            book
        }.onSuccess {
            if (currentBook?.bookUrl == it.bookUrl) {
                currentBook = it
                syncUiState()
            }
            success?.invoke()
        }
    }

    fun saveChapterList(success: (() -> Unit)? = null) {
        execute {
            appDb.bookChapterDao.insert(*currentChapterList.toTypedArray())
        }.onSuccess {
            success?.invoke()
        }
    }

    fun addToBookshelf(success: (() -> Unit)? = null) {
        val book = currentBook ?: return
        execute {
            book.removeType(BookType.notShelf)
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.getBook(book.name, book.author)?.let {
                book.durChapterIndex = it.durChapterIndex
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            if (ReadBook.book?.isSameNameAuthor(book) == true) {
                ReadBook.book = book
            } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                AudioPlay.book = book
            }
            book.save()
            SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, bookSource, book)
            appDb.bookChapterDao.insert(*currentChapterList.toTypedArray())
            book
        }.onSuccess {
            currentBook = it
            inBookshelf = true
            syncUiState()
            success?.invoke()
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
            book
        }.onSuccess {
            if (currentBook?.bookUrl == it.bookUrl) {
                currentBook = it
                currentChapterList = toc
                inBookshelf = true
                syncUiState(isTocLoading = false)
            }
            success?.invoke()
        }.onError {
            AppLog.put("添加书籍到书架失败", it)
            context.toastOnUi("添加书籍失败")
        }
    }

    fun delBook(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        val book = currentBook ?: return
        execute {
            inBookshelf = false
            if (book.isLocal) {
                LocalBook.deleteBook(book, deleteOriginal)
            }
            book.delete()
        }.onSuccess {
            success?.invoke()
        }
    }

    fun refreshBook(book: Book) {
        syncUiState(isTocLoading = true)
        execute {
            if (book.isLocal) {
                book.tocUrl = ""
                remoteBookRepository.refreshLocalBook(book)
            } else {
                val bs = bookSource ?: return@execute
                if (book.originName != bs.bookSourceName) {
                    book.originName = bs.bookSourceName
                }
            }
            book
        }.onError {
            when (it) {
                is ObjectNotFoundException -> {
                    book.origin = BookType.localTag
                }

                else -> {
                    AppLog.put("下载远程书籍<${book.name}>失败", it)
                }
            }
        }.onFinally {
            loadBookInfo(book, canReName = false)
        }
    }

    fun loadBookInfo(
        book: Book,
        canReName: Boolean = true,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope,
    ) {
        syncUiState(isTocLoading = true)
        if (book.isLocal) {
            LocalBook.upBookInfo(book)
            currentBook = book
            syncUiState(isTocLoading = true)
            loadChapter(book)
        } else {
            val source = bookSource ?: run {
                currentChapterList = emptyList()
                syncUiState(isTocLoading = false)
                context.toastOnUi(R.string.error_no_source)
                return
            }
            WebBook.getBookInfo(scope, source, book, canReName = canReName)
                .onSuccess(IO) { loadedBook ->
                    val dbBook = appDb.bookDao.getBook(loadedBook.name, loadedBook.author)
                    if (!inBookshelf && dbBook != null && !dbBook.isNotShelf && dbBook.origin == loadedBook.origin) {
                        dbBook.updateTo(loadedBook)
                        inBookshelf = true
                    }
                    currentBook = loadedBook
                    if (inBookshelf) {
                        loadedBook.save()
                    }
                    syncUiState(isTocLoading = true)
                    refreshMeta(loadedBook)
                    if (loadedBook.isWebFile) {
                        loadWebFile(loadedBook)
                        currentChapterList = emptyList()
                        syncUiState(isTocLoading = false)
                    } else {
                        loadChapter(loadedBook, runPreUpdateJs)
                    }
                }.onError {
                    AppLog.put("获取书籍信息失败\n${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_book_info)
                    syncUiState(isTocLoading = false)
                }
        }
    }
    fun changeTo(
        source: BookSource,
        book: Book,
        toc: List<BookChapter>,
        options: ChangeSourceMigrationOptions,
    ) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            val oldBook = currentBook ?: return@execute book
            bookSource = source
            if (inBookshelf) {
                changeBookSourceUseCase.changeTo(oldBook, book, toc, options)
            } else {
                changeBookSourceUseCase.applyMigration(oldBook, book, toc, options)
            }
            book
        }.onSuccess {
            currentBook = it
            currentChapterList = toc
            currentGroupNames = null
            currentHasCustomGroup = false
            currentKindLabels = emptyList()
            syncUiState(isTocLoading = false)
            refreshMeta(it)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    private fun upBook(book: Book) {
        currentBook = book
        currentChapterList = emptyList()
        currentWebFiles = emptyList()
        currentKindLabels = emptyList()
        currentGroupNames = null
        currentHasCustomGroup = false
        syncUiState(isTocLoading = true)
        refreshMeta(book)
        upCoverByRule(book)
        bookSource = if (book.isLocal) null else appDb.bookSourceDao.getBookSource(book.origin)
        syncUiState(isTocLoading = true)
        if (book.tocUrl.isEmpty() && !book.isLocal) {
            loadBookInfo(book, runPreUpdateJs = inBookshelf)
        } else {
            execute {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }.onSuccess { chapters ->
                if (chapters.isNotEmpty()) {
                    currentChapterList = chapters
                    syncUiState(isTocLoading = false)
                } else {
                    loadChapter(book)
                }
            }.onError {
                loadChapter(book)
            }
        }
    }

    private fun upCoverByRule(book: Book) {
        execute {
            if (book.coverUrl.isNullOrBlank() && book.customCoverUrl.isNullOrBlank()) {
                val coverUrl = BookCover.searchCover(book)
                if (!coverUrl.isNullOrBlank()) {
                    book.customCoverUrl = coverUrl
                    if (inBookshelf) {
                        saveBook(book)
                    }
                }
            }
            book
        }.onSuccess {
            if (currentBook?.bookUrl == it.bookUrl) {
                currentBook = it
                syncUiState()
            }
        }
    }

    private fun refreshMeta(book: Book) {
        execute {
            val kinds = book.getKindList().toMutableList()
            if (book.isLocal) {
                val size = FileDoc.fromFile(book.bookUrl).size
                if (size > 0) {
                    kinds.add(ConvertUtils.formatFileSize(size))
                }
            }
            val userGroupIds = appDb.bookGroupDao.idsSum
            val groupAnd = userGroupIds and book.group
            val hasCustomGroup = book.group > 0L && groupAnd != 0L
            val groupNames = appDb.bookGroupDao.getGroupNames(book.group).joinToString(",")
            val normalizedGroupNames = groupNames.ifBlank { null }
            Triple(kinds.toList(), normalizedGroupNames, hasCustomGroup)
        }.onSuccess {
            currentKindLabels = it.first
            currentGroupNames = it.second
            currentHasCustomGroup = it.third
            syncUiState()
        }
    }

    private fun loadChapter(
        book: Book,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope,
    ) {
        syncUiState(isTocLoading = true)
        if (book.isLocal) {
            execute(scope) {
                LocalBook.getChapterList(book).also {
                    appDb.bookDao.update(book)
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    ReadBook.onChapterListUpdated(book)
                }
            }.onSuccess {
                currentBook = book
                currentChapterList = it
                syncUiState(isTocLoading = false)
            }.onError {
                currentChapterList = emptyList()
                syncUiState(isTocLoading = false)
                context.toastOnUi("LoadTocError:${it.localizedMessage}")
            }
        } else {
            val source = bookSource ?: run {
                currentChapterList = emptyList()
                syncUiState(isTocLoading = false)
                context.toastOnUi(R.string.error_no_source)
                return
            }
            val oldBook = book.copy()
            WebBook.getChapterList(scope, source, book, runPreUpdateJs)
                .onSuccess(IO) { chapters ->
                    if (inBookshelf) {
                        appDb.bookDao.replace(oldBook, book)
                        if (oldBook.bookUrl != book.bookUrl) {
                            BookHelp.updateCacheFolder(oldBook, book)
                        }
                        appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                        appDb.bookChapterDao.insert(*chapters.toTypedArray())
                        ReadBook.onChapterListUpdated(book)
                    }
                    currentBook = book
                    currentChapterList = chapters
                    syncUiState(isTocLoading = false)
                }.onError {
                    currentChapterList = emptyList()
                    syncUiState(isTocLoading = false)
                    AppLog.put("获取目录失败\n${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_chapter_list)
                }
        }
    }

    private fun loadWebFile(book: Book) {
        execute {
            val fileNameNoExtension = if (book.author.isBlank()) book.name else "${book.name} 作者：${book.author}"
            book.downloadUrls.orEmpty().map { url ->
                val analyzeUrl = AnalyzeUrl(
                    url,
                    source = bookSource,
                    coroutineContext = coroutineContext,
                )
                val fileName = UrlUtil.getFileName(analyzeUrl)
                    ?: "${fileNameNoExtension}.${analyzeUrl.type}"
                BookInfoWebFile(url = url, name = fileName)
            }
        }.onSuccess {
            currentWebFiles = it
            syncUiState(isTocLoading = false)
        }.onError {
            currentWebFiles = emptyList()
            context.toastOnUi("LoadWebFileError\n${it.localizedMessage}")
            syncUiState(isTocLoading = false)
        }
    }

    private fun onBackPressed() {
        if (!inBookshelf && AppConfig.showAddToShelfAlert && currentBook != null) {
            showDialog(BookInfoDialog.AddToShelfOnBack)
        } else {
            emitEffect(BookInfoEffect.Finish(afterTransition = true))
        }
    }

    private fun onReadClick() {
        val book = currentBook ?: return
        if (book.isWebFile) {
            setSheet(BookInfoSheet.WebFiles(openAfterImport = true))
        } else {
            readBook(book)
        }
    }

    private fun onShelfClick() {
        val book = currentBook ?: return
        if (inBookshelf) {
            showDialog(BookInfoDialog.DeleteBook(book.isLocal))
        } else if (book.isWebFile) {
            setSheet(BookInfoSheet.WebFiles(openAfterImport = false))
        } else {
            addToBookshelf()
        }
    }

    private fun onTocClick() {
        val book = currentBook ?: return
        if (currentChapterList.isEmpty()) {
            context.toastOnUi(R.string.chapter_list_empty)
            return
        }
        if (!inBookshelf) {
            saveBook(book) {
                saveChapterList {
                    emitEffect(BookInfoEffect.OpenToc(book.bookUrl))
                }
            }
        } else {
            emitEffect(BookInfoEffect.OpenToc(book.bookUrl))
        }
    }

    private fun updateGroup(groupId: Long) {
        currentBook?.let { book ->
            book.group = groupId
            currentGroupNames = null
            currentHasCustomGroup = false
            refreshMeta(book)
            if (inBookshelf) {
                saveBook(book)
            } else if (groupId > 0) {
                addToBookshelf()
            } else {
                syncUiState()
            }
        }
    }

    private fun updateCover(coverUrl: String) {
        currentBook?.let { book ->
            book.customCoverUrl = coverUrl
            currentBook = book
            syncUiState()
            if (inBookshelf) {
                saveBook(book)
            }
        }
    }
    private fun deleteBook(deleteOriginal: Boolean) {
        currentBook?.let { book ->
            LocalConfig.deleteBookOriginal = deleteOriginal
            _uiState.update { it.copy(deleteOriginal = deleteOriginal) }
            SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, bookSource, book)
            delBook(deleteOriginal) {
                emitEffect(BookInfoEffect.Finish(resultCode = RESULT_OK))
            }
        }
    }

    private fun handleWebFileSelection(webFile: BookInfoWebFile, openAfterImport: Boolean) {
        when {
            webFile.isSupported -> {
                dismissSheet()
                importOrDownloadWebFile<Book>(webFile) { book ->
                    if (openAfterImport) {
                        openReader(book)
                    }
                }
            }

            webFile.isSupportDecompress -> {
                importOrDownloadWebFile<Uri>(webFile) { uri ->
                    getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            importArchiveBook(uri, fileNames.first()) { book ->
                                if (openAfterImport) {
                                    openReader(book)
                                }
                            }
                        } else {
                            setSheet(
                                BookInfoSheet.ArchiveEntries(
                                    archiveUri = uri,
                                    entries = fileNames,
                                    openAfterImport = openAfterImport,
                                )
                            )
                        }
                    }
                }
            }

            else -> {
                showDialog(BookInfoDialog.UnsupportedWebFile(webFile, openAfterImport))
            }
        }
    }

    private fun readBook(book: Book) {
        if (!inBookshelf) {
            book.addType(BookType.notShelf)
            saveBook(book) {
                saveChapterList {
                    openReader(book)
                }
            }
        } else {
            saveBook(book) {
                openReader(book)
            }
        }
    }

    private fun openReader(book: Book) {
        emitEffect(BookInfoEffect.OpenReader(book.uiCopy(), inBookshelf, chapterChanged))
    }

    private fun handleMenuAction(action: BookInfoMenuAction) {
        val book = currentBook ?: return
        when (action) {
            BookInfoMenuAction.Edit -> openEdit()
            BookInfoMenuAction.Share -> {
                val bookJson = GSON.toJson(book)
                emitEffect(
                    BookInfoEffect.RunSourceCallback(
                        event = SourceCallBack.CLICK_SHARE_BOOK,
                        source = bookSource,
                        book = book.uiCopy(),
                        action = BookInfoCallbackAction.ShareText(
                            chooserTitle = book.name,
                            text = "${book.bookUrl}#$bookJson",
                        )
                    )
                )
            }

            BookInfoMenuAction.Upload -> uploadBook {
                context.toastOnUi("上传成功")
            }
            BookInfoMenuAction.SyncRemote -> syncFromRemote()
            BookInfoMenuAction.Refresh -> refreshCurrentBook()
            BookInfoMenuAction.ReadRecord -> setSheet(BookInfoSheet.ReadRecord)
            BookInfoMenuAction.Login -> bookSource?.let {
                emitEffect(BookInfoEffect.OpenSourceLogin(it.bookSourceUrl))
            }

            BookInfoMenuAction.Top -> topBook()
            BookInfoMenuAction.SetSourceVariable -> requestSourceVariableDialog()
            BookInfoMenuAction.SetBookVariable -> requestBookVariableDialog()
            BookInfoMenuAction.CopyBookUrl -> emitEffect(
                BookInfoEffect.RunSourceCallback(
                    event = SourceCallBack.CLICK_COPY_BOOK_URL,
                    source = bookSource,
                    book = book.uiCopy(),
                    action = BookInfoCallbackAction.CopyText(book.bookUrl),
                )
            )

            BookInfoMenuAction.CopyTocUrl -> emitEffect(
                BookInfoEffect.RunSourceCallback(
                    event = SourceCallBack.CLICK_COPY_TOC_URL,
                    source = bookSource,
                    book = book.uiCopy(),
                    action = BookInfoCallbackAction.CopyText(book.tocUrl),
                )
            )

            BookInfoMenuAction.ToggleCanUpdate -> toggleCanUpdate()
            BookInfoMenuAction.ToggleSplitLongChapter -> toggleSplitLongChapter()
            BookInfoMenuAction.ToggleDeleteAlert -> toggleDeleteAlert()
            BookInfoMenuAction.ClearCache -> emitEffect(
                BookInfoEffect.RunSourceCallback(
                    event = SourceCallBack.CLICK_CLEAR_CACHE,
                    source = bookSource,
                    book = book.uiCopy(),
                    action = BookInfoCallbackAction.ClearCache,
                )
            )

            BookInfoMenuAction.ShowLog -> showAppLog()
        }
    }

    private fun onAuthorClick(longClick: Boolean) {
        val book = currentBook ?: return
        emitEffect(
            BookInfoEffect.RunSourceCallback(
                event = if (longClick) SourceCallBack.LONG_CLICK_AUTHOR else SourceCallBack.CLICK_AUTHOR,
                source = bookSource,
                book = book.uiCopy(),
                action = BookInfoCallbackAction.Search(book.author),
            )
        )
    }

    private fun onBookNameClick(longClick: Boolean) {
        val book = currentBook ?: return
        emitEffect(
            BookInfoEffect.RunSourceCallback(
                event = if (longClick) SourceCallBack.LONG_CLICK_BOOK_NAME else SourceCallBack.CLICK_BOOK_NAME,
                source = bookSource,
                book = book.uiCopy(),
                action = BookInfoCallbackAction.Search(book.name),
            )
        )
    }

    private fun onOriginClick() {
        val book = currentBook ?: return
        if (book.isLocal) return
        if (!appDb.bookSourceDao.has(book.origin)) {
            context.toastOnUi(R.string.error_no_source)
            return
        }
        emitEffect(BookInfoEffect.OpenBookSourceEdit(book.origin))
    }

    fun getArchiveFilesName(archiveFileUri: Uri, onSuccess: (List<String>) -> Unit) {
        execute {
            ArchiveUtils.getArchiveFilesName(archiveFileUri) {
                AppPattern.bookFileRegex.matches(it)
            }
        }.onError {
            AppLog.put("getArchiveEntriesName Error:\n${it.localizedMessage}", it)
            context.toastOnUi("getArchiveEntriesName Error:\n${it.localizedMessage}")
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    fun importArchiveBook(
        archiveFileUri: Uri,
        archiveEntryName: String,
        success: ((Book) -> Unit)? = null,
    ) {
        execute {
            val suffix = archiveEntryName.substringAfterLast(".")
            LocalBook.importArchiveFile(
                archiveFileUri,
                currentBook!!.getExportFileName(suffix)
            ) {
                it.contains(archiveEntryName)
            }.first()
        }.onSuccess {
            val book = changeToLocalBook(it)
            success?.invoke(book)
        }.onError {
            AppLog.put("importArchiveBook Error:\n${it.localizedMessage}", it)
            context.toastOnUi("importArchiveBook Error:\n${it.localizedMessage}")
        }
    }

    fun <T> importOrDownloadWebFile(webFile: BookInfoWebFile, success: ((T) -> Unit)? = null) {
        bookSource ?: return
        val book = currentBook ?: return
        execute {
            setBusy(true)
            if (webFile.isSupported) {
                val localBook = LocalBook.importFileOnLine(
                    webFile.url,
                    book.getExportFileName(webFile.suffix),
                    bookSource
                )
                changeToLocalBook(localBook)
            } else {
                LocalBook.saveBookFile(
                    webFile.url,
                    book.getExportFileName(webFile.suffix),
                    bookSource
                )
            }
        }.onSuccess {
            @Suppress("UNCHECKED_CAST")
            success?.invoke(it as T)
        }.onError {
            when (it) {
                is NoBooksDirException -> emitEffect(BookInfoEffect.OpenSelectBooksDir)
                else -> {
                    AppLog.put("ImportWebFileError\n${it.localizedMessage}", it)
                    context.toastOnUi("ImportWebFileError\n${it.localizedMessage}")
                }
            }
        }.onFinally {
            setBusy(false)
        }
    }

    private fun changeToLocalBook(localBook: Book): Book {
        return LocalBook.mergeBook(localBook, currentBook).let {
            currentBook = it
            currentWebFiles = emptyList()
            inBookshelf = true
            syncUiState(isTocLoading = true)
            refreshMeta(it)
            loadChapter(it)
            it
        }
    }

    private fun observeReadRecordIfNeeded(book: Book?) {
        if (book == null) {
            clearReadRecordObserve()
            return
        }
        val key = "${book.name}|||${book.author}"
        if (observingReadRecordKey == key && readRecordObserveJob?.isActive == true) return
        observingReadRecordKey = key
        readRecordObserveJob?.cancel()
        readRecordObserveJob = viewModelScope.launch {
            combine(
                readRecordRepository.getBookReadTime(book.name, book.author),
                readRecordRepository.getBookTimelineDays(book.name, book.author)
            ) { totalTime, timelineDays ->
                totalTime to timelineDays
            }.collectLatest { (totalTime, timelineDays) ->
                currentReadRecordTotalTime = totalTime
                currentReadRecordTimelineDays = timelineDays
                _uiState.update {
                    it.copy(
                        readRecordTotalTime = currentReadRecordTotalTime,
                        readRecordTimelineDays = currentReadRecordTimelineDays
                    )
                }
            }
        }
    }

    private fun clearReadRecordObserve() {
        readRecordObserveJob?.cancel()
        readRecordObserveJob = null
        observingReadRecordKey = null
        currentReadRecordTotalTime = 0L
        currentReadRecordTimelineDays = emptyList()
    }

    private fun dismissSheet() {
        setSheet(BookInfoSheet.None)
    }

    private fun setSheet(sheet: BookInfoSheet) {
        _uiState.update { it.copy(sheet = sheet) }
    }

    private fun dismissDialog() {
        showDialog(null)
    }

    private fun showDialog(dialog: BookInfoDialog?) {
        _uiState.update { it.copy(dialog = dialog) }
    }

    private fun setBusy(isBusy: Boolean) {
        _uiState.update { it.copy(isBusy = isBusy) }
    }

    private fun syncUiState(isTocLoading: Boolean = _uiState.value.isTocLoading) {
        _uiState.update {
            it.copy(
                book = currentBook?.uiCopy(),
                chapterList = currentChapterList,
                webFiles = currentWebFiles,
                kindLabels = currentKindLabels,
                groupNames = currentGroupNames,
                hasCustomGroup = currentHasCustomGroup,
                readRecordTotalTime = currentReadRecordTotalTime,
                readRecordTimelineDays = currentReadRecordTimelineDays,
                inBookshelf = inBookshelf,
                bookSource = bookSource,
                isTocLoading = isTocLoading,
                deleteAlertEnabled = LocalConfig.bookInfoDeleteAlert,
                deleteOriginal = LocalConfig.deleteBookOriginal,
            )
        }
    }

    private fun emitEffect(effect: BookInfoEffect) {
        _effects.tryEmit(effect)
    }

    private fun Book.uiCopy(): Book {
        return copy().also { snapshot ->
            snapshot.infoHtml = infoHtml
            snapshot.tocHtml = tocHtml
            snapshot.downloadUrls = downloadUrls
        }
    }
}

private val BookInfoWebFile.suffix: String
    get() = UrlUtil.getSuffix(name)

private val BookInfoWebFile.isSupported: Boolean
    get() = AppPattern.bookFileRegex.matches(name)

private val BookInfoWebFile.isSupportDecompress: Boolean
    get() = AppPattern.archiveFileRegex.matches(name)
