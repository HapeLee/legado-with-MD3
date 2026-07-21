package io.legado.app.ui.book.info

import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.SuccessResult
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
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.RemoteBookRepository
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.exception.NoBooksDirException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.getDisplayTagList
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.parseHighlightedTags
import io.legado.app.help.book.removeType
import io.legado.app.help.book.upKind
import io.legado.app.help.book.updateTo
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
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.main.MainIntent
import io.legado.app.ui.widget.components.image.cover.buildCoverImageRequest
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.postEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class BookInfoViewModel(
    application: Application,
    private val remoteBookRepository: RemoteBookRepository,
    private val readRecordRepository: ReadRecordRepository,
    private val changeBookSourceUseCase: ChangeBookSourceUseCase,
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
    private val bookGroupRepository: BookGroupRepository,
    private val imageLoader: ImageLoader,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
    private val coverSettingsGateway: CoverSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
) : BaseViewModel(application) {

    val allGroups = bookGroupRepository.flowSelect().map { it.toImmutableList() }

    // 仅保存“每本书/屏幕”状态；外观与其他设置不在此存储，避免整体重置时被抹掉。
    private val _screenState = MutableStateFlow(BookInfoUiState())

    // 设置类字段始终从各自 gateway（唯一 SSOT）派生叠加，重置屏幕状态无法影响它们。
    val uiState: StateFlow<BookInfoUiState> = combine(
        _screenState,
        themeSettingsGateway.settings,
        coverSettingsGateway.settings,
        otherSettingsGateway.settings,
    ) { screen, theme, cover, other ->
        screen.withSettings(theme, cover, other)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookInfoUiState().withSettings(
            themeSettingsGateway.currentSettings,
            coverSettingsGateway.currentSettings,
            otherSettingsGateway.currentSettings,
        ),
    )

    private val _effects = MutableSharedFlow<BookInfoEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        collectEventBus()
    }

    private fun collectEventBus() {
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.REFRESH_BOOK_INFO).collect {
                currentBook?.let { book ->
                    refreshBook(book)
                }
            }
        }
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.REFRESH_BOOK_TOC).collect {
                currentBook?.let { book ->
                    loadChapter(book)
                }
            }
        }
    }

    private inline fun <reified T> eventFlow(tag: String): Flow<T> = callbackFlow {
        val obs = androidx.lifecycle.Observer<T> { trySend(it) }
        com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).observeForever(obs)
        awaitClose {
            com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).removeObserver(obs)
        }
    }

    private var currentBook: Book? = null
        set(value) {
            field = value
            observeReadRecordIfNeeded(value)
        }
    private var currentChapterList: List<BookChapter> = emptyList()
    private var tocLoadFailed = false
    private var currentWebFiles: List<BookInfoWebFile> = emptyList()
    private var currentRelatedBooks: List<RelatedBooksUi> = emptyList()
    private var currentCharacters: List<BookInfoCharacterUi> = emptyList()
    private var currentHighlightedTags: List<HighlightedTag> = emptyList()
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
    private var relatedBooksLoadJob: Job? = null
    private var characterLoadJob: Job? = null

    fun initData(intent: Intent) {
        initData(
            bookUrl = intent.getStringExtra(MainIntent.EXTRA_BOOK_URL) ?: "",
            name = intent.getStringExtra(MainIntent.EXTRA_BOOK_NAME),
            author = intent.getStringExtra(MainIntent.EXTRA_BOOK_AUTHOR),
            origin = intent.getStringExtra(MainIntent.EXTRA_BOOK_ORIGIN),
            coverPath = intent.getStringExtra(MainIntent.EXTRA_BOOK_COVER)
        )
    }

    fun initData(
        bookUrl: String,
        name: String? = null,
        author: String? = null,
        origin: String? = null,
        coverPath: String? = null
    ) {
        val current = currentBook
        if (current != null) return
        currentBook = if (!name.isNullOrBlank() && !author.isNullOrBlank()) {
            Book(
                bookUrl = bookUrl,
                name = name,
                author = author,
                origin = origin ?: BookType.localTag,
                coverUrl = coverPath
            ).apply {
                addType(BookType.notShelf)
            }
        } else {
            null
        }
        clearReadRecordObserve()
        relatedBooksLoadJob?.cancel()
        characterLoadJob?.cancel()
        syncUiState()
        execute {
            val dbBook = appDb.bookDao.getBook(bookUrl)
            if (dbBook != null) {
                inBookshelf = !dbBook.isNotShelf
                dbBook
            } else {
                val searchBook = appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()
                if (searchBook != null) {
                    inBookshelf = false
                    searchBook
                } else {
                    currentBook ?: throw NoStackTraceException("未找到书籍")
                }
            }
        }.onSuccess { book ->
            // 如果从数据库/搜索中拿到的书没有封面，但我们有传入的封面，则保留传入的封面
            if (book.coverUrl.isNullOrBlank() && !coverPath.isNullOrBlank()) {
                book.coverUrl = coverPath
            }
            val source = if (book.isLocal) {
                null
            } else {
                appDb.bookSourceDao.getBookSource(book.origin)
            }
            upBook(book, source)
        }.onError {
            showMessage(it.localizedMessage ?: "未找到书籍")
            emitEffect(BookInfoEffect.Finish(afterTransition = true))
        }
    }

    fun onIntent(intent: BookInfoIntent) {
        when (intent) {
            BookInfoIntent.DismissSheet -> dismissSheet()
            BookInfoIntent.DismissDialog -> dismissDialog()
            is BookInfoIntent.MenuAction -> handleMenuAction(intent.action)
            is BookInfoIntent.AuthorClick -> onAuthorClick(intent.longClick)
            is BookInfoIntent.BookNameClick -> onBookNameClick(intent.longClick)
            BookInfoIntent.OriginClick -> onOriginClick()
            BookInfoIntent.DismissAppLogSheet -> {
                _screenState.update { it.copy(showAppLogSheet = false) }
            }

            BookInfoIntent.ReadClick -> onReadClick()
            BookInfoIntent.ShelfClick -> onShelfClick()
            BookInfoIntent.TocClick -> onTocClick()
            BookInfoIntent.CoverClick -> setSheet(BookInfoSheet.CoverPicker)
            BookInfoIntent.CoverLongClick -> currentBook?.getDisplayCover()?.takeIf { it.isNotBlank() }
                ?.let { showDialog(BookInfoDialog.PhotoPreview(it)) }

            BookInfoIntent.GroupClick -> setSheet(BookInfoSheet.GroupPicker)
            BookInfoIntent.ChangeSourceClick -> currentBook?.uiCopy()
                ?.let { setSheet(BookInfoSheet.SourcePicker(it)) }
            BookInfoIntent.ReadRecordClick -> setSheet(BookInfoSheet.ReadRecord)
            BookInfoIntent.RemarkClick -> showDialog(BookInfoDialog.EditRemark(currentBook?.remark))
            is BookInfoIntent.SaveCover -> {
                saveCoverToGallery(intent.path)
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
                    showMessage("已添加到书架")
                }
            }

            is BookInfoIntent.ReplaceConflictingBook -> {
                dismissSheet()
                changeTo(
                    source = intent.source,
                    book = intent.book,
                    toc = intent.toc,
                    options = intent.options,
                    replacedBook = intent.oldBook,
                )
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

            is BookInfoIntent.RelatedBookClick -> onRelatedBookClick(intent.book)
            is BookInfoIntent.RelatedBooksMore -> onRelatedBooksMore(intent.title, intent.url)
            is BookInfoIntent.CharacterClick -> openCharacterDetail(intent.characterId)
            BookInfoIntent.AddCharacterClick -> openCharacterDetail(null)
            BookInfoIntent.CharacterNetworkClick -> openCharacterNetwork()
            BookInfoIntent.CharacterListClick -> openCharacterList()
            BookInfoIntent.KnowledgeListClick -> openKnowledgeList()
            BookInfoIntent.EventListClick -> openEventList()
            is BookInfoIntent.SetDefaultBookTreeUri -> viewModelScope.launch {
                otherSettingsGateway.update { it.copy(defaultBookTreeUri = intent.value) }
            }
        }
    }

    fun openEdit() {
        currentBook?.let {
            emitEffect(BookInfoEffect.OpenBookInfoEdit(it.bookUrl))
        }
    }

    fun showAppLog() {
        _screenState.update { it.copy(showAppLogSheet = true) }
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
            execute {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@execute null
                val source = if (book.isLocal) {
                    null
                } else {
                    appDb.bookSourceDao.getBookSource(book.origin)
                }
                book to source
            }.onSuccess {
                it?.let { (book, source) -> upBook(book, source) }
            }
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

            READER_RESULT_DELETED -> {
                emitEffect(BookInfoEffect.Finish(resultCode = RESULT_OK))
            }
        }
    }

    fun refreshShelfState() {
        val bookUrl = currentBook?.bookUrl ?: return
        execute {
            appDb.bookDao.getBook(bookUrl)
        }.onSuccess { dbBook ->
            val nextInBookshelf = dbBook != null && !dbBook.isNotShelf
            if (nextInBookshelf) {
                currentBook = dbBook
            }
            if (inBookshelf != nextInBookshelf || nextInBookshelf) {
                inBookshelf = nextInBookshelf
                syncUiState()
            }
            loadBookCharacters(bookUrl)
            loadBookKnowledge(bookUrl)
            loadBookEvents(bookUrl)
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
                showMessage(R.string.need_more_time_load_content)
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
            showMessage(it.localizedMessage ?: "书源不存在")
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
            showMessage(it.localizedMessage ?: "书源不存在")
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
            showMessage("同步完成")
        }.onFinally {
            setBusy(false)
        }.onError {
            showMessage(it.localizedMessage ?: "同步失败")
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
            showMessage(it.localizedMessage ?: "操作失败")
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
                showMessage(R.string.clear_cache_success)
            }.onError {
                showMessage("清理缓存出错\n${it.localizedMessage}")
            }
        }
    }

    private fun saveCoverToGallery(path: String) {
        val book = currentBook
        val sourceOrigin = if (book?.getDisplayCover() == path) book.origin else null
        execute {
            setBusy(true)
            val request = buildCoverImageRequest(
                context = context,
                data = path,
                sourceOrigin = sourceOrigin,
                loadOnlyWifi = CoverConfig.loadCoverOnlyWifi,
                crossfade = false
            )
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.drawable.toBitmap()
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                ImageSaveUtils.saveImageToGallery(context, byteArray, "Cover_")
            } else {
                false
            }
        }.onSuccess { success ->
            if (success) {
                showMessage("保存成功")
            } else {
                showMessage("保存失败")
            }
        }.onFinally {
            setBusy(false)
        }.onError {
            showMessage("保存出错: ${it.localizedMessage}")
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
            showMessage("添加书籍失败")
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
        showLoading: Boolean = true,
    ) {
        syncUiState(isTocLoading = showLoading)
        if (book.isLocal) {
            LocalBook.upBookInfo(book)
            currentBook = book
            syncUiState(isTocLoading = showLoading)
            loadChapter(book, showLoading = showLoading)
        } else {
            val source = bookSource ?: run {
                currentChapterList = emptyList()
                syncUiState(isTocLoading = false)
                showMessage(R.string.error_no_source)
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
                    syncUiState(isTocLoading = showLoading)
                    refreshMeta(loadedBook)
                    if (loadedBook.isWebFile) {
                        loadWebFile(loadedBook)
                        currentChapterList = emptyList()
                        syncUiState(isTocLoading = false)
                    } else {
                        loadChapter(loadedBook, runPreUpdateJs, showLoading = showLoading)
                    }
                    scheduleRelatedBooksLoad(loadedBook, source)
                }.onError {
                    AppLog.put("获取书籍信息失败\n${it.localizedMessage}", it)
                    showMessage(R.string.error_get_book_info)
                    syncUiState(isTocLoading = false)
                }
        }
    }
    fun changeTo(
        source: BookSource,
        book: Book,
        toc: List<BookChapter>,
        options: ChangeSourceMigrationOptions,
        replacedBook: Book? = null,
    ) {
        val shouldPersist = replacedBook != null || inBookshelf
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            val oldBook = replacedBook ?: currentBook ?: return@execute book
            if (shouldPersist) {
                changeBookSourceUseCase.changeTo(oldBook, book, toc, options)
            } else {
                changeBookSourceUseCase.applyMigration(oldBook, book, toc, options)
            }
            book
        }.onSuccess {
            bookSource = source
            currentBook = it
            if (shouldPersist) {
                inBookshelf = true
            }
            currentChapterList = toc
            currentRelatedBooks = emptyList()
            currentCharacters = emptyList()
            currentGroupNames = null
            currentHasCustomGroup = false
            currentKindLabels = emptyList()
            syncUiState(isTocLoading = false)
            refreshMeta(it)
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    private fun upBook(book: Book, source: BookSource?) {
        currentBook = book
        currentChapterList = emptyList()
        tocLoadFailed = false
        currentWebFiles = emptyList()
        currentRelatedBooks = emptyList()
        currentCharacters = emptyList()
        currentKindLabels = emptyList()
        currentGroupNames = null
        currentHasCustomGroup = false
        bookSource = source
        syncUiState(isTocLoading = false)
        loadBookCharacters(book.bookUrl)
        loadBookKnowledge(book.bookUrl)
        loadBookEvents(book.bookUrl)
        refreshMeta(book)
        upCoverByRule(book)
        if (book.tocUrl.isEmpty() && !book.isLocal) {
            loadBookInfo(book, runPreUpdateJs = inBookshelf, showLoading = false)
        } else {
            execute {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }.onSuccess { chapters ->
                if (chapters.isNotEmpty()) {
                    currentChapterList = chapters
                    syncUiState(isTocLoading = false)
                    source?.let { scheduleRelatedBooksLoad(book, it) }
                } else {
                    loadChapter(book, showLoading = false)
                }
            }.onError {
                loadChapter(book, showLoading = false)
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
            book.upKind()
            val userGroupIds = appDb.bookGroupDao.idsSum
            val groupAnd = userGroupIds and book.group
            val hasCustomGroup = book.group > 0L && groupAnd != 0L
            val groupNames = appDb.bookGroupDao.getGroupNames(book.group).joinToString(",")
            val normalizedGroupNames = groupNames.ifBlank { null }
            appDb.bookDao.update(book)
            val finalKinds = book.getDisplayTagList()
            val enabledRules = appDb.highlightTagRuleDao.getEnabled()
            val (highlighted, regular) = parseHighlightedTags(finalKinds, enabledRules)
            HighlightMeta(highlighted, regular, normalizedGroupNames, hasCustomGroup)
        }.onSuccess {
            currentHighlightedTags = it.highlighted
            currentKindLabels = it.regular
            currentGroupNames = it.groupNames
            currentHasCustomGroup = it.hasCustomGroup
            syncUiState()
        }
    }

    private data class HighlightMeta(
        val highlighted: List<HighlightedTag>,
        val regular: List<String>,
        val groupNames: String?,
        val hasCustomGroup: Boolean,
    )

    private fun loadChapter(
        book: Book,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope,
        showLoading: Boolean = true,
    ) {
        tocLoadFailed = false
        syncUiState(isTocLoading = showLoading)
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
                tocLoadFailed = true
                syncUiState(isTocLoading = false)
            }
        } else {
            val source = bookSource ?: run {
                currentChapterList = emptyList()
                syncUiState(isTocLoading = false)
                showMessage(R.string.error_no_source)
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
                    tocLoadFailed = true
                    syncUiState(isTocLoading = false)
                    AppLog.put("获取目录失败\n${it.localizedMessage}", it)
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
            showMessage("LoadWebFileError\n${it.localizedMessage}")
            syncUiState(isTocLoading = false)
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
            showMessage(R.string.chapter_list_empty)
            return
        }
        if (!inBookshelf) {
            book.addType(BookType.notShelf)
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
            _screenState.update { it.copy(deleteOriginal = deleteOriginal) }
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
            BookInfoMenuAction.CustomButton -> emitEffect(
                BookInfoEffect.RunSourceCallback(
                    event = SourceCallBack.CLICK_CUSTOM_BUTTON,
                    source = bookSource,
                    book = book.uiCopy(),
                    action = BookInfoCallbackAction.None,
                )
            )
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
                showMessage("上传成功")
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
            showMessage(R.string.error_no_source)
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
            showMessage("getArchiveEntriesName Error:\n${it.localizedMessage}")
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
            showMessage("importArchiveBook Error:\n${it.localizedMessage}")
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
                    showMessage("ImportWebFileError\n${it.localizedMessage}")
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
                _screenState.update {
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
        _screenState.update { it.copy(sheet = sheet) }
    }

    private fun dismissDialog() {
        showDialog(null)
    }

    private fun showDialog(dialog: BookInfoDialog?) {
        _screenState.update { it.copy(dialog = dialog) }
    }

    private fun setBusy(isBusy: Boolean) {
        _screenState.update { it.copy(isBusy = isBusy) }
    }

    private fun syncUiState(isTocLoading: Boolean = _screenState.value.isTocLoading) {
        _screenState.update {
            it.copy(
                book = currentBook?.toBookInfoBookUi(),
                hasChapters = currentChapterList.isNotEmpty(),
                tocLoadFailed = tocLoadFailed,
                webFiles = currentWebFiles,
                relatedBooks = currentRelatedBooks.toImmutableList(),
                characters = currentCharacters.toImmutableList(),
                highlightedTags = currentHighlightedTags,
                kindLabels = currentKindLabels,
                groupNames = currentGroupNames,
                hasCustomGroup = currentHasCustomGroup,
                readRecordTotalTime = currentReadRecordTotalTime,
                readRecordTimelineDays = currentReadRecordTimelineDays,
                inBookshelf = inBookshelf,
                bookSource = bookSource?.toBookInfoSourceUi(),
                isTocLoading = isTocLoading,
                deleteAlertEnabled = LocalConfig.bookInfoDeleteAlert,
                deleteOriginal = LocalConfig.deleteBookOriginal,
            )
        }
    }

    private fun onRelatedBookClick(book: SearchBook) {
        emitEffect(
            BookInfoEffect.NavigateToBookInfo(
                name = book.name,
                author = book.author,
                bookUrl = book.bookUrl,
                origin = book.origin,
                coverPath = book.coverUrl,
            )
        )
    }

    private fun onRelatedBooksMore(title: String, resolvedUrl: String) {
        val source = bookSource ?: return
        emitEffect(
            BookInfoEffect.NavigateToExploreShow(
                title = title,
                sourceUrl = source.bookSourceUrl,
                exploreUrl = resolvedUrl,
            )
        )
    }

    private fun openCharacterDetail(characterId: String?) {
        val bookUrl = currentBook?.bookUrl ?: return
        emitEffect(BookInfoEffect.OpenCharacterDetail(bookUrl, characterId))
    }

    private fun openCharacterNetwork() {
        val bookUrl = currentBook?.bookUrl ?: return
        emitEffect(BookInfoEffect.OpenCharacterNetwork(bookUrl))
    }

    private fun openKnowledgeList() {
        val bookUrl = currentBook?.bookUrl ?: return
        emitEffect(BookInfoEffect.OpenKnowledgeList(bookUrl))
    }

    private fun openCharacterList() {
        val bookUrl = currentBook?.bookUrl ?: return
        emitEffect(BookInfoEffect.OpenCharacterList(bookUrl))
    }

    private fun openEventList() {
        val bookUrl = currentBook?.bookUrl ?: return
        emitEffect(BookInfoEffect.OpenEventList(bookUrl))
    }

    private var knowledgeLoadJob: Job? = null
    private var eventLoadJob: Job? = null
    private var currentKnowledgeEntries: List<BookInfoKnowledgeUi> = emptyList()
    private var currentRecentEvents: List<BookInfoEventUi> = emptyList()

    private fun loadBookCharacters(bookUrl: String) {
        characterLoadJob?.cancel()
        characterLoadJob = viewModelScope.launch {
            val roleOrder = mapOf(
                io.legado.app.data.entities.BookCharacterProfile.ROLE_MALE_LEAD to 0,
                io.legado.app.data.entities.BookCharacterProfile.ROLE_FEMALE_LEAD to 1,
                io.legado.app.data.entities.BookCharacterProfile.ROLE_MALE_SUPPORTING to 2,
                io.legado.app.data.entities.BookCharacterProfile.ROLE_FEMALE_SUPPORTING to 3,
            )
            val characters = try {
                withContext(IO) {
                    bookKnowledgeGateway.getCharacterProfiles(bookUrl, limit = 50)
                }.sortedBy { roleOrder[it.role] ?: 99 }
                    .map {
                        val tags = GSON.fromJsonArray<String>(it.tagsJson).getOrNull().orEmpty()
                        BookInfoCharacterUi(
                            id = it.id,
                            name = it.name,
                            avatarUri = it.avatarUri,
                            role = it.role,
                            tags = tags.joinToString(" | "),
                            summary = it.summary,
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            if (currentBook?.bookUrl != bookUrl) return@launch
            currentCharacters = characters
            _screenState.update {
                it.copy(characters = currentCharacters.toImmutableList())
            }
        }
    }

    private fun loadBookKnowledge(bookUrl: String) {
        knowledgeLoadJob?.cancel()
        knowledgeLoadJob = viewModelScope.launch {
            val entries = try {
                withContext(IO) {
                    bookKnowledgeGateway.searchKnowledgeEntries(bookUrl, "", null, null, 10)
                }.map {
                    BookInfoKnowledgeUi(
                        id = it.id,
                        type = it.type,
                        title = it.title,
                        summary = it.content.take(80),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            if (currentBook?.bookUrl != bookUrl) return@launch
            currentKnowledgeEntries = entries
            _screenState.update {
                it.copy(knowledgeEntries = currentKnowledgeEntries.toImmutableList())
            }
        }
    }

    private fun loadBookEvents(bookUrl: String) {
        eventLoadJob?.cancel()
        eventLoadJob = viewModelScope.launch {
            val events = try {
                withContext(IO) {
                    bookKnowledgeGateway.getCharacterEvents(bookUrl, null, null, 10)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            if (currentBook?.bookUrl != bookUrl) return@launch
            val profiles = try {
                withContext(IO) {
                    bookKnowledgeGateway.getCharacterProfiles(bookUrl, 200)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            val nameMap = profiles.associate { it.id to it.name }
            currentRecentEvents = events.map { event ->
                BookInfoEventUi(
                    id = event.id,
                    chapterTitle = event.chapterTitle,
                    eventTimeText = event.eventTimeText,
                    content = event.content.take(80),
                    characterName = nameMap[event.characterId].orEmpty(),
                )
            }
            _screenState.update {
                it.copy(recentEvents = currentRecentEvents.toImmutableList())
            }
        }
    }

    private fun scheduleRelatedBooksLoad(
        book: Book,
        source: BookSource,
        delayMillis: Long = 350L,
    ) {
        relatedBooksLoadJob?.cancel()
        relatedBooksLoadJob = viewModelScope.launch {
            delay(delayMillis)
            if (!isCurrentBookSource(book, source)) return@launch

            val modules = parseRelatedBookModules(source)
            if (modules.isEmpty()) {
                currentRelatedBooks = emptyList()
                syncUiState()
                return@launch
            }

            try {
                val result = withContext(IO) {
                    loadRelatedBooks(book, source, modules)
                }
                if (!isCurrentBookSource(book, source)) return@launch
                currentRelatedBooks = result
                syncUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!isCurrentBookSource(book, source)) return@launch
                currentRelatedBooks = emptyList()
                syncUiState()
            }
        }
    }

    private fun isCurrentBookSource(book: Book, source: BookSource): Boolean {
        return currentBook?.bookUrl == book.bookUrl && bookSource?.bookSourceUrl == source.bookSourceUrl
    }

    private fun parseRelatedBookModules(source: BookSource): List<RelatedBooksDef> {
        val modulesJson = source.ruleBookInfo?.relatedBooks
        if (modulesJson.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            GSON.fromJsonArray<RelatedBooksDef>(modulesJson)
                .getOrNull()
                ?.filter { !it.url.isNullOrBlank() }
                ?.map { it.copy(url = it.url!!.replace(Regex("\\s"), "")) }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun loadRelatedBooks(
        book: Book,
        source: BookSource,
        modules: List<RelatedBooksDef>,
    ): List<RelatedBooksUi> {
        return coroutineScope {
            modules.map { def ->
                async {
                    val url = def.url.orEmpty()
                    val (resolvedUrl, books) = try {
                        resolveAndExplore(source, url, book)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        url to emptyList()
                    }
                    RelatedBooksUi(
                        key = def.key ?: def.title.orEmpty(),
                        title = def.title.orEmpty(),
                        url = url,
                        resolvedUrl = resolvedUrl,
                        books = books.filter { it.bookUrl != book.bookUrl }.toImmutableList(),
                    )
                }
            }.awaitAll().filter { it.books.isNotEmpty() }
        }
    }

    private suspend fun resolveAndExplore(
        source: BookSource,
        url: String,
        book: Book,
    ): Pair<String, List<SearchBook>> {
        return WebBook.exploreBookWithResolvedUrl(source, url, 1, book)
    }

    private fun showMessage(resId: Int) = showMessage(context.getString(resId))

    private fun showMessage(message: String) {
        emitEffect(BookInfoEffect.ShowMessage(message))
    }

    private fun emitEffect(effect: BookInfoEffect) {
        _effects.tryEmit(effect)
    }

    private fun Book.toBookInfoBookUi(): BookInfoBookUi {
        return BookInfoBookUi(
            bookUrl = bookUrl,
            name = name,
            author = author,
            realAuthor = getRealAuthor(),
            origin = origin,
            originName = originName,
            coverPath = getDisplayCover(),
            group = group,
            isLocal = isLocal,
            type = type,
            canUpdate = canUpdate,
            splitLongChapter = getSplitLongChapter(),
            durChapterTitle = durChapterTitle,
            latestChapterTitle = latestChapterTitle,
            totalChapterNum = totalChapterNum,
            durChapterIndex = durChapterIndex,
            durChapterPos = durChapterPos,
            remark = remark,
            displayIntro = getDisplayIntro(),
        )
    }

    private fun BookSource.toBookInfoSourceUi(): BookInfoSourceUi {
        return BookInfoSourceUi(
            sourceUrl = bookSourceUrl,
            hasLogin = !loginUrl.isNullOrBlank(),
            hasCustomButton = customButton,
        )
    }

    private fun Book.uiCopy(): Book {
        return copy().also { snapshot ->
            snapshot.infoHtml = infoHtml
            snapshot.tocHtml = tocHtml
            snapshot.downloadUrls = downloadUrls
        }
    }
}

/**
 * 把三类设置（各自的 SSOT）叠加到屏幕状态上，得到完整的 UI 状态。
 * 纯函数：设置字段只来自参数，与屏幕状态如何重置无关。
 */
internal fun BookInfoUiState.withSettings(
    theme: ThemeSettings,
    cover: CoverSettings,
    other: OtherSettings,
): BookInfoUiState = copy(
    bookInfoFollowCoverColor = theme.bookInfoFollowCoverColor,
    bookInfoNetworkCoverBackground = theme.bookInfoNetworkCoverBackground,
    bookInfoDefaultCoverBackground = theme.bookInfoDefaultCoverBackground,
    loadCoverOnlyOnWifi = cover.loadOnlyOnWifi,
    defaultCover = cover.defaultCover,
    defaultCoverDark = cover.defaultCoverDark,
    showMangaUi = other.showMangaUi,
)

private val BookInfoWebFile.suffix: String
    get() = UrlUtil.getSuffix(name)

private val BookInfoWebFile.isSupported: Boolean
    get() = AppPattern.bookFileRegex.matches(name)

private val BookInfoWebFile.isSupportDecompress: Boolean
    get() = AppPattern.archiveFileRegex.matches(name)

private data class RelatedBooksDef(
    val key: String? = null,
    val title: String? = null,
    val url: String? = null,
)
