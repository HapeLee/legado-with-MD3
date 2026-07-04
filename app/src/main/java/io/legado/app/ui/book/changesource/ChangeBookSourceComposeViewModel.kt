package io.legado.app.ui.book.changesource

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.usecase.ChangeSourceSearchEvent
import io.legado.app.domain.usecase.ChangeSourceSearchUseCase
import io.legado.app.domain.usecase.GetChapterContentUseCase
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.primaryStr
import io.legado.app.ui.book.search.SearchScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangeBookSourceComposeViewModel(
    private val changeSourceSearchUseCase: ChangeSourceSearchUseCase,
    private val getChapterContentUseCase: GetChapterContentUseCase,
    private val searchRepository: SearchRepository,
    private val bookDao: BookDao,
) : ViewModel() {

    // Public state for the sheet
    val enabledGroups = searchRepository.enabledGroups
    val enabledSources = searchRepository.enabledSources

    private val searchScope = SearchScope(ChangeSourceConfig.searchScope)

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

    var totalSourceCount: Int = 0
        private set

    fun getBookFromMap(key: String): Book? = bookMap[key]

    fun findShelfConflict(book: Book, onResult: (Book?) -> Unit) {
        viewModelScope.launch {
            val conflict = withContext(IO) {
                bookDao.getShelfBookConflict(book.name, book.author)
            }
            onResult(conflict)
        }
    }

    // Options
    val checkAuthor: Boolean get() = ChangeSourceConfig.checkAuthor
    val loadInfo: Boolean get() = ChangeSourceConfig.loadInfo
    val loadToc: Boolean get() = ChangeSourceConfig.loadToc
    val loadWordCount: Boolean get() = ChangeSourceConfig.loadWordCount

    // Internal state
    private val chapterNumRegex = "^\\[(\\d+)]".toRegex()
    private var searchJob: Job? = null
    private var oldBook: Book? = null
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var fromReadBookActivity: Boolean = false
    private var screenKey: String = ""
    private var knownEnabledSourceUrls: Set<String>? = null
    private var cachedTocChapterCount = 0
    private val searchResults = mutableListOf<SearchBook>()
    private val bookMap = mutableMapOf<String, Book>()
    private val tocMap = mutableMapOf<String, List<BookChapter>>()

    init {
        viewModelScope.launch {
            searchRepository.enabledSources.collect { sources ->
                val sourceUrls = sources.mapTo(linkedSetOf()) { it.bookSourceUrl }
                val previousSourceUrls = knownEnabledSourceUrls
                knownEnabledSourceUrls = sourceUrls
                if (oldBook != null && previousSourceUrls != null) {
                    val addedSources = sources.filter {
                        it.bookSourceUrl !in previousSourceUrls
                    }
                    if (addedSources.isNotEmpty()) {
                        startSearch(addedSources)
                    }
                }
            }
        }
    }

    fun initData(name: String, author: String, book: Book, fromReadBookActivity: Boolean) {
        this.oldBook = book
        this.bookName = name
        this.bookAuthor = author
        this.fromReadBookActivity = fromReadBookActivity
        if (searchJob?.isActive != true) {
            viewModelScope.launch {
                val dbBooks = withContext(IO) { getDbSearchBooks() }
                if (dbBooks.isNotEmpty()) {
                    searchResults.clear()
                    searchResults.addAll(dbBooks)
                    searchResults.forEach { bookMap[it.primaryStr()] = it.toBook() }
                    filterResults()
                    val cachedOrigins = dbBooks.mapTo(hashSetOf()) { it.origin }
                    val missingSources = withContext(IO) {
                        SearchScope(ChangeSourceConfig.searchScope)
                            .getBookSourceParts()
                            .filter { it.bookSourceUrl !in cachedOrigins }
                    }
                    if (missingSources.isNotEmpty()) {
                        startSearch(missingSources)
                    }
                } else {
                    startSearch()
                }
            }
        }
    }

    private fun getDbSearchBooks(): List<SearchBook> {
        val searchScope = SearchScope(ChangeSourceConfig.searchScope)
        val group = when {
            searchScope.isAll() || searchScope.isSource() -> ""
            else -> {
                val names = searchScope.displayNames
                if (names.size == 1) names.first() else ""
            }
        }
        val bookAuthor = if (checkAuthor) bookAuthor else ""
        return if (screenKey.isEmpty()) {
            io.legado.app.data.appDb.searchBookDao.changeSourceByGroup(
                bookName, bookAuthor, group
            )
        } else {
            io.legado.app.data.appDb.searchBookDao.changeSourceSearch(
                bookName, bookAuthor, screenKey, group
            )
        }
    }

    fun startSearch() {
        startSearch(
            scope = SearchScope(ChangeSourceConfig.searchScope),
            replacedOrigins = null,
        )
    }

    fun startSearch(origin: String) {
        viewModelScope.launch {
            val source = withContext(IO) {
                io.legado.app.data.appDb.bookSourceDao.getBookSourcePart(origin)
            } ?: return@launch
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
        val removedResults = if (replacedOrigins == null) {
            searchResults.toList()
        } else {
            searchResults.filter { it.origin in replacedOrigins }
        }
        val removedKeys = removedResults.mapTo(hashSetOf()) { it.primaryStr() }
        searchResults.removeAll(removedResults.toSet())
        removedKeys.forEach {
            bookMap.remove(it)
            tocMap.remove(it)
        }
        if (replacedOrigins == null) {
            cachedTocChapterCount = 0
        }
        _changeSourceProgress.value = 0 to ""
        filterResults()

        searchJob = viewModelScope.launch {
            if (removedResults.isNotEmpty()) {
                withContext(IO) {
                    io.legado.app.data.appDb.searchBookDao.delete(*removedResults.toTypedArray())
                }
            }
            val pendingBooks = arrayListOf<SearchBook>()
            try {
                changeSourceSearchUseCase.search(
                    name = book.name,
                    author = book.author,
                    scope = scope,
                    oldBook = book,
                    fromReadBookActivity = fromReadBookActivity,
                ).collect { event ->
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
                            val key = event.searchBook.primaryStr()
                            searchResults.add(event.searchBook)
                            pendingBooks.add(event.searchBook)
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
                            searchRepository.saveSearchBooks(pendingBooks)
                        }
                    }
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        _isSearching.value = false
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
        val filtered = if (screenKey.isEmpty()) {
            searchResults.toList()
        } else {
            searchResults.filter {
                it.name.contains(screenKey) || it.originName.contains(screenKey)
            }
        }
        val comparator = if (ChangeSourceConfig.loadWordCount) {
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
        if (text.isNullOrBlank()) return 0
        return chapterNumRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    fun getToc(
        book: Book,
        onSuccess: (toc: List<BookChapter>, source: BookSource) -> Unit,
        onError: (e: Throwable) -> Unit,
    ) {
        stopSearch()
        viewModelScope.launch {
            try {
                val cachedToc = tocMap[book.primaryStr()]
                if (cachedToc != null) {
                    val source = withContext(IO) {
                        io.legado.app.data.appDb.bookSourceDao.getBookSource(book.origin)
                    }
                    if (source != null) {
                        onSuccess(cachedToc, source)
                        return@launch
                    }
                }
                if (book.isWebFile) {
                    val source = withContext(IO) {
                        io.legado.app.data.appDb.bookSourceDao.getBookSource(book.origin)
                    }
                        ?: throw io.legado.app.exception.NoStackTraceException("书源不存在")
                    onSuccess(emptyList(), source)
                    return@launch
                }
                val (toc, source) = getChapterContentUseCase.getToc(book)
                tocMap[book.primaryStr()] = toc
                onSuccess(toc, source)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // Options
    fun onCheckAuthorChange(enabled: Boolean) {
        if (ChangeSourceConfig.checkAuthor == enabled) return
        ChangeSourceConfig.checkAuthor = enabled
        refresh()
    }

    fun onLoadInfoChange(enabled: Boolean) {
        if (ChangeSourceConfig.loadInfo == enabled) return
        ChangeSourceConfig.loadInfo = enabled
    }

    fun onLoadTocChange(enabled: Boolean) {
        if (ChangeSourceConfig.loadToc == enabled) return
        ChangeSourceConfig.loadToc = enabled
    }

    fun onLoadWordCountChange(enabled: Boolean) {
        if (ChangeSourceConfig.loadWordCount == enabled) return
        ChangeSourceConfig.loadWordCount = enabled
        if (enabled) {
            startSearch()
        } else {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val dbBooks = withContext(IO) { getDbSearchBooks() }
            searchResults.clear()
            bookMap.clear()
            tocMap.clear()
            cachedTocChapterCount = 0
            if (dbBooks.isNotEmpty()) {
                searchResults.addAll(dbBooks)
                searchResults.forEach { bookMap[it.primaryStr()] = it.toBook() }
                filterResults()
            } else {
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
        changeSourceSearchUseCase.disableSource(searchBook)
        searchResults.remove(searchBook)
        filterResults()
    }

    fun del(searchBook: SearchBook) {
        changeSourceSearchUseCase.deleteSource(searchBook)
        searchResults.remove(searchBook)
        filterResults()
    }

    fun autoChangeSource(
        bookType: Int?,
        onSuccess: (book: Book, toc: List<BookChapter>, source: BookSource) -> Unit,
    ) {
        viewModelScope.launch {
            val found = searchResults.firstOrNull { it.type == bookType }
            if (found != null) {
                try {
                    val (toc, source) = getChapterContentUseCase.getToc(found.toBook())
                    onSuccess(found.toBook(), toc, source)
                } catch (_: Exception) {
                }
            }
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
        ChangeSourceConfig.searchScope = searchScope.toString()
        _scopeUiState.value = ScopeUiState(
            isAll = searchScope.isAll(),
            isSource = searchScope.isSource(),
            displayNames = searchScope.displayNames,
            sourceUrls = searchScope.sourceUrls
        )
        refresh()
    }

    private companion object {
        const val MAX_CACHED_CHAPTERS = 30_000
    }
}
