package io.legado.app.ui.book.changesource

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.domain.usecase.ChangeSourceSearchEvent
import io.legado.app.domain.usecase.ChangeSourceSearchUseCase
import io.legado.app.domain.usecase.GetChapterContentUseCase
import io.legado.app.ui.book.search.SearchScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangeChapterSourceViewModel(
    private val changeSourceSearchUseCase: ChangeSourceSearchUseCase,
    private val getChapterContentUseCase: GetChapterContentUseCase,
    private val searchRepository: SearchRepository,
    private val changeSourceSettingsGateway: ChangeSourceSettingsGateway,
) : ViewModel() {

    private val initialSettings = changeSourceSettingsGateway.currentSettings
    private val _uiState = MutableStateFlow(
        ChangeChapterSourceUiState(
            checkAuthor = initialSettings.checkAuthor,
            loadInfo = initialSettings.loadInfo,
            loadToc = initialSettings.loadToc,
            loadWordCount = initialSettings.loadWordCount,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChangeChapterSourceEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    // Internal state
    private val chapterNumRegex = "^\\[(\\d+)]".toRegex()
    private var searchJob: Job? = null
    private var oldBook: Book? = null
    private var chapterIndex: Int = 0
    private var chapterTitle: String = ""
    private var screenKey: String = ""
    private val searchResults = mutableListOf<SearchBook>()
    private val bookMap = mutableMapOf<String, SearchBook>()

    // Scope state
    private val searchScope = SearchScope(initialSettings.searchScope)

    init {
        // Load initial scope state
        _uiState.update {
            it.copy(
                scopeState = ScopeUiState(
                    isAll = searchScope.isAll(),
                    isSource = searchScope.isSource(),
                    displayNames = searchScope.displayNames,
                    sourceUrls = searchScope.sourceUrls
                )
            )
        }
        // Collect enabled groups and sources
        viewModelScope.launch {
            changeSourceSettingsGateway.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        checkAuthor = settings.checkAuthor,
                        loadInfo = settings.loadInfo,
                        loadToc = settings.loadToc,
                        loadWordCount = settings.loadWordCount,
                    )
                }
            }
        }
        viewModelScope.launch {
            searchRepository.enabledGroups.collect { groups ->
                _uiState.update { it.copy(enabledGroups = groups.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            searchRepository.enabledSources.collect { sources ->
                _uiState.update { it.copy(enabledSources = sources.toImmutableList()) }
            }
        }
    }

    fun initData(book: Book, chapterIndex: Int, chapterTitle: String) {
        this.oldBook = book
        this.chapterIndex = chapterIndex
        this.chapterTitle = chapterTitle
        _uiState.update {
            it.copy(
                showToc = false,
                tocItems = persistentListOf(),
                isLoadingToc = false,
                currentTocIndex = -1,
                selectedSourceName = "",
            )
        }
        if (searchJob?.isActive != true) {
            viewModelScope.launch {
                val dbBooks = withContext(Dispatchers.IO) {
                    getDbSearchBooks()
                }
                if (dbBooks.isNotEmpty()) {
                    searchResults.clear()
                    searchResults.addAll(dbBooks)
                    searchResults.forEach { bookMap[it.primaryStr()] = it }
                    filterResults()
                } else {
                    startSearch()
                }
            }
        }
    }

    private fun getDbSearchBooks(): List<SearchBook> {
        val name = oldBook?.name ?: return emptyList()
        val author = oldBook?.author ?: return emptyList()
        val settings = changeSourceSettingsGateway.currentSettings
        val searchScope = SearchScope(settings.searchScope)
        val group = when {
            searchScope.isAll() || searchScope.isSource() -> ""
            else -> {
                val names = searchScope.displayNames
                if (names.size == 1) names.first() else ""
            }
        }
        val bookAuthor = if (settings.checkAuthor) author else ""
        return if (screenKey.isEmpty()) {
            io.legado.app.data.appDb.searchBookDao.changeSourceByGroup(
                name, bookAuthor, group
            )
        } else {
            io.legado.app.data.appDb.searchBookDao.changeSourceSearch(
                name, bookAuthor, screenKey, group
            )
        }
    }

    fun onIntent(intent: ChangeChapterSourceIntent) {
        when (intent) {
            is ChangeChapterSourceIntent.UpdateQuery -> {
                screenKey = intent.query.trim()
                _uiState.update { it.copy(searchQuery = intent.query) }
                filterResults()
            }

            is ChangeChapterSourceIntent.StartStopSearch -> {
                if (searchJob?.isActive == true) {
                    stopSearch()
                } else {
                    startSearch()
                }
            }

            is ChangeChapterSourceIntent.Refresh -> {
                startSearch()
            }

            is ChangeChapterSourceIntent.SelectSource -> {
                selectSource(intent.searchBook)
            }

            is ChangeChapterSourceIntent.BackFromToc -> {
                _uiState.update {
                    it.copy(
                        showToc = false,
                        tocItems = persistentListOf(),
                        isLoadingToc = false,
                        currentTocIndex = -1,
                    )
                }
            }

            is ChangeChapterSourceIntent.SelectChapter -> {
                selectChapter(intent.chapter)
            }
            // Options
            is ChangeChapterSourceIntent.SetCheckAuthor -> {
                updateSetting { it.copy(checkAuthor = intent.enabled) }
                _uiState.update { it.copy(checkAuthor = intent.enabled) }
                refreshResults()
            }

            is ChangeChapterSourceIntent.SetLoadInfo -> {
                updateSetting { it.copy(loadInfo = intent.enabled) }
                _uiState.update { it.copy(loadInfo = intent.enabled) }
            }

            is ChangeChapterSourceIntent.SetLoadToc -> {
                updateSetting { it.copy(loadToc = intent.enabled) }
                _uiState.update { it.copy(loadToc = intent.enabled) }
            }

            is ChangeChapterSourceIntent.SetLoadWordCount -> {
                updateSetting { it.copy(loadWordCount = intent.enabled) }
                _uiState.update { it.copy(loadWordCount = intent.enabled) }
                if (intent.enabled) {
                    startSearch()
                } else {
                    refreshResults()
                }
            }
            // Source actions
            is ChangeChapterSourceIntent.TopSource -> {
                changeSourceSearchUseCase.topSource(intent.searchBook)
                refreshResults()
            }

            is ChangeChapterSourceIntent.BottomSource -> {
                changeSourceSearchUseCase.bottomSource(intent.searchBook)
                refreshResults()
            }

            is ChangeChapterSourceIntent.DisableSource -> {
                changeSourceSearchUseCase.disableSource(intent.searchBook)
                searchResults.remove(intent.searchBook)
                filterResults()
            }

            is ChangeChapterSourceIntent.DeleteSource -> {
                changeSourceSearchUseCase.deleteSource(intent.searchBook)
                searchResults.remove(intent.searchBook)
                filterResults()
            }
            // Scope
            is ChangeChapterSourceIntent.ShowFilterSheet -> {
                // Handled by UI
            }

            is ChangeChapterSourceIntent.DismissFilterSheet -> {
                // Handled by UI
            }

            is ChangeChapterSourceIntent.SelectAllScope -> {
                searchScope.update("")
                updateScopeState()
            }

            is ChangeChapterSourceIntent.ToggleScopeGroup -> {
                if (searchScope.isSource()) {
                    searchScope.update("")
                }
                val selected = searchScope.displayNames.toMutableSet()
                if (selected.contains(intent.groupName)) {
                    selected.remove(intent.groupName)
                } else {
                    selected.add(intent.groupName)
                }
                searchScope.update(selected.toList())
                updateScopeState()
            }

            is ChangeChapterSourceIntent.ToggleScopeSource -> {
                val selectedUrls = if (searchScope.isSource()) {
                    searchScope.sourceUrls.toMutableSet()
                } else {
                    mutableSetOf()
                }
                if (selectedUrls.contains(intent.source.bookSourceUrl)) {
                    selectedUrls.remove(intent.source.bookSourceUrl)
                } else {
                    selectedUrls.add(intent.source.bookSourceUrl)
                }
                if (selectedUrls.isEmpty()) {
                    searchScope.update("")
                } else {
                    val selectedSources =
                        io.legado.app.data.appDb.bookSourceDao.allEnabledPart.filter {
                            selectedUrls.contains(it.bookSourceUrl)
                        }
                    searchScope.updateSources(selectedSources)
                }
                updateScopeState()
            }

            is ChangeChapterSourceIntent.ApplyScope -> {
                val value = searchScope.toString()
                updateSetting { it.copy(searchScope = value) }
                refreshResults()
            }
        }
    }

    private fun startSearch() {
        val book = oldBook ?: return
        stopSearch()
        if (searchResults.isNotEmpty()) {
            io.legado.app.data.appDb.searchBookDao.delete(*searchResults.toTypedArray())
        }
        searchResults.clear()
        bookMap.clear()
        val scope = SearchScope(changeSourceSettingsGateway.currentSettings.searchScope)
        _uiState.update {
            it.copy(
                totalSourceCount = scope.getBookSourceParts().size,
                searchProgress = 0 to "",
            )
        }
        filterResults()

        searchJob = viewModelScope.launch {
            changeSourceSearchUseCase.search(
                name = book.name,
                author = book.author,
                scope = scope,
                oldBook = book,
                fromReadBookActivity = true,
            ).collect { event ->
                when (event) {
                    is ChangeSourceSearchEvent.Started -> {
                        _uiState.update { it.copy(isSearching = true) }
                    }

                    is ChangeSourceSearchEvent.Progress -> {
                        _uiState.update {
                            it.copy(
                                searchProgress = event.processedSources to event.sourceName,
                                totalSourceCount = event.totalSources,
                            )
                        }
                    }

                    is ChangeSourceSearchEvent.Result -> {
                        searchResults.add(event.searchBook)
                        bookMap[event.searchBook.primaryStr()] = event.searchBook
                        io.legado.app.data.appDb.searchBookDao.insert(event.searchBook)
                        filterResults()
                    }

                    is ChangeSourceSearchEvent.Finished -> {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                }
            }
        }
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        _uiState.update { it.copy(isSearching = false) }
    }

    fun dispose() {
        stopSearch()
    }

    private fun refreshResults() {
        viewModelScope.launch {
            val dbBooks = withContext(Dispatchers.IO) {
                getDbSearchBooks()
            }
            searchResults.clear()
            bookMap.clear()
            if (dbBooks.isNotEmpty()) {
                searchResults.addAll(dbBooks)
                searchResults.forEach { bookMap[it.primaryStr()] = it }
                filterResults()
            } else {
                startSearch()
            }
        }
    }

    private fun filterResults() {
        val filtered = if (screenKey.isEmpty()) {
            searchResults.toList()
        } else {
            searchResults.filter {
                it.name.contains(screenKey) || it.originName.contains(screenKey)
            }
        }
        val comparator = if (_uiState.value.loadWordCount) {
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
        _uiState.update {
            it.copy(
                searchResults = filtered.sortedWith(comparator).toImmutableList(),
                bookMap = bookMap.toMap()
            )
        }
    }

    private fun getChapterNum(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return chapterNumRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun selectSource(searchBook: SearchBook) {
        val book = searchBook.toBook()
        _uiState.update {
            it.copy(
                showToc = true,
                selectedSourceName = searchBook.originName,
                isLoadingToc = true,
                currentTocIndex = -1,
            )
        }
        viewModelScope.launch {
            try {
                val (toc, _) = withContext(Dispatchers.IO) {
                    getChapterContentUseCase.getToc(book)
                }
                val currentTocIndex = getChapterContentUseCase.getDurChapterIndex(
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    toc = toc,
                    totalChapterNum = oldBook?.totalChapterNum ?: 0,
                ).takeIf { it in toc.indices } ?: -1
                _uiState.update {
                    it.copy(
                        tocItems = toc.toImmutableList(),
                        isLoadingToc = false,
                        currentTocIndex = currentTocIndex,
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        showToc = false,
                        isLoadingToc = false,
                        currentTocIndex = -1,
                    )
                }
                _effects.tryEmit(ChangeChapterSourceEffect.ShowToast("获取目录失败"))
            }
        }
    }

    private fun selectChapter(chapter: BookChapter) {
        val book = oldBook ?: return
        val selectedSearchBook = _uiState.value.searchResults.find {
            it.originName == _uiState.value.selectedSourceName
        } ?: return

        _uiState.update { it.copy(isLoadingToc = true) }
        viewModelScope.launch {
            try {
                val searchBook = selectedSearchBook.toBook()
                val toc = _uiState.value.tocItems
                val nextChapterUrl = toc.getOrNull(chapter.index + 1)?.url
                val content = withContext(Dispatchers.IO) {
                    getChapterContentUseCase.getContent(searchBook, chapter, nextChapterUrl)
                }
                _uiState.update { it.copy(isLoadingToc = false) }
                _effects.tryEmit(ChangeChapterSourceEffect.ReplaceContent(content))
                _effects.tryEmit(ChangeChapterSourceEffect.Dismiss)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingToc = false) }
                _effects.tryEmit(
                    ChangeChapterSourceEffect.ShowToast(
                        e.localizedMessage ?: "获取正文出错"
                    )
                )
            }
        }
    }

    private fun updateScopeState() {
        _uiState.update {
            it.copy(
                scopeState = ScopeUiState(
                    isAll = searchScope.isAll(),
                    isSource = searchScope.isSource(),
                    displayNames = searchScope.displayNames,
                    sourceUrls = searchScope.sourceUrls
                )
            )
        }
    }

    private fun updateSetting(transform: (ChangeSourceSettings) -> ChangeSourceSettings) {
        viewModelScope.launch {
            changeSourceSettingsGateway.update(transform)
        }
    }

    fun bookScoreFlow(searchBook: SearchBook) = ObservableSourceConfig.bookScoreFlow(searchBook)

    fun onBookScoreClick(searchBook: SearchBook) {
        val currentScore = ObservableSourceConfig.getBookScore(searchBook)
        ObservableSourceConfig.setBookScore(searchBook, if (currentScore > 0) 0 else 1)
    }
}
