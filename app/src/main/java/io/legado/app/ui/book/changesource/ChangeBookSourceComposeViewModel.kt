package io.legado.app.ui.book.changesource

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.domain.usecase.ChangeSourceSearchEvent
import io.legado.app.domain.usecase.ChangeSourceSearchUseCase
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.domain.usecase.GetChapterContentUseCase
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.primaryStr
import io.legado.app.ui.book.search.SearchScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

sealed interface ChangeBookSourceEffect {
    data class ShowMessage(val message: String) : ChangeBookSourceEffect
    data class ShowMessageResource(@StringRes val messageRes: Int) : ChangeBookSourceEffect
}

class ChangeBookSourceComposeViewModel(
    private val changeSourceSearchUseCase: ChangeSourceSearchUseCase,
    private val getChapterContentUseCase: GetChapterContentUseCase,
    private val searchRepository: SearchRepository,
    private val bookDao: BookDao,
    private val changeSourceSettingsGateway: ChangeSourceSettingsGateway,
) : ViewModel() {

    // Public state for the sheet
    val enabledGroups = searchRepository.enabledGroups
    val enabledSources = searchRepository.enabledSources

    val settings = changeSourceSettingsGateway.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        changeSourceSettingsGateway.currentSettings,
    )
    private val searchScope = SearchScope(settings.value.searchScope)

    data class ScopeUiState(
        val isAll: Boolean,
        val isSource: Boolean,
        val displayNames: List<String>,
        val sourceUrls: List<String>
    )

    private val _scopeUiState = MutableStateFlow(
        ScopeUiState(
            isAll = searchScope.isAll(),
            isSource = searchScope.isSource(),
            displayNames = searchScope.displayNames,
            sourceUrls = searchScope.sourceUrls
        )
    )
    val scopeUiState = _scopeUiState.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _changeSourceProgress = MutableStateFlow(0 to "")
    val changeSourceProgress = _changeSourceProgress.asStateFlow()

    private val _searchDataFlow = MutableStateFlow<List<SearchBook>>(emptyList())
    val searchDataFlow: StateFlow<List<SearchBook>> = _searchDataFlow.asStateFlow()

    private val _emptyScopeName = MutableStateFlow<String?>(null)
    val emptyScopeName = _emptyScopeName.asStateFlow()

    private val _effects =
        MutableSharedFlow<ChangeBookSourceEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    @Volatile
    var totalSourceCount: Int = 0
        private set

    fun getBookFromMap(key: String): Book? = bookMap[key]

    fun findShelfConflict(book: Book, onResult: (Book?) -> Unit) {
        viewModelScope.launch(IO) {
            val conflict = bookDao.getShelfBookConflict(book.name, book.author)
            onMain { onResult(conflict) }
        }
    }

    // Options
    val checkAuthor: Boolean get() = settings.value.checkAuthor
    val loadInfo: Boolean get() = settings.value.loadInfo
    val loadToc: Boolean get() = settings.value.loadToc
    val loadWordCount: Boolean get() = settings.value.loadWordCount

    // Internal state
    private val chapterNumRegex = "^\\[(\\d+)]".toRegex()
    private var searchJob: Job? = null
    private var oldBook: Book? = null
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var fromReadBookActivity: Boolean = false
    @Volatile
    private var screenKey: String = ""
    private var cachedTocChapterCount = 0
    private val searchResults = Collections.synchronizedList(mutableListOf<SearchBook>())
    private val bookMap = ConcurrentHashMap<String, Book>()
    private val tocMap = ConcurrentHashMap<String, List<BookChapter>>()

    fun initData(name: String, author: String, book: Book, fromReadBookActivity: Boolean) {
        this.oldBook = book
        this.bookName = name
        this.bookAuthor = author.replace(AppPattern.authorRegex, "")
        this.fromReadBookActivity = fromReadBookActivity
        if (searchJob?.isActive != true) {
            viewModelScope.launch(IO) {
                val dbBooks = getDbSearchBooks()
                if (dbBooks.isNotEmpty()) {
                    synchronized(searchResults) {
                        searchResults.clear()
                        searchResults.addAll(dbBooks)
                    }
                    dbBooks.forEach { bookMap[it.primaryStr()] = it.toBook() }
                    filterResults()
                } else {
                    startSearch()
                }
            }
        }
    }

    private fun getDbSearchBooks(): List<SearchBook> {
        val scope = SearchScope(settings.value.searchScope)
        val author = if (checkAuthor) bookAuthor else ""
        val dbBooks = io.legado.app.data.appDb.searchBookDao.changeSourceByGroup(
            bookName, author, ""
        )
        if (scope.isAll()) return dbBooks

        val allowedOrigins = scope.getBookSourceParts()
            .mapTo(hashSetOf()) { it.bookSourceUrl }
        return dbBooks.filter { it.origin in allowedOrigins }
    }

    fun startSearch() {
        startSearch(
            scope = SearchScope(settings.value.searchScope),
            replacedOrigins = null,
        )
    }

    fun startSearch(origin: String) {
        viewModelScope.launch(IO) {
            val source = io.legado.app.data.appDb.bookSourceDao
                .getBookSourcePart(origin) ?: return@launch
            startSearch(listOf(source))
        }
    }

    private fun startSearch(sources: List<BookSourcePart>) {
        val scope = SearchScope("").apply { updateSources(sources) }
        startSearch(
            scope = scope,
            replacedOrigins = sources.mapTo(hashSetOf()) { it.bookSourceUrl },
        )
    }

    private fun startSearch(
        scope: SearchScope,
        replacedOrigins: Set<String>?,
    ) {
        val book = oldBook ?: return
        stopSearch()
        val removedResults = synchronized(searchResults) {
            val results = if (replacedOrigins == null) {
                searchResults.toList()
            } else {
                searchResults.filter { it.origin in replacedOrigins }
            }
            searchResults.removeAll(results.toSet())
            results
        }
        val removedKeys = removedResults.mapTo(hashSetOf()) { it.primaryStr() }
        removedKeys.forEach {
            bookMap.remove(it)
            tocMap.remove(it)
        }
        if (replacedOrigins == null) {
            cachedTocChapterCount = 0
        }
        _changeSourceProgress.value = 0 to ""
        filterResults()

        searchJob = viewModelScope.launch(IO) {
            try {
                if (removedResults.isNotEmpty()) {
                    io.legado.app.data.appDb.searchBookDao.delete(*removedResults.toTypedArray())
                }
                collectSearchEvents(
                    events = changeSourceSearchUseCase.search(
                        name = bookName,
                        author = bookAuthor,
                        scope = scope,
                        oldBook = book,
                        fromReadBookActivity = fromReadBookActivity,
                    ),
                    emptyScope = scope,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportSearchError(error)
            }
        }
    }

    private suspend fun collectSearchEvents(
        events: Flow<ChangeSourceSearchEvent>,
        emptyScope: SearchScope? = null,
    ) {
        try {
            events.collect { event ->
                when (event) {
                    is ChangeSourceSearchEvent.Started -> {
                        _isSearching.value = true
                        totalSourceCount = event.totalSources
                    }

                    is ChangeSourceSearchEvent.Progress -> {
                        totalSourceCount = event.totalSources
                        _changeSourceProgress.value =
                            event.processedSources to event.sourceName
                        filterResults()
                    }

                    is ChangeSourceSearchEvent.Result -> {
                        event.searchBook.releaseHtmlData()
                        searchRepository.saveSearchBook(event.searchBook)
                        val key = event.searchBook.primaryStr()
                        searchResults.add(event.searchBook)
                        bookMap[key] = event.book
                        event.toc?.let { toc ->
                            if (cachedTocChapterCount < MAX_CACHED_CHAPTERS) {
                                cachedTocChapterCount += toc.size
                                tocMap[key] = toc
                            }
                        }
                    }

                    is ChangeSourceSearchEvent.Finished -> {
                        filterResults()
                        val isResultEmpty = synchronized(searchResults) {
                            searchResults.isEmpty()
                        }
                        if (event.isEmpty && isResultEmpty && emptyScope?.isAll() == false) {
                            _emptyScopeName.value = emptyScope.display
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            reportSearchError(error)
        } finally {
            _isSearching.value = false
        }
    }

    private fun reportSearchError(error: Throwable) {
        AppLog.put("换源搜索出错\n${error.localizedMessage}", error)
        _effects.tryEmit(
            ChangeBookSourceEffect.ShowMessage(
                error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.toString()
            )
        )
    }

    fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        _isSearching.value = false
    }

    fun confirmEmptyScopeSearch() {
        _emptyScopeName.value = null
        searchScope.update("")
        updateScopeState()
        startSearch()
    }

    fun dismissEmptyScopeSearch() {
        _emptyScopeName.value = null
    }

    fun screen(key: String?) {
        screenKey = key?.trim() ?: ""
        filterResults()
    }

    fun startOrStopSearch() {
        if (searchJob?.isActive == true) {
            stopSearch()
        } else {
            startSearch()
        }
    }

    fun pause() = Unit

    fun resume() = Unit

    private fun filterResults() {
        val key = screenKey
        val filtered = synchronized(searchResults) {
            if (key.isEmpty()) {
                searchResults.toList()
            } else {
                searchResults.filter {
                    it.originName.contains(key) ||
                        it.latestChapterTitle?.contains(key) == true
                }
            }
        }
        val comparator = if (settings.value.loadWordCount) {
            compareByDescending<SearchBook> { ObservableSourceConfig.getBookScore(it) }
                .thenByDescending { io.legado.app.help.config.SourceConfig.getSourceScore(it.origin) }
                .thenByDescending { it.chapterWordCount > 1000 }
                .thenByDescending { getChapterNum(it.chapterWordCountText) }
                .thenByDescending { it.chapterWordCount }
                .thenBy { it.originOrder }
        } else {
            compareByDescending<SearchBook> { ObservableSourceConfig.getBookScore(it) }
                .thenByDescending { io.legado.app.help.config.SourceConfig.getSourceScore(it.origin) }
                .thenBy { it.originOrder }
        }
        _searchDataFlow.value = filtered.sortedWith(comparator)
    }

    private fun getChapterNum(text: String?): Int {
        if (text.isNullOrBlank()) return -1
        return chapterNumRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    fun getToc(
        book: Book,
        onSuccess: (toc: List<BookChapter>, source: BookSource) -> Unit,
        onError: (e: Throwable) -> Unit,
    ) {
        stopSearch()
        viewModelScope.launch(IO) {
            try {
                val cachedToc = tocMap[book.primaryStr()]
                if (cachedToc != null) {
                    val source =
                        io.legado.app.data.appDb.bookSourceDao.getBookSource(book.origin)
                    if (source != null) {
                        onMain { onSuccess(cachedToc, source) }
                        return@launch
                    }
                }
                if (book.isWebFile) {
                    val source =
                        io.legado.app.data.appDb.bookSourceDao.getBookSource(book.origin)
                        ?: throw io.legado.app.exception.NoStackTraceException("书源不存在")
                    onMain { onSuccess(emptyList(), source) }
                    return@launch
                }
                val (toc, source) = getChapterContentUseCase.getToc(book)
                tocMap[book.primaryStr()] = toc
                onMain { onSuccess(toc, source) }
            } catch (e: Exception) {
                onMain { onError(e) }
            }
        }
    }

    // Options
    fun onCheckAuthorChange(enabled: Boolean) {
        if (settings.value.checkAuthor == enabled) return
        updateSetting { it.copy(checkAuthor = enabled) }
        refresh()
    }

    fun onLoadInfoChange(enabled: Boolean) {
        if (settings.value.loadInfo == enabled) return
        updateSetting { it.copy(loadInfo = enabled) }
    }

    fun onLoadTocChange(enabled: Boolean) {
        if (settings.value.loadToc == enabled) return
        updateSetting { it.copy(loadToc = enabled) }
    }

    fun onLoadWordCountChange(enabled: Boolean) {
        if (settings.value.loadWordCount == enabled) return
        updateSetting { it.copy(loadWordCount = enabled) }
        if (enabled) {
            refreshMissingWordCounts()
        } else {
            refresh()
        }
    }

    fun setMigrationOptions(options: ChangeSourceMigrationOptions) {
        viewModelScope.launch {
            changeSourceSettingsGateway.setMigrationOptions(options)
        }
    }

    private fun refreshMissingWordCounts() {
        val book = oldBook ?: return
        stopSearch()
        val refreshBooks = synchronized(searchResults) {
            searchResults
                .filter { it.chapterWordCountText == null }
                .also { searchResults.removeAll(it.toSet()) }
        }
        if (refreshBooks.isEmpty()) {
            filterResults()
            return
        }
        filterResults()
        _changeSourceProgress.value = 0 to ""
        searchJob = viewModelScope.launch(IO) {
            collectSearchEvents(
                changeSourceSearchUseCase.refresh(
                    books = refreshBooks,
                    oldBook = book,
                    fromReadBookActivity = fromReadBookActivity,
                )
            )
        }
    }

    fun refresh(startSearchIfEmpty: Boolean = false) {
        viewModelScope.launch(IO) {
            val dbBooks = getDbSearchBooks()
            synchronized(searchResults) {
                searchResults.clear()
                searchResults.addAll(dbBooks)
            }
            bookMap.clear()
            tocMap.clear()
            cachedTocChapterCount = 0
            if (dbBooks.isNotEmpty()) {
                dbBooks.forEach { bookMap[it.primaryStr()] = it.toBook() }
            }
            filterResults()
            if (dbBooks.isEmpty() && startSearchIfEmpty) {
                startSearch()
            }
        }
    }

    // Source actions
    fun topSource(searchBook: SearchBook) {
        changeSourceSearchUseCase.topSource(searchBook)
        refresh()
    }

    fun bottomSource(searchBook: SearchBook) {
        changeSourceSearchUseCase.bottomSource(searchBook)
        refresh()
    }

    fun disableSource(searchBook: SearchBook) {
        searchResults.remove(searchBook)
        filterResults()
        viewModelScope.launch(IO) {
            changeSourceSearchUseCase.disableSource(searchBook)
        }
    }

    fun del(searchBook: SearchBook) {
        searchResults.remove(searchBook)
        filterResults()
        viewModelScope.launch(IO) {
            changeSourceSearchUseCase.deleteSource(searchBook)
        }
    }

    fun autoChangeSource(
        bookType: Int?,
        onSuccess: (book: Book, toc: List<BookChapter>, source: BookSource) -> Unit,
    ) {
        viewModelScope.launch(IO) {
            val matchingBooks = synchronized(searchResults) {
                searchResults.filter { it.type == bookType }
            }
            for (found in matchingBooks) {
                try {
                    val book = found.toBook()
                    val (toc, source) = getChapterContentUseCase.getToc(book)
                    onMain { onSuccess(book, toc, source) }
                    return@launch
                } catch (_: Exception) {
                }
            }
            _effects.tryEmit(
                ChangeBookSourceEffect.ShowMessageResource(R.string.auto_change_source_failed)
            )
        }
    }

    // Score
    fun bookScoreFlow(searchBook: SearchBook) = ObservableSourceConfig.bookScoreFlow(searchBook)

    fun onBookScoreClick(searchBook: SearchBook) {
        val currentScore = ObservableSourceConfig.getBookScore(searchBook)
        changeSourceSearchUseCase.topSource(searchBook)
        ObservableSourceConfig.setBookScore(searchBook, if (currentScore > 0) 0 else 1)
    }

    // Scope
    fun selectAllScope() {
        searchScope.update("")
        saveScope()
    }

    fun toggleScopeGroup(groupName: String) {
        if (searchScope.isSource()) {
            searchScope.update("")
        }
        val selected = searchScope.displayNames.toMutableSet()
        if (selected.contains(groupName)) {
            selected.remove(groupName)
        } else {
            selected.add(groupName)
        }
        searchScope.update(selected.toList())
        saveScope()
    }

    fun toggleScopeSource(source: BookSourcePart) {
        val selectedUrls = if (searchScope.isSource()) {
            searchScope.sourceUrls.toMutableSet()
        } else {
            mutableSetOf()
        }
        if (selectedUrls.contains(source.bookSourceUrl)) {
            selectedUrls.remove(source.bookSourceUrl)
        } else {
            selectedUrls.add(source.bookSourceUrl)
        }
        if (selectedUrls.isEmpty()) {
            searchScope.update("")
        } else {
            val selectedSources = io.legado.app.data.appDb.bookSourceDao.allEnabledPart.filter {
                selectedUrls.contains(it.bookSourceUrl)
            }
            searchScope.updateSources(selectedSources)
        }
        saveScope()
    }

    fun applyScopeSelection(selection: io.legado.app.ui.book.search.ScopeSelection) {
        if (selection.isSourceScope) {
            if (selection.sources.isEmpty()) {
                searchScope.update("")
            } else {
                searchScope.updateSources(selection.sources)
            }
        } else {
            if (selection.groupNames.isEmpty()) {
                searchScope.update("")
            } else {
                searchScope.update(selection.groupNames)
            }
        }
        saveScope()
    }

    private fun saveScope() {
        updateScopeState()
        refresh(startSearchIfEmpty = true)
    }

    private fun updateScopeState() {
        val value = searchScope.toString()
        updateSetting { it.copy(searchScope = value) }
        _scopeUiState.value = ScopeUiState(
            isAll = searchScope.isAll(),
            isSource = searchScope.isSource(),
            displayNames = searchScope.displayNames,
            sourceUrls = searchScope.sourceUrls
        )
    }

    private suspend fun onMain(block: () -> Unit) {
        withContext(Main.immediate) {
            block()
        }
    }

    private fun updateSetting(transform: (ChangeSourceSettings) -> ChangeSourceSettings) {
        viewModelScope.launch {
            changeSourceSettingsGateway.update(transform)
        }
    }

    private companion object {
        const val MAX_CACHED_CHAPTERS = 30_000
    }
}
