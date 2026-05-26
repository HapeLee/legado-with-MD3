package io.legado.app.ui.book.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.MatchMode
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookSearchControl
import io.legado.app.domain.usecase.BookSearchRequest
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SearchRunEvent
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: SearchRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val searchBooksUseCase: SearchBooksUseCase,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
    private val localPreferencesRepository: LocalPreferencesRepository,
) : ViewModel() {

    val searchLayoutMode = localPreferencesRepository
        .getPreference(LocalPreferencesKeys.SEARCH_LAYOUT_MODE, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleSearchLayout() {
        viewModelScope.launch {
            val newMode = if (searchLayoutMode.value == 0) 1 else 0
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.SEARCH_LAYOUT_MODE, newMode
            )
        }
    }

    private val matchModeFlow = localPreferencesRepository
        .getPreference(LocalPreferencesKeys.MATCH_MODE, MatchMode.DEFAULT.value)
        .distinctUntilChanged()
        .map { MatchMode.of(it) }

    private val _uiState = MutableStateFlow(
        SearchUiState(
            scopeDisplay = SearchScope(AppConfig.searchScope).display,
            scopeDisplayNames = SearchScope(AppConfig.searchScope).displayNames.toImmutableList(),
            isAllScope = SearchScope(AppConfig.searchScope).isAll(),
            isSourceScope = SearchScope(AppConfig.searchScope).isSource(),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SearchEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val queryFlow = MutableStateFlow("")
    private val bookshelfKeys = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private val searchScope = SearchScope(AppConfig.searchScope)
    private val searchControl = BookSearchControl()
    private val searchResultBooks = LinkedHashMap<String, SearchBook>()

    private var searchJob: Job? = null
    private var currentSearchPage = 1
    private var wasSearching = false

    init {
        syncScopeState()
        observeEnabledGroups()
        observeEnabledSources()
        observeBookshelf()
        observeQueryHistory()
        observeQueryBookshelfHints()
        observeMatchMode()
    }

    fun onAddToShelf(book: SearchBook) {
        viewModelScope.launch {
            addToBookshelfUseCase.execute(book)
        }
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Initialize -> initialize(intent.key, intent.scopeRaw)
            is SearchIntent.UpdateQuery -> updateQuery(intent.query, showSuggestions = true)
            SearchIntent.SubmitSearch -> submitSearch()
            SearchIntent.LoadMore -> loadMore()
            SearchIntent.StopSearch -> stopSearch()
            SearchIntent.ClearSearchResults -> clearSearchResults()
            SearchIntent.PauseEngine -> {
                wasSearching = wasSearching || (searchJob?.isActive == true)
                searchControl.pause()
            }

            SearchIntent.ResumeEngine -> {
                searchControl.resume()
                if (wasSearching) {
                    val state = _uiState.value
                    if (state.committedQuery.isNotBlank() && searchJob?.isActive != true) {
                        startSearch(state.committedQuery, currentSearchPage)
                    }
                    wasSearching = false
                }
            }
            is SearchIntent.UseHistoryKeyword -> {
                updateQuery(intent.keyword, showSuggestions = false)
                submitSearch(intent.keyword)
            }

            is SearchIntent.OpenSearchBook -> {
                emitEffect(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name,
                        author = intent.book.author,
                        bookUrl = intent.book.bookUrl,
                        origin = intent.book.origin,
                        coverPath = intent.book.coverUrl,
                        sharedCoverKey = intent.sharedCoverKey,
                    )
                )
            }

            is SearchIntent.OpenBookshelfBook -> {
                emitEffect(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name,
                        author = intent.book.author,
                        bookUrl = intent.book.bookUrl,
                        origin = intent.book.origin,
                        coverPath = intent.book.getDisplayCover(),
                        sharedCoverKey = null,
                    )
                )
            }

            is SearchIntent.DeleteHistory -> viewModelScope.launch {
                repository.deleteSearchKeyword(intent.item)
            }

            is SearchIntent.SetClearHistoryDialogVisible -> {
                _uiState.update { it.copy(showClearHistoryDialog = intent.visible) }
            }

            SearchIntent.ConfirmClearHistory -> {
                _uiState.update { it.copy(showClearHistoryDialog = false) }
                viewModelScope.launch {
                    repository.clearSearchKeywords()
                }
            }

            is SearchIntent.SetScopeSheetVisible -> {
                _uiState.update { it.copy(showScopeSheet = intent.visible) }
            }

            is SearchIntent.SetSettingsSheetVisible -> {
                _uiState.update { it.copy(showSettingsSheet = intent.visible) }
            }

            is SearchIntent.ToggleSourceType -> {
                _uiState.update { state ->
                    val current = state.selectedSourceTypes
                    val next = if (current.contains(intent.type)) {
                        current - intent.type
                    } else {
                        current + intent.type
                    }
                    state.copy(selectedSourceTypes = next)
                }
                restartCommittedSearchIfNeeded()
            }

            SearchIntent.SelectAllScope -> {
                val oldScope = searchScope.toString()
                searchScope.update("")
                syncScopeState(restartSearch = true, oldScope = oldScope)
            }

            is SearchIntent.ToggleScopeGroup -> toggleScopeGroup(intent.groupName)
            is SearchIntent.ToggleScopeSource -> toggleScopeSource(intent.source)
            is SearchIntent.RemoveScopeItem -> {
                val oldScope = searchScope.toString()
                searchScope.remove(intent.scopeName)
                syncScopeState(restartSearch = true, oldScope = oldScope)
            }

            is SearchIntent.SetMatchMode -> {
                viewModelScope.launch {
                    localPreferencesRepository.updatePreference(
                        LocalPreferencesKeys.MATCH_MODE, intent.mode.value
                    )
                }
                restartCommittedSearchIfNeeded()
            }

            SearchIntent.ConfirmEmptyScopeAction -> handleEmptyScopeActionConfirmed()
            SearchIntent.DismissEmptyScopeAction -> {
                _uiState.update { it.copy(emptyScopeAction = null) }
            }

            SearchIntent.OpenSourceManage -> emitEffect(SearchEffect.OpenSourceManage)

            is SearchIntent.ExpandSource -> {
                _uiState.update {
                    it.copy(
                        expandedSourceUrl = intent.sourceUrl,
                        expandedSourceName = intent.sourceName,
                        expandedSourceBooks = persistentListOf(),
                        expandedSourceLoading = true,
                        expandedSourceEnd = false,
                        expandedSourceError = null,
                        expandedSourcePage = 1,
                    )
                }
                loadExpandedSourcePage(intent.sourceUrl, page = 1)
            }

            SearchIntent.DismissExpandedSource -> {
                _uiState.update {
                    it.copy(
                        expandedSourceUrl = null,
                        expandedSourceName = null,
                        expandedSourceBooks = persistentListOf(),
                        expandedSourceLoading = false,
                        expandedSourceEnd = false,
                        expandedSourceError = null,
                        expandedSourcePage = 1,
                    )
                }
            }

            SearchIntent.LoadMoreExpandedSource -> {
                val state = _uiState.value
                val sourceUrl = state.expandedSourceUrl ?: return
                if (state.expandedSourceLoading || state.expandedSourceEnd) return
                _uiState.update {
                    it.copy(
                        expandedSourceLoading = true,
                        expandedSourceError = null
                    )
                }
                loadExpandedSourcePage(sourceUrl, page = state.expandedSourcePage)
            }

            is SearchIntent.OpenExpandedSourceBook -> {
                _uiState.update {
                    it.copy(
                        expandedSourceUrl = null,
                        expandedSourceName = null,
                        expandedSourceBooks = persistentListOf(),
                        expandedSourceLoading = false,
                        expandedSourceEnd = false,
                        expandedSourceError = null,
                        expandedSourcePage = 1,
                    )
                }
                emitEffect(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name,
                        author = intent.book.author,
                        bookUrl = intent.book.bookUrl,
                        origin = intent.book.origin,
                        coverPath = intent.book.coverUrl,
                        sharedCoverKey = intent.sharedCoverKey,
                    )
                )
            }

            is SearchIntent.SaveScrollState -> {
                _uiState.update {
                    it.copy(
                        savedScrollIndex = intent.index,
                        savedScrollOffset = intent.offset,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        stopSearch(manualStop = false)
        super.onCleared()
    }

    private fun initialize(key: String?, scopeRaw: String?) {
        scopeRaw?.let {
            searchScope.update(it, postValue = false)
        }
        syncScopeState()

        val initKey = key?.trim().orEmpty()
        if (initKey.isNotEmpty()) {
            updateQuery(initKey, showSuggestions = false)
            submitSearch(initKey)
        } else if (key != null) {
            updateQuery(initKey, showSuggestions = true)
        }
    }

    private fun observeEnabledGroups() {
        viewModelScope.launch {
            repository.enabledGroups
                .catch { emit(emptyList()) }
                .collect { groups ->
                    _uiState.update { it.copy(enabledGroups = groups.toImmutableList()) }
                }
        }
    }

    private fun observeEnabledSources() {
        viewModelScope.launch {
            repository.enabledSources
                .catch { emit(emptyList()) }
                .collect { sources ->
                    _uiState.update { it.copy(enabledSources = sources.toImmutableList()) }
                }
        }
    }

    private fun observeBookshelf() {
        viewModelScope.launch {
            repository.bookshelfKeys
                .catch { emit(emptySet()) }
                .collect { keys ->
                    bookshelfKeys.value = keys
                    _uiState.update { state ->
                        state.copy(results = state.results.withShelfState(keys).toImmutableList())
                    }
                }
        }
    }

    private fun observeQueryHistory() {
        viewModelScope.launch {
            queryFlow
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { repository.searchHistory(it) }
                .catch { emit(emptyList()) }
                .collect { history ->
                    _uiState.update { it.copy(history = history.toImmutableList()) }
                }
        }
    }

    private fun observeQueryBookshelfHints() {
        viewModelScope.launch {
            queryFlow
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { repository.searchBookshelf(it) }
                .catch { emit(emptyList()) }
                .collect { books ->
                    _uiState.update { it.copy(bookshelfHints = books.toImmutableList()) }
                }
        }
    }

    private fun observeMatchMode() {
        viewModelScope.launch {
            matchModeFlow.collect { mode ->
                _uiState.update { it.copy(matchMode = mode) }
            }
        }
    }

    private fun updateQuery(query: String, showSuggestions: Boolean) {
        val currentState = _uiState.value
        val isSameQuery = currentState.query == query
        val sameUiFlag = currentState.showSuggestions == showSuggestions
        if (isSameQuery && sameUiFlag) {
            return
        }
        if (showSuggestions && currentState.isSearching && !isSameQuery) {
            stopSearch(manualStop = false)
        }
        queryFlow.value = query
        _uiState.update {
            it.copy(
                query = query,
                showSuggestions = showSuggestions,
                isManualStop = false,
                emptyScopeAction = null,
            )
        }
    }

    private fun submitSearch(keyOverride: String? = null) {
        val keyword = keyOverride?.trim() ?: queryFlow.value.trim()
        if (keyword.isBlank()) return

        updateQuery(keyword, showSuggestions = false)

        currentSearchPage = 1
        searchResultBooks.clear()
        _uiState.update {
            it.copy(
                committedQuery = keyword,
                results = persistentListOf(),
                isManualStop = false,
                hasMore = true,
                processedSources = 0,
                totalSources = 0,
                emptyScopeAction = null,
            )
        }

        viewModelScope.launch {
            repository.saveSearchKeyword(keyword)
        }
        startSearch(keyword, currentSearchPage)
    }

    private fun loadMore() {
        val state = _uiState.value
        if (state.isSearching) return
        if (state.committedQuery.isBlank()) return
        if (!state.hasMore) return

        currentSearchPage += 1
        _uiState.update {
            it.copy(
                isManualStop = false,
                showSuggestions = false,
            )
        }
        startSearch(state.committedQuery, currentSearchPage)
    }

    private fun startSearch(keyword: String, page: Int) {
        searchJob?.cancel()
        searchControl.resume()
        wasSearching = true
        searchJob = viewModelScope.launch {
            try {
                searchBooksUseCase
                    .execute(
                        BookSearchRequest(
                            keyword = keyword,
                            page = page,
                            scope = BookSearchScope(searchScope.toString()),
                            matchMode = _uiState.value.matchMode,
                            concurrency = OtherConfig.threadCount,
                            types = _uiState.value.selectedSourceTypes.takeIf { it.isNotEmpty() },
                        ),
                        searchControl
                    )
                    .collect { event -> handleSearchEvent(event) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                _uiState.update { it.copy(isSearching = false) }
                exception.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { emitEffect(SearchEffect.ShowMessage(it)) }
            }
        }
    }

    private fun handleSearchEvent(event: SearchRunEvent) {
        when (event) {
            SearchRunEvent.Started -> {
                _uiState.update { it.copy(isSearching = true) }
            }

            is SearchRunEvent.Progress -> {
                event.removedBookUrls.forEach { searchResultBooks.remove(it) }
                event.upsertBooks.forEach { book ->
                    searchResultBooks[book.bookUrl] = book
                }
                _uiState.update {
                    it.copy(
                        results = buildSearchResultItems(
                            shelf = bookshelfKeys.value,
                        ).toImmutableList(),
                        processedSources = event.processedSources,
                        totalSources = event.totalSources,
                    )
                }
            }

            is SearchRunEvent.Finished -> {
                _uiState.update { state ->
                    val emptyAction = if (searchResultBooks.isEmpty() && event.isEmpty && !searchScope.isAll()) {
                        SearchEmptyScopeAction(
                            scopeDisplay = searchScope.display,
                            wasMatchMode = state.matchMode,
                        )
                    } else {
                        null
                    }
                    state.copy(
                        isSearching = false,
                        hasMore = event.hasMore,
                        emptyScopeAction = emptyAction,
                    )
                }
            }
        }
    }

    private fun stopSearch(manualStop: Boolean = true) {
        searchJob?.cancel()
        searchJob = null
        wasSearching = false
        _uiState.update {
            it.copy(
                isSearching = false,
                isManualStop = manualStop || it.isManualStop,
            )
        }
    }

    private fun clearSearchResults() {
        stopSearch(manualStop = true)
        searchResultBooks.clear()
        _uiState.update {
            it.copy(
                query = "",
                committedQuery = "",
                results = persistentListOf(),
                processedSources = 0,
                totalSources = 0,
                isSearching = false,
                isManualStop = false,
                hasMore = true,
                showSuggestions = true,
                emptyScopeAction = null,
            )
        }
    }

    private fun toggleScopeGroup(groupName: String) {
        val oldScope = searchScope.toString()
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
        syncScopeState(restartSearch = true, oldScope = oldScope)
    }

    private fun toggleScopeSource(source: BookSourcePart) {
        val oldScope = searchScope.toString()
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
            val selectedSources = _uiState.value.enabledSources.filter {
                selectedUrls.contains(it.bookSourceUrl)
            }
            searchScope.updateSources(selectedSources)
        }
        syncScopeState(restartSearch = true, oldScope = oldScope)
    }

    private fun handleEmptyScopeActionConfirmed() {
        val action = _uiState.value.emptyScopeAction ?: return
        _uiState.update { it.copy(emptyScopeAction = null) }

        if (action.wasMatchMode == MatchMode.EXACT) {
            viewModelScope.launch {
                localPreferencesRepository.updatePreference(
                    LocalPreferencesKeys.MATCH_MODE, MatchMode.DEFAULT.value
                )
            }
        } else {
            searchScope.update("")
            syncScopeState()
        }

        restartCommittedSearchIfNeeded()
    }

    private fun restartCommittedSearchIfNeeded() {
        val committed = _uiState.value.committedQuery
        if (committed.isNotBlank()) {
            submitSearch(committed)
        }
    }

    private fun syncScopeState(
        restartSearch: Boolean = false,
        oldScope: String? = null,
    ) {
        val scopeChanged = oldScope == null || oldScope != searchScope.toString()
        _uiState.update {
            it.copy(
                scopeDisplay = searchScope.display,
                scopeDisplayNames = searchScope.displayNames.toImmutableList(),
                selectedScopeSourceUrls = searchScope.sourceUrls.toSet(),
                isAllScope = searchScope.isAll(),
                isSourceScope = searchScope.isSource(),
            )
        }
        if (restartSearch && scopeChanged) {
            restartCommittedSearchIfNeeded()
        }
    }

    private fun List<SearchBook>.toSearchResultItems(
        shelf: Set<BookShelfKey>
    ): List<SearchResultItemUi> {
        return map { book ->
            SearchResultItemUi(
                book = book,
                shelfState = resolveBookShelfStateUseCase.execute(
                    name = book.name,
                    author = book.author,
                    url = book.bookUrl,
                    shelf = shelf
                )
            )
        }
    }

    private fun buildSearchResultItems(
        shelf: Set<BookShelfKey>,
    ): List<SearchResultItemUi> {
        return searchResultBooks.values.toList().toSearchResultItems(shelf)
    }

    private fun List<SearchResultItemUi>.withShelfState(
        shelf: Set<BookShelfKey>
    ): List<SearchResultItemUi> {
        return map { item ->
            item.copy(
                shelfState = resolveBookShelfStateUseCase.execute(
                    name = item.book.name,
                    author = item.book.author,
                    url = item.book.bookUrl,
                    shelf = shelf
                )
            )
        }
    }

    private fun loadExpandedSourcePage(sourceUrl: String, page: Int) {
        viewModelScope.launch {
            val keyword = _uiState.value.committedQuery
            try {
                val result = exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = null,
                    args = null,
                    page = page,
                    key = keyword,
                )
                val newBooks = result.books
                _uiState.update {
                    it.copy(
                        expandedSourceBooks = (it.expandedSourceBooks + newBooks).toImmutableList(),
                        expandedSourceLoading = false,
                        expandedSourceEnd = newBooks.isEmpty(),
                        expandedSourcePage = page + 1,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        expandedSourceLoading = false,
                        expandedSourceError = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }

    private fun emitEffect(effect: SearchEffect) {
        _effects.tryEmit(effect)
    }
}
